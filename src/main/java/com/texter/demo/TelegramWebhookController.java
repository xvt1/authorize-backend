// TelegramWebhookController.java
package com.texter.demo;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@RestController
public class TelegramWebhookController {

    private final UserRepository userRepository;
    private final TelegramNotificationService telegramNotificationService;

    @Value("${telegram.webhook.secret}")
    private String webhookSecret;

    public TelegramWebhookController(UserRepository userRepository,
                                      TelegramNotificationService telegramNotificationService) {
        this.userRepository = userRepository;
        this.telegramNotificationService = telegramNotificationService;
    }

    @PostMapping("/telegram/webhook")
    public ResponseEntity<?> handleUpdate(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretHeader,
            @RequestBody Map<String, Object> update) {

        if (!webhookSecret.equals(secretHeader)) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        Map<String, Object> message = (Map<String, Object>) update.get("message");
        if (message != null) {
            handleMessage(message);
            return ResponseEntity.ok().build();
        }

        Map<String, Object> callbackQuery = (Map<String, Object>) update.get("callback_query");
        if (callbackQuery != null) {
            handleCallback(callbackQuery);
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.ok().build();
    }

    private void handleMessage(Map<String, Object> message) {
        Map<String, Object> chat = (Map<String, Object>) message.get("chat");
        if (chat == null) return;

        Long chatId = Long.valueOf(String.valueOf(chat.get("id")));
        if (!isAdmin(chatId)) return;

        String text = String.valueOf(message.getOrDefault("text", "")).trim();

        switch (text) {
            case "/help", "/start" -> telegramNotificationService.sendHelp(chatId);
            case "/list" -> telegramNotificationService.sendUserList(chatId, userRepository.findAll());
            default -> { /* невідома команда */ }
        }
    }

    private void handleCallback(Map<String, Object> callbackQuery) {
        String callbackId = (String) callbackQuery.get("id");
        String data = (String) callbackQuery.get("data");

        Map<String, Object> messageObj = (Map<String, Object>) callbackQuery.get("message");
        Long chatId = null;
        Integer messageId = null;
        if (messageObj != null) {
            messageId = (Integer) messageObj.get("message_id");
            Map<String, Object> chat = (Map<String, Object>) messageObj.get("chat");
            if (chat != null) {
                chatId = Long.valueOf(String.valueOf(chat.get("id")));
            }
        }

        if (chatId == null || !isAdmin(chatId)) {
            telegramNotificationService.answerCallback(callbackId);
            return;
        }

        if ("back_to_list".equals(data)) {
            telegramNotificationService.backToList(chatId, messageId, userRepository.findAll());
            telegramNotificationService.answerCallback(callbackId);
            return;
        }

        if (data == null || !data.contains(":")) {
            telegramNotificationService.answerCallback(callbackId);
            return;
        }

        String[] parts = data.split(":", 2);
        String action = parts[0];
        Long userId;
        try {
            userId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            telegramNotificationService.answerCallback(callbackId);
            return;
        }

        Integer finalMessageId = messageId;
        Long finalChatId = chatId;

        if ("view".equals(action)) {
            userRepository.findById(userId).ifPresent(user ->
                    telegramNotificationService.showUserDetails(finalChatId, finalMessageId, user));
        } else if ("accept".equals(action)) {
            // Прийняти → показати вибір ролі (роль ще не змінюємо)
            userRepository.findById(userId).ifPresent(user ->
                    telegramNotificationService.showRoleSelection(finalChatId, finalMessageId, user));
        } else {
            Role newRole = switch (action) {
                case "grant_view" -> Role.VIEWER;
                case "grant_edit" -> Role.EDITOR;
                case "grant_admin" -> Role.ADMIN;
                case "block" -> Role.BLOCKED;
                case "reject" -> Role.BLOCKED; // відхилити = заблокувати / не пускати
                default -> null;
            };

            if (newRole != null) {
                userRepository.findById(userId).ifPresent(user -> {
                    user.setRole(newRole);
                    userRepository.save(user);
                    telegramNotificationService.showUserDetails(finalChatId, finalMessageId, user);
                });
            }
        }

        telegramNotificationService.answerCallback(callbackId);
    }

    private boolean isAdmin(Long chatId) {
        return telegramNotificationService.getAdminChatId().equals(chatId);
    }
}
