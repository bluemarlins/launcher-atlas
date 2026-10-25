package dev.launcheratlas.core

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.math.ceil

@Serializable data class PendingWidget(
    val id: Int, val provider: String, val profileId: String, val placement: CellPlacement,
    val sizes: List<CellSize>, val stage: String = "bind",
    val replaceExisting: Boolean = false,
)

class AtlasWidgetHost(private val context: Context) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host = AppWidgetHost(context, 2048)
    private val preferences = context.getSharedPreferences("widget-bindings", Context.MODE_PRIVATE)
    var pending: PendingWidget?
        get() = preferences.getString("pending", null)?.let { runCatching { atlasJson.decodeFromString<PendingWidget>(it) }.getOrNull() }
        set(value) { preferences.edit().apply { if (value == null) remove("pending") else putString("pending", atlasJson.encodeToString(value)) }.commit() }
    fun start() { runCatching { host.startListening() } }
    fun stop() { runCatching { host.stopListening() } }
    fun delete(id: Int) { runCatching { host.deleteAppWidgetId(id) } }
    fun providers(): List<AppWidgetProviderInfo> = manager.installedProviders
        .sortedBy { it.loadLabel(context.packageManager).toString() }
    fun info(id: Int) = manager.getAppWidgetInfo(id)

    fun sizes(info: AppWidgetProviderInfo, slotW: Float, slotH: Float, grid: GridSpec, gap: Float = 0f): List<CellSize> {
        val density = context.resources.displayMetrics.density
        val padding = android.appwidget.AppWidgetHostView.getDefaultPaddingForWidget(context, info.provider, null)
        val padW = (padding.left + padding.right) / density + gap
        val padH = (padding.top + padding.bottom) / density + gap
        val horizontal = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val vertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        val initialX = maxOf(1, info.targetCellWidth, ceil((info.minWidth / density + padW) / slotW).toInt())
        val initialY = maxOf(1, info.targetCellHeight, ceil((info.minHeight / density + padH) / slotH).toInt())
        val minX = if (horizontal) ceil(((if (info.minResizeWidth > 0) info.minResizeWidth else info.minWidth) / density + padW) / slotW).toInt().coerceAtLeast(1) else initialX
        val minY = if (vertical) ceil(((if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight) / density + padH) / slotH).toInt().coerceAtLeast(1) else initialY
        val maxX = if (!horizontal) initialX else if (info.maxResizeWidth > 0) floorSpan(info.maxResizeWidth / density + padW, slotW).coerceAtMost(grid.columns) else grid.columns
        val maxY = if (!vertical) initialY else if (info.maxResizeHeight > 0) floorSpan(info.maxResizeHeight / density + padH, slotH).coerceAtMost(grid.rows) else grid.rows
        return (minX..maxX).flatMap { x -> (minY..maxY).map { y -> CellSize(x, y) } }.filter { it.x <= grid.columns && it.y <= grid.rows }
    }
    private fun floorSpan(dp: Float, slot: Float) = kotlin.math.floor(dp / slot).toInt()
    fun begin(info: AppWidgetProviderInfo, profileId: String, placement: CellPlacement, sizes: List<CellSize>, replaceExisting: Boolean = false): Boolean {
        cancel()
        val id = host.allocateAppWidgetId()
        pending = PendingWidget(id, info.provider.flattenToString(), profileId, placement, sizes, replaceExisting = replaceExisting)
        return manager.bindAppWidgetIdIfAllowed(id, info.provider)
    }
    fun bindIntent(): Intent? = pending?.let { Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, it.id)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, ComponentName.unflattenFromString(it.provider)) }
    fun configure(activity: Activity): Boolean {
        val p = pending ?: return false
        val info = info(p.id) ?: return false
        if (info.configure == null) return false
        pending = p.copy(stage = "configure")
        host.startAppWidgetConfigureActivityForResult(activity, p.id, 0, CONFIGURE_REQUEST, null)
        return true
    }
    fun cancel() { pending?.let { delete(it.id) }; pending = null }
    fun commit(s: CellWorkspace): CellWorkspace {
        val p = pending ?: return s
        if (info(p.id) == null) { cancel(); return s }
        val c = Cell(p.placement.cellId, "widget", provider = p.provider, appWidgetId = p.id)
        val added = if (p.replaceExisting && s.cells.any { it.id == c.id && it.kind == "widget" }) s.copy(cells = s.cells.map { if (it.id == c.id) c else it },
            profiles = s.profiles.map { if (it.id == p.profileId) it.copy(placements = it.placements.map { if (it.cellId == c.id) p.placement else it }) else it })
            else CellEditor.add(s, p.profileId, c, p.placement)
        val candidate = if (added != s) added.copy(profiles = added.profiles.map { if (it.id == p.profileId) it.copy(constraints = it.constraints.filterNot { it.cellId == c.id } + CellConstraint(c.id, p.sizes)) else it }) else s
        val next = candidate.takeIf(CellEditor::valid) ?: s
        if (next == s) cancel() else pending = null
        return next
    }
    fun createView(context: Context, id: Int) = info(id)?.let { host.createView(context, id, it) }
    fun updateSize(view: android.appwidget.AppWidgetHostView, width: Float, height: Float) {
        view.updateAppWidgetSize(Bundle(), listOf(SizeF(width.coerceAtLeast(1f), height.coerceAtLeast(1f))))
    }
    fun reconcile(workspace: CellWorkspace) {
        val used = workspace.cells.mapNotNull { it.appWidgetId }.toSet() + listOfNotNull(pending?.id)
        host.appWidgetIds.filterNot(used::contains).forEach(::delete)
    }
    companion object { const val CONFIGURE_REQUEST = 4101 }
}
