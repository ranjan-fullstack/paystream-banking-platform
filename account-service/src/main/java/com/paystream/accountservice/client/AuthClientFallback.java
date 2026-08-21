package com.paystream.accountservice.client;

import com.paystream.accountservice.client.dto.UserBranchInfoResponse;
import com.paystream.accountservice.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuthClientFallback implements AuthClient {

    @Override
    public UserBranchInfoResponse getUserBranchInfo(Long userId) {
        log.error("Circuit breaker triggered for auth-service getUserBranchInfo({})", userId);
        throw new ServiceUnavailableException("Auth service unavailable — cannot resolve branch for user " + userId);
    }
}
