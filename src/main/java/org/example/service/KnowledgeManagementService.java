package org.example.service;

import org.example.database.entity.Client;
import org.example.database.repository.ClientRepository;
import org.example.service.gemini.FileSearchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Service for managing the knowledge base using File Search API.
 */
@Service
public class KnowledgeManagementService {

    private final FileSearchService fileSearchService;
    private final ClientRepository clientRepository;

    public KnowledgeManagementService(FileSearchService fileSearchService, ClientRepository clientRepository) {
        this.fileSearchService = fileSearchService;
        this.clientRepository = clientRepository;
    }

    /**
     * Processes a text file from an InputStream, splits it into chunks,
     * and uploads them as files to the client's File Search Store.
     *
     * @param clientId    The ID of the client whose knowledge base is being updated.
     * @param inputStream The InputStream of the file to process.
     * @throws IOException              if an I/O error occurs.
     * @throws IllegalArgumentException if the client is not found.
     */
    @Transactional
    public void processAndStoreKnowledge(Long clientId, InputStream inputStream) throws IOException, InterruptedException {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client with ID " + clientId + " not found."));

        if (client.getFileSearchStoreId() == null || client.getFileSearchStoreId().isEmpty()) {
            String storeId = fileSearchService.createFileSearchStore("store_for_client_" + clientId);
            client.setFileSearchStoreId(storeId);
            clientRepository.save(client);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder paragraph = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    if (!paragraph.toString().trim().isEmpty()) {
                        String fileName = "knowledge-" + UUID.randomUUID() + ".txt";
                        fileSearchService.uploadFile(client.getFileSearchStoreId(), fileName, paragraph.toString().trim());
                        paragraph.setLength(0);
                    }
                } else {
                    paragraph.append(line).append("\n");
                }
            }
            if (!paragraph.toString().trim().isEmpty()) {
                String fileName = "knowledge-" + UUID.randomUUID() + ".txt";
                fileSearchService.uploadFile(client.getFileSearchStoreId(), fileName, paragraph.toString().trim());
            }
        }
    }
}
