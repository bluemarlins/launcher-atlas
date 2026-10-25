package dev.launcheratlas.core

import android.app.PendingIntent
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

open class FixtureWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, dev.launcheratlas.core.test.R.layout.fixture_widget)
            views.setOnClickPendingIntent(dev.launcheratlas.core.test.R.id.widget_action,
                PendingIntent.getBroadcast(context, id, Intent(context, FixtureWidgetAction::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            val items=RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
            repeat(30) { index ->
                val row=RemoteViews(context.packageName,android.R.layout.simple_list_item_1)
                row.setTextViewText(android.R.id.text1,"Widget row $index")
                items.addItem(index.toLong(),row)
            }
            views.setRemoteAdapter(dev.launcheratlas.core.test.R.id.widget_list,items.build())
            manager.updateAppWidget(id, views)
        }
    }
}
class FixtureConfigWidget : FixtureWidget()
class FixtureConfigurationActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        receivedId=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1)
        val root=android.widget.LinearLayout(this).apply { orientation=android.widget.LinearLayout.VERTICAL }
        root.setOnApplyWindowInsetsListener { view,insets ->
            val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            insets
        }
        for ((label,result) in listOf("Cancel widget" to RESULT_CANCELED,"Finish widget" to RESULT_OK)) root.addView(android.widget.Button(this).apply {
            text=label;setOnClickListener { lastResult=result;setResult(result,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,receivedId));finish() }
        })
        setContentView(root)
    }
    companion object { var receivedId=-1;var lastResult=-100 }
}
class FixtureWidgetAction : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.getSharedPreferences("fixture",0).edit().putBoolean("clicked",true).commit()
    }
}
class WidgetHostTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.context
    private fun host(): AtlasWidgetHost {
        org.junit.Assume.assumeTrue("Shell grantbind fixture is emulator-only",android.os.Build.MODEL.startsWith("sdk_") || android.os.Build.FINGERPRINT.contains("generic"))
        instrumentation.uiAutomation.executeShellCommand("appwidget grantbind --package ${context.packageName} --user 0").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        return AtlasWidgetHost(context)
    }
    @Test fun configurationActivityCancelAndSuccessUsePendingBinding() {
        val host=host()
        val info=host.providers().first { it.provider==ComponentName(context,FixtureConfigWidget::class.java) }
        for (success in listOf(false,true)) {
            assertTrue(host.begin(info,"p",CellPlacement("w"),listOf(CellSize())))
            val id=host.pending!!.id
            compose.runOnIdle { assertTrue(host.configure(compose.activity)) }
            assertEquals("configure",host.pending!!.stage)
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(if(success) "Finish widget" else "Cancel widget")).perform(androidx.test.espresso.action.ViewActions.click())
            assertEquals(id,FixtureConfigurationActivity.receivedId)
            assertEquals(if(success) android.app.Activity.RESULT_OK else android.app.Activity.RESULT_CANCELED,FixtureConfigurationActivity.lastResult)
            if(success) {
                val committed=host.commit(CellWorkspace(profiles=listOf(ProfileLayout("p"))))
                assertEquals(id,committed.cells.single().appWidgetId)
                host.delete(id)
            } else { host.cancel();assertNull(host.info(id)) }
        }
    }
    @Test fun allocationBindingCancelAndRestartCleanup() {
        val host = host()
        host.cancel()
        val provider = host.providers().first { it.provider == ComponentName(context, FixtureWidget::class.java) }
        assertTrue(host.begin(provider,"p",CellPlacement("w"),listOf(CellSize())))
        val id = host.pending!!.id
        assertNotNull(host.info(id))
        val restored = AtlasWidgetHost(context)
        assertEquals(id,restored.pending!!.id)
        restored.cancel()
        assertNull(host.info(id))
        assertFalse(host.host.appWidgetIds.contains(id))
        val orphan = host.host.allocateAppWidgetId()
        host.reconcile(CellWorkspace())
        assertFalse(host.host.appWidgetIds.contains(orphan))
    }
    @Test fun fixedProviderHintsDoNotOverrideItsMinimumDpSize() {
        val host=host()
        val info=host.providers().first { it.provider == ComponentName(context,FixtureWidget::class.java) }
        info.targetCellWidth=1
        info.minWidth=(150*context.resources.displayMetrics.density).toInt()
        info.resizeMode=android.appwidget.AppWidgetProviderInfo.RESIZE_NONE
        assertTrue(host.sizes(info,80f,100f,GridSpec(4,5),8f).all { it.x>=3 })
    }
    @Test fun hostedWidgetReceivesCenterClickInsideMixedStack() {
        val host = host()
        host.cancel()
        val provider = host.providers().first { it.provider == ComponentName(context, FixtureWidget::class.java) }
        val placement = CellPlacement("w",spanX=2,spanY=2)
        assertTrue(host.begin(provider,"seed",placement,listOf(CellSize(2,2))))
        var workspace = host.commit(CellWorkspace(profiles=listOf(ProfileLayout("seed"))))
        val id = workspace.cells.single().appWidgetId!!
        FixtureWidget().onUpdate(context,host.manager,intArrayOf(id))
        workspace = CellEditor.add(workspace,"seed",Cell("clock","builtin",widget="clock"),CellPlacement("clock",x=2,spanX=2,spanY=2))
        workspace = CellEditor.stack(workspace,"seed","clock","w","s")
        context.getSharedPreferences("fixture",0).edit().clear().commit()
        host.start()
        var state by mutableStateOf(workspace)
        compose.setContent { AtlasTheme(StyleSheet()) { LauncherScreen(LauncherSpec(id="test",name="Test",applicationId="dev.launcheratlas.test"),state,emptyList(),true,{}, {}, { state=it }, {}, {}, host) } }
        compose.waitForIdle()
        // Espresso traverses the real RemoteViews Button, proving the edge rail leaves its center interactive.
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Widget action")).perform(androidx.test.espresso.action.ViewActions.click())
        compose.waitUntil(5000) { context.getSharedPreferences("fixture",0).getBoolean("clicked",false) }
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.ListView::class.java)).perform(androidx.test.espresso.action.ViewActions.swipeUp())
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.ListView::class.java)).check { view, _ -> assertTrue((view as android.widget.ListView).firstVisiblePosition>0) }
        compose.runOnIdle { assertEquals("w",CellEditor.active(state,state.activeProfileId!!,"s")?.id) }
        compose.onNodeWithContentDescription("Stack 다음").performClick()
        compose.onNodeWithContentDescription("clock").assertExists()
        compose.runOnIdle { assertNotNull(host.info(id)) }
        host.stop(); host.delete(id)
    }
}
