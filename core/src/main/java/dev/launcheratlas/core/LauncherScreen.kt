package dev.launcheratlas.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private fun color(hex: String) = Color(android.graphics.Color.parseColor(hex))

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

@Composable private fun Header(components: List<String>) {
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
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        if ("clock" in components) Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.headlineLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if ("date" in components) Text(now.format(DateTimeFormatter.ofPattern("MMM d · EEEE")), style = MaterialTheme.typography.bodyMedium)
            if ("battery" in components) Text(if (battery >= 0) "$battery%" else "—", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun LauncherScreen(
    spec: LauncherSpec, workspace: WorkspaceState, apps: List<AppEntry>, home: Boolean,
    onSetHome: () -> Unit, onLaunch: (AppRef) -> Unit, onWorkspace: (WorkspaceState) -> Unit,
    onWallpaper: (Boolean) -> Unit, onReset: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { workspace.pageCount })
    var drawer by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var addSlot by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var menuId by remember { mutableStateOf<String?>(null) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    var folderTarget by remember { mutableStateOf(false) }
    var moveTarget by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var resetConfirm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPoint by remember { mutableStateOf(Offset.Zero) }
    val bounds = remember { mutableMapOf<Pair<Int, Int>, Rect>() }
    val appByRef = remember(apps) { apps.associateBy { it.ref } }
    val selected = workspace.items.find { it.id == menuId }
    val folder = workspace.items.find { it.id == openFolderId && it.kind == "folder" }
    val motionOff = spec.style.reducedMotion == "always" || Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val wallpaperAsset = spec.assets.find { it.id == spec.style.wallpaper }
    val wallpaper = remember(wallpaperAsset?.path) { wallpaperAsset?.let { runCatching { context.assets.open(it.path).use { stream -> BitmapFactory.decodeStream(stream).asImageBitmap() } }.getOrNull() } }

    BackHandler(drawer || settings || menuId != null || openFolderId != null || addSlot != null || editing) {
        drawer = false; settings = false; menuId = null; openFolderId = null; addSlot = null; editing = false
    }
    Box(Modifier.fillMaxSize().background(color(spec.style.background))) {
        wallpaper?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = if (spec.style.wallpaperCrop == "fit") ContentScale.Fit else ContentScale.Crop) }
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(spec.name.uppercase(), color = color(spec.style.accent), style = MaterialTheme.typography.titleLarge)
                Row {
                    TextButton(onClick = { editing = !editing }, modifier = Modifier.semantics { contentDescription = "배치 편집" }) { Text(if (editing) "완료" else "편집") }
                    TextButton(onClick = { settings = true }, modifier = Modifier.semantics { contentDescription = "런처 설정" }) { Text("설정") }
                }
            }
            if (!home) TextButton(onClick = onSetHome) { Text("기본 홈으로 설정") }
            Header(spec.layout.components)
            if (editing) Text("길게 눌러 드래그 · 탭하여 폴더/이동/제거", style = MaterialTheme.typography.bodyMedium)
            HorizontalPager(state = pager, modifier = Modifier.weight(1f), userScrollEnabled = dragId == null) { page ->
                Column(Modifier.fillMaxSize().graphicsLayer {
                    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                    if (!motionOff) when (spec.style.pageTransition) {
                        "fade" -> alpha = 1f - abs(offset) * .6f
                        "depth" -> { rotationY = offset * -18f; scaleX = 1f - abs(offset) * .08f; scaleY = scaleX; cameraDistance = 12 * density }
                    }
                }.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(spec.style.spacing.dp)) {
                    repeat(workspace.rows) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spec.style.spacing.dp)) {
                            repeat(workspace.columns) { col ->
                                val cell = row * workspace.columns + col
                                val item = workspace.items.find { it.page == page && it.cell == cell }
                                WorkspaceCell(item, appByRef, spec, editing, Modifier.weight(1f),
                                    "셀 ${cell + 1} 앱 추가", { bounds[page to cell] = it },
                                    { if (item == null) addSlot = page to cell else if (editing) menuId = item.id else if (item.kind == "folder") openFolderId = item.id else item.app?.let(onLaunch) },
                                    { if (item != null) menuId = item.id },
                                    { point -> dragId = item?.id; dragPoint = bounds[page to cell]?.topLeft?.plus(point) ?: point },
                                    { delta -> dragPoint += delta },
                                    {
                                        val destination = bounds.entries.firstOrNull { it.key.first in listOf(page, -1) && it.value.contains(dragPoint) }?.key
                                        if (destination != null && dragId != null) onWorkspace(WorkspaceEditor.move(workspace, dragId!!, destination.first, destination.second))
                                        dragId = null }, { dragId = null })
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(workspace.pageCount) { page -> TextButton(onClick = { scope.launch { if (motionOff) pager.scrollToPage(page) else pager.animateScrollToPage(page) } }, contentPadding = PaddingValues(2.dp), modifier = Modifier.widthIn(min = 32.dp).semantics { contentDescription = "페이지 ${page + 1}" }) { Text(if (pager.currentPage == page) "●" else "○") } }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(spec.style.cornerRadius.dp)).background(color(spec.style.surface)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(workspace.dockCapacity) { cell ->
                    val item = workspace.items.find { it.page == -1 && it.cell == cell }
                    WorkspaceCell(item, appByRef, spec, editing, Modifier.weight(1f), "Dock ${cell + 1} 앱 추가", { bounds[-1 to cell] = it },
                        { if (item == null) addSlot = -1 to cell else if (editing) menuId = item.id else if (item.kind == "folder") openFolderId = item.id else item.app?.let(onLaunch) },
                        { if (item != null) menuId = item.id },
                        { point -> dragId = item?.id; dragPoint = bounds[-1 to cell]?.topLeft?.plus(point) ?: point },
                        { delta -> dragPoint += delta },
                        {
                            val destination = bounds.entries.firstOrNull { it.key.first in listOf(pager.currentPage, -1) && it.value.contains(dragPoint) }?.key
                            if (destination != null && dragId != null) onWorkspace(WorkspaceEditor.move(workspace, dragId!!, destination.first, destination.second))
                            dragId = null }, { dragId = null })
                }
            }
            TextButton(onClick = { query = ""; drawer = true }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "앱 서랍 열기" }) { Text("모든 앱  ↑") }
        }
        if (dragId != null) Surface(Modifier.align(Alignment.TopCenter).systemBarsPadding(), color = color(spec.style.accent)) { Text("놓아서 이동 · 겹친 항목은 자리 교환", Modifier.padding(12.dp), color = color(spec.style.background)) }
    }

    if (drawer || addSlot != null) {
        AtlasDialog(if (addSlot != null) "홈에 앱 추가" else "모든 앱", { drawer = false; addSlot = null }) {
            OutlinedTextField(query, { query = it }, label = { Text("앱 검색") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "앱 검색" }, singleLine = true)
            val filtered = apps.filter { it.label.contains(query, true) || it.ref.component.contains(query, true) }
            if (filtered.isEmpty()) Text("검색 결과가 없습니다")
            filtered.forEach { app ->
                Row(Modifier.fillMaxWidth().combinedClickable(onClick = {
                    val target = addSlot
                    if (target != null) onWorkspace(WorkspaceEditor.add(workspace, app.ref, target.first, target.second)) else onLaunch(app.ref)
                    drawer = false; addSlot = null
                }, onLongClick = {
                    WorkspaceEditor.firstFree(workspace, pager.currentPage)?.let { onWorkspace(WorkspaceEditor.add(workspace, app.ref, pager.currentPage, it)) }
                    drawer = false; addSlot = null
                }).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppPicture(app.ref, app, spec, Modifier.size(42.dp)); Spacer(Modifier.width(12.dp)); Text(app.label)
                }
            }
        }
    }
    if (selected != null) {
        AtlasDialog("항목 편집", { menuId = null; folderTarget = false; moveTarget = false }) {
            Text(if (selected.kind == "folder") selected.label else appByRef[selected.app]?.label ?: "미설치 앱")
            if (selected.kind == "folder") {
                var label by remember(selected.id) { mutableStateOf(selected.label) }
                OutlinedTextField(label, { label = it }, label = { Text("폴더 이름") })
                TextButton(onClick = { onWorkspace(WorkspaceEditor.rename(workspace, selected.id, label)); menuId = null }) { Text("이름 저장") }
            }
            TextButton(onClick = { moveTarget = !moveTarget; folderTarget = false }) { Text("다른 페이지 / Dock으로 이동") }
            if (moveTarget) {
                (-1 until workspace.pageCount).forEach { page ->
                    WorkspaceEditor.firstFree(workspace, page)?.let { cell -> TextButton(onClick = { onWorkspace(WorkspaceEditor.move(workspace, selected.id, page, cell)); menuId = null; moveTarget = false }) { Text(if (page == -1) "Dock 빈 자리" else "페이지 ${page + 1} 빈 자리") } }
                }
            }
            if (selected.kind == "app") {
                TextButton(onClick = { folderTarget = !folderTarget; moveTarget = false }) { Text("다른 항목과 폴더로 묶기") }
                if (folderTarget) workspace.items.filter { it.id != selected.id }.forEach { target ->
                    TextButton(onClick = { onWorkspace(WorkspaceEditor.folder(workspace, selected.id, target.id, if (target.kind == "folder") target.label else "폴더")); menuId = null; folderTarget = false }) { Text(if (target.kind == "folder") target.label else appByRef[target.app]?.label ?: "미설치 앱") }
                }
            }
            TextButton(onClick = { onWorkspace(WorkspaceEditor.remove(workspace, selected.id)); menuId = null }) { Text(if (selected.kind == "folder") "홈에서 폴더 제거 (앱은 삭제되지 않음)" else "홈에서 제거") }
        }
    }
    if (folder != null) {
        AtlasDialog(folder.label, { openFolderId = null }) {
            folder.children.forEach { ref ->
                TextButton(onClick = { onLaunch(ref) }) { Text(appByRef[ref]?.label ?: "미설치: ${ref.component.substringBefore('/')}") }
                Row {
                    TextButton(onClick = { WorkspaceEditor.firstFree(workspace, pager.currentPage)?.let { onWorkspace(WorkspaceEditor.extract(workspace, folder.id, ref, pager.currentPage, it)) } }) { Text("홈으로 꺼내기") }
                    TextButton(onClick = { onWorkspace(WorkspaceEditor.removeChild(workspace, folder.id, ref)) }) { Text("폴더에서 제거") }
                }
            }
            TextButton(onClick = { menuId = folder.id; openFolderId = null }) { Text("폴더 편집") }
        }
    }
    if (settings) AtlasDialog("${spec.name} 설정", { settings = false }) {
        Text("Launcher Atlas · ${spec.versionName} · Core ${spec.coreVersion}")
        TextButton(onClick = onSetHome) { Text("시스템에서 기본 홈 선택") }
        TextButton(onClick = { onWallpaper(false) }) { Text("홈 배경 적용") }
        TextButton(onClick = { onWallpaper(true) }) { Text("잠금 배경 적용") }
        TextButton(onClick = { onWorkspace(WorkspaceEditor.addPage(workspace)) }) { Text("홈 페이지 추가 (${workspace.pageCount}/9)") }
        TextButton(onClick = { onWorkspace(WorkspaceEditor.removeLastPage(workspace)) }) { Text("마지막 빈 페이지 삭제") }
        TextButton(onClick = { resetConfirm = true }) { Text("레이아웃 초기화…") }
        Text("스타일 업데이트는 현재 배치를 유지합니다. 타사 위젯·업무/Private profile은 지원하지 않습니다.", style = MaterialTheme.typography.bodyMedium)
    }
    if (resetConfirm) AlertDialog(onDismissRequest = { resetConfirm = false }, title = { Text("배치를 초기화할까요?") }, text = { Text("사용자가 편집한 폴더와 배치가 기본 SPEC으로 바뀝니다.") }, confirmButton = { TextButton(onClick = { onReset(); resetConfirm = false; settings = false }) { Text("초기화") } }, dismissButton = { TextButton(onClick = { resetConfirm = false }) { Text("취소") } })
}

