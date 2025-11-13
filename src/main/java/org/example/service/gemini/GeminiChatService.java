package org.example.service.gemini;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.example.database.entity.Client;
import org.example.database.entity.Interaction;
import org.example.database.repository.InteractionRepository;
import org.example.model.response.ResponsePayload;
import org.example.model.response.ResponseCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class GeminiChatService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiChatService.class);
    private static final String API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Value("${gemini.api.key}")
    private String apiKey;

    private final InteractionRepository interactionRepository;
    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    @Autowired
    public GeminiChatService(InteractionRepository interactionRepository) {
        this.interactionRepository = interactionRepository;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public String sendMessage(Client client, String userPsid, String userMessage) throws IOException {

        long startTime = System.currentTimeMillis();

        // 1. Створюємо системний промпт
        String systemPromptText = buildSystemPromptWithInactivityInfo(client.getAiSystemPrompt(), getLastUserMessageTime(client, userPsid));
        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemPart = new JsonObject();
        systemPart.addProperty("text", systemPromptText);
        systemParts.add(systemPart);
        systemInstruction.add("parts", systemParts);

        // 2. Створюємо інструмент FileSearch з фільтром
        JsonArray tools = new JsonArray();
        if (client.getFileSearchStoreName() != null && !client.getFileSearchStoreName().isBlank()) {

            // Ми шукаємо рядок "true", а не boolean true
            String metadataFilter = "is_active = \"true\"";

            JsonObject fileSearch = new JsonObject();
            JsonArray storeNames = new JsonArray();
            storeNames.add(client.getFileSearchStoreName());
            fileSearch.add("file_search_store_names", storeNames);
            fileSearch.addProperty("metadata_filter", metadataFilter);

            JsonObject tool = new JsonObject();
            tool.add("file_search", fileSearch);
            tools.add(tool);

            logger.debug("Використання FileSearchStore: {} з фільтром: {}", client.getFileSearchStoreName(), metadataFilter);
        }

        // 3. Будуємо історію
        JsonArray history = buildConversationHistory(client, userPsid);

        // 4. Додаємо поточне повідомлення
        JsonObject userMsgPart = new JsonObject();
        userMsgPart.addProperty("text", userMessage);
        JsonArray userParts = new JsonArray();
        userParts.add(userMsgPart);
        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");
        userContent.add("parts", userParts);
        history.add(userContent);

        // 5. Формуємо тіло запиту
        JsonObject payload = new JsonObject();
        payload.add("contents", history);
        payload.add("system_instruction", systemInstruction);
        if (tools.size() > 0) {
            payload.add("tools", tools);
        }

        String jsonPayload = gson.toJson(payload);
        RequestBody body = RequestBody.create(jsonPayload, JSON);
        Request request = new Request.Builder()
                .url(API_URL_TEMPLATE + apiKey)
                .post(body)
                .build();

        // 6. Виконуємо запит
        try (Response response = httpClient.newCall(request).execute()) {

            // Читаємо тіло відповіді лише один раз
            String responseBody = Objects.requireNonNull(response.body()).string();

            if (!response.isSuccessful()) {
                logger.error("Помилка API Gemini: {} - {}", response.code(), responseBody);
                throw new IOException("Unexpected code " + response.code() + " | " + responseBody);
            }

            // 7. Парсимо відповідь та перевіряємо цитати
            ResponsePayload responsePayload = gson.fromJson(responseBody, ResponsePayload.class);
            if (responsePayload.getCandidates() == null || responsePayload.getCandidates().isEmpty()) {
                logger.warn("Отримано 0 кандидатів у відповіді (PSID: {})", userPsid);
                return "Вибачте, сталася помилка. Не вдалося отримати відповідь.";
            }

            ResponseCandidate candidate = responsePayload.getCandidates().get(0);
            int citationCount = 0;

            if (candidate.getGroundingMetadata() == null ||
                    candidate.getGroundingMetadata().getCitationSources() == null ||
                    candidate.getGroundingMetadata().getCitationSources().isEmpty()) {

                logger.warn("Відповідь згенерована БЕЗ цитат (fallback). PSID: {}. Запит: '{}'", userPsid, userMessage);
            } else {
                citationCount = candidate.getGroundingMetadata().getCitationSources().size();
            }

            // --- ВИПРАВЛЕННЯ: Null-check для Content та Parts ---
            if (candidate.getContent() != null &&
                    candidate.getContent().getParts() != null &&
                    !candidate.getContent().getParts().isEmpty())
            {
                String responseText = candidate.getContent().getParts().get(0).getText();
                long duration = System.currentTimeMillis() - startTime;
                logger.info("Відповідь згенеровано за {} мс. Кількість цитат: {} (PSID: {})", duration, citationCount, userPsid);
                return responseText;
            } else {
                logger.warn("Відповідь згенерована без тексту (PSID: {}). Повна відповідь: {}", userPsid, responseBody);
                return "Вибачте, сталася помилка. Модель повернула пусте повідомлення.";
            }
            // --- КІНЕЦЬ ВИПРАВЛЕННЯ ---

        } catch (Exception e) {
            logger.error("Помилка під час виклику GeminiChatService (PSID: {}): {}", userPsid, e.getMessage(), e);
            throw new IOException("Помилка під час виклику GeminiChatService: " + e.getMessage(), e);
        }
    }

    private JsonArray buildConversationHistory(Client client, String userPsid) {
        List<Interaction> interactions = interactionRepository.findTop10ByClientIdAndSenderPsidOrderByTimestampDesc(client.getId(), userPsid);
        Collections.reverse(interactions); // Від найстарішого до найновішого

        JsonArray history = new JsonArray();
        for (Interaction interaction : interactions) {
            String role = "USER".equalsIgnoreCase(interaction.getAuthor()) ? "user" : "model";

            JsonObject part = new JsonObject();
            part.addProperty("text", interaction.getText());
            JsonArray parts = new JsonArray();
            parts.add(part);
            JsonObject content = new JsonObject();
            content.addProperty("role", role);
            content.add("parts", parts);

            history.add(content);
        }
        return history;
    }

    private LocalDateTime getLastUserMessageTime(Client client, String userPsid) {
        return interactionRepository.findTop10ByClientIdAndSenderPsidOrderByTimestampDesc(client.getId(), userPsid)
                .stream()
                .filter(interaction -> "USER".equalsIgnoreCase(interaction.getAuthor()))
                .map(Interaction::getTimestamp)
                .findFirst()
                .orElse(null);
    }

    // (Метод buildSystemPromptWithInactivityInfo залишається без змін)
    private String buildSystemPromptWithInactivityInfo(String basePrompt, LocalDateTime lastUserMessageTime) {
        if (lastUserMessageTime == null) {
            return basePrompt;
        }

        Duration sinceLastUserMessage = Duration.between(lastUserMessageTime, LocalDateTime.now());
        if (sinceLastUserMessage.isNegative()) {
            return basePrompt;
        }

        long days = sinceLastUserMessage.toDays();
        long hours = sinceLastUserMessage.toHours() % 24;
        long minutes = sinceLastUserMessage.toMinutes() % 60;

        StringBuilder inactivityDescription = new StringBuilder("Остання активність користувача була ");
        if (days > 0) {
            inactivityDescription.append(days).append(days == 1 ? " день" : " дні");
            if (hours > 0 || minutes > 0) {
                inactivityDescription.append(" ");
            }
        }
        if (hours > 0) {
            inactivityDescription.append(hours).append(hours == 1 ? " година" : " годин");
            if (minutes > 0) {
                inactivityDescription.append(" ");
            }
        }
        if (minutes > 0) {
            inactivityDescription.append(minutes).append(minutes == 1 ? " хвилина" : " хвилин");
        }

        if (days == 0 && hours == 0 && minutes == 0) {
            inactivityDescription.append("менше хвилини тому");
        } else {
            inactivityDescription.append(" тому");
        }

        inactivityDescription.append(". Якщо минуло 12 або більше годин, ввічливо привітайся знову. Якщо менше 12 годин — продовжуй спілкування без повторного привітання, якщо для контексту це не потрібно.");

        return basePrompt + "\n\n" + inactivityDescription;
    }
}