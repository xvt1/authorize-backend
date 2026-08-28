package com.texter.demo;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;
import java.time.Instant;

@Entity
@Table(name = "users")
@DynamicUpdate // Hibernate буде включати в UPDATE тільки реально змінені колонки,
// а не всі (password/email/role тощо) при кожному save()
public class User {

    private static final long ONLINE_THRESHOLD_MINUTES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, nullable = false)
    private String email;

    /** Відображуване ім'я (нікнейм) */
    @Column(name = "nickname")
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.PENDING;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public boolean isOnline() {
        if (lastSeenAt == null) return false;
        return lastSeenAt.isAfter(Instant.now().minusSeconds(ONLINE_THRESHOLD_MINUTES * 60));
    }

    /** Для відображення: нікнейм, якщо є, інакше username */
    public String getDisplayName() {
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        return username;
    }
}