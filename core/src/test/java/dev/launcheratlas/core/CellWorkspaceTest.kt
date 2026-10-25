package dev.launcheratlas.core

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString

class CellWorkspaceTest {
    private val app = Cell("a", app = AppRef("com.a/.Main", 0))
    private val folder = Cell("f", "folder", children = listOf(AppRef("com.b/.Main", 0)))
    private fun state() = CellWorkspace(cells = listOf(app, folder), profiles = listOf(ProfileLayout("compact-portrait", GridSpec(4, 5), placements = listOf(CellPlacement("a"), CellPlacement("f", x = 1, spanX = 2, spanY = 2)))))

    @Test fun partialOverlapAndBoundsRollbackWithoutSwapping() {
        val s = state()
        assertEquals(s, CellEditor.place(s, "compact-portrait", CellPlacement("a", x = 2, y = 1)))
        assertEquals(s, CellEditor.place(s, "compact-portrait", CellPlacement("f", x = 3, spanX = 2, spanY = 2)))
        assertEquals(s, CellEditor.place(s, "compact-portrait", CellPlacement("a", spanX = 2)))
        assertTrue(CellEditor.valid(s))
    }
    @Test fun failedResizeAndHingeCollisionPreserveState() {
        val s = state()
        assertEquals(s, CellEditor.place(s, "compact-portrait", CellPlacement("f", x = 0, spanX = 2, spanY = 2)))
        val grid = GridSpec(4, 5, blocked = listOf(3, 7, 11, 15, 19))
        val p = ProfileLayout("hinge", grid)
        assertFalse(CellEditor.fits(p, CellPlacement("f", x = 2, spanX = 2)))
    }
    @Test fun mixedStackCyclesAndAppLocksResize() {
        var s = state().let { CellEditor.place(it, "compact-portrait", CellPlacement("f", x = 1)) }
        s = CellEditor.stack(s, "compact-portrait", "a", "f", "s")
        assertTrue(CellEditor.valid(s))
        assertEquals(3, s.cells.size)
        assertEquals(1, s.profiles.single().placements.size)
        assertEquals("f", CellEditor.active(s, "compact-portrait", "s")?.id)
        s = CellEditor.cycle(s, "compact-portrait", "s", 1)
        assertEquals("a", CellEditor.active(s, "compact-portrait", "s")?.id)
        assertEquals(s, CellEditor.place(s, "compact-portrait", CellPlacement("s", spanX = 2)))
    }
    @Test fun fullGridExtractionIsAtomicAndNoNestedStack() {
        val p = ProfileLayout("tiny", GridSpec(1, 1), pageCount = 1, placements = listOf(CellPlacement("s")))
        val s = CellWorkspace(cells = listOf(app, folder, Cell("s", "stack", members = listOf("a", "f"))), profiles = listOf(p))
        assertEquals(s, CellEditor.extract(s, "s", "a"))
        assertEquals(s, CellEditor.stack(s, "tiny", "s", "s", "nested"))
    }
    @Test fun profilesShareContentAndKeepGeometryAndStackSelection() {
        val s = state()
        val p = DeviceProfile("expanded-landscape", 800, 600, 2f, 320, 1f)
        val forked = CellEditor.ensureProfile(s, p, GridSpec(5, 6))
        val moved = CellEditor.place(forked, p.id, CellPlacement("a", x = 4, y = 5))
        assertEquals(s.profiles.single(), moved.profiles.first())
        assertEquals(4, moved.profiles.last().placements.first { it.cellId == "a" }.x)
        assertEquals(moved, atlasJson.decodeFromString<CellWorkspace>(atlasJson.encodeToString(moved)))
    }
    @Test fun migrationPreservesAppsFoldersDockAndRejectsCorruption() {
        val old = WorkspaceState(items = listOf(WorkspaceItem("a", app = app.app, page = -1), WorkspaceItem("f", "folder", children = folder.children, cell = 15)))
        val migrated = CellEditor.migrate(old)
        assertTrue(CellEditor.valid(migrated))
        assertEquals(-1, migrated.profiles.single().placements.first { it.cellId == "a" }.pageId)
        assertEquals(3, migrated.profiles.single().placements.first { it.cellId == "f" }.x)
        assertEquals(3, migrated.profiles.single().placements.first { it.cellId == "f" }.y)
        assertThrows(IllegalArgumentException::class.java) { CellEditor.migrate(old.copy(items = old.items + old.items.first())) }
    }
    @Test fun gridRecommendationsRespectFontScaleAndSmallLandscape() {
        val phone = DeviceProfile("compact-portrait", 360, 560, 3f, 480, 1f)
        assertTrue(GridPolicy.available(phone, GridSpec(4, 5)))
        assertFalse(GridPolicy.available(phone.copy(fontScale = 2f), GridSpec(5, 6)))
        val small = phone.copy(widthDp = 500, heightDp = 180)
        assertTrue(GridPolicy.recommend(small).rows < 5)
    }
    @Test fun providerConstraintIntersectionPreventsFixedWidgetResize() {
        val widget = Cell("w", "widget", provider = "com.w/.Provider", sizes = listOf(CellSize(2, 2)))
        val s = CellWorkspace(cells = listOf(widget), profiles = listOf(ProfileLayout("p", GridSpec(4,5), placements = listOf(CellPlacement("w", spanX=2, spanY=2)))))
        assertEquals(s, CellEditor.place(s, "p", CellPlacement("w", spanX = 3, spanY = 2)))
        assertTrue(CellEditor.valid(s))
    }

