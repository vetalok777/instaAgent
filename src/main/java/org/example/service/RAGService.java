package org.example.service;

import org.example.database.entity.CatalogItem;
import org.example.database.entity.Client;
import org.example.database.entity.Knowledge;
import org.example.database.repository.KnowledgeRepository;
import org.example.service.gemini.GeminiEmbeddingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for Retrieval-Augmented Generation (RAG).
 * This service handles the creation of embeddings and finding relevant context
 * from the knowledge base.
 */
@Service
public class RAGService {

    private final KnowledgeRepository knowledgeRepository;
    private final GeminiEmbeddingService geminiEmbeddingService;

    public RAGService(KnowledgeRepository knowledgeRepository, GeminiEmbeddingService geminiEmbeddingService) {
        this.knowledgeRepository = knowledgeRepository;
        this.geminiEmbeddingService = geminiEmbeddingService;
    }

    /**
     * Creates a vector embedding for the given content and stores it in the knowledge base
     * associated with a specific client.
     *
     * @param client  The client to whom this knowledge belongs.
     * @param content The textual content to be embedded and stored.
     * @throws IOException if the embedding generation fails.
     */
    @Transactional
    public void createAndStoreEmbedding(Client client, String content) throws IOException {
        float[] embeddingVector = geminiEmbeddingService.getEmbedding(content);

        Knowledge knowledge = new Knowledge();
        knowledge.setClient(client);
        knowledge.setContent(content);
        knowledge.setEmbedding(embeddingVector);

        knowledgeRepository.save(knowledge);
    }

    /**
     * Finds relevant context from the knowledge base for a given user query.
     *
     * @param client    The client whose knowledge base should be searched.
     * @param userQuery The user's query text.
     * @param limit     The maximum number of relevant documents to retrieve.
     * @return A formatted string containing the relevant context, or an empty string if no context is found.
     * @throws IOException if the embedding generation for the query fails.
     */
    public String findRelevantContext(Client client, String userQuery, int limit) throws IOException {
        float[] queryVector = geminiEmbeddingService.getEmbedding(userQuery);

        List<Knowledge> nearestNeighbors = knowledgeRepository.findNearestNeighbors(client.getId(), queryVector, limit);

        if (nearestNeighbors.isEmpty()) {
            return "";
        }

        return "### Контекст з Бази Знань (Source of Truth) ###\n" +
                "Це єдина достовірна інформація. Відповідай СУВОРО на основі цих даних. НЕ вигадуй нічого, чого немає в цьому контексті.\n\n" +
                nearestNeighbors.stream()
                        .map(Knowledge::getContent)
                        .collect(Collectors.joining("\n---\n")) +
                "\n### Кінець Контексту ###\n";
    }

    /**
     * Creates a vector embedding and links it to a specific catalog item.
     */
    @Transactional
    public void createAndStoreEmbedding(Client client, String content, CatalogItem catalogItem) throws IOException {
        float[] embeddingVector = getEmbeddingForText(content);

        Knowledge knowledge = new Knowledge();
        knowledge.setClient(client);
        knowledge.setContent(content);
        knowledge.setCatalogItem(catalogItem); // Link to the catalog item
        knowledge.setEmbedding(embeddingVector);

        knowledgeRepository.save(knowledge);
    }

    /**
     * Deletes all knowledge entries associated with a specific catalog item ID.
     */
    public void deleteKnowledgeForCatalogItem(Long catalogItemId) {
        knowledgeRepository.deleteAllByCatalogItemId(catalogItemId);
    }


    public float[] getEmbeddingForText(String text) throws IOException {
        return geminiEmbeddingService.getEmbedding(text);
    }
}