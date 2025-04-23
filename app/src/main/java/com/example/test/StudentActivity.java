package com.example.test;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.journeyapps.barcodescanner.ScanIntentResult;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StudentActivity extends AppCompatActivity {
    private static final String TAG = "StudentActivity";
    private static final int CAMERA_PERMISSION_CODE = 101;
    private static final int SCAN_REQUEST_CODE = 102;

    private EditText studentIdEditText;
    private DBHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student);
        Log.d(TAG, "StudentActivity created");

        dbHelper = new DBHelper(this);
        initViews();
    }

    private void initViews() {
        studentIdEditText = findViewById(R.id.studentIdEditText);
        findViewById(R.id.btnScanBarcode).setOnClickListener(this::startScan);
        findViewById(R.id.btnManualEntry).setOnClickListener(this::manualEntry);
    }

    public void startScan(View view) {
        if (checkCameraPermission()) {
            launchScanner();
        } else {
            requestCameraPermission();
        }
    }

    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return false;
        }
        return true;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
    }

    private void launchScanner() {
        Log.d(TAG, "Launching QR Scanner");
        try {
            Intent intent = new Intent(this, CaptureActivityPortrait.class);
            intent.putExtra("PROMPT_MESSAGE", "Scan the QR Code provided by Admin");
            intent.putExtra("BEEP_ENABLED", true);
            intent.putExtra("ORIENTATION_LOCKED", true);
            startActivityForResult(intent, SCAN_REQUEST_CODE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch scanner: " + e.getMessage(), e);
            showError("فشل في تشغيل الماسح: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SCAN_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ScanIntentResult result = ScanIntentResult.parseActivityResult(resultCode, data);
            if (result != null && result.getContents() != null) {
                processScannedData(result.getContents());
            } else {
                showError("لم يتم مسح الكود أو تم إلغاؤه!");
            }
        } else {
            showError("مفيش بيانات رجعت من السكانر!");
        }
    }

    private void processScannedData(String scannedData) {
        Log.d(TAG, "Processing scanned data: " + scannedData);
        String studentId = studentIdEditText.getText().toString().trim();

        if (studentId.isEmpty()) {
            showError("من فضلك ادخل رقم الطالب قبل المسح!");
            return;
        }
        Log.d(TAG, "Student ID entered: " + studentId);

        // تفكيك محتوى الـ QR Code
        String[] parts = scannedData.split("_", 2); // تقسيم إلى جزأين فقط
        if (parts.length < 2) {
            showError("رمز QR غير صالح!");
            return;
        }

        final String sessionId = parts[0];
        String studentIdsStr = parts[1];

        // طباعة البيانات للتصحيح
        Log.d(TAG, "Session ID from QR: " + sessionId);
        Log.d(TAG, "Student IDs string from QR: " + studentIdsStr);

        List<String> allowedStudentIds = Arrays.asList(studentIdsStr.split(","));

        // تنظيف وتحويل allowedStudentIds لـ lowercase
        List<String> cleanedStudentIds = new ArrayList<>();
        for (String id : allowedStudentIds) {
            String cleanId = id.trim().toLowerCase();
            // محاولة تنظيف الرقم إذا كان رقماً
            try {
                cleanId = String.valueOf((long) Double.parseDouble(cleanId));
            } catch (NumberFormatException e) {
                // استخدم القيمة كما هي
            }
            cleanedStudentIds.add(cleanId);
        }

        // تنظيف رقم الطالب المدخل
        String tempStudentId;
        try {
            tempStudentId = String.valueOf((long) Double.parseDouble(studentId.toLowerCase()));
        } catch (NumberFormatException e) {
            tempStudentId = studentId.toLowerCase();
        }
        final String cleanStudentId = tempStudentId; // تعيين مرة واحدة

        Log.d(TAG, "Cleaned Student ID entered: " + cleanStudentId);
        Log.d(TAG, "Cleaned Student IDs from QR: " + cleanedStudentIds);

        // التحقق من الطالب داخل QR
        if (!cleanedStudentIds.contains(cleanStudentId)) {
            showError("رقم الطالب " + studentId + " مش مسجل في الجلسة دي!");
            return;
        }

        // التحقق من الجلسة في SQLite أولاً
        if (!dbHelper.isStudentInValidSession(cleanStudentId, sessionId)) {
            // بدلاً من إظهار خطأ، نضيف الطالب إلى الجلسة
            Log.d(TAG, "Student not in session, adding to session: " + cleanStudentId);
            List<String> singleStudent = new ArrayList<>();
            singleStudent.add(cleanStudentId);
            dbHelper.addSession(sessionId, singleStudent, System.currentTimeMillis() + (2 * 60 * 60 * 1000));

            // التحقق مرة أخرى
            if (!dbHelper.isStudentInValidSession(cleanStudentId, sessionId)) {
                showError("فشل في إضافة الطالب إلى الجلسة!");
                return;
            }
        }

        Log.d(TAG, "Student " + cleanStudentId + " is valid in session " + sessionId + " in SQLite");

        // التحقق من صلاحية الجلسة من Firebase
        final String finalCleanStudentId = cleanStudentId; // تعريف متغير نهائي للاستخدام في lambda
        final String finalSessionId = sessionId; // تعريف متغير نهائي للاستخدام في lambda

        com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("attendance")
                .child(finalSessionId)
                .get()
                .addOnSuccessListener(dataSnapshot -> {
                    if (dataSnapshot.exists()) {
                        Log.d(TAG, "Session " + finalSessionId + " exists in Firebase");
                        recordAttendance(finalCleanStudentId, finalSessionId);
                    } else {
                        Log.e(TAG, "Session " + finalSessionId + " does not exist in Firebase");

                        // إنشاء الجلسة في Firebase إذا لم تكن موجودة
                        List<String> singleStudent = new ArrayList<>();
                        singleStudent.add(finalCleanStudentId);
                        dbHelper.createSessionInFirebase(finalSessionId, singleStudent);

                        // تسجيل الحضور بعد إنشاء الجلسة
                        recordAttendance(finalCleanStudentId, finalSessionId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل الاتصال بـ Firebase: " + e.getMessage());

                    // تسجيل الحضور محلياً على الرغم من فشل الاتصال بـ Firebase
                    boolean localUpdateSuccess = dbHelper.updateAttendance(finalCleanStudentId, StudentActivity.this, null);
                    if (localUpdateSuccess) {
                        showSuccess("✅ تم تسجيل الحضور محلياً، لكن فشل الاتصال بالسيرفر!");
                    } else {
                        showError("فشل في تسجيل الحضور محلياً وفشل الاتصال بالسيرفر!");
                    }
                });
    }
    public void manualEntry(View view) {
        final String studentId = studentIdEditText.getText().toString().trim();
        if (studentId.isEmpty()) {
            showError("من فضلك ادخل رقم الطالب!");
            return;
        }

        // تنظيف رقم الطالب
        final String cleanStudentId;
        String tempId;
        try {
            tempId = String.valueOf((long) Double.parseDouble(studentId.toLowerCase()));
        } catch (NumberFormatException e) {
            tempId = studentId.toLowerCase();
        }
        cleanStudentId = tempId; // تعيين مرة واحدة

        // التحقق من وجود الطالب في قاعدة البيانات
        if (!dbHelper.isStudentExists(cleanStudentId)) {
            // إضافة الطالب إذا لم يكن موجوداً
            dbHelper.addStudent(cleanStudentId, "Student " + cleanStudentId);
        }

        // التحقق من وجود جلسة نشطة
        String sessionId = dbHelper.getValidSessionIdForStudent(cleanStudentId);

        if (sessionId != null) {
            // استخدام الجلسة الموجودة
            final String finalSessionId = sessionId;

            // التحقق من صلاحية الجلسة من Firebase
            com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("attendance")
                    .child(finalSessionId)
                    .get()
                    .addOnSuccessListener(dataSnapshot -> {
                        if (dataSnapshot.exists()) {
                            recordAttendance(cleanStudentId, finalSessionId);
                        } else {
                            // إنشاء الجلسة في Firebase إذا لم تكن موجودة
                            List<String> singleStudent = new ArrayList<>();
                            singleStudent.add(cleanStudentId);
                            dbHelper.createSessionInFirebase(finalSessionId, singleStudent);

                            // تسجيل الحضور بعد إنشاء الجلسة
                            recordAttendance(cleanStudentId, finalSessionId);
                        }
                    })
                    .addOnFailureListener(e -> {
                        // تسجيل الحضور محلياً على الرغم من فشل الاتصال بـ Firebase
                        boolean localUpdateSuccess = dbHelper.updateAttendance(cleanStudentId, StudentActivity.this, null);
                        if (localUpdateSuccess) {
                            showSuccess("✅ تم تسجيل الحضور محلياً، لكن فشل الاتصال بالسيرفر!");
                        } else {
                            showError("فشل في تسجيل الحضور محلياً وفشل الاتصال بالسيرفر!");
                        }
                    });
        } else {
            // إنشاء جلسة جديدة إذا لم تكن هناك جلسة نشطة
            final String newSessionId = "SESSION_" + System.currentTimeMillis();
            List<String> singleStudent = new ArrayList<>();
            singleStudent.add(cleanStudentId);
            dbHelper.addSession(newSessionId, singleStudent, System.currentTimeMillis() + (2 * 60 * 60 * 1000));

            // تسجيل الحضور في الجلسة الجديدة
            recordAttendance(cleanStudentId, newSessionId);
        }
    }
    private void recordAttendance(String studentId, String sessionId) {
        // التحقق أولاً إذا كان الطالب قد سجل حضوره بالفعل
        com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("attendance")
                .child(sessionId)
                .child(studentId)
                .get()
                .addOnSuccessListener(dataSnapshot -> {
                    if (dataSnapshot.exists() && dataSnapshot.getValue(String.class).contains("Present")) {
                        showSuccess("✅ الطالب سجل حضوره بالفعل!");
                        studentIdEditText.setText("");
                        return;
                    }

                    // تحديث قاعدة البيانات المحلية
                    boolean localUpdateSuccess = dbHelper.updateAttendance(studentId, StudentActivity.this, null);

                    if (!localUpdateSuccess) {
                        showError("❌ فشل في تحديث الحضور محلياً!");
                        return;
                    }

                    // الحصول على اسم الطالب من قاعدة البيانات
                    String studentName = dbHelper.getStudentNameFromDB(studentId);
                    final String finalStudentName = (studentName == null || studentName.isEmpty()) ?
                            studentId.toUpperCase() : studentName;

                    // إنشاء طابع زمني للحضور
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    String attendanceTime = sdf.format(new Date());

                    // تحديث Firebase مع وقت الحضور
                    com.google.firebase.database.FirebaseDatabase.getInstance()
                            .getReference("attendance")
                            .child(sessionId)
                            .child(studentId)
                            .setValue("Present - " + finalStudentName + " - " + attendanceTime)
                            .addOnSuccessListener(unused -> {
                                showSuccess("✅ تم تسجيل الحضور بنجاح لـ ID: " + studentId);
                                studentIdEditText.setText("");
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "فشل في تحديث الحضور في Firebase: " + e.getMessage());
                                showError("⚠️ تم تسجيل الحضور محلياً لكن فشل التحديث على السيرفر.");
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل في التحقق من الحضور المسبق: " + e.getMessage());

                    // تسجيل الحضور محلياً على الرغم من فشل الاتصال بـ Firebase
                    boolean localUpdateSuccess = dbHelper.updateAttendance(studentId, StudentActivity.this, null);
                    if (localUpdateSuccess) {
                        showSuccess("✅ تم تسجيل الحضور محلياً، لكن فشل الاتصال بالسيرفر!");
                    } else {
                        showError("فشل في تسجيل الحضور محلياً وفشل الاتصال بالسيرفر!");
                    }
                });
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchScanner();
            } else {
                showError("تم رفض إذن الكاميرا. مش هينفع تمسح الكود.");
            }
        }
    }

    private void showSuccess(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showError(String message) {
        View rootView = findViewById(android.R.id.content);
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(ContextCompat.getColor(this, R.color.error_color))
                .setTextColor(ContextCompat.getColor(this, R.color.background))
                .show();
    }
}
