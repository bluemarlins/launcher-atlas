# Launcher SPEC v1

`launchers/<id>/src/main/assets/launcher.json` is the complete, offline runtime contract. [JSON Schema](launcher-spec.schema.json) describes the editable format. The Android parser rejects unsupported schema/core versions.

| Group | Contract |
|---|---|
| Identity | `id`, `name`, stable `applicationId`, increasing `versionCode`, `versionName`, `coreVersion=1.0.0` |
| Style | Background/surface/foreground/accent `#RRGGBB`, typography, shape, icon size, labels, spacing, shadow, transition, reduced motion, optional wallpaper asset and crop |
| Layout | `header-grid-dock`, columns 3–6, rows 2–6, initial pages 1–9, dock 1–6, clock/date/battery header slots, initial items |
| Assets | Unique ID, `media/<safe-name>.png`, SHA-256, source and distribution terms |
| Icon mappings | Android `package/activity` component → asset ID; unmapped/missing image uses original app icon |

`com.example/com.example.Main` and `com.example/.Main` identify the same component. Factory writes the short form; Android normalizes both. Personal-profile serial numbers are assigned when the defaults become a user's workspace.

An initial item has `id`, `kind` (`app` or `folder`), `page`, `cell` and either `component` or folder `children` plus `label`. `page=-1` means dock; cell indexes start at 0 in row order. IDs and occupied cells must be unique. The starter specs intentionally have empty placements; users add installed apps through the launcher.

The installed user's `workspace-v1.json` is private app state, separate from this file. Existing columns/rows/pages/placements survive a style update. Explicit layout reset applies new defaults. Missing apps retain their placement so reinstalling the same component restores it.

`material` records resource art direction; color, shapes, shadows, imported raster artwork and transition parameters determine actual rendering. It does not synthesize a 3D bitmap or enable a 3D engine.

배포용 리소스는 완성 파일과 출처·배포조건을 함께 제공하세요. 프롬프트·원본 참조·서명키·Factory 데이터는 SPEC에 넣지 않습니다. Sheet에 Kotlin·shell 코드를 삽입하는 확장 방식은 지원하지 않습니다.
