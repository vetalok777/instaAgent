package org.example.controller;

import org.example.service.WebhookProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private final WebhookProcessingService processingService;

    @Value("${instagram.verify.token}")
    private String verifyToken;

    @Autowired
    public WebhookController(WebhookProcessingService processingService) {
        this.processingService = processingService;
    }

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

    @PostMapping
    public ResponseEntity<Void> handleMessage(@RequestBody String payload) {
        processingService.processWebhookPayload(payload);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}