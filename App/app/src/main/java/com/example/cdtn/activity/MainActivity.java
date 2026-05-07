package com.example.cdtn.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cdtn.R;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        com.example.cdtn.utils.SharedPrefManager pref = new com.example.cdtn.utils.SharedPrefManager(this);
        android.widget.TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Xin chào, " + pref.getName());

        Button btnRegister = findViewById(R.id.btnRegisterFace);
        Button btnUpdate = findViewById(R.id.btnUpdateFace);
        Button btnAttendance = findViewById(R.id.btnAttendance);
        Button btnHistory = findViewById(R.id.btnHistory);

        btnRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterFaceActivity.class));
        });

        btnUpdate.setOnClickListener(v -> {
            startActivity(new Intent(this, UpdateFaceActivity.class));
        });

        btnAttendance.setOnClickListener(v -> {
            startActivity(new Intent(this, AttendanceActivity.class));
        });

        btnHistory.setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
        });

        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });
    }
}