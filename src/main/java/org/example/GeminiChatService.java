package org.example;

import com.google.gson.Gson;
import okhttp3.*;
import org.example.model.Content;
import org.example.model.Part;
import org.example.model.RequestPayload;
import org.example.model.ResponsePayload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Service
public class GeminiChatService {
    private static final String API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent?key=";
    private final String apiKey;
    private final OkHttpClient client;
    private final Gson gson = new Gson();
    private final List<Content> history = new ArrayList<>();

    public GeminiChatService() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input == null) throw new IOException("Cannot find application.properties");
            properties.load(input);
        }
        this.apiKey = properties.getProperty("gemini.api.key");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("API key not found in application.properties");
        }

        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        initializeChat();
    }

    private void initializeChat() {
        String systemPrompt = "Ти — привітний та ефективний AI-асистент для інтернет-магазину одягу 'FashionStyle'. Твоє завдання — відповідати на запитання клієнтів про товари, наявність, доставку та допомагати з вибором. Будь ввічливим, використовуй емодзі. Якщо не знаєш відповіді або клієнт хоче поговорити з людиною, скажи: 'Одну хвилинку, я покличу нашого менеджера'. Не вигадуй ціни чи наявність товарів, якщо не маєш точних даних.";

        Part systemPart = new Part();
        systemPart.text = systemPrompt;
        Content systemContent = new Content();
        systemContent.parts = List.of(systemPart);
        systemContent.role = "user";

        Part modelResponsePart = new Part();
        modelResponsePart.text = "Так, я готовий допомагати!";
        Content modelResponseContent = new Content();
        modelResponseContent.parts = List.of(modelResponsePart);
        modelResponseContent.role = "model";

        history.add(systemContent);
        history.add(modelResponseContent);
        System.out.println("AI Агент успішно ініціалізований через прямий HTTP-запит!");
    }

    public String sendMessage(String message) throws IOException {
        Part userPart = new Part();
        userPart.text = message;
        Content userContent = new Content();
        userContent.parts = List.of(userPart);
        userContent.role = "user";

        List<Content> currentHistory = new ArrayList<>(history);
        currentHistory.add(userContent);

        RequestPayload payload = new RequestPayload();
        payload.contents = currentHistory;

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

            if (responsePayload.candidates != null && !responsePayload.candidates.isEmpty()) {
                String modelText = responsePayload.candidates.get(0).content.parts.get(0).text;

                Part modelPart = new Part();
                modelPart.text = modelText;
                Content modelContent = new Content();
                modelContent.parts = List.of(modelPart);
                modelContent.role = "model";

                history.add(userContent);
                history.add(modelContent);

                return modelText;
            }
            return "Не вдалося отримати відповідь.";
        }
    }
}