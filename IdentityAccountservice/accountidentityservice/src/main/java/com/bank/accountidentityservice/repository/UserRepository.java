package com.bank.accountidentityservice.repository;

import com.bank.accountidentityservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByCustomerId(String customerId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByPanNumber(String panNumber);

    @Query("SELECT MAX(u.customerId) FROM User u")
    Optional<String> findMaxCustomerId();
}
