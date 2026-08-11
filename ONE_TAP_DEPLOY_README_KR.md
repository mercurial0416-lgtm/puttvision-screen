# PuttVision v0.6 — ZIP → GitHub 원터치 배포

## 목표
앞으로 ChatGPT가 만든 PuttVision 패치 ZIP을 폰에서 선택하면 앱이 직접
`mercurial0416-lgtm/puttvision-screen`의 `main` 브랜치에 한 커밋으로 반영합니다.
그 뒤 GitHub Actions가 signed APK를 private GitHub Release로 자동 발행하고 앱이 같은 GitHub 연결로 새 APK를 확인/다운로드합니다.

## 최초 1회만
`ZIP → GitHub` 버튼을 처음 누르면 GitHub Fine-grained personal access token을 입력합니다.
권장 설정:
- Resource owner: mercurial0416-lgtm
- Repository access: Only select repositories → puttvision-screen
- Repository permissions: Contents = Read and write
- Repository permissions: Workflows = Read and write

토큰은 APK 코드에 포함되지 않습니다. 사용자가 입력한 토큰은 Android Keystore AES/GCM 키로
암호화되어 앱의 private SharedPreferences에 저장됩니다.

## 이후 사용
1. ChatGPT에서 PuttVision 수정 ZIP 다운로드
2. PuttVision → `ZIP → GitHub`
3. ZIP 선택
4. 앱이 blob/tree/commit/ref 순서로 GitHub REST API에 반영
5. Actions 자동 실행
6. CI가 끝날 때쯤 앱이 업데이트를 자동 재확인
7. 새 APK가 준비되면 기존 업데이트 흐름으로 설치

## 배포 ZIP 형식
루트 또는 단일 최상위 폴더 안에 `puttvision-deploy.json`을 둘 수 있습니다.

```json
{
  "schema": 1,
  "message": "commit message",
  "stripPrefix": "OptionalTopFolder/",
  "delete": ["obsolete/file.kt"]
}
```

manifest가 없어도 단일 최상위 폴더는 자동으로 벗겨집니다.
`.git`, `.gradle`, `build`, `local.properties`는 자동 제외됩니다.
ZIP 경로 탈출(`..`)은 차단합니다.

## 안전장치
- target repo/branch는 앱 코드에서 `mercurial0416-lgtm/puttvision-screen` / `main`으로 고정
- ZIP 최대 25MB, 최대 500개 파일
- non-force fast-forward ref update
- GitHub API 오류가 나면 main ref를 갱신하지 않으므로 부분 커밋이 main에 적용되지 않음
- ZIP 배포 버튼 길게 누르면 GitHub 토큰을 다시 설정할 수 있음

## v0.6 추가 편의 기능
- 앱 설치 후 런처에 `PuttVision Deploy` 아이콘이 하나 더 생깁니다.
- ZIP 선택 없이도 Android 파일 앱에서 ZIP → 공유 → `PuttVision Deploy`를 누르면 바로 배포할 수 있습니다.
- 배포 후 Actions가 private GitHub Release에 `puttvision.apk` + SHA-256을 올립니다.
- PuttVision은 저장된 GitHub 자격증명으로 private Release를 읽기 때문에 소스 repo와 APK를 공개할 필요가 없습니다.
