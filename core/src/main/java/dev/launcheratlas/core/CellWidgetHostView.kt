package dev.launcheratlas.core

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.ui.geometry.Offset

internal data class WidgetCellGesture(
    val select:()->Unit,val start:(Offset)->Unit,val move:(Offset)->Unit,val end:()->Unit,val cancel:()->Unit,
)

internal class CellWidgetHostView(context:Context):AppWidgetHostView(context) {
    var gesture:WidgetCellGesture?=null
        set(value) { field=value;if(value==null && !captured) clear(false) }
    private val handler=Handler(Looper.getMainLooper())
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    private var tracking:WidgetCellGesture?=null
    private var captured=false
    private var moved=false
    private var down=Offset.Zero
    private var previous=Offset.Zero
    private var downTime=0L
    private val hold=Runnable {
        tracking?.let { callbacks ->
            captured=true;moved=false
            val cancel=MotionEvent.obtain(downTime,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,previous.x,previous.y,0)
            super.dispatchTouchEvent(cancel);cancel.recycle()
            callbacks.select();callbacks.start(down)
        }
    }
    override fun dispatchTouchEvent(event:MotionEvent):Boolean {
        val point=Offset(event.x,event.y)
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                clear(false);tracking=gesture;down=point;previous=point;downTime=event.downTime
                if(tracking!=null) handler.postDelayed(hold,ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if(captured) { if(point!=previous) moved=true;tracking?.move?.invoke(point-previous);previous=point;return true }
                if((point-down).getDistance()>slop) clear(false)
                previous=point
            }
            MotionEvent.ACTION_UP -> {
                if(captured) { if(moved) tracking?.end?.invoke() else tracking?.cancel?.invoke();clear(false);return true }
                clear(false)
            }
            MotionEvent.ACTION_CANCEL,MotionEvent.ACTION_POINTER_DOWN -> {
                val consumed=captured;clear(true);if(consumed) return true
            }
        }
        val handled=super.dispatchTouchEvent(event)
        // Keep observing the stream even if a static RemoteViews child ignores DOWN.
        return handled || (event.actionMasked==MotionEvent.ACTION_DOWN && tracking!=null)
    }
    private fun clear(cancel:Boolean) {
        handler.removeCallbacks(hold)
        if(cancel && captured) tracking?.cancel?.invoke()
        captured=false;tracking=null
    }
    override fun onDetachedFromWindow() {clear(true);super.onDetachedFromWindow()}
}
