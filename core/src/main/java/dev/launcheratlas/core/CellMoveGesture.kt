package dev.launcheratlas.core

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope

// Observe before clickable/Android interop; consume only a selected Cell or a successful hold.
internal suspend fun PointerInputScope.cellMoveGesture(
    selected:()->Boolean, select:()->Unit, start:(Offset)->Unit,
    move:(Offset)->Unit, end:()->Unit, cancel:()->Unit,
) = awaitEachGesture {
    val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
    if(!selected()) {
        val releasedOrMoved=withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while(true) {
                val event=awaitPointerEvent(PointerEventPass.Initial)
                val change=event.changes.find { it.id==down.id }
                if(change==null || !change.pressed || event.changes.size>1 || (change.position-down.position).getDistance()>viewConfiguration.touchSlop) return@withTimeoutOrNull true
            }
        }
        if(releasedOrMoved!=null) return@awaitEachGesture
        select()
    }
    start(down.position)
    var previous=down.position
    var moved=false
    try {
        while(true) {
            val event=awaitPointerEvent(PointerEventPass.Initial)
            val change=event.changes.find { it.id==down.id }
            if(change==null || event.changes.size>1) { cancel();break }
            val wasConsumed=change.isConsumed
            change.consume()
            if(!change.pressed) { if(moved && !wasConsumed) end() else cancel();break }
            val delta=change.position-previous
            if(delta!=Offset.Zero) { moved=true;move(delta) }
            previous=change.position
        }
    } catch(e:kotlinx.coroutines.CancellationException) { cancel();throw e }
}
