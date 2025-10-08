package org.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A service responsible for sending messages to users via the Instagram Graph API.
 * <p>
 * This class formats and sends replies, and also handles long messages
 * by splitting them into multiple parts according to the platform's limitations.
 */
@Service
public class InstagramMessageService {
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();

    @Value("${instagram.access.token}")
    private String accessToken;

    @Value("${instagram.graph.api.url}")
    private String graphApiUrl;

    @Value("${instagram.page.id}")
    private String pageId;

    /**
     * Sends a text reply to a user on Instagram.
     * <p>
     * If the message exceeds 1000 characters, it is automatically split
     * into smaller parts and sent sequentially with a short delay.
     *
     * @param recipientId The Instagram user ID to send the reply to.
     * @param text        The text of the message to send.
     */
    public void sendReply(String recipientId, String text) {
        // Check if the message needs to be split
        if (text.length() > 1000) {
            List<String> messageParts = splitMessage(text, 990);
            for (String part : messageParts) {
                try {
                    sendMessagePart(recipientId, part);
                    Thread.sleep(1500); // Затримка між частинами
                } catch (IOException | InterruptedException e) {
                    System.err.println("Помилка надсилання частини повідомлення: " + e.getMessage());
                    Thread.currentThread().interrupt(); // Відновлюємо статус переривання
                }
            }
        } else {
            try {
                sendMessagePart(recipientId, text);
            } catch (IOException e) {
                System.err.println("Помилка надсилання повідомлення: " + e.getMessage());
            }
        }
    }

    /**
     * An internal method for sending a single part of a message via the Instagram Graph API.
     *
     * @param recipientId The recipient's ID.
     * @param text        The text of the message (or a part of it).
     * @throws IOException if an error occurs during the HTTP request execution.
     */
    private void sendMessagePart(String recipientId, String text) throws IOException {
        String fullUrl = graphApiUrl + "/" + pageId + "/messages";

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

    /**
     * A helper method to split a long string into a list of smaller parts.
     *
     * @param text The string to be split.
     * @param size The maximum size of each part.
     * @return A list of strings, which are parts of the original text.
     */
    private List<String> splitMessage(String text, int size) {
        List<String> parts = new ArrayList<>();
        int length = text.length();
        for (int i = 0; i < length; i += size) {
            parts.add(text.substring(i, Math.min(length, i + size)));
        }
        return parts;
    }
}

