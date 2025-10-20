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

    private final Map<String, String> postShareCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * Constructs a new WebhookProcessingService with the required dependencies.
     *
     * @param chatService             The service for communicating with the Gemini AI.
     * @param interactionRepository   The repository for saving and retrieving interaction data.
     * @param instagramMessageService The service for sending messages back to Instagram.
     */
    @Autowired
    public WebhookProcessingService(GeminiChatService chatService, InteractionRepository interactionRepository, InstagramMessageService instagramMessageService, PostProductLinkRepository postProductLinkRepository, ProductRepository productRepository) {
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
            JsonObject messaging = data.getAsJsonArray("entry").get(0).getAsJsonObject().getAsJsonArray("messaging").get(0).getAsJsonObject();
            String senderId = messaging.getAsJsonObject("sender").get("id").getAsString();

            if (!messaging.has("message") || messaging.getAsJsonObject("message").has("is_echo")) {
                System.out.println("Отримано системну подію (echo/read) або подію без повідомлення. Ігноруємо.");
                return;
            }

            JsonObject messageObject = messaging.getAsJsonObject("message");
            String messageId = messageObject.get("mid").getAsString();

            if (interactionRepository.existsByMessageId(messageId)) {
                System.out.println("Отримано дублікат повідомлення з ID: " + messageId + ". Ігноруємо.");
                return;
            }

            boolean hasText = messageObject.has("text");
            Product sharedProduct = getProductFromAttachment(messaging);

            // Unified logic for handling different message types
            if (hasText && sharedProduct != null) {
                // Case 1: User sends a post with a text message in a single event.
                String messageText = messageObject.get("text").getAsString();
                postShareCache.remove(senderId); // In case there's a lingering cache, clear it.
                try {
                    handleTextReplyToPost(senderId, messageText, sharedProduct, messageId);
                } catch (IOException e) {
                    System.err.println("Помилка при обробці відповіді на пост з текстом: " + e.getMessage());
                }
            } else if (hasText) {
                // Case 2: User sends a text message. It could be a standalone message
                // or a reply to a post that was sent in a separate, preceding event.
                String messageText = messageObject.get("text").getAsString();
                String cachedValue = postShareCache.remove(senderId);

                if (cachedValue != null) {
                    // A post was shared recently. Treat this text as a reply to that post.
                    Long productId = Long.parseLong(cachedValue.split(":")[0]);
                    productRepository.findById(productId).ifPresent(product -> {
                        try {
                            handleTextReplyToPost(senderId, messageText, product, messageId);
                        } catch (IOException e) {
                            System.err.println("Помилка при обробці відповіді на пост: " + e.getMessage());
                        }
                    });
                } else {
                    // No recent post share. Treat as a simple text message.
                    handleSimpleTextMessage(senderId, messageText, messageId);
                }
            } else if (sharedProduct != null) {
                // Case 3: User shares a post without any text.
                // We'll wait a few seconds to see if a text message follows up.
                String cacheValue = sharedProduct.getId() + ":" + messageId;
                postShareCache.put(senderId, cacheValue);
                scheduler.schedule(() -> handleSimplePostShare(senderId), 3, TimeUnit.SECONDS);
            }

        } catch (Exception e) {
            System.err.println("Помилка обробки повідомлення: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleSimpleTextMessage(String senderId, String messageText, String messageId) {
        try {
            Interaction userInteraction = new Interaction(senderId, "USER", messageText);
            userInteraction.setMessageId(messageId);
            interactionRepository.save(userInteraction);

            String contextPrompt = String.format(
                "Проаналізуй історію чату і дай відповідь на повідомлення користувача. Якщо це початок розмови, можеш привітатися. В іншому випадку, не вітайся і одразу переходь до суті. Повідомлення користувача: '%s'",
                messageText
            );
            String replyText = chatService.sendMessage(senderId, contextPrompt);

            interactionRepository.save(new Interaction(senderId, "AI", replyText));
            instagramMessageService.sendReply(senderId, replyText);
        } catch (IOException e) {
            System.err.println("Помилка при обробці простого текстового повідомлення: " + e.getMessage());
        }
    }

    private void handleTextReplyToPost(String senderId, String userReply, Product product, String messageId) throws IOException {
        String dbMessage = String.format("Поділився постом про '%s' і відповів: \"%s\"", product.getName(), userReply);
        Interaction userInteraction = new Interaction(senderId, "USER", dbMessage);
        userInteraction.setMessageId(messageId);
        interactionRepository.save(userInteraction);

        String contextPrompt = String.format(
                "Користувач відповів на пост про товар. Інформація про товар: Назва: '%s', Ціна: %s грн, Опис: '%s'. Повідомлення користувача: '%s'. Проаналізуй історію чату: не вітайся, якщо ви вже спілкувалися. Дай змістовну відповідь по суті.",
                product.getName(), product.getPrice().toString(), product.getDescription(), userReply
        );

        String replyText = chatService.sendMessage(senderId, contextPrompt);
        interactionRepository.save(new Interaction(senderId, "AI", replyText));
        instagramMessageService.sendReply(senderId, replyText);
    }

    private void handleSimplePostShare(String senderId) {
        String cachedValue = postShareCache.remove(senderId);
        if (cachedValue == null) {
            return; // Already processed as a reply with text
        }

        String[] parts = cachedValue.split(":");
        Long productId = Long.parseLong(parts[0]);
        String messageId = parts[1];

        if (interactionRepository.existsByMessageId(messageId)) {
            return; // Avoid race conditions and double processing
        }

        productRepository.findById(productId).ifPresent(product -> {
            try {
                String shareMessage = String.format("Користувач поділився постом про товар: %s", product.getName());
                Interaction userInteraction = new Interaction(senderId, "USER", shareMessage);
                userInteraction.setMessageId(messageId);
                interactionRepository.save(userInteraction);

                String contextPrompt = String.format(
                        "Користувач щойно поділився постом про товар. Інформація про товар: Назва: '%s', Ціна: %s грн, Опис: '%s'. Проаналізуй історію чату: якщо це початок розмови, привітайся. Потім запитай, що саме його цікавить у цьому товарі.",
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

    private Product getProductFromAttachment(JsonObject messaging) {
        JsonObject messageObject = messaging.getAsJsonObject("message");
        if (!messageObject.has("attachments")) return null;

        JsonArray attachments = messageObject.getAsJsonArray("attachments");
        JsonObject firstAttachment = attachments.get(0).getAsJsonObject();

        if (firstAttachment.has("type") && "share".equals(firstAttachment.get("type").getAsString())) {
            String url = firstAttachment.getAsJsonObject("payload").get("url").getAsString();
            String assetId = extractAssetIdFromUrl(url);

            if (assetId != null) {
                String shortcode = instagramMessageService.getShortcodeFromAssetId(assetId);
                if (shortcode != null) {
                    Optional<PostProductLink> linkOptional = postProductLinkRepository.findByInstagramPostId(shortcode);
                    return linkOptional.map(PostProductLink::getProduct).orElse(null);
                }
            }
        }
        return null;
    }

    private String extractAssetIdFromUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String query = uri.getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
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
