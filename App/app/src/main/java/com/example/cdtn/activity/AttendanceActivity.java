package com.example.cdtn.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import android.Manifest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.cdtn.R;
import com.example.cdtn.api.ApiService;
import com.example.cdtn.api.RetrofitClient;
import com.example.cdtn.model.ApiResponse;
import com.example.cdtn.model.AttendanceRequest;
import com.example.cdtn.model.AttendanceResponse;
import com.example.cdtn.model.AvailableSchedule;
import com.example.cdtn.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AttendanceActivity extends AppCompatActivity {
    private static final int CAMERA_REQUEST = 101;

    private Bitmap bitmap;
    private Spinner spinnerSchedule;
    private List<AvailableSchedule> availableSchedules = new ArrayList<>();
    private Button btnAttendance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        spinnerSchedule = findViewById(R.id.spinnerSchedule);
        ImageView imageView = findViewById(R.id.imageView);
        Button btnCapture = findViewById(R.id.btnCapture);
        btnAttendance = findViewById(R.id.btnAttendance);

        // Khóa nút điểm danh cho đến khi load được lịch học
        btnAttendance.setEnabled(false);

        btnCapture.setOnClickListener(v -> openCamera());

        btnAttendance.setOnClickListener(v -> {
            if (bitmap == null) {
                Toast.makeText(this, "Chưa chụp ảnh", Toast.LENGTH_SHORT).show();
                return;
            }

            if (spinnerSchedule.getSelectedItem() == null) {
                Toast.makeText(this, "Vui lòng chọn môn học", Toast.LENGTH_SHORT).show();
                return;
            }

            AvailableSchedule selected = (AvailableSchedule) spinnerSchedule.getSelectedItem();
            String base64 = ImageUtils.bitmapToBase64(bitmap);

            AttendanceRequest request = new AttendanceRequest(base64, selected.getScheduleId());

            ApiService apiService = RetrofitClient
                    .getClient(this)
                    .create(ApiService.class);

            apiService.attendance(request)
                    .enqueue(new Callback<ApiResponse<AttendanceResponse>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<AttendanceResponse>> call,
                                               Response<ApiResponse<AttendanceResponse>> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                AttendanceResponse data = response.body().getData();
                                Toast.makeText(AttendanceActivity.this,
                                        "Điểm danh thành công!\n" +
                                                "Sinh viên: " + data.getStudentName() + "\n" +
                                                "Thời gian: " + data.getCheckInTime(),
                                        Toast.LENGTH_LONG).show();
                                finish();
                            } else {
                                String errorMsg = "Lỗi hệ thống";
                                try {
                                    if (response.errorBody() != null) {
                                        String errorJson = response.errorBody().string();
                                        // Cố gắng lấy message từ JSON nếu có
                                        if (errorJson.contains("\"message\":\"")) {
                                            errorMsg = errorJson.split("\"message\":\"")[1].split("\"")[0];
                                        } else {
                                            errorMsg = response.message();
                                        }
                                    }
                                } catch (Exception e) {
                                    errorMsg = response.message();
                                }
                                Toast.makeText(AttendanceActivity.this, "Thất bại: " + errorMsg, Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<AttendanceResponse>> call, Throwable t) {
                            Toast.makeText(AttendanceActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        loadAvailableSchedules();
    }

    private void loadAvailableSchedules() {
        ApiService apiService = RetrofitClient.getClient(this).create(ApiService.class);
        apiService.getAvailableSchedules().enqueue(new Callback<ApiResponse<List<AvailableSchedule>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<AvailableSchedule>>> call, Response<ApiResponse<List<AvailableSchedule>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    availableSchedules = response.body().getData();
                    if (availableSchedules == null || availableSchedules.isEmpty()) {
                        Toast.makeText(AttendanceActivity.this, "Hiện không có buổi học nào mở điểm danh", Toast.LENGTH_LONG).show();
                    } else {
                        ArrayAdapter<AvailableSchedule> adapter = new ArrayAdapter<>(
                                AttendanceActivity.this,
                                android.R.layout.simple_spinner_item,
                                availableSchedules
                        );
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        spinnerSchedule.setAdapter(adapter);
                        btnAttendance.setEnabled(true);
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<AvailableSchedule>>> call, Throwable t) {
                Toast.makeText(AttendanceActivity.this, "Lỗi tải lịch học: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 102);
        } else {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra("android.intent.extras.CAMERA_FACING", 1);
            startActivityForResult(intent, CAMERA_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 102 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            Toast.makeText(this, "Cần cấp quyền camera để chụp ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            bitmap = (Bitmap) data.getExtras().get("data");
            ImageView imageView = findViewById(R.id.imageView);
            imageView.setImageBitmap(bitmap);
        }
    }
}
