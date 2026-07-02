package com.paystream.impsservice.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class ApiError {
    private final Instant timestamp;
    private final int status;
    private final String error;
    private final String message;
}
