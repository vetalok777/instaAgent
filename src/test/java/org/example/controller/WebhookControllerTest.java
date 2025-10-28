package org.example.controller;

import org.example.service.WebhookProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock
    private WebhookProcessingService processingService;

    @InjectMocks
    private WebhookController webhookController;

    private final String TEST_VERIFY_TOKEN = "my_secret_token";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(webhookController, "verifyToken", TEST_VERIFY_TOKEN);
    }

    @Test
    void verifyWebhook_withCorrectTokenAndMode_returnsChallenge() {
        // Given
        String mode = "subscribe";
        String challenge = "challenge_accepted";

        // When
        ResponseEntity<String> response = webhookController.verifyWebhook(mode, TEST_VERIFY_TOKEN, challenge);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(challenge, response.getBody());
    }

    @Test
    void verifyWebhook_withIncorrectMode_returnsForbidden() {
        // Given
        String mode = "unsubscribe"; // Неправильний режим
        String challenge = "challenge_ignored";

        // When
        ResponseEntity<String> response = webhookController.verifyWebhook(mode, TEST_VERIFY_TOKEN, challenge);

        // Then
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void verifyWebhook_withIncorrectToken_returnsForbidden() {
        // Given
        String mode = "subscribe";
        String token = "wrong_token"; // Неправильний токен
        String challenge = "challenge_ignored";

        // When
        ResponseEntity<String> response = webhookController.verifyWebhook(mode, token, challenge);

        // Then
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void handleMessage_callsProcessingServiceAndReturnsOk() {
        // Given
        String payload = "{\"object\":\"instagram\",\"entry\":[{\"id\":\"<PAGE_ID>\",\"time\":1515114666,\"messaging\":[{\"sender\":{\"id\":\"<PSID>\"},\"recipient\":{\"id\":\"<PAGE_ID>\"},\"message\":{\"text\":\"Hello, world!\"}}]}]}";

        // When
        ResponseEntity<Void> response = webhookController.handleMessage(payload);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(processingService, times(1)).processWebhookPayload(payload);
    }
}