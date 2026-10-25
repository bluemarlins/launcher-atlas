package dev.launcheratlas.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val atlasJson = Json { encodeDefaults = true }

fun canonicalComponent(value: String): String {
    val parts = value.split('/', limit = 2)
    if (parts.size != 2) return value
    return if (parts[1].startsWith(parts[0] + ".")) parts[0] + "/" + parts[1].removePrefix(parts[0]) else value
}

@Serializable data class LauncherSpec(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val applicationId: String,
    val versionCode: Int = 1,
    val versionName: String = "1.0.0",
    val coreVersion: String = "1.0.0",
    val featurePreset: String = "standard",
    val style: StyleSheet = StyleSheet(),
    val layout: LayoutSpec = LayoutSpec(),
    val assets: List<Asset> = emptyList(),
    val iconMappings: List<IconMapping> = emptyList(),
) {
    fun validate(): LauncherSpec {
        require(schemaVersion == 1 && coreVersion == "1.0.0" && featurePreset == "standard") { "Unsupported launcher SPEC" }
        require(id.matches(Regex("[a-z][a-z0-9-]{0,31}")))
        require(layout.columns in 3..6 && layout.rows in 2..6 && layout.initialPages in 1..9 && layout.dockCapacity in 1..6)
        require(style.iconSize in 40..64 && style.spacing in 0..24 && style.cornerRadius in 0..40 && style.shadow in 0..20)
        require(listOf(style.background, style.surface, style.foreground, style.accent).all { it.matches(Regex("#[0-9A-Fa-f]{6}")) })
        require(style.typography in listOf("serif", "rounded", "mono") && style.material in listOf("paper", "clay", "neon"))
        require(style.iconShape in listOf("rounded", "circle", "squircle") && style.pageTransition in listOf("slide", "fade", "depth"))
        require(style.reducedMotion in listOf("system", "always") && style.wallpaperCrop in listOf("fill", "fit"))
        require(layout.template == "header-grid-dock" && layout.components.all { it in listOf("clock", "date", "battery") })
        require(assets.map { it.id }.distinct().size == assets.size)
        require(assets.all { it.path.matches(Regex("media/[A-Za-z0-9_.-]+\\.png")) && ".." !in it.path && it.sha256.matches(Regex("[a-f0-9]{64}")) })
        val assetIds = assets.map { it.id }.toSet()
        require(style.wallpaper == null || style.wallpaper in assetIds)
        require(iconMappings.all { it.assetId in assetIds })
        require(iconMappings.map { it.component }.distinct().size == iconMappings.size)
        val initial = WorkspaceEditor.initial(this, 0)
        require(WorkspaceEditor.valid(initial)) { "Invalid initial placement" }
        return this
    }
}

@Serializable data class StyleSheet(
    val background: String = "#F5F0E8", val surface: String = "#FFFCF5",
    val foreground: String = "#252820", val accent: String = "#53624A",
    val typography: String = "serif", val material: String = "paper", val iconShape: String = "rounded",
    val iconSize: Int = 52, val showLabels: Boolean = true, val cornerRadius: Int = 20,
    val spacing: Int = 8, val shadow: Int = 2, val pageTransition: String = "slide",
    val reducedMotion: String = "system", val wallpaper: String? = null, val wallpaperCrop: String = "fill",
)
@Serializable data class LayoutSpec(
    val template: String = "header-grid-dock", val columns: Int = 4, val rows: Int = 4,
    val initialPages: Int = 3, val dockCapacity: Int = 4,
    val components: List<String> = listOf("clock", "date", "battery"), val items: List<InitialItem> = emptyList(),
)
@Serializable data class InitialItem(
    val id: String, val kind: String = "app", val component: String = "", val label: String = "",
    val children: List<String> = emptyList(), val page: Int = 0, val cell: Int = 0,
)
@Serializable data class Asset(val id: String, val path: String, val sha256: String, val source: String, val license: String)
@Serializable data class IconMapping(val component: String, val assetId: String)
