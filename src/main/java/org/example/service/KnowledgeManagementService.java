package org.example.service;

import org.example.database.entity.Client;
import org.example.database.entity.Knowledge;
import org.example.database.repository.ClientRepository;
import org.example.database.repository.KnowledgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Service for managing the knowledge base.
 * Handles processing of uploaded files and populating the knowledge base.
 */
@Service
public class KnowledgeManagementService {

    private final RAGService ragService;

    private final ClientRepository clientRepository;
    private final KnowledgeRepository knowledgeRepository;

    public KnowledgeManagementService(RAGService ragService, ClientRepository clientRepository, KnowledgeRepository knowledgeRepository) {
        this.ragService = ragService;
        this.clientRepository = clientRepository;
        this.knowledgeRepository = knowledgeRepository;
    }

    /**
     * Processes a text file from an InputStream, splits it into chunks,
     * and stores them as embeddings for a specific client.
     *
     * @param clientId    The ID of the client whose knowledge base is being updated.
     * @param inputStream The InputStream of the file to process.
     * @throws IOException              if an I/O error occurs.
     * @throws IllegalArgumentException if the client is not found.
     */
    @Transactional
    public void processAndStoreKnowledge(Long clientId, InputStream inputStream) throws IOException {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клієнт з ID " + clientId + " не знайдений."));

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder paragraph = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    if (!paragraph.toString().trim().isEmpty()) {
                        ragService.createAndStoreEmbedding(client, paragraph.toString().trim());
                        paragraph.setLength(0);
                    }
                } else {
                    paragraph.append(line).append("\n");
                }
            }
            if (!paragraph.toString().trim().isEmpty()) {
                ragService.createAndStoreEmbedding(client, paragraph.toString().trim());
            }
        }
    }

    /**
     * Retrieves all general knowledge entries (not linked to a specific catalog item) for a given client.
     *
     * @param clientId The ID of the client.
     * @return A list of {@link Knowledge} entries representing general information.
     */
    public List<Knowledge> getGeneralKnowledge(Long clientId) {
        clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клієнт з ID " + clientId + " не знайдений."));
        return knowledgeRepository.findByClientIdAndCatalogItemIsNull(clientId);
    }

    /**
     * Updates the content of a specific knowledge entry and regenerates its embedding.
     * This method is suitable for updating general knowledge entries.
     *
     * @param knowledgeId The ID of the knowledge entry to update.
     * @param newContent  The new text content.
     * @throws IOException if embedding generation fails.
     */
    @Transactional
    public void updateGeneralKnowledge(Long knowledgeId, String newContent) throws IOException {
        updateKnowledge(knowledgeId, newContent);
    }

    /**
     * Updates the content of a specific knowledge entry and regenerates its embedding.
     *
     * @param knowledgeId The ID of the knowledge entry to update.
     * @param newContent  The new text content.
     * @throws IOException if embedding generation fails.
     * @throws IllegalArgumentException if the knowledge entry is not found.
     */
    @Transactional
    public void updateKnowledge(Long knowledgeId, String newContent) throws IOException {
        Knowledge knowledge = knowledgeRepository.findById(knowledgeId)
                .orElseThrow(() -> new IllegalArgumentException("Запис знань з ID " + knowledgeId + " не знайдено."));

        knowledge.setContent(newContent);
        float[] newEmbedding = ragService.getEmbeddingForText(newContent);
        knowledge.setEmbedding(newEmbedding);

        knowledgeRepository.save(knowledge);
    }
}