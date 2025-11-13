package org.example.service;

import org.example.database.entity.Client;
import org.example.database.repository.ClientRepository;
import org.example.service.google.GoogleFileSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeManagementServiceTest {

    @Mock
    private GoogleFileSearchService fileSearchService; // ЗМІНЕНО

    @Mock
    private ClientRepository clientRepository;

    @Spy // Використовуємо Spy, щоб мокати асинхронний метод
    @InjectMocks
    private KnowledgeManagementService knowledgeManagementService;

    @Test
    void processAndStoreKnowledge_uploadsFileWithCorrectMetadata() throws Exception {
        // Arrange
        Client client = new Client();
        client.setId(1L);
        Long clientId = 1L;
        String storeName = "fileSearchStores/123";
        String fileName = "knowledge.txt";
        String mimeType = "text/plain";
        String content = "Test content";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(fileSearchService.getOrCreateStoreForClient(client)).thenReturn(storeName);

        // Мокаємо асинхронний метод, щоб він нічого не робив
        doNothing().when(knowledgeManagementService).deleteOldDocumentsAsync(anyString(), anyLong());

        when(fileSearchService.uploadAndImportFile(eq(storeName), eq(fileName), any(byte[].class), eq(mimeType), anyList()))
                .thenReturn("fileSearchStores/123/documents/new-doc");

        ArgumentCaptor<List<Map<String, Object>>> metadataCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        knowledgeManagementService.processAndStoreKnowledge(clientId, inputStream, fileName, mimeType);

        // Assert
        // 1. Перевіряємо, що старі документи видаляються
        verify(knowledgeManagementService).deleteOldDocumentsAsync(storeName, clientId);

        // 2. Перевіряємо, що новий файл завантажується з правильними метаданими
        verify(fileSearchService).uploadAndImportFile(
                eq(storeName),
                eq(fileName),
                eq(content.getBytes(StandardCharsets.UTF_8)),
                eq(mimeType),
                metadataCaptor.capture()
        );

        List<Map<String, Object>> metadata = metadataCaptor.getValue();
        assertEquals("doctype", metadata.get(0).get("key"));
        assertEquals("general", metadata.get(0).get("value"));
        assertEquals("is_active", metadata.get(1).get("key"));
        assertEquals(true, metadata.get(1).get("value"));
        assertEquals("client_id", metadata.get(3).get("key"));
        assertEquals(clientId, metadata.get(3).get("value"));

        // 3. Перевіряємо, що клієнт зберігається (якщо storeName був новий)
        verify(clientRepository).save(client);
    }

    @Test
    void processAndStoreKnowledge_throwsExceptionWhenClientNotFound() throws IOException {
        // Arrange
        when(clientRepository.findById(anyLong())).thenReturn(Optional.empty());
        InputStream inputStream = new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> knowledgeManagementService.processAndStoreKnowledge(42L, inputStream, "file.txt", "text/plain"));

        // Перевіряємо, що сервіс Google не викликався
        verify(fileSearchService, never()).getOrCreateStoreForClient(any());
        verify(knowledgeManagementService, never()).deleteOldDocumentsAsync(anyString(), anyLong());
    }

    // Тест `updateKnowledge` видалено, оскільки метод видалено
}