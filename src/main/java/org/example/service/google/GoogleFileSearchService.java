package org.example.service.google;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.example.database.entity.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class GoogleFileSearchService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleFileSearchService.class);

    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String API_VERSION = "/v1beta";
    private static final long POLL_INITIAL_DELAY_MS = 2000;
    private static final long POLL_MAX_DELAY_MS = 30000;
    private static final int POLL_MAX_ATTEMPTS = 10;

    @Value("${gemini.api.key}")
    private String apiKey;

    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    public GoogleFileSearchService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    private String uploadFileBytes(byte[] fileData, String mimeType, String displayName) throws IOException {
        String mediaUrl = String.format("%s/upload%s/files?uploadType=media&key=%s", API_BASE_URL, API_VERSION, apiKey);
        Request request = new Request.Builder()
                .url(mediaUrl)
                .post(RequestBody.create(fileData, MediaType.get(mimeType)))
                .addHeader("X-Goog-Upload-File-Name", displayName)
                .build();

        logger.info("Крок 1/3: Завантаження {} байт (displayName: {})", fileData.length, displayName);

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = Objects.requireNonNull(response.body()).string();
            if (!response.isSuccessful()) {
                logger.error("Не вдалося завантажити файл: {} - {}", response.code(), responseBody);
                throw new IOException("Не вдалося завантажити файл: " + responseBody);
            }

            JsonObject responseObject = gson.fromJson(responseBody, JsonObject.class);
            String tempFileName = responseObject.getAsJsonObject("file").get("name").getAsString();

            logger.info("Файл завантажено, тимчасове ім'я: {}", tempFileName);
            return tempFileName;
        }
    }

    private String importFileToStore(String storeName, String tempFileName, List<Map<String, Object>> metadata) throws IOException {
        String url = String.format("%s%s/%s:importFile?key=%s", API_BASE_URL, API_VERSION, storeName, apiKey);

        JsonArray metadataArray = new JsonArray();
        for (Map<String, Object> meta : metadata) {
            JsonObject metaObj = new JsonObject();
            metaObj.addProperty("key", meta.get("key").toString());

            if (meta.get("value") instanceof String) {
                metaObj.addProperty("stringValue", meta.get("value").toString());
            } else if (meta.get("value") instanceof Number) {
                metaObj.addProperty("numericValue", (Number) meta.get("value"));
            } else if (meta.get("value") instanceof Boolean) {
                metaObj.addProperty("stringValue", meta.get("value").toString());
            }

            metadataArray.add(metaObj);
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("fileName", tempFileName);
        payload.add("customMetadata", metadataArray);

        RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(body).build();

        logger.info("Крок 2/3: Імпорт файлу {} у сховище {} з тілом: {}", tempFileName, storeName, payload.toString());

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = Objects.requireNonNull(response.body()).string();
            if (!response.isSuccessful()) {
                logger.error("Не вдалося імпортувати файл: {} - {}", response.code(), responseBody);
                throw new IOException("Не вдалося імпортувати файл: " + responseBody);
            }
            JsonObject responseObject = gson.fromJson(responseBody, JsonObject.class);
            String operationName = responseObject.get("name").getAsString();
            logger.info("Імпорт запущено, операція: {}", operationName);
            return operationName;
        }
    }

    private String pollOperation(String operationName) throws IOException, InterruptedException {
        String url = String.format("%s%s/%s?key=%s", API_BASE_URL, API_VERSION, operationName, apiKey);
        long delay = POLL_INITIAL_DELAY_MS;

        logger.info("Крок 3/3: Очікування завершення операції {}...", operationName);

        for (int i = 0; i < POLL_MAX_ATTEMPTS; i++) {
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = Objects.requireNonNull(response.body()).string();
                if (!response.isSuccessful()) {
                    throw new IOException("Помилка під час очікування операції: " + responseBody);
                }

                JsonObject responseObject = gson.fromJson(responseBody, JsonObject.class);
                if (responseObject.has("done") && responseObject.get("done").getAsBoolean()) {
                    if (responseObject.has("error")) {
                        throw new IOException("Операція завершилася з помилкою: " + responseObject.get("error").toString());
                    }
                    logger.info("Операція {} успішно завершена.", operationName);

                    if (responseObject.has("response") && responseObject.getAsJsonObject("response").has("documentName")) {
                        JsonObject responseObj = responseObject.getAsJsonObject("response");
                        String parentStoreId = responseObj.get("parent").getAsString();
                        String docId = responseObj.get("documentName").getAsString();

                        String fullDocumentName = parentStoreId + "/documents/" + docId;
                        return fullDocumentName;
                    }

                    logger.error("Операція завершена, але тіло відповіді не містить 'documentName': {}", responseBody);
                    throw new IOException("Операція завершена, але не повернула ім'я документа.");
                }
            }

            logger.info("Операція {} ще не завершена, очікуємо {} мс.", operationName, delay);
            Thread.sleep(delay);
            delay = Math.min(delay * 2, POLL_MAX_DELAY_MS);
        }
        throw new IOException("Вичерпано час очікування операції " + operationName);
    }

    public String uploadAndImportFile(String storeName, String displayName, byte[] fileData, String mimeType, List<Map<String, Object>> metadata) throws IOException, InterruptedException {
        String tempFileName = uploadFileBytes(fileData, mimeType, displayName);
        String operationName = importFileToStore(storeName, tempFileName, metadata);
        return pollOperation(operationName);
    }

    public String createStore(String clientDisplayName) throws IOException {
        String url = String.format("%s%s/fileSearchStores?key=%s", API_BASE_URL, API_VERSION, apiKey);

        String jsonPayload = gson.toJson(Map.of("display_name", clientDisplayName));

        RequestBody body = RequestBody.create(jsonPayload, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(body).build();
        logger.info("Створення FileSearchStore для '{}' з тілом: {}", clientDisplayName, jsonPayload);

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = Objects.requireNonNull(response.body()).string();
            if (!response.isSuccessful()) {
                logger.error("Не вдалося створити FileSearchStore: {}", responseBody);
                throw new IOException("Не вдалося створити FileSearchStore: " + responseBody);
            }
            JsonObject responseObject = gson.fromJson(responseBody, JsonObject.class);
            return responseObject.get("name").getAsString();
        }
    }

    /**
     * Знаходить УСІ документи (з пагінацією).
     */
    public List<String> findDocumentNames(String storeName) throws IOException {

        List<String> allDocNames = new ArrayList<>();
        String nextPageToken = null;

        do {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(String.format("%s%s/%s/documents", API_BASE_URL, API_VERSION, storeName)).newBuilder();
            urlBuilder.addQueryParameter("key", apiKey);
            urlBuilder.addQueryParameter("pageSize", "20");

            if (nextPageToken != null) {
                urlBuilder.addQueryParameter("pageToken", nextPageToken);
            }

            String url = urlBuilder.build().toString();

            Request request = new Request.Builder().url(url).get().build();
            logger.debug("Пошук документів у {} (URL: {})", storeName, url);

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = Objects.requireNonNull(response.body()).string();
                if (!response.isSuccessful()) {
                    logger.error("Не вдалося знайти документи: {}", responseBody);
                    throw new IOException("Не вдалося знайти документи: " + responseBody);
                }

                JsonObject responseObject = gson.fromJson(responseBody, JsonObject.class);

                if (responseObject.has("documents")) {
                    responseObject.getAsJsonArray("documents").forEach(doc ->
                            allDocNames.add(doc.getAsJsonObject().get("name").getAsString())
                    );
                }

                nextPageToken = responseObject.has("nextPageToken") ? responseObject.get("nextPageToken").getAsString() : null;

            }
        } while (nextPageToken != null);

        logger.debug("Успішно отримано {} імен документів.", allDocNames.size());
        return allDocNames;
    }

    /**
     * Отримує один документ, щоб ми могли прочитати його метадані.
     */
    public JsonObject getDocument(String documentName) throws IOException {
        String url = String.format("%s%s/%s?key=%s", API_BASE_URL, API_VERSION, documentName, apiKey);
        Request request = new Request.Builder().url(url).get().build();
        logger.debug("Отримання документа: {}", documentName);

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = Objects.requireNonNull(response.body()).string();
            if (!response.isSuccessful()) {
                logger.error("Не вдалося отримати документ {}: {}", documentName, responseBody);
                throw new IOException("Не вдалося отримати документ: " + responseBody);
            }
            return gson.fromJson(responseBody, JsonObject.class);
        }
    }


    /**
     * Видаляє документ.
     */
    public void deleteDocument(String documentName) throws IOException {
        // --- ФІНАЛЬНЕ ВИПРАВЛЕННЯ: ДОДАЄМО &force=true ---
        String url = String.format("%s%s/%s?key=%s&force=true", API_BASE_URL, API_VERSION, documentName, apiKey);
        Request request = new Request.Builder().url(url).delete().build();
        logger.info("Видалення документа: {}", documentName);
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 404) {
                logger.error("Не вдалося видалити документ {}: {} - {}", documentName, response.code(), Objects.requireNonNull(response.body()).string());
                throw new IOException("Не вдалося видалити документ: " + Objects.requireNonNull(response.body()).string());
            }
            logger.info("Документ {} успішно видалено.", documentName);
        }
    }

    /**
     * Допоміжний метод для сервісів, щоб отримати або створити сховище.
     */
    public String getOrCreateStoreForClient(Client client) throws IOException {
        if (client.getFileSearchStoreName() != null && !client.getFileSearchStoreName().isBlank()) {
            return client.getFileSearchStoreName();
        }

        String clientName = client.getClientName() != null ? client.getClientName() : "Unknown";
        String newStoreName = createStore("Store for " + clientName + " (ID: " + client.getId() + ")");
        client.setFileSearchStoreName(newStoreName);
        return newStoreName;
    }
}