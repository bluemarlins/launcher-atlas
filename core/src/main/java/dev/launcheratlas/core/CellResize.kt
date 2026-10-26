package dev.launcheratlas.core

internal enum class ResizeEdge { LEFT, TOP, RIGHT, BOTTOM }

internal fun resizePlacement(base:CellPlacement,edge:ResizeEdge,slots:Int):CellPlacement = when(edge) {
    ResizeEdge.LEFT -> slots.coerceAtMost(base.spanX-1).let { base.copy(x=base.x+it,spanX=base.spanX-it) }
    ResizeEdge.TOP -> slots.coerceAtMost(base.spanY-1).let { base.copy(y=base.y+it,spanY=base.spanY-it) }
    ResizeEdge.RIGHT -> base.copy(spanX=(base.spanX+slots).coerceAtLeast(1))
    ResizeEdge.BOTTOM -> base.copy(spanY=(base.spanY+slots).coerceAtLeast(1))
}

internal fun resizeEdges(s:CellWorkspace,c:Cell,p:CellPlacement,profile:ProfileLayout):Set<ResizeEdge> {
    if(p.pageId<0) return emptySet()
    val horizontal=(1..profile.grid.columns).any { it!=p.spanX && CellEditor.allowed(s,c,CellSize(it,p.spanY),profile) }
    val vertical=(1..profile.grid.rows).any { it!=p.spanY && CellEditor.allowed(s,c,CellSize(p.spanX,it),profile) }
    return buildSet { if(horizontal) addAll(listOf(ResizeEdge.LEFT,ResizeEdge.RIGHT));if(vertical) addAll(listOf(ResizeEdge.TOP,ResizeEdge.BOTTOM)) }
}
