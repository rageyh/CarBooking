package me.enrico.carbooking.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CarBookingRequest {
    private String name;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String reason;

}
