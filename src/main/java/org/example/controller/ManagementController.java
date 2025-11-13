package org.example.controller;

import jakarta.validation.Valid;

import org.example.database.entity.CatalogItem;
import org.example.service.CatalogManagementService;
import org.example.service.KnowledgeManagementService;
import org.springframework.beans.BeanUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


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
     * Завантажує файл (PDF/TXT) із загальними знаннями.
     */
    @PostMapping("/knowledge/upload")
    public ResponseEntity<String> uploadKnowledgeFile(@RequestParam("file") MultipartFile file, @RequestParam("clientId") Long clientId) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Файл порожній!");
        }
        try {
            knowledgeManagementService.processAndStoreKnowledge(
                    clientId,
                    file.getInputStream(),
                    file.getOriginalFilename(),
                    file.getContentType() != null ? file.getContentType() : "text/plain"
            );
            return ResponseEntity.ok("Базу знань (загальну) для клієнта ID " + clientId + " успішно оновлено.");
        } catch (Exception e) {
            logger.error("Помилка під час завантаження файлу знань", e);
            return ResponseEntity.internalServerError().body("Помилка під час обробки файлу: " + e.getMessage());
        }
    }

    /**
     * Створює новий товар в каталозі.
     */
    @PostMapping("/catalog-items")
    public ResponseEntity<?> createCatalogItem(@Valid @RequestBody org.example.model.dto.CatalogItemDto itemDto, @RequestParam Long clientId) {
        try {
            CatalogItem newItem = new CatalogItem();
            BeanUtils.copyProperties(itemDto, newItem);
            CatalogItem createdItem = catalogManagementService.createCatalogItem(newItem, clientId);
            return ResponseEntity.ok(createdItem);
        } catch (Exception e) {
            logger.error("Помилка створення товару", e);
            return ResponseEntity.internalServerError().body("Помилка створення товару: " + e.getMessage());
        }
    }

    /**
     * Оновлює існуючий товар в каталозі.
     */
    @PutMapping("/catalog-items/{itemId}")
    public ResponseEntity<?> updateCatalogItem(@PathVariable Long itemId, @Valid @RequestBody org.example.model.dto.CatalogItemDto itemDto) {
        try {
            CatalogItem updatedItem = new CatalogItem();
            BeanUtils.copyProperties(itemDto, updatedItem);
            CatalogItem result = catalogManagementService.updateCatalogItem(itemId, updatedItem);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Помилка оновлення товару", e);
            return ResponseEntity.internalServerError().body("Помилка оновлення товару: " + e.getMessage());
        }
    }

    /**
     * Видаляє товар з каталогу.
     */
    @DeleteMapping("/catalog-items/{itemId}")
    public ResponseEntity<Void> deleteCatalogItem(@PathVariable Long itemId) {
        try {
            catalogManagementService.deleteCatalogItem(itemId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Помилка видалення товару", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // Додаємо статичний логгер, оскільки ми в контролері
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ManagementController.class);
}