package com.example.docker_compose_rest_spring_boot.controller;

import com.example.docker_compose_rest_spring_boot.entity.Account;
import com.example.docker_compose_rest_spring_boot.service.AccountService;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/{customerId}")
    public Account createAccount(@PathVariable Long customerId,
                                 @RequestParam BigDecimal initialBalance) {
        return accountService.createAccount(customerId, initialBalance);
    }

    @GetMapping("/{accountNumber}")
    public Account getAccount(@PathVariable String accountNumber) {
        return accountService.getAccount(accountNumber);
    }

    @GetMapping
    public List<Account> getAllAccounts() {
        return accountService.getAllAccounts();
    }
}
