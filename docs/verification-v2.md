# v2 검증 기록 — 2026-10-26

이 기록은 개발용 빌드와 에뮬레이터 결과다. 실제 Galaxy Fold/Pixel 호환성, 사용자 수용, production 서명 검증을 대신하지 않는다.

| 검사 | 결과 |
|---|---|
| Kotlin/JVM | 27 tests, 실패 0 |
| Android UI | 전용 API36.1 에뮬레이터, 9 tests, 실패 0 |
| Paper·Clay·Neon | versionCode2 / versionName2.0.0, build·lint 통과 |
| 독립 소스 | 새 export 디렉터리에서 세 앱 test/build/lint 통과 |
| 업데이트 | v1 APK 설치 데이터에서 v2 업데이트, v1 backup·Cell ID 보존 확인 |
| HOME·앱 추가·프로세스 종료 | 세 앱 설치·HOME 전환·앱 추가·배치 재시작 확인 |
| 화면 전환 | 가로 회전·display size override와 기존 세로 배치 복원 확인 |
| 접근성 설정 | font scale1.6, 모션 축소 설정에서 화면 확인 |

단위 테스트는 부분 점유·경계·resize rollback, 앱1×1, 혼합 Stack·회전별 상태, 공간 없는 꺼내기, migration 거부, profile별 위젯 제약, 새 화면의 최신 사용자 배치 복사, 긴 폴더의 가로 화면 적응, 공유 Stack 원자성, Stack 폴더의 마지막 앱 꺼내기, 고정 provider의 보류·복원을 포함한다.

Android UI 테스트에는 앱 검색·추가·최신 workspace 드래그·초기화 직후 profile 생성, Stack 전환, AppWidgetHost 바인딩·취소·pending 복원·orphan 정리, provider 최소 dp 크기, 실제 RemoteViews 버튼과 ListView 스크롤, 설정 Activity 취소/완료가 포함된다.

위젯 검사는 테스트 APK의 fixture provider를 사용한다. 바인딩 권한은 에뮬레이터 shell로 부여하며 해당 테스트는 물리 기기에서 건너뛴다. 이 결과는 실제 사용자의 승인이나 모든 OEM provider의 호환성을 증명하지 않는다. 설정 Activity에서 시스템 bar inset을 적용해 API36의 edge-to-edge 상태도 반영했다.

에뮬레이터 부팅 때 Pixel Launcher/SystemUI의 ANR 팝업이 테스트 창을 가렸다. 실패 화면으로 원인을 확인하고 팝업을 닫은 뒤 UI 검사를 다시 통과했다. 초기 실패를 제품 호환성 성공으로 계산하지 않았다.

실제 접힘과 가려지는 경첩 하드웨어, 승인/설정 도중 강제 프로세스 종료 복귀, OEM 위젯 제거·재설치, 실기기 전환 성능은 추가 실기기 검증 대상으로 남는다. runtime pending과 provider 누락 처리는 구현되어 있다.
