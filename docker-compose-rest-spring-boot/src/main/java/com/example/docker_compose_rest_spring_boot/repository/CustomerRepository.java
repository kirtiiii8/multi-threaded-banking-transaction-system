
package com.example.docker_compose_rest_spring_boot.repository;

import com.example.docker_compose_rest_spring_boot.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByPhone(String phone);
}
