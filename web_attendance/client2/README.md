# 얼굴 출석 테스트 앱 (client2)

서버 API, WebSocket 스트리밍, 등록/인식을 **테스트**하기 위한 Flutter 앱입니다.

## 실행

```bash
cd web_attendance/client2
flutter pub get
flutter run
```

## 탭 구성

| 탭 | 기능 |
|----|------|
| **API 테스트** | Health, Persons, Attendances 호출, 로그 확인 |
| **스트림 테스트** | 카메라 → WebSocket 실시간 전송, 인식 결과 표시 |
| **등록/인식** | 이름+사진 등록, 선택한 사진으로 인식 테스트 |

## 설정

- 첫 화면에서 **서버 URL** 입력 (예: `http://192.168.0.10:8000` 또는 `http://10.0.2.2:8000`
- [저장] 클릭 후 각 탭에서 테스트

## 사용 순서

1. 서버 실행 (`run_server.bat`)
2. 웹 스트림 뷰어 접속 (`http://서버:8000/stream`) → [연결]
3. 이 앱 실행 → API 테스트 탭에서 Health 확인
4. 스트림 테스트 탭에서 카메라 스트리밍 확인
5. 등록/인식 탭에서 사용자 등록 후 인식 테스트
