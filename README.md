# DayKnit Widget Demo

DayKnit 안드로이드 **홈 위젯 UI**(RemoteViews)만 떼어낸 독립 프로젝트입니다.
앱 본체·Supabase·시크릿은 전혀 포함하지 않습니다 — 위젯 모양을 CI에서 렌더해
스크린샷으로 확인하기 위한 용도(데모)입니다.

## 포함된 위젯
- **월간 캘린더** (`MonthWidgetProvider`) — 정적 35칸 그리드, 둥근 색칩
- **할 일** (`TodoWidgetProvider`) — 날짜 그룹 아젠다, 색 링 체크표시
- **타임라인(데이뷰)** (`TimelineWidgetProvider`) — 시간대별 위치 블록(Android 12+)
- 설정(`WidgetConfigActivity`), 그날 팝업(`DayPopupActivity`), 빠른추가(`QuickAddActivity`)

## 렌더(스크린샷)
`.github/workflows/render.yml` 이 push 마다 실행됩니다:
1. 순수 Android 디버그 APK 빌드(Gradle, NDK/Rust 없음 → 빠름)
2. CI 에뮬레이터(API 33)에 설치 → `WidgetGalleryActivity` 가 위젯들을 실제 렌더
3. 화면 캡처를 **artifact(`widget-screenshots`)** 로 업로드

Public repo 라 GitHub Actions 무료·무제한입니다.

## 메인 앱
실제 앱은 별도 비공개 저장소에서 Tauri 플러그인(`tauri-plugin-dayknit-widget`)으로
이 위젯 코드를 사용합니다. 이 데모는 그 위젯 소스를 복사한 미러입니다.
