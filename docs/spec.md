# Launcher SPEC v2

`launchers/<id>/src/main/assets/launcher.json` is the complete, offline runtime contract. [JSON Schema](launcher-spec.schema.json) describes the editable format. The Android parser rejects unsupported schema/core versions.

| Group | Contract |
|---|---|
| Identity | `schemaVersion=2`, `id`, `name`, stable `applicationId`, increasing `versionCode`, `versionName`, `coreVersion=2.0.0` |
| Style | Background/surface/foreground/accent `#RRGGBB`, typography, shape, icon size, labels, spacing, shadow, transition, reduced motion, optional wallpaper asset and crop |
| Layout | Cell grid and dock, base columns 3–6 / rows 2–6, initial pages 1–9, dock 1–6, shared Cells and profile-specific span placements |
| Assets | Unique ID, `media/<safe-name>.png`, SHA-256, source and distribution terms |
| Icon mappings | Android `package/activity` component → asset ID; unmapped/missing image uses original app icon |

`com.example/com.example.Main` and `com.example/.Main` identify the same component. Factory writes the short form; Android normalizes both. Personal-profile serial numbers are assigned when the defaults become a user's workspace.

v2 separates `layout.cells` from `layout.placements` or explicit `layout.profiles`. A Cell contains app/folder/builtin/widget/Stack content. A placement contains `cellId`, `pageId`, `x`, `y`, `spanX`, `spanY`; `pageId=-1` means dock. All occupied Slots must be unique. The starter specs contain movable builtin widgets; users add their installed apps. See the [Cell contract](cells.md) for mixed Stack and display-profile rules. The legacy template identifier `header-grid-dock` remains for import compatibility; v2 has no fixed header area.

The installed user's `workspace-v2.json` is private app state, separate from this file. Existing profile grids/pages/placements survive a style update. Explicit layout reset applies new defaults to all displays. Missing apps retain their placement so reinstalling the same component restores it. v1 imports are supported and Android preserves `workspace-v1.backup.json` before converting legacy items. Runtime widget binding IDs are never part of the public SPEC.

`material` records resource art direction; color, shapes, shadows, imported raster artwork and transition parameters determine actual rendering. It does not synthesize a 3D bitmap or enable a 3D engine.

배포용 리소스는 완성 파일과 출처·배포조건을 함께 제공하세요. 프롬프트·원본 참조·서명키·Factory 데이터는 SPEC에 넣지 않습니다. Sheet에 Kotlin·shell 코드를 삽입하는 확장 방식은 지원하지 않습니다.