@Composable private fun AtlasDialog(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }, confirmButton = { TextButton(onClick = close) { Text("닫기") } })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun WorkspaceCell(
    item: WorkspaceItem?, apps: Map<AppRef, AppEntry>, spec: LauncherSpec, editing: Boolean, modifier: Modifier,
    emptyDescription: String, onBounds: (Rect) -> Unit, click: () -> Unit, longClick: () -> Unit,
    dragStart: (Offset) -> Unit, drag: (Offset) -> Unit, dragEnd: () -> Unit, dragCancel: () -> Unit,
) {
    val latestStart by rememberUpdatedState(dragStart)
    val latestDrag by rememberUpdatedState(drag)
    val latestEnd by rememberUpdatedState(dragEnd)
    val latestCancel by rememberUpdatedState(dragCancel)
    val dragModifier = if (editing && item != null) Modifier.pointerInput(item.id) {
        detectDragGesturesAfterLongPress(
            onDragStart = { latestStart(it) }, onDragEnd = { latestEnd() }, onDragCancel = { latestCancel() },
        ) { change, amount -> change.consume(); latestDrag(amount) }
    } else Modifier
    Column(modifier.heightIn(min = 90.dp).onGloballyPositioned { onBounds(it.boundsInRoot()) }.then(dragModifier)
        .combinedClickable(onClick = click, onLongClick = if (editing) null else longClick)
        .semantics { contentDescription = if (item == null) emptyDescription else if (item.kind == "folder") "폴더 ${item.label}" else apps[item.app]?.label ?: "미설치 앱" },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when {
            item == null -> Box(Modifier.size(spec.style.iconSize.dp).border(1.dp, color(spec.style.foreground).copy(alpha = .15f), RoundedCornerShape(spec.style.cornerRadius.dp)), contentAlignment = Alignment.Center) { Text("+", color = color(spec.style.foreground).copy(alpha = .4f)) }
            item.kind == "folder" -> Box(Modifier.size(spec.style.iconSize.dp).clip(RoundedCornerShape(spec.style.cornerRadius.dp)).background(color(spec.style.surface)), contentAlignment = Alignment.Center) { Text("▦", fontSize = 30.sp, color = color(spec.style.accent)) }
            else -> AppPicture(item.app!!, apps[item.app], spec, Modifier.size(spec.style.iconSize.dp))
        }
        if (item != null && spec.style.showLabels) Text(if (item.kind == "folder") item.label else apps[item.app]?.label ?: "미설치 앱", fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable private fun AppPicture(ref: AppRef, app: AppEntry?, spec: LauncherSpec, modifier: Modifier) {
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
