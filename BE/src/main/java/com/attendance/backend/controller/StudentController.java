package com.attendance.backend.controller;

import com.attendance.backend.dto.StudentDTO;
import com.attendance.backend.service.StudentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import com.attendance.backend.dto.ApiResponse;
import com.attendance.backend.dto.FaceRequest;
import com.attendance.backend.dto.StudentAttendanceResponse;
import com.attendance.backend.service.AttendanceService;

@RestController
@RequestMapping("/students")
public class StudentController {

    private final StudentService studentService;
    private final com.attendance.backend.repository.TeacherRepository teacherRepository;
    private final com.attendance.backend.repository.ScheduleRepository scheduleRepository;
    private final AttendanceService attendanceService;

    public StudentController(StudentService studentService, 
                             com.attendance.backend.repository.TeacherRepository teacherRepository,
                             com.attendance.backend.repository.ScheduleRepository scheduleRepository,
                             AttendanceService attendanceService) {
        this.studentService = studentService;
        this.teacherRepository = teacherRepository;
        this.scheduleRepository = scheduleRepository;
        this.attendanceService = attendanceService;
    }

    @GetMapping
    public ResponseEntity<List<StudentDTO>> getAll(org.springframework.security.core.Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));
        boolean isTeacher = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER") || a.getAuthority().equals("TEACHER"));
        
        if (!isAdmin && isTeacher) {
            String username = authentication.getName();
            List<String> classIds = scheduleRepository.findByTeacherIdIgnoreCase(username).stream()
                    .map(com.attendance.backend.entity.Schedule::getClassId).distinct().toList();
            
            // Fallback: thử profile ID nếu không tìm thấy lịch theo username
            if (classIds.isEmpty()) {
                String profileId = teacherRepository.findByUsernameOrId(username)
                        .map(com.attendance.backend.entity.Teacher::getId).orElse(username);
                if (!profileId.equals(username)) {
                    classIds = scheduleRepository.findByTeacherIdIgnoreCase(profileId).stream()
                            .map(com.attendance.backend.entity.Schedule::getClassId).distinct().toList();
                }
            }

            if (classIds.isEmpty()) return ResponseEntity.ok(java.util.Collections.emptyList());

            // Bước 1: tìm qua bảng student_classes (enrollment chính thức)
            List<StudentDTO> teacherStudents = studentService.getStudentsByClasses(classIds);
            
            // Bước 2: Fallback tìm qua bản ghi điểm danh (cho dữ liệu cũ chưa enroll)
            if (teacherStudents.isEmpty()) {
                teacherStudents = studentService.getStudentsByAttendanceClasses(classIds);
            }
            
            // Bước 3: Nếu vẫn rỗng → trả về tất cả sinh viên (chưa có dữ liệu điểm danh)
            if (teacherStudents.isEmpty()) {
                teacherStudents = studentService.getAllStudents();
            }
            
            return ResponseEntity.ok(teacherStudents);
        }
        
        return ResponseEntity.ok(studentService.getAllStudents());
    }

    @GetMapping("/class/{classId}")
    public ResponseEntity<List<StudentDTO>> getByClass(@PathVariable String classId) {
        // Bước 1: tìm qua enrollment chính thức (student_classes)
        List<StudentDTO> students = studentService.getStudentsByClass(classId);
        // Bước 2: fallback qua bản ghi điểm danh
        if (students.isEmpty()) {
            students = studentService.getStudentsByAttendanceClasses(java.util.List.of(classId));
        }
        // Bước 3: nếu vẫn rỗng, trả về tất cả sinh viên
        if (students.isEmpty()) {
            students = studentService.getAllStudents();
        }
        return ResponseEntity.ok(students);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentDTO> getById(@PathVariable String id) {
        return ResponseEntity.ok(studentService.getStudentById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentDTO> create(@RequestBody StudentDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.createStudent(dto));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StudentDTO>> createBulk(@RequestBody List<StudentDTO> dtos) {
        return ResponseEntity.ok(studentService.createStudentsBulk(dtos));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentDTO> update(@PathVariable String id, @RequestBody StudentDTO dto) {
        return ResponseEntity.ok(studentService.updateStudent(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        studentService.deleteStudent(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentDTO> toggleActive(@PathVariable String id) {
        return ResponseEntity.ok(studentService.toggleActive(id));
    }

    @PostMapping("/me/face")
    public ResponseEntity<ApiResponse<?>> registerFace(
            @RequestBody FaceRequest request,
            org.springframework.security.core.Authentication authentication
    ) {
        String studentId = authentication.getName();
        studentService.registerFace(studentId, request.getBase64());
        return ResponseEntity.ok(
                new ApiResponse<>("Đăng ký khuôn mặt thành công", "SUCCESS", "", null)
        );
    }

    @PutMapping("/me/face-update")
    public ResponseEntity<ApiResponse<?>> updateFace(
            @RequestBody FaceRequest request,
            org.springframework.security.core.Authentication authentication
    ) {
        String studentId = authentication.getName();
        studentService.updateFace(studentId, request.getBase64());
        return ResponseEntity.ok(
                new ApiResponse<>("Cập nhật khuôn mặt thành công", "SUCCESS", "", null)
        );
    }

    @GetMapping("/me/attendance")
    public ResponseEntity<ApiResponse<List<StudentAttendanceResponse>>> getMyAttendance(
            org.springframework.security.core.Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Chưa đăng nhập");
        }

        String studentId = authentication.getName();
        
        // Lấy tất cả điểm danh
        List<com.attendance.backend.dto.AttendanceRecordDTO> records = attendanceService.getByStudentAndDateRange(
                studentId, LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1)
        );
        
        List<StudentAttendanceResponse> responseList = records.stream().map(r -> {
            StudentAttendanceResponse res = new StudentAttendanceResponse();
            res.setClassName(r.getClassId());
            
            scheduleRepository.findById(r.getScheduleId()).ifPresent(s -> res.setSubjectName(s.getSubject()));
            
            res.setAttendanceTime(r.getDate().toString() + " " + (r.getCheckInTime() != null ? r.getCheckInTime() : ""));
            return res;
        }).toList();

        return ResponseEntity.ok(
                new ApiResponse<>("Lấy lịch sử điểm danh thành công", "SUCCESS", "", responseList, new ApiResponse.MetaData(0, 1))
        );
    }
    @GetMapping("/me/schedules/available")
    public ResponseEntity<ApiResponse<List<com.attendance.backend.dto.AvailableScheduleDTO>>> getAvailableSchedules(
            org.springframework.security.core.Authentication authentication
    ) {
        String studentId = authentication.getName();
        List<com.attendance.backend.dto.AvailableScheduleDTO> schedules = attendanceService.getAvailableSchedulesForStudent(studentId);
        return ResponseEntity.ok(
                new ApiResponse<>("Lấy danh sách lịch học mở điểm danh thành công", "SUCCESS", "", schedules)
        );
    }

    @GetMapping("/me/schedules")
    public ResponseEntity<ApiResponse<List<com.attendance.backend.dto.ScheduleDTO>>> getMyWeeklySchedules(
            org.springframework.security.core.Authentication authentication
    ) {
        String studentId = authentication.getName();
        com.attendance.backend.dto.StudentDTO student = studentService.getStudentById(studentId);
        
        List<com.attendance.backend.dto.ScheduleDTO> schedules = new java.util.ArrayList<>();
        if (student.getClassId() != null && !student.getClassId().trim().isEmpty()) {
            java.util.List<String> classIds = java.util.Arrays.stream(student.getClassId().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            for (String classId : classIds) {
                schedules.addAll(scheduleRepository.findByClassId(classId).stream()
                        .map(s -> com.attendance.backend.dto.ScheduleDTO.builder()
                                .id(s.getId())
                                .classId(s.getClassId())
                                .subject(s.getSubject())
                                .teacherId(s.getTeacherId())
                                .dayOfWeek(s.getDayOfWeek())
                                .startTime(s.getStartTime())
                                .endTime(s.getEndTime())
                                .room(s.getRoom())
                                .locationId(s.getLocationId())
                                .build())
                        .toList());
            }
        }
        return ResponseEntity.ok(
                new ApiResponse<>("Lấy danh sách lịch học trong tuần thành công", "SUCCESS", "", schedules)
        );
    }
}
