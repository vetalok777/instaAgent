package org.example.service;

import org.example.database.entity.Client;
import org.example.database.repository.ClientRepository;
import org.example.service.google.GoogleFileSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy; // <-- ДОДАНО ІМПОРТ
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeManagementService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeManagementService.class);

    private final GoogleFileSearchService fileSearchService;
    private final ClientRepository clientRepository;

    private KnowledgeManagementService self;
    @Autowired
    public void setSelf(@Lazy KnowledgeManagementService self) { // <-- ДОДАНО @Lazy
        this.self = self;
    }

    public KnowledgeManagementService(GoogleFileSearchService fileSearchService, ClientRepository clientRepository) {
        this.fileSearchService = fileSearchService;
        this.clientRepository = clientRepository;
    }

    @Transactional
    public void processAndStoreKnowledge(Long clientId, InputStream inputStream, String fileName, String mimeType) throws IOException, InterruptedException {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client with ID " + clientId + " not found."));

        String storeName = fileSearchService.getOrCreateStoreForClient(client);
        if (client.getFileSearchStoreName() == null || !client.getFileSearchStoreName().equals(storeName)) {
            clientRepository.save(client);
        }

        self.deleteOldDocumentsAsync(storeName, client.getId());

        List<Map<String, Object>> metadata = List.of(
                Map.of("key", "doctype", "value", "general"),
                Map.of("key", "is_active", "value", true),
                Map.of("key", "updated_at_epoch", "value", Instant.now().getEpochSecond()),
                Map.of("key", "client_id", "value", client.getId())
        );

        byte[] fileData = inputStream.readAllBytes();

        String newDocumentName = fileSearchService.uploadAndImportFile(
                storeName, fileName, fileData, mimeType, metadata);

        logger.info("Новий документ загальних знань '{}' завантажено для клієнта {}", newDocumentName, clientId);
    }

    @Async("taskExecutor")
    public void deleteOldDocumentsAsync(String storeName, Long clientId) {
        if (storeName == null || storeName.isBlank()) return;
        String filter = String.format("doctype = \"general\" AND client_id = %d", clientId);
        logger.info("ASYNC: Пошук старих 'general' документів для видалення (Filter: {})", filter);
        try {
            List<String> oldDocNames = fileSearchService.findDocumentNames(storeName);
            logger.info("ASYNC: Знайдено {} старих 'general' документів для видалення.", oldDocNames.size());
            for (String docName : oldDocNames) {
                fileSearchService.deleteDocument(docName);
            }
        } catch (IOException e) {
            logger.error("ASYNC: Помилка під час видалення старих 'general' документів: {}", e.getMessage());
        }
    }
}