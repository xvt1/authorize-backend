// TelegramNotificationService.java
package com.texter.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TelegramNotificationService {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.admin.chatId}")
    private Long adminChatId;

    private final RestTemplate restTemplate;

    public TelegramNotificationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Long getAdminChatId() {
        return adminChatId;
    }

    // ---------- Сценарії ----------

    public void notifyAdminAboutNewUser(User user) {
        String nick = user.getNickname() != null ? user.getNickname() : user.getUsername();
        String text = "🆕 Новий підтверджений користувач:\n"
                + "Нікнейм: " + nick + "\n"
                + "Username: " + user.getUsername() + "\n"
                + "Статус: ⏳ Очікує\n\n"
                + "Спочатку прийміть або відхиліть заявку:";

        sendMessage(adminChatId, text, buildAcceptRejectKeyboard(user.getId()));
    }

    public void sendHelp(Long chatId) {
        String text = "🤖 Доступні команди:\n\n"
                + "/help — показати цю довідку\n"
                + "/list — показати список користувачів (🟢 онлайн / ⚫ офлайн) і керувати їх правами";
        sendMessage(chatId, text, null);
    }

    public void sendUserList(Long chatId, List<User> users) {
        sendMessage(chatId, formatUserListText(users), buildUserListKeyboard(users));
    }

    public void showUserDetails(Long chatId, Integer messageId, User user) {
        String text = formatUserDetails(user);
        Map<String, Object> keyboard;
        if (user.getRole() == Role.PENDING) {
            keyboard = buildAcceptRejectKeyboard(user.getId(), true);
        } else {
            keyboard = buildRoleKeyboard(user.getId(), true);
        }
        editMessage(chatId, messageId, text, keyboard);
    }

    /** Після натискання «Прийняти» — показати вибір ролі */
    public void showRoleSelection(Long chatId, Integer messageId, User user) {
        String nick = user.getNickname() != null ? user.getNickname() : user.getUsername();
        String text = "👤 " + nick + "\n"
                + "Login: " + user.getUsername() + "\n\n"
                + "✅ Заявку прийнято. Оберіть роль:";
        editMessage(chatId, messageId, text, buildRoleKeyboard(user.getId(), false));
    }

    public void backToList(Long chatId, Integer messageId, List<User> users) {
        editMessage(chatId, messageId, formatUserListText(users), buildUserListKeyboard(users));
    }

    public void answerCallback(String callbackQueryId) {
        Map<String, Object> body = new HashMap<>();
        body.put("callback_query_id", callbackQueryId);
        try {
            restTemplate.postForObject(apiUrl("answerCallbackQuery"), body, String.class);
        } catch (Exception ignored) {
            // ігноруємо збої answerCallbackQuery
        }
    }

    // ---------- Форматування тексту ----------

    private String formatUserListText(List<User> users) {
        if (users.isEmpty()) {
            return "Користувачів поки немає.";
        }
        StringBuilder text = new StringBuilder("👥 Користувачі (" + users.size() + "):\n\n");
        for (User u : users) {
            text.append(statusEmoji(u)).append(" ").append(u.getDisplayName())
                    .append(" (").append(u.getUsername()).append(")")
                    .append(" — ").append(roleLabel(u.getRole())).append("\n");
        }
        text.append("\nОберіть користувача нижче, щоб переглянути деталі або змінити права:");
        return text.toString();
    }

    private String formatUserDetails(User user) {
        String nick = user.getNickname() != null ? user.getNickname() : user.getUsername();
        return "👤 " + nick + "\n"
                + "Login: " + user.getUsername() + "\n"
                + "Статус: " + statusEmoji(user) + " " + statusLabel(user) + "\n"
                + "Роль: " + roleLabel(user.getRole()) + "\n\n"
                + (user.getRole() == Role.PENDING
                    ? "Спочатку прийміть або відхиліть заявку:"
                    : "Оберіть роль або дію:");
    }

    private String statusEmoji(User user) {
        return user.isOnline() ? "🟢" : "⚫";
    }

    private String statusLabel(User user) {
        return user.isOnline() ? "онлайн" : "офлайн";
    }

    private String roleLabel(Role role) {
        return switch (role) {
            case PENDING -> "⏳ Очікує";
            case VIEWER -> "👁 Перегляд";
            case EDITOR -> "✏️ Редагування";
            case ADMIN -> "👑 Адмін";
            case BLOCKED -> "🚫 Заблокований";
        };
    }

    // ---------- Клавіатури ----------

    private Map<String, Object> buildUserListKeyboard(List<User> users) {
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        for (User u : users) {
            String label = statusEmoji(u) + " " + u.getDisplayName();
            rows.add(List.of(button(label, "view:" + u.getId())));
        }
        Map<String, Object> keyboard = new HashMap<>();
        keyboard.put("inline_keyboard", rows);
        return keyboard;
    }

    private Map<String, Object> buildAcceptRejectKeyboard(Long userId) {
        return buildAcceptRejectKeyboard(userId, false);
    }

    private Map<String, Object> buildAcceptRejectKeyboard(Long userId, boolean withBackButton) {
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        rows.add(List.of(
                button("✅ Прийняти", "accept:" + userId),
                button("❌ Відхилити", "reject:" + userId)
        ));
        if (withBackButton) {
            rows.add(List.of(button("🔙 До списку", "back_to_list")));
        }
        Map<String, Object> keyboard = new HashMap<>();
        keyboard.put("inline_keyboard", rows);
        return keyboard;
    }

    private Map<String, Object> buildRoleKeyboard(Long userId, boolean withBackButton) {
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        rows.add(List.of(
                button("👁 Перегляд", "grant_view:" + userId)
        ));
        rows.add(List.of(
                button("✏️ Редагування", "grant_edit:" + userId)
        ));
        rows.add(List.of(
                button("👑 Адмін", "grant_admin:" + userId)
        ));
        rows.add(List.of(
                button("🚫 Заблокувати", "block:" + userId)
        ));
        if (withBackButton) {
            rows.add(List.of(button("🔙 До списку", "back_to_list")));
        }
        Map<String, Object> keyboard = new HashMap<>();
        keyboard.put("inline_keyboard", rows);
        return keyboard;
    }

    private Map<String, Object> button(String text, String callbackData) {
        Map<String, Object> b = new HashMap<>();
        b.put("text", text);
        b.put("callback_data", callbackData);
        return b;
    }

    // ---------- Низькорівневі виклики Telegram API ----------

    private void sendMessage(Long chatId, String text, Map<String, Object> replyMarkup) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        if (replyMarkup != null) {
            body.put("reply_markup", replyMarkup);
        }
        restTemplate.postForObject(apiUrl("sendMessage"), body, String.class);
    }

    private void editMessage(Long chatId, Integer messageId, String text, Map<String, Object> replyMarkup) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("text", text);
        if (replyMarkup != null) {
            body.put("reply_markup", replyMarkup);
        }
        try {
            restTemplate.postForObject(apiUrl("editMessageText"), body, String.class);
        } catch (HttpClientErrorException.BadRequest e) {
            String desc = e.getResponseBodyAsString();
            if (desc != null && desc.contains("message is not modified")) {
                return;
            }
            throw e;
        }
    }

    private String apiUrl(String method) {
        return "https://api.telegram.org/bot" + botToken + "/" + method;
    }
}
