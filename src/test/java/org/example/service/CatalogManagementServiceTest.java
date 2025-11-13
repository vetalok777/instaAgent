package org.example.service;

import org.example.database.entity.CatalogItem;
import org.example.database.entity.Client;
import org.example.database.repository.CatalogItemRepository;
import org.example.database.repository.ClientRepository;
import org.example.service.google.GoogleFileSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogManagementServiceTest {

    @Mock
    private CatalogItemRepository catalogItemRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private GoogleFileSearchService fileSearchService; // ЗМІНЕНО

    @Spy // Використовуємо Spy, щоб мокати асинхронний метод
    @InjectMocks
    private CatalogManagementService catalogManagementService;

    private Method generateKnowledgeTextMethod;
    private Client client;
    private String storeName = "fileSearchStores/123";

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        // Моки тепер інжектуються через @Mock та @InjectMocks

        generateKnowledgeTextMethod = CatalogManagementService.class
                .getDeclaredMethod("generateKnowledgeText", CatalogItem.class);
        generateKnowledgeTextMethod.setAccessible(true);

        client = buildClient();
    }

    @Test
    void createCatalogItem_assignsClientPersistsAndSynchronizesKnowledge() throws Exception {
        // Arrange
        CatalogItem item = buildCatalogItem();
        item.setAttributes(Map.of("Колір", "Чорний"));

        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(catalogItemRepository.save(any(CatalogItem.class))).thenReturn(item);
        when(fileSearchService.getOrCreateStoreForClient(client)).thenReturn(storeName);
        when(fileSearchService.uploadAndImportFile(anyString(), anyString(), any(), anyString(), anyList()))
                .thenReturn(storeName + "/documents/doc-v1");

        ArgumentCaptor<List<Map<String, Object>>> metadataCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        CatalogItem result = catalogManagementService.createCatalogItem(item, 1L);

        // Assert
        verify(catalogItemRepository).save(item);
        assertEquals(client, result.getClient());
        assertEquals(1, result.getDocVersion()); // Перевіряємо, що версія 1

        // Перевіряємо виклик Google
        verify(fileSearchService).uploadAndImportFile(
                eq(storeName),
                eq("sku-SKU123-v1.txt"),
                any(),
                eq("text/plain"),
                metadataCaptor.capture()
        );

        // Перевіряємо метадані
        List<Map<String, Object>> metadata = metadataCaptor.getValue();
        assertTrue(metadata.contains(Map.of("key", "sku", "value", "SKU123")));
        assertTrue(metadata.contains(Map.of("key", "version", "value", 1)));
        assertTrue(metadata.contains(Map.of("key", "is_active", "value", true)));

        // Перевіряємо, що асинхронне видалення НЕ викликалось для нового товару
        verify(catalogManagementService, never()).deleteOldDocumentsAsync(anyString(), anyString(), anyInt());
    }

    @Test
    void updateCatalogItem_updatesFieldsAndSynchronizesKnowledge() throws Exception {
        // Arrange
        CatalogItem existingItem = buildCatalogItem();
        existingItem.setId(5L);
        existingItem.setClient(client);
        existingItem.setDocVersion(1); // Попередня версія

        CatalogItem updatedItemData = new CatalogItem();
        updatedItemData.setName("Updated name");
        updatedItemData.setPrice(new BigDecimal("149.49"));
        updatedItemData.setQuantity(8);
        updatedItemData.setAttributes(Map.of("Колір", "Білий"));

        when(catalogItemRepository.findById(5L)).thenReturn(Optional.of(existingItem));
        when(catalogItemRepository.save(existingItem)).thenReturn(existingItem);
        when(fileSearchService.getOrCreateStoreForClient(client)).thenReturn(storeName);

        // Мокаємо асинхронний метод
        doNothing().when(catalogManagementService).deleteOldDocumentsAsync(anyString(), anyString(), anyInt());

        when(fileSearchService.uploadAndImportFile(anyString(), anyString(), any(), anyString(), anyList()))
                .thenReturn(storeName + "/documents/doc-v2");

        ArgumentCaptor<List<Map<String, Object>>> metadataCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        CatalogItem result = catalogManagementService.updateCatalogItem(5L, updatedItemData);

        // Assert
        assertEquals(2, result.getDocVersion()); // Версія оновилась
        assertEquals("Updated name", result.getName());
        assertEquals(new BigDecimal("149.49"), result.getPrice());

        // Перевіряємо виклик Google
        verify(fileSearchService).uploadAndImportFile(
                eq(storeName),
                eq("sku-SKU123-v2.txt"),
                any(),
                eq("text/plain"),
                metadataCaptor.capture()
        );

        // Перевіряємо метадані
        List<Map<String, Object>> metadata = metadataCaptor.getValue();
        assertTrue(metadata.contains(Map.of("key", "version", "value", 2)));
        assertTrue(metadata.contains(Map.of("key", "is_active", "value", true)));

        // Перевіряємо, що асинхронне видалення БУЛО викликане для v < 2
        verify(catalogManagementService).deleteOldDocumentsAsync(storeName, "SKU123", 2);
    }

    @Test
    void deleteCatalogItem_removesKnowledgeAndRepositoryEntry() {
        // Arrange
        CatalogItem item = buildCatalogItem();
        item.setId(7L);
        item.setClient(client);
        client.setFileSearchStoreName(storeName);

        when(catalogItemRepository.findById(7L)).thenReturn(Optional.of(item));
        doNothing().when(catalogManagementService).deleteOldDocumentsAsync(anyString(), anyString());

        // Act
        catalogManagementService.deleteCatalogItem(7L);

        // Assert
        // Перевіряємо, що викликалось повне асинхронне видалення (без версії)
        verify(catalogManagementService).deleteOldDocumentsAsync(storeName, "SKU123");
        verify(catalogItemRepository).deleteById(7L);
    }

    @Test
    void deleteCatalogItem_whenItemMissingThrowsIllegalArgumentException() {
        // Arrange
        when(catalogItemRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> catalogManagementService.deleteCatalogItem(99L));

        verify(catalogManagementService, never()).deleteOldDocumentsAsync(anyString(), anyString());
        verify(catalogItemRepository, never()).deleteById(anyLong());
    }

    // Тести generateKnowledgeText залишаються без змін, оскільки метод не змінився
    @Test
    void generateKnowledgeText_withAttributes_includesFormattedAttributes() throws InvocationTargetException, IllegalAccessException {
        CatalogItem item = buildCatalogItem();
        item.setAttributes(Map.of("Колір", "Чорний", "Розмір", "L"));

        String knowledgeText = (String) generateKnowledgeTextMethod.invoke(catalogManagementService, item);

        String baseText = "Товар: Test name (Артикул: SKU123). Опис: Test description. Ціна: 199.99 грн. В наявності: 5 шт.";
        assertTrue(knowledgeText.startsWith(baseText));
        assertTrue(knowledgeText.contains("Характеристики:"));
        assertTrue(knowledgeText.contains("Колір: Чорний"));
        assertTrue(knowledgeText.contains("Розмір: L"));
    }

    @Test
    void generateKnowledgeText_withoutAttributes_omitsAttributesSectionAndInitializesMap() throws InvocationTargetException, IllegalAccessException {
        CatalogItem item = buildCatalogItem();
        item.setAttributes(null);

        String knowledgeText = (String) generateKnowledgeTextMethod.invoke(catalogManagementService, item);

        assertEquals("Товар: Test name (Артикул: SKU123). Опис: Test description. Ціна: 199.99 грн. В наявності: 5 шт.",
                knowledgeText);
        assertNotNull(item.getAttributes());
        assertTrue(item.getAttributes().isEmpty());
    }
    // --- Кінець незмінних тестів ---

    private CatalogItem buildCatalogItem() {
        CatalogItem item = new CatalogItem();
        item.setName("Test name");
        item.setSku("SKU123");
        item.setDescription("Test description");
        item.setPrice(new BigDecimal("199.99"));
        item.setQuantity(5);
        return item;
    }

    private Client buildClient() {
        Client client = new Client();
        client.setId(3L);
        client.setClientName("Test Client");
        client.setInstagramPageId("page_id");
        client.setAccessToken("token");
        client.setAiSystemPrompt("prompt");
        return client;
    }
}