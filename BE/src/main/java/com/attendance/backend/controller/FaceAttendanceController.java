package com.attendance.backend.controller;

import com.attendance.backend.dto.ApiResponse;
import com.attendance.backend.dto.AttendanceRecordDTO;
import com.attendance.backend.service.AttendanceService;
import com.attendance.backend.service.StudentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Map;

/**
 * Controller xử lý điểm danh khuôn mặt.
 * Toàn bộ logic validate đã được tập trung tại AttendanceService và StudentService.
 */
@RestController
@RequestMapping("/attendance/face")
public class FaceAttendanceController {

    private final AttendanceService attendanceService;
    private final StudentService studentService;

    public FaceAttendanceController(AttendanceService attendanceService,
                                    StudentService studentService) {
        this.attendanceService = attendanceService;
        this.studentService = studentService;
    }

    /**
     * Điểm danh khuôn mặt qua form-data (multipart).
     * Yêu cầu JWT hợp lệ — studentId lấy từ token, không phải từ request.
     * Delegate hoàn toàn sang AttendanceService.checkin() để đảm bảo validate nhất quán.
     */
    @PostMapping("/check")
    public ResponseEntity<ApiResponse<AttendanceRecordDTO>> checkFace(
            @RequestParam("image") MultipartFile image,
            @RequestParam("scheduleId") String scheduleId,
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            Authentication authentication
    ) {
        try {
            String studentId = authentication.getName();
            String base64Image = Base64.getEncoder().encodeToString(image.getBytes());

            AttendanceRecordDTO result = attendanceService.checkin(studentId, scheduleId, base64Image, lat, lng);

            return ResponseEntity.ok(
                    new ApiResponse<>("Điểm danh thành công", "SUCCESS", "", result)
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>(e.getMessage(), "ERROR", e.getMessage(), null)
            );
        }
    }

    /**
     * Đăng ký khuôn mặt cho sinh viên qua form-data (multipart).
     * Chỉ ADMIN hoặc TEACHER mới được gọi endpoint này (đăng ký hộ).
     * Validate trùng khuôn mặt được xử lý bởi StudentService.registerFace().
     */
    @PostMapping("/register-face/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<?>> registerFace(
            @PathVariable String studentId,
            @RequestParam("image") MultipartFile image
    ) {
        try {
            String base64Image = Base64.getEncoder().encodeToString(image.getBytes());
            studentService.registerFace(studentId, base64Image);
            return ResponseEntity.ok(
                    new ApiResponse<>("Đăng ký khuôn mặt thành công", "SUCCESS", "", null)
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>(e.getMessage(), "ERROR", e.getMessage(), null)
            );
        }
    }

    /**
     * Cập nhật khuôn mặt cho sinh viên qua form-data (multipart).
     * Chỉ ADMIN hoặc TEACHER mới được gọi endpoint này.
     */
    @PutMapping("/update-face/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<?>> updateFace(
            @PathVariable String studentId,
            @RequestParam("image") MultipartFile image
    ) {
        try {
            String base64Image = Base64.getEncoder().encodeToString(image.getBytes());
            studentService.updateFace(studentId, base64Image);
            return ResponseEntity.ok(
                    new ApiResponse<>("Cập nhật khuôn mặt thành công", "SUCCESS", "", null)
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>(e.getMessage(), "ERROR", e.getMessage(), null)
            );
        }
    }
}
