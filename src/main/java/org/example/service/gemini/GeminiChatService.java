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
        List<Content> history = new ArrayList<>();
        history.add(createSystemPrompt(client));

        List<Interaction> interactions = interactionRepository.findTop10ByClientIdAndSenderPsidOrderByTimestampDesc(client.getId(), userPsid);
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

    private Content createSystemPrompt(Client client) {
        Part systemPart = new Part();
        systemPart.setText(client.getAiSystemPrompt());
        Content systemContent = new Content();
        systemContent.setParts(List.of(systemPart));
        systemContent.setRole("user"); // System prompts are passed as 'user' role in the first turn
        return systemContent;
    }
}
