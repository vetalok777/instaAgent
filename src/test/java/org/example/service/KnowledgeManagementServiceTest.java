package org.example.service;

import org.example.database.entity.Client;
import org.example.database.repository.ClientRepository;
import org.example.service.gemini.FileSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeManagementServiceTest {

    @Mock
    private FileSearchService fileSearchService;

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private KnowledgeManagementService knowledgeManagementService;

    @Test
    void processAndStoreKnowledge_uploadsEachParagraphAsAFile() throws Exception {
        Client client = new Client();
        client.setFileSearchStoreId("store-123");
        Long clientId = 1L;
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

        String content = "First paragraph line 1.\nFirst paragraph line 2.\n\nSecond paragraph.\n\nThird paragraph.";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        knowledgeManagementService.processAndStoreKnowledge(clientId, inputStream);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileSearchService, times(3)).uploadFile(eq("store-123"), anyString(), contentCaptor.capture());

        List<String> contents = contentCaptor.getAllValues();
        assertEquals(
                List.of(
                        "First paragraph line 1.\nFirst paragraph line 2.",
                        "Second paragraph.",
                        "Third paragraph."
                ),
                contents
        );
    }

    @Test
    void processAndStoreKnowledge_createsStoreIfItDoesNotExist() throws Exception {
        Client client = new Client();
        Long clientId = 1L;
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(fileSearchService.createFileSearchStore(anyString())).thenReturn("new-store-456");

        String content = "A single paragraph.";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        knowledgeManagementService.processAndStoreKnowledge(clientId, inputStream);

        verify(fileSearchService).createFileSearchStore("store_for_client_1");
        assertEquals("new-store-456", client.getFileSearchStoreId());
        verify(clientRepository).save(client);
        verify(fileSearchService).uploadFile(eq("new-store-456"), anyString(), eq("A single paragraph."));
    }

    @Test
    void processAndStoreKnowledge_throwsExceptionWhenClientNotFound() throws Exception {
        when(clientRepository.findById(anyLong())).thenReturn(Optional.empty());

        InputStream inputStream = new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class,
                () -> knowledgeManagementService.processAndStoreKnowledge(42L, inputStream));

        verify(fileSearchService, never()).createFileSearchStore(anyString());
        verify(fileSearchService, never()).uploadFile(anyString(), anyString(), anyString());
    }
}
