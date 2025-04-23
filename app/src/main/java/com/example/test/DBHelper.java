package com.example.test;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.apache.poi.ss.usermodel.Cell;
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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "attendance.db";
    private static final int DATABASE_VERSION = 3;
    private Context context;
    private static final String TAG = "DBHelper";
    private static final String TABLE_STUDENTS = "students";
    private static final String TABLE_SESSIONS = "sessions";

    private static final String COLUMN_STUDENT_ID = "student_id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_ATTENDANCE = "attendance";

    private static final String COLUMN_SESSION_ID = "session_id";
    private static final String COLUMN_EXPIRY_TIME = "expiry_time";

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_STUDENTS_TABLE = "CREATE TABLE " + TABLE_STUDENTS + " (" +
                COLUMN_STUDENT_ID + " TEXT PRIMARY KEY," +
                COLUMN_NAME + " TEXT," +
                COLUMN_ATTENDANCE + " INTEGER DEFAULT 0)";

        String CREATE_SESSIONS_TABLE = "CREATE TABLE " + TABLE_SESSIONS + " (" +
                COLUMN_SESSION_ID + " TEXT," +
                COLUMN_STUDENT_ID + " TEXT," +
                COLUMN_EXPIRY_TIME + " INTEGER," +
                "PRIMARY KEY (" + COLUMN_SESSION_ID + ", " + COLUMN_STUDENT_ID + "), " +
                "FOREIGN KEY(" + COLUMN_STUDENT_ID + ") REFERENCES " + TABLE_STUDENTS + "(" + COLUMN_STUDENT_ID + "))";

        db.execSQL(CREATE_STUDENTS_TABLE);
        db.execSQL(CREATE_SESSIONS_TABLE);

        Log.d("DBHelper", "Database created successfully.");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_STUDENTS);
        onCreate(db);
        Log.d("DBHelper", "Database upgraded from version " + oldVersion + " to " + newVersion);
    }

    /**
     * إضافة طالب إلى قاعدة البيانات أو تحديث بياناته إذا كان موجوداً بالفعل
     */
    public void addStudent(String studentId, String name) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            ContentValues values = new ContentValues();

            // تنظيف وتوحيد تنسيق رقم الطالب
            String cleanStudentId = cleanStudentId(studentId);

            values.put(COLUMN_STUDENT_ID, cleanStudentId);
            values.put(COLUMN_NAME, name.trim().isEmpty() ? "Unknown Student" : name.trim());
            values.put(COLUMN_ATTENDANCE, 0);

            long result = db.insertWithOnConflict(TABLE_STUDENTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            Log.d(TAG, "تم إضافة/تحديث الطالب: " + cleanStudentId + " باسم " + name + ", result: " + result);
        } catch (Exception e) {
            Log.e(TAG, "Error adding student: " + e.getMessage(), e);
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    /**
     * إضافة جلسة جديدة مع قائمة الطلاب المسموح لهم بالحضور
     */
    public void addSession(String sessionId, List<String> studentIds, long expiryTime) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            db.beginTransaction();

            // طباعة معلومات الجلسة للتصحيح
            Log.d(TAG, "Adding session: " + sessionId);
            Log.d(TAG, "Expiry time: " + expiryTime);
            Log.d(TAG, "Number of students: " + studentIds.size());

            // حذف أي جلسة قديمة بنفس المعرّف
            db.delete(TABLE_SESSIONS, COLUMN_SESSION_ID + " = ?", new String[]{sessionId});

            for (String studentId : studentIds) {
                // تنظيف رقم الطالب
                String cleanId = cleanStudentId(studentId);

                // التحقق من وجود الطالب في جدول الطلاب
                Cursor cursor = db.rawQuery(
                        "SELECT 1 FROM " + TABLE_STUDENTS + " WHERE " + COLUMN_STUDENT_ID + " = ?",
                        new String[]{cleanId});

                boolean studentExists = cursor.moveToFirst();
                cursor.close();

                // إذا لم يكن الطالب موجوداً، أضفه
                if (!studentExists) {
                    ContentValues studentValues = new ContentValues();
                    studentValues.put(COLUMN_STUDENT_ID, cleanId);
                    studentValues.put(COLUMN_NAME, "Student " + cleanId);
                    studentValues.put(COLUMN_ATTENDANCE, 0);
                    db.insertWithOnConflict(TABLE_STUDENTS, null, studentValues, SQLiteDatabase.CONFLICT_REPLACE);
                    Log.d(TAG, "Added missing student: " + cleanId);
                }

                // إضافة الطالب إلى الجلسة
                ContentValues values = new ContentValues();
                values.put(COLUMN_SESSION_ID, sessionId);
                values.put(COLUMN_STUDENT_ID, cleanId);
                values.put(COLUMN_EXPIRY_TIME, expiryTime);

                long result = db.insertWithOnConflict(TABLE_SESSIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                Log.d(TAG, "Added student " + cleanId + " to session " + sessionId + ", result: " + result);
            }

            db.setTransactionSuccessful();
            Log.d(TAG, "Session " + sessionId + " added successfully with " + studentIds.size() + " students.");

            // التحقق من إضافة الجلسة بنجاح
            Cursor countCursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE_SESSIONS + " WHERE " + COLUMN_SESSION_ID + " = ?",
                    new String[]{sessionId});

            if (countCursor.moveToFirst()) {
                int count = countCursor.getInt(0);
                Log.d(TAG, "Verified: Session " + sessionId + " has " + count + " students in database.");
            }
            countCursor.close();

        } catch (Exception e) {
            Log.e(TAG, "Error adding session: " + e.getMessage(), e);
        } finally {
            if (db.inTransaction()) {
                db.endTransaction();
            }
            db.close();
        }

        // رفع الجلسة لـ Firebase
        createSessionInFirebase(sessionId, studentIds);
    }

    /**
     * تحديث حالة حضور طالب
     */
    /**
     * تحديث حالة حضور طالب
     */
    public boolean updateAttendance(String studentId, Context context, Uri excelUri) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ATTENDANCE, 1);

        String cleanId = cleanStudentId(studentId);
        int rowsUpdated = db.update(TABLE_STUDENTS, values, COLUMN_STUDENT_ID + " = ?", new String[]{cleanId});
        Log.d(TAG, "🧪 [ATTENDANCE] rowsUpdated = " + rowsUpdated + " للطالب " + cleanId);
        db.close();

        if (rowsUpdated > 0) {
            Log.d(TAG, "✅ تم تحديث الحضور للطالب: " + cleanId);

            String studentName = getStudentNameFromDB(cleanId);
            String sessionId = getValidSessionIdForStudent(cleanId);

            if (sessionId != null) {
                try {
                    // إنشاء طابع زمني للحضور
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    String attendanceTime = sdf.format(new Date());

                    FirebaseDatabase.getInstance()
                            .getReference("attendance")
                            .child(sessionId)
                            .child(cleanId)
                            .setValue("Present - " + studentName + " - " + attendanceTime);
                } catch (Exception e) {
                    Log.e(TAG, "❌ خطأ في تحديث Firebase", e);
                }
            }

            return true;
        } else {
            Log.w(TAG, "⚠️ لم يتم العثور على الطالب في قاعدة البيانات: " + cleanId);
            return false;
        }
    }

    /**
     * الحصول على معرّف الجلسة الصالحة للطالب
     */
    public String getValidSessionIdForStudent(String studentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String currentTime = String.valueOf(System.currentTimeMillis());
        String cleanId = cleanStudentId(studentId);

        String sessionId = null;
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT " + COLUMN_SESSION_ID + " FROM " + TABLE_SESSIONS +
                            " WHERE " + COLUMN_STUDENT_ID + " = ? AND " + COLUMN_EXPIRY_TIME + " > ? ORDER BY " + COLUMN_EXPIRY_TIME + " DESC LIMIT 1",
                    new String[]{cleanId, currentTime}
            );
            if (cursor.moveToFirst()) {
                sessionId = cursor.getString(0);
                Log.d(TAG, "Found valid session for student " + cleanId + ": " + sessionId);
            } else {
                Log.d(TAG, "No valid session found for student " + cleanId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching session_id: " + e.getMessage(), e);
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }

        return sessionId;
    }

    /**
     * التحقق من وجود الطالب في قاعدة البيانات
     */
    public boolean isStudentExists(String studentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String cleanId = cleanStudentId(studentId);

        String query = "SELECT 1 FROM " + TABLE_STUDENTS + " WHERE " + COLUMN_STUDENT_ID + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{cleanId});

        boolean exists = cursor.moveToFirst();
        cursor.close();
        db.close();

        Log.d(TAG, "Student " + cleanId + " exists: " + exists);
        return exists;
    }

    /**
     * التحقق من وجود الطالب في جلسة صالحة
     */
    public boolean isStudentInValidSession(String studentId, String sessionId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String currentTime = String.valueOf(System.currentTimeMillis());

        // تنظيف وتوحيد تنسيق رقم الطالب
        String cleanId = cleanStudentId(studentId);

        Log.d(TAG, "🔍 Checking session validity for:");
        Log.d(TAG, "Student ID (original): " + studentId);
        Log.d(TAG, "Student ID (cleaned): " + cleanId);
        Log.d(TAG, "Session ID: " + sessionId);
        Log.d(TAG, "Current Time: " + currentTime);

        // التحقق من وجود الطالب في قاعدة البيانات
        Cursor studentCursor = db.rawQuery(
                "SELECT 1 FROM " + TABLE_STUDENTS + " WHERE " + COLUMN_STUDENT_ID + " = ?",
                new String[]{cleanId});

        boolean studentExists = studentCursor.moveToFirst();
        studentCursor.close();

        if (!studentExists) {
            Log.d(TAG, "❌ الطالب غير موجود في قاعدة البيانات!");

            // إضافة الطالب تلقائياً إذا لم يكن موجوداً
            ContentValues values = new ContentValues();
            values.put(COLUMN_STUDENT_ID, cleanId);
            values.put(COLUMN_NAME, "Student " + cleanId);
            values.put(COLUMN_ATTENDANCE, 0);
            db.insertWithOnConflict(TABLE_STUDENTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            Log.d(TAG, "✅ تمت إضافة الطالب تلقائياً: " + cleanId);
        }

        // التحقق من وجود الجلسة في قاعدة البيانات
        Cursor sessionCursor = db.rawQuery(
                "SELECT 1 FROM " + TABLE_SESSIONS + " WHERE " + COLUMN_SESSION_ID + " = ?",
                new String[]{sessionId});

        boolean sessionExists = sessionCursor.moveToFirst();
        sessionCursor.close();

        if (!sessionExists) {
            Log.d(TAG, "❌ الجلسة غير موجودة في قاعدة البيانات!");
            db.close();
            return false;
        }

        // التحقق من وجود الطالب في الجلسة
        String query = "SELECT 1 FROM " + TABLE_SESSIONS +
                " WHERE " + COLUMN_STUDENT_ID + " = ? AND " + COLUMN_SESSION_ID + " = ? AND " + COLUMN_EXPIRY_TIME + " > ?";

        Cursor cursor = db.rawQuery(query, new String[]{cleanId, sessionId, currentTime});
        boolean exists = cursor.moveToFirst();

        Log.d(TAG, "Final Result: " + (exists ? "✅ VALID SESSION" : "❌ INVALID SESSION"));

        // إذا لم يكن الطالب في الجلسة، أضفه تلقائياً
        if (!exists) {
            // الحصول على وقت انتهاء الجلسة
            Cursor expiryCursor = db.rawQuery(
                    "SELECT " + COLUMN_EXPIRY_TIME + " FROM " + TABLE_SESSIONS +
                            " WHERE " + COLUMN_SESSION_ID + " = ? LIMIT 1",
                    new String[]{sessionId});

            if (expiryCursor.moveToFirst()) {
                long expiryTime = expiryCursor.getLong(0);
                expiryCursor.close();

                // إضافة الطالب إلى الجلسة
                ContentValues values = new ContentValues();
                values.put(COLUMN_SESSION_ID, sessionId);
                values.put(COLUMN_STUDENT_ID, cleanId);
                values.put(COLUMN_EXPIRY_TIME, expiryTime);

                long result = db.insertWithOnConflict(TABLE_SESSIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                Log.d(TAG, "✅ تمت إضافة الطالب " + cleanId + " إلى الجلسة " + sessionId + " تلقائياً, result: " + result);

                exists = true;
            } else {
                expiryCursor.close();
            }
        }

        // طباعة عدد الصفوف في جدول الجلسات للتصحيح
        Cursor countCursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_SESSIONS, null);
        countCursor.moveToFirst();
        int sessionCount = countCursor.getInt(0);
        countCursor.close();
        Log.d(TAG, "Total sessions in database: " + sessionCount);

        // طباعة جميع الجلسات للطالب للتصحيح
        Cursor studentSessionsCursor = db.rawQuery(
                "SELECT " + COLUMN_SESSION_ID + ", " + COLUMN_EXPIRY_TIME + " FROM " + TABLE_SESSIONS +
                        " WHERE " + COLUMN_STUDENT_ID + " = ?",
                new String[]{cleanId});

        Log.d(TAG, "Sessions for student " + cleanId + ":");
        while (studentSessionsCursor.moveToNext()) {
            String sid = studentSessionsCursor.getString(0);
            long expiry = studentSessionsCursor.getLong(1);
            Log.d(TAG, "Session: " + sid + ", Expires: " + expiry +
                    ", Valid: " + (expiry > Long.parseLong(currentTime)));
        }
        studentSessionsCursor.close();

        cursor.close();
        db.close();

        return exists;
    }



    /**
     * الحصول على قائمة الحضور
     */
    public List<String[]> getAttendanceList() {
        List<String[]> attendanceList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT " + COLUMN_STUDENT_ID + ", " + COLUMN_NAME + ", " + COLUMN_ATTENDANCE +
                " FROM " + TABLE_STUDENTS;
        Cursor cursor = db.rawQuery(query, null);

        while (cursor.moveToNext()) {
            String studentId = cursor.getString(0);
            String name = cursor.getString(1);
            String status = (cursor.getInt(2) == 1) ? "Present" : "Absent";

            attendanceList.add(new String[]{studentId, name, status});
        }

        cursor.close();
        db.close();
        return attendanceList;
    }

    /**
     * إعادة تعيين حالة الحضور لجميع الطلاب
     */
    public void resetAttendance() {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ATTENDANCE, 0);
        db.update(TABLE_STUDENTS, values, null, null);
        db.close();
        Log.d(TAG, "Attendance reset for all students.");
    }

    /**
     * مسح قاعدة البيانات
     */
    public void clearDatabase() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_STUDENTS);
        db.execSQL("DELETE FROM " + TABLE_SESSIONS);
        db.close();
        Log.d(TAG, "Database cleared successfully.");
    }

    /**
     * الحصول على وقت انتهاء الجلسة
     */
    public long getSessionExpiryTime(String sessionId) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        long expiryTime = 0;
        try {
            db = this.getReadableDatabase();
            cursor = db.rawQuery("SELECT " + COLUMN_EXPIRY_TIME + " FROM " + TABLE_SESSIONS + " WHERE " + COLUMN_SESSION_ID + " = ? LIMIT 1", new String[]{sessionId});
            if (cursor.moveToFirst()) {
                expiryTime = cursor.getLong(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting session expiry time: " + e.getMessage(), e);
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }
        return expiryTime;
    }

    /**
     * إنشاء جلسة في Firebase
     */
    public void createSessionInFirebase(String sessionId, List<String> studentIds) {
        try {
            DatabaseReference dbRef = FirebaseDatabase.getInstance()
                    .getReference("attendance")
                    .child(sessionId);

            // التحقق من وجود الجلسة أولاً
            dbRef.get().addOnSuccessListener(snapshot -> {
                if (!snapshot.exists()) {
                    // إنشاء الجلسة إذا لم تكن موجودة
                    Map<String, Object> sessionData = new HashMap<>();

                    for (String studentId : studentIds) {
                        String cleanId = cleanStudentId(studentId);

                        // الحصول على اسم الطالب من قاعدة البيانات المحلية
                        String studentName = getStudentNameFromDB(cleanId);
                        if (studentName == null || studentName.isEmpty() || studentName.equals("Unknown Student")) {
                            studentName = "Student " + cleanId;
                        }

                        // تخزين حالة الطالب مع اسمه
                        sessionData.put(cleanId, "Not Yet Attended - " + studentName);
                    }

                    // حفظ البيانات في Firebase
                    dbRef.setValue(sessionData)
                            .addOnSuccessListener(unused -> {
                                Log.d(TAG, "تم إنشاء الجلسة في Firebase بنجاح: " + sessionId);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "فشل في إنشاء الجلسة في Firebase: " + e.getMessage(), e);
                            });
                } else {
                    Log.d(TAG, "الجلسة موجودة بالفعل في Firebase: " + sessionId);
                }
            }).addOnFailureListener(e -> {
                Log.e(TAG, "فشل في التحقق من وجود الجلسة في Firebase: " + e.getMessage(), e);
            });
        } catch (Exception e) {
            Log.e(TAG, "خطأ في إنشاء الجلسة في Firebase: " + e.getMessage(), e);
        }
    }
    /**
     * الحصول على اسم الطالب من قاعدة البيانات
     */
    public String getStudentNameFromDB(String studentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String name = null;

        String cleanId = cleanStudentId(studentId);

        Cursor cursor = db.rawQuery(
                "SELECT " + COLUMN_NAME + " FROM " + TABLE_STUDENTS + " WHERE " + COLUMN_STUDENT_ID + " = ?",
                new String[]{cleanId});

        if (cursor.moveToFirst()) {
            name = cursor.getString(0);
        }

        cursor.close();
        db.close();

        return name;
    }

    /**
     * تنظيف وتوحيد تنسيق رقم الطالب
     */
    private String cleanStudentId(String studentId) {
        if (studentId == null) {
            return "";
        }

        // إزالة المسافات الزائدة
        String cleanId = studentId.trim().toLowerCase();

        // محاولة تحويل الرقم إلى تنسيق موحد إذا كان رقمًا
        try {
            // تحويل الرقم العشري إلى رقم صحيح إذا كان ممكنًا
            double numValue = Double.parseDouble(cleanId);
            cleanId = String.valueOf((long) numValue);
        } catch (NumberFormatException e) {
            // إذا لم يكن رقمًا، استخدم القيمة كما هي
        }

        return cleanId;
    }
    public String getLatestValidSessionId() {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        String sessionId = null;
        try {
            db = this.getReadableDatabase();
            long currentTime = System.currentTimeMillis();
            cursor = db.rawQuery(
                    "SELECT " + COLUMN_SESSION_ID +
                            " FROM " + TABLE_SESSIONS +
                            " WHERE " + COLUMN_EXPIRY_TIME + " > ?" +
                            " ORDER BY " + COLUMN_EXPIRY_TIME + " DESC LIMIT 1",
                    new String[]{String.valueOf(currentTime)}
            );

            if (cursor.moveToFirst()) {
                sessionId = cursor.getString(0);
                Log.d(TAG, "🔁 Latest valid sessionId: " + sessionId);
            } else {
                Log.w(TAG, "⚠️ No valid session found in DB.");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting latest session: " + e.getMessage(), e);
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }

        return sessionId;
    }

}
