package com.doova.doovafeeds.dto;


import lombok.Data;

@Data
public class ApiResponse {
    private Object data;
    private String timestamp;
    private String message;

    public ApiResponse(Object data, String message) {
        this.data = data;
        this.message = message;
        this.timestamp = java.time.LocalDateTime.now().toString();  // add current timestamp
    }


}
