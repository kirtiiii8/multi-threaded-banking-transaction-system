package com.example.docker_compose_rest_spring_boot.service;

import com.example.docker_compose_rest_spring_boot.dto.*;
import com.example.docker_compose_rest_spring_boot.entity.Account;
import com.example.docker_compose_rest_spring_boot.entity.BankTransaction;
import com.example.docker_compose_rest_spring_boot.exception.InsufficientBalanceException;
import com.example.docker_compose_rest_spring_boot.exception.ResourceNotFoundException;
import com.example.docker_compose_rest_spring_boot.repository.AccountRepository;
import com.example.docker_compose_rest_spring_boot.repository.BankTransactionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final TransactionLoggingService loggingService;

    public TransactionService(
            AccountRepository accountRepository,
            BankTransactionRepository transactionRepository,
            TransactionLoggingService loggingService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.loggingService = loggingService;
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }

        if (transactionRepository.findByIdempotencyKey(key).isPresent()) {
            throw new IllegalArgumentException("Duplicate transaction request");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private Account getLockedActiveAccount(String accountNumber) {
        Account account = accountRepository
                .findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Account not found: " + accountNumber));

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new IllegalArgumentException(
                    "Account is not active: " + accountNumber);
        }

        return account;
    }

    private void recordFailure(
            String operation, String key, long startTime,
            RuntimeException exception) {

        try {
            loggingService.logFailure(
                    operation,
                    key,
                    (System.nanoTime() - startTime) / 1_000_000,
                    exception
            );
        } catch (RuntimeException loggingException) {
            exception.addSuppressed(loggingException);
        }
    }

    @Transactional
    public BankTransaction deposit(DepositRequest request) {
        long startTime = System.nanoTime();

        try {
            validateAmount(request.getAmount());
            validateIdempotencyKey(request.getIdempotencyKey());

            Account account = getLockedActiveAccount(
                    request.getAccountNumber());

            account.setBalance(
                    account.getBalance().add(request.getAmount()));

            accountRepository.save(account);

            BankTransaction transaction = new BankTransaction();
            transaction.setTransactionType("DEPOSIT");
            transaction.setAmount(request.getAmount());
            transaction.setDestinationAccount(account);
            transaction.setStatus("SUCCESS");
            transaction.setIdempotencyKey(request.getIdempotencyKey());

            BankTransaction saved = transactionRepository.save(transaction);

            loggingService.logSuccess(
                    request.getIdempotencyKey(),
                    "DEPOSIT",
                    (System.nanoTime() - startTime) / 1_000_000
            );

            return saved;

        } catch (RuntimeException exception) {
            recordFailure(
                    "DEPOSIT",
                    request == null ? null : request.getIdempotencyKey(),
                    startTime,
                    exception
            );
            throw exception;
        }
    }

    @Transactional
    public BankTransaction withdraw(WithdrawRequest request) {
        long startTime = System.nanoTime();

        try {
            validateAmount(request.getAmount());
            validateIdempotencyKey(request.getIdempotencyKey());

            Account account = getLockedActiveAccount(
                    request.getAccountNumber());

            if (account.getBalance().compareTo(request.getAmount()) < 0) {
                throw new InsufficientBalanceException(
                        "Insufficient account balance");
            }

            account.setBalance(
                    account.getBalance().subtract(request.getAmount()));

            accountRepository.save(account);

            BankTransaction transaction = new BankTransaction();
            transaction.setTransactionType("WITHDRAWAL");
            transaction.setAmount(request.getAmount());
            transaction.setSourceAccount(account);
            transaction.setStatus("SUCCESS");
            transaction.setIdempotencyKey(request.getIdempotencyKey());

            BankTransaction saved = transactionRepository.save(transaction);

            loggingService.logSuccess(
                    request.getIdempotencyKey(),
                    "WITHDRAWAL",
                    (System.nanoTime() - startTime) / 1_000_000
            );

            return saved;

        } catch (RuntimeException exception) {
            recordFailure(
                    "WITHDRAWAL",
                    request == null ? null : request.getIdempotencyKey(),
                    startTime,
                    exception
            );
            throw exception;
        }
    }

    @Transactional
    public BankTransaction transfer(TransferRequest request) {
        long startTime = System.nanoTime();

        try {
            validateAmount(request.getAmount());
            validateIdempotencyKey(request.getIdempotencyKey());

            if (request.getSourceAccountNumber()
                    .equals(request.getDestinationAccountNumber())) {
                throw new IllegalArgumentException(
                        "Source and destination must be different");
            }

            String firstNumber =
                    request.getSourceAccountNumber()
                            .compareTo(request.getDestinationAccountNumber()) < 0
                            ? request.getSourceAccountNumber()
                            : request.getDestinationAccountNumber();

            String secondNumber =
                    firstNumber.equals(request.getSourceAccountNumber())
                            ? request.getDestinationAccountNumber()
                            : request.getSourceAccountNumber();

            Account first = getLockedActiveAccount(firstNumber);
            Account second = getLockedActiveAccount(secondNumber);

            Account source = first.getAccountNumber()
                    .equals(request.getSourceAccountNumber())
                    ? first : second;

            Account destination = first.getAccountNumber()
                    .equals(request.getDestinationAccountNumber())
                    ? first : second;

            if (source.getBalance().compareTo(request.getAmount()) < 0) {
                throw new InsufficientBalanceException(
                        "Insufficient account balance");
            }

            source.setBalance(
                    source.getBalance().subtract(request.getAmount()));

            destination.setBalance(
                    destination.getBalance().add(request.getAmount()));

            accountRepository.save(source);
            accountRepository.save(destination);

            BankTransaction transaction = new BankTransaction();
            transaction.setTransactionType("TRANSFER");
            transaction.setAmount(request.getAmount());
            transaction.setSourceAccount(source);
            transaction.setDestinationAccount(destination);
            transaction.setStatus("SUCCESS");
            transaction.setIdempotencyKey(request.getIdempotencyKey());

            BankTransaction saved = transactionRepository.save(transaction);

            loggingService.logSuccess(
                    request.getIdempotencyKey(),
                    "TRANSFER",
                    (System.nanoTime() - startTime) / 1_000_000
            );

            return saved;

        } catch (RuntimeException exception) {
            recordFailure(
                    "TRANSFER",
                    request == null ? null : request.getIdempotencyKey(),
                    startTime,
                    exception
            );
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<BankTransaction> getTransactionHistory(Long accountId) {

        if (!accountRepository.existsById(accountId)) {
            throw new ResourceNotFoundException(
                    "Account not found: " + accountId);
        }

        return transactionRepository
                .findBySourceAccountAccountIdOrDestinationAccountAccountId(
                        accountId, accountId);
    }
}
