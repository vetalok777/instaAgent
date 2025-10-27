package org.example.controller;

import org.example.service.WebhookProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final WebhookProcessingService processingService;

    @Value("${instagram.verify.token}")
    private String verifyToken;

    /**
     * Constructs the controller and injects the required WebhookProcessingService.
     *
     * @param processingService The service responsible for processing webhook payloads.
     */
    @Autowired
    public WebhookController(WebhookProcessingService processingService) {
        this.processingService = processingService;
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
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            System.out.println("WEBHOOK_VERIFIED");
            return new ResponseEntity<>(challenge, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Handles incoming message payloads from the Instagram webhook.
     * <p>
     * This endpoint receives the JSON payload from the webhook as a raw string
     * and passes it to the {@link WebhookProcessingService} for asynchronous processing.
     *
     * @param payload The JSON payload from the webhook.
     * @return A {@link ResponseEntity} with an OK status to acknowledge receipt of the message.
     */
    @PostMapping
    public ResponseEntity<Void> handleMessage(@RequestBody String payload) {
        processingService.processWebhookPayload(payload);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}