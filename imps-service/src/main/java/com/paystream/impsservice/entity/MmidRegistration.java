package com.paystream.impsservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "mmid_registrations")
@Getter
@Setter
public class MmidRegistration {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false, unique = true)
    private String mobileNumber;

    @Column(nullable = false, unique = true, length = 7)
    private String mmid;

    @Column(nullable = false)
    private boolean active = true;
}
