package dev.launcheratlas.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.BatteryManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal fun color(hex: String) = Color(android.graphics.Color.parseColor(hex))

@Composable fun AtlasTheme(style: StyleSheet, content: @Composable () -> Unit) {
    val font = when (style.typography) { "serif" -> FontFamily.Serif; "mono" -> FontFamily.Monospace; else -> FontFamily.SansSerif }
    val background = color(style.background)
    val base = if ((background.red + background.green + background.blue) < 1.5f) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = base.copy(primary = color(style.accent), background = background, surface = color(style.surface), onBackground = color(style.foreground), onSurface = color(style.foreground)),
        typography = Typography(
            bodyLarge = TextStyle(fontFamily = font, fontSize = 16.sp),
            bodyMedium = TextStyle(fontFamily = font, fontSize = 14.sp),
            titleLarge = TextStyle(fontFamily = font, fontSize = 22.sp),
            headlineLarge = TextStyle(fontFamily = font, fontSize = 44.sp),
        ), content = { CompositionLocalProvider(LocalContentColor provides color(style.foreground), content = content) },
    )
}

@Composable internal fun BuiltinWidget(kind: String, width: Float, height: Float) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var battery by remember { mutableIntStateOf(-1) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { while (true) { now = LocalDateTime.now(); delay(1000) } }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                battery = if (level >= 0 && scale > 0) level * 100 / scale else -1
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(when (kind) {
            "clock" -> now.format(DateTimeFormatter.ofPattern("HH:mm"))
            "date" -> now.format(DateTimeFormatter.ofPattern(if (width > 150) "MMM d · EEEE" else "MM/dd"))
            else -> if (battery >= 0) "$battery%" else "—"
        }, fontSize = minOf(if (kind == "clock") width / 4.5f else width / 7f, height / 2.2f, 56f).coerceAtLeast(12f).sp, maxLines = 1)
        if (height > 130) Text(if (kind == "clock") now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")) else if (kind == "battery") "배터리" else now.year.toString(), fontSize = 13.sp, maxLines = 1)
    }
}

@Composable internal fun AtlasDialog(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }, confirmButton = { TextButton(onClick = close) { Text("닫기") } })
}

@Composable internal fun AppPicture(ref: AppRef, app: AppEntry?, spec: LauncherSpec, modifier: Modifier) {
    val context = LocalContext.current
    val mapping = spec.iconMappings.find { canonicalComponent(it.component) == canonicalComponent(ref.component) }
    val asset = spec.assets.find { it.id == mapping?.assetId }
    val bitmap = remember(ref, app?.icon, asset?.path) {
        asset?.let { runCatching { context.assets.open(it.path).use { stream -> BitmapFactory.decodeStream(stream)?.asImageBitmap() } }.getOrNull() }
            ?: runCatching { app?.icon?.toBitmap(144, 144)?.asImageBitmap() }.getOrNull()
    }
    val shape = if (spec.style.iconShape == "circle") CircleShape else RoundedCornerShape(if (spec.style.iconShape == "squircle") 18.dp else spec.style.cornerRadius.dp)
    Box(modifier.shadow(spec.style.shadow.dp, shape).clip(shape).background(color(spec.style.surface)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize().padding(if (asset == null) 5.dp else 0.dp), contentScale = ContentScale.Fit)
        else Text(app?.label?.take(1) ?: "?", fontSize = 24.sp, color = color(spec.style.accent))
    }
}
