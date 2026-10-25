package dev.launcheratlas.core

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.floor

@Serializable data class CellSize(val x: Int = 1, val y: Int = 1)
@Serializable data class Cell(
    val id: String, val kind: String = "app", val app: AppRef? = null, val label: String = "",
    val children: List<AppRef> = emptyList(), val widget: String = "", val provider: String = "",
    val appWidgetId: Int? = null, val members: List<String> = emptyList(),
    val sizes: List<CellSize> = emptyList(),
)
@Serializable data class CellPlacement(
    val cellId: String, val pageId: Int = 0, val x: Int = 0, val y: Int = 0,
    val spanX: Int = 1, val spanY: Int = 1,
)
@Serializable data class GridSpec(val columns: Int = 4, val rows: Int = 5, val blocked: List<Int> = emptyList())
@Serializable data class DeviceProfile(
    val id: String, val widthDp: Int, val heightDp: Int, val density: Float,
    val densityDpi: Int, val fontScale: Float, val posture: String = "flat",
)
@Serializable data class StackState(val cellId: String, val activeId: String)
@Serializable data class CellConstraint(val cellId: String, val sizes: List<CellSize>)
@Serializable data class ProfileLayout(
    val id: String, val grid: GridSpec = GridSpec(), val pageCount: Int = 3,
    val placements: List<CellPlacement> = emptyList(), val stacks: List<StackState> = emptyList(),
    val constraints: List<CellConstraint> = emptyList(),
    val device: DeviceProfile? = null,
    val unplaced: List<CellPlacement> = emptyList(),
)
@Serializable data class CellWorkspace(
    val schemaVersion: Int = 2, val cells: List<Cell> = emptyList(),
    val profiles: List<ProfileLayout> = emptyList(), val dockCapacity: Int = 4,
    val activeProfileId: String? = null,
)

object GridPolicy {
    val presets = listOf(GridSpec(4, 5), GridSpec(4, 6), GridSpec(5, 5), GridSpec(5, 6))
    fun available(p: DeviceProfile, g: GridSpec): Boolean =
        p.widthDp / g.columns >= 64 + 8 * (p.fontScale - 1) && p.heightDp / g.rows >= 82 + 24 * (p.fontScale - 1)
    fun recommend(p: DeviceProfile): GridSpec =
        (if (p.widthDp >= 600) GridSpec(5, 6) else GridSpec(4, 5)).let { preferred ->
            if (available(p, preferred)) preferred else GridSpec(
                floor(p.widthDp / (64 + 8 * (p.fontScale - 1))).toInt().coerceIn(1, 6),
                floor(p.heightDp / (82 + 24 * (p.fontScale - 1))).toInt().coerceIn(1, 6))
        }
}

object CellEditor {
    fun slots(p: CellPlacement, columns: Int): Set<Int> = (p.y until p.y + p.spanY).flatMap { y ->
        (p.x until p.x + p.spanX).map { x -> y * columns + x }
    }.toSet()

    fun fits(profile: ProfileLayout, p: CellPlacement, dock: Int = 4, ignore: Set<String> = setOf(p.cellId)): Boolean {
        val g = profile.grid
        if (p.spanX < 1 || p.spanY < 1 || p.x < 0 || p.y < 0 || p.pageId !in -1 until profile.pageCount) return false
        if (p.pageId == -1) return p.y == 0 && p.spanX == 1 && p.spanY == 1 && p.x < dock &&
            profile.placements.none { it.cellId !in ignore && it.pageId == -1 && it.x == p.x }
        if (p.x + p.spanX > g.columns || p.y + p.spanY > g.rows) return false
        val occupied = slots(p, g.columns)
        return occupied.none { it in g.blocked } && profile.placements.none {
            it.cellId !in ignore && it.pageId == p.pageId && slots(it, g.columns).any(occupied::contains)
        }
    }

    fun allowed(s: CellWorkspace, cell: Cell, size: CellSize, profile: ProfileLayout? = null): Boolean = when (cell.kind) {
        "app" -> size == CellSize()
        "stack" -> cell.members.isNotEmpty() && cell.members.all { id -> s.cells.find { it.id == id }?.let { it.kind != "stack" && allowed(s, it, size, profile) } == true }
        else -> size.x in 1..6 && size.y in 1..6 && (cell.sizes.isEmpty() || size in cell.sizes) &&
            (profile?.constraints?.find { it.cellId == cell.id }?.let { size in it.sizes } ?: true)
    }

