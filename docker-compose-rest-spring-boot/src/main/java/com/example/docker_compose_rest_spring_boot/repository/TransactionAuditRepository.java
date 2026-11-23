package com.example.docker_compose_rest_spring_boot.repository;

import com.example.docker_compose_rest_spring_boot.entity.TransactionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionAuditRepository
        extends JpaRepository<TransactionAudit, Long> {
}
