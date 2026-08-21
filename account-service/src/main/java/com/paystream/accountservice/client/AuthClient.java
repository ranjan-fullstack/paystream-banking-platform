package com.paystream.accountservice.client;

import com.paystream.accountservice.client.dto.UserBranchInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auth-service", path = "/internal/auth", fallback = AuthClientFallback.class)
public interface AuthClient {

    @GetMapping("/users/{userId}")
    UserBranchInfoResponse getUserBranchInfo(@PathVariable("userId") Long userId);
}
