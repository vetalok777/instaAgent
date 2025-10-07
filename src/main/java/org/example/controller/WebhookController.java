package org.example.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import okhttp3.RequestBody;
import org.example.GeminiChatService;
import org.example.database.entity.Interaction;
import org.example.database.repository.InteractionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
public class WebhookController {

    private final GeminiChatService chatService;
    private final InteractionRepository interactionRepository;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();

    @Value("${instagram.verify.token}")
    private String verifyToken;

    @Value("${instagram.access.token}")
    private String accessToken;

    @Value("${instagram.graph.api.url}")
    private String graphApiUrl;

    @Value("${instagram.page.id}")
    private String pageId;

    @Autowired
    public WebhookController(GeminiChatService chatService, InteractionRepository interactionRepository) {
        this.chatService = chatService;
        this.interactionRepository = interactionRepository;
    }

    // Метод для верифікації Webhook
    @GetMapping("/webhook")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            System.out.println("WEBHOOK_VERIFIED");
            return new ResponseEntity<>(challenge, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleMessage(@org.springframework.web.bind.annotation.RequestBody String payload) {
        System.out.println("Отримано повідомлення від Instagram: " + payload);

        JsonObject data = gson.fromJson(payload, JsonObject.class);
        try {
            JsonObject messaging = data.getAsJsonArray("entry").get(0).getAsJsonObject()
                    .getAsJsonArray("messaging").get(0).getAsJsonObject();

            // ГОЛОВНА ПЕРЕВІРКА: обробляємо, тільки якщо це справжнє повідомлення, а не "echo" чи "read"
            if (messaging.has("message") && !messaging.getAsJsonObject("message").has("is_echo")) {
                JsonObject messageObject = messaging.getAsJsonObject("message");

                // Перевіряємо, чи є в повідомленні текст
                if (messageObject.has("text")) {
                    String messageId = messageObject.get("mid").getAsString();

                    // ПЕРЕВІРКА НА ДУБЛІКАТ
                    if (interactionRepository.existsByMessageId(messageId)) {
                        System.out.println("Отримано дублікат повідомлення з ID: " + messageId + ". Ігноруємо.");
                        return new ResponseEntity<>(HttpStatus.OK);
                    }

                    String messageText = messageObject.get("text").getAsString();
                    String senderId = messaging.getAsJsonObject("sender").get("id").getAsString();

                    // 2. Зберігаємо повідомлення від користувача в БД
                    Interaction userInteraction = new Interaction(senderId, "USER", messageText);
                    userInteraction.setMessageId(messageId); // Встановлюємо ID
                    interactionRepository.save(userInteraction);
                    System.out.println("Збережено повідомлення від " + senderId);

                    // 3. Викликаємо сервіс
                    String replyText = chatService.sendMessage(senderId, messageText);

                    // 4. Зберігаємо відповідь від AI в БД
                    Interaction aiInteraction = new Interaction(senderId, "AI", replyText);
                    interactionRepository.save(aiInteraction);
                    System.out.println("Збережено відповідь AI для " + senderId);

                    // 5. Надсилаємо відповідь користувачу
                    sendReply(senderId, replyText); // Спростив, прибрав розбивку для ясності
                } else {
                    System.out.println("Отримано повідомлення без тексту (стікер, фото і т.д.). Ігноруємо.");
                }
            } else {
                // Це може бути "echo", "read", або інша системна подія
                System.out.println("Отримано системну подію (echo/read). Ігноруємо.");
            }
        } catch (Exception e) {
            System.err.println("Помилка обробки повідомлення: " + e.getMessage());
            e.printStackTrace();
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

    private void sendReply(String recipientId, String text) throws IOException {
        String fullUrl =  graphApiUrl + "/" + pageId + "/messages";

        // Створюємо JSON для відповіді
        JsonObject recipient = new JsonObject();
        recipient.addProperty("id", recipientId);
        JsonObject message = new JsonObject();
        message.addProperty("text", text);
        JsonObject requestBodyJson = new JsonObject();
        requestBodyJson.add("recipient", recipient);
        requestBodyJson.add("message", message);
        requestBodyJson.addProperty("messaging_type", "RESPONSE");
        requestBodyJson.addProperty("access_token", accessToken);

        RequestBody body = RequestBody.create(
                gson.toJson(requestBodyJson),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(fullUrl)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("Помилка надсилання відповіді: " + response.body().string());
            } else {
                System.out.println("Відповідь успішно надіслано.");
            }
        }
    }

    private List<String> splitMessage(String text, int size) {
        List<String> parts = new ArrayList<>();
        int length = text.length();
        for (int i = 0; i < length; i += size) {
            parts.add(text.substring(i, Math.min(length, i + size)));
        }
        return parts;
    }
}