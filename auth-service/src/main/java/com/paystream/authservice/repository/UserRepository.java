package com.paystream.authservice.repository;

import com.paystream.authservice.entity.Role;
import com.paystream.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    List<User> findByBranchCodeAndRole(String branchCode, Role role);
}
