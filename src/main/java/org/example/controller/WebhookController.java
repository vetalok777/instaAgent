package org.example.controller;

import org.example.service.WebhookProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Handles incoming webhook requests from the Instagram Graph API.
 * <p>
 * This controller provides two endpoints: one for the initial webhook verification (GET)
 * and another for processing incoming message notifications (POST).
 */
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final String HUB_MODE_PARAM = "hub.mode";
    private static final String HUB_VERIFY_TOKEN_PARAM = "hub.verify_token";
    private static final String HUB_CHALLENGE_PARAM = "hub.challenge";
    private static final String SUBSCRIBE_MODE = "subscribe";

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookProcessingService processingService;
    private final String verifyToken;

    /**
     * Constructs the controller and injects the required WebhookProcessingService.
     *
     * @param processingService The service responsible for processing webhook payloads.
     * @param verifyToken       The token for webhook verification from application properties.
     */
    public WebhookController(WebhookProcessingService processingService,
                             @Value("${instagram.verify.token}") String verifyToken) {
        this.processingService = processingService;
        this.verifyToken = verifyToken;
    }

    /**
     * Handles the webhook verification challenge from Instagram.
     * <p>
     * This endpoint is called by Instagram to verify the webhook's authenticity.
     * It checks if the {@code hub.mode} is "subscribe" and if the {@code hub.verify_token}
     * matches the one configured in the application.
     *
     * @param mode      The mode from the verification request (expected to be "subscribe").
     * @param token     The verification token sent by Instagram.
     * @param challenge A random string that must be returned to Instagram to complete the verification.
     * @return A {@link ResponseEntity} containing the challenge string with an OK status if successful,
     * or a FORBIDDEN status if verification fails.
     */
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(HUB_MODE_PARAM) String mode,
            @RequestParam(HUB_VERIFY_TOKEN_PARAM) String token,
            @RequestParam(HUB_CHALLENGE_PARAM) String challenge) {

        if (SUBSCRIBE_MODE.equals(mode) && verifyToken.equals(token)) {
            log.info("Webhook successfully verified.");
            return new ResponseEntity<>(challenge, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Handles incoming message payloads from the Instagram webhook.
     * <p>
     * This endpoint receives the JSON payload from the webhook as a raw string
     * and passes it to the {@link WebhookProcessingService} for processing.
     *
     * @param payload The JSON payload from the webhook.
     * @return A {@link ResponseEntity} with an OK status to acknowledge receipt of the message.
     */
    @PostMapping
    public ResponseEntity<Void> handleMessage(@RequestBody String payload) {
        try {
            processingService.processWebhookPayload(payload);
        } catch (Exception e) {
            log.error("Error submitting webhook payload for processing.", e);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }
}