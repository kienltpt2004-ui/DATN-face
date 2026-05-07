package com.example.cdtn.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cdtn.R;
import com.example.cdtn.api.ApiService;
import com.example.cdtn.api.RetrofitClient;
import com.example.cdtn.model.ApiResponse;
import com.example.cdtn.model.FaceRequest;
import com.example.cdtn.utils.ImageUtils;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import android.Manifest;
public class RegisterFaceActivity extends AppCompatActivity {
    private static final int CAMERA_REQUEST = 100;

    private Bitmap bitmap;
    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_face);
        imageView = findViewById(R.id.imageView);
        Button btnCapture = findViewById(R.id.btnCapture);
        Button btnRegister = findViewById(R.id.btnRegister);
        btnCapture.setOnClickListener(v -> openCamera());

        btnRegister.setOnClickListener(v -> registerFace());
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 102);
        } else {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
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
            imageView.setImageBitmap(bitmap);
        }
    }
    private void registerFace() {

        if (bitmap == null) {
            Toast.makeText(this, "Chưa chụp ảnh", Toast.LENGTH_SHORT).show();
            return;
        }

        String base64 = ImageUtils.bitmapToBase64(bitmap);

        FaceRequest request = new FaceRequest(base64);

        ApiService apiService = RetrofitClient
                .getClient(this)
                .create(ApiService.class);

        apiService.registerFace(request)
                .enqueue(new Callback<ApiResponse<Object>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Object>> call,
                                           Response<ApiResponse<Object>> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(RegisterFaceActivity.this,
                                    "Đăng ký thành công",
                                    Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            String errorMsg = "Lỗi hệ thống";
                            try {
                                if (response.errorBody() != null) {
                                    String errorJson = response.errorBody().string();
                                    if (errorJson.contains("\"message\":\"")) {
                                        errorMsg = errorJson.split("\"message\":\"")[1].split("\"")[0];
                                    }
                                }
                            } catch (Exception e) {}
                            Toast.makeText(RegisterFaceActivity.this,
                                    "Thất bại: " + errorMsg,
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Object>> call,
                                          Throwable t) {

                        Toast.makeText(RegisterFaceActivity.this,
                                t.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

}
