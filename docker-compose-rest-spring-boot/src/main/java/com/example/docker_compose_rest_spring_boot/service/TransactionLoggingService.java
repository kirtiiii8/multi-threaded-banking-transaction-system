package com.example.docker_compose_rest_spring_boot.service;

import com.example.docker_compose_rest_spring_boot.entity.TransactionAudit;
import com.example.docker_compose_rest_spring_boot.entity.FailedTransaction;
import com.example.docker_compose_rest_spring_boot.repository.TransactionAuditRepository;
import com.example.docker_compose_rest_spring_boot.repository.FailedTransactionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.UUID;

@Service
public class TransactionLoggingService {

    private final TransactionAuditRepository auditRepository;
    private final FailedTransactionRepository failedRepository;

    public TransactionLoggingService(
            TransactionAuditRepository auditRepository,
            FailedTransactionRepository failedRepository) {
        this.auditRepository = auditRepository;
        this.failedRepository = failedRepository;
    }

    @Transactional
    public void logSuccess(String transactionId, String operation, long duration) {

        TransactionAudit audit = new TransactionAudit();
        audit.setTransactionId(transactionId);
        audit.setThreadName(Thread.currentThread().getName());
        audit.setOperation(operation);
        audit.setResult("SUCCESS");
        audit.setDurationMs(duration);

        auditRepository.save(audit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(String operation, String requestKey,
                           long duration, RuntimeException exception) {

        String failureId = UUID.randomUUID().toString();

        String reason = exception.getMessage() == null
                ? "Transaction failed"
                : exception.getMessage();

        if (requestKey != null && !requestKey.isBlank()) {
            reason = "Request key: " + requestKey + " | " + reason;
        }

        if (reason.length() > 500) {
            reason = reason.substring(0, 500);
        }

        TransactionAudit audit = new TransactionAudit();
        audit.setTransactionId(failureId);
        audit.setThreadName(Thread.currentThread().getName());
        audit.setOperation(operation);
        audit.setResult("FAILED");
        audit.setDurationMs(duration);
        audit.setErrorCategory(exception.getClass().getSimpleName());

        auditRepository.save(audit);

        FailedTransaction failed = new FailedTransaction();
        failed.setTransactionId(failureId);
        failed.setErrorCategory(exception.getClass().getSimpleName());
        failed.setReason(reason);

        failedRepository.save(failed);
    }
}
