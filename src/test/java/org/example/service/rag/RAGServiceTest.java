package org.example.service.rag;

import org.example.database.entity.CatalogItem;
import org.example.database.entity.Client;
import org.example.database.entity.Knowledge;
import org.example.database.repository.KnowledgeRepository;
import org.example.service.RAGService;
import org.example.service.gemini.GeminiEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RAGServiceTest {

    @Mock
    private KnowledgeRepository knowledgeRepository;

    @Mock
    private GeminiEmbeddingService geminiEmbeddingService;

    @InjectMocks
    private RAGService ragService;

    private Client testClient;
    private float[] testEmbedding;

    @BeforeEach
    void setUp() {
        testClient = new Client();
        testClient.setId(1L);
        testEmbedding = new float[]{0.1f, 0.2f, 0.3f};
    }

    @Test
    void createAndStoreEmbedding_success() throws IOException {
        // Given
        String content = "This is a test content.";
        when(geminiEmbeddingService.getEmbedding(content)).thenReturn(testEmbedding);

        // When
        ragService.createAndStoreEmbedding(testClient, content);

        // Then
        ArgumentCaptor<Knowledge> knowledgeCaptor = ArgumentCaptor.forClass(Knowledge.class);
        verify(knowledgeRepository).save(knowledgeCaptor.capture());

        Knowledge savedKnowledge = knowledgeCaptor.getValue();
        assertEquals(testClient, savedKnowledge.getClient());
        assertEquals(content, savedKnowledge.getContent());
        assertArrayEquals(testEmbedding, savedKnowledge.getEmbedding());
        assertNull(savedKnowledge.getCatalogItem()); // Ensure it's a general knowledge
    }

    @Test
    void createAndStoreEmbedding_withCatalogItem_success() throws IOException {
        // Given
        String content = "This is a catalog item content.";
        CatalogItem catalogItem = new CatalogItem();
        catalogItem.setId(10L);

        when(geminiEmbeddingService.getEmbedding(content)).thenReturn(testEmbedding);

        // When
        ragService.createAndStoreEmbedding(testClient, content, catalogItem);

        // Then
        ArgumentCaptor<Knowledge> knowledgeCaptor = ArgumentCaptor.forClass(Knowledge.class);
        verify(knowledgeRepository).save(knowledgeCaptor.capture());

        Knowledge savedKnowledge = knowledgeCaptor.getValue();
        assertEquals(testClient, savedKnowledge.getClient());
        assertEquals(content, savedKnowledge.getContent());
        assertEquals(catalogItem, savedKnowledge.getCatalogItem());
        assertArrayEquals(testEmbedding, savedKnowledge.getEmbedding());
    }

    @Test
    void createAndStoreEmbedding_throwsIOException_whenEmbeddingFails() throws IOException {
        // Given
        String content = "Some content";
        when(geminiEmbeddingService.getEmbedding(content)).thenThrow(new IOException("API Error"));

        // When & Then
        assertThrows(IOException.class, () -> ragService.createAndStoreEmbedding(testClient, content));
        verify(knowledgeRepository, never()).save(any(Knowledge.class));
    }

    @Test
    void findRelevantContext_whenNeighborsFound() throws IOException {
        // Given
        String userQuery = "What is the price?";
        int limit = 3;
        when(geminiEmbeddingService.getEmbedding(userQuery)).thenReturn(testEmbedding);

        Knowledge neighbor1 = new Knowledge();
        neighbor1.setContent("The price is $100.");
        Knowledge neighbor2 = new Knowledge();
        neighbor2.setContent("We have a special offer.");
        List<Knowledge> neighbors = List.of(neighbor1, neighbor2);

        when(knowledgeRepository.findNearestNeighbors(testClient.getId(), testEmbedding, limit)).thenReturn(neighbors);

        // When
        String context = ragService.findRelevantContext(testClient, userQuery, limit);

        // Then
        assertTrue(context.contains("### Контекст з Бази Знань (Source of Truth) ###"));
        assertTrue(context.contains("The price is $100."));
        assertTrue(context.contains("\n---\n"));
        assertTrue(context.contains("We have a special offer."));
        assertTrue(context.contains("### Кінець Контексту ###"));
    }

    @Test
    void findRelevantContext_whenNoNeighborsFound() throws IOException {
        // Given
        String userQuery = "Some obscure query";
        int limit = 3;
        when(geminiEmbeddingService.getEmbedding(userQuery)).thenReturn(testEmbedding);
        when(knowledgeRepository.findNearestNeighbors(anyLong(), any(), anyInt())).thenReturn(Collections.emptyList());

        // When
        String context = ragService.findRelevantContext(testClient, userQuery, limit);

        // Then
        assertEquals("", context);
    }

    @Test
    void findRelevantContext_throwsIOException_whenEmbeddingFails() throws IOException {
        // Given
        String userQuery = "A query that will fail";
        when(geminiEmbeddingService.getEmbedding(userQuery)).thenThrow(new IOException("API Error"));

        // When & Then
        assertThrows(IOException.class, () -> ragService.findRelevantContext(testClient, userQuery, 3));
        verify(knowledgeRepository, never()).findNearestNeighbors(anyLong(), any(), anyInt());
    }

    @Test
    void deleteKnowledgeForCatalogItem_callsRepository() {
        // Given
        Long catalogItemId = 123L;

        // When
        ragService.deleteKnowledgeForCatalogItem(catalogItemId);

        // Then
        verify(knowledgeRepository, times(1)).deleteAllByCatalogItemId(catalogItemId);
    }

    @Test
    void getEmbeddingForText_returnsEmbeddingFromService() throws IOException {
        // Given
        String text = "Sample text";
        when(geminiEmbeddingService.getEmbedding(text)).thenReturn(testEmbedding);

        // When
        float[] result = ragService.getEmbeddingForText(text);

        // Then
        assertArrayEquals(testEmbedding, result);
        verify(geminiEmbeddingService, times(1)).getEmbedding(text);
    }
}
