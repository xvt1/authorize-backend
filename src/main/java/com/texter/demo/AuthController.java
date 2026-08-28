package com.texter.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "*")
public class AuthController {

    private static final long LAST_SEEN_UPDATE_THRESHOLD_SECONDS = 20;

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final TelegramNotificationService telegramNotificationService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository,
                          JwtService jwtService,
                          TelegramNotificationService telegramNotificationService,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.telegramNotificationService = telegramNotificationService;
        this.passwordEncoder = passwordEncoder;
    }

    private void touchLastSeenIfNeeded(User user) {
        Instant now = Instant.now();
        Instant last = user.getLastSeenAt();
        if (last == null || last.isBefore(now.minusSeconds(LAST_SEEN_UPDATE_THRESHOLD_SECONDS))) {
            userRepository.touchLastSeenAt(user.getUsername(), now);
        }
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody LoginRequest req) {

        if (req.getUsername() == null || req.getUsername().isBlank()) {
            return ResponseEntity.status(400).body("Логін обов'язковий");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            return ResponseEntity.status(400).body("Пароль обов'язковий");
        }

        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            return ResponseEntity.status(400).body("Такий користувач вже існує");
        }

        String nick = (req.getNickname() != null && !req.getNickname().isBlank())
                ? req.getNickname().trim()
                : req.getUsername().trim();

        // email: якщо фронт не передає — ставимо placeholder, щоб NOT NULL не падав
        String email = (req.getEmail() != null && !req.getEmail().isBlank())
                ? req.getEmail().trim()
                : req.getUsername().trim() + "@local";

        User newUser = new User();
        newUser.setUsername(req.getUsername().trim());
        newUser.setPassword(passwordEncoder.encode(req.getPassword()));
        newUser.setNickname(nick);
        newUser.setEmail(email);
        // role за замовчуванням PENDING
        newUser.setLastSeenAt(Instant.now());
        userRepository.save(newUser);

        telegramNotificationService.notifyAdminAboutNewUser(newUser);

        String token = jwtService.generateToken(newUser.getUsername());
        return ResponseEntity.ok(token);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        Optional<User> existing = userRepository.findByUsername(req.getUsername());

        if (existing.isEmpty()) {
            return ResponseEntity.status(401).body("Користувача не знайдено");
        }

        User user = existing.get();
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body("Невірний пароль");
        }

        if (user.getRole() == Role.BLOCKED) {
            return ResponseEntity.status(403).body("blocked");
        }

        // PENDING теж отримує токен — на сайті /auth/me покаже «очікуйте»
        touchLastSeenIfNeeded(user);

        String token = jwtService.generateToken(user.getUsername());
        return ResponseEntity.ok(token);
    }

    @PostMapping("/auth/heartbeat")
    public ResponseEntity<?> heartbeat(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("status", "error", "message", "username required"));
        }

        return userRepository.findByUsername(username)
                .<ResponseEntity<?>>map(user -> {
                    if (user.getRole() == Role.BLOCKED) {
                        return ResponseEntity.status(403).body(Map.of("status", "blocked"));
                    }
                    if (user.getRole() == Role.PENDING) {
                        return ResponseEntity.status(403).body(Map.of("status", "pending"));
                    }
                    touchLastSeenIfNeeded(user);
                    return ResponseEntity.ok(Map.of("status", "ok"));
                })
                .orElse(ResponseEntity.status(404).body(Map.of("status", "not_found")));
    }

    @GetMapping("/auth/me")
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("status", "unauthorized", "message", "Немає токена"));
        }

        String token = authHeader.substring(7);
        if (!jwtService.isValid(token)) {
            return ResponseEntity.status(401).body(Map.of("status", "unauthorized", "message", "Токен недійсний"));
        }

        String username = jwtService.extractUsername(token);

        return userRepository.findByUsername(username)
                .<ResponseEntity<?>>map(user -> {
                    if (user.getRole() == Role.BLOCKED) {
                        return ResponseEntity.status(403).body(Map.of(
                                "status", "blocked",
                                "message", "blocked"
                        ));
                    }
                    if (user.getRole() == Role.PENDING) {
                        return ResponseEntity.status(403).body(Map.of(
                                "status", "pending",
                                "message", "Очікуйте підтвердження адміністратора"
                        ));
                    }

                    touchLastSeenIfNeeded(user);

                    return ResponseEntity.ok(Map.of(
                            "status", "ok",
                            "username", user.getUsername(),
                            "nickname", user.getNickname() != null ? user.getNickname() : user.getUsername(),
                            "role", user.getRole().name()
                    ));
                })
                .orElse(ResponseEntity.status(404).body(Map.of("status", "not_found", "message", "Користувача не знайдено")));
    }
}