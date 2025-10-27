package org.example.service.gemini;

import com.google.gson.Gson;
import okhttp3.*;
import org.example.model.Content;
import org.example.model.request.EmbeddingRequest;
import org.example.model.response.EmbeddingResponse;
import org.example.model.Part;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Service for generating vector embeddings using the Google Gemini API.
 */
@Service
public class GeminiEmbeddingService {

    private static final String API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=%s";

    @Value("${gemini.api.key}")
    private String apiKey;

    private final OkHttpClient client;
    private final Gson gson = new Gson();

    public GeminiEmbeddingService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Generates a vector embedding for the given text.
     *
     * @param text The input text to be embedded.
     * @return A float array representing the vector embedding.
     * @throws IOException if the API call fails.
     */
    public float[] getEmbedding(String text) throws IOException {
        Part part = new Part();
        part.setText(text);

        Content content = new Content();
        content.setParts(List.of(part));

        EmbeddingRequest payload = new EmbeddingRequest();
        payload.setContent(content);

        String jsonPayload = gson.toJson(payload);
        RequestBody body = RequestBody.create(jsonPayload, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(String.format(API_URL_TEMPLATE, apiKey))
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected API response code " + response + " | " + Objects.requireNonNull(response.body()).string());
            }

            String responseBody = Objects.requireNonNull(response.body()).string();
            EmbeddingResponse embeddingResponse = gson.fromJson(responseBody, EmbeddingResponse.class);

            if (embeddingResponse != null && embeddingResponse.getEmbedding() != null && embeddingResponse.getEmbedding().getValues() != null) {
                List<Float> values = embeddingResponse.getEmbedding().getValues();
                float[] result = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    result[i] = values.get(i);
                }
                return result;
            }
            throw new IOException("Failed to parse embedding from API response.");
        }
    }
}