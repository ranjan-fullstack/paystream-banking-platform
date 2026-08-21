package com.paystream.authservice.entity;

import com.paystream.authservice.entity.Role;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String username;

    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    // Assigned branch for BRANCH_MANAGER / TELLER staff — null for CUSTOMER/ADMIN.
    private String branchCode;

    // Staff identifier for BRANCH_MANAGER / TELLER — null for CUSTOMER/ADMIN.
    private String employeeId;

    @Builder.Default
    private boolean isFirstLogin = false;

    // Which branch created this CUSTOMER, if any.
    private String createdByBranch;

    // Which branch manager (employeeId) created this CUSTOMER, if any.
    private String createdByManager;
}
