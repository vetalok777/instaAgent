package org.example.service;

import org.example.database.entity.CatalogItem;
import org.example.database.repository.CatalogItemRepository;
import org.example.database.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CatalogManagementServiceTest {

    private CatalogManagementService catalogManagementService;
    private Method generateKnowledgeTextMethod;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        catalogManagementService = new CatalogManagementService(
                Mockito.mock(CatalogItemRepository.class),
                Mockito.mock(ClientRepository.class),
                Mockito.mock(RAGService.class)
        );

        generateKnowledgeTextMethod = CatalogManagementService.class
                .getDeclaredMethod("generateKnowledgeText", CatalogItem.class);
        generateKnowledgeTextMethod.setAccessible(true);
    }

    @Test
    void generateKnowledgeText_withAttributes_includesFormattedAttributes() throws InvocationTargetException, IllegalAccessException {
        CatalogItem item = buildCatalogItem();
        item.setAttributes(Map.of("Колір", "Чорний", "Розмір", "L"));

        String knowledgeText = (String) generateKnowledgeTextMethod.invoke(catalogManagementService, item);

        String baseText = "Товар: Test name (Артикул: SKU123). Опис: Test description. Ціна: 199.99 грн. В наявності: 5 шт.";
        assertTrue(knowledgeText.startsWith(baseText));
        assertTrue(knowledgeText.contains("Характеристики:"));
        assertTrue(knowledgeText.contains("Колір: Чорний"));
        assertTrue(knowledgeText.contains("Розмір: L"));
    }

    @Test
    void generateKnowledgeText_withoutAttributes_omitsAttributesSectionAndInitializesMap() throws InvocationTargetException, IllegalAccessException {
        CatalogItem item = buildCatalogItem();
        item.setAttributes(null);

        String knowledgeText = (String) generateKnowledgeTextMethod.invoke(catalogManagementService, item);

        assertEquals("Товар: Test name (Артикул: SKU123). Опис: Test description. Ціна: 199.99 грн. В наявності: 5 шт.",
                knowledgeText);
        assertNotNull(item.getAttributes());
        assertTrue(item.getAttributes().isEmpty());
    }

    private CatalogItem buildCatalogItem() {
        CatalogItem item = new CatalogItem();
        item.setName("Test name");
        item.setSku("SKU123");
        item.setDescription("Test description");
        item.setPrice(new BigDecimal("199.99"));
        item.setQuantity(5);
        return item;
    }
}
