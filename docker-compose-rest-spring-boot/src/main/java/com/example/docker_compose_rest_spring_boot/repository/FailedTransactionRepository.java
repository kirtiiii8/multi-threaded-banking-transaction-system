package com.example.docker_compose_rest_spring_boot.repository;

import com.example.docker_compose_rest_spring_boot.entity.FailedTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailedTransactionRepository
        extends JpaRepository<FailedTransaction, Long> {
}
