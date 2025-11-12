package org.example.service;

import org.example.database.entity.CatalogItem;
import org.example.database.entity.Client;
import org.example.database.repository.CatalogItemRepository;
import org.example.database.repository.ClientRepository;
import org.example.service.gemini.FileSearchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages the lifecycle of catalog items, ensuring synchronization
 * between the structured catalog and the File Search store.
 */
@Service
public class CatalogManagementService {

    private final CatalogItemRepository catalogItemRepository;
    private final ClientRepository clientRepository;
    private final FileSearchService fileSearchService;

    public CatalogManagementService(CatalogItemRepository catalogItemRepository,
                                    ClientRepository clientRepository,
                                    FileSearchService fileSearchService) {
        this.catalogItemRepository = catalogItemRepository;
        this.clientRepository = clientRepository;
        this.fileSearchService = fileSearchService;
    }

    /**
     * Creates a new catalog item and uploads its data to the File Search store.
     */
    @Transactional
    public CatalogItem createCatalogItem(CatalogItem item, Long clientId) throws IOException, InterruptedException {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клієнт з ID " + clientId + " не знайдений."));
        item.setClient(client);

        if (client.getFileSearchStoreId() == null || client.getFileSearchStoreId().isEmpty()) {
            String storeId = fileSearchService.createFileSearchStore("store_for_client_" + clientId);
            client.setFileSearchStoreId(storeId);
            clientRepository.save(client);
        }

        CatalogItem savedItem = catalogItemRepository.save(item);
        synchronizeKnowledge(savedItem);
        return savedItem;
    }

    /**
     * Updates an existing catalog item and re-uploads its data to the File Search store.
     */
    @Transactional
    public CatalogItem updateCatalogItem(Long itemId, CatalogItem updatedItemData) throws IOException, InterruptedException {
        CatalogItem existingItem = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Товар з ID " + itemId + " не знайдено."));

        if (existingItem.getFileId() != null && !existingItem.getFileId().isEmpty()) {
            try {
                fileSearchService.deleteFile(existingItem.getFileId());
            } catch (IOException e) {
                // Log the error, but continue with the update
                System.err.println("Error deleting old file: " + e.getMessage());
            }
        }

        existingItem.setName(updatedItemData.getName());
        existingItem.setDescription(updatedItemData.getDescription());
        existingItem.setPrice(updatedItemData.getPrice());
        existingItem.setQuantity(updatedItemData.getQuantity());
        existingItem.setAttributes(updatedItemData.getAttributes());

        CatalogItem savedItem = catalogItemRepository.save(existingItem);
        synchronizeKnowledge(savedItem);
        return savedItem;
    }

    /**
     * Deletes a catalog item and its corresponding file from the File Search store.
     */
    @Transactional
    public void deleteCatalogItem(Long itemId) throws IOException {
        CatalogItem item = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Товар з ID " + itemId + " не знайдено."));

        if (item.getFileId() != null && !item.getFileId().isEmpty()) {
            fileSearchService.deleteFile(item.getFileId());
        }
        catalogItemRepository.deleteById(itemId);
    }

    /**
     * Synchronizes the File Search store for a given catalog item.
     */
    private void synchronizeKnowledge(CatalogItem item) throws IOException, InterruptedException {
        String knowledgeText = generateKnowledgeText(item);
        String fileId = fileSearchService.uploadFile(item.getClient().getFileSearchStoreId(), "item-" + item.getId() + ".txt", knowledgeText);
        item.setFileId(fileId);
        catalogItemRepository.save(item);
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
