# 액션노치 (TouchNotch)

노치·펀치홀 영역을 터치해서 손전등, 전원 메뉴, 스크린샷 등을 바로 실행하는 안드로이드 앱입니다.
**갤럭시 Z 폴드처럼 접었을 때와 펼쳤을 때 화면이 다른 기기를 위해 두 상태의 설정을 완전히 분리**했습니다.

## 다운로드

[**최신 APK 받기 → Releases**](https://github.com/Manggome/TouchNotch/releases/latest)

한 번 설치한 뒤에는 앱 안의 **업데이트 › 업데이트 확인**으로 새 버전을 바로 받아 설치할 수 있습니다.

## 제스처 · 동작

| 제스처 | 설명 |
| --- | --- |
| 싱글 터치 | 노치를 한 번 톡 |
| 더블 터치 | 두 번 연속 톡 |
| 롱 터치 | 0.38초 이상 꾹 |
| 왼쪽으로 스와이프 | 노치에서 왼쪽으로 밀기 |
| 오른쪽으로 스와이프 | 노치에서 오른쪽으로 밀기 |

각 제스처에 아래 동작을 자유롭게 연결할 수 있습니다.

- 손전등 켜기 (다시 실행하면 꺼짐)
- 전원 메뉴 열기
- 소리/진동 토글
- 맨 위로 스크롤
- 스크린샷 찍기
- 화면 녹화 시작 (다시 실행하면 정지·저장)
- 카메라 켜기
- 사용 안 함

## 터치 영역 설정

- **내 노치 찾기** — 기기가 보고하는 디스플레이 컷아웃을 읽어 크기·위치를 자동으로 맞춥니다.
  자동 감지가 안 되는 화면(폴드 메인 화면의 언더 디스플레이 카메라 등)에서는
  **위치 직접 조정** 모드로 화면 위 파란 영역을 손가락으로 끌어 맞출 수 있습니다.
- **노치 크기** — 가로 24~320dp, 세로 12~140dp
- **수평 위치** — 왼쪽 끝(-100%) ~ 가운데(0) ~ 오른쪽 끝(+100%)
- **수직 위치** — 화면 최상단부터 0~240dp
- **촉각 피드백** — 제스처가 인식될 때 진동 (세기 5~80ms)

이 모든 값이 **접었을 때 / 펼쳤을 때** 각각 따로 저장됩니다.
화면을 접거나 펴면 `smallestScreenWidthDp` 변화를 감지해 해당 프로필로 자동 전환됩니다.
판정 기준값(기본 480dp)도 앱에서 직접 조절할 수 있습니다.

## 권한

| 권한 | 필요한 이유 | 필수 |
| --- | --- | --- |
| 접근성 서비스 | 화면 위 터치 영역 표시, 전원 메뉴·스크린샷 실행 | ✅ |
| 방해 금지 접근 | 소리/진동 토글 | 해당 동작만 |
| 다른 앱 위에 표시 | 백그라운드에서 카메라·화면 녹화 실행 | 해당 동작만 |
| 알 수 없는 앱 설치 | 앱 내 업데이트 설치 | 업데이트만 |
| 알림 | 화면 녹화 중 알림 | 녹화만 |
| 마이크 | 화면 녹화에 소리 포함 | 선택 |

접근성 서비스로 읽은 화면 정보는 **맨 위로 스크롤** 동작에만 쓰이고 기기 밖으로 나가지 않습니다.
앱에는 분석·광고 SDK가 없습니다.

## 빌드

```bash
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

- JDK 17, Android SDK 35 (compileSdk 35 / minSdk 30 / targetSdk 35)
- `local.properties` 에 `sdk.dir` 을 지정해야 합니다 (예: `sdk.dir=C:/Users/이름/Android/Sdk`)

> **Windows 참고** — Android Gradle Plugin 은 프로젝트 경로에 한글이 있으면 빌드를 거부합니다.
> 한글 경로에 두고 쓰려면 ASCII 경로 정션을 만들어 그쪽에서 빌드하세요.
> ```
> mklink /J C:\TouchNotch "C:\...\액션노치"
> cd C:\TouchNotch && gradlew assembleRelease
> ```

### 버전

`version.properties` 의 `baseVersionName` 하나만 관리합니다.
GitHub Actions 는 `baseVersionName.<빌드번호>` 로 버전을 붙여 릴리스를 만듭니다 (예: `1.0.0.7`).
`versionCode` 는 `baseVersionCode * 1000 + 빌드번호` 로 항상 증가합니다.

### 서명

앱 내 업데이트가 기존 설치를 덮어쓰려면 서명이 항상 같아야 하므로,
개인용 저장소라는 전제로 `keystore/` 에 키스토어와 비밀번호를 함께 넣어두었습니다.
저장소를 공개로 쓸 계획이라면 키스토어를 GitHub Secrets 로 옮기고
`keystore/keystore.properties` 를 `.gitignore` 에 추가하세요.

## 릴리스 자동화

`main` 브랜치에 푸시하면 [워크플로](.github/workflows/release.yml)가

1. 릴리스 APK 를 빌드하고
2. `v<버전>` 태그로 GitHub 릴리스를 만들고
3. APK 를 릴리스 자산으로 올립니다.

앱의 업데이트 기능은 GitHub `releases/latest` API 를 읽어 이 자산을 내려받습니다.

## 구조

```
app/src/main/java/kr/manggome/touchnotch/
├─ model/Models.kt              제스처·동작·폴드 상태·프로필 정의
├─ data/
│  ├─ SettingsStore.kt          프로필별 설정 저장 (SharedPreferences)
│  └─ FoldDetector.kt           접힘/펼침 판별
├─ service/
│  ├─ NotchAccessibilityService.kt  오버레이 윈도우 관리 · 제스처 → 동작 연결
│  ├─ NotchTouchView.kt             투명 터치 영역 (편집 모드에서 드래그 가능)
│  └─ NotchGestureDetector.kt       좁은 영역에 맞춘 제스처 판정
├─ action/
│  ├─ ActionExecutor.kt         동작 실행
│  └─ TorchController.kt        손전등
├─ record/
│  ├─ ScreenCaptureRequestActivity.kt  녹화 권한 요청(투명 액티비티)
│  └─ ScreenRecordService.kt           MediaProjection 녹화
├─ update/Updater.kt            GitHub 릴리스 확인 · APK 다운로드 · 설치
└─ ui/                          Compose 설정 화면
```

터치 영역은 `TYPE_ACCESSIBILITY_OVERLAY` 윈도우로 띄우기 때문에
화면에 영역을 표시하는 것 자체는 '다른 앱 위에 표시' 권한 없이 동작합니다.
