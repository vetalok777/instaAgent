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
        // Arrange
        MultipartFile file = new MockMultipartFile("test.txt", "test.txt", "text/plain", "Test content".getBytes());
        // Оновлюємо сигнатуру мока
        doNothing().when(knowledgeManagementService).processAndStoreKnowledge(anyLong(), any(), anyString(), anyString());

        // Act
        ResponseEntity<String> response = managementController.uploadKnowledgeFile(file, testClientId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Базу знань (загальну) для клієнта ID " + testClientId + " успішно оновлено.", response.getBody());
        // Оновлюємо сигнатуру verify
        verify(knowledgeManagementService, times(1)).processAndStoreKnowledge(
                eq(testClientId),
                any(),
                eq("test.txt"),
                eq("text/plain")
        );
    }

    @Test
    void uploadKnowledgeFile_emptyFile() throws Exception {
        // Arrange
        MultipartFile file = new MockMultipartFile("test.txt", "test.txt", "text/plain", new byte[0]);

        // Act
        ResponseEntity<String> response = managementController.uploadKnowledgeFile(file, testClientId);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Файл порожній!", response.getBody());
        verify(knowledgeManagementService, never()).processAndStoreKnowledge(anyLong(), any(), anyString(), anyString());
    }

    @Test
    void uploadKnowledgeFile_serviceThrowsException() throws Exception {
        // Arrange
        MultipartFile file = new MockMultipartFile("test.txt", "test.txt", "text/plain", "Test content".getBytes());
        // Оновлюємо сигнатуру мока
        doThrow(new IOException("File processing error")).when(knowledgeManagementService)
                .processAndStoreKnowledge(anyLong(), any(), anyString(), anyString());

        // Act
        ResponseEntity<String> response = managementController.uploadKnowledgeFile(file, testClientId);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Помилка під час обробки файлу: File processing error", response.getBody());
        // Оновлюємо сигнатуру verify
        verify(knowledgeManagementService, times(1)).processAndStoreKnowledge(
                eq(testClientId),
                any(),
                eq("test.txt"),
                eq("text/plain")
        );
    }

    // --- ВИДАЛЕНО ТЕСТИ ---
    // testGetGeneralKnowledge_success
    // testGetGeneralKnowledge_clientNotFound
    // testGetGeneralKnowledge_serviceThrowsGenericException
    // testUpdateGeneralKnowledge_success
    // testUpdateGeneralKnowledge_serviceThrowsException
    // --- КІНЕЦЬ ВИДАЛЕННЯ ---

    @Test
    void createCatalogItem_success() throws Exception {
        // Arrange
        CatalogItemDto itemToCreate = new CatalogItemDto();
        itemToCreate.setName("New Product");
        CatalogItem createdItem = new CatalogItem();
        BeanUtils.copyProperties(itemToCreate, createdItem);

        when(catalogManagementService.createCatalogItem(any(CatalogItem.class), anyLong())).thenReturn(createdItem);

        // Act
        ResponseEntity<?> response = managementController.createCatalogItem(itemToCreate, testClientId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(createdItem, response.getBody());
        verify(catalogManagementService, times(1)).createCatalogItem(any(CatalogItem.class), eq(testClientId));
    }

    @Test
    void createCatalogItem_serviceThrowsException() throws Exception {
        // Arrange
        CatalogItemDto itemToCreate = new CatalogItemDto();
        String errorMessage = "Failed to create item";
        doThrow(new RuntimeException(errorMessage)).when(catalogManagementService).createCatalogItem(any(CatalogItem.class), anyLong());

        // Act
        ResponseEntity<?> response = managementController.createCatalogItem(itemToCreate, testClientId);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Помилка створення товару: " + errorMessage, response.getBody());
    }

    @Test
    void updateCatalogItem_success() throws Exception {
        // Arrange
        Long itemId = 2L;
        CatalogItemDto updatedItemRequest = new CatalogItemDto();
        CatalogItem returnedItem = new CatalogItem();

        when(catalogManagementService.updateCatalogItem(anyLong(), any(CatalogItem.class))).thenReturn(returnedItem);

        // Act
        ResponseEntity<?> response = managementController.updateCatalogItem(itemId, updatedItemRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(returnedItem, response.getBody());
    }

    @Test
    void updateCatalogItem_serviceThrowsException() throws Exception {
        // Arrange
        Long itemId = 2L;
        CatalogItemDto updatedItemRequest = new CatalogItemDto();
        String errorMessage = "Failed to update item";
        doThrow(new RuntimeException(errorMessage)).when(catalogManagementService).updateCatalogItem(anyLong(), any(CatalogItem.class));

        // Act
        ResponseEntity<?> response = managementController.updateCatalogItem(itemId, updatedItemRequest);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Помилка оновлення товару: " + errorMessage, response.getBody());
    }

    @Test
    void deleteCatalogItem_success() {
        // Arrange
        Long itemId = 3L;
        doNothing().when(catalogManagementService).deleteCatalogItem(anyLong());

        // Act
        ResponseEntity<Void> response = managementController.deleteCatalogItem(itemId);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(catalogManagementService, times(1)).deleteCatalogItem(eq(itemId));
    }

    @Test
    void deleteCatalogItem_serviceThrowsException() {
        // Arrange
        Long itemId = 3L;
        doThrow(new RuntimeException("Delete failed")).when(catalogManagementService).deleteCatalogItem(itemId);

        // Act
        ResponseEntity<Void> response = managementController.deleteCatalogItem(itemId);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
}