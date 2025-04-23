package com.example.test;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DisplayActivity extends AppCompatActivity {
    private static final String TAG = "DisplayActivity";
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Uri attendanceFileUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display);

        setupToolbar();

        // التحقق مما إذا كان هناك تحديث مطلوب
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("attendance_file_uri")) {
            attendanceFileUri = Uri.parse(intent.getStringExtra("attendance_file_uri"));
        }

        // تحميل وعرض البيانات من الإكسل
        if (getIntent() != null && getIntent().hasExtra("excel_data")) {
            displayDataFromIntent();  // ← الأولوية للبيانات الجاهزة
        } else if (attendanceFileUri != null) {
            reloadExcelData();        // ← fallback للقراءة من Excel
        } else {
            Toast.makeText(this, "لا توجد بيانات لعرضها!", Toast.LENGTH_LONG).show();
        }

    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void displayDataFromIntent() {
        TextView textView = findViewById(R.id.displayTextView);
        textView.setMovementMethod(new ScrollingMovementMethod());

        Intent intent = getIntent();
        if (intent == null || !intent.hasExtra("excel_data")) {
            textView.setText("خطأ: لم يتم استلام البيانات المطلوبة.");
            Toast.makeText(this, "خطأ في استلام البيانات!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Intent or excel_data is missing");
            return;
        }

        String rawData = intent.getStringExtra("excel_data");
        if (rawData == null || rawData.trim().isEmpty()) {
            textView.setText("لا توجد بيانات للعرض!");
            Toast.makeText(this, "لم تصل أي بيانات!", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Intent data (excel_data) is null or empty");
            return;
        }

        // معالجة البيانات وعرضها
        processAndDisplayData(rawData, textView);
    }

    private void reloadExcelData() {
        if (attendanceFileUri == null) {
            Toast.makeText(this, "ملف الإكسل غير متوفر!", Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            String updatedData = readExcelData(attendanceFileUri);
            mainHandler.post(() -> {
                if (updatedData != null) {
                    TextView textView = findViewById(R.id.displayTextView);
                    processAndDisplayData(updatedData, textView);
                } else {
                    Toast.makeText(this, "فشل في إعادة تحميل البيانات!", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private String readExcelData(Uri fileUri) {
        StringBuilder result = new StringBuilder();
        try (InputStream fis = getContentResolver().openInputStream(fileUri);
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                Log.e(TAG, "لا توجد بيانات في ملف الإكسل!");
                return null;
            }

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // تخطي صف العناوين

                Cell idCell = row.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                Cell nameCell = row.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                Cell attendanceCell = row.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                Cell timeCell = row.getCell(3, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK); // إضافة خلية الوقت

                String studentId = idCell.toString().trim();
                String studentName = nameCell.toString().trim();
                String attendanceStatus = attendanceCell.toString().trim();
                String attendanceTime = timeCell != null ? timeCell.toString().trim() : "";

                // التحقق من حالة الحضور وتنسيقها بشكل صحيح
                if (attendanceStatus.toLowerCase().contains("present")) {
                    attendanceStatus = "Present";
                } else {
                    attendanceStatus = "Absent";
                }

                // إضافة وقت الحضور إذا كان متاحاً
                String displayText = studentId + " - " + studentName + ": " + attendanceStatus;
                if (!attendanceTime.isEmpty() && attendanceStatus.equals("Present")) {
                    displayText += " (" + attendanceTime + ")";
                }

                result.append(displayText).append("\n\n");
            }

            Log.d(TAG, "تم تحميل البيانات من ملف الإكسل بنجاح!");
            return result.toString();

        } catch (Exception e) {
            Log.e(TAG, "خطأ في قراءة بيانات الإكسل", e);
            return null;
        }
    }

    private void processAndDisplayData(String rawData, TextView textView) {
        Set<String> uniqueLines = new LinkedHashSet<>(Arrays.asList(rawData.split("\n")));
        SpannableStringBuilder formattedData = new SpannableStringBuilder();

        for (String line : uniqueLines) {
            if (line.trim().isEmpty()) continue;
            SpannableString spannableLine = new SpannableString(line + "\n");
            applyTextColor(spannableLine, "Present", Color.GREEN);
            applyTextColor(spannableLine, "Absent", Color.RED);
            formattedData.append(spannableLine);
        }

        textView.setText(formattedData);
    }

    private void applyTextColor(SpannableString text, String target, int color) {
        String textStr = text.toString();
        int start = textStr.indexOf(target);
        while (start != -1) {
            int end = start + target.length();
            text.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = textStr.indexOf(target, end);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}