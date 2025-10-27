package org.example.service;

import org.example.database.entity.CatalogItem;
import org.example.database.entity.Client;
import org.example.database.repository.CatalogItemRepository;
import org.example.database.repository.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages the lifecycle of catalog items, ensuring synchronization
 * between the structured catalog and the vectorized knowledge base.
 */
@Service
public class CatalogManagementService {

    private final CatalogItemRepository catalogItemRepository;
    private final ClientRepository clientRepository;
    private final RAGService ragService;

    public CatalogManagementService(CatalogItemRepository catalogItemRepository,
                                    ClientRepository clientRepository,
                                    RAGService ragService) {
        this.catalogItemRepository = catalogItemRepository;
        this.clientRepository = clientRepository;
        this.ragService = ragService;
    }

    /**
     * Creates a new catalog item and generates its corresponding knowledge base embedding.
     */
    @Transactional
    public CatalogItem createCatalogItem(CatalogItem item, Long clientId) throws IOException {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клієнт з ID " + clientId + " не знайдений."));
        item.setClient(client);

        CatalogItem savedItem = catalogItemRepository.save(item);
        synchronizeKnowledge(savedItem);
        return savedItem;
    }

    /**
     * Updates an existing catalog item and re-generates its knowledge base embedding.
     */
    @Transactional
    public CatalogItem updateCatalogItem(Long itemId, CatalogItem updatedItemData) throws IOException {
        CatalogItem existingItem = catalogItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Товар з ID " + itemId + " не знайдено."));

        // Оновлюємо поля
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
     * Deletes a catalog item and all associated knowledge base entries.
     */
    @Transactional
    public void deleteCatalogItem(Long itemId) {
        if (!catalogItemRepository.existsById(itemId)) {
            throw new IllegalArgumentException("Товар з ID " + itemId + " не знайдено.");
        }
        // Видалення векторних знань, пов'язаних з цим товаром
        ragService.deleteKnowledgeForCatalogItem(itemId);
        // Видалення самого товару
        catalogItemRepository.deleteById(itemId);
    }

    /**
     * Synchronizes the knowledge base for a given catalog item.
     * It first deletes old entries and then creates a new one based on the current item state.
     */
    private void synchronizeKnowledge(CatalogItem item) throws IOException {
        // 1. Видалити старі знання, пов'язані з цим товаром
        ragService.deleteKnowledgeForCatalogItem(item.getId());

        // 2. Створити новий текстовий опис на основі актуальних даних
        String knowledgeText = generateKnowledgeText(item);

        // 3. Створити новий векторний запис у базі знань
        ragService.createAndStoreEmbedding(item.getClient(), knowledgeText, item);
    }

    private String generateKnowledgeText(CatalogItem item) {
        String attributes = item.getAttributes().entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(", "));

        return String.format("Товар: %s (Артикул: %s). Опис: %s. Ціна: %s грн. В наявності: %d шт. Характеристики: %s.",
                item.getName(), item.getSku(), item.getDescription(), item.getPrice(), item.getQuantity(), attributes);
    }
}