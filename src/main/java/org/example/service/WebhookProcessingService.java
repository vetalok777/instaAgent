package org.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.example.database.entity.Interaction;
import org.example.database.entity.PostProductLink;
import org.example.database.entity.Product;
import org.example.database.repository.InteractionRepository;
import org.example.database.repository.PostProductLinkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

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
    private final PostProductLinkRepository postProductLinkRepository;

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
                                    InstagramMessageService instagramMessageService, PostProductLinkRepository postProductLinkRepository) {
        this.chatService = chatService;
        this.interactionRepository = interactionRepository;
        this.instagramMessageService = instagramMessageService;
        this.postProductLinkRepository = postProductLinkRepository;
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
                        return;
                    }

                    String messageText = messageObject.get("text").getAsString();
                    String senderId = messaging.getAsJsonObject("sender").get("id").getAsString();

                    Interaction userInteraction = new Interaction(senderId, "USER", messageText);
                    userInteraction.setMessageId(messageId);
                    interactionRepository.save(userInteraction);
                    System.out.println("Збережено повідомлення від " + senderId);

                    String replyText = chatService.sendMessage(senderId, messageText);

                    Interaction aiInteraction = new Interaction(senderId, "AI", replyText);
                    interactionRepository.save(aiInteraction);
                    System.out.println("Збережено відповідь AI для " + senderId);

                    instagramMessageService.sendReply(senderId, replyText);
                } else if (messageObject.has("attachments")) {
                    JsonArray attachments = messageObject.getAsJsonArray("attachments");
                    JsonObject firstAttachment = attachments.get(0).getAsJsonObject();

                    if (firstAttachment.has("type") && "share".equals(firstAttachment.get("type").getAsString())) {
                        JsonObject payloadObject = firstAttachment.getAsJsonObject("payload");

                        if (payloadObject.has("url")) {
                            String url = payloadObject.get("url").getAsString();

                            String instagramPostId = extractAssetIdFromUrl(url);

                            if (instagramPostId != null) {
                                String senderId = messaging.getAsJsonObject("sender").get("id").getAsString();
                                handlePostShare(senderId, instagramPostId);
                            } else {
                                System.out.println("Не вдалося витягти asset_id з URL.");
                            }
                        }
                    } else {
                        System.out.println("Отримано інший тип вкладення. Ігноруємо.");
                    }
                } else {
                        System.out.println("Отримано повідомлення без тексту (стікер, фото і т.д.). Ігноруємо.");
                    }
                } else {
                    System.out.println("Отримано системну подію (echo/read). Ігноруємо.");
                }
            } catch(Exception e){
                System.err.println("Помилка обробки повідомлення: " + e.getMessage());
                e.printStackTrace();
            }
        }

    /**
     * Handles the logic for when a user shares a post to the chat.
     * @param senderId The ID of the user who shared the post.
     * @param assetId The ID of the shared Instagram post.
     */
    private void handlePostShare(String senderId, String assetId) {
        String shortCode = instagramMessageService.getShortcodeFromAssetId(assetId);

        if (shortCode == null) {
            System.err.println("Не вдалося отримати shortcode для asset_id: " + assetId);
            return;
        }

        Optional<PostProductLink> linkOptional = postProductLinkRepository.findByInstagramPostId(shortCode);

        if (linkOptional.isPresent()) {
            Product product = linkOptional.get().getProduct();

            if (product == null) {
                instagramMessageService.sendReply(senderId, "Дякую! Схоже, цей товар вже неактуальний. Можливо, вас зацікавить щось інше з нашого асортименту?");
                return;
            }

            String userQuestion = String.format(
                    "Я зацікавився/лась цим товаром. Розкажи про нього детальніше, будь ласка."
            );
            String contextPrompt = String.format(
                    "Користувач запитує про товар, на який він відповів у Instagram. Ось інформація про товар з нашої бази даних: Назва: '%s', Ціна: %s грн, Опис: '%s'. Дай відповідь на його уявне питання: '%s'.",
                    product.getName(), product.getPrice().toString(), product.getDescription(), userQuestion
            );

            try {
                String replyText = chatService.sendMessage(senderId, contextPrompt);

                interactionRepository.save(new Interaction(senderId, "USER", userQuestion));
                interactionRepository.save(new Interaction(senderId, "AI", replyText));

                instagramMessageService.sendReply(senderId, replyText);

            } catch (IOException e) {
                System.err.println("Помилка при обробці поширеного поста: " + e.getMessage());
                instagramMessageService.sendReply(senderId, "Вибачте, сталася внутрішня помилка. Я вже повідомив менеджерів!");
            }

        } else {
            System.out.println("Не знайдено зв'язку для поста з ID: " + shortCode);
            instagramMessageService.sendReply(senderId, "Дякую, що поділилися! На жаль, я не зміг автоматично знайти інформацію про цей товар. Наш менеджер незабаром підключиться, щоб вам допомогти.");
        }
    }

    /**
     * Extracts the 'asset_id' value from the Instagram CDN URL.
     * @param url The URL from the attachment payload.
     * @return The asset_id as a String, or null if not found.
     */
    private String extractAssetIdFromUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String query = uri.getQuery();
            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && "asset_id".equals(pair[0])) {
                        return pair[1];
                    }
                }
            }
        } catch (java.net.URISyntaxException e) {
            System.err.println("Помилка розбору URL: " + e.getMessage());
        }
        return null;
    }
}