    @Test fun providerSizesAreProfileSpecificAndDuplicateBindingsRejected() {
        val widget = Cell("w", "widget", provider="com.w/.Provider", appWidgetId=4)
        val compact = ProfileLayout("compact", constraints=listOf(CellConstraint("w", listOf(CellSize(2,2)))), placements=listOf(CellPlacement("w",spanX=2,spanY=2)))
        val expanded = ProfileLayout("expanded", constraints=listOf(CellConstraint("w", listOf(CellSize(1,1),CellSize(2,2)))), placements=listOf(CellPlacement("w")))
        val s = CellWorkspace(cells=listOf(widget), profiles=listOf(compact,expanded))
        assertTrue(CellEditor.valid(s))
        assertEquals(s, CellEditor.place(s,"compact",CellPlacement("w")))
        assertNotEquals(s, CellEditor.place(s,"expanded",CellPlacement("w",spanX=2,spanY=2)))
        assertFalse(CellEditor.valid(s.copy(cells=s.cells+widget.copy(id="duplicate"))))
    }

    @Test fun regridKeepsEveryCellAndUsesOverflowPage() {
        val s = CellWorkspace(cells=(0..4).map { Cell("a$it",app=app.app) },profiles=listOf(ProfileLayout("p",GridSpec(5,1),pageCount=1,placements=(0..4).map { CellPlacement("a$it",x=it) })))
        val next = CellEditor.regrid(s,"p",GridSpec(2,2))
        assertEquals(5,next.profiles.single().placements.size)
        assertEquals(2,next.profiles.single().pageCount)
        assertTrue(CellEditor.valid(next))
    }

