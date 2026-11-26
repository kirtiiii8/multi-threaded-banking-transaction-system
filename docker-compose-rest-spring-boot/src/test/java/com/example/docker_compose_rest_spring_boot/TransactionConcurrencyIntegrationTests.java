package com.example.docker_compose_rest_spring_boot;

import com.example.docker_compose_rest_spring_boot.dto.DepositRequest;
import com.example.docker_compose_rest_spring_boot.entity.Account;
import com.example.docker_compose_rest_spring_boot.entity.Customer;
import com.example.docker_compose_rest_spring_boot.entity.BankTransaction;
import com.example.docker_compose_rest_spring_boot.repository.AccountRepository;
import com.example.docker_compose_rest_spring_boot.repository.BankTransactionRepository;
import com.example.docker_compose_rest_spring_boot.repository.CustomerRepository;
import com.example.docker_compose_rest_spring_boot.service.TransactionService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class TransactionConcurrencyIntegrationTests {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BankTransactionRepository transactionRepository;

    @Test
    void shouldHandleConcurrentDepositsWithoutLosingUpdates() throws Exception {

        Customer customer = null;
        Account account = null;
        ExecutorService executor = Executors.newFixedThreadPool(10);

        try {
            customer = new Customer();
            customer.setName("Concurrency Test");
            customer.setEmail("concurrency_" + System.nanoTime() + "@test.com");
            customer.setPhone(String.valueOf(System.nanoTime()).substring(0, 10));
            customer = customerRepository.saveAndFlush(customer);

            account = new Account();
            account.setAccountNumber("CONC" + System.nanoTime());
            account.setBalance(new BigDecimal("100.00"));
            account.setStatus("ACTIVE");
            account.setCustomer(customer);
            account = accountRepository.saveAndFlush(account);

            final String accountNumber = account.getAccountNumber();

            int threadCount = 10;
            int depositsPerThread = 10;

            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);

            List<Future<?>> futures = new java.util.ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int threadNumber = i;

                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();

                    for (int j = 0; j < depositsPerThread; j++) {
                        DepositRequest request = new DepositRequest();
                        request.setAccountNumber(accountNumber);
                        request.setAmount(new BigDecimal("1.00"));
                        request.setIdempotencyKey(
                            "CONC-" + threadNumber + "-" + j + "-" + System.nanoTime()
                        );

                        transactionService.deposit(request);
                    }
                    return null;
                }));
            }

            if (!ready.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Threads did not become ready in time");
            }

            start.countDown();

            for (Future<?> future : futures) {
                future.get(90, TimeUnit.SECONDS);
            }

            Account updatedAccount = accountRepository
                .findById(account.getAccountId())
                .orElseThrow();

            BigDecimal expectedBalance = new BigDecimal("200.00");

            assertEquals(
                0,
                expectedBalance.compareTo(updatedAccount.getBalance()),
                "Balance mismatch: concurrent deposits may have lost updates"
            );

            System.out.println("CONCURRENCY TEST PASSED");
            System.out.println("Threads: " + threadCount);
            System.out.println("Total deposits: " + (threadCount * depositsPerThread));
            System.out.println("Final balance: " + updatedAccount.getBalance());
        } finally {
            executor.shutdownNow();

            if (account != null) {
                List<BankTransaction> transactions =
                    transactionRepository
                        .findBySourceAccountAccountIdOrDestinationAccountAccountId(
                            account.getAccountId(),
                            account.getAccountId()
                        );

                transactionRepository.deleteAll(transactions);
                accountRepository.delete(account);
            }

            if (customer != null) {
                customerRepository.delete(customer);
            }
        }
    }
}
