package com.texter.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    /**
     * Пряме оновлення тільки колонки last_seen_at, без SELECT усього рядка
     * і без перезапису password/email/role. Саме це прибирає зайві
     * "select ... update ..." пари з логів Hibernate при частих
     * зверненнях (/auth/me, /auth/heartbeat).
     */
    @Modifying
    @Transactional
    @Query("update User u set u.lastSeenAt = :now where u.username = :username")
    int touchLastSeenAt(String username, Instant now);
}