    @Test fun newScreenCopiesMostRecentlyActiveUserPlacement() {
        val compact=DeviceProfile("compact-portrait-flat",360,600,3f,480,1f)
        var s=CellEditor.ensureProfile(state(),compact)
        s=CellEditor.add(s,compact.id,Cell("user",app=app.app),CellPlacement("user",x=3,y=4))
        val expanded=DeviceProfile("expanded-landscape-flat",800,300,3f,480,1f)
        s=CellEditor.ensureProfile(s,expanded)
        assertTrue(s.profiles.last().placements.any { it.cellId=="user" })
    }
    @Test fun tallResizableFolderAdaptsWhenEnteringLandscape() {
        val s=CellWorkspace(cells=listOf(folder),profiles=listOf(ProfileLayout("seed",GridSpec(4,5),placements=listOf(CellPlacement("f",spanX=2,spanY=5)))))
        val next=CellEditor.ensureProfile(s,DeviceProfile("landscape",700,200,2f,320,1f),GridSpec(5,2))
        assertEquals(2,next.profiles.last().placements.single().spanY)
        assertTrue(CellEditor.valid(next))
    }
    @Test fun addingMemberToSharedStackUpdatesAllProfilesAtomically() {
        val cells=listOf(app,folder,Cell("clock","builtin",widget="clock"),Cell("s","stack",members=listOf("f","clock")))
        val first=ProfileLayout("p",placements=listOf(CellPlacement("s"),CellPlacement("a",x=1)))
        val state=CellWorkspace(cells=cells,profiles=listOf(first,first.copy(id="q")))
        val next=CellEditor.stack(state,"p","a","s")
        assertTrue(next.cells.first { it.id=="s" }.members.contains("a"))
        assertTrue(next.profiles.all { p -> p.placements.none { it.cellId=="a" } })
        assertTrue(CellEditor.valid(next))
    }
    @Test fun extractingLastAppFromStackFolderRemovesEmptyFolderWithoutDuplication() {
        val p=ProfileLayout("p",placements=listOf(CellPlacement("s")))
        val s=CellWorkspace(cells=listOf(folder,Cell("clock","builtin",widget="clock"),Cell("s","stack",members=listOf("f","clock"))),profiles=listOf(p))
        val next=CellEditor.extractApp(s,"p","f",folder.children.single(),0)
        assertTrue(next.cells.none { it.id=="f" || it.id=="s" })
        assertEquals(1,next.cells.count { it.kind=="app" })
        assertTrue(CellEditor.valid(next))
    }
    @Test fun oversizedFixedWidgetIsRetainedForLargerDisplayWithoutBlockingOtherCells() {
        val widget=Cell("w","widget",provider="com.w/.Provider",sizes=listOf(CellSize(4,4)))
        val s=CellWorkspace(cells=listOf(app,widget),profiles=listOf(ProfileLayout("seed",GridSpec(5,5),placements=listOf(CellPlacement("w",spanX=4,spanY=4),CellPlacement("a",x=4)))))
        val small=CellEditor.ensureProfile(s,DeviceProfile("small",200,200,1f,160,1f),GridSpec(2,2))
        assertEquals(listOf("w"),small.profiles.last().unplaced.map { it.cellId })
        assertEquals("a",small.profiles.last().placements.single().cellId)
        val large=CellEditor.ensureProfile(small,DeviceProfile("large",800,800,1f,160,1f),GridSpec(5,6))
        assertTrue(large.profiles.last().unplaced.isEmpty())
        assertEquals(2,large.profiles.last().placements.size)
        assertTrue(CellEditor.valid(large))
    }
    @Test fun sharedStackAdditionRespectsAppSizeOnEveryScreen() {
        val cells=listOf(app,folder,Cell("clock","builtin",widget="clock"),Cell("s","stack",members=listOf("f","clock")))
        val p=ProfileLayout("p",placements=listOf(CellPlacement("s"),CellPlacement("a",x=1)))
        val q=ProfileLayout("q",placements=listOf(CellPlacement("s",spanX=2,spanY=2),CellPlacement("a",x=3)))
        val next=CellEditor.stack(CellWorkspace(cells=cells,profiles=listOf(p,q)),"p","a","s")
        assertEquals(3,next.cells.first { it.id=="s" }.members.size)
        assertTrue(next.profiles.all { it.placements.single().spanX==1 && it.placements.single().spanY==1 })
        assertTrue(CellEditor.valid(next))
    }
}
