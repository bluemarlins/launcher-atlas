package dev.launcheratlas.core

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkspaceTest {
    private val spec = LauncherSpec(id = "paper", name = "Paper", applicationId = "dev.launcheratlas.paper")
    private val ref = AppRef("com.example/.Main", 0)

    @Test fun defaultWorkspaceUsesSpecDimensions() {
        val state = WorkspaceEditor.initial(spec, 0)
        assertEquals(3, state.pageCount)
        assertEquals(4, state.columns)
        assertTrue(state.items.isEmpty())
    }

    @Test fun moveSwapsOccupiedCellsWithoutLosingItems() {
        val a = WorkspaceItem("a", app = ref, page = 0, cell = 0)
        val b = WorkspaceItem("b", app = ref.copy(component = "com.other/.Main"), page = -1, cell = 0)
        val state = WorkspaceState(items = listOf(a, b))
        val moved = WorkspaceEditor.move(state, "a", -1, 0)
        assertEquals(2, moved.items.size)
        assertEquals(-1, moved.items.first { it.id == "a" }.page)
        assertEquals(0, moved.items.first { it.id == "b" }.page)
    }

    @Test fun invalidMoveIsRejectedAndOriginalStateRetained() {
        val state = WorkspaceState(items = listOf(WorkspaceItem("a", app = ref)))
        assertEquals(state, WorkspaceEditor.move(state, "a", -1, 99))
        assertEquals(state, WorkspaceEditor.move(state, "a", 9, 0))
    }

    @Test fun folderCreationAndExtractionPreserveApps() {
        val state = WorkspaceState(items = listOf(WorkspaceItem("a", app = ref, cell = 0), WorkspaceItem("b", app = ref.copy(component = "com.other/.Main"), cell = 1)))
        val grouped = WorkspaceEditor.folder(state, "a", "b", "Tools")
        assertEquals(1, grouped.items.size)
        assertEquals(listOf("com.other/.Main", "com.example/.Main"), grouped.items.single().children.map { it.component })
        val extracted = WorkspaceEditor.extract(grouped, grouped.items.single().id, ref, 1, 0)
        assertEquals(2, extracted.items.size)
        assertEquals(1, extracted.items.first { it.app == ref }.page)
    }

    @Test fun failedExtractionDoesNotRemoveChild() {
        val folder = WorkspaceItem("f", kind = "folder", children = listOf(ref), cell = 0)
        val state = WorkspaceState(items = listOf(folder))
        assertEquals(state, WorkspaceEditor.extract(state, "f", ref, 0, 0))
    }

    @Test fun styleUpdateDoesNotReplaceSavedGridOrPlacements() {
        val existing = WorkspaceState(columns = 3, rows = 3, pageCount = 2, items = listOf(WorkspaceItem("a", app = ref, page = 1, cell = 8)))
        val updatedSpec = spec.copy(layout = LayoutSpec(columns = 6, initialPages = 1))
        assertEquals(existing, WorkspaceEditor.restore(updatedSpec, Json.encodeToString(existing), 0))
    }

    @Test fun removingNonEmptyPageIsRejected() {
        val state = WorkspaceState(items = listOf(WorkspaceItem("a", app = ref, page = 2)))
        assertEquals(state, WorkspaceEditor.removeLastPage(state))
        assertEquals(2, WorkspaceEditor.removeLastPage(WorkspaceState()).pageCount)
    }

    @Test fun addingToFullDockDoesNotDuplicateOrRemoveExistingItems() {
        val state = WorkspaceState(dockCapacity = 1, items = listOf(WorkspaceItem("a", app = ref, page = -1)))
        assertEquals(state, WorkspaceEditor.add(state, ref, -1, 0))
    }

    @Test fun corruptSavedStateFallsBackWithoutAcceptingUnsupportedVersions() {
        assertEquals(WorkspaceEditor.initial(spec, 0), WorkspaceEditor.restore(spec, "{broken", 0))
        assertEquals(WorkspaceEditor.initial(spec, 0), WorkspaceEditor.restore(spec, "{\"schemaVersion\":99}", 0))
    }

    @Test fun unknownCatalogAppsAreKeptInWorkspaceForReinstall() {
        val state = WorkspaceState(items = listOf(WorkspaceItem("a", app = ref)))
        assertEquals(state, WorkspaceEditor.restore(spec, Json.encodeToString(state), 0))
    }

    @Test fun fullComponentNamesMatchCatalogAndLegacyWorkspace() {
        assertEquals("com.example/.Main", canonicalComponent("com.example/com.example.Main"))
        val configured = spec.copy(layout = LayoutSpec(items = listOf(InitialItem("a", component = "com.example/com.example.Main"))))
        assertEquals(ref, WorkspaceEditor.initial(configured, 0).items.single().app)
        val saved = WorkspaceState(items = listOf(WorkspaceItem("a", app = AppRef("com.example/com.example.Main", 0))))
        assertEquals(ref, WorkspaceEditor.restore(spec, Json.encodeToString(saved), 0).items.single().app)
    }
}
