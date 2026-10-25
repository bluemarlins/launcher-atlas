# Launcher Atlas

Three Android home screens built from one shared Kotlin / Jetpack Compose core.

| Launcher | Direction | Source |
|---|---|---|
| Paper | Quiet colors, serif typography, restrained motion | [Paper](launchers/paper) |
| Clay | Warm colors, rounded shapes, soft depth | [Clay](launchers/clay) |
| Neon | Dark surfaces, bright accents, perspective transitions | [Neon](launchers/neon) |

| Paper | Clay | Neon |
|---|---|---|
| <img src="gallery/paper-emulator.png" width="180" alt="Paper on Android emulator"> | <img src="gallery/clay-emulator.png" width="180" alt="Clay on Android emulator"> | <img src="gallery/neon-emulator.png" width="180" alt="Neon on Android emulator"> |

These are actual Android 16.1 **emulator** captures using installed app icons. Physical-device verification and a human-approved AI icon set remain pending.

## Build

Install JDK 17 and Android SDK platform 36 / build-tools 36.0.0. Set `ANDROID_HOME` (or a local, uncommitted `local.properties`). From this directory:

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Windows: use `gradlew.bat`. APKs are in `launchers/<id>/build/outputs/apk/debug/`. These are **development APKs**, not production signed releases. The three application IDs are distinct, allowing side-by-side installation. No private Factory dependency is needed.

## What is supported

Android 12+ in the current personal profile, default HOME selection, app drawer/search/launch, paged Slot grids, dock, responsive folders, movable clock/date/battery Cells, third-party widgets, original app icon fallback, image mappings, wallpapers, and reduced motion.

Version 2 uses a fixed grid fitted to the available window. Apps occupy one Slot. Folders and widgets resize in grid increments; provider constraints apply to hosted widgets. Cells with the same span can form a mixed Stack. Swipe vertically to switch; hosted widgets use a visible edge rail so their inner controls remain interactive. Previous/next controls are also available from Stack editing.

See the [Cell/SPEC contract](docs/cells.md) and [v2 verification record](docs/verification-v2.md).

Portrait/landscape and compact/expanded/folding postures have separate placements and Grid settings with shared Cell content. Occluding hinges block intersecting Slots. A new profile is seeded once; existing placements are retained on updates. Before converting v1 data, the app preserves `workspace-v1.backup.json`; malformed data raises an error instead of resetting it.

Replacement of system authentication, Recents integration, work/private profiles and a real-time 3D engine are outside this release. Lock wallpaper does not replace the system lock screen. Browser previews are approximate; Android rendering is authoritative. Public widget specifications contain provider references; binding IDs and approval results stay on each device.

## Assets and verification status

The starter designs use **original installed app icons**. They do not contain a completed AI-generated icon set. Approved image assets can be added through a versioned manifest with hashes, source and distribution terms. Never treat the sample styles as evidence of image generation feasibility.

See [verification and release checklist](docs/release.md). Pixel/Galaxy physical-device testing and real-device promotional videos must be completed before a public release; no hardware compatibility is claimed until recorded. Download links are added only after a release actually exists.

## 한국어

공통 코어와 작품별 `launcher.json`·리소스로 구성한 런처 모음입니다. 위 명령으로 세 앱을 빌드하고 설치한 뒤 시스템에서 기본 홈으로 선택합니다. 빈 Slot을 탭해 앱/위젯을 추가하고, 편집 모드에서 길게 눌러 이동하거나 모서리를 드래그해 크기를 조절합니다. 항목 편집에는 페이지 이동·폴더·동일 크기 Stack·크기 선택 버튼이 있습니다. Stack은 상하 Swipe로 전환하며 타사 위젯은 오른쪽 가장자리를 사용합니다. 앱은 1×1 고정이며 Dock에는 앱과 1×1 폴더만 놓습니다. 접힘/펼침·방향별 위치는 따로 저장하고 폴더 내용과 위젯 연결은 공유합니다. 실제 하드웨어 접힘과 OEM 위젯 호환성은 별도 검증이 필요합니다.

코드는 MIT이며 외부 리소스의 배포 조건은 각 manifest를 따릅니다. 제작 도구·참조 이미지·프롬프트·서명키는 이 공개 트리에 포함하지 않습니다.
