package com.flipkartclone.productservice.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@RefreshScope
public class TestRefreshController {

    @Value("${test.message}")
    private String message;

    @GetMapping("/message")
    public String getMessage() {
        return message;
    }
}
