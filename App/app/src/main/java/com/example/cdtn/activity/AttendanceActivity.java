package com.example.cdtn.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
// import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
// import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.IntentSender;
import android.location.Location;
import android.location.LocationManager;

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

// import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

public class AttendanceActivity extends AppCompatActivity {
    private static final int CAMERA_REQUEST = 101;
    private static final int REQUEST_CHECK_SETTINGS = 102;

    private Bitmap bitmap;
    private TextView tvActiveSubject, tvActiveTime;
    private AvailableSchedule activeSchedule;
    private Button btnAttendance;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        tvActiveSubject = findViewById(R.id.tvActiveSubject);
        tvActiveTime = findViewById(R.id.tvActiveTime);
        ImageView imageView = findViewById(R.id.imageView);
        Button btnCapture = findViewById(R.id.btnCapture);
        btnAttendance = findViewById(R.id.btnAttendance);

        btnAttendance.setEnabled(false);

        btnCapture.setOnClickListener(v -> openCamera());

        btnAttendance.setOnClickListener(v -> {
            if (bitmap == null) {
                Toast.makeText(this, "Chưa chụp ảnh", Toast.LENGTH_SHORT).show();
                return;
            }

            if (activeSchedule == null) {
                Toast.makeText(this, "Không có môn học đang mở", Toast.LENGTH_SHORT).show();
                return;
            }

            String base64 = ImageUtils.bitmapToBase64(bitmap);
            requestLocationAndSend(base64, activeSchedule.getScheduleId());
        });

        loadAvailableSchedules();
        requestLocationPermission();
    }

    @SuppressLint("MissingPermission")
    private void requestLocationAndSend(String base64, String scheduleId) {
        Toast.makeText(this, "Đang lấy vị trí GPS...", Toast.LENGTH_SHORT).show();

        // Dùng getCurrentLocation để lấy vị trí HIỆN TẠI, không phải cache cũ
        com.google.android.gms.location.CurrentLocationRequest locationRequest =
                new com.google.android.gms.location.CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setMaxUpdateAgeMillis(0) // Không dùng cache, bắt buộc lấy mới
                        .setDurationMillis(10000)
                        .build();

        fusedLocationClient.getCurrentLocation(locationRequest, null)
                .addOnSuccessListener(this, location -> {
                    Double lat = null;
                    Double lng = null;
                    if (location != null) {
                        lat = location.getLatitude();
                        lng = location.getLongitude();
                        Toast.makeText(this, "GPS: " + String.format("%.5f, %.5f", lat, lng), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Không lấy được vị trí GPS, sẽ thử điểm danh không có GPS", Toast.LENGTH_SHORT).show();
                    }

                    sendAttendance(base64, scheduleId, lat, lng);
                })
                .addOnFailureListener(this, e -> {
                    Toast.makeText(this, "Lỗi lấy GPS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    sendAttendance(base64, scheduleId, null, null);
                });
    }

    private void sendAttendance(String base64, String scheduleId, Double lat, Double lng) {
        AttendanceRequest request = new AttendanceRequest(base64, scheduleId, lat, lng);
        ApiService apiService = RetrofitClient.getClient(this).create(ApiService.class);
        apiService.attendance(request).enqueue(new Callback<ApiResponse<AttendanceResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<AttendanceResponse>> call, Response<ApiResponse<AttendanceResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AttendanceResponse data = response.body().getData();
                    String msg = "Điểm danh thành công!";
                    if (data != null && data.getStudentName() != null) {
                        msg += "\n" + data.getStudentName();
                    }
                    Toast.makeText(AttendanceActivity.this, msg, Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    String errorMsg = "Lỗi hệ thống";
                    try {
                        if (response.errorBody() != null) {
                            String errorJson = response.errorBody().string();
                            org.json.JSONObject obj = new org.json.JSONObject(errorJson);
                            errorMsg = obj.optString("message", "Lỗi hệ thống");
                        }
                    } catch (Exception ignored) {}

                    Toast.makeText(AttendanceActivity.this, "Thất bại: " + errorMsg, Toast.LENGTH_LONG).show();

                    if (errorMsg.contains("Vui lòng bật GPS")) {
                        checkGPSAndRun();
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<AttendanceResponse>> call, Throwable t) {
                Toast.makeText(AttendanceActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 103);
        } else {
            checkGPSAndRun();
        }
    }

    private void checkGPSAndRun() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .build();

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, locationSettingsResponse -> {});

        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    resolvable.startResolutionForResult(AttendanceActivity.this, REQUEST_CHECK_SETTINGS);
                } catch (IntentSender.SendIntentException ignored) {}
            }
        });
    }

    private void loadAvailableSchedules() {
        ApiService apiService = RetrofitClient.getClient(this).create(ApiService.class);
        apiService.getAvailableSchedules().enqueue(new Callback<ApiResponse<List<AvailableSchedule>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<AvailableSchedule>>> call, Response<ApiResponse<List<AvailableSchedule>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<AvailableSchedule> list = response.body().getData();
                    if (list != null && !list.isEmpty()) {
                        activeSchedule = list.get(0); // Lấy môn học đầu tiên đang mở
                        tvActiveSubject.setText(activeSchedule.getSubject());
                        tvActiveTime.setText(activeSchedule.getTimeRange());
                        btnAttendance.setEnabled(true);
                    } else {
                        tvActiveSubject.setText("Hiện không có môn học nào mở điểm danh");
                        tvActiveTime.setText("Vui lòng quay lại sau");
                        btnAttendance.setEnabled(false);
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<AvailableSchedule>>> call, Throwable t) {
                Toast.makeText(AttendanceActivity.this, "Lỗi tải lịch học", Toast.LENGTH_SHORT).show();
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
        } else if (requestCode == 103 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkGPSAndRun();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data == null || data.getExtras() == null) {
                Toast.makeText(this, "Không lấy được ảnh từ camera", Toast.LENGTH_SHORT).show();
                return;
            }
            Bitmap captured = (Bitmap) data.getExtras().get("data");
            if (captured == null) {
                Toast.makeText(this, "Ảnh rỗng, vui lòng thử lại", Toast.LENGTH_SHORT).show();
                return;
            }
            bitmap = captured;
            ImageView imageView = findViewById(R.id.imageView);
            imageView.setImageBitmap(bitmap);
        } else if (requestCode == REQUEST_CHECK_SETTINGS && resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "GPS đã được bật", Toast.LENGTH_SHORT).show();
        }
    }
}
