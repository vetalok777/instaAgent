package org.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.example.database.entity.Interaction;
import org.example.database.repository.InteractionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service responsible for processing incoming webhook payloads from Instagram.
 * <p>
 * This service acts as the central hub for handling messages. It parses the payload,
 * checks for duplicates, saves the user's message, retrieves a response from the AI,
 * saves the AI's response, and sends the reply back to the user.
 */
@Service
public class WebhookProcessingService {
    private final Gson gson = new Gson();
    private final GeminiChatService chatService;
    private final InteractionRepository interactionRepository;
    private final InstagramMessageService instagramMessageService;

    /**
     * Constructs a new WebhookProcessingService with the required dependencies.
     *
     * @param chatService             The service for communicating with the Gemini AI.
     * @param interactionRepository   The repository for saving and retrieving interaction data.
     * @param instagramMessageService The service for sending messages back to Instagram.
     */
    @Autowired
    public WebhookProcessingService(GeminiChatService chatService,
                                    InteractionRepository interactionRepository,
                                    InstagramMessageService instagramMessageService) {
        this.chatService = chatService;
        this.interactionRepository = interactionRepository;
        this.instagramMessageService = instagramMessageService;
    }

    /**
     * Processes the raw JSON payload received from the Instagram webhook.
     * <p>
     * This method extracts the message details, processes only textual messages,
     * checks for duplicates, and orchestrates the conversation flow by involving
     * the chat service and the message sending service. It ignores non-text messages
     * and system events like echoes or read receipts.
     *
     * @param payload The JSON string payload from the webhook.
     */
    public void processWebhookPayload(String payload) {
        System.out.println("Отримано повідомлення від Instagram: " + payload);

        JsonObject data = gson.fromJson(payload, JsonObject.class);
        try {
            JsonObject messaging = data.getAsJsonArray("entry").get(0).getAsJsonObject()
                    .getAsJsonArray("messaging").get(0).getAsJsonObject();

            if (messaging.has("message") && !messaging.getAsJsonObject("message").has("is_echo")) {
                JsonObject messageObject = messaging.getAsJsonObject("message");

                if (messageObject.has("text")) {
                    String messageId = messageObject.get("mid").getAsString();

                    if (interactionRepository.existsByMessageId(messageId)) {
                        System.out.println("Отримано дублікат повідомлення з ID: " + messageId + ". Ігноруємо.");
                        return; // Просто виходимо з методу
                    }

                    String messageText = messageObject.get("text").getAsString();
                    String senderId = messaging.getAsJsonObject("sender").get("id").getAsString();

                    // 1. Зберігаємо повідомлення користувача
                    Interaction userInteraction = new Interaction(senderId, "USER", messageText);
                    userInteraction.setMessageId(messageId);
                    interactionRepository.save(userInteraction);
                    System.out.println("Збережено повідомлення від " + senderId);

                    // 2. Отримуємо відповідь від AI
                    String replyText = chatService.sendMessage(senderId, messageText);

                    // 3. Зберігаємо відповідь AI
                    Interaction aiInteraction = new Interaction(senderId, "AI", replyText);
                    interactionRepository.save(aiInteraction);
                    System.out.println("Збережено відповідь AI для " + senderId);

                    // 4. Надсилаємо відповідь користувачу через новий сервіс
                    instagramMessageService.sendReply(senderId, replyText);
                } else {
                    System.out.println("Отримано повідомлення без тексту (стікер, фото і т.д.). Ігноруємо.");
                }
            } else {
                System.out.println("Отримано системну подію (echo/read). Ігноруємо.");
            }
        } catch (Exception e) {
            System.err.println("Помилка обробки повідомлення: " + e.getMessage());
            e.printStackTrace();
        }
    }
}


