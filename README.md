📱 Smart Student Attendance Tracker
An all-in-one Android application for managing student attendance using QR codes, local SQLite database, Firebase Realtime Database, and Excel file integration. This project ensures accurate and fast attendance tracking with a user-friendly interface.

🚀 Main Features
✅ Automatic QR code generation per session.

📷 QR scanning for quick student check-in.

📁 Excel file reading and updating via Apache POI.

🔥 Realtime sync with Firebase for centralized attendance records.

🧠 Automatic validation of student sessions and eligibility.

🧾 Instant attendance reports with Present/Absent status.

🔐 Permission handling and secure local data storage.

🎯 Project Purpose
This project was developed as a practical solution for tracking student attendance in classrooms or training environments. It focuses on:

Speed

Accuracy

Ease of Use

Export & Reporting Capability

🛠️ Tech Stack
Java (Android SDK)

Firebase Realtime Database

SQLite

Apache POI (for Excel support)

ZXing (QR code generator & scanner)

💡 User Flow
Student Interface: Enter student ID → Scan QR or use manual entry → Get confirmation.

Admin Interface: Upload Excel → Generate QR → Sync with Firebase → Export attendance.

All actions update both local and cloud databases.

📂 Project Structure
📦 StudentAttendanceApp
├── app/src/main/java/com/example/test/
│   ├── MainActivity.java
│   ├── AdminActivity.java
│   ├── StudentActivity.java
│   ├── DBHelper.java
│   └── DisplayActivity.java
├── app/src/main/res/layout/
│   └── [All UI XML files]
├── app/google-services.json
└── README.md
📸 Screenshots
(Screenshots will be added soon in the next commit.)

📄 License
This project is licensed under the [MIT License](./LICENSE). You are free to use, modify, and distribute the code with proper attribution.
✨ Credits
Developed with 💙 by [@7amed3li](https://github.com/7amed3li)  
Contact: [Feel free to open an issue](https://github.com/7amed3li/student-attendance-tracker/issues) for feature requests or questions.
