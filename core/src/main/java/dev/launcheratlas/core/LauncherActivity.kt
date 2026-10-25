package dev.launcheratlas.core

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.graphics.ColorUtils
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.FoldingFeature
import androidx.compose.ui.geometry.Rect

class LauncherActivity : ComponentActivity() {
    private val model: LauncherModel by viewModels()
    private val requestRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { model.refresh() }
    private val homeEpoch = mutableIntStateOf(0)
    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) advanceWidget() else model.widgets.cancel()
    }

    private fun advanceWidget() {
        runCatching {
            if (!model.widgets.configure(this)) commitWidget()
        }.onFailure { model.widgets.cancel(); model.error("위젯 설정을 열 수 없습니다: ${it.message}") }
    }
    private fun commitWidget() { model.save(model.widgets.commit(model.state.value.workspace)) }

    @Deprecated("Widget host configuration callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AtlasWidgetHost.CONFIGURE_REQUEST) {
            if (resultCode == RESULT_OK) commitWidget() else model.widgets.cancel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val background = Color.parseColor(model.state.value.spec?.style?.background ?: "#F5F0E8")
        val darkBackground = ColorUtils.calculateLuminance(background) < .5
        val bars = if (darkBackground) SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        setContent {
            BackHandler { /* HOME must never finish on Back. */ }
            val state by model.state.collectAsStateWithLifecycle()
            val window by remember { WindowInfoTracker.getOrCreate(this@LauncherActivity).windowLayoutInfo(this@LauncherActivity) }
                .collectAsState(initial = null)
            val fold = window?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()
            val posture = if (fold?.state == FoldingFeature.State.HALF_OPENED) "half-open" else "flat"
            val hinge = fold?.takeIf { it.occlusionType == FoldingFeature.OcclusionType.FULL }?.bounds?.let { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
            val spec = state.spec
            if (spec == null) {
                MaterialTheme { Surface(Modifier.fillMaxSize()) { Text(state.error ?: "불러오는 중…", Modifier.padding(24.dp)) } }
            } else {
                AtlasTheme(spec.style) {
                    key(homeEpoch.intValue) {
                        LauncherScreen(spec, state.workspace, state.apps, state.home,
                            { model.roleIntent()?.let { requestRole.launch(it) } ?: model.error("기본 홈 선택을 사용할 수 없습니다") },
                            model::launch, model::save, model::applyWallpaper, model::reset,
                            model.widgets, { info, profile, placement, sizes ->
                                runCatching {
                                    if (model.widgets.begin(info, profile, placement, sizes, model.state.value.workspace.cells.any { it.id == placement.cellId })) advanceWidget()
                                    else model.widgets.bindIntent()?.let(bindWidget::launch)
                                }.onFailure { model.widgets.cancel(); model.error("위젯 추가 실패: ${it.message}") }
                            }, posture, hinge)
                    }
                    state.error?.let { message -> AlertDialog(
                        onDismissRequest = { model.error(null) }, text = { Text(message) },
                        confirmButton = { TextButton(onClick = { model.error(null) }) { Text("확인") } },
                    ) }
                }
            }
        }
    }
    override fun onStart() { super.onStart(); model.widgets.start() }
    override fun onStop() { model.widgets.stop(); super.onStop() }
    override fun onResume() { super.onResume(); model.refresh() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); homeEpoch.intValue++ }
}
