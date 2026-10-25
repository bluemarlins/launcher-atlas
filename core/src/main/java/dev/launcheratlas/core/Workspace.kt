package dev.launcheratlas.core

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable data class AppRef(val component: String, val userSerial: Long)
@Serializable data class WorkspaceItem(
    val id: String, val kind: String = "app", val app: AppRef? = null,
    val label: String = "", val children: List<AppRef> = emptyList(), val page: Int = 0, val cell: Int = 0,
)
@Serializable data class WorkspaceState(
    val schemaVersion: Int = 1, val columns: Int = 4, val rows: Int = 4,
    val pageCount: Int = 3, val dockCapacity: Int = 4, val items: List<WorkspaceItem> = emptyList(),
)

object WorkspaceEditor {
    fun initial(spec: LauncherSpec, serial: Long) = WorkspaceState(
        columns = spec.layout.columns, rows = spec.layout.rows, pageCount = spec.layout.initialPages,
        dockCapacity = spec.layout.dockCapacity,
        items = spec.layout.items.map { item -> WorkspaceItem(
            item.id, item.kind, if (item.kind == "app") AppRef(canonicalComponent(item.component), serial) else null,
            item.label, item.children.map { AppRef(canonicalComponent(it), serial) }, item.page, item.cell,
        ) },
    )

    private fun slot(state: WorkspaceState, page: Int, cell: Int) =
        page in -1 until state.pageCount && cell >= 0 && cell < if (page == -1) state.dockCapacity else state.columns * state.rows

    fun valid(state: WorkspaceState): Boolean = state.schemaVersion == 1 && state.columns in 3..6 &&
        state.rows in 2..6 && state.pageCount in 1..9 && state.dockCapacity in 1..6 &&
        state.items.map { it.id }.distinct().size == state.items.size &&
        state.items.map { it.page to it.cell }.distinct().size == state.items.size &&
        state.items.all { item -> slot(state, item.page, item.cell) && item.id.isNotBlank() && when(item.kind) {
            "app" -> item.app != null && item.app.component.contains('/') && item.children.isEmpty()
            "folder" -> item.app == null && item.children.isNotEmpty() && item.children.size <= 64 && item.children.distinct().size == item.children.size && item.children.all { it.component.contains('/') }
            else -> false
        } }

    fun restore(spec: LauncherSpec, saved: String?, serial: Long): WorkspaceState =
        saved?.let { runCatching { atlasJson.decodeFromString<WorkspaceState>(it) }.getOrNull() }?.takeIf(::valid)?.let { state ->
            state.copy(items = state.items.map { item -> item.copy(
                app = item.app?.let { it.copy(component = canonicalComponent(it.component)) },
                children = item.children.map { it.copy(component = canonicalComponent(it.component)) },
            ) })
        } ?: initial(spec, serial)

    fun add(state: WorkspaceState, ref: AppRef, page: Int, cell: Int): WorkspaceState {
        if (!slot(state, page, cell) || state.items.any { it.page == page && it.cell == cell }) return state
        return state.copy(items = state.items + WorkspaceItem(UUID.randomUUID().toString(), app = ref, page = page, cell = cell))
    }

    fun move(state: WorkspaceState, id: String, page: Int, cell: Int): WorkspaceState {
        if (!slot(state, page, cell)) return state
        val source = state.items.find { it.id == id } ?: return state
        val target = state.items.find { it.page == page && it.cell == cell }
        return state.copy(items = state.items.map { when (it.id) {
            id -> it.copy(page = page, cell = cell)
            target?.id -> it.copy(page = source.page, cell = source.cell)
            else -> it
        } })
    }

    fun remove(state: WorkspaceState, id: String) = state.copy(items = state.items.filterNot { it.id == id })

    fun rename(state: WorkspaceState, id: String, label: String) = state.copy(items = state.items.map {
        if (it.id == id && it.kind == "folder") it.copy(label = label.take(60).ifBlank { "폴더" }) else it
    })

    fun folder(state: WorkspaceState, sourceId: String, targetId: String, label: String): WorkspaceState {
        if (sourceId == targetId) return state
        val source = state.items.find { it.id == sourceId && it.kind == "app" } ?: return state
        val target = state.items.find { it.id == targetId } ?: return state
        val children = (if (target.kind == "folder") target.children else listOfNotNull(target.app)) + listOfNotNull(source.app)
        if (children.size > 64 || children.distinct().size != children.size) return state
        return state.copy(items = state.items.filterNot { it.id == sourceId }.map {
            if (it.id == targetId) it.copy(kind = "folder", app = null, children = children, label = label.ifBlank { "폴더" }.take(60)) else it
        })
    }

    fun extract(state: WorkspaceState, folderId: String, ref: AppRef, page: Int, cell: Int): WorkspaceState {
        val folder = state.items.find { it.id == folderId && ref in it.children } ?: return state
        val added = add(state, ref, page, cell)
        if (added == state) return state
        val remaining = folder.children - ref
        return added.copy(items = added.items.filterNot { it.id == folderId && remaining.isEmpty() }.map {
            if (it.id == folderId) it.copy(children = remaining) else it
        })
    }

    fun removeChild(state: WorkspaceState, folderId: String, ref: AppRef): WorkspaceState = state.copy(items = state.items.map {
        if (it.id == folderId) it.copy(children = it.children - ref) else it
    }.filterNot { it.kind == "folder" && it.children.isEmpty() })

    fun firstFree(state: WorkspaceState, page: Int): Int? =
        (0 until if (page == -1) state.dockCapacity else state.columns * state.rows).firstOrNull { cell -> state.items.none { it.page == page && it.cell == cell } }

    fun addPage(state: WorkspaceState) = if (state.pageCount < 9) state.copy(pageCount = state.pageCount + 1) else state
    fun removeLastPage(state: WorkspaceState) =
        if (state.pageCount > 1 && state.items.none { it.page == state.pageCount - 1 }) state.copy(pageCount = state.pageCount - 1) else state
}
