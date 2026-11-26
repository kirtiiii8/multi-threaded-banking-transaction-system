package com.example.docker_compose_rest_spring_boot;

import com.example.docker_compose_rest_spring_boot.dto.*;
import com.example.docker_compose_rest_spring_boot.entity.*;
import com.example.docker_compose_rest_spring_boot.exception.InsufficientBalanceException;
import com.example.docker_compose_rest_spring_boot.repository.*;
import com.example.docker_compose_rest_spring_boot.service.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class TransactionServiceTests {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BankTransactionRepository transactionRepository;

    @Mock
    private TransactionLoggingService loggingService;

    @InjectMocks
    private TransactionService transactionService;

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setAccountId(1L);
        account.setAccountNumber("TEST1001");
        account.setBalance(new BigDecimal("1000.00"));
        account.setStatus("ACTIVE");

        when(transactionRepository.saveAndFlush(any(BankTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void depositShouldIncreaseBalance() {
        when(transactionRepository.findByIdempotencyKey("deposit-001"))
                .thenReturn(Optional.empty());
        when(accountRepository.findByAccountNumberWithLock("TEST1001"))
                .thenReturn(Optional.of(account));

        DepositRequest request = new DepositRequest();
        request.setAccountNumber("TEST1001");
        request.setAmount(new BigDecimal("200.00"));
        request.setIdempotencyKey("deposit-001");

        BankTransaction result = transactionService.deposit(request);

        assertEquals(new BigDecimal("1200.00"), account.getBalance());
        assertEquals("DEPOSIT", result.getTransactionType());
        assertEquals("SUCCESS", result.getStatus());
        verify(loggingService).logSuccess(
                any(), eq("DEPOSIT"), anyLong());
    }

    @Test
    void withdrawalShouldDecreaseBalance() {
        when(transactionRepository.findByIdempotencyKey("withdraw-001"))
                .thenReturn(Optional.empty());
        when(accountRepository.findByAccountNumberWithLock("TEST1001"))
                .thenReturn(Optional.of(account));

        WithdrawRequest request = new WithdrawRequest();
        request.setAccountNumber("TEST1001");
        request.setAmount(new BigDecimal("300.00"));
        request.setIdempotencyKey("withdraw-001");

        BankTransaction result = transactionService.withdraw(request);

        assertEquals(new BigDecimal("700.00"), account.getBalance());
        assertEquals("WITHDRAWAL", result.getTransactionType());
        assertEquals("SUCCESS", result.getStatus());
    }

    @Test
    void withdrawalShouldRejectInsufficientBalance() {
        when(transactionRepository.findByIdempotencyKey("withdraw-002"))
                .thenReturn(Optional.empty());
        when(accountRepository.findByAccountNumberWithLock("TEST1001"))
                .thenReturn(Optional.of(account));

        WithdrawRequest request = new WithdrawRequest();
        request.setAccountNumber("TEST1001");
        request.setAmount(new BigDecimal("5000.00"));
        request.setIdempotencyKey("withdraw-002");

        assertThrows(
                InsufficientBalanceException.class,
                () -> transactionService.withdraw(request)
        );

        assertEquals(new BigDecimal("1000.00"), account.getBalance());
        verify(loggingService).logFailure(
                eq("WITHDRAWAL"), eq("withdraw-002"),
                anyLong(), any(RuntimeException.class));
        verify(transactionRepository, never())
                .saveAndFlush(any(BankTransaction.class));
    }

    @Test
    void duplicateIdempotencyKeyShouldBeRejected() {
        when(transactionRepository.findByIdempotencyKey("duplicate-001"))
                .thenReturn(Optional.of(new BankTransaction()));

        DepositRequest request = new DepositRequest();
        request.setAccountNumber("TEST1001");
        request.setAmount(new BigDecimal("100.00"));
        request.setIdempotencyKey("duplicate-001");

        assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.deposit(request)
        );

        assertEquals(new BigDecimal("1000.00"), account.getBalance());
        verify(accountRepository, never())
                .findByAccountNumberWithLock(any());
    }

    @Test
    void transferShouldRejectMissingSourceAccount() {
        TransferRequest request = new TransferRequest();
        request.setSourceAccountNumber(null);
        request.setDestinationAccountNumber("TEST2002");
        request.setAmount(new BigDecimal("100.00"));
        request.setIdempotencyKey("transfer-001");

        assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.transfer(request)
        );

        verify(accountRepository, never())
                .findByAccountNumberWithLock(any());
    }

    @Test
    void transferShouldRejectSameSourceAndDestination() {
        TransferRequest request = new TransferRequest();
        request.setSourceAccountNumber("TEST1001");
        request.setDestinationAccountNumber("TEST1001");
        request.setAmount(new BigDecimal("100.00"));
        request.setIdempotencyKey("transfer-002");

        when(transactionRepository.findByIdempotencyKey("transfer-002"))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.transfer(request)
        );

        verify(accountRepository, never())
                .findByAccountNumberWithLock(any());
    }
}