    fun valid(s: CellWorkspace): Boolean {
        if (s.schemaVersion != 2 || s.dockCapacity !in 1..6 || s.cells.map { it.id }.distinct().size != s.cells.size ||
            s.profiles.map { it.id }.distinct().size != s.profiles.size) return false
        val byId = s.cells.associateBy { it.id }
        val bindings = s.cells.mapNotNull { it.appWidgetId }
        if (bindings.distinct().size != bindings.size) return false
        if (s.cells.any { c -> c.id.isBlank() || when (c.kind) {
            "app" -> c.app == null || !c.app.component.contains('/') || c.children.isNotEmpty() || c.members.isNotEmpty()
            "folder" -> c.app != null || c.children.isEmpty() || c.children.size > 64 || c.children.distinct().size != c.children.size || c.children.any { !it.component.contains('/') } || c.members.isNotEmpty()
            "builtin" -> c.widget !in listOf("clock", "date", "battery") || c.members.isNotEmpty()
            "widget" -> !c.provider.contains('/') || (c.appWidgetId != null && c.appWidgetId <= 0) || c.members.isNotEmpty()
            "stack" -> c.members.size !in 2..64 || c.members.distinct().size != c.members.size || c.members.any { byId[it] == null || byId[it]?.kind == "stack" }
            else -> true
        } }) return false
        return s.profiles.all { p ->
            val referenced = (p.placements + p.unplaced).flatMap { listOf(it.cellId) + (byId[it.cellId]?.members ?: emptyList()) }
            p.id.isNotBlank() && p.grid.columns in 1..6 && p.grid.rows in 1..6 && p.pageCount in 1..99 &&
                p.grid.blocked.all { it in 0 until p.grid.columns * p.grid.rows } &&
                referenced.distinct().size == referenced.size &&
                p.unplaced.all { it.cellId in byId && it.spanX in 1..6 && it.spanY in 1..6 } &&
                p.placements.all { placement -> byId[placement.cellId]?.let { c ->
                    allowed(s, c, CellSize(placement.spanX, placement.spanY), p) && fits(p, placement, s.dockCapacity) &&
                        (placement.pageId != -1 || c.kind in listOf("app", "folder"))
                } == true } && p.constraints.map { it.cellId }.distinct().size == p.constraints.size && p.constraints.all { byId[it.cellId]?.kind == "widget" } &&
                p.stacks.map { it.cellId }.distinct().size == p.stacks.size && p.stacks.all { it.activeId in (byId[it.cellId]?.members ?: emptyList()) }
        }
    }

    private fun checked(original: CellWorkspace, next: CellWorkspace) = if (valid(next)) next else original
    fun place(s: CellWorkspace, profileId: String, placement: CellPlacement): CellWorkspace {
        val p = s.profiles.find { it.id == profileId } ?: return s
        if (s.cells.none { it.id == placement.cellId } || (p.placements+p.unplaced).none { it.cellId == placement.cellId }) return s
        return checked(s, s.copy(profiles = s.profiles.map { if (it.id == profileId) it.copy(placements = it.placements.filterNot { it.cellId == placement.cellId } + placement,unplaced=it.unplaced.filterNot { it.cellId==placement.cellId }) else it }))
    }

    fun free(p: ProfileLayout, id: String, size: CellSize = CellSize(), dock: Int = 4, page: Int? = null): CellPlacement? {
        val pages = if (page == null) (0 until p.pageCount).toList() else listOf(page)
        for (pg in pages) for (y in 0 until if (pg == -1) 1 else p.grid.rows) for (x in 0 until if (pg == -1) dock else p.grid.columns) {
            val candidate = CellPlacement(id, pg, x, y, size.x, size.y)
            if (fits(p, candidate, dock)) return candidate
        }
        return null
    }

    fun add(s: CellWorkspace, profileId: String, cell: Cell, placement: CellPlacement): CellWorkspace {
        if (s.cells.any { it.id == cell.id } || placement.cellId != cell.id) return s
        return checked(s, s.copy(cells = s.cells + cell, profiles = s.profiles.map {
            if (it.id == profileId) it.copy(placements = it.placements + placement) else it
        }))
    }

