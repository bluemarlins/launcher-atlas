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
