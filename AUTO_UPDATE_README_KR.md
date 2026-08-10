# PuttVision v0.5 자동업데이트

- 앱 실행 후 1.8초 뒤 자동으로 update.json 확인
- 새 versionCode가 있으면 업데이트 팝업
- APK 다운로드
- SHA-256 검증
- Android 설치창 호출
- 최초 1회 '이 출처 허용' 필요
- 이후 같은 release key로 빌드되므로 삭제 없이 덮어쓰기 가능

주의:
현재 설치된 v0.4 debug APK와 v0.5 release APK는 서명이 다르므로
v0.5 최초 설치 때만 기존 v0.4를 삭제해야 할 수 있습니다.
그 이후 v0.5+는 고정 release key를 사용합니다.

업데이트 엔드포인트:
https://puttvision-update.vercel.app/update.json

repo는 private 유지 가능하고, APK/update.json만 Vercel로 공개 배포하는 구조입니다.
