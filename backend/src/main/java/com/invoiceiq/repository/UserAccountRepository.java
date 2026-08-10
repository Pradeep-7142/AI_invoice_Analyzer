package com.invoiceiq.repository;

import com.invoiceiq.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
