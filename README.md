
# 📊 Smart Student Attendance Tracker

An all-in-one Android application for managing student attendance using QR codes, local SQLite database, Firebase Realtime Database, and Excel file integration. This project ensures accurate and fast attendance tracking with a user-friendly interface.

## ✅ Main Features
- Automatic QR code generation per session.
- QR scanning for quick student check-in.
- Excel file reading and updating via Apache POI.
- Realtime sync with Firebase for centralized attendance records.
- Automatic validation of student sessions and eligibility.
- Instant attendance reports with Present/Absent status.
- Permission handling and secure local data storage.

## 🎯 Project Purpose
This project was developed as a practical solution for tracking student attendance in classrooms or training environments.  
It focuses on:
- Speed
- Accuracy
- Ease of Use
- Export & Reporting Capability

## 🧰 Tech Stack
- Java (Android SDK)
- Firebase Realtime Database
- SQLite
- Apache POI (for Excel support)
- ZXing (QR code generator & scanner)

## 🔄 User Flow
- **Student Interface**: Enter student ID → Scan QR or use manual entry → Get confirmation.
- **Admin Interface**: Upload Excel → Generate QR → Sync with Firebase → Export attendance.

All actions update both local and cloud databases.

## 📁 Project Structure
```
StudentAttendanceApp
 └── app/src/main/java/com/example/test/
     ├── MainActivity.java
     ├── AdminActivity.java
     ├── StudentActivity.java
     ├── DBHelper.java
     └── DisplayActivity.java
 └── app/src/main/res/layout/      # All UI XML files
 └── app/google-services.json
 └── README.md
```

## 📸 Screenshots
## 📱 User Interface Screenshots

### 🔹 Main Screen
![Main Screen](https://github.com/user-attachments/assets/e651c45a-5dd3-4ccf-aaa2-1874a781a6b5)

### 🔹 Admin Dashboard
![Admin Dashboard](https://github.com/user-attachments/assets/bba6d66b-6463-44c5-b641-7e4e64d8634e)

### 🔹 Attendance Details
![Attendance Details](https://github.com/user-attachments/assets/36d9cd88-66f2-43d4-89f1-2ad13177a0fe)

### 🔹 Student QR Input
![Student QR Input](https://github.com/user-attachments/assets/0b85c7f2-bf37-4105-94ec-12c580fc44cb)

## 🪪 License
This project is licensed under the [MIT License](https://opensource.org/licenses/MIT). You are free to use, modify, and distribute the code with proper attribution.

## ✨ Credits
Developed with ❤️ by [@7amed3li](https://github.com/7amed3li)  
Contact: [Open an issue](https://github.com/7amed3li/student-attendance-tracker/issues) for feature requests or questions.
