package com.attendance.backend.service;

import com.attendance.backend.entity.ClassRoom;
import com.attendance.backend.exception.ResourceNotFoundException;
import com.attendance.backend.repository.AttendanceRecordRepository;
import com.attendance.backend.repository.ClassRoomRepository;
import com.attendance.backend.repository.ScheduleRepository;
import com.attendance.backend.repository.StudentRepository;
import com.attendance.backend.repository.TeacherRepository;
import com.attendance.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClassRoomService {

    private final ClassRoomRepository classRoomRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ScheduleRepository scheduleRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;

    public ClassRoomService(ClassRoomRepository classRoomRepository,
                            AttendanceRecordRepository attendanceRecordRepository,
                            ScheduleRepository scheduleRepository,
                            StudentRepository studentRepository,
                            UserRepository userRepository,
                            TeacherRepository teacherRepository) {
        this.classRoomRepository = classRoomRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.scheduleRepository = scheduleRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.teacherRepository = teacherRepository;
    }

    public List<ClassRoom> getAllClasses() {
        return classRoomRepository.findAll();
    }

    public List<ClassRoom> getByIds(List<String> ids) {
        return classRoomRepository.findAllById(ids);
    }

    public ClassRoom getById(String id) {
        return classRoomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp: " + id));
    }

    @Transactional
    public ClassRoom create(ClassRoom classRoom) {
        if (classRoom.getId() == null || classRoom.getId().isBlank())
            throw new RuntimeException("Mã lớp không được để trống");
        if (classRoom.getName() == null || classRoom.getName().isBlank())
            throw new RuntimeException("Tên lớp không được để trống");
        if (classRoomRepository.existsById(classRoom.getId()))
            throw new RuntimeException("Mã lớp đã tồn tại: " + classRoom.getId());
        if (classRoomRepository.existsByName(classRoom.getName()))
            throw new RuntimeException("Tên lớp đã tồn tại: " + classRoom.getName());
        return classRoomRepository.save(classRoom);
    }

    @Transactional
    public List<ClassRoom> createClassesBulk(List<ClassRoom> classes) {
        // Validate toàn bộ trước khi tạo bất kỳ record nào
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < classes.size(); i++) {
            ClassRoom c = classes.get(i);
            int row = i + 1;
            if (c.getId() == null || c.getId().isBlank()) {
                errors.add("Dòng " + row + ": Mã lớp không được để trống");
                continue;
            }
            if (c.getName() == null || c.getName().isBlank())
                errors.add("Dòng " + row + ": Tên lớp không được để trống");
            if (classRoomRepository.existsById(c.getId()))
                errors.add("Dòng " + row + ": Mã lớp '" + c.getId() + "' đã tồn tại");
            if (c.getName() != null && classRoomRepository.existsByName(c.getName()))
                errors.add("Dòng " + row + ": Tên lớp '" + c.getName() + "' đã tồn tại");
        }
        if (!errors.isEmpty())
            throw new RuntimeException("Lỗi import:\n" + String.join("\n", errors));

        return classRoomRepository.saveAll(classes);
    }

    @Transactional
    public ClassRoom update(String id, ClassRoom updated) {
        ClassRoom existing = getById(id);

        // Trường hợp đổi Mã lớp (ID)
        if (!id.equals(updated.getId())) {
            if (classRoomRepository.existsById(updated.getId()))
                throw new RuntimeException("Mã lớp mới '" + updated.getId() + "' đã tồn tại trong hệ thống.");

            // Cập nhật hàng loạt thay vì save từng record
            var students = studentRepository.findByClassId(id);
            students.forEach(s -> s.setClassId(updated.getId()));
            studentRepository.saveAll(students);

            var schedules = scheduleRepository.findByClassId(id);
            schedules.forEach(s -> s.setClassId(updated.getId()));
            scheduleRepository.saveAll(schedules);

            var records = attendanceRecordRepository.findByClassIdAndDateBetween(
                    id, LocalDate.of(2000, 1, 1), LocalDate.of(2100, 12, 31));
            records.forEach(r -> r.setClassId(updated.getId()));
            attendanceRecordRepository.saveAll(records);

            classRoomRepository.delete(existing);
            return classRoomRepository.save(updated);
        }

        // Trường hợp chỉ đổi tên hoặc mô tả
        if (!existing.getName().equals(updated.getName()) && classRoomRepository.existsByName(updated.getName()))
            throw new RuntimeException("Tên lớp '" + updated.getName() + "' đã tồn tại.");

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setMaxStudents(updated.getMaxStudents());
        return classRoomRepository.save(existing);
    }

    @Transactional
    public void delete(String id) {
        getById(id);

        // Xóa trực tiếp bằng query — không load vào memory
        attendanceRecordRepository.deleteByClassId(id);
        scheduleRepository.deleteByClassId(id);

        // Gỡ liên kết lớp của học sinh (không xóa học sinh)
        var students = studentRepository.findByClassId(id);
        students.forEach(s -> s.setClassId(null));
        studentRepository.saveAll(students);

        classRoomRepository.deleteById(id);
    }
}
