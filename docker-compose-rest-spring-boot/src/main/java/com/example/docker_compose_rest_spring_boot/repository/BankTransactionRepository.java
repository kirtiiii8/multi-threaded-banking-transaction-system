
package com.example.docker_compose_rest_spring_boot.repository;

import com.example.docker_compose_rest_spring_boot.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankTransactionRepository
        extends JpaRepository<BankTransaction, Long> {

    Optional<BankTransaction> findByIdempotencyKey(String idempotencyKey);

    List<BankTransaction> findBySourceAccountAccountIdOrDestinationAccountAccountId(
            Long sourceId, Long destinationId);
}
