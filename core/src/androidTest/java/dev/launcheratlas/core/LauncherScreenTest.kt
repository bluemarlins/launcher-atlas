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
        compose.setContent { MaterialTheme { LauncherScreen(spec, WorkspaceState(), apps, false, {}, { launched = it }, {}, {}, {}) } }
        compose.onNodeWithContentDescription("앱 서랍 열기").performClick()
        compose.onNodeWithContentDescription("앱 검색").performTextInput("Notes")
        compose.onNodeWithText("Camera").assertDoesNotExist()
        compose.onNode(hasText("Notes") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.runOnIdle { assertEquals("com.notes/.Main", launched?.component) }
    }

    @Test fun addAppToEmptyHomePersistsComponentThroughCallback() {
        var saved: WorkspaceState? = null
        compose.setContent { MaterialTheme { LauncherScreen(spec, WorkspaceState(), apps, true, {}, {}, { saved = it }, {}, {}) } }
        compose.onNodeWithContentDescription("셀 1 앱 추가").performClick()
        compose.onNodeWithText("Camera").performClick()
        compose.runOnIdle { assertEquals("com.camera/.Main", saved?.items?.single()?.app?.component) }
    }

    @Test fun dragAfterAddingAnotherItemPreservesLatestWorkspace() {
        var workspace by mutableStateOf(WorkspaceState(items = listOf(WorkspaceItem("a", app = apps[0].ref))))
        compose.setContent { MaterialTheme { LauncherScreen(spec, workspace, apps, true, {}, {}, { workspace = it }, {}, {}) } }
        compose.onNodeWithContentDescription("배치 편집").performClick()
        // Start the pointer coroutine for A before a later workspace update.
        compose.onNodeWithContentDescription("Camera").performClick()
        compose.onNodeWithText("닫기").performClick()
        compose.runOnIdle { workspace = WorkspaceEditor.add(workspace, apps[1].ref, 0, 1) }
        val source = compose.onNodeWithContentDescription("Camera")
        val sourceBounds = source.fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithContentDescription("Dock 1 앱 추가").fetchSemanticsNode().boundsInRoot.center
        source.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(800)
        source.performTouchInput { moveTo(dock - sourceBounds.topLeft); up() }
        compose.runOnIdle {
            assertEquals(2, workspace.items.size)
            assertTrue(workspace.items.any { it.app == apps[1].ref })
            assertEquals(-1, workspace.items.first { it.id == "a" }.page)
        }
    }
}
