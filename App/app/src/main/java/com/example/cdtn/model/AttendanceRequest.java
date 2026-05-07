package com.example.cdtn.model;

public class AttendanceRequest {

    private String base64;
    private String scheduleId;

    public AttendanceRequest(String base64,
                             String scheduleId) {

        this.base64 = base64;
        this.scheduleId = scheduleId;
    }
}