package org.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(InstagramMessageService.class);

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
    public void sendReply(String accessToken, String recipientId, String text) {
        // Check if the message needs to be split
        if (text.length() > 1000) {
            List<String> messageParts = splitMessage(text, 990);
            for (String part : messageParts) {
                try {
                    sendMessagePart(accessToken, recipientId, part);
                } catch (IOException e) {
                    logger.error("Помилка надсилання частини повідомлення: {}", e.getMessage(), e);
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            try {
                sendMessagePart(accessToken, recipientId, text);
            } catch (IOException e) {
                logger.error("Помилка надсилання повідомлення: {}", e.getMessage(), e);
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
    private void sendMessagePart(String accessToken, String recipientId, String text) throws IOException {
        HttpUrl url = HttpUrl.parse(graphApiUrl);
        if (url == null) {
            logger.error("Invalid base graphApiUrl: {}", graphApiUrl);
            throw new IOException("Invalid base graphApiUrl: " + graphApiUrl);
        }
        HttpUrl fullUrl = url.newBuilder().addPathSegment(pageId).addPathSegment("messages").build();

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
                logger.error("Помилка надсилання відповіді на URL [{}]: {}", fullUrl, response.body() != null ? response.body().string() : "Немає тіла відповіді");
            } else {
                logger.info("Відповідь успішно надіслано користувачу {}.", recipientId);
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

    public String getShortcodeFromAssetId(String accessToken, String assetId) {
        String url = graphApiUrl + "/" + assetId + "?fields=shortcode&access_token=" + accessToken;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Помилка отримання shortcode: {}", response.body() != null ? response.body().string() : "Немає тіла відповіді");
                return null;
            }
            String responseBody = response.body().string();
            JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
            if (jsonObject.has("shortcode")) {
                return jsonObject.get("shortcode").getAsString();
            }
        } catch (IOException e) {
            logger.error("Помилка API-запиту для отримання shortcode: {}", e.getMessage(), e);
        }
        return null;
    }
}
