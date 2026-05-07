package com.example.cdtn.activity;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cdtn.R;
import com.example.cdtn.utils.SharedPrefManager;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btnChangePassword).setOnClickListener(v -> {
            startActivity(new Intent(this, ChangePasswordActivity.class));
        });

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            new SharedPrefManager(this).logout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