    fun remove(s: CellWorkspace, id: String): CellWorkspace {
        val target = s.cells.find { it.id==id } ?: return s
        val collapsed = s.cells.filter { it.kind=="stack" && id in it.members && it.members.size==2 }.associate { it.id to (it.members-id).single() }
        val deleted = setOf(id)+collapsed.keys
        var next=s.copy(cells=s.cells.filterNot { it.id in deleted }.map { if (it.kind=="stack") it.copy(members=it.members-id) else it },profiles=s.profiles.map { p ->
            fun placements(items:List<CellPlacement>)=items.filterNot { it.cellId==id }.map { old -> collapsed[old.cellId]?.let { old.copy(cellId=it) } ?: old }
            p.copy(placements=placements(p.placements),unplaced=placements(p.unplaced),stacks=p.stacks.filterNot { it.cellId in deleted }.map { state ->
                if (state.activeId==id) state.copy(activeId=s.cells.first { it.id==state.cellId }.members.first { it!=id }) else state
            },constraints=p.constraints.filterNot { it.cellId==id })
        })
        val referenced=next.profiles.flatMap { it.placements+it.unplaced }.map { it.cellId }.toSet()
        val keep=referenced+next.cells.filter { it.id in referenced }.flatMap { it.members }
        val candidates=deleted+target.members
        next=next.copy(cells=next.cells.filter { it.id !in candidates || it.id in keep })
        next=next.copy(profiles=next.profiles.map { p -> p.copy(constraints=p.constraints.filter { rule -> next.cells.any { it.id==rule.cellId } }) })
        return checked(s, next)
    }

    fun rename(s: CellWorkspace, id: String, label: String) = checked(s, s.copy(cells = s.cells.map { if (it.id == id && it.kind == "folder") it.copy(label = label.take(60).ifBlank { "폴더" }) else it }))
    fun folder(s: CellWorkspace, profileId: String, sourceId: String, targetId: String): CellWorkspace {
        val source = s.cells.find { it.id == sourceId && it.kind == "app" } ?: return s
        val target = s.cells.find { it.id == targetId && it.kind in listOf("app", "folder") } ?: return s
        if (sourceId == targetId) return s
        val children = (if (target.kind == "folder") target.children else listOfNotNull(target.app)) + listOfNotNull(source.app)
        return checked(s, s.copy(cells = s.cells.map { if (it.id == targetId) it.copy(kind = "folder", app = null, label = target.label.ifBlank { "폴더" }, children = children) else it },
            profiles = s.profiles.map { if (it.id == profileId) it.copy(placements = it.placements.filterNot { it.cellId == sourceId }) else it }))
    }
    fun removeChild(s: CellWorkspace, id: String, ref: AppRef): CellWorkspace {
        val c = s.cells.find { it.id == id && it.kind == "folder" } ?: return s
        if (c.children.size == 1 && ref in c.children) return remove(s, id)
        return checked(s, s.copy(cells = s.cells.map { if (it.id == id) it.copy(children = it.children - ref) else it }))
    }
    fun extractApp(s: CellWorkspace, profileId: String, id: String, ref: AppRef, page: Int): CellWorkspace {
        val c = s.cells.find { it.id == id && ref in it.children } ?: return s
        val p = s.profiles.find { it.id == profileId } ?: return s
        val app = Cell(UUID.randomUUID().toString(), app = ref)
        val placement = free(p, app.id, dock = s.dockCapacity, page = page) ?: return s
        val added = add(s, profileId, app, placement)
        return if (added != s) removeChild(added, c.id, ref) else s
    }

