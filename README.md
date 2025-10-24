# 🎥 Zoom Clone - JavaFX Video Conferencing Application

A feature-rich **video conferencing application** built with **JavaFX**, supporting real-time video calls, screen sharing, chat, and meeting recording.

---

## 🚀 Features

- 🎦 **Video Conferencing:** Real-time video calls with multiple participants  
- 🖥️ **Screen Sharing:** Share your entire screen or specific applications  
- 🎤 **Audio/Video Controls:** Mute/unmute audio, enable/disable video  
- 💬 **Chat System:** Real-time text messaging during meetings  
- 🎥 **Meeting Recording:** Record meetings in MP4 format  
- 👥 **Participant Management:** View all participants and their status  
- 📸 **Webcam Support:** Multiple webcam detection and selection  
- 🗃️ **Database Integration:** User authentication and meeting history  
- 💻 **Cross-Platform:** Runs on Windows, macOS, and Linux  

---

## 🛠️ Technologies Used

| Category | Technology |
|-----------|-------------|
| Programming Language | Java 17 |
| UI Framework | JavaFX 17 |
| Video Processing | JavaCV / FFmpeg |
| Real-Time Communication | Java-WebSocket |
| Database | MySQL |
| UI Enhancements | ControlsFX |
| Webcam Access | Webcam Capture API |

---

## 📋 Prerequisites

- ☕ Java 17 or higher  
- 🧰 Maven 3.6+  
- 🗄️ MySQL Server 8.0+  
- 🎞️ FFmpeg (for recording functionality)

---

## 🔧 Installation

### 1️⃣ Clone the Repository
```bash
git clone https://github.com/Tauhid-Topu-007/zoomApp.git
cd zoom-clone
```

### 2️⃣ Database Setup
Run the following SQL script in MySQL:
```sql
CREATE DATABASE zoom_app;
USE zoom_app;

CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE meetings (
    id INT AUTO_INCREMENT PRIMARY KEY,
    meeting_id VARCHAR(100) UNIQUE NOT NULL,
    host_id INT,
    title VARCHAR(255),
    scheduled_time TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (host_id) REFERENCES users(id)
);

CREATE TABLE meeting_participants (
    id INT AUTO_INCREMENT PRIMARY KEY,
    meeting_id INT,
    user_id INT,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMP NULL,
    FOREIGN KEY (meeting_id) REFERENCES meetings(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

### 3️⃣ Configuration
Edit the database settings in your Java config:
```java
private static final String URL = "jdbc:mysql://localhost:3306/zoom_app";
private static final String USERNAME = "your_username";
private static final String PASSWORD = "your_password";
```

### 4️⃣ Build and Run
```bash
# Build the project
mvn clean compile

# Run the application
mvn javafx:run
```

---

## 🎯 Usage

### ▶️ Starting the Application
- Run `mvn javafx:run`
- Choose to:
  - Start a new meeting  
  - Join an existing meeting  
  - Schedule a meeting  

### 🧑‍💼 Hosting a Meeting
- Click **“New Meeting”**
- Configure audio/video settings  
- Share the meeting ID  
- Manage participants in real time  

### 👋 Joining a Meeting
- Click **“Join Meeting”**
- Enter meeting ID  
- Configure audio/video  
- Join and chat in real time  

### 🕹️ During a Meeting
- 🎥 Toggle camera  
- 🎤 Mute/unmute microphone  
- 🖥️ Share screen  
- 💬 Chat with participants  
- ⏺️ Start/stop recording  
- 👥 View participant list  

---

## 📁 Project Structure

```
src/main/java/org/example/zoom/
├── controllers/
│   ├── MainController.java
│   ├── MeetingController.java
│   └── AuthController.java
├── models/
│   ├── User.java
│   ├── Meeting.java
│   └── Participant.java
├── services/
│   ├── VideoService.java
│   ├── AudioService.java
│   ├── WebSocketService.java
│   └── DatabaseService.java
├── utils/
│   ├── WebcamUtils.java
│   ├── FFmpegRecorder.java
│   └── Config.java
└── HelloApplication.java
```

---

## 🎥 Key Components

### 🧩 Video Service
- Manages webcam capture and video streaming  
- Supports multiple webcams  
- Handles quality and device settings  

### 🔊 Audio Service
- Manages microphone input and audio stream  
- Supports live monitoring and device switching  

### 🌐 WebSocket Service
- Real-time communication between participants  
- Handles signaling and chat messages  

### 💾 Recording Service
- Records meetings in MP4 format  
- Uses FFmpeg for high-quality encoding  

---

## ⚙️ Configuration

### `application.properties`
```properties
# Database
db.url=jdbc:mysql://localhost:3306/zoom_app
db.username=your_username
db.password=your_password

# WebSocket
websocket.port=8887
websocket.host=localhost

# Video
video.width=1280
video.height=720
video.fps=30

# Audio
audio.sample-rate=44100
audio.channels=2
```

### FFmpeg Setup
Ensure **FFmpeg** is installed and available in your system PATH.  
You can verify by running:
```bash
ffmpeg -version
```

---

## 🐛 Troubleshooting

| Issue | Possible Fix |
|--------|---------------|
| Webcam not detected | Close other apps using webcam or reinstall drivers |
| Audio not working | Check mic permissions or device selection |
| Recording fails | Verify FFmpeg installation and disk permissions |
| Database errors | Check MySQL server, credentials, and schema |

---

## 🤝 Contributing

1. Fork the repository  
2. Create a feature branch  
   ```bash
   git checkout -b feature/AmazingFeature
   ```
3. Commit changes  
   ```bash
   git commit -m "Add some AmazingFeature"
   ```
4. Push to branch  
   ```bash
   git push origin feature/AmazingFeature
   ```
5. Open a Pull Request  

---

## 📝 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- [JavaCV](https://github.com/bytedeco/javacv) and **FFmpeg** for video processing  
- [Webcam Capture API](https://github.com/sarxos/webcam-capture) for webcam access  
- [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) for real-time communication  
- **JavaFX Team** for the modern UI framework  

---

## 📞 Support

If you encounter issues or have questions, please open an **issue** on GitHub or contact the development team.

---

### 🌟 Star this repo if you like it!  
Your support motivates future development ❤️
