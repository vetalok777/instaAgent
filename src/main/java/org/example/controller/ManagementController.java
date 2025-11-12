package org.example.controller;

import jakarta.validation.Valid;

import org.example.database.entity.CatalogItem;
import org.example.service.CatalogManagementService;
import org.example.service.KnowledgeManagementService;
import org.springframework.beans.BeanUtils;
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
     * Endpoint for creating a new catalog item.
     *
     * @param itemDto  The CatalogItem DTO from the request body.
     * @param clientId The ID of the client to whom this item belongs.
     * @return The created CatalogItem.
     */
    @PostMapping("/catalog-items")
    public ResponseEntity<?> createCatalogItem(@Valid @RequestBody org.example.model.dto.CatalogItemDto itemDto, @RequestParam Long clientId) {
        try {
            CatalogItem newItem = new CatalogItem();
            BeanUtils.copyProperties(itemDto, newItem);
            CatalogItem createdItem = catalogManagementService.createCatalogItem(newItem, clientId);
            return ResponseEntity.ok(createdItem);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Помилка створення товару: " + e.getMessage());
        }
    }

    /**
     * Endpoint for updating an existing catalog item.
     *
     * @param itemId      The ID of the item to update.
     * @param itemDto     The updated item data.
     * @return The updated CatalogItem.
     */
    @PutMapping("/catalog-items/{itemId}")
        public ResponseEntity<?> updateCatalogItem(@PathVariable Long itemId, @Valid @RequestBody org.example.model.dto.CatalogItemDto itemDto) {
        try {
            CatalogItem updatedItem = new CatalogItem();
            BeanUtils.copyProperties(itemDto, updatedItem);
            CatalogItem result = catalogManagementService.updateCatalogItem(itemId, updatedItem);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Помилка оновлення товару: " + e.getMessage());
        }
    }

    @DeleteMapping("/catalog-items/{itemId}")
    public ResponseEntity<Void> deleteCatalogItem(@PathVariable Long itemId) {
        try {
            catalogManagementService.deleteCatalogItem(itemId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
