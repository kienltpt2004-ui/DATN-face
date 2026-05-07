package com.attendance.backend.service;

import com.attendance.backend.dto.AttendanceSummaryDTO;
import com.attendance.backend.dto.AttendanceRecordDTO;
import com.attendance.backend.dto.BulkAttendanceRequest;
import com.attendance.backend.entity.AttendanceRecord;
import com.attendance.backend.entity.Student;
import com.attendance.backend.exception.ResourceNotFoundException;
import com.attendance.backend.repository.AttendanceRecordRepository;
import com.attendance.backend.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AttendanceService {

    private static final Logger logger = LoggerFactory.getLogger(AttendanceService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("[HH:mm][H:mm][HH:mm:ss]");

    private final AttendanceRecordRepository attendanceRepo;
    private final StudentRepository studentRepository;
    private final com.attendance.backend.repository.ScheduleRepository scheduleRepository;
    private final FaceRecognitionService faceRecognitionService;

    public AttendanceService(AttendanceRecordRepository attendanceRepo, 
                            StudentRepository studentRepository,
                            com.attendance.backend.repository.ScheduleRepository scheduleRepository,
                            FaceRecognitionService faceRecognitionService) {
        this.attendanceRepo = attendanceRepo;
        this.studentRepository = studentRepository;
        this.scheduleRepository = scheduleRepository;
        this.faceRecognitionService = faceRecognitionService;
    }

    /** Lấy danh sách điểm danh theo lớp và ngày */
    public List<AttendanceRecordDTO> getByClassAndDate(String classId, LocalDate date) {
        return attendanceRepo.findValidRecordsByClassAndDate(classId, date)
                .stream().map(this::toDTO).toList();
    }

    /** Lấy lịch sử điểm danh của 1 sinh viên trong khoảng ngày */
    public List<AttendanceRecordDTO> getByStudentAndDateRange(String studentId, LocalDate from, LocalDate to) {
        return attendanceRepo.findByStudentIdAndDateBetween(studentId, from, to)
                .stream().map(this::toDTO).toList();
    }

    /** Lấy toàn bộ điểm danh theo lớp trong khoảng ngày */
    public List<AttendanceRecordDTO> getByClassAndDateRange(String classId, LocalDate from, LocalDate to) {
        return attendanceRepo.findValidRecordsByClassAndDateRange(classId, from, to)
                .stream().map(this::toDTO).toList();
    }

    /** Ghi nhận điểm danh hàng loạt cho cả lớp */
    @Transactional
    public List<AttendanceRecordDTO> bulkSave(BulkAttendanceRequest request) {
        LocalDate date = LocalDate.parse(request.getDate());
        
        java.time.ZoneId zoneId = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
        if (!date.equals(LocalDate.now(zoneId))) {
            throw new RuntimeException("Chỉ được phép điểm danh cho ngày hôm nay (" + LocalDate.now(zoneId) + ")");
        }

        // Ràng buộc 2: Chỉ được điểm danh vào những ngày có lịch dạy
        String dayOfWeekStr = getVietnameseDayOfWeek(date.getDayOfWeek().getValue());
        List<com.attendance.backend.entity.Schedule> daySchedules = scheduleRepository.findByClassId(request.getClassId()).stream()
                .filter(s -> s.getDayOfWeek().equalsIgnoreCase(dayOfWeekStr))
                .toList();
        
        if (daySchedules.isEmpty()) {
            throw new RuntimeException("Hôm nay (" + dayOfWeekStr + ") không có lịch dạy cho học phần " + request.getClassId() + ". Không thể điểm danh.");
        }

        // Ràng buộc 3: Chỉ được điểm danh trong khung giờ học (cho phép buffer 30p trước và sau)
        LocalTime now = LocalTime.now();
        logger.info("Checking attendance time for class: {}, day: {}, now: {}", request.getClassId(), dayOfWeekStr, now);

        boolean isWithinTime = daySchedules.stream().anyMatch(s -> {
            try {
                LocalTime start = LocalTime.parse(s.getStartTime(), TIME_FORMATTER).minusMinutes(30);
                LocalTime end = LocalTime.parse(s.getEndTime(), TIME_FORMATTER).plusMinutes(30);
                boolean match = !now.isBefore(start) && !now.isAfter(end);
                logger.info("Schedule: {} - {}, Match: {}", s.getStartTime(), s.getEndTime(), match);
                return match;
            } catch (DateTimeParseException e) {
                logger.error("Failed to parse time for schedule {}: {} - {}", s.getId(), s.getStartTime(), s.getEndTime());
                return false;
            }
        });

        if (!isWithinTime) {
            String timeInfo = daySchedules.stream()
                .map(s -> s.getStartTime() + " - " + s.getEndTime())
                .reduce((a, b) -> a + ", " + b).orElse("");
            logger.warn("Attendance denied: Outside scheduled time. Now: {}, Allowed: {}", now, timeInfo);
            throw new RuntimeException("Hiện tại (" + now.format(DateTimeFormatter.ofPattern("HH:mm")) + ") nằm ngoài khung giờ học của học phần này (" + timeInfo + "). Hệ thống cho phép điểm danh sớm hoặc muộn tối đa 30 phút.");
        }

        List<AttendanceRecord> saved = new ArrayList<>();

        request.getAttendanceMap().forEach((studentId, statusStr) -> {
            AttendanceRecord.AttendanceStatus status =
                    AttendanceRecord.AttendanceStatus.valueOf(statusStr.toLowerCase());

            // Ràng buộc 2: Sinh viên phải thuộc đúng lớp mới được điểm danh
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sinh viên: " + studentId));
            
            boolean isInClass = student.getClasses().stream()
                    .anyMatch(c -> c.getId().equals(request.getClassId()));
            
            if (!isInClass) {
                throw new RuntimeException("Sinh viên " + studentId + " không thuộc học phần " + request.getClassId());
            }

            // Upsert: nếu đã có thì cập nhật (dựa trên student, date, class và schedule)
            AttendanceRecord record = attendanceRepo
                    .findByStudentIdAndDateAndClassIdAndScheduleId(studentId, date, request.getClassId(), request.getScheduleId())
                    .orElse(null);

            if (record == null) {
                record = AttendanceRecord.builder()
                        .studentId(studentId)
                        .studentName(student.getName())
                        .classId(request.getClassId())
                        .date(date)
                        .scheduleId(request.getScheduleId())
                        .method(AttendanceRecord.Method.MANUAL)
                        .build();
            }

            record.setStatus(status);
            if (status == AttendanceRecord.AttendanceStatus.present) {
                record.setCheckInTime(LocalTime.now());
            }
            saved.add(attendanceRepo.save(record));
        });

        return saved.stream().map(this::toDTO).toList();
    }

    private String getVietnameseDayOfWeek(int dayValue) {
        return switch (dayValue) {
            case 1 -> "Thứ 2";
            case 2 -> "Thứ 3";
            case 3 -> "Thứ 4";
            case 4 -> "Thứ 5";
            case 5 -> "Thứ 6";
            case 6 -> "Thứ 7";
            case 7 -> "Chủ Nhật";
            default -> "Thứ 2";
        };
    }

    /** Cập nhật 1 bản ghi điểm danh */
    @Transactional
    public AttendanceRecordDTO updateRecord(Long id, AttendanceRecordDTO dto) {
        AttendanceRecord record = attendanceRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản ghi điểm danh: " + id));

        record.setStatus(AttendanceRecord.AttendanceStatus.valueOf(dto.getStatus().toLowerCase()));
        if (dto.getNote() != null) record.setNote(dto.getNote());
        if (dto.getCheckInTime() != null) {
            record.setCheckInTime(LocalTime.parse(dto.getCheckInTime()));
        }

        return toDTO(attendanceRepo.save(record));
    }

    /** Thống kê tỷ lệ có mặt và mức độ cảnh báo của sinh viên */
    public AttendanceSummaryDTO getAttendanceSummary(String studentId, LocalDate from, LocalDate to) {
        long total = attendanceRepo.countTotalByStudentAndDateRange(studentId, from, to);
        if (total == 0) {
            return new AttendanceSummaryDTO(studentId, 0.0, "NONE", "Chưa có dữ liệu điểm danh");
        }
        
        long present = attendanceRepo.countByStatusAndStudentAndDateRange(studentId, AttendanceRecord.AttendanceStatus.present, from, to);
        double rate = Math.round((double) present / total * 1000.0) / 10.0;
        double absenceRate = 100.0 - rate;
        
        String alertLevel = "NONE";
        String message = "Chuyên cần tốt";
        
        if (absenceRate > 20.0) {
            alertLevel = "RED";
            message = "Cảnh báo Đỏ: Nghỉ quá 20% (" + absenceRate + "%). Nguy cơ cấm thi!";
        } else if (absenceRate > 15.0) {
            alertLevel = "YELLOW";
            message = "Cảnh báo Vàng: Nghỉ quá 15% (" + absenceRate + "%). Cần chú ý chuyên cần.";
        }
        
        return new AttendanceSummaryDTO(studentId, rate, alertLevel, message);
    }

    private AttendanceRecordDTO toDTO(AttendanceRecord r) {
        return AttendanceRecordDTO.builder()
                .id(r.getId())
                .studentId(r.getStudentId())
                .studentName(r.getStudentName())
                .classId(r.getClassId())
                .date(r.getDate())
                .status(r.getStatus().name())
                .checkInTime(r.getCheckInTime() != null
                        ? r.getCheckInTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : null)
                .scheduleId(r.getScheduleId())
                .note(r.getNote())
                .method(r.getMethod() != null ? r.getMethod().name() : null)
                .build();
    }

    @Transactional
    public AttendanceRecordDTO checkin(String studentId, String scheduleId, String base64Image) {
        logger.info("[DEBUG] Bắt đầu Check-in cho SV: {} | Lịch dạy: {}", studentId, scheduleId);
        
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sinh viên"));

        com.attendance.backend.entity.Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch học"));

        if (student.getFaceEmbedding() == null) {
            logger.warn("[DEBUG] SV {} chưa đăng ký khuôn mặt", studentId);
            throw new RuntimeException("Chưa đăng ký khuôn mặt");
        }

        boolean isInClass = student.getClasses().stream()
                .anyMatch(c -> c.getId().equals(schedule.getClassId()));
        
        if (!isInClass) {
            logger.warn("[DEBUG] SV {} không thuộc lớp {}", studentId, schedule.getClassId());
            throw new RuntimeException("Sinh viên không thuộc học phần này");
        }

        java.time.ZoneId zoneId = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
        boolean existed = attendanceRepo.findByStudentIdAndDateAndClassIdAndScheduleId(
                studentId, LocalDate.now(zoneId), schedule.getClassId(), schedule.getId()
        ).isPresent();

        if (existed) {
            logger.warn("[DEBUG] SV {} đã điểm danh lớp này hôm nay", studentId);
            throw new RuntimeException("Đã điểm danh rồi");
        }

        try {
            logger.info("[DEBUG] Đang gửi ảnh đi xác thực khuôn mặt...");
            Map<String, Object> faceRes = faceRecognitionService.verifyFace(base64Image, student.getFaceEmbedding());
            boolean matched = (boolean) faceRes.getOrDefault("match", false);
            double similarity = (double) faceRes.getOrDefault("similarity", 0.0);
            
            logger.info("[DEBUG] Kết quả FaceID: Khớp={} | Độ tương đồng={}", matched, similarity);
            
            if (!matched) {
                throw new RuntimeException("Khuôn mặt không khớp (Độ tương đồng: " + similarity + ")");
            }
        } catch (Exception e) {
            logger.error("[DEBUG] Lỗi FaceID: {}", e.getMessage());
            throw new RuntimeException("Lỗi xác thực khuôn mặt: " + e.getMessage());
        }

        LocalTime now = LocalTime.now(zoneId);
        LocalTime startTime = LocalTime.parse(schedule.getStartTime(), TIME_FORMATTER);
        LocalTime endTime = LocalTime.parse(schedule.getEndTime(), TIME_FORMATTER);

        if (now.isAfter(endTime.plusMinutes(30))) {
            logger.warn("[DEBUG] Điểm danh thất bại: Đã quá giờ kết thúc lớp ({}). Giờ hiện tại: {}", endTime, now);
            throw new RuntimeException("Đã quá thời gian điểm danh (lớp đã kết thúc)");
        }

        AttendanceRecord.AttendanceStatus status = now.isAfter(startTime.plusMinutes(15))
                ? AttendanceRecord.AttendanceStatus.late
                : AttendanceRecord.AttendanceStatus.present;

        AttendanceRecord record = AttendanceRecord.builder()
                .studentId(studentId)
                .studentName(student.getName())
                .classId(schedule.getClassId())
                .date(LocalDate.now(zoneId))
                .scheduleId(schedule.getId())
                .method(AttendanceRecord.Method.FACE_ID)
                .status(status)
                .checkInTime(now)
                .note(status == AttendanceRecord.AttendanceStatus.late ? "Đi muộn" : "Đúng giờ")
                .build();

        logger.info("[DEBUG] Điểm danh THÀNH CÔNG cho SV: {} | Trạng thái: {}", studentId, status);
        return toDTO(attendanceRepo.save(record));
    }

    @Transactional(readOnly = true)
    public List<com.attendance.backend.dto.AvailableScheduleDTO> getAvailableSchedulesForStudent(String studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sinh viên"));

        logger.info("[DEBUG] Bắt đầu tìm lịch cho SV: {}", studentId);
        
        List<String> classIds = student.getClasses().stream()
                .map(com.attendance.backend.entity.ClassRoom::getId).toList();
        
        java.time.ZoneId zoneId = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.now(zoneId);
        String dayOfWeekStr = getVietnameseDayOfWeek(today.getDayOfWeek().getValue());
        LocalTime now = LocalTime.now(zoneId);
        
        logger.info("[DEBUG] Lớp của SV: {} | Hôm nay: {} | Thứ: {} | Giờ hiện tại: {}", classIds, today, dayOfWeekStr, now);

        if (classIds.isEmpty()) {
            logger.warn("[DEBUG] SV {} không thuộc lớp nào!", studentId);
            return List.of();
        }

        List<com.attendance.backend.entity.Schedule> allSchedules = new ArrayList<>();
        for (String cid : classIds) {
            List<com.attendance.backend.entity.Schedule> found = scheduleRepository.findByClassIdAndDayOfWeek(cid, dayOfWeekStr);
            logger.info("[DEBUG] Lớp {}: Tìm thấy {} lịch dạy trong DB cho ngày {}", cid, found.size(), dayOfWeekStr);
            allSchedules.addAll(found);
        }

        List<com.attendance.backend.dto.AvailableScheduleDTO> result = allSchedules.stream().filter(s -> {
            try {
                LocalTime startTime = LocalTime.parse(s.getStartTime(), TIME_FORMATTER);
                LocalTime endTime = LocalTime.parse(s.getEndTime(), TIME_FORMATTER);
                LocalTime startLimit = startTime.minusMinutes(30);
                LocalTime endLimit = endTime.plusMinutes(30);
                boolean isMatch = !now.isBefore(startLimit) && !now.isAfter(endLimit);
                
                logger.info("[DEBUG] Kiểm tra môn {}: {} - {}. Khung giờ cho phép: {} - {}. Kết quả: {}", 
                    s.getSubject(), s.getStartTime(), s.getEndTime(), startLimit, endLimit, isMatch);
                
                return isMatch;
            } catch (Exception e) {
                logger.error("[DEBUG] Lỗi parse giờ lịch dạy {}: {}", s.getId(), e.getMessage());
                return false;
            }
        }).map(s -> new com.attendance.backend.dto.AvailableScheduleDTO(
                s.getId(), s.getClassId(), s.getSubject(), s.getStartTime() + " - " + s.getEndTime()
        )).toList();

        logger.info("[DEBUG] KẾT THÚC: Hiển thị {} lớp lên App.", result.size());
        return result;
    }

}
