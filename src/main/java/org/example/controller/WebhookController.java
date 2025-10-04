package org.example.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import okhttp3.RequestBody;
import org.example.GeminiChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
public class WebhookController {

    private final GeminiChatService chatService;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();

    @Value("${instagram.verify.token}")
    private String verifyToken;

    @Value("${instagram.access.token}")
    private String accessToken;

    public WebhookController(GeminiChatService chatService) {
        this.chatService = chatService;
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

    // Метод для обробки вхідних повідомлень
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleMessage(@org.springframework.web.bind.annotation.RequestBody String payload) {
        System.out.println("Отримано повідомлення від Instagram: " + payload);

        JsonObject data = gson.fromJson(payload, JsonObject.class);
        // Розбираємо складний JSON від Meta
        try {
            String messageText = data.getAsJsonArray("entry").get(0).getAsJsonObject()
                    .getAsJsonArray("messaging").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("text").getAsString();

            String senderId = data.getAsJsonArray("entry").get(0).getAsJsonObject()
                    .getAsJsonArray("messaging").get(0).getAsJsonObject()
                    .getAsJsonObject("sender").get("id").getAsString();

            // Генеруємо відповідь через Gemini
            String replyText = chatService.sendMessage(messageText);

            // Надсилаємо відповідь через Graph API
            sendReply(senderId, replyText);

        } catch (Exception e) {
            System.err.println("Помилка обробки повідомлення: " + e.getMessage());
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

    private void sendReply(String recipientId, String text) throws IOException {
        String graphApiUrl = "https://graph.facebook.com/v23.0/830199246836474/messages";

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
                .url(graphApiUrl)
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
}