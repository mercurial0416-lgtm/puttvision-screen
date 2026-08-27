# PuttVision 자동 업데이트 / 버전 정책

사용자에게 보이는 버전은 **앱 버전 하나만** 사용합니다.

- 표시 형식: `0.7.xxx`
- 앱 설치 정보 `versionName`: `0.7.xxx`
- 업데이트 manifest `versionName`: `0.7.xxx`
- GitHub Release 제목: `PuttVision 0.7.xxx`
- 업데이트 팝업: `PuttVision 0.7.xxx 업데이트`

`V176`, `V158`, `pv-100206`, `versionCode=100206` 같은 값은 개발/배포 내부 식별자이며 사용자 버전으로 표시하지 않습니다.

## 업데이트 흐름

1. 앱이 공개 `update-v2.json`을 확인합니다.
2. manifest의 `versionCode`가 설치된 앱보다 높으면 업데이트를 제안합니다.
3. 사용자에게는 manifest와 APK의 동일한 `0.7.xxx` 버전만 표시합니다.
4. APK 다운로드 후 SHA-256, 패키지명, 서명 계보, versionCode를 검증합니다.
5. 검증이 모두 통과해야 Android 설치 화면으로 넘어갑니다.

## 배포 규칙

프로덕션 배포는 `current-main` 직접 릴리스 경로만 사용합니다. 과거 V158/V152/Vxxx 전용 릴리스 트리거는 신규 배포에 사용하지 않습니다.

내부 태그나 기능 세대 번호가 무엇이든 사용자가 확인해야 할 최신 버전은 **`0.7.xxx` 하나**입니다.
