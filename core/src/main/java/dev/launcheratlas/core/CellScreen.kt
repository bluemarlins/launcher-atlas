package dev.launcheratlas.core

import android.appwidget.AppWidgetProviderInfo
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable fun LauncherScreen(
    spec: LauncherSpec, workspace: CellWorkspace, apps: List<AppEntry>, home: Boolean,
    onSetHome: () -> Unit, onLaunch: (AppRef) -> Unit, onWorkspace: (CellWorkspace) -> Unit,
    onWallpaper: (Boolean) -> Unit, onReset: () -> Unit,
    widgetHost: AtlasWidgetHost? = null, onWidget: (AppWidgetProviderInfo, String, CellPlacement, List<CellSize>) -> Unit = { _, _, _, _ -> },
    posture: String = "flat", hinge: Rect? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    var profileId by remember { mutableStateOf("") }
    val profile = workspace.profiles.find { it.id == profileId }
    val pager = rememberPagerState(pageCount = { profile?.pageCount ?: 1 })
    var drawer by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var addAt by remember { mutableStateOf<CellPlacement?>(null) }
    var addKind by remember { mutableStateOf("app") }
    var menuId by remember { mutableStateOf<String?>(null) }
    var folderId by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var device by remember { mutableStateOf<DeviceProfile?>(null) }
    var slotWidth by remember { mutableFloatStateOf(80f) }
    var slotHeight by remember { mutableFloatStateOf(90f) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPoint by remember { mutableStateOf(Offset.Zero) }
    var resizing by remember { mutableStateOf<CellPlacement?>(null) }
    var collision by remember { mutableStateOf<Pair<String, String>?>(null) }
    val bounds = remember { mutableMapOf<Pair<Int, Int>, Rect>() }
    val cellBounds = remember { mutableMapOf<String, Rect>() }
    val appMap = remember(apps) { apps.associateBy { it.ref } }
    val byId = workspace.cells.associateBy { it.id }
    val selected = byId[menuId]
    val selectedPlacement = profile?.let { (it.placements+it.unplaced).find { placement -> placement.cellId==menuId } }
    val folder = byId[folderId]?.takeIf { it.kind == "folder" }
    val motionOff = spec.style.reducedMotion == "always" || Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val asset = spec.assets.find { it.id == spec.style.wallpaper }
    val wallpaper = remember(asset?.path) { asset?.let { runCatching { context.assets.open(it.path).use { BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull() } }
    val latestWorkspace by rememberUpdatedState(workspace)
    val latestProfile by rememberUpdatedState(profile)
    val latestSave by rememberUpdatedState(onWorkspace)
    fun commit(next: CellWorkspace) { if (next == workspace) message = "빈 공간이나 허용 크기를 확인하세요. 현재 배치를 유지했습니다." else onWorkspace(next) }
    fun newCell(c: Cell, size: CellSize = CellSize()) {
        val p = profile ?: return
        val requested = addAt?.copy(cellId = c.id, spanX = size.x, spanY = size.y)
        val placement = requested?.takeIf { CellEditor.fits(p, it, workspace.dockCapacity) }
            ?: CellEditor.free(p, c.id, size, workspace.dockCapacity, pager.currentPage)
        if (placement == null) message = "이 페이지에 빈 공간이 없습니다" else commit(CellEditor.add(workspace, profileId, c, placement))
        addAt = null
    }
    fun finishDrag() {
        val s = latestWorkspace
        val p = latestProfile
        val id = dragId
        if (id != null && p != null) {
            val target = bounds.entries.firstOrNull { it.key.first in listOf(-1, pager.currentPage) && it.value.contains(dragPoint) }?.key
            val old = p.placements.find { it.cellId == id }
            if (target != null && old != null) {
                val cols = if (target.first == -1) s.dockCapacity else p.grid.columns
                val candidate = old.copy(pageId = target.first, x = target.second % cols, y = if (target.first == -1) 0 else target.second / cols)
                val hit = p.placements.firstOrNull { it.cellId != id && it.pageId == candidate.pageId && CellEditor.slots(it, cols).any(CellEditor.slots(candidate, cols)::contains) }
                if (hit != null) collision = id to hit.cellId else latestSave(CellEditor.place(s, p.id, candidate))
            }
        }
        dragId = null
    }

    BackHandler(drawer || settings || menuId != null || folderId != null || addAt != null || editing) {
        drawer = false; settings = false; menuId = null; folderId = null; addAt = null; editing = false
    }
    Box(Modifier.fillMaxSize().background(color(spec.style.background))) {
        wallpaper?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = if (spec.style.wallpaperCrop == "fit") androidx.compose.ui.layout.ContentScale.Fit else androidx.compose.ui.layout.ContentScale.Crop) }
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(spec.name.uppercase(), style = MaterialTheme.typography.titleLarge, color = color(spec.style.accent))
                Row {
                    TextButton({ editing = !editing }, Modifier.semantics { contentDescription = "배치 편집" }) { Text(if (editing) "완료" else "편집") }
                    TextButton({ settings = true }, Modifier.semantics { contentDescription = "런처 설정" }) { Text("설정") }
                }
            }
            if (!home) TextButton(onSetHome) { Text("기본 홈으로 설정") }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val width = maxWidth.value.toInt().coerceAtLeast(1)
                val height = maxHeight.value.toInt().coerceAtLeast(1)
                val orientation = if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
                val id = "${if (configuration.smallestScreenWidthDp >= 600 || width >= 600) "expanded" else "compact"}-$orientation-$posture"
                val currentDevice = DeviceProfile(id, width, height, density.density, (density.density * 160).roundToInt(), density.fontScale, posture)
                LaunchedEffect(id, width, height, density.fontScale, profile == null) {
                    device = currentDevice; profileId = id; bounds.clear(); cellBounds.clear()
                    val grid=latestWorkspace.profiles.find { it.id==id }?.grid ?: GridPolicy.recommend(currentDevice)
                    val rules=latestWorkspace.cells.mapNotNull { c -> c.appWidgetId?.let { binding -> widgetHost?.info(binding) }?.let { info ->
                        CellConstraint(c.id,widgetHost!!.sizes(info,width.toFloat()/grid.columns,height.toFloat()/grid.rows,grid,spec.style.spacing.toFloat()))
                    } }
                    runCatching { CellEditor.ensureProfile(latestWorkspace, currentDevice, grid, rules) }.onSuccess { if (it != latestWorkspace) latestSave(it) }.onFailure { message = it.message }
                }
                if (profile != null) {
                    slotWidth = maxWidth.value / profile.grid.columns
                    slotHeight = maxHeight.value / profile.grid.rows
                    LaunchedEffect(profileId, profile.grid, width, height, workspace.cells.map { it.id to it.appWidgetId }) {
                        if (widgetHost != null) {
                            val rules = workspace.cells.filter { it.kind == "widget" }.mapNotNull { c ->
                                c.appWidgetId?.let(widgetHost::info)?.let { info -> CellConstraint(c.id, widgetHost.sizes(info, slotWidth, slotHeight, profile.grid, spec.style.spacing.toFloat())) }
                                    ?: profile.constraints.find { it.cellId == c.id }
                            }
                            if (rules != profile.constraints) runCatching { CellEditor.regrid(latestWorkspace, id, profile.grid, rules) }.onSuccess(latestSave).onFailure { message = it.message }
                        }
                    }
                    // Window folding bounds are in root pixels. Block every intersecting Slot.
                    var gridBounds by remember { mutableStateOf(Rect.Zero) }
                    val blocked = remember(hinge, gridBounds, profile.grid.columns, profile.grid.rows) {
                        if (hinge == null || gridBounds == Rect.Zero) emptyList() else (0 until profile.grid.columns * profile.grid.rows).filter { i ->
                            val w = gridBounds.width / profile.grid.columns; val h = gridBounds.height / profile.grid.rows
                            Rect(gridBounds.left + i % profile.grid.columns * w, gridBounds.top + i / profile.grid.columns * h,
                                gridBounds.left + (i % profile.grid.columns + 1) * w, gridBounds.top + (i / profile.grid.columns + 1) * h).overlaps(hinge)
                        }
                    }
                    LaunchedEffect(blocked, profileId) {
                        if (profile.grid.blocked != blocked) runCatching { CellEditor.regrid(latestWorkspace, id, profile.grid.copy(blocked = blocked)) }.onSuccess(latestSave).onFailure { message = it.message }
                    }
                    HorizontalPager(pager, Modifier.fillMaxSize().onGloballyPositioned { gridBounds = it.boundsInRoot() }, userScrollEnabled = dragId == null && resizing == null && !editing) { page ->
                        Box(Modifier.fillMaxSize().graphicsLayer {
                            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                            if (!motionOff) when (spec.style.pageTransition) {
                                "fade" -> alpha = 1f - abs(offset) * .6f
                                "depth" -> { rotationY = -offset * 18; scaleX = 1 - abs(offset) * .08f; scaleY = scaleX; cameraDistance = 12 * density.density }
                            }
                        }) {
                            repeat(profile.grid.rows * profile.grid.columns) { i ->
                                val blockedSlot = i in profile.grid.blocked
                                Box(Modifier.offset((slotWidth * (i % profile.grid.columns)).dp, (slotHeight * (i / profile.grid.columns)).dp)
                                    .size(slotWidth.dp, slotHeight.dp).onGloballyPositioned { bounds[page to i] = it.boundsInRoot() }
                                    .padding(3.dp).then(if (!blockedSlot) Modifier.clickable { addAt = CellPlacement("", page, i % profile.grid.columns, i / profile.grid.columns) } else Modifier)
                                    .semantics { contentDescription = if (blockedSlot) "경첩 영역" else "셀 ${i + 1} 앱 추가" }, contentAlignment = Alignment.Center) {
                                    if (editing && !blockedSlot) Text("+", color = color(spec.style.foreground).copy(alpha = .25f))
                                }
                            }
                            profile.placements.filter { it.pageId == page }.forEach { placement ->
                                val cell = byId[placement.cellId] ?: return@forEach
                                key(cell.id) {
                                    val active = CellEditor.active(workspace, profileId, cell.id) ?: cell
                                    val base = Modifier.offset((placement.x * slotWidth).dp, (placement.y * slotHeight).dp).size((placement.spanX * slotWidth).dp, (placement.spanY * slotHeight).dp)
                                        .onGloballyPositioned { cellBounds[cell.id] = it.boundsInRoot() }
                                    val dragModifier = if (editing) Modifier.pointerInput(cell.id) {
                                        detectDragGesturesAfterLongPress(onDragStart = { dragId = cell.id; dragPoint = (cellBounds[cell.id]?.topLeft ?: Offset.Zero) + it },
                                            onDrag = { change, amount -> change.consume(); dragPoint += amount }, onDragEnd = ::finishDrag, onDragCancel = { dragId = null })
                                    } else Modifier
                                    Box(base.then(dragModifier).padding((spec.style.spacing / 2f).dp)) {
                                        CellContent(active, spec, appMap, widgetHost, editing, Modifier.fillMaxSize(),
                                            { if (editing) menuId = cell.id else if (active.kind == "folder") folderId = active.id else active.app?.let(onLaunch) },
                                            { menuId = cell.id })
                                        if (cell.kind == "stack") {
                                            val swipe = Modifier.pointerInput(cell.id) {
                                                var total = 0f
                                                detectVerticalDragGestures(onDragStart = { total = 0f }, onVerticalDrag = { change, amount -> change.consume(); total += amount },
                                                    onDragEnd = { if (abs(total) > 36 * density.density) latestSave(CellEditor.cycle(latestWorkspace, profileId, cell.id, if (total < 0) 1 else -1)) })
                                            }
                                            // Hosted widgets keep their own touch stream except this explicit edge rail.
                                            if (!editing) Box((if (active.kind == "widget") Modifier.align(Alignment.CenterEnd).width(24.dp).fillMaxHeight()
                                                .background(color(spec.style.surface).copy(alpha = .55f)).combinedClickable(onClick = { onWorkspace(CellEditor.cycle(workspace, profileId, cell.id, 1)) }, onLongClick = { menuId = cell.id })
                                                .semantics { contentDescription = "Stack 다음" }
                                            else Modifier.fillMaxSize().combinedClickable(
                                                onClick = { if (active.kind == "folder") folderId = active.id else active.app?.let(onLaunch) }, onLongClick = { menuId = cell.id })).then(swipe), contentAlignment = Alignment.Center) { if (active.kind == "widget") Text("↕", fontSize = 12.sp) }
                                            if (active.kind != "widget") Row(Modifier.align(Alignment.BottomCenter).background(color(spec.style.surface).copy(alpha = .9f))) {
                                                TextButton({ onWorkspace(CellEditor.cycle(workspace, profileId, cell.id, -1)) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.sizeIn(minWidth = 32.dp, minHeight = 32.dp).semantics { contentDescription = "Stack 이전" }) { Text("↓") }
                                                TextButton({ onWorkspace(CellEditor.cycle(workspace, profileId, cell.id, 1)) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.sizeIn(minWidth = 32.dp, minHeight = 32.dp).semantics { contentDescription = "Stack 다음" }) { Text("↑") }
                                            }
                                        }
                                        if (editing && cell.kind != "app") Box(Modifier.align(Alignment.BottomEnd).size(32.dp).background(color(spec.style.accent)).pointerInput(cell.id, placement) {
                                            var delta = Offset.Zero
                                            detectDragGestures(onDragStart = { delta = Offset.Zero; resizing = placement }, onDrag = { change, amount ->
                                                change.consume(); delta += amount
                                                resizing = placement.copy(spanX = (placement.spanX + delta.x / (slotWidth * density.density)).roundToInt().coerceAtLeast(1), spanY = (placement.spanY + delta.y / (slotHeight * density.density)).roundToInt().coerceAtLeast(1))
                                            }, onDragCancel = { resizing = null }, onDragEnd = { resizing?.let { latestSave(CellEditor.place(latestWorkspace, profileId, it)) }; resizing = null })
                                        }.clickable { menuId = cell.id }.semantics { contentDescription = "크기 조절" }, contentAlignment = Alignment.Center) { Text("↘", color = color(spec.style.background)) }
                                    }
                                }
                            }
                            val preview = resizing ?: dragId?.let { drag ->
                                val hit = bounds.entries.firstOrNull { it.key.first == page && it.value.contains(dragPoint) }?.key?.second
                                profile.placements.find { it.cellId == drag }?.let { old -> hit?.let { old.copy(x = it % profile.grid.columns, y = it / profile.grid.columns) } }
                            }
                            preview?.takeIf { it.pageId == page }?.let { p -> Box(Modifier.offset((p.x * slotWidth).dp, (p.y * slotHeight).dp).size((p.spanX * slotWidth).dp, (p.spanY * slotHeight).dp)
                                .border(3.dp, if (CellEditor.fits(profile, p, workspace.dockCapacity) && byId[p.cellId]?.let { CellEditor.allowed(workspace, it, CellSize(p.spanX, p.spanY), profile) } == true) color(spec.style.accent) else androidx.compose.ui.graphics.Color.Red)) }
                        }
                    }
                }
            }
            if (profile?.unplaced?.isNotEmpty()==true) TextButton({ settings=true }) { Text("이 화면에 맞지 않는 Cell ${profile.unplaced.size}개 · 보류 목록") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(profile?.pageCount ?: 1) { page -> TextButton({ scope.launch { if (motionOff) pager.scrollToPage(page) else pager.animateScrollToPage(page) } }, contentPadding = PaddingValues(0.dp), modifier = Modifier.widthIn(min = 28.dp).semantics { contentDescription = "페이지 ${page + 1}" }) { Text(if (pager.currentPage == page) "●" else "○") } }
            }
            Row(Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(spec.style.cornerRadius.dp)).background(color(spec.style.surface))) {
                repeat(workspace.dockCapacity) { i ->
                    val p = profile?.placements?.find { it.pageId == -1 && it.x == i }
                    val c = byId[p?.cellId]
                    Box(Modifier.weight(1f).fillMaxHeight().onGloballyPositioned { bounds[-1 to i] = it.boundsInRoot() }
                        .then(if (editing && c != null) Modifier.pointerInput(c.id) { detectDragGesturesAfterLongPress(
                            onDragStart = { dragId = c.id; dragPoint = (bounds[-1 to i]?.topLeft ?: Offset.Zero) + it }, onDrag = { change, amount -> change.consume(); dragPoint += amount }, onDragEnd = ::finishDrag, onDragCancel = { dragId = null }) } else Modifier), contentAlignment = Alignment.Center) {
                        if (c == null) TextButton({ addAt = CellPlacement("", -1, i) }, Modifier.semantics { contentDescription = "Dock ${i + 1} 앱 추가" }) { Text("+") }
                        else CellContent(c, spec, appMap, widgetHost, editing, Modifier.fillMaxSize(), { if (editing) menuId = c.id else if (c.kind == "folder") folderId = c.id else c.app?.let(onLaunch) }, { menuId = c.id })
                    }
                }
            }
            TextButton({ query = ""; drawer = true }, Modifier.fillMaxWidth().semantics { contentDescription = "앱 서랍 열기" }) { Text("모든 앱  ↑") }
        }
    }
    if (drawer || addAt != null) AtlasDialog(if (addAt != null) "Cell 추가" else "모든 앱", { drawer = false; addAt = null }) {
        if (addAt != null && addAt?.pageId != -1) Row {
            TextButton({ addKind = "app" }) { Text("앱") }
            TextButton({ addKind = "widget" }) { Text("위젯") }
        }
        if (addKind == "widget" && addAt?.pageId != -1 && addAt != null) {
            Row { listOf("clock" to "시계", "date" to "날짜", "battery" to "배터리").forEach { (kind, label) -> TextButton({ newCell(Cell(UUID.randomUUID().toString(), "builtin", widget = kind, label = label), if (kind == "clock") CellSize(2,1) else CellSize()) }) { Text(label) } } }
            widgetHost?.providers()?.forEach { info ->
                val sizes = profile?.let { widgetHost.sizes(info, slotWidth, slotHeight, it.grid, spec.style.spacing.toFloat()) } ?: emptyList()
                TextButton(enabled = sizes.isNotEmpty(), onClick = {
                    val p = profile ?: return@TextButton
                    val desired = sizes.sortedBy { it.x * it.y }.firstNotNullOfOrNull { size ->
                        val at = addAt?.copy(cellId = UUID.randomUUID().toString(), spanX = size.x, spanY = size.y)
                        at?.takeIf { CellEditor.fits(p, it, workspace.dockCapacity) } ?: CellEditor.free(p, UUID.randomUUID().toString(), size, workspace.dockCapacity, pager.currentPage)
                    }
                    if (desired == null) message = "위젯을 놓을 빈 공간이 없습니다" else { onWidget(info, profileId, desired, sizes); addAt = null }
                }) { Text("위젯 · ${info.loadLabel(context.packageManager)}") }
            }
        }
        if (addAt == null || addKind == "app" || addAt?.pageId == -1) {
        OutlinedTextField(query, { query = it }, label = { Text("앱 검색") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "앱 검색" }, singleLine = true)
        apps.filter { it.label.contains(query, true) || it.ref.component.contains(query, true) }.forEach { app ->
            Row(Modifier.fillMaxWidth().combinedClickable(onClick = { if (addAt != null) newCell(Cell(UUID.randomUUID().toString(), app = app.ref)) else onLaunch(app.ref); drawer = false },
                onLongClick = { newCell(Cell(UUID.randomUUID().toString(), app = app.ref)); drawer = false }).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppPicture(app.ref, app, spec, Modifier.size(40.dp)); Spacer(Modifier.width(12.dp)); Text(app.label)
            }
        }
        }
    }
    if (selected != null && selectedPlacement != null && profile != null) AtlasDialog("Cell 편집", { menuId = null }) {
        Text(cellLabel(selected, appMap))
        if (selected.kind == "folder") {
            var name by remember(selected.id) { mutableStateOf(selected.label) }
            OutlinedTextField(name, { name = it }, label = { Text("폴더 이름") })
            TextButton({ onWorkspace(CellEditor.rename(workspace, selected.id, name)) }) { Text("이름 저장") }
        }
        if (selected.kind != "app" && selectedPlacement.pageId >= 0) {
            Text("크기 ${selectedPlacement.spanX} × ${selectedPlacement.spanY} · 빈 Slot에만 적용")
            (1..profile.grid.rows).forEach { y -> Row { (1..profile.grid.columns).forEach { x ->
                val next = selectedPlacement.copy(spanX = x, spanY = y)
                TextButton(enabled = CellEditor.allowed(workspace, selected, CellSize(x,y), profile) && CellEditor.fits(profile, next, workspace.dockCapacity), onClick = { commit(CellEditor.place(workspace, profileId, next)) }, contentPadding = PaddingValues(2.dp), modifier = Modifier.weight(1f)) { Text("$x×$y", fontSize = 12.sp) }
            } } }
        }
        (-1 until profile.pageCount).forEach { page ->
            val destination = CellEditor.free(profile, selected.id, CellSize(selectedPlacement.spanX,selectedPlacement.spanY), workspace.dockCapacity, page)
            if (destination != null && (page >= 0 || selected.kind in listOf("app", "folder"))) TextButton({ commit(CellEditor.place(workspace, profileId, destination)); menuId = null }) { Text(if (page == -1) "Dock으로 이동" else "페이지 ${page + 1} 빈 자리로 이동") }
        }
        if (selected.kind == "stack") selected.members.forEach { id ->
            val member = byId[id]
            Text(member?.let { cellLabel(it, appMap) } ?: id)
            if (member?.kind == "widget" && (member.appWidgetId == null || widgetHost?.info(member.appWidgetId) == null)) {
                val provider = widgetHost?.providers()?.find { canonicalComponent(it.provider.flattenToString()) == canonicalComponent(member.provider) }
                TextButton(enabled = provider != null, onClick = {
                    if (provider != null && widgetHost != null) {
                        val sizes = widgetHost.sizes(provider,slotWidth,slotHeight,profile.grid,spec.style.spacing.toFloat())
                        if (CellSize(selectedPlacement.spanX,selectedPlacement.spanY) !in sizes) message = "위젯 허용 크기에 맞게 Stack 전체 크기를 조절하세요"
                        else { onWidget(provider,profileId,selectedPlacement.copy(cellId=id),sizes); menuId=null }
                    }
                }) { Text("구성원 위젯 연결 승인") }
            }
            Row {
                TextButton({ commit(CellEditor.reorder(workspace, selected.id, id, -1)) }) { Text("순서 ↑") }
                TextButton({ commit(CellEditor.reorder(workspace, selected.id, id, 1)) }) { Text("순서 ↓") }
                TextButton({ commit(CellEditor.extract(workspace, selected.id, id)); menuId = null }) { Text("꺼내기") }
            }
        }
        if (selected.kind == "stack") Row {
            TextButton({ onWorkspace(CellEditor.cycle(workspace, profileId, selected.id, -1)) }) { Text("Stack 이전") }
            TextButton({ onWorkspace(CellEditor.cycle(workspace, profileId, selected.id, 1)) }) { Text("Stack 다음") }
        }
        profile.placements.filter { it.cellId != selected.id }.forEach { target ->
            val c = byId[target.cellId] ?: return@forEach
            if (selected.kind == "app" && c.kind in listOf("app", "folder")) TextButton({ commit(CellEditor.folder(workspace, profileId, selected.id, c.id)); menuId = null }) { Text("폴더로 묶기 · ${cellLabel(c, appMap)}") }
            if (selected.kind != "stack" && target.pageId >= 0 && selectedPlacement.pageId >= 0 && target.spanX == selectedPlacement.spanX && target.spanY == selectedPlacement.spanY) TextButton({ commit(CellEditor.stack(workspace, profileId, selected.id, c.id)); menuId = null }) { Text("Stack에 쌓기 · ${cellLabel(c, appMap)}") }
        }
        if (selected.kind == "widget" && (selected.appWidgetId == null || widgetHost?.info(selected.appWidgetId) == null)) {
            val provider = widgetHost?.providers()?.find { canonicalComponent(it.provider.flattenToString()) == canonicalComponent(selected.provider) }
            TextButton(enabled = provider != null, onClick = {
                if (provider != null && widgetHost != null) {
                    val sizes = widgetHost.sizes(provider, slotWidth, slotHeight, profile.grid, spec.style.spacing.toFloat())
                    val destination = sizes.sortedBy { abs(it.x-selectedPlacement.spanX)+abs(it.y-selectedPlacement.spanY) }.firstNotNullOfOrNull { size ->
                        selectedPlacement.copy(spanX=size.x,spanY=size.y).takeIf { CellEditor.fits(profile,it,workspace.dockCapacity) }
                    }
                    if (destination == null) message = "이 위젯이 지원하는 크기의 빈 공간이 없습니다"
                    else { onWidget(provider,profileId,destination,sizes); menuId=null }
                }
            }) { Text(if (provider == null) "Provider 앱 설치 후 연결 가능" else "위젯 연결 승인") }
        }
        Text("Cell 제거는 모든 화면에 적용됩니다. 설치된 앱은 삭제되지 않습니다.", fontSize=12.sp)
        TextButton({ onWorkspace(CellEditor.remove(workspace, selected.id)); menuId = null }) { Text("모든 화면에서 Cell 제거") }
    }
    if (folder != null) AtlasDialog(folder.label, { folderId = null }) {
        folder.children.forEach { ref ->
            TextButton({ onLaunch(ref) }) { Text(appMap[ref]?.label ?: ref.component.substringBefore('/')) }
            Row {
                TextButton({ commit(CellEditor.extractApp(workspace, profileId, folder.id, ref, pager.currentPage)) }) { Text("홈으로 꺼내기") }
                TextButton({ commit(CellEditor.removeChild(workspace, folder.id, ref)) }) { Text("폴더에서 제거") }
            }
        }
    }
    collision?.let { (source, target) -> AtlasDialog("겹친 Cell과의 동작", { collision = null }) {
        val a = byId[source]; val b = byId[target]
        if (a?.kind == "app" && b?.kind in listOf("app", "folder")) TextButton({ commit(CellEditor.folder(workspace, profileId, source, target)); collision = null }) { Text("폴더로 묶기") }
        TextButton({ commit(CellEditor.stack(workspace, profileId, source, target)); collision = null }) { Text("같은 크기로 Stack 만들기") }
        Text("닫으면 기존 배치를 유지합니다")
    } }
    if (settings) AtlasDialog("${spec.name} 설정", { settings = false }) {
        Text("${spec.versionName} · ${device?.densityDpi ?: 0} DPI · $profileId")
        TextButton(onSetHome) { Text("시스템에서 기본 홈 선택") }
        device?.let { d -> GridPolicy.presets.forEach { grid -> TextButton(enabled = GridPolicy.available(d, grid), onClick = {
            runCatching { CellEditor.regrid(workspace, profileId, grid) }.onSuccess(onWorkspace).onFailure { message = it.message }
        }) { Text("Grid ${grid.columns} × ${grid.rows}") } } }
        profile?.let { p ->
            if (p.unplaced.isNotEmpty()) {
                Text("보류 Cell: provider 크기가 화면에 맞지 않습니다. 다른 Grid나 넓은 화면에서 다시 배치할 수 있습니다.")
                p.unplaced.forEach { pending -> TextButton({ menuId=pending.cellId; settings=false }) { Text(byId[pending.cellId]?.let { cellLabel(it,appMap) } ?: pending.cellId) } }
                TextButton({ onWorkspace(CellEditor.regrid(workspace,profileId,p.grid)) }) { Text("보류 Cell 다시 배치") }
            }
            TextButton({ if (p.pageCount < 9) onWorkspace(workspace.copy(profiles = workspace.profiles.map { if (it.id == p.id) it.copy(pageCount = it.pageCount + 1) else it })) }) { Text("페이지 추가") }
            TextButton(enabled = p.pageCount > 1 && p.placements.none { it.pageId == p.pageCount - 1 }, onClick = { onWorkspace(workspace.copy(profiles = workspace.profiles.map { if (it.id == p.id) it.copy(pageCount = it.pageCount - 1) else it })) }) { Text("마지막 빈 페이지 삭제") }
        }
        TextButton({ onWallpaper(false) }) { Text("홈 배경 적용") }
        TextButton({ onWallpaper(true) }) { Text("잠금 배경 적용") }
        TextButton({ reset = true }) { Text("레이아웃 초기화…") }
        if (widgetHost?.pending != null) TextButton({ widgetHost.cancel(); message = "완료되지 않은 위젯 연결을 취소했습니다" }) { Text("대기 중 위젯 연결 취소") }
        Text("화면별 Grid와 위치를 따로 저장합니다. Stack은 상하 Swipe 또는 ↑↓ 버튼으로 전환합니다.")
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("배치를 초기화할까요?") }, text = { Text("모든 화면의 사용자 배치와 위젯 연결이 기본 SPEC으로 바뀝니다.") }, confirmButton = { TextButton({ onReset(); reset = false; settings = false }) { Text("초기화") } }, dismissButton = { TextButton({ reset = false }) { Text("취소") } })
    message?.let { AlertDialog(onDismissRequest = { message = null }, text = { Text(it) }, confirmButton = { TextButton({ message = null }) { Text("확인") } }) }
}

