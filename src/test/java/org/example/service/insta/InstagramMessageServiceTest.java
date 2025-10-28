package org.example.service.insta;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.example.service.InstagramMessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class InstagramMessageServiceTest {

    private MockWebServer mockWebServer;
    private InstagramMessageService messageService;
    private final Gson gson = new Gson();

    private static final String TEST_ACCESS_TOKEN = "test-access-token";
    private static final String TEST_RECIPIENT_ID = "user-123";
    private static final String TEST_PAGE_ID = "page-456";

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        messageService = new InstagramMessageService();

        // Inject values for @Value fields using reflection
        String baseUrl = mockWebServer.url("").toString();
        ReflectionTestUtils.setField(messageService, "graphApiUrl", baseUrl);
        ReflectionTestUtils.setField(messageService, "pageId", TEST_PAGE_ID);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    public void sendReply_withShortMessage_sendsOneRequest() throws InterruptedException {
        // Given
        String shortMessage = "Hello, this is a short reply.";
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        // When
        messageService.sendReply(TEST_ACCESS_TOKEN, TEST_RECIPIENT_ID, shortMessage);

        // Then
        assertEquals(1, mockWebServer.getRequestCount());
        RecordedRequest request = mockWebServer.takeRequest();

        assertEquals("/" + TEST_PAGE_ID + "/messages", request.getPath());
        assertEquals("POST", request.getMethod());

        String requestBody = request.getBody().readUtf8();
        JsonObject bodyJson = gson.fromJson(requestBody, JsonObject.class);

        assertEquals(TEST_RECIPIENT_ID, bodyJson.getAsJsonObject("recipient").get("id").getAsString());
        assertEquals(shortMessage, bodyJson.getAsJsonObject("message").get("text").getAsString());
        assertEquals("RESPONSE", bodyJson.get("messaging_type").getAsString());
        assertEquals(TEST_ACCESS_TOKEN, bodyJson.get("access_token").getAsString());
    }

    @Test
    public void sendReply_withLongMessage_sendsMultipleRequests() throws InterruptedException {
        // Given
        String longMessage = "a".repeat(1500);
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)); // For first part
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)); // For second part

        // When
        messageService.sendReply(TEST_ACCESS_TOKEN, TEST_RECIPIENT_ID, longMessage);

        // Then
        assertEquals(2, mockWebServer.getRequestCount());

        // Verify first request
        RecordedRequest request1 = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request1);
        String body1 = request1.getBody().readUtf8();
        JsonObject json1 = gson.fromJson(body1, JsonObject.class);
        assertEquals("a".repeat(990), json1.getAsJsonObject("message").get("text").getAsString());

        // Verify second request
        RecordedRequest request2 = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request2);
        String body2 = request2.getBody().readUtf8();
        JsonObject json2 = gson.fromJson(body2, JsonObject.class);
        assertEquals("a".repeat(1500 - 990), json2.getAsJsonObject("message").get("text").getAsString());
    }

    @Test
    public void sendReply_whenApiFails_doesNotThrowException() {
        // Given
        String message = "This will fail.";
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

        // When & Then
        // The method should catch the IOException and log it, not re-throw it.
        assertDoesNotThrow(() -> messageService.sendReply(TEST_ACCESS_TOKEN, TEST_RECIPIENT_ID, message));
        assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    public void getShortcodeFromAssetId_success() throws InterruptedException {
        // Given
        String assetId = "asset-789";
        String expectedShortcode = "CqXyZabc";
        String jsonResponse = "{\"shortcode\": \"" + expectedShortcode + "\", \"id\": \"" + assetId + "\"}";
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(jsonResponse));

        // When
        String actualShortcode = messageService.getShortcodeFromAssetId(TEST_ACCESS_TOKEN, assetId);

        // Then
        assertEquals(expectedShortcode, actualShortcode);

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("GET", request.getMethod());
        assertNotNull(request.getPath());
        assertTrue(request.getPath().contains("/" + assetId));
        assertTrue(request.getPath().contains("fields=shortcode"));
        assertTrue(request.getPath().contains("access_token=" + TEST_ACCESS_TOKEN));
    }

    @Test
    public void getShortcodeFromAssetId_whenApiFails_returnsNull() {
        // Given
        String assetId = "asset-789";
        mockWebServer.enqueue(new MockResponse().setResponseCode(404).setBody("Not Found"));

        // When
        String actualShortcode = messageService.getShortcodeFromAssetId(TEST_ACCESS_TOKEN, assetId);

        // Then
        assertNull(actualShortcode);
    }

    @Test
    public void getShortcodeFromAssetId_whenResponseIsMissingShortcode_returnsNull() {
        // Given
        String assetId = "asset-789";
        String jsonResponse = "{\"id\": \"" + assetId + "\"}"; // No 'shortcode' field
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(jsonResponse));

        // When
        String actualShortcode = messageService.getShortcodeFromAssetId(TEST_ACCESS_TOKEN, assetId);

        // Then
        assertNull(actualShortcode);
    }
}