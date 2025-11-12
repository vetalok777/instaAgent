package org.example.service;

import org.example.database.entity.CatalogItem;
import org.example.database.entity.Client;
import org.example.database.repository.CatalogItemRepository;
import org.example.database.repository.ClientRepository;
import org.example.service.gemini.FileSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CatalogManagementServiceTest {

    private CatalogItemRepository catalogItemRepository;
    private ClientRepository clientRepository;
    private FileSearchService fileSearchService;
    private CatalogManagementService catalogManagementService;

    @BeforeEach
    void setUp() {
        catalogItemRepository = Mockito.mock(CatalogItemRepository.class);
        clientRepository = Mockito.mock(ClientRepository.class);
        fileSearchService = Mockito.mock(FileSearchService.class);

        catalogManagementService = new CatalogManagementService(
                catalogItemRepository,
                clientRepository,
                fileSearchService
        );
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
        when(fileSearchService.uploadFile(anyString(), anyString(), anyString())).thenReturn("file-id-123");

        CatalogItem result = catalogManagementService.createCatalogItem(item, 1L);

        ArgumentCaptor<CatalogItem> catalogItemCaptor = ArgumentCaptor.forClass(CatalogItem.class);
        verify(catalogItemRepository, times(2)).save(catalogItemCaptor.capture());
        assertSame(client, catalogItemCaptor.getValue().getClient());
        assertSame(result, catalogItemCaptor.getValue());
        assertEquals("file-id-123", result.getFileId());

        ArgumentCaptor<String> knowledgeCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileSearchService).uploadFile(eq(client.getFileSearchStoreId()), eq("item-10.txt"), knowledgeCaptor.capture());

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
        existingItem.setFileId("old-file-id");

        CatalogItem updatedItem = new CatalogItem();
        updatedItem.setName("Updated name");
        updatedItem.setDescription("Updated description");
        updatedItem.setPrice(new BigDecimal("149.49"));
        updatedItem.setQuantity(8);
        updatedItem.setAttributes(Map.of("Колір", "Білий", "Матеріал", "Бавовна"));

        when(catalogItemRepository.findById(5L)).thenReturn(Optional.of(existingItem));
        when(catalogItemRepository.save(existingItem)).thenReturn(existingItem);
        when(fileSearchService.uploadFile(anyString(), anyString(), anyString())).thenReturn("new-file-id");

        CatalogItem result = catalogManagementService.updateCatalogItem(5L, updatedItem);

        assertSame(existingItem, result);
        assertEquals("Updated name", existingItem.getName());
        assertEquals("Updated description", existingItem.getDescription());
        assertEquals(new BigDecimal("149.49"), existingItem.getPrice());
        assertEquals(8, existingItem.getQuantity());
        assertEquals(Map.of("Колір", "Білий", "Матеріал", "Бавовна"), existingItem.getAttributes());
        assertEquals("new-file-id", result.getFileId());

        verify(fileSearchService).deleteFile("old-file-id");
        ArgumentCaptor<String> knowledgeCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileSearchService).uploadFile(eq(existingItem.getClient().getFileSearchStoreId()), eq("item-5.txt"), knowledgeCaptor.capture());

        String knowledgeText = knowledgeCaptor.getValue();
        String expectedBase = "Товар: Updated name (Артикул: SKU123). Опис: Updated description. Ціна: 149.49 грн. В наявності: 8 шт.";
        assertTrue(knowledgeText.startsWith(expectedBase));
        assertTrue(knowledgeText.contains("Колір: Білий"));
        assertTrue(knowledgeText.contains("Матеріал: Бавовна"));
    }

    @Test
    void deleteCatalogItem_removesKnowledgeAndRepositoryEntry() throws IOException {
        CatalogItem item = buildCatalogItem();
        item.setId(7L);
        item.setFileId("file-to-delete");
        when(catalogItemRepository.findById(7L)).thenReturn(Optional.of(item));

        catalogManagementService.deleteCatalogItem(7L);

        verify(fileSearchService).deleteFile("file-to-delete");
        verify(catalogItemRepository).deleteById(7L);
    }

    @Test
    void deleteCatalogItem_whenItemMissingThrowsIllegalArgumentException() throws Exception {
        when(catalogItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> catalogManagementService.deleteCatalogItem(99L));

        verify(fileSearchService, never()).deleteFile(anyString());
        verify(catalogItemRepository, never()).deleteById(anyLong());
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
        client.setFileSearchStoreId("store-id-123");
        return client;
    }
}
