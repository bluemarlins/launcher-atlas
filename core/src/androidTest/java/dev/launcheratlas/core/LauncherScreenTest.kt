package dev.launcheratlas.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LauncherScreenTest {
    @get:Rule val compose = createComposeRule()
    private val spec = LauncherSpec(id = "paper", name = "Paper", applicationId = "dev.launcheratlas.paper")
    private val apps = listOf(AppEntry(AppRef("com.camera/.Main", 0), "Camera"), AppEntry(AppRef("com.notes/.Main", 0), "Notes"))

    @Test fun longPressSelectsWithoutOpeningNumericMenuAndLeftHandleCommitsOnce() {
        val cell=Cell("f","folder",label="Tools",children=listOf(apps[0].ref))
        var workspace by mutableStateOf(CellWorkspace(cells=listOf(cell),profiles=listOf(ProfileLayout("seed",placements=listOf(CellPlacement("f",x=1,y=1,spanX=2,spanY=2))))))
        var saves=0
        compose.setContent { AtlasTheme(StyleSheet()) { LauncherScreen(spec,workspace,apps,true,{}, {}, { workspace=it;saves++ }, {}, {}) } }
        compose.onNodeWithContentDescription("Tools").performTouchInput { longClick() }
        compose.onNodeWithTag("cell-selection").assertExists()
        compose.onNodeWithText("Cell 편집").assertDoesNotExist()
        val slot=compose.onNodeWithTag("cell-selection").fetchSemanticsNode().boundsInRoot.width/2
        compose.runOnIdle { saves=0 }
        compose.onNodeWithTag("resize-left").performTouchInput { down(center);moveTo(center+androidx.compose.ui.geometry.Offset(-slot,0f));up() }
        compose.runOnIdle {
            val placement=workspace.profiles.first { it.id==workspace.activeProfileId }.placements.single()
            assertEquals(0,placement.x);assertEquals(3,placement.spanX);assertEquals(1,saves)
        }
    }
    @Test fun selectedAppCannotLaunchAndHasNoResizeHandles() {
        var launched=false
        var workspace by mutableStateOf(CellWorkspace(cells=listOf(Cell("a",app=apps[0].ref)),profiles=listOf(ProfileLayout("seed",placements=listOf(CellPlacement("a"))))))
        compose.setContent { AtlasTheme(StyleSheet()) { LauncherScreen(spec,workspace,apps,true,{}, { launched=true }, { workspace=it }, {}, {}) } }
        compose.onNodeWithContentDescription("Camera").performTouchInput { longClick() }
        compose.onNodeWithTag("cell-selection").assertExists()
        compose.onNodeWithTag("resize-right").assertDoesNotExist()
        compose.onNodeWithContentDescription("Camera").performClick()
        compose.runOnIdle { assertFalse(launched) }
    }
    @Test fun invalidResizeAndPointerCancellationKeepPlacementAndDoNotSave() {
        val folder=Cell("f","folder",label="Tools",children=listOf(apps[0].ref))
        val original=CellPlacement("f",x=1,y=1,spanX=2,spanY=2)
        var workspace by mutableStateOf(CellWorkspace(cells=listOf(folder,Cell("a",app=apps[1].ref)),profiles=listOf(ProfileLayout("seed",placements=listOf(original,CellPlacement("a",x=3,y=1))))))
        var saves=0
        compose.setContent { AtlasTheme(StyleSheet()) { LauncherScreen(spec,workspace,apps,true,{}, {}, {workspace=it;saves++}, {}, {}) } }
        compose.onNodeWithContentDescription("Tools").performTouchInput {longClick()}
        val slot=compose.onNodeWithTag("cell-selection").fetchSemanticsNode().boundsInRoot.width/2
        compose.runOnIdle {saves=0}
        compose.onNodeWithTag("resize-right").performTouchInput {down(center);moveTo(center+androidx.compose.ui.geometry.Offset(slot,0f));up()}
        compose.runOnIdle {assertEquals(original,workspace.profiles.last().placements.first {it.cellId=="f"});assertEquals(0,saves)}
        compose.onNodeWithTag("resize-left").performTouchInput {down(center);moveTo(center-androidx.compose.ui.geometry.Offset(slot,0f));cancel()}
        compose.runOnIdle {assertEquals(original,workspace.profiles.last().placements.first {it.cellId=="f"});assertEquals(0,saves)}
        compose.onNodeWithText("완료").performClick()
        compose.onNodeWithTag("cell-selection").assertDoesNotExist()
    }
    @Test fun cancellingCellBodyMoveNeverCommitsDestination() {
        var workspace by mutableStateOf(CellWorkspace(cells=listOf(Cell("a",app=apps[0].ref)),profiles=listOf(ProfileLayout("seed",placements=listOf(CellPlacement("a"))))))
        var saves=0
        compose.setContent { AtlasTheme(StyleSheet()) {LauncherScreen(spec,workspace,apps,true,{}, {}, {workspace=it;saves++}, {}, {})} }
        compose.onNodeWithContentDescription("Camera").performTouchInput {longClick()}
        val source=compose.onNodeWithContentDescription("Camera")
        val bounds=source.fetchSemanticsNode().boundsInRoot
        val dock=compose.onNodeWithContentDescription("Dock 1 앱 추가").fetchSemanticsNode().boundsInRoot.center
        compose.runOnIdle {saves=0}
        source.performTouchInput {down(center);moveTo(dock-bounds.topLeft);cancel()}
        compose.runOnIdle {assertEquals(0,workspace.profiles.last().placements.single().pageId);assertEquals(0,saves)}
    }
    @Test fun outOfBoundsBodyDropDoesNotWriteWorkspace() {
        val folder=Cell("f","folder",label="Tools",children=listOf(apps[0].ref))
        val original=CellPlacement("f",spanX=2,spanY=2)
        var workspace by mutableStateOf(CellWorkspace(cells=listOf(folder),profiles=listOf(ProfileLayout("seed",placements=listOf(original)))))
        var saves=0
        compose.setContent {AtlasTheme(StyleSheet()) {LauncherScreen(spec,workspace,apps,true,{}, {}, {workspace=it;saves++}, {}, {})}}
        compose.onNodeWithContentDescription("Tools").performTouchInput {longClick()}
        val source=compose.onNodeWithContentDescription("Tools")
        val origin=source.fetchSemanticsNode().boundsInRoot.topLeft
        val lastColumn=compose.onNodeWithContentDescription("셀 4 앱 추가").fetchSemanticsNode().boundsInRoot.center
        compose.runOnIdle {saves=0}
        source.performTouchInput {down(androidx.compose.ui.geometry.Offset(16f,16f));moveTo(lastColumn-origin);up()}
        compose.runOnIdle {assertEquals(original,workspace.profiles.last().placements.single());assertEquals(0,saves)}
    }

    @Test fun drawerFiltersRealCatalogAndLaunchesSelectedComponent() {
        var launched: AppRef? = null
        compose.setContent { MaterialTheme { LauncherScreen(spec, CellWorkspace(), apps, false, {}, { launched = it }, {}, {}, {}) } }
        compose.onNodeWithContentDescription("앱 서랍 열기").performClick()
        compose.onNodeWithContentDescription("앱 검색").performTextInput("Notes")
        compose.onNodeWithText("Camera").assertDoesNotExist()
        compose.onNode(hasText("Notes") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.runOnIdle { assertEquals("com.notes/.Main", launched?.component) }
    }

    @Test fun addAppToEmptyHomePersistsComponentThroughCallback() {
        var saved: CellWorkspace? = null
        var workspace by mutableStateOf(CellWorkspace())
        compose.setContent { MaterialTheme { LauncherScreen(spec, workspace, apps, true, {}, {}, { saved = it; workspace = it }, {}, {}) } }
        compose.onNodeWithContentDescription("셀 1 앱 추가").performClick()
        compose.onNodeWithText("Camera").performClick()
        compose.runOnIdle { assertEquals("com.camera/.Main", saved?.cells?.single()?.app?.component) }
    }

    @Test fun stackButtonsSwitchAndKeepAllMembers() {
        val cells = listOf(Cell("a", app = apps[0].ref), Cell("b", app = apps[1].ref), Cell("s", "stack", members = listOf("a", "b")))
        var workspace by mutableStateOf(CellWorkspace(cells = cells, profiles = listOf(ProfileLayout("seed", placements = listOf(CellPlacement("s"))))))
        compose.setContent { MaterialTheme { LauncherScreen(spec, workspace, apps, true, {}, {}, { workspace = it }, {}, {}) } }
        compose.onNodeWithContentDescription("Stack 다음").performClick()
        compose.runOnIdle { assertEquals(3, workspace.cells.size) }
        compose.onNodeWithContentDescription("Notes").assertExists()
    }

    @Test fun longPressDragUsesLatestCellWorkspace() {
        var workspace by mutableStateOf(CellWorkspace(cells=listOf(Cell("a",app=apps[0].ref)),profiles=listOf(ProfileLayout("seed",placements=listOf(CellPlacement("a"))))))
        compose.setContent { AtlasTheme(StyleSheet()) { LauncherScreen(spec,workspace,apps,true,{}, {}, { workspace=it }, {}, {}) } }
        compose.onNodeWithContentDescription("배치 편집").performClick()
        compose.onNodeWithContentDescription("Camera").performClick()
        compose.onNodeWithText("옵션").performClick()
        compose.onNodeWithText("닫기").performClick()
        compose.runOnIdle {
            val p=workspace.profiles.last()
            workspace=CellEditor.add(workspace,p.id,Cell("b",app=apps[1].ref),CellPlacement("b",x=1))
        }
        val source=compose.onNodeWithContentDescription("Camera")
        val bounds=source.fetchSemanticsNode().boundsInRoot
        val dock=compose.onNodeWithContentDescription("Dock 1 앱 추가").fetchSemanticsNode().boundsInRoot.center
        source.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(800)
        source.performTouchInput { moveTo(dock-bounds.topLeft);up() }
        compose.runOnIdle {
            assertEquals(2,workspace.cells.size)
            assertEquals(-1,workspace.profiles.last().placements.first { it.cellId=="a" }.pageId)
        }
    }
    @Test fun resetImmediatelyRecreatesCurrentProfileWithoutReenteringHome() {
        var workspace by mutableStateOf(CellWorkspace(cells=listOf(Cell("a",app=apps[0].ref)),profiles=listOf(ProfileLayout("seed",placements=listOf(CellPlacement("a"))))))
        compose.setContent { AtlasTheme(StyleSheet()) { LauncherScreen(spec,workspace,apps,true,{}, {}, { workspace=it }, {}, { workspace=spec.initialCells(0) }) } }
        compose.onNodeWithContentDescription("런처 설정").performClick()
        compose.onNodeWithText("레이아웃 초기화…").performScrollTo().performClick()
        compose.onNodeWithText("초기화",useUnmergedTree=true).performClick()
        compose.onNodeWithContentDescription("셀 1 앱 추가").assertExists()
    }
}
