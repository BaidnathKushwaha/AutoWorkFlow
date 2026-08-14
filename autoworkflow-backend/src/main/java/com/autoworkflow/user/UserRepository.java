package com.autoworkflow.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    @Modifying
    @Query("UPDATE User u SET u.aiRequestsCount = u.aiRequestsCount + 1 WHERE u.id = :userId")
    void incrementAiRequestsCount(@Param("userId") UUID userId);
}

