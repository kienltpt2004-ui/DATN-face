package com.attendance.backend.dto;

public class StudentAttendanceResponse {
    private String className;
    private String subjectName;
    private String attendanceTime;

    public StudentAttendanceResponse() {}

    public StudentAttendanceResponse(String className, String subjectName, String attendanceTime) {
        this.className = className;
        this.subjectName = subjectName;
        this.attendanceTime = attendanceTime;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public String getAttendanceTime() {
        return attendanceTime;
    }

    public void setAttendanceTime(String attendanceTime) {
        this.attendanceTime = attendanceTime;
    }
}
