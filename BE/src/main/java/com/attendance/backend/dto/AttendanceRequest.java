package com.attendance.backend.dto;

public class AttendanceRequest {
    private String base64;
    private String scheduleId;

    public AttendanceRequest() {
    }

    public AttendanceRequest(String base64, String scheduleId) {
        this.base64 = base64;
        this.scheduleId = scheduleId;
    }

    public String getBase64() {
        return base64;
    }

    public void setBase64(String base64) {
        this.base64 = base64;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(String scheduleId) {
        this.scheduleId = scheduleId;
    }
}
