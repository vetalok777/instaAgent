package org.example.service;

import com.google.gson.Gson;
import okhttp3.*;
import org.example.database.entity.Interaction;
import org.example.database.entity.Product;
import org.example.database.repository.InteractionRepository;
import org.example.model.Content;
import org.example.model.Part;
import org.example.model.RequestPayload;
import org.example.model.ResponsePayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class GeminiChatService {
    private static final String API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    @Value("${gemini.api.key}")
    private String apiKey;
    private final InteractionRepository interactionRepository;
    private final ProductService productService;

    private final OkHttpClient client;
    private final Gson gson = new Gson();

    @Autowired
    public GeminiChatService(InteractionRepository interactionRepository, ProductService productService) {
        this.interactionRepository = interactionRepository;
        this.productService = productService;

        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public String sendMessage(String senderId, String message) throws IOException {
        List<Content> conversationHistory = buildConversationHistory(senderId);
        String productContext = buildProductContext(message);

        String finalUserMessage = message;
        if (productContext != null && !productContext.isEmpty()) {
            finalUserMessage = productContext + "\n\nОсь запит від клієнта: \"" + message + "\". Дай відповідь на основі контексту вище.";
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

        try (Response response = client.newCall(request).execute()) {
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

    private List<Content> buildConversationHistory(String senderId) {
        List<Content> history = new ArrayList<>();
        history.add(createSystemPrompt());

        List<Interaction> interactions = interactionRepository.findTop10BySenderIdOrderByTimestampDesc(senderId);
        Collections.reverse(interactions);

        for (Interaction interaction : interactions) {
            Part part = new Part();
            part.setText(interaction.getText());
            Content content = new Content();
            content.setParts(List.of(part));
            content.setRole(interaction.getAuthor().equalsIgnoreCase("AI") ? "model" : "user");
            history.add(content);
        }
        return history;
    }

    private String buildProductContext(String userMessage) {
        List<String> keywords = extractKeywords(userMessage);
        if (keywords.isEmpty()) {
            return "";
        }

        Set<Product> foundProducts = new HashSet<>();
        for (String keyword : keywords) {
            foundProducts.addAll(productService.findProductsByName(keyword));
        }

        if (foundProducts.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder("### Контекст з Бази Даних (Source of Truth) ###\n");
        context.append("Це єдина достовірна інформація про товари. Відповідай СУВОРО на основі цих даних. НЕ вигадуй ціни, кольори, розміри чи наявність.\n\n");

        for (Product product : foundProducts) {
            context.append(String.format("Товар: %s\n", product.getName()));
            context.append(String.format("Опис: %s\n", product.getDescription()));
            context.append(String.format("Ціна: %.2f грн.\n", product.getPrice()));

            String variantsInfo = product.getVariants().stream()
                    .filter(variant -> variant.getQuantity() > 0)
                    .map(variant -> String.format("- Розмір: `%s`, Колір: `%s` (в наявності: %d шт.)",
                            variant.getSize(), variant.getColor(), variant.getQuantity()))
                    .collect(Collectors.joining("\n"));

            if (!variantsInfo.isEmpty()) {
                context.append("Доступні варіанти:\n").append(variantsInfo).append("\n");
            } else {
                context.append("На жаль, цього товару або його варіантів немає в наявності.\n");
            }
            context.append("\n");
        }
        context.append("### Кінець Контексту ###\n");
        return context.toString();
    }

    private List<String> extractKeywords(String text) {
        String[] words = text.toLowerCase()
                .replaceAll("[.,!?\"']", "")
                .split("\\s+");

        List<String> stopWords = List.of("є", "у", "вас", "чи", "який", "яка", "яке", "які", "а", "і", "в", "на", "до", "з", "за", "по", "про", "хочу", "купити", "є", "в", "наявності", "ціна");
        Set<String> keywords = new HashSet<>();

        for (String word : words) {
            if (!stopWords.contains(word) && word.length() > 3) { // Increased length to avoid stemming short words
                keywords.add(lemmatize(word));
            }
        }
        return new ArrayList<>(keywords);
    }

    private String lemmatize(String word) {
        if (word.length() > 4) {
            if (word.endsWith("и") || word.endsWith("і") || word.endsWith("а") || word.endsWith("я")) {
                return word.substring(0, word.length() - 1);
            }
            if (word.endsWith("ого") || word.endsWith("ому")) {
                return word.substring(0, word.length() - 3);
            }
            if (word.endsWith("ий") || word.endsWith("ій") || word.endsWith("ої") || word.endsWith("им")) {
                return word.substring(0, word.length() - 2);
            }
        }
        return word;
    }

    private Content createSystemPrompt() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("system_prompt.txt")) {
            if (inputStream == null) {
                throw new IOException("System prompt file not found");
            }
            String systemPromptText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            Part systemPart = new Part();
            systemPart.setText(systemPromptText);
            Content systemContent = new Content();
            systemContent.setParts(List.of(systemPart));
            systemContent.setRole("user");
            return systemContent;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read system prompt", e);
        }
    }
}
