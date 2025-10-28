package org.example.service.gemini;

import com.google.gson.Gson;
import okhttp3.*;
import org.example.database.entity.Client;
import org.example.database.entity.Interaction;
import org.example.database.repository.InteractionRepository;
import org.example.model.Content;
import org.example.model.Part;
import org.example.model.request.RequestPayload;
import org.example.model.response.ResponsePayload;
import org.example.service.RAGService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class GeminiChatService {
    private static final String API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    @Value("${gemini.api.key}")
    private String apiKey;
    private final InteractionRepository interactionRepository;
    private final RAGService ragService;

    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    @Autowired
    public GeminiChatService(InteractionRepository interactionRepository, RAGService ragService) {
        this.interactionRepository = interactionRepository;
        this.ragService = ragService;

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public String sendMessage(Client client, String userPsid, String userMessage) throws IOException {
        List<Content> conversationHistory = buildConversationHistory(client, userPsid);
        String ragContext = ragService.findRelevantContext(client, userMessage, 3);

        String finalUserMessage = userMessage;
        if (!ragContext.isEmpty()) {
            finalUserMessage = ragContext + "\n\nОсь запит від клієнта: \"" + userMessage + "\". Дай відповідь на основі контексту вище.";
        }

        Part userPart = new Part();
        userPart.setText(finalUserMessage);
        Content userContent = new Content();
        userContent.setParts(List.of(userPart));
        userContent.setRole("user");
        conversationHistory.add(userContent);

        RequestPayload payload = new RequestPayload();
        payload.setContents(conversationHistory);

        String jsonPayload = gson.toJson(payload);
        RequestBody body = RequestBody.create(jsonPayload, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(API_URL_TEMPLATE + apiKey)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response + " | " + response.body().string());
            }

            String responseBody = response.body().string();
            ResponsePayload responsePayload = gson.fromJson(responseBody, ResponsePayload.class);

            if (responsePayload.getCandidates() != null && !responsePayload.getCandidates().isEmpty()) {
                return responsePayload.getCandidates().get(0).getContent().getParts().get(0).getText();
            }
            return "Вибачте, сталася помилка. Не вдалося отримати відповідь.";
        }
    }

    private List<Content> buildConversationHistory(Client client, String userPsid) {
        List<Interaction> interactions = interactionRepository.findTop10ByClientIdAndSenderPsidOrderByTimestampDesc(client.getId(), userPsid);
        LocalDateTime lastUserMessageTime = interactions.stream()
                .filter(interaction -> "USER".equalsIgnoreCase(interaction.getAuthor()))
                .map(Interaction::getTimestamp)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        List<Content> history = new ArrayList<>();
        history.add(createSystemPrompt(client, lastUserMessageTime));

        Collections.reverse(interactions);

        for (Interaction interaction : interactions) {
            Part part = new Part();
            part.setText(interaction.getText());
            Content content = new Content();
            content.setParts(List.of(part));
            content.setRole("USER".equalsIgnoreCase(interaction.getAuthor()) ? "user" : "model");
            history.add(content);
        }
        return history;
    }

    private Content createSystemPrompt(Client client, LocalDateTime lastUserMessageTime) {
        Part systemPart = new Part();
        systemPart.setText(buildSystemPromptWithInactivityInfo(client.getAiSystemPrompt(), lastUserMessageTime));
        Content systemContent = new Content();
        systemContent.setParts(List.of(systemPart));
        systemContent.setRole("user"); // System prompts are passed as 'user' role in the first turn
        return systemContent;
    }

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