private fun cellLabel(c: Cell, apps: Map<AppRef, AppEntry>): String = when (c.kind) {
    "app" -> apps[c.app]?.label ?: "미설치 앱"
    "folder" -> c.label.ifBlank { "폴더" }
    "builtin" -> c.label.ifBlank { c.widget }
    "stack" -> "Stack (${c.members.size})"
    else -> "위젯 · ${c.provider.substringBefore('/')}"
}

@Composable private fun CellContent(c: Cell, spec: LauncherSpec, apps: Map<AppRef, AppEntry>, host: AtlasWidgetHost?, editing: Boolean, modifier: Modifier, click: () -> Unit, longClick: () -> Unit) {
    val label = cellLabel(c, apps)
    BoxWithConstraints(modifier.clip(RoundedCornerShape(spec.style.cornerRadius.dp)).then(
        if (c.kind != "widget" || editing || c.appWidgetId == null || host?.info(c.appWidgetId) == null) Modifier.combinedClickable(onClick = if (c.kind == "widget" && !editing) longClick else click, onLongClick = if (editing) null else longClick).semantics { contentDescription = label } else Modifier)) {
        val contentWidth = maxWidth
        val contentHeight = maxHeight
        val iconSize = minOf(spec.style.iconSize.dp, contentWidth - 8.dp, contentHeight - if (spec.style.showLabels) 26.dp else 8.dp).coerceAtLeast(20.dp)
        when (c.kind) {
            "app" -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                c.app?.let { AppPicture(it, apps[it], spec, Modifier.size(iconSize)) }
                if (spec.style.showLabels) Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            "folder" -> Column(Modifier.fillMaxSize().background(color(spec.style.surface)).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val columns = if (contentWidth < 100.dp) 2 else (contentWidth.value / 56).toInt().coerceIn(2, 6)
                val rows = if (contentHeight < 100.dp) 2 else ((contentHeight.value - 24) / 52).toInt().coerceAtLeast(1)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                    c.children.take(columns * rows).chunked(columns).forEach { refs -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        refs.forEach { ref -> AppPicture(ref, apps[ref], spec, Modifier.size(minOf(spec.style.iconSize.dp, ((contentWidth.value - 20) / columns - 4).dp, ((contentHeight.value - 32) / rows).dp).coerceAtLeast(12.dp))) }
                    } }
                }
                Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            "builtin" -> Box(Modifier.fillMaxSize().background(color(spec.style.surface)).padding(8.dp), contentAlignment = Alignment.Center) {
                BuiltinWidget(c.widget, contentWidth.value, contentHeight.value)
            }
            "widget" -> {
                val id = c.appWidgetId
                if (id != null && host?.info(id) != null) key(id) {
                    AndroidView(factory = { ctx -> host.createView(ctx, id) ?: android.widget.TextView(ctx).apply { text = "위젯을 불러올 수 없습니다" } }, modifier = Modifier.fillMaxSize(),
                        update = { view -> if (view is android.appwidget.AppWidgetHostView) host.updateSize(view, contentWidth.value, contentHeight.value) },
                        onRelease = { view -> (view.parent as? android.view.ViewGroup)?.removeView(view) })
                } else Text("위젯 연결 필요\n${c.provider.substringBefore('/')}", Modifier.align(Alignment.Center).padding(8.dp), fontSize = 12.sp)
                if (editing) Box(Modifier.fillMaxSize().background(color(spec.style.accent).copy(alpha = .12f)).clickable(onClick = click))
            }
        }
    }
}
