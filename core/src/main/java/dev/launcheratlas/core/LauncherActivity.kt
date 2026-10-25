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

class LauncherActivity : ComponentActivity() {
    private val model: LauncherModel by viewModels()
    private val requestRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { model.refresh() }
    private val homeEpoch = mutableIntStateOf(0)

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
            val spec = state.spec
            if (spec == null) {
                MaterialTheme { Surface(Modifier.fillMaxSize()) { Text(state.error ?: "불러오는 중…", Modifier.padding(24.dp)) } }
            } else {
                AtlasTheme(spec.style) {
                    key(homeEpoch.intValue) {
                        LauncherScreen(spec, state.workspace, state.apps, state.home,
                            { model.roleIntent()?.let { requestRole.launch(it) } ?: model.error("기본 홈 선택을 사용할 수 없습니다") },
                            model::launch, model::save, model::applyWallpaper, model::reset)
                    }
                    state.error?.let { message -> AlertDialog(
                        onDismissRequest = { model.error(null) }, text = { Text(message) },
                        confirmButton = { TextButton(onClick = { model.error(null) }) { Text("확인") } },
                    ) }
                }
            }
        }
    }
    override fun onResume() { super.onResume(); model.refresh() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); homeEpoch.intValue++ }
}
