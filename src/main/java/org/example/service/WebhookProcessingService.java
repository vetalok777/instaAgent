package org.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.example.database.entity.Interaction;
import org.example.database.entity.PostProductLink;
import org.example.database.entity.Product;
import org.example.database.repository.InteractionRepository;
import org.example.database.repository.PostProductLinkRepository;
import org.example.database.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    private final InstagramMessageService instagramMessageService;

    private final InteractionRepository interactionRepository;
    private final PostProductLinkRepository postProductLinkRepository;
    private final ProductRepository productRepository;

    private final Map<String, Long> postShareCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

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
                                    InstagramMessageService instagramMessageService, PostProductLinkRepository postProductLinkRepository, ProductRepository productRepository) {
        this.chatService = chatService;
        this.interactionRepository = interactionRepository;
        this.instagramMessageService = instagramMessageService;
        this.postProductLinkRepository = postProductLinkRepository;
        this.productRepository = productRepository;
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
    @Async
    @Transactional
    public void processWebhookPayload(String payload) {
        System.out.println("Отримано повідомлення від Instagram: " + payload);

        JsonObject data = gson.fromJson(payload, JsonObject.class);
        try {
            JsonObject messaging = data.getAsJsonArray("entry").get(0).getAsJsonObject()
                    .getAsJsonArray("messaging").get(0).getAsJsonObject();
            String senderId = messaging.getAsJsonObject("sender").get("id").getAsString();

            if (messaging.has("message") && !messaging.getAsJsonObject("message").has("is_echo")) {
                JsonObject messageObject = messaging.getAsJsonObject("message");

                if (messageObject.has("text")) {
                    String messageId = messageObject.get("mid").getAsString();
                    if (interactionRepository.existsByMessageId(messageId)) {
                        System.out.println("Отримано дублікат повідомлення з ID: " + messageId + ". Ігноруємо.");
                        return;
                    }

                    String messageText = messageObject.get("text").getAsString();

                    Long sharedProductId = postShareCache.remove(senderId);
                    if (sharedProductId != null) {
                        Optional<Product> productOptional = productRepository.findById(sharedProductId);
                        productOptional.ifPresent(product -> {
                            try {
                                handleTextReplyToPost(senderId, messageText, product, messageId);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    } else {
                        handleSimpleTextMessage(senderId, messageText, messageId);
                    }

                } else if (messageObject.has("attachments")) {
                    handleAttachmentMessage(messaging);
                }
            } else {
                System.out.println("Отримано системну подію (echo/read). Ігноруємо.");
            }
        } catch (Exception e) {
            System.err.println("Помилка обробки повідомлення: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleSimpleTextMessage(String senderId, String messageText, String messageId) throws IOException {
        Interaction userInteraction = new Interaction(senderId, "USER", messageText);
        userInteraction.setMessageId(messageId);
        interactionRepository.save(userInteraction);
        System.out.println("Збережено повідомлення від " + senderId);

        String replyText = chatService.sendMessage(senderId, messageText);

        Interaction aiInteraction = new Interaction(senderId, "AI", replyText);
        interactionRepository.save(aiInteraction);
        System.out.println("Збережено відповідь AI для " + senderId);

        instagramMessageService.sendReply(senderId, replyText);
    }

    private void handleTextReplyToPost(String senderId, String userReply, Product product, String messageId) throws IOException {
        System.out.println("Обробка відповіді на поширений пост.");

        String contextPrompt = String.format(
                "Користувач відповів на пост про товар. Інформація про товар: Назва: '%s', Ціна: %s грн, Опис: '%s'. Повідомлення користувача: '%s'. Дай змістовну відповідь, враховуючи і товар, і повідомлення.",
                product.getName(), product.getPrice().toString(), product.getDescription(), userReply
        );

        Interaction userInteraction = new Interaction(senderId, "USER", userReply);
        userInteraction.setMessageId(messageId);
        interactionRepository.save(userInteraction);

        String replyText = chatService.sendMessage(senderId, contextPrompt);
        interactionRepository.save(new Interaction(senderId, "AI", replyText));

        instagramMessageService.sendReply(senderId, replyText);
    }

    private void handleAttachmentMessage(JsonObject messaging) {
        String senderId = messaging.getAsJsonObject("sender").get("id").getAsString();
        JsonObject messageObject = messaging.getAsJsonObject("message");
        JsonArray attachments = messageObject.getAsJsonArray("attachments");
        JsonObject firstAttachment = attachments.get(0).getAsJsonObject();

        if (firstAttachment.has("type") && "share".equals(firstAttachment.get("type").getAsString())) {
            String url = firstAttachment.getAsJsonObject("payload").get("url").getAsString();
            String assetId = extractAssetIdFromUrl(url);

            if (assetId != null) {
                String shortcode = instagramMessageService.getShortcodeFromAssetId(assetId);
                if (shortcode != null) {
                    Optional<PostProductLink> linkOptional = postProductLinkRepository.findByInstagramPostId(shortcode);
                    if (linkOptional.isPresent() && linkOptional.get().getProduct() != null) {
                        Product product = linkOptional.get().getProduct();

                        postShareCache.put(senderId, product.getId());
                        System.out.println("Пост поширено. Товар додано в кеш на 2 секунди для senderId: " + senderId);

                        scheduler.schedule(() -> processDelayedPostShare(senderId), 2, TimeUnit.SECONDS);
                    }
                }
            }
        } else {
            System.out.println("Отримано інший тип вкладення. Ігноруємо.");
        }
    }

    private void processDelayedPostShare(String senderId) {
        Long productId = postShareCache.remove(senderId);
        if (productId != null) {
            System.out.println("Відповідь на пост не надійшла. Обробляємо як просте поширення.");

            productRepository.findById(productId).ifPresent(product -> {
                try {
                    String contextPrompt = String.format(
                            "Користувач щойно поділився постом про товар. Інформація про товар: Назва: '%s', Ціна: %s грн, Опис: '%s'. Привітайся і запитай, що саме його цікавить у цьому товарі.",
                            product.getName(), product.getPrice().toString(), product.getDescription()
                    );

                    String replyText = chatService.sendMessage(senderId, contextPrompt);
                    interactionRepository.save(new Interaction(senderId, "AI", replyText));
                    instagramMessageService.sendReply(senderId, replyText);
                } catch (IOException e) {
                    System.err.println("Помилка при відкладеній обробці поста: " + e.getMessage());
                }
            });
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



