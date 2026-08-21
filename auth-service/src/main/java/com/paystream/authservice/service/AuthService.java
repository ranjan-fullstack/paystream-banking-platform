package com.paystream.authservice.service;

import com.paystream.authservice.dto.AdminCreateUserRequest;
import com.paystream.authservice.dto.AuthResponse;
import com.paystream.authservice.dto.BranchDetailResponse;
import com.paystream.authservice.dto.BranchManagerSummary;
import com.paystream.authservice.dto.BranchResponse;
import com.paystream.authservice.dto.ChangePasswordRequest;
import com.paystream.authservice.dto.CreateBranchManagerRequest;
import com.paystream.authservice.dto.CreateBranchManagerResponse;
import com.paystream.authservice.dto.CreateBranchRequest;
import com.paystream.authservice.dto.CreateCustomerByBranchRequest;
import com.paystream.authservice.dto.CreateCustomerByBranchResponse;
import com.paystream.authservice.dto.LoginRequest;
import com.paystream.authservice.dto.RefreshTokenRequest;
import com.paystream.authservice.dto.RegisterRequest;
import com.paystream.authservice.dto.UserBranchInfoResponse;
import com.paystream.authservice.entity.Branch;
import com.paystream.authservice.entity.Role;
import com.paystream.authservice.entity.User;
import com.paystream.authservice.exception.AccessDeniedException;
import com.paystream.authservice.exception.BranchNotFoundException;
import com.paystream.authservice.exception.DuplicateBranchException;
import com.paystream.authservice.exception.InvalidCredentialsException;
import com.paystream.authservice.exception.InvalidPasswordException;
import com.paystream.authservice.exception.UserNotFoundException;
import com.paystream.authservice.repository.BranchRepository;
import com.paystream.authservice.repository.UserRepository;
import com.paystream.authservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_USERNAME_RETRIES = 5;

    private final UserRepository userRepo;
    private final BranchRepository branchRepo;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    public void register(RegisterRequest request) {

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .build();

        userRepo.save(user);
    }

    public void registerByAdmin(AdminCreateUserRequest request) {

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();

        userRepo.save(user);
    }


    public AuthResponse login(LoginRequest request) {

        User user = userRepo.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {

        User user = refreshTokenService.validateRefreshToken(request.getRefreshToken());

        String newAccessToken = jwtService.generateToken(user);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(request.getRefreshToken())
                .build();
    }

    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        String jti = jwtService.extractJti(token);
        Date expiration = jwtService.extractExpiration(token);
        long ttlSeconds = Math.max(0, (expiration.getTime() - System.currentTimeMillis()) / 1000);
        redisTemplate.opsForValue().set("blacklist:" + jti, "1", ttlSeconds, TimeUnit.SECONDS);
    }

    public BranchResponse createBranch(CreateBranchRequest request) {
        if (branchRepo.existsByBranchCode(request.getBranchCode())) {
            throw new DuplicateBranchException(request.getBranchCode());
        }
        Branch branch = new Branch();
        branch.setBranchCode(request.getBranchCode());
        branch.setBranchName(request.getBranchName());
        branch.setCity(request.getCity());
        branch.setState(request.getState());
        branch.setBranchPhone(request.getBranchPhone());
        branch = branchRepo.save(branch);

        return BranchResponse.builder()
                .id(branch.getId())
                .branchCode(branch.getBranchCode())
                .branchName(branch.getBranchName())
                .city(branch.getCity())
                .state(branch.getState())
                .branchPhone(branch.getBranchPhone())
                .isActive(branch.isActive())
                .createdAt(branch.getCreatedAt())
                .message("Branch created")
                .build();
    }

    public CreateBranchManagerResponse createBranchManager(CreateBranchManagerRequest request) {
        Branch branch = branchRepo.findByBranchCode(request.getBranchCode())
                .orElseThrow(() -> new BranchNotFoundException(request.getBranchCode()));

        User manager = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.BRANCH_MANAGER)
                .branchCode(branch.getBranchCode())
                .employeeId(request.getEmployeeId())
                .isFirstLogin(false)
                .build();
        userRepo.save(manager);

        return CreateBranchManagerResponse.builder()
                .username(manager.getUsername())
                .role(manager.getRole())
                .branchCode(manager.getBranchCode())
                .employeeId(manager.getEmployeeId())
                .build();
    }

    public CreateCustomerByBranchResponse createCustomerByBranch(Long managerUserId, CreateCustomerByBranchRequest request) {
        User manager = userRepo.findById(managerUserId)
                .orElseThrow(() -> new UserNotFoundException(String.valueOf(managerUserId)));
        if (manager.getRole() != Role.BRANCH_MANAGER) {
            throw new AccessDeniedException("Only a branch manager may create a customer");
        }

        String username = generateUniqueUsername(request.getFirstName());
        String temporaryPassword = generateTemporaryPassword();

        User customer = User.builder()
                .username(username)
                .password(passwordEncoder.encode(temporaryPassword))
                .role(Role.CUSTOMER)
                .isFirstLogin(true)
                .createdByBranch(manager.getBranchCode())
                .createdByManager(manager.getEmployeeId())
                .build();
        customer = userRepo.save(customer);

        return CreateCustomerByBranchResponse.builder()
                .userId(customer.getId())
                .username(username)
                .temporaryPassword(temporaryPassword)
                .message("Customer created — share the temporary password securely; it must be changed on first login")
                .build();
    }

    public void changePassword(Long userId, ChangePasswordRequest request, String authHeader) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(String.valueOf(userId)));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidPasswordException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setFirstLogin(false);
        userRepo.save(user);

        // The password just changed — the JWT the caller used to authenticate this
        // request must not remain valid, so it's blacklisted the same way logout does.
        logout(authHeader);
    }

    public List<BranchResponse> getBranches() {
        return branchRepo.findAll().stream()
                .map(branch -> BranchResponse.builder()
                        .id(branch.getId())
                        .branchCode(branch.getBranchCode())
                        .branchName(branch.getBranchName())
                        .city(branch.getCity())
                        .state(branch.getState())
                        .branchPhone(branch.getBranchPhone())
                        .isActive(branch.isActive())
                        .createdAt(branch.getCreatedAt())
                        .build())
                .toList();
    }

    public BranchDetailResponse getBranchDetail(String branchCode) {
        Branch branch = branchRepo.findByBranchCode(branchCode)
                .orElseThrow(() -> new BranchNotFoundException(branchCode));

        List<BranchManagerSummary> managers = userRepo.findByBranchCodeAndRole(branchCode, Role.BRANCH_MANAGER).stream()
                .map(manager -> BranchManagerSummary.builder()
                        .userId(manager.getId())
                        .username(manager.getUsername())
                        .employeeId(manager.getEmployeeId())
                        .build())
                .toList();

        return BranchDetailResponse.builder()
                .id(branch.getId())
                .branchCode(branch.getBranchCode())
                .branchName(branch.getBranchName())
                .city(branch.getCity())
                .state(branch.getState())
                .branchPhone(branch.getBranchPhone())
                .isActive(branch.isActive())
                .createdAt(branch.getCreatedAt())
                .managers(managers)
                .build();
    }

    public UserBranchInfoResponse getUserBranchInfo(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(String.valueOf(userId)));
        return UserBranchInfoResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .branchCode(user.getBranchCode())
                .employeeId(user.getEmployeeId())
                .build();
    }

    private String generateUniqueUsername(String firstName) {
        String base = firstName.toLowerCase().replaceAll("[^a-z]", "");
        if (base.isEmpty()) {
            base = "customer";
        }
        for (int attempt = 0; attempt < MAX_USERNAME_RETRIES; attempt++) {
            String candidate = base + (1000 + RANDOM.nextInt(9000));
            if (!userRepo.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique username, please retry");
    }

    private String generateTemporaryPassword() {
        return "Temp@" + (1000 + RANDOM.nextInt(9000));
    }
}
