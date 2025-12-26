# Dalo - YouTube Video & Audio Downloader

Dalo는 안드로이드 환경에서 유튜브 영상을 비디오나 오디오로 편리하게 내려받을 수 있도록 도와주는 오픈 소스 애플리케이션입니다.

## Download (다운로드)
[![Download APK](https://img.shields.io/badge/Download-APK-brightgreen.svg)](https://github.com/Li-Bertygi/Dalo/releases/latest)
> **주의:** 최초 설치 시 '출처를 알 수 없는 앱' 경고가 뜰 수 있습니다. 본인이 직접 빌드한 안전한 파일이므로 '무시하고 설치'를 진행해 주세요.

----

## 📖 Dalo 사용 가이드 (User Guide)

### 한국어

#### 🚀 사용 방법
1. **URL 입력**: 다운로드하고 싶은 유튜브 영상의 링크(URL)를 입력란에 붙여넣으세요.
2. **형식 선택**: **음악(Audio)** 또는 **동영상(Video)** 버튼을 눌러 원하는 파일 형식을 선택하세요.
3. **다운로드 시작**: **다운로드** 버튼을 클릭하면 다운로드가 시작됩니다.

#### ✨ 주요 특징
* **백그라운드 지원**: 앱을 닫거나 화면을 꺼도 다운로드는 백그라운드에서 계속 진행됩니다.
* **파일 저장 경로**: 다운로드된 파일은 기기의 `Download/Dalo/Music` 또는 `Download/Dalo/Video` 폴더에 저장됩니다.
* **다운로드 설정**: 설정 메뉴(우측 상단 톱니바퀴)에서 선호하는 **음질, 해상도, FPS(프레임)**를 지정할 수 있습니다.
  > *참고: 설정한 해상도나 FPS를 원본 영상이 지원하지 않는 경우, 가장 적합한 옵션으로 자동 조정되어 다운로드됩니다.*
  
### English

#### 🚀 How to Use
1. **Enter URL**: Paste the YouTube video link (URL) into the input field.
2. **Select Format**: Choose your desired format by tapping **Music (Audio)** or **Video**.
3. **Start Download**: Click the **Download** button to begin.

#### ✨ Key Features
* **Background Download**: Downloads continue in the background even if you close the app or turn off the screen.
* **Storage Location**: Files are saved in the `Download/Dalo/Music` or `Download/Dalo/Video` directory on your device.
* **Preferences**: You can configure your preferred **Audio Quality, Video Resolution, and FPS** in the Settings menu.
  > *Note: If the source video does not support the selected resolution or FPS, the app will automatically download the closest available option.*

---

## Disclaimer (면책 조항)
- **English:** This project is for educational and research purposes only. Use of this tool for downloading copyrighted content without permission may violate the YouTube Terms of Service. The developer is not responsible for any misuse of this application.
- **한국어:** 이 프로젝트는 교육 및 연구 목적으로만 제작되었습니다. 허가 없이 저작권이 있는 콘텐츠를 다운로드하는 행위는 유튜브 서비스 약관을 위반할 수 있으며, 이 프로그램을 사용하여 발생하는 모든 법적 책임은 사용자 본인에게 있습니다.

---

## Key Features (주요 기능)
- **Video Download:** 다양한 해상도 및 FPS 선택 지원.
- **Audio Download:** 고음질 m4a 추출 기능 제공.
- **Background Support:** 앱이 백그라운드에 있어도 알림창을 통해 다운로드 진행 상황을 실시간으로 확인할 수 있습니다.

---

## Tech Stack (기술 스택)
- **Language:** Kotlin, Python (via Chaquopy).
- **Core Library:** yt-dlp.
- **Architecture:** Android Service with Foreground Notification.

---

## License
This project is licensed under the **MIT License**.