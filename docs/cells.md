# Cell workspace v2

`Slot`은 Grid의 최소 칸이고 `Cell`은 앱·폴더·내장/타사 위젯·Stack 콘텐츠입니다. 위치와 span은 Cell 콘텐츠와 분리해 화면별로 저장합니다.

- 공개 SPEC: `layout.cells`, `layout.placements`, 선택적 `layout.profiles`.
- Cell ID와 Stack 구성원 ID는 안정적인 참조입니다. 앱 component는 개인 프로필의 설치 앱에 연결합니다.
- 배치는 `cellId`, `pageId`, `x`, `y`, `spanX`, `spanY`입니다. `pageId=-1`은 Dock입니다.
- profile ID는 `compact/expanded` + `portrait/landscape` + `flat/half-open`입니다. 예: `expanded-landscape-half-open`.
- profile을 명시하면 각 profile의 Grid·배치·`stacks`가 기본값입니다. 없으면 공통 기본 배치를 사용합니다.
- 앱은 1×1 고정입니다. Dock에는 앱과 1×1 폴더만 배치합니다.
- Stack은 같은 span의 Cell을 참조하며 중첩하지 않습니다. 앱이 포함되면 1×1입니다.
- widget Cell에는 provider component만 넣습니다. `appWidgetId`, 사용자 serial, 승인 결과는 기기의 runtime 데이터입니다.

```json
{
  "cells": [
    {"id":"clock","kind":"builtin","widget":"clock"},
    {"id":"date","kind":"builtin","widget":"date"},
    {"id":"s","kind":"stack","members":["clock","date"]}
  ],
  "placements":[{"cellId":"s","pageId":0,"x":0,"y":0,"spanX":2,"spanY":1}]
}
```

Android는 사용 가능한 dp 영역과 density/DPI·font scale·시스템 inset·folding 정보를 사용합니다. 가용 크기에 맞지 않는 Grid 프리셋은 선택할 수 없습니다. 새로운 화면은 가장 최근에 활성화한 화면에서 한 번 복사하며 기존 화면의 배치는 유지합니다. 접힘에 가려지는 Slot은 점유할 수 없습니다.

타사 위젯의 허용 span은 화면별 dp/Slot과 provider의 min/max·resizeMode, framework padding, 스타일 간격을 함께 계산합니다. 활성 Stack 구성원만 AndroidView로 연결합니다. 타사 위젯은 오른쪽 전환 영역을 사용하며 내부 버튼과 ListView 스크롤은 위젯으로 전달합니다. 다른 Cell은 표면에서 상하 Swipe합니다. 편집 메뉴에는 이전/다음, 순서와 꺼내기 버튼도 있습니다.

화면에 들어갈 수 없는 고정 provider는 runtime profile의 보류 목록에 콘텐츠·연결을 보존합니다. 홈에 보류 개수를 표시하며 설정에서 확인하고 Grid 변경 후 재배치를 시도할 수 있습니다. 작은 화면에 맞추려고 provider 내부 화면을 임의로 축소하지 않습니다.

공유 Stack의 구성원 변경과 Cell 제거는 모든 화면에 원자적으로 적용합니다. 제거 버튼에 범위를 표시합니다. Stack에서 꺼낼 공간이 없으면 원본을 유지합니다. 빈 폴더가 Stack 안에 있으면 폴더를 제거하고 남은 구성원이 하나인 Stack은 해제합니다.

v1 workspace는 백업을 남긴 뒤 v2로 변환합니다. 손상된 데이터는 오류를 표시하고 보존합니다. 스타일 업데이트는 사용자 배치를 덮어쓰지 않으며 모든 화면 초기화는 사용자 확인 후 수행합니다.

실제 Fold 하드웨어, OEM provider별 호환성, 사용자 승인·production 서명 수용은 각각 별도 검증입니다.
