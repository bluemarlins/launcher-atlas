package dev.launcheratlas.core

import android.app.Application
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File
import java.text.Collator

data class AppEntry(val ref: AppRef, val label: String, val icon: Drawable? = null)
data class AtlasState(
    val spec: LauncherSpec? = null, val workspace: CellWorkspace = CellWorkspace(),
    val apps: List<AppEntry> = emptyList(), val home: Boolean = false, val error: String? = null,
)

class LauncherModel(private val application: Application) : AndroidViewModel(application) {
    private val launcherApps = application.getSystemService(LauncherApps::class.java)
    private val users = application.getSystemService(UserManager::class.java)
    private val serial = users.getSerialNumberForUser(Process.myUserHandle())
    private val workspaceFile = AtomicFile(File(application.filesDir, "workspace-v2.json"))
    val widgets = AtlasWidgetHost(application)
    private val mutable = MutableStateFlow(AtlasState())
    val state = mutable.asStateFlow()
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    init {
        runCatching {
            val spec = atlasJson.decodeFromString<LauncherSpec>(application.assets.open("launcher.json").bufferedReader().use { it.readText() }).validate()
            require(spec.applicationId == application.packageName) { "SPEC applicationId does not match this app" }
            val workspace = loadWorkspace(spec)
            widgets.reconcile(workspace)
            mutable.value = AtlasState(spec, workspace)
            launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
            refresh()
        }.onFailure { mutable.update { state -> state.copy(error = "설정 읽기 실패: ${it.message}") } }
    }

    private fun loadWorkspace(spec: LauncherSpec): CellWorkspace {
        if (workspaceFile.baseFile.exists()) return atlasJson.decodeFromString<CellWorkspace>(workspaceFile.openRead().bufferedReader().use { it.readText() })
            .also { require(CellEditor.valid(it)) { "저장된 v2 배치가 손상되었습니다. 원본을 보존했습니다." } }
        val legacy = File(application.filesDir, "workspace-v1.json")
        if (!legacy.exists()) return spec.initialCells(serial)
        val text = legacy.readText()
        val backup = File(application.filesDir, "workspace-v1.backup.json")
        if (!backup.exists()) backup.writeText(text)
        val migrated = CellEditor.migrate(atlasJson.decodeFromString<WorkspaceState>(text), listOf("clock", "date", "battery"))
        var stream: java.io.FileOutputStream? = null
        try {
            stream = workspaceFile.startWrite()
            stream.write(atlasJson.encodeToString(migrated).toByteArray())
            workspaceFile.finishWrite(stream)
        } catch (e: Exception) { workspaceFile.failWrite(stream); throw e }
        return migrated
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                val entries = withContext(Dispatchers.IO) {
                    val collator = Collator.getInstance()
                    launcherApps.getActivityList(null, Process.myUserHandle())
                        .filter { it.componentName.packageName != application.packageName }
                        .map { AppEntry(AppRef(it.componentName.flattenToShortString(), serial), it.label.toString(), it.getBadgedIcon(0)) }
                        .distinctBy { it.ref }.sortedWith { a, b -> collator.compare(a.label, b.label) }
                }
                val role = application.getSystemService(RoleManager::class.java)
                mutable.update { it.copy(apps = entries, home = role.isRoleAvailable(RoleManager.ROLE_HOME) && role.isRoleHeld(RoleManager.ROLE_HOME)) }
            }.onFailure { error("앱 목록을 읽지 못했습니다: ${it.message}") }
        }
    }

    fun save(workspace: CellWorkspace) {
        if (state.value.spec == null || !CellEditor.valid(workspace)) { error("잘못된 배치를 저장할 수 없습니다"); return }
        var stream: java.io.FileOutputStream? = null
        try {
            stream = workspaceFile.startWrite()
            stream.write(atlasJson.encodeToString(workspace).toByteArray(Charsets.UTF_8))
            workspaceFile.finishWrite(stream)
            val removedIds = state.value.workspace.cells.mapNotNull { it.appWidgetId }.toSet() - workspace.cells.mapNotNull { it.appWidgetId }.toSet()
            mutable.update { it.copy(workspace = workspace, error = null) }
            removedIds.forEach(widgets::delete)
        } catch (exception: Exception) {
            workspaceFile.failWrite(stream)
            error("배치 저장 실패: ${exception.message}")
        }
    }

    fun launch(ref: AppRef) {
        runCatching {
            require(ref.userSerial == serial) { "다른 프로필은 지원하지 않습니다" }
            val component = ComponentName.unflattenFromString(ref.component) ?: throw IllegalArgumentException("잘못된 앱 정보")
            launcherApps.startMainActivity(component, Process.myUserHandle(), null, null)
        }.onFailure { error("앱이 없거나 실행할 수 없습니다. 앱 서랍에서 다시 선택하세요.") }
    }

    fun roleIntent(): Intent? {
        val role = application.getSystemService(RoleManager::class.java)
        return if (role.isRoleAvailable(RoleManager.ROLE_HOME)) role.createRequestRoleIntent(RoleManager.ROLE_HOME) else null
    }

    fun applyWallpaper(lock: Boolean) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val spec = state.value.spec ?: throw IllegalStateException("SPEC missing")
                    val manager = WallpaperManager.getInstance(application)
                    check(manager.isWallpaperSupported && manager.isSetWallpaperAllowed) { "이 기기는 배경 변경을 허용하지 않습니다" }
                    val asset = spec.assets.find { it.id == spec.style.wallpaper }
                    if (asset != null) {
                        application.assets.open(asset.path).use { manager.setStream(it, null, true, if (lock) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM) }
                    } else {
                        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(Color.parseColor(spec.style.background))
                        manager.setBitmap(bitmap, null, true, if (lock) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM)
                        bitmap.recycle()
                    }
                }
                error(if (lock) "잠금 배경을 적용했습니다" else "홈 배경을 적용했습니다")
            }.onFailure { error("배경 적용 실패: ${it.message}") }
        }
    }

    fun profile(device: DeviceProfile) { runCatching { CellEditor.ensureProfile(state.value.workspace, device) }.onSuccess { if (it != state.value.workspace) save(it) }.onFailure { error(it.message) } }
    fun reset() { state.value.spec?.let { save(it.initialCells(serial)) } }
    fun error(message: String?) { mutable.update { it.copy(error = message) } }
    override fun onCleared() { runCatching { launcherApps.unregisterCallback(callback) }; widgets.stop() }
}
