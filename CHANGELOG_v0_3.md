# PuttVision Screen v0.3 HFR Precision

추가된 핵심:
- 기기 Camera2 고속모드 자동 조회
- 240fps fixed 최우선
- 120fps fixed fallback
- HFR이 없으면 기존 v0.2 일반 실시간 추적
- CameraX HighSpeedVideoSessionConfig + VideoCapture
- PRECISION READY 시 HFR 녹화 자동 시작
- Preview는 공 움직임/임팩트 트리거만 담당
- 임팩트 후 650ms 자동 추가 캡처
- HFR MP4 실제 프레임 재분석
- HFR 영상 내부 QR 4개 재인식/재캘리브레이션
- 볼스피드 / 출발각 / 헤드스피드 / 페이스각 / 패스 / F2P
- smash 근사 / 임팩트 좌우 오프셋 근사
- 분석 결과를 TV 가상그린 물리엔진에 전달

중요:
CameraX 고속 세션에서 VideoCapture는 120/240fps로 동작하지만 Preview는 보통 표준 FPS입니다.
그래서 Preview 수치를 정밀값으로 쓰지 않고, 저장된 고속 프레임을 다시 읽어 측정하는 구조입니다.

실물 테스트 전 Alpha이므로 상용 센서급 정확도를 아직 보장하지 않습니다.
첫 실폰 테스트 후 조명/색상/임팩트 threshold와 노출/셔터 잠금을 튜닝해야 합니다.
