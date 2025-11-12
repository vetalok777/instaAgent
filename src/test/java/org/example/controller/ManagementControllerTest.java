package org.example.controller;

import org.example.database.entity.CatalogItem;
import org.example.model.dto.CatalogItemDto;
import org.example.service.CatalogManagementService;
import org.example.service.KnowledgeManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.beans.BeanUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagementControllerTest {

    @Mock
    private KnowledgeManagementService knowledgeManagementService;

    @Mock
    private CatalogManagementService catalogManagementService;

    @InjectMocks
    private ManagementController managementController;

    private final Long testClientId = 1L;

    @Test
    void uploadKnowledgeFile_success() throws Exception {
        MultipartFile file = new MockMultipartFile("test.txt", "test.txt", "text/plain", "Test content".getBytes());
        doNothing().when(knowledgeManagementService).processAndStoreKnowledge(anyLong(), any());

        ResponseEntity<String> response = managementController.uploadKnowledgeFile(file, testClientId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Базу знань для клієнта ID " + testClientId + " успішно оновлено.", response.getBody());
        verify(knowledgeManagementService, times(1)).processAndStoreKnowledge(eq(testClientId), any(ByteArrayInputStream.class));
    }

    @Test
    void uploadKnowledgeFile_emptyFile() throws Exception {
        MultipartFile file = new MockMultipartFile("test.txt", "test.txt", "text/plain", new byte[0]);

        ResponseEntity<String> response = managementController.uploadKnowledgeFile(file, testClientId);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Файл порожній!", response.getBody());
        verify(knowledgeManagementService, never()).processAndStoreKnowledge(anyLong(), any());
    }

    @Test
    void uploadKnowledgeFile_serviceThrowsException() throws Exception {
        MultipartFile file = new MockMultipartFile("test.txt", "test.txt", "text/plain", "Test content".getBytes());
        doThrow(new IOException("File processing error")).when(knowledgeManagementService).processAndStoreKnowledge(anyLong(), any());

        ResponseEntity<String> response = managementController.uploadKnowledgeFile(file, testClientId);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Помилка під час обробки файлу: File processing error", response.getBody());
        verify(knowledgeManagementService, times(1)).processAndStoreKnowledge(eq(testClientId), any(ByteArrayInputStream.class));
    }

    @Test
    void createCatalogItem_success() throws Exception {
        CatalogItemDto itemToCreate = new CatalogItemDto();
        itemToCreate.setName("New Product");

        CatalogItem createdItem = new CatalogItem();
        createdItem.setId(1L);
        BeanUtils.copyProperties(itemToCreate, createdItem);

        when(catalogManagementService.createCatalogItem(any(CatalogItem.class), anyLong())).thenReturn(createdItem);

        ResponseEntity<?> response = managementController.createCatalogItem(itemToCreate, testClientId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(createdItem, response.getBody());
        // We verify that the service is called with any CatalogItem and the correct client ID
        verify(catalogManagementService, times(1)).createCatalogItem(any(CatalogItem.class), eq(testClientId));
    }

    @Test
    void createCatalogItem_serviceThrowsException() throws Exception {
        CatalogItemDto itemToCreate = new CatalogItemDto();
        String errorMessage = "Failed to create item";
        doThrow(new RuntimeException(errorMessage)).when(catalogManagementService).createCatalogItem(any(CatalogItem.class), anyLong());

        ResponseEntity<?> response = managementController.createCatalogItem(itemToCreate, testClientId);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Помилка створення товару: " + errorMessage, response.getBody());
        verify(catalogManagementService, times(1)).createCatalogItem(any(CatalogItem.class), eq(testClientId));
    }

    @Test
    void updateCatalogItem_success() throws Exception {
        Long itemId = 2L;
        CatalogItemDto updatedItemRequest = new CatalogItemDto();
        updatedItemRequest.setName("Updated Product");

        CatalogItem returnedItem = new CatalogItem();
        returnedItem.setId(itemId);
        BeanUtils.copyProperties(updatedItemRequest, returnedItem);

        when(catalogManagementService.updateCatalogItem(anyLong(), any(CatalogItem.class))).thenReturn(returnedItem);

        ResponseEntity<?> response = managementController.updateCatalogItem(itemId, updatedItemRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(returnedItem, response.getBody());
        verify(catalogManagementService, times(1)).updateCatalogItem(eq(itemId), any(CatalogItem.class));
    }

    @Test
    void updateCatalogItem_serviceThrowsException() throws Exception {
        Long itemId = 2L;
        CatalogItemDto updatedItemRequest = new CatalogItemDto();
        updatedItemRequest.setName("Updated Product");
        String errorMessage = "Failed to update item";
        doThrow(new RuntimeException(errorMessage)).when(catalogManagementService).updateCatalogItem(anyLong(), any(CatalogItem.class));

        ResponseEntity<?> response = managementController.updateCatalogItem(itemId, updatedItemRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Помилка оновлення товару: " + errorMessage, response.getBody());
        verify(catalogManagementService, times(1)).updateCatalogItem(eq(itemId), any(CatalogItem.class));
    }

    @Test
    void deleteCatalogItem_success() throws Exception {
        Long itemId = 3L;
        doNothing().when(catalogManagementService).deleteCatalogItem(anyLong());

        ResponseEntity<Void> response = managementController.deleteCatalogItem(itemId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(catalogManagementService, times(1)).deleteCatalogItem(eq(itemId));
    }
}
