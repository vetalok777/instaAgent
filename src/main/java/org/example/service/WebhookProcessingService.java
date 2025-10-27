package org.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.example.database.entity.Client;
import org.example.database.entity.Interaction;
import org.example.database.repository.ClientRepository;
import org.example.database.repository.InteractionRepository;
import org.example.service.gemini.GeminiChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final InstagramMessageService instagramMessageService;
    private final InteractionRepository interactionRepository;
    private final ClientRepository clientRepository;

    private static final Logger logger = LoggerFactory.getLogger(WebhookProcessingService.class);

    /**
     * Constructs a new WebhookProcessingService with the required dependencies.
     *
     * @param chatService             The service for communicating with the Gemini AI.
     * @param interactionRepository   The repository for saving and retrieving interaction data.
     * @param instagramMessageService The service for sending messages back to Instagram.
     * @param clientRepository        The repository for managing clients.
     */
    @Autowired
    public WebhookProcessingService(GeminiChatService chatService, InteractionRepository interactionRepository, InstagramMessageService instagramMessageService, ClientRepository clientRepository) {
        this.chatService = chatService;
        this.interactionRepository = interactionRepository;
        this.instagramMessageService = instagramMessageService;
        this.clientRepository = clientRepository;
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
    @Transactional
    public void processWebhookPayload(String payload) {
        logger.info("Отримано повідомлення від Instagram: {}", payload);
        JsonObject data = gson.fromJson(payload, JsonObject.class);

        Client client = null;
        try {
            JsonObject entry = data.getAsJsonArray("entry").get(0).getAsJsonObject();
            String pageId = entry.get("id").getAsString();

            Optional<Client> clientOptional = clientRepository.findByInstagramPageId(pageId);
            if (clientOptional.isEmpty()) {
                logger.warn("Отримано повідомлення для незареєстрованої сторінки з ID: {}. Ігноруємо.", pageId);
                return;
            }
            client = clientOptional.get();

            JsonObject messaging = entry.getAsJsonArray("messaging").get(0).getAsJsonObject();
            String senderPsid = messaging.getAsJsonObject("sender").get("id").getAsString();

            if (!messaging.has("message") || messaging.getAsJsonObject("message").has("is_echo")) {
                logger.info("Отримано системну подію (echo/read) або подію без повідомлення. Ігноруємо.");
                return;
            }

            JsonObject messageObject = messaging.getAsJsonObject("message");

            // Ignore non-text messages for now
            if (!messageObject.has("text")) {
                logger.info("Отримано не-текстове повідомлення. Ігноруємо.");
                return;
            }

            String messageId = messageObject.get("mid").getAsString();

            if (interactionRepository.existsByMessageId(messageId)) {
                logger.info("Отримано дублікат повідомлення з ID: {}. Ігноруємо.", messageId);
                return;
            }

            String messageText = messageObject.get("text").getAsString();

            // Save user interaction
            Interaction userInteraction = new Interaction(senderPsid, "USER", messageText);
            userInteraction.setClient(client);
            userInteraction.setMessageId(messageId);
            interactionRepository.save(userInteraction);

            // Get AI response
            String replyText = chatService.sendMessage(client, senderPsid, messageText);

            // Save AI interaction
            Interaction aiInteraction = new Interaction(senderPsid, "AI", replyText);
            aiInteraction.setClient(client);
            interactionRepository.save(aiInteraction);

            // Send reply to user
            instagramMessageService.sendReply(client.getAccessToken(), senderPsid, replyText);
        } catch (Exception e) {
            logger.error("Помилка обробки повідомлення для клієнта '{}': {} - {}", (client != null ? client.getClientName() : "N/A"), e.getClass().getName(), e.getMessage(), e);
            e.printStackTrace();
        }
    }
}
