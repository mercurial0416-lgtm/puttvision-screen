# PuttVision 완전 자동 배포/업데이트 운영 가이드

## 구조

소스 저장소는 **private**로 유지합니다. `main`에 병합되면
`release-apk.yml`이 검증, 새 키로 서명, 서명 인증서 확인, SHA-256 생성까지 수행한 후
APK와 `update.json`만 별도의 **public 배포 저장소**에 push합니다. 앱은 빌드 시 주입된
`DISTRIBUTION_BASE_URL/update.json`을 실행 1.8초 뒤 확인합니다.

`versionCode`는 `100000 + GITHUB_RUN_NUMBER`로 자동 생성되므로 main 배포마다 증가합니다.
같은 workflow run을 재실행하면 동일 버전이 다시 만들어져 잘못된 중복 버전 증가도 없습니다.

## 최초 1회 설정

1. APK만 공개할 별도 public GitHub 저장소(예: `company/puttvision-downloads`)를 만듭니다.
   소스 파일이나 signing key를 이 저장소에 넣지 않습니다.
2. private 소스 저장소의 `production` Environment에 아래 **Secrets**를 등록합니다.
   - `RELEASE_KEYSTORE_BASE64`: 새 release JKS 전체를 base64 한 값
   - `RELEASE_STORE_PASSWORD`: 새 keystore 암호
   - `RELEASE_KEY_ALIAS`: 새 key alias
   - `RELEASE_KEY_PASSWORD`: 새 key 암호
   - `RELEASE_CERT_SHA256`: `keytool -list -v`에 표시되는 새 인증서 SHA-256
   - `DISTRIBUTION_TOKEN`: public 배포 저장소 `Contents: Read and write`만 허용한 fine-grained PAT
3. 같은 Environment에 아래 **Variables**를 등록합니다.
   - `DISTRIBUTION_REPOSITORY`: `company/puttvision-downloads` 형식
   - `DISTRIBUTION_BASE_URL`: 예:
     `https://raw.githubusercontent.com/company/puttvision-downloads/main`
4. Environment protection rule에 main branch만 배포 가능하도록 제한합니다.

새 키 생성 예시(암호는 명령행에 쓰지 말고 prompt에서 입력):

```bash
keytool -genkeypair -v -keystore puttvision-release.jks -alias puttvision \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 puttvision-release.jks
keytool -list -v -keystore puttvision-release.jks -alias puttvision
```

키 원본은 오프라인 백업하고 저장소에는 절대 commit하지 않습니다. `.gitignore`도 JKS,
keystore, APK를 차단합니다. 과거 v0.5에 노출됐던 `ci/puttvision-release.jks.b64`와 암호는
삭제했으며 어떤 workflow에서도 사용하지 않습니다. 해당 키는 폐기해야 합니다.

## 배포와 검증

- PR: `clean check assembleDebug`를 통과해야 합니다.
- main: `clean check assembleDebug assembleRelease` 후 `apksigner verify`를 실행합니다.
- `RELEASE_CERT_SHA256`과 실제 APK 인증서가 다르면 배포하지 않습니다.
- APK 내 `versionCode`가 자동 할당 값과 다르면 배포하지 않습니다.
- 생성한 APK의 SHA-256을 `update.json`에 넣고 즉시 다시 검증합니다.
- 검증이 모두 성공한 경우에만 public 저장소의 `releases/`와 `update.json`을 갱신합니다.

branch protection에서 `Verify pull request / verify`를 required check로 지정하고 direct push를
막아야 모든 변경이 검증된 PR을 거치게 됩니다.

## 앱 업데이트 동작과 키 교체 주의사항

앱은 HTTPS manifest와 APK만 허용하고, manifest의 64자리 SHA-256을 필수로 검사합니다.
다운로드 크기/완료 여부와 digest가 일치해야만 `FileProvider` URI로 Android 설치창을 띄웁니다.
Android 8 이상에서는 최초 1회 “이 출처 허용”이 필요합니다.

Android는 **다른 키로 서명된 APK의 덮어쓰기 설치를 거부**합니다. 따라서 노출된 예전 v0.5
키로 설치한 기기는 새 키로 서명된 첫 정식 APK를 설치하기 전에 기존 앱을 삭제해야 합니다
(앱 로컬 데이터도 백업 필요). 그 최초 전환 이후에는 새 고정 키를 계속 사용하므로 자동
업데이트가 정상적으로 덮어쓰기됩니다. 이는 손상된 기존 키를 재사용하지 않고는 우회할 수
없는 Android 보안 제약입니다.
