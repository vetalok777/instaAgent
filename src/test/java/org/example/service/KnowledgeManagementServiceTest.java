package org.example.service;

import org.example.database.entity.Client;
import org.example.database.entity.Knowledge;
import org.example.database.repository.ClientRepository;
import org.example.database.repository.KnowledgeRepository;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeManagementServiceTest {

    @Mock
    private RAGService ragService;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private KnowledgeRepository knowledgeRepository;

    @InjectMocks
    private KnowledgeManagementService knowledgeManagementService;

    @Test
    void processAndStoreKnowledge_createsEmbeddingsForEachParagraph() throws Exception {
        Client client = new Client();
        Long clientId = 1L;
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

        String content = "First paragraph line 1.\nFirst paragraph line 2.\n\nSecond paragraph.\n\nThird paragraph.";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        knowledgeManagementService.processAndStoreKnowledge(clientId, inputStream);

        ArgumentCaptor<String> paragraphCaptor = ArgumentCaptor.forClass(String.class);
        verify(ragService, times(3)).createAndStoreEmbedding(eq(client), paragraphCaptor.capture());

        List<String> paragraphs = paragraphCaptor.getAllValues();
        assertEquals(
                List.of(
                        "First paragraph line 1.\nFirst paragraph line 2.",
                        "Second paragraph.",
                        "Third paragraph."
                ),
                paragraphs
        );
    }

    @Test
    void processAndStoreKnowledge_throwsExceptionWhenClientNotFound() throws IOException {
        when(clientRepository.findById(anyLong())).thenReturn(Optional.empty());

        InputStream inputStream = new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class,
                () -> knowledgeManagementService.processAndStoreKnowledge(42L, inputStream));

        verify(ragService, never()).createAndStoreEmbedding(org.mockito.Mockito.any(), anyString());
    }

    @Test
    void updateKnowledge_updatesContentAndEmbeddingAndSaves() throws Exception {
        Long knowledgeId = 5L;
        Knowledge knowledge = new Knowledge();
        knowledge.setContent("Old content");
        knowledge.setEmbedding(new float[]{0.1f});

        when(knowledgeRepository.findById(knowledgeId)).thenReturn(Optional.of(knowledge));
        float[] expectedEmbedding = new float[]{0.5f, 0.6f};
        String newContent = "New knowledge content";
        when(ragService.getEmbeddingForText(newContent)).thenReturn(expectedEmbedding);

        knowledgeManagementService.updateKnowledge(knowledgeId, newContent);

        assertEquals(newContent, knowledge.getContent());
        assertArrayEquals(expectedEmbedding, knowledge.getEmbedding());
        verify(ragService).getEmbeddingForText(newContent);
        verify(knowledgeRepository).save(knowledge);
    }
}
