package dev.launcheratlas.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable internal fun CellSelectionOverlay(
    base:CellPlacement, preview:CellPlacement?, edges:Set<ResizeEdge>,slotW:Float,slotH:Float,
    width:Float,height:Float,accent:Color,valid:Boolean, accessible:(ResizeEdge,Int)->Boolean,
    start:(ResizeEdge)->Unit,update:(ResizeEdge,Int)->Unit,finish:()->Unit,cancel:()->Unit,
) {
    val p=preview ?: base
    val actions=listOf(Triple("가로 늘리기",ResizeEdge.RIGHT,1),Triple("가로 줄이기",ResizeEdge.RIGHT,-1),
        Triple("세로 늘리기",ResizeEdge.BOTTOM,1),Triple("세로 줄이기",ResizeEdge.BOTTOM,-1))
    Box(Modifier.offset((p.x*slotW).dp,(p.y*slotH).dp).size((p.spanX*slotW).dp,(p.spanY*slotH).dp)
        .border(2.dp,if(valid) accent else Color.Red).testTag("cell-selection").semantics {
            stateDescription="Cell 선택됨, ${p.spanX}열 ${p.spanY}행"
            customActions=actions.filter { it.second in edges }.map { (label,edge,delta) -> CustomAccessibilityAction(label) { accessible(edge,delta) } }
        })
    val currentStart by rememberUpdatedState(start)
    val currentUpdate by rememberUpdatedState(update)
    val currentFinish by rememberUpdatedState(finish)
    val currentCancel by rememberUpdatedState(cancel)
    edges.forEach { edge ->
        val cx=when(edge) { ResizeEdge.LEFT -> p.x*slotW;ResizeEdge.RIGHT -> (p.x+p.spanX)*slotW;else -> (p.x+p.spanX/2f)*slotW }
        val cy=when(edge) { ResizeEdge.TOP -> p.y*slotH;ResizeEdge.BOTTOM -> (p.y+p.spanY)*slotH;else -> (p.y+p.spanY/2f)*slotH }
        // Keep the detector stationary during preview; the visible handle follows the candidate.
        val hitX=when(edge) { ResizeEdge.LEFT -> base.x*slotW;ResizeEdge.RIGHT -> (base.x+base.spanX)*slotW;else -> (base.x+base.spanX/2f)*slotW }
        val hitY=when(edge) { ResizeEdge.TOP -> base.y*slotH;ResizeEdge.BOTTOM -> (base.y+base.spanY)*slotH;else -> (base.y+base.spanY/2f)*slotH }
        Box(Modifier.offset((hitX-24).coerceIn(0f,(width-48).coerceAtLeast(0f)).dp,(hitY-24).coerceIn(0f,(height-48).coerceAtLeast(0f)).dp).size(48.dp).testTag("resize-${edge.name.lowercase()}")
            .semantics { contentDescription=when(edge) { ResizeEdge.LEFT->"왼쪽 크기 조절";ResizeEdge.RIGHT->"오른쪽 크기 조절";ResizeEdge.TOP->"위쪽 크기 조절";ResizeEdge.BOTTOM->"아래쪽 크기 조절" } }
            .pointerInput(base,edge,slotW,slotH) {
                var total=0f
                detectDragGestures(onDragStart={total=0f;currentStart(edge)},onDrag={ change,delta ->
                    change.consume();total+=if(edge==ResizeEdge.LEFT || edge==ResizeEdge.RIGHT) delta.x else delta.y
                    val unit=(if(edge==ResizeEdge.LEFT || edge==ResizeEdge.RIGHT) slotW else slotH)*density
                    currentUpdate(edge,(total/unit).roundToInt())
                },onDragEnd={currentFinish()},onDragCancel={currentCancel()})
            })
        Box(Modifier.offset((cx-6).coerceIn(0f,(width-12).coerceAtLeast(0f)).dp,(cy-6).coerceIn(0f,(height-12).coerceAtLeast(0f)).dp).size(12.dp).clip(CircleShape).background(if(valid) accent else Color.Red))
    }
}