    fun stack(s: CellWorkspace, profileId: String, sourceId: String, targetId: String, id: String = UUID.randomUUID().toString()): CellWorkspace {
        if (sourceId == targetId || s.cells.any { it.id == id }) return s
        val p = s.profiles.find { it.id == profileId } ?: return s
        val source = p.placements.find { it.cellId == sourceId && it.pageId >= 0 } ?: return s
        val target = p.placements.find { it.cellId == targetId && it.pageId >= 0 } ?: return s
        val a = s.cells.find { it.id == sourceId && it.kind != "stack" } ?: return s
        val b = s.cells.find { it.id == targetId } ?: return s
        if (source.spanX != target.spanX || source.spanY != target.spanY) return s
        if (b.kind == "stack") {
            val affected=s.profiles.filter { profile -> (profile.placements+profile.unplaced).any { it.cellId==targetId } }.map { it.id }.toSet()
            var next=s.copy(cells=s.cells.map { if (it.id==targetId) it.copy(members=it.members+a.id) else it },profiles=s.profiles.map { profile ->
                if (profile.id in affected) profile.copy(placements=profile.placements.filterNot { it.cellId==sourceId },unplaced=profile.unplaced.filterNot { it.cellId==sourceId }) else profile
            })
            for (profile in next.profiles.filter { it.id in affected }) next=regrid(next,profile.id,profile.grid,validate=false)
            return checked(s,next)
        }
        val stack = Cell(id, "stack", members = listOf(b.id, a.id))
        return checked(s, s.copy(cells = s.cells + stack, profiles = s.profiles.map { if (it.id == profileId) it.copy(
            placements = it.placements.filterNot { it.cellId in listOf(a.id, b.id) } + target.copy(cellId = id), stacks = it.stacks + StackState(id, b.id)) else it }))
    }
    fun active(s: CellWorkspace, profileId: String, id: String): Cell? {
        val cell = s.cells.find { it.id == id } ?: return null
        if (cell.kind != "stack") return cell
        val active = s.profiles.find { it.id == profileId }?.stacks?.find { it.cellId == id }?.activeId ?: cell.members.first()
        return s.cells.find { it.id == active }
    }
    fun cycle(s: CellWorkspace, profileId: String, id: String, delta: Int): CellWorkspace {
        val c = s.cells.find { it.id == id && it.kind == "stack" } ?: return s
        val index = c.members.indexOf(active(s, profileId, id)?.id)
        val next = c.members[Math.floorMod(index + delta, c.members.size)]
        return checked(s, s.copy(profiles = s.profiles.map { if (it.id == profileId) it.copy(stacks = it.stacks.filterNot { it.cellId == id } + StackState(id, next)) else it }))
    }
    fun reorder(s: CellWorkspace, id: String, member: String, delta: Int): CellWorkspace {
        val c = s.cells.find { it.id == id && member in it.members } ?: return s
        val members = c.members.toMutableList()
        val from = members.indexOf(member)
        val to = (from + delta).coerceIn(0, members.lastIndex)
        members.add(to, members.removeAt(from))
        return checked(s, s.copy(cells = s.cells.map { if (it.id == id) it.copy(members = members) else it }))
    }
    fun extract(s: CellWorkspace, id: String, member: String): CellWorkspace {
        val c = s.cells.find { it.id == id && member in it.members } ?: return s
        var next = s
        for (p in s.profiles.filter { profile -> (profile.placements+profile.unplaced).any { it.cellId == id } }) {
            val old = (p.placements+p.unplaced).first { it.cellId == id }
            val destination = free(p, member, CellSize(old.spanX, old.spanY), s.dockCapacity) ?: return s
            next = next.copy(profiles = next.profiles.map { if (it.id == p.id) it.copy(placements = it.placements + destination) else it })
        }
        val remaining = c.members - member
        next = next.copy(cells = next.cells.map { if (it.id == id) it.copy(members = remaining) else it })
        if (remaining.size == 1) next = next.copy(cells = next.cells.filterNot { it.id == id }, profiles = next.profiles.map { p -> p.copy(
            placements = p.placements.map { if (it.cellId == id) it.copy(cellId = remaining.first()) else it },unplaced=p.unplaced.map { if (it.cellId==id) it.copy(cellId=remaining.first()) else it }, stacks = p.stacks.filterNot { it.cellId == id }) })
        else next = next.copy(profiles = next.profiles.map { p -> p.copy(stacks = p.stacks.map { if (it.cellId == id && it.activeId == member) it.copy(activeId = remaining.first()) else it }) })
        return checked(s, next)
    }

