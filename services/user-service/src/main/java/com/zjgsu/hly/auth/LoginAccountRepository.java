package com.zjgsu.hly.auth;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LoginAccountRepository extends JpaRepository<LoginAccount,Long> {
    Optional<LoginAccount> findByUsername(String username);
}
