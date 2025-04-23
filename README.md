![Screenshot 2025-04-23 152703](https://github.com/user-attachments/assets/26414e77-1d2f-4f31-91a4-17eeb22af91a)📱 Smart Student Attendance Tracker
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
![Screenshot 2025-04-23 152729](https://github.com/user-attachments/assets/f2089fd7-d863-47ea-b424-d1386f02ede1)
![![Screenshot 2025-04-23 152723](https://github.com/user-attachments/assets/bb0c54e8-c8ba-4531-b07e-c3f9c9612f20) Screenshot 2025-04-23 152703.png…]()
![Screenshot 2025-04-23 152645](https://github.com/user-attachments/assets/a73d00ba-3e71-4a7f-a0fc-ccde31f56df0)


📄 License
This project is licensed under the [MIT License](./LICENSE). You are free to use, modify, and distribute the code with proper attribution.
✨ Credits
Developed with 💙 by [@7amed3li](https://github.com/7amed3li)  
Contact: [Feel free to open an issue](https://github.com/7amed3li/student-attendance-tracker/issues) for feature requests or questions.
