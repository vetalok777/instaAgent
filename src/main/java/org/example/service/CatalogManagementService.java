package org.example.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.example.database.entity.CatalogItem;
import org.example.database.entity.Client;
import org.example.database.repository.CatalogItemRepository;
import org.example.database.repository.ClientRepository;
import org.example.service.google.GoogleFileSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CatalogManagementService {

    private static final Logger logger = LoggerFactory.getLogger(CatalogManagementService.class);

    private final CatalogItemRepository catalogItemRepository;
    private final ClientRepository clientRepository;
    private final GoogleFileSearchService fileSearchService;

    private CatalogManagementService self;
    @Autowired
    public void setSelf(@Lazy CatalogManagementService self) {
        this.self = self;
    }

    public CatalogManagementService(CatalogItemRepository catalogItemRepository,
                                    ClientRepository clientRepository,
                                    GoogleFileSearchService fileSearchService) {
        this.catalogItemRepository = catalogItemRepository;
        this.clientRepository = clientRepository;
        this.fileSearchService = fileSearchService;
    }

    @Transactional
    public CatalogItem createCatalogItem(CatalogItem item, Long clientId) throws IOException, InterruptedException {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клієнт з ID " + clientId + " не знайдений."));
        item.setClient(client);
        item.setDocVersion(0);

        synchronizeKnowledge(item, true);

        return catalogItemRepository.save(item);
    }

    @Transactional
    public CatalogItem updateCatalogItem(Long itemId, CatalogItem updatedItemData) throws IOException, InterruptedException {
        CatalogItem existingItem = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Товар з ID " + itemId + " не знайдено."));

        existingItem.setName(updatedItemData.getName());
        existingItem.setDescription(updatedItemData.getDescription());
        existingItem.setPrice(updatedItemData.getPrice());
        existingItem.setQuantity(updatedItemData.getQuantity());
        existingItem.setAttributes(updatedItemData.getAttributes());

        synchronizeKnowledge(existingItem, false);

        return catalogItemRepository.save(existingItem);
    }

    @Transactional
    public void deleteCatalogItem(Long itemId) {
        CatalogItem item = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Товар з ID " + itemId + " не знайдено."));

        self.deleteOldDocumentsAsync(item.getClient().getFileSearchStoreName(), item.getSku());

        catalogItemRepository.deleteById(itemId);
    }

    private void synchronizeKnowledge(CatalogItem item, boolean isNewItem) throws IOException, InterruptedException {
        Client client = item.getClient();
        String storeName = fileSearchService.getOrCreateStoreForClient(client);
        if (client.getFileSearchStoreName() == null || !client.getFileSearchStoreName().equals(storeName)) {
            clientRepository.save(client);
        }

        int newVersion = isNewItem ? 1 : item.getDocVersion() + 1;
        item.setDocVersion(newVersion);

        String deterministicDisplayName = String.format("sku-%s-v%d.txt", item.getSku(), newVersion);

        List<Map<String, Object>> metadata = List.of(
                Map.of("key", "doctype", "value", "product"),
                Map.of("key", "sku", "value", item.getSku()),
                Map.of("key", "version", "value", newVersion),
                Map.of("key", "is_active", "value", true),
                Map.of("key", "updated_at_epoch", "value", Instant.now().getEpochSecond()),
                Map.of("key", "client_id", "value", client.getId()),
                Map.of("key", "in_stock", "value", item.getQuantity() > 0)
        );

        String knowledgeText = generateKnowledgeText(item);
        byte[] fileData = knowledgeText.getBytes(StandardCharsets.UTF_8);

        logger.info("Запуск синхронізації для {} (версія {})...", deterministicDisplayName, newVersion);
        String newDocumentName = fileSearchService.uploadAndImportFile(
                storeName, deterministicDisplayName, fileData, "text/plain", metadata);

        logger.info("Успішно синхронізовано {}, новий документ: {}", deterministicDisplayName, newDocumentName);

        if (!isNewItem) {
            self.deleteOldDocumentsAsync(storeName, item.getSku(), newVersion);
        }
    }


    // --- МЕТОД ПОВНІСТЮ ПЕРЕПИСАНО (Обхідний шлях) ---
    @Async("taskExecutor")
    public void deleteOldDocumentsAsync(String storeName, String sku, int newVersion) {
        if (storeName == null || storeName.isBlank()) return;

        logger.info("ASYNC (Обхід): Пошук старих документів для видалення (SKU: {}, Version < {})", sku, newVersion);

        try {
            // 1. Отримуємо УСІ документи (без фільтра)
            List<String> allDocNames = fileSearchService.findDocumentNames(storeName);
            logger.info("ASYNC (Обхід): Знайдено {} документів у сховищі. Початок ручної фільтрації...", allDocNames.size());

            int deletedCount = 0;
            for (String docName : allDocNames) {
                try {
                    // 2. Отримуємо метадані кожного
                    JsonObject doc = fileSearchService.getDocument(docName);
                    if (!doc.has("customMetadata")) continue;

                    JsonArray metadata = doc.getAsJsonArray("customMetadata");

                    // 3. Перевіряємо метадані вручну
                    if (isDocumentMatch(metadata, sku, newVersion)) {
                        logger.info("ASYNC (Обхід): Знайдено відповідність: {}. Видалення...", docName);
                        fileSearchService.deleteDocument(docName);
                        deletedCount++;
                    }
                } catch (IOException e) {
                    logger.error("ASYNC (Обхід): Не вдалося обробити/видалити документ {}: {}", docName, e.getMessage());
                }
            }
            logger.info("ASYNC (Обхід): Завершено. Видалено {} старих документів для SKU: {}", deletedCount, sku);

        } catch (IOException e) {
            logger.error("ASYNC (Обхід): Помилка під час видалення старих документів для SKU {}: {}", sku, e.getMessage(), e);
        }
    }

    // --- НОВИЙ ДОПОМІЖНИЙ МЕТОД ---
    private boolean isDocumentMatch(JsonArray metadata, String targetSku, int newVersion) {
        String docSku = null;
        long docVersion = -1;
        String docType = null;

        for (JsonElement metaElement : metadata) {
            JsonObject meta = metaElement.getAsJsonObject();
            String key = meta.get("key").getAsString();

            if ("sku".equals(key) && meta.has("stringValue")) {
                docSku = meta.get("stringValue").getAsString();
            }
            if ("version".equals(key) && meta.has("numericValue")) {
                docVersion = meta.get("numericValue").getAsLong();
            }
            if ("doctype".equals(key) && meta.has("stringValue")) {
                docType = meta.get("stringValue").getAsString();
            }
        }

        // Ми шукаємо ТІЛЬКИ товари
        if (!"product".equals(docType)) {
            return false;
        }

        // Якщо SKU не збігається, це не наш товар
        if (!targetSku.equals(docSku)) {
            return false;
        }

        // Якщо `newVersion == 0` (повне видалення, напр. DELETE), ми видаляємо всі версії
        if (newVersion == 0) {
            return true;
        }

        // Інакше видаляємо, лише якщо версія СТАРА
        return docVersion < newVersion;
    }
    // --- КІНЕЦЬ НОВОГО МЕТОДУ ---

    @Async("taskExecutor")
    public void deleteOldDocumentsAsync(String storeName, String sku) {
        deleteOldDocumentsAsync(storeName, sku, 0);
    }

    private String generateKnowledgeText(CatalogItem item) {
        Map<String, String> attributesMap = item.getAttributes();
        if (attributesMap == null) {
            attributesMap = Map.of();
            item.setAttributes(attributesMap);
        }

        String attributes = attributesMap.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(", "));

        String baseText = String.format("Товар: %s (Артикул: %s). Опис: %s. Ціна: %s грн. В наявності: %d шт.",
                item.getName(), item.getSku(), item.getDescription(), item.getPrice(), item.getQuantity());

        if (!attributes.isEmpty()) {
            return baseText + " Характеристики: " + attributes + ".";
        }

        return baseText;
    }
}