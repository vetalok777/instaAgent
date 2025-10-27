package org.example.controller;

import org.example.database.entity.CatalogItem;
import org.example.database.entity.Knowledge;
import org.example.service.CatalogManagementService;
import org.example.service.KnowledgeManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Controller for handling management tasks, such as knowledge base updates.
 */
@RestController
@RequestMapping("/api/v1/management")
public class ManagementController {

    private final KnowledgeManagementService knowledgeManagementService;
    private final CatalogManagementService catalogManagementService;

    public ManagementController(KnowledgeManagementService knowledgeManagementService,
                                CatalogManagementService catalogManagementService) {
        this.catalogManagementService = catalogManagementService;
        this.knowledgeManagementService = knowledgeManagementService;
    }

    /**
     * Endpoint for uploading a text file to populate a client's knowledge base.
     * The file is processed paragraph by paragraph.
     *
     * @param file     The text file to upload.
     * @param clientId The ID of the client to associate the knowledge with.
     * @return A response indicating the result of the operation.
     */
    @PostMapping("/knowledge/upload")
    public ResponseEntity<String> uploadKnowledgeFile(@RequestParam("file") MultipartFile file, @RequestParam("clientId") Long clientId) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Файл порожній!");
        }
        try {
            knowledgeManagementService.processAndStoreKnowledge(clientId, file.getInputStream());
            return ResponseEntity.ok("Базу знань для клієнта ID " + clientId + " успішно оновлено.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Помилка під час обробки файлу: " + e.getMessage());
        }
    }

    /**
     * Endpoint for retrieving all general knowledge entries for a specific client.
     * General knowledge entries are those not linked to a specific catalog item.
     *
     * @param clientId The ID of the client.
     * @return A list of general {@link Knowledge} entries.
     */
    @GetMapping("/knowledge/general")
    public ResponseEntity<?> getGeneralKnowledge(@RequestParam Long clientId) {
        try {
            List<Knowledge> generalKnowledge = knowledgeManagementService.getGeneralKnowledge(clientId);
            return ResponseEntity.ok(generalKnowledge);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Помилка отримання загальних знань: " + e.getMessage());
        }
    }

    /**
     * Endpoint for updating a specific general knowledge entry.
     *
     * @param knowledgeId The ID of the knowledge entry to update.
     * @param newContent  The new text content for the knowledge entry.
     * @return A response indicating the result of the operation.
     */
    @PutMapping("/knowledge/general/{knowledgeId}")
    public ResponseEntity<String> updateGeneralKnowledge(@PathVariable Long knowledgeId, @RequestBody String newContent) {
        try {
            knowledgeManagementService.updateGeneralKnowledge(knowledgeId, newContent);
            return ResponseEntity.ok("Запис загальних знань ID " + knowledgeId + " успішно оновлено.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Помилка оновлення загальних знань: " + e.getMessage());
        }
    }

    /**
     * Endpoint for creating a new catalog item.
     *
     * @param item     The CatalogItem object from the request body.
     * @param clientId The ID of the client to whom this item belongs.
     * @return The created CatalogItem.
     */
    @PostMapping("/catalog-items")
    public ResponseEntity<?> createCatalogItem(@RequestBody CatalogItem item, @RequestParam Long clientId) {
        try {
            CatalogItem createdItem = catalogManagementService.createCatalogItem(item, clientId);
            return ResponseEntity.ok(createdItem);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Помилка створення товару: " + e.getMessage());
        }
    }

    /**
     * Endpoint for updating an existing catalog item.
     *
     * @param itemId      The ID of the item to update.
     * @param updatedItem The updated item data.
     * @return The updated CatalogItem.
     */
    @PutMapping("/catalog-items/{itemId}")
    public ResponseEntity<?> updateCatalogItem(@PathVariable Long itemId, @RequestBody CatalogItem updatedItem) {
        try {
            CatalogItem result = catalogManagementService.updateCatalogItem(itemId, updatedItem);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Помилка оновлення товару: " + e.getMessage());
        }
    }

    @DeleteMapping("/catalog-items/{itemId}")
    public ResponseEntity<Void> deleteCatalogItem(@PathVariable Long itemId) {
        catalogManagementService.deleteCatalogItem(itemId);
        return ResponseEntity.noContent().build();
    }
}