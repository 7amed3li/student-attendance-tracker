package com.example.test;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // تهيئة التولبار
        setupToolbar();

        // استعادة حالة النشاط إذا كانت محفوظة
        if (savedInstanceState != null) {
            String toolbarTitle = savedInstanceState.getString("toolbarTitle", "Attendance System");
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(toolbarTitle);
            }
        }
    }

    // تهيئة التولبار
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Attendance System");
        }
    }

    // الانتقال إلى شاشة الطالب مع رسالة تأكيد
    public void goToStudentActivity(View view) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm")
                .setMessage("Are you sure you want to proceed as a student?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    startActivity(new Intent(this, StudentActivity.class));
                })
                .setNegativeButton("No", null)
                .show();
    }

    // الانتقال إلى شاشة الأدمن مع رسالة تأكيد
    public void goToAdminActivity(View view) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm")
                .setMessage("Are you sure you want to proceed as an admin?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    startActivity(new Intent(this, AdminActivity.class));
                })
                .setNegativeButton("No", null)
                .show();
    }

    // إدارة النقر على عناصر القائمة (مثل زر الرجوع)
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // حفظ حالة النشاط عند تغيير الاتجاه
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (getSupportActionBar() != null) {
            outState.putString("toolbarTitle", getSupportActionBar().getTitle().toString());
        }
    }

    // استعادة حالة النشاط بعد تغيير الاتجاه
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        String toolbarTitle = savedInstanceState.getString("toolbarTitle", "Attendance System");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(toolbarTitle);
        }
    }
}