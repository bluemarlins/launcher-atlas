package dev.launcheratlas.core

import org.junit.Assert.*
import org.junit.Test

class CellResizeTest {
    private val base=CellPlacement("f",x=2,y=2,spanX=2,spanY=2)
    @Test fun edgesKeepOppositeBoundaryFixed() {
        assertEquals(base.copy(x=1,spanX=3),resizePlacement(base,ResizeEdge.LEFT,-1))
        assertEquals(base.copy(y=1,spanY=3),resizePlacement(base,ResizeEdge.TOP,-1))
        assertEquals(base.copy(spanX=3),resizePlacement(base,ResizeEdge.RIGHT,1))
        assertEquals(base.copy(spanY=3),resizePlacement(base,ResizeEdge.BOTTOM,1))
    }
    @Test fun crossingOppositeEdgeStopsAtOneSlot() {
        assertEquals(base.copy(x=3,spanX=1),resizePlacement(base,ResizeEdge.LEFT,9))
        assertEquals(base.copy(y=3,spanY=1),resizePlacement(base,ResizeEdge.TOP,9))
        assertEquals(base.copy(spanX=1),resizePlacement(base,ResizeEdge.RIGHT,-9))
    }
    @Test fun invalidGeometryRollsBackThroughExistingEditor() {
        val folder=Cell("f","folder",children=listOf(AppRef("com.a/.Main",0)))
        val p=ProfileLayout("p",GridSpec(5,5),placements=listOf(base,CellPlacement("b",x=1,y=2)))
        val s=CellWorkspace(cells=listOf(folder,Cell("b",app=AppRef("com.b/.Main",0))),profiles=listOf(p))
        assertEquals(s,CellEditor.place(s,"p",resizePlacement(base,ResizeEdge.LEFT,-1)))
        assertEquals(s,CellEditor.place(s,"p",resizePlacement(base,ResizeEdge.TOP,-3)))
    }
    @Test fun handlesFollowProviderAxisAndStackIntersection() {
        val widget=Cell("w","widget",provider="com.w/.Provider",sizes=listOf(CellSize(2,1),CellSize(3,1)))
        val app=Cell("a",app=AppRef("com.a/.Main",0))
        val s=CellWorkspace(cells=listOf(widget,app,Cell("s","stack",members=listOf("w","a"))))
        assertEquals(setOf(ResizeEdge.LEFT,ResizeEdge.RIGHT),resizeEdges(s,widget,CellPlacement("w",spanX=2),ProfileLayout("p")))
        assertTrue(resizeEdges(s,app,CellPlacement("a"),ProfileLayout("p")).isEmpty())
        val fixed=widget.copy(sizes=listOf(CellSize(2,1)))
        assertTrue(resizeEdges(s,fixed,CellPlacement("w",spanX=2),ProfileLayout("p")).isEmpty())
    }
}
