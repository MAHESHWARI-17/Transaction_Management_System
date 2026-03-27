package com.bank.accountidentityservice.repository;

import com.bank.accountidentityservice.entity.OtpStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OtpStoreRepository extends JpaRepository<OtpStore, UUID> {

    @Query("SELECT o FROM OtpStore o WHERE o.customerId = :customerId " +
           "AND o.purpose = :purpose AND o.usedAt IS NULL " +
           "AND o.expiresAt > CURRENT_TIMESTAMP ORDER BY o.createdAt DESC")
    List<OtpStore> findValidOtps(String customerId, OtpStore.OtpPurpose purpose);

    @Modifying
    @Query("UPDATE OtpStore o SET o.usedAt = CURRENT_TIMESTAMP " +
           "WHERE o.customerId = :customerId AND o.purpose = :purpose AND o.usedAt IS NULL")
    void invalidateAll(String customerId, OtpStore.OtpPurpose purpose);
}
