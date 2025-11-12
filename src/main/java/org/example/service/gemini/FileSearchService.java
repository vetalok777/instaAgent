package org.example.service.gemini;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class FileSearchService {

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/";

    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    @Value("${gemini.api.key}")
    private String apiKey;

    public FileSearchService() {
        this.httpClient = new OkHttpClient.Builder()
                .callTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public String createFileSearchStore(String displayName) throws IOException {
        HttpUrl url = HttpUrl.parse(API_URL + "fileSearchStores").newBuilder()
                .addQueryParameter("key", apiKey)
                .build();

        JsonObject requestBodyJson = new JsonObject();
        requestBodyJson.addProperty("displayName", displayName);

        RequestBody body = RequestBody.create(
                gson.toJson(requestBodyJson),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response + " | " + response.body().string());
            }
            JsonObject responseBody = gson.fromJson(response.body().string(), JsonObject.class);
            return responseBody.get("name").getAsString();
        }
    }

    public String uploadFile(String fileSearchStoreName, String fileName, String content) throws IOException, InterruptedException {
        // 1. Upload the file
        HttpUrl uploadUrl = HttpUrl.parse("https://generativelanguage.googleapis.com/upload/v1beta/files").newBuilder()
                .addQueryParameter("key", apiKey)
                .build();

        File tempFile = File.createTempFile("upload-", ".txt");
        try (PrintWriter out = new PrintWriter(tempFile)) {
            out.print(content);
        }

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName,
                        RequestBody.create(tempFile, MediaType.parse("text/plain")))
                .build();

        Request uploadRequest = new Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build();

        String fileId;
        try (Response uploadResponse = httpClient.newCall(uploadRequest).execute()) {
            if (!uploadResponse.isSuccessful()) {
                throw new IOException("Unexpected code " + uploadResponse + " | " + uploadResponse.body().string());
            }
            JsonObject uploadResponseBody = gson.fromJson(uploadResponse.body().string(), JsonObject.class);
            fileId = uploadResponseBody.getAsJsonObject("file").get("name").getAsString();
        }

        // 2. Import the file to the store
        HttpUrl importUrl = HttpUrl.parse(API_URL + fileSearchStoreName + "/files").newBuilder()
                .addQueryParameter("key", apiKey)
                .build();

        JsonObject importRequestBodyJson = new JsonObject();
        importRequestBodyJson.addProperty("name", fileId);

        RequestBody importBody = RequestBody.create(
                gson.toJson(importRequestBodyJson),
                MediaType.get("application/json; charset=utf-8")
        );

        Request importRequest = new Request.Builder()
                .url(importUrl)
                .post(importBody)
                .build();

        String operationName;
        try (Response importResponse = httpClient.newCall(importRequest).execute()) {
            if (!importResponse.isSuccessful()) {
                throw new IOException("Unexpected code " + importResponse + " | " + importResponse.body().string());
            }
            JsonObject importResponseBody = gson.fromJson(importResponse.body().string(), JsonObject.class);
            operationName = importResponseBody.get("name").getAsString();
        }

        // 3. Wait for the operation to complete
        HttpUrl operationUrl = HttpUrl.parse(API_URL + operationName).newBuilder()
                .addQueryParameter("key", apiKey)
                .build();
        Request operationRequest = new Request.Builder()
                .url(operationUrl)
                .get()
                .build();

        boolean done = false;
        while (!done) {
            try (Response operationResponse = httpClient.newCall(operationRequest).execute()) {
                if (!operationResponse.isSuccessful()) {
                    throw new IOException("Unexpected code " + operationResponse + " | " + operationResponse.body().string());
                }
                JsonObject operationResponseBody = gson.fromJson(operationResponse.body().string(), JsonObject.class);
                if (operationResponseBody.has("done") && operationResponseBody.get("done").getAsBoolean()) {
                    done = true;
                } else {
                    Thread.sleep(5000); // Wait 5 seconds before checking again
                }
            }
        }

        return fileId;
    }


    public void deleteFile(String fileId) throws IOException {
        HttpUrl url = HttpUrl.parse(API_URL + fileId).newBuilder()
                .addQueryParameter("key", apiKey)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .delete()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response + " | " + response.body().string());
            }
        }
    }
}
