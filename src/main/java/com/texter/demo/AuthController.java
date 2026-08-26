package com.texter.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@RestController
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final Map<String, String> verificationCodes = new HashMap<>();
    private final JwtService jwtService;
    private final TelegramNotificationService telegramNotificationService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, EmailService emailService, JwtService jwtService, TelegramNotificationService telegramNotificationService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.telegramNotificationService = telegramNotificationService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody LoginRequest req) {

        if (req.getUsername() == null || req.getUsername().isBlank()) {
            return ResponseEntity.status(400).body("Логін обов'язковий");
        }
        if (req.getEmail() == null || req.getEmail().isBlank()) {
            return ResponseEntity.status(400).body("Email обов'язковий");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            return ResponseEntity.status(400).body("Пароль обов'язковий");
        }

        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            return ResponseEntity.status(400).body("Такий користувач вже існує");
        }

        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            return ResponseEntity.status(400).body("Цей email вже використовується");
        }

        String code = String.valueOf(new Random().nextInt(900000) + 100000);
        verificationCodes.put(req.getEmail(), code);
        verificationCodes.put(req.getEmail() + "_username", req.getUsername());
        verificationCodes.put(req.getEmail() + "_password", passwordEncoder.encode(req.getPassword()));
        String nick = (req.getNickname() != null && !req.getNickname().isBlank())
                ? req.getNickname().trim()
                : req.getUsername().trim();
        verificationCodes.put(req.getEmail() + "_nickname", nick);

        emailService.sendVerificationCode(req.getEmail(), code);

        return ResponseEntity.ok("Код відправлено на email");
    }

    @PostMapping("/auth/verify")
    public ResponseEntity<?> verify(@RequestBody VerifyRequest req) {

        String savedCode = verificationCodes.get(req.getEmail());

        if (savedCode == null || !savedCode.equals(req.getCode())) {
            return ResponseEntity.status(400).body("Невірний код");
        }

        User newUser = new User();
        newUser.setEmail(req.getEmail());
        newUser.setUsername(verificationCodes.get(req.getEmail() + "_username"));
        newUser.setPassword(verificationCodes.get(req.getEmail() + "_password"));
        newUser.setNickname(verificationCodes.get(req.getEmail() + "_nickname"));
        // role = PENDING — користувач НЕ може зайти, поки адмін не прийме
        userRepository.save(newUser);

        verificationCodes.remove(req.getEmail());
        verificationCodes.remove(req.getEmail() + "_username");
        verificationCodes.remove(req.getEmail() + "_password");
        verificationCodes.remove(req.getEmail() + "_nickname");

        telegramNotificationService.notifyAdminAboutNewUser(newUser);

        // Видаємо токен одразу: користувач «увійшов», на сайті побачить
        // «Очікуйте підтвердження» поки адмін не прийме
        newUser.setLastSeenAt(Instant.now());
        userRepository.save(newUser);
        String token = jwtService.generateToken(newUser.getUsername());
        return ResponseEntity.ok(token);
    }

    @PostMapping("/auth/register/resend")
    public ResponseEntity<?> resend(@RequestBody VerifyRequest req) {
        if (!verificationCodes.containsKey(req.getEmail())) {
            return ResponseEntity.status(400).body("Спочатку зареєструйся");
        }

        String code = String.valueOf(new Random().nextInt(900000) + 100000);
        verificationCodes.put(req.getEmail(), code);
        emailService.sendVerificationCode(req.getEmail(), code);

        return ResponseEntity.ok("Код відправлено знову");
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

        // заблокованим — не даємо увійти
        if (user.getRole() == Role.BLOCKED) {
            return ResponseEntity.status(403).body("blocked");
        }

        // PENDING теж отримує токен: на сайті покажеться «очікуйте»,
        // поки адмін не прийме (перевірка через /auth/me)
        user.setLastSeenAt(Instant.now());
        userRepository.save(user);

        String token = jwtService.generateToken(user.getUsername());
        return ResponseEntity.ok(token);
    }

    @PostMapping("/auth/heartbeat")
    public ResponseEntity<?> heartbeat(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body("username обов'язковий");
        }

        return userRepository.findByUsername(username)
                .<ResponseEntity<?>>map(user -> {
                    if (user.getRole() == Role.BLOCKED) {
                        return ResponseEntity.status(403).body(Map.of("status", "blocked"));
                    }
                    if (user.getRole() == Role.PENDING) {
                        return ResponseEntity.status(403).body(Map.of("status", "pending"));
                    }
                    user.setLastSeenAt(Instant.now());
                    userRepository.save(user);
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

                    user.setLastSeenAt(Instant.now());
                    userRepository.save(user);

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
