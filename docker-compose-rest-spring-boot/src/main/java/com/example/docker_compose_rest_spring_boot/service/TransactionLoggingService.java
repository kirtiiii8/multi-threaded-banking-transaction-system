
package com.example.docker_compose_rest_spring_boot.service;

import com.example.docker_compose_rest_spring_boot.entity.BankTransaction;
import com.example.docker_compose_rest_spring_boot.entity.TransactionAudit;
import com.example.docker_compose_rest_spring_boot.entity.FailedTransaction;

import com.example.docker_compose_rest_spring_boot.repository.BankTransactionRepository;
import com.example.docker_compose_rest_spring_boot.repository.TransactionAuditRepository;
import com.example.docker_compose_rest_spring_boot.repository.FailedTransactionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransactionLoggingService {

    private final BankTransactionRepository bankTransactionRepository;
    private final TransactionAuditRepository auditRepository;
    private final FailedTransactionRepository failedRepository;

    public TransactionLoggingService(
            BankTransactionRepository bankTransactionRepository,
            TransactionAuditRepository auditRepository,
            FailedTransactionRepository failedRepository) {

        this.bankTransactionRepository = bankTransactionRepository;
        this.auditRepository = auditRepository;
        this.failedRepository = failedRepository;
    }

    @Transactional
    public void logSuccess(
            String transactionId,
            String operation,
            long duration) {

        TransactionAudit audit = new TransactionAudit();

        audit.setTransactionId(transactionId);
        audit.setThreadName(Thread.currentThread().getName());
        audit.setOperation(operation);
        audit.setResult("SUCCESS");
        audit.setDurationMs(duration);

        auditRepository.save(audit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(
            String operation,
            String requestKey,
            BigDecimal amount,
            long duration,
            RuntimeException exception) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "A valid attempted amount is required for failure logging"
            );
        }

        // Generate a unique internal idempotency key for the failed attempt.
        String failureKey = "FAILURE-" + UUID.randomUUID();

        // Create parent transaction first to satisfy the foreign key.
        BankTransaction failedTransaction = new BankTransaction();

        failedTransaction.setTransactionType(operation);
        failedTransaction.setAmount(amount);
        failedTransaction.setStatus("FAILED");
        failedTransaction.setIdempotencyKey(failureKey);

        BankTransaction savedTransaction =
                bankTransactionRepository.saveAndFlush(failedTransaction);

        String failureTransactionId = savedTransaction.getTransactionId();

        String errorCategory = exception.getClass().getSimpleName();

        String reason = exception.getMessage();

        if (reason == null || reason.isBlank()) {
            reason = "Transaction failed";
        }

        if (reason.length() > 500) {
            reason = reason.substring(0, 500);
        }

        // Save failure audit.
        TransactionAudit audit = new TransactionAudit();

        audit.setTransactionId(failureTransactionId);
        audit.setThreadName(Thread.currentThread().getName());
        audit.setOperation(operation);
        audit.setResult("FAILED");
        audit.setDurationMs(duration);
        audit.setErrorCategory(errorCategory);

        auditRepository.save(audit);

        // Save failed transaction details.
        FailedTransaction failed = new FailedTransaction();

        failed.setTransactionId(failureTransactionId);
        failed.setErrorCategory(errorCategory);
        failed.setReason(reason);

        failedRepository.save(failed);
    }
}