    fun ensureProfile(s: CellWorkspace, device: DeviceProfile, grid: GridSpec = GridPolicy.recommend(device), constraints: List<CellConstraint>? = null): CellWorkspace {
        val existing=s.profiles.find { it.id==device.id }
        if (existing!=null) {
            val next=s.copy(activeProfileId=device.id,profiles=s.profiles.map { if (it.id==device.id) it.copy(device=device) else it })
            return if (existing.device==null && !GridPolicy.available(device,existing.grid)) regrid(next,device.id,grid,constraints) else next
        }
        val source=s.profiles.find { it.id==s.activeProfileId } ?: s.profiles.firstOrNull()
        val seed=(source ?: ProfileLayout(device.id)).copy(id=device.id,device=device,constraints=constraints ?: emptyList())
        val next=s.copy(profiles=s.profiles+seed,activeProfileId=device.id)
        return regrid(next,device.id,grid,constraints ?: emptyList())
    }

    fun regrid(s: CellWorkspace, profileId: String, grid: GridSpec, constraints: List<CellConstraint>? = null, validate: Boolean = true): CellWorkspace {
        val old = s.profiles.find { it.id == profileId } ?: return s
        var p = old.copy(grid = grid, placements = emptyList(),unplaced=emptyList(), constraints = constraints ?: old.constraints)
        for (placement in old.placements+old.unplaced) {
            val c = s.cells.first { it.id == placement.cellId }
            val sizes = (1..grid.columns).flatMap { x -> (1..grid.rows).map { y -> CellSize(x,y) } }
                .filter { allowed(s,c,it,p) && (placement.pageId != -1 || it == CellSize()) }
                .sortedBy { kotlin.math.abs(it.x-placement.spanX)+kotlin.math.abs(it.y-placement.spanY) }
            var result: CellPlacement? = null
            for (size in sizes) {
                val candidate = placement.copy(spanX=size.x,spanY=size.y)
                result = candidate.takeIf { fits(p,it,s.dockCapacity) } ?: free(p,c.id,size,s.dockCapacity,if (placement.pageId == -1) -1 else null)
                if (result != null) break
            }
            if (result == null && sizes.isNotEmpty() && placement.pageId >= 0) {
                require(p.pageCount < 99) { "페이지 한도에 도달했습니다" }
                val empty=p.copy(placements=emptyList(),pageCount=p.pageCount+1)
                val overflow=sizes.firstNotNullOfOrNull { free(empty,c.id,it,s.dockCapacity,p.pageCount) }
                if (overflow!=null) { p=p.copy(pageCount=p.pageCount+1); result=overflow }
            }
            p = if (result==null) p.copy(unplaced=p.unplaced+placement) else p.copy(placements=p.placements+result)
        }
        return s.copy(profiles = s.profiles.map { if (it.id == profileId) p else it }).also { if (validate) require(valid(it)) }
    }

    fun migrate(old: WorkspaceState, builtins: List<String> = emptyList()): CellWorkspace {
        require(WorkspaceEditor.valid(old)) { "v1 배치가 손상되었습니다. 원본을 보존했습니다." }
        var migrated = CellWorkspace(cells = old.items.map { Cell(it.id, it.kind,
            it.app?.let { ref -> ref.copy(component = canonicalComponent(ref.component)) }, it.label,
            it.children.map { ref -> ref.copy(component = canonicalComponent(ref.component)) }) },
            profiles = listOf(ProfileLayout("legacy-portrait", GridSpec(old.columns, old.rows), old.pageCount,
                old.items.map { CellPlacement(it.id, it.page, if (it.page == -1) it.cell else it.cell % old.columns, if (it.page == -1) 0 else it.cell / old.columns) })),
            dockCapacity = old.dockCapacity).also { require(valid(it)) }
        for (kind in builtins) {
            var id = "builtin_$kind"
            while (migrated.cells.any { it.id == id }) id += "_"
            var p = migrated.profiles.single()
            var destination = free(p,id,dock=migrated.dockCapacity)
            if (destination == null) {
                p = p.copy(pageCount=p.pageCount+1)
                migrated = migrated.copy(profiles=listOf(p))
                destination = free(p,id,dock=migrated.dockCapacity)
            }
            require(destination != null) { "내장 위젯을 옮길 공간이 없습니다" }
            migrated = add(migrated,p.id,Cell(id,"builtin",widget=kind),destination)
        }
        return migrated
    }
}
