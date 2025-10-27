package org.example.service;

import org.example.database.entity.CatalogItem;
import org.example.database.entity.Client;
import org.example.database.repository.CatalogItemRepository;
import org.example.database.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogManagementServiceTest {

    private CatalogItemRepository catalogItemRepository;
    private ClientRepository clientRepository;
    private RAGService ragService;
    private CatalogManagementService catalogManagementService;
    private Method generateKnowledgeTextMethod;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        catalogItemRepository = Mockito.mock(CatalogItemRepository.class);
        clientRepository = Mockito.mock(ClientRepository.class);
        ragService = Mockito.mock(RAGService.class);

        catalogManagementService = new CatalogManagementService(
                catalogItemRepository,
                clientRepository,
                ragService
        );

        generateKnowledgeTextMethod = CatalogManagementService.class
                .getDeclaredMethod("generateKnowledgeText", CatalogItem.class);
        generateKnowledgeTextMethod.setAccessible(true);
    }

    @Test
    void createCatalogItem_assignsClientPersistsAndSynchronizesKnowledge() throws Exception {
        CatalogItem item = buildCatalogItem();
        item.setAttributes(Map.of("Колір", "Чорний"));
        Client client = buildClient();

        when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
        when(catalogItemRepository.save(any(CatalogItem.class))).thenAnswer(invocation -> {
            CatalogItem saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        CatalogItem result = catalogManagementService.createCatalogItem(item, 1L);

        ArgumentCaptor<CatalogItem> catalogItemCaptor = ArgumentCaptor.forClass(CatalogItem.class);
        verify(catalogItemRepository).save(catalogItemCaptor.capture());
        assertSame(client, catalogItemCaptor.getValue().getClient());
        assertSame(result, catalogItemCaptor.getValue());

        verify(ragService).deleteKnowledgeForCatalogItem(10L);
        ArgumentCaptor<String> knowledgeCaptor = ArgumentCaptor.forClass(String.class);
        verify(ragService).createAndStoreEmbedding(eq(client), knowledgeCaptor.capture(), same(result));

        String knowledgeText = knowledgeCaptor.getValue();
        String expectedBase = "Товар: Test name (Артикул: SKU123). Опис: Test description. Ціна: 199.99 грн. В наявності: 5 шт.";
        assertTrue(knowledgeText.startsWith(expectedBase));
        assertTrue(knowledgeText.contains("Колір: Чорний"));
    }

    @Test
    void updateCatalogItem_updatesFieldsAndSynchronizesKnowledge() throws Exception {
        CatalogItem existingItem = buildCatalogItem();
        existingItem.setId(5L);
        existingItem.setClient(buildClient());
        existingItem.setAttributes(Map.of("Стан", "Новий"));

        CatalogItem updatedItem = new CatalogItem();
        updatedItem.setName("Updated name");
        updatedItem.setDescription("Updated description");
        updatedItem.setPrice(new BigDecimal("149.49"));
        updatedItem.setQuantity(8);
        updatedItem.setAttributes(Map.of("Колір", "Білий", "Матеріал", "Бавовна"));

        when(catalogItemRepository.findById(5L)).thenReturn(Optional.of(existingItem));
        when(catalogItemRepository.save(existingItem)).thenReturn(existingItem);

        CatalogItem result = catalogManagementService.updateCatalogItem(5L, updatedItem);

        assertSame(existingItem, result);
        assertEquals("Updated name", existingItem.getName());
        assertEquals("Updated description", existingItem.getDescription());
        assertEquals(new BigDecimal("149.49"), existingItem.getPrice());
        assertEquals(8, existingItem.getQuantity());
        assertEquals(Map.of("Колір", "Білий", "Матеріал", "Бавовна"), existingItem.getAttributes());

        verify(catalogItemRepository).save(existingItem);
        verify(ragService).deleteKnowledgeForCatalogItem(5L);
        ArgumentCaptor<String> knowledgeCaptor = ArgumentCaptor.forClass(String.class);
        verify(ragService).createAndStoreEmbedding(eq(existingItem.getClient()), knowledgeCaptor.capture(), same(existingItem));

        String knowledgeText = knowledgeCaptor.getValue();
        String expectedBase = "Товар: Updated name (Артикул: SKU123). Опис: Updated description. Ціна: 149.49 грн. В наявності: 8 шт.";
        assertTrue(knowledgeText.startsWith(expectedBase));
        assertTrue(knowledgeText.contains("Колір: Білий"));
        assertTrue(knowledgeText.contains("Матеріал: Бавовна"));
    }

    @Test
    void deleteCatalogItem_removesKnowledgeAndRepositoryEntry() {
        when(catalogItemRepository.existsById(7L)).thenReturn(true);

        catalogManagementService.deleteCatalogItem(7L);

        verify(ragService).deleteKnowledgeForCatalogItem(7L);
        verify(catalogItemRepository).deleteById(7L);
    }

    @Test
    void deleteCatalogItem_whenItemMissingThrowsIllegalArgumentException() {
        when(catalogItemRepository.existsById(99L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> catalogManagementService.deleteCatalogItem(99L));

        verify(ragService, never()).deleteKnowledgeForCatalogItem(anyLong());
        verify(catalogItemRepository, never()).deleteById(anyLong());
    }

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
