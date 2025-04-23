package com.example.test;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AdminActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_CODE = 124;
    private static final String TAG = "AdminActivity";
    private com.google.firebase.database.ValueEventListener attendanceListener;


    private ImageView qrCodeImageView;
    private Uri attendanceFileUri;
    private MaterialToolbar toolbar;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private DBHelper dbHelper;

    private final ActivityResultLauncher<Intent> pickFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            attendanceFileUri = uri;
                            Log.d(TAG, "Selected file Uri: " + attendanceFileUri.toString());
                            copyExcelFile(attendanceFileUri);

                            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("attendance_file_uri", attendanceFileUri.toString());
                            editor.apply();

                            Toast.makeText(this, "تم تحميل الملف بنجاح", Toast.LENGTH_SHORT).show();
                        } catch (SecurityException e) {
                            Log.e(TAG, "Permission error: " + e.getMessage());
                            showErrorAsync("خطأ في الأذونات: " + e.getMessage());
                        }
                    } else {
                        showErrorAsync("مفيش ملف تم اختياره أو الملف غير صالح!");
                    }
                } else {
                    showErrorAsync("لم يتم اختيار ملف!");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);


        dbHelper = new DBHelper(this);
        initViews();
        setupToolbar();
        checkStoragePermission();

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String uriString = prefs.getString("attendance_file_uri", null);
        if (uriString != null) {
             attendanceFileUri = Uri.parse(uriString);
            Log.d(TAG, "Loaded attendance file Uri from preferences: " + attendanceFileUri.toString());
            if (!hasUriPermission(attendanceFileUri)) {
                attendanceFileUri = null;
                Toast.makeText(this, "الرجاء اختيار ملف الحضور مرة أخرى", Toast.LENGTH_LONG).show();
            } else {
                // تحقق من أن الملف قابل للكتابة
                try (OutputStream testFos = getContentResolver().openOutputStream(attendanceFileUri)) {
                    if (testFos == null) {
                        attendanceFileUri = null;
                        Toast.makeText(this, "لا يمكن الكتابة إلى الملف، اختر ملفًا آخر", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Cannot write to file: " + e.getMessage());
                    attendanceFileUri = null;
                    Toast.makeText(this, "خطأ في الوصول للملف، اختر ملفًا آخر", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
    private void syncAttendanceFromFirebase(String sessionId, Uri excelUri, Runnable onComplete) {
        Log.d(TAG, "🔁 [SYNC] بدء مزامنة الحضور من Firebase للجلسة: " + sessionId);

        DatabaseReference dbRef = FirebaseDatabase.getInstance()
                .getReference("attendance")
                .child(sessionId);

        dbRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                Log.d(TAG, "📥 [SYNC] عدد الطلاب في Firebase: " + snapshot.getChildrenCount());

                int[] counter = {0};
                long total = snapshot.getChildrenCount();

                for (DataSnapshot child : snapshot.getChildren()) {
                    String studentId = child.getKey();
                    String status = child.getValue(String.class);

                    Log.d(TAG, "🧾 [SYNC] طالب: " + studentId + " | الحالة: " + status);

                    if (status != null && status.contains("Present")) {
                        boolean updated = dbHelper.updateAttendance(studentId, AdminActivity.this, excelUri);
                        Log.d(TAG, "🛠️ [SYNC] تم تحديث الطالب؟ " + updated);
                    }

                    counter[0]++;
                    if (counter[0] == total) {
                        Log.d(TAG, "✅ [SYNC] انتهت المزامنة");
                        onComplete.run();
                    }
                }
            } else {
                Log.w(TAG, "⚠️ [SYNC] لا يوجد بيانات في Firebase للجلسة");
                onComplete.run();
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "❌ [SYNC] فشل في جلب البيانات من Firebase: " + e.getMessage(), e);
            onComplete.run();
        });
    }
    private boolean hasUriPermission(Uri uri) {
        try {
            getContentResolver().openInputStream(uri).close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "No permission to access Uri: " + e.getMessage());
            return false;
        }
    }

    public void onUpdateExcelClick(View view) {
        String sessionId = dbHelper.getLatestValidSessionId();
        if (sessionId == null) {
            showErrorAsync("❌ لا توجد جلسة حالية صالحة!");
            return;
        }

        Toast.makeText(this, "⏳ جاري تحميل الحضور من Firebase وإنشاء ملف جديد...", Toast.LENGTH_SHORT).show();
        generateExcelFromFirebase(sessionId);
    }

    private void reloadExcelFileAsync() {
        executor.execute(() -> {
            try {
                String result = readExcelData(attendanceFileUri);
                mainHandler.post(() -> {
                    if (result != null) {
                        Intent intent = new Intent(AdminActivity.this, DisplayActivity.class);
                        intent.putExtra("excel_data", result);
                        startActivity(intent);
                    } else {
                        showErrorAsync("فشل في تحميل بيانات الإكسل!");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error reloading Excel: " + e.getMessage(), e);
                showErrorAsync("خطأ: " + e.getMessage());
            }
        });
    }

    private String readExcelData(Uri fileUri) {
        StringBuilder result = new StringBuilder();
        try (InputStream fis = getContentResolver().openInputStream(fileUri);
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                Log.e(TAG, "No data in Excel sheet!");
                return null;
            }
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                Cell idCell = row.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                Cell nameCell = row.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                Cell attendanceCell = row.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                String studentId = idCell.toString().trim();
                String studentName = nameCell.toString().trim();
                String attendanceStatus = attendanceCell.toString().trim();
                result.append(studentId).append(" - ").append(studentName).append(": ").append(attendanceStatus).append("\n\n");
            }
            Log.d(TAG, "Excel data loaded successfully!");
            return result.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error reading Excel: " + e.getMessage(), e);
            return null;
        }
    }
    private void initViews() {
        qrCodeImageView = findViewById(R.id.qrCodeImageView);
        toolbar = findViewById(R.id.toolbar);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Admin Dashboard");
        }
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // لا حاجة لعمل شيء هنا لأن الملف هيتم اختياره ديناميكيًا
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Permission Needed")
                            .setMessage("This app needs storage permission to manage attendance files.")
                            .setPositiveButton("OK", (dialog, which) -> {
                                ActivityCompat.requestPermissions(this,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        STORAGE_PERMISSION_CODE);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            STORAGE_PERMISSION_CODE);
                }
            }
        }
    }

    // في AdminActivity داخل onGenerateBarcodeClick
    public void onGenerateBarcodeClick(View view) {
        executor.execute(() -> {
            try {
                List<Pair<String, String>> students = getAllStudents();
                if (students.isEmpty()) {
                    showErrorAsync("لم يتم العثور على أرقام طلاب في ملف الإكسل!");
                    return;
                }

                String sessionId = "SESSION_" + System.currentTimeMillis();
                long expiryTime = System.currentTimeMillis() + (2 * 60 * 60 * 1000); // 2 ساعة

                List<String> studentIds = new ArrayList<>();
                for (Pair<String, String> student : students) {
                    studentIds.add(student.first.trim().toLowerCase());
                }
                Log.d(TAG, "Generating QR for session: " + sessionId + ", Students: " + studentIds);

                // حفظ الجلسة في قاعدة البيانات المحلية
                dbHelper.addSession(sessionId, studentIds, expiryTime);

                // توليد QR
                String qrContent = sessionId + "_" + String.join(",", studentIds);
                Log.d(TAG, "QR Content: " + qrContent);
                QRCodeWriter writer = new QRCodeWriter();
                BitMatrix bitMatrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, 512, 512);
                Bitmap bitmap = toBitmap(bitMatrix);

                mainHandler.post(() -> {
                    qrCodeImageView.setImageBitmap(bitmap);
                    saveQRCodeToGallery(bitmap);
                    showSuccess("تم إنشاء رمز QR للجلسة: " + sessionId);
                });
            } catch (Exception e) {
                Log.e(TAG, "خطأ في إنشاء رمز QR: " + e.getMessage());
                showErrorAsync("فشل في إنشاء رمز QR: " + e.getMessage());
            }
        });
    }
    private List<Pair<String, String>> getAllStudents() {
        List<Pair<String, String>> students = new ArrayList<>();
        if (attendanceFileUri == null) {
            showErrorAsync("لم يتم اختيار ملف إكسل!");
            return students;
        }
        try (InputStream fis = getContentResolver().openInputStream(attendanceFileUri);
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                Cell idCell = row.getCell(0);
                Cell nameCell = row.getCell(1);
                if (idCell != null && nameCell != null) {
                    String studentId = idCell.toString().trim();
                    String name = nameCell.toString().trim();
                    if (!studentId.isEmpty() && !name.isEmpty()) {
                        students.add(new Pair<>(studentId, name));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading students from Excel", e);
            showErrorAsync("فشل في قراءة بيانات الطلاب: " + e.getMessage());
        }
        return students;
    }

    private String generateSessionData() {
        return "ATTENDANCE-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(new Date());
    }

    private Bitmap toBitmap(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }

        return bitmap;
    }

    private void saveQRCodeToGallery(Bitmap bitmap) {
        String fileName = "QRCode_" + System.currentTimeMillis() + ".png";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues(); // تعريف المتغير values هنا
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

            ContentResolver resolver = getContentResolver();
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                    Toast.makeText(this, "تم حفظ رمز QR في المعرض", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "خطأ في حفظ رمز QR: " + e.getMessage(), e);
                    Toast.makeText(this, "فشل في حفظ رمز QR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            File imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File image = new File(imagesDir, fileName);

            try (FileOutputStream fos = new FileOutputStream(image)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);

                // إضافة الصورة إلى معرض الصور
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri contentUri = Uri.fromFile(image);
                mediaScanIntent.setData(contentUri);
                sendBroadcast(mediaScanIntent);

                Toast.makeText(this, "تم حفظ رمز QR في المعرض", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "خطأ في حفظ رمز QR: " + e.getMessage(), e);
                Toast.makeText(this, "فشل في حفظ رمز QR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }
    public void onViewAttendanceClick(View view) {
        String sessionId = dbHelper.getLatestValidSessionId();
        if (sessionId == null) {
            Toast.makeText(this, "لا توجد جلسة حالية صالحة!", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "جاري تحميل بيانات الحضور...", Toast.LENGTH_SHORT).show();

        // الحصول على بيانات الحضور من Firebase
        DatabaseReference dbRef = FirebaseDatabase.getInstance()
                .getReference("attendance")
                .child(sessionId);

        dbRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                // إنشاء قائمة بجميع الطلاب (الحاضرين وغير الحاضرين)
                List<Pair<String, String>> allStudents = getAllStudents();
                Map<String, String> attendanceMap = new HashMap<>();

                // تحويل بيانات Firebase إلى Map للبحث السريع
                for (DataSnapshot child : snapshot.getChildren()) {
                    String studentId = child.getKey();
                    String status = child.getValue(String.class);
                    attendanceMap.put(studentId, status);
                }

                // بناء نص لعرض بيانات الحضور
                StringBuilder attendanceData = new StringBuilder();
                for (Pair<String, String> student : allStudents) {
                    String studentId = student.first;
                    String studentName = student.second;

                    // التحقق من حالة الحضور
                    String status = attendanceMap.get(studentId);
                    String attendanceStatus;
                    String attendanceTime = "";

                    if (status != null && status.contains("Present")) {
                        attendanceStatus = "Present";

                        // استخراج وقت الحضور من النص
                        if (status.contains(" - ")) {
                            String[] parts = status.split(" - ");
                            if (parts.length >= 3) {
                                attendanceTime = parts[2];
                            }
                        }
                    } else {
                        attendanceStatus = "Absent";
                    }

                    // بناء سطر لكل طالب
                    attendanceData.append(studentId)
                            .append(" - ")
                            .append(studentName)
                            .append(": ")
                            .append(attendanceStatus);

                    // إضافة وقت الحضور إذا كان متاحًا
                    if (!attendanceTime.isEmpty()) {
                        attendanceData.append(" (").append(attendanceTime).append(")");
                    }

                    attendanceData.append("\n\n");
                }

                // عرض بيانات الحضور
                Intent intent = new Intent(this, DisplayActivity.class);
                intent.putExtra("excel_data", attendanceData.toString());
                startActivity(intent);
            } else {
                Toast.makeText(this, "لا توجد بيانات حضور للجلسة المحددة", Toast.LENGTH_LONG).show();
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "فشل في جلب بيانات الحضور من Firebase: " + e.getMessage(), e);
            Toast.makeText(this, "فشل في جلب بيانات الحضور: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    public void onViewStudentListClick(View view) {
        // الحصول على قائمة الطلاب من قاعدة البيانات المحلية
        List<Pair<String, String>> students = getAllStudents();

        if (students.isEmpty()) {
            Toast.makeText(this, "No students registered!", Toast.LENGTH_SHORT).show();
            return;
        }

        // بناء نص لعرض قائمة الطلاب (الرقم والاسم فقط)
        StringBuilder studentList = new StringBuilder();
        studentList.append("Student List:\n\n");

        for (Pair<String, String> student : students) {
            studentList.append("ID: ").append(student.first)
                    .append("\nName: ").append(student.second)
                    .append("\n\n");
        }

        // عرض قائمة الطلاب
        Intent intent = new Intent(this, DisplayActivity.class);
        intent.putExtra("excel_data", studentList.toString());
        intent.putExtra("title", "Student List");
        startActivity(intent);
    }


    public void onUploadExcelClick (View view){
            Log.d(TAG, "Upload Excel clicked");
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            pickFileLauncher.launch(intent);
        }


        public void copyExcelFile (Uri uri){
            executor.execute(() -> {
                try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                    Workbook workbook = WorkbookFactory.create(inputStream);
                    Sheet sheet = workbook.getSheetAt(0);
                    SQLiteDatabase db = dbHelper.getWritableDatabase();
                    db.execSQL("DELETE FROM students");
                    Log.d(TAG, "تم مسح البيانات القديمة من جدول students");

                    DataFormatter dataFormatter = new DataFormatter();
                    HashSet<String> existingIds = new HashSet<>();

                    for (Row row : sheet) {
                        if (row.getRowNum() == 0) continue;

                        Cell idCell = row.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        String studentId = getCellValue(idCell, dataFormatter);
                        if (idCell.getCellType() == CellType.NUMERIC) {
                            studentId = String.valueOf((long) idCell.getNumericCellValue());
                        }

                        if (studentId.trim().isEmpty() || existingIds.contains(studentId)) {
                            Log.w(TAG, "Skipping duplicate or empty student ID: " + studentId);
                            continue;
                        }

                        Cell nameCell = row.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        String studentName = getCellValue(nameCell, dataFormatter);
                        if (studentName.trim().isEmpty()) {
                            studentName = "Unknown Student";
                        }

                        dbHelper.addStudent(studentId, studentName);
                        existingIds.add(studentId);
                        Log.d(TAG, "Added student: " + studentId + " with name: " + studentName);
                    }
                    db.close();
                    workbook.close();
                    mainHandler.post(() -> Toast.makeText(this, "تم نسخ بيانات الملف بنجاح", Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    Log.e(TAG, "Error copying Excel file: " + e.getMessage(), e);
                    showErrorAsync("خطأ في نسخ الملف: " + e.getMessage());
                }
            });
        }
        // دالة مساعدة لقراءة قيمة الخلية بكل الطرق الممكنة
        private String getCellValue (Cell cell, DataFormatter formatter){
            if (cell == null) return "";
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    return formatter.formatCellValue(cell).trim();
                case BLANK:
                    return "";
                default:
                    return formatter.formatCellValue(cell).trim();
            }
        }
        private void importStudentsFromExcel (File file){
            executor.execute(() -> {
                try (FileInputStream fis = new FileInputStream(file);
                     Workbook workbook = WorkbookFactory.create(fis)) {
                    Sheet sheet = workbook.getSheetAt(0);
                    for (Row row : sheet) {
                        if (row.getRowNum() == 0) continue;
                        Cell idCell = row.getCell(0);
                        Cell nameCell = row.getCell(1);
                        if (idCell != null && nameCell != null) {
                            String studentId = idCell.toString().trim();
                            String name = nameCell.toString().trim();
                            dbHelper.addStudent(studentId, name);
                        }
                    }
                    Log.d(TAG, "All students imported into database.");
                } catch (Exception e) {
                    Log.e(TAG, "Error importing students", e);
                }
            });
        }

        private boolean isValidHeader (Row headerRow){
            if (headerRow == null) {
                Log.e(TAG, "Header row is null");
                return false;
            }
            String[] expectedHeaders = {"Student ID", "Name", "Attendance"};
            for (int i = 0; i < expectedHeaders.length; i++) {
                Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                String cellValue = cell.getStringCellValue().trim()
                        .replaceAll("[^a-zA-Z0-9]", "")
                        .toLowerCase();
                String expected = expectedHeaders[i].replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                Log.d(TAG, "Header Check - Expected: " + expected + " | Found: " + cellValue);
                if (!expected.equals(cellValue)) {
                    Log.e(TAG, "Header mismatch at column " + i);
                    return false;
                }
            }
            return true;
        }

        private void readExcelAndDisplay ( boolean showAttendance){
            if (attendanceFileUri == null) {
                showErrorAsync("لم يتم اختيار ملف إكسل!");
                return;
            }
            executor.execute(() -> {
                try (InputStream fis = getContentResolver().openInputStream(attendanceFileUri);
                     Workbook workbook = WorkbookFactory.create(fis)) {
                    Log.d(TAG, "Reading Excel file...");
                    Sheet sheet = workbook.getSheetAt(0);
                    if (sheet == null) {
                        showErrorAsync("الورقة فارغة!");
                        return;
                    }
                    StringBuilder result = new StringBuilder();
                    HashMap<String, String> attendanceMap = new HashMap<>();
                    for (Row row : sheet) {
                        if (row.getRowNum() == 0) continue;
                        Cell idCell = row.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        Cell nameCell = row.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        Cell attendanceCell = row.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        String studentId = idCell.toString().trim();
                        if (studentId.isEmpty()) continue;
                        Cursor cursor = dbHelper.getReadableDatabase().rawQuery(
                                "SELECT name, attendance FROM students WHERE student_id = ?",
                                new String[]{studentId.trim().toLowerCase()});
                        String studentName = "Unknown Student";
                        String attendanceStatus = "Absent";
                        if (cursor.moveToFirst()) {
                            studentName = cursor.getString(0);
                            int attendance = cursor.getInt(1);
                            attendanceStatus = attendance == 1 ? "Present" : "Absent";
                        }
                        cursor.close();
                        nameCell.setCellValue(studentName);
                        attendanceCell.setCellValue(attendanceStatus);
                        attendanceMap.put(studentId, studentName + ": " + attendanceStatus);
                    }
                    try (OutputStream fos = getContentResolver().openOutputStream(attendanceFileUri)) {
                        workbook.write(fos);
                        Log.d(TAG, "Excel file updated successfully!");
                    }
                    for (Map.Entry<String, String> entry : attendanceMap.entrySet()) {
                        result.append(entry.getKey()).append(" - ").append(entry.getValue()).append("\n\n");
                    }
                    if (result.length() == 0) {
                        showErrorAsync("لا يوجد طلاب!");
                        return;
                    }
                    mainHandler.post(() -> {
                        Intent intent = new Intent(AdminActivity.this, DisplayActivity.class);
                        intent.putExtra("excel_data", result.toString());
                        startActivity(intent);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "خطأ في قراءة أو تحديث الإكسل", e);
                    showErrorAsync("خطأ: " + e.getMessage());
                }
            });
        }

        private String getCellStringValue (Cell cell){
            if (cell == null) return "N/A";
            try {
                switch (cell.getCellType()) {
                    case STRING:
                        return cell.getStringCellValue().trim();
                    case NUMERIC:
                        if (DateUtil.isCellDateFormatted(cell)) {
                            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                    .format(cell.getDateCellValue());
                        }
                        return String.valueOf((int) cell.getNumericCellValue());
                    case BOOLEAN:
                        return String.valueOf(cell.getBooleanCellValue());
                    case FORMULA:
                        return cell.getCellFormula();
                    default:
                        return "N/A";
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading cell", e);
                return "N/A";
            }
        }

        private void showSuccessAsync (String message){
            mainHandler.post(() -> showSuccess(message));
        }

        private void showErrorAsync (String message){
            mainHandler.post(() -> {
                Log.e(TAG, message);
                showError(message);
            });
        }

        private void showSuccess (String message){
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }

        private void showError (String message){
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }

        @Override
        public boolean onOptionsItemSelected (MenuItem item){
            if (item.getItemId() == android.R.id.home) {
                finish();
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onRequestPermissionsResult ( int requestCode, @NonNull String[] permissions,
        @NonNull int[] grantResults){
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == STORAGE_PERMISSION_CODE) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
                }
            }
        }


        // أضف هذه الدالة في onCreate
        private void setupFirebaseListener () {
            // إذا كان هناك جلسة نشطة، قم بإعداد المراقب
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery(
                    "SELECT session_id FROM sessions WHERE expiry_time > ? ORDER BY expiry_time DESC LIMIT 1",
                    new String[]{String.valueOf(System.currentTimeMillis())}
            );

            if (cursor.moveToFirst()) {
                String activeSessionId = cursor.getString(0);
                cursor.close();
                db.close();

                Log.d(TAG, "Setting up Firebase listener for session: " + activeSessionId);

                com.google.firebase.database.DatabaseReference dbRef =
                        com.google.firebase.database.FirebaseDatabase.getInstance()
                                .getReference("attendance")
                                .child(activeSessionId);

                attendanceListener = new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        Log.d(TAG, "Firebase data changed for session: " + activeSessionId);
                        // تحديث قاعدة البيانات المحلية من Firebase
                        for (com.google.firebase.database.DataSnapshot childSnapshot : snapshot.getChildren()) {
                            String studentId = childSnapshot.getKey();
                            String attendanceValue = childSnapshot.getValue(String.class);

                            if (studentId != null && attendanceValue != null && attendanceValue.contains("Present")) {
                                Log.d(TAG, "Student marked present in Firebase: " + studentId);
                                // تحديث قاعدة البيانات المحلية
                                SQLiteDatabase localDb = dbHelper.getWritableDatabase();
                                ContentValues values = new ContentValues();
                                values.put("attendance", 1);
                                localDb.update("students", values, "student_id = ?", new String[]{studentId.trim().toLowerCase()});
                                localDb.close();
                            }
                        }

                    }

                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                        Log.e(TAG, "Firebase listener cancelled: " + error.getMessage());
                    }
                };

                dbRef.addValueEventListener(attendanceListener);
            } else {
                cursor.close();
                db.close();
                Log.d(TAG, "No active session found for Firebase listener");
            }
        }

        // أضف هذه الدالة في onDestroy
        @Override
        protected void onDestroy () {
            super.onDestroy();
            // إلغاء المراقب عند إغلاق النشاط
            if (attendanceListener != null) {
                com.google.firebase.database.FirebaseDatabase.getInstance()
                        .getReference("attendance")
                        .removeEventListener(attendanceListener);
            }
        }
    private void generateExcelFromFirebase(String sessionId) {
        executor.execute(() -> {
            try {
                // إنشاء مصنف Excel جديد
                Workbook workbook = new XSSFWorkbook();
                Sheet sheet = workbook.createSheet("Attendance");

                // إنشاء الصف الأول (العناوين)
                Row headerRow = sheet.createRow(0);
                Cell idHeaderCell = headerRow.createCell(0);
                idHeaderCell.setCellValue("Student ID");
                Cell nameHeaderCell = headerRow.createCell(1);
                nameHeaderCell.setCellValue("Student Name");
                Cell attendanceHeaderCell = headerRow.createCell(2);
                attendanceHeaderCell.setCellValue("Attendance Status");
                Cell timeHeaderCell = headerRow.createCell(3);
                timeHeaderCell.setCellValue("Attendance Time");

                // الحصول على بيانات الحضور من Firebase
                DatabaseReference dbRef = FirebaseDatabase.getInstance()
                        .getReference("attendance")
                        .child(sessionId);

                dbRef.get().addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Log.d(TAG, "تم العثور على بيانات الحضور في Firebase: " + snapshot.getChildrenCount() + " طالب");

                        // إنشاء قائمة بجميع الطلاب (الحاضرين وغير الحاضرين)
                        List<Pair<String, String>> allStudents = getAllStudents();
                        Map<String, String> attendanceMap = new HashMap<>();

                        // تحويل بيانات Firebase إلى Map للبحث السريع
                        for (DataSnapshot child : snapshot.getChildren()) {
                            String studentId = child.getKey();
                            String status = child.getValue(String.class);
                            attendanceMap.put(studentId, status);
                        }

                        // إضافة جميع الطلاب إلى ملف Excel
                        int rowNum = 1;
                        for (Pair<String, String> student : allStudents) {
                            String studentId = student.first;
                            String studentName = student.second;

                            Row row = sheet.createRow(rowNum++);
                            row.createCell(0).setCellValue(studentId);
                            row.createCell(1).setCellValue(studentName);

                            // التحقق من حالة الحضور
                            String status = attendanceMap.get(studentId);
                            if (status != null && status.contains("Present")) {
                                row.createCell(2).setCellValue("Present");

                                // استخراج وقت الحضور من النص - هنا التعديل المطلوب
                                String attendanceTime = "";
                                if (status.contains(" - ")) {
                                    // تقسيم النص بناءً على " - "
                                    String[] parts = status.split(" - ");
                                    // الجزء الأخير يحتوي على التاريخ والوقت (إذا كان موجودًا)
                                    if (parts.length >= 3) {
                                        attendanceTime = parts[parts.length - 1]; // استخدام آخر جزء دائمًا
                                        Log.d(TAG, "استخراج الوقت: " + attendanceTime + " من النص: " + status);
                                    }
                                }
                                row.createCell(3).setCellValue(attendanceTime);
                            } else {
                                // إضافة اسم الطالب للغائبين أيضًا
                                row.createCell(2).setCellValue("Absent");
                                row.createCell(3).setCellValue("");

                                // تحديث Firebase بأسماء الطلاب الغائبين
                                if (status == null || status.equals("Not Yet Attended")) {
                                    dbRef.child(studentId).setValue("Not Yet Attended - " + studentName);
                                }
                            }
                        }

                        // حفظ ملف Excel
                        saveExcelFile(workbook, sessionId);
                    } else {
                        mainHandler.post(() -> {
                            Toast.makeText(AdminActivity.this, "لا توجد بيانات حضور للجلسة المحددة", Toast.LENGTH_LONG).show();
                        });
                    }
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "فشل في جلب بيانات الحضور من Firebase: " + e.getMessage(), e);
                    mainHandler.post(() -> {
                        Toast.makeText(AdminActivity.this, "فشل في جلب بيانات الحضور: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                });
            } catch (Exception e) {
                Log.e(TAG, "خطأ في إنشاء ملف Excel: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    Toast.makeText(AdminActivity.this, "خطأ في إنشاء ملف Excel: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }


        private void saveExcelFile (Workbook workbook, String sessionId){
            try {
                // إنشاء اسم الملف
                String fileName = "Attendance_" + sessionId + ".xlsx";

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // استخدام MediaStore لحفظ الملف في Android 10 وما فوق
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS);

                    ContentResolver resolver = getContentResolver();
                    Uri uri = resolver.insert(MediaStore.Files.getContentUri("external"), values);

                    if (uri != null) {
                        try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                            workbook.write(outputStream);

                            // حفظ URI في SharedPreferences
                            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("attendance_file_uri", uri.toString());
                            editor.apply();

                            attendanceFileUri = uri;

                            mainHandler.post(() -> {
                                Toast.makeText(AdminActivity.this, "تم حفظ ملف Excel بنجاح: " + fileName, Toast.LENGTH_LONG).show();

                                // عرض البيانات بعد الحفظ
                                Intent intent = new Intent(AdminActivity.this, DisplayActivity.class);
                                intent.putExtra("attendance_file_uri", uri.toString());
                                startActivity(intent);
                            });
                        }
                    } else {
                        throw new IOException("فشل في إنشاء URI للملف");
                    }
                } else {
                    // لإصدارات Android الأقدم
                    File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                    if (!documentsDir.exists()) {
                        documentsDir.mkdirs();
                    }

                    File file = new File(documentsDir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        workbook.write(fos);

                        // حفظ مسار الملف في SharedPreferences
                        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("attendance_file_uri", Uri.fromFile(file).toString());
                        editor.apply();

                        attendanceFileUri = Uri.fromFile(file);

                        mainHandler.post(() -> {
                            Toast.makeText(AdminActivity.this, "تم حفظ ملف Excel بنجاح: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();

                            // عرض البيانات بعد الحفظ
                            Intent intent = new Intent(AdminActivity.this, DisplayActivity.class);
                            intent.putExtra("attendance_file_uri", Uri.fromFile(file).toString());
                            startActivity(intent);
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "خطأ في حفظ ملف Excel: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    Toast.makeText(AdminActivity.this, "خطأ في حفظ ملف Excel: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }

}
