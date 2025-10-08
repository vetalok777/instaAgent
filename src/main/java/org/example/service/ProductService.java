package org.example.service;

import org.example.database.entity.Product;
import org.example.database.repository.ProductRepository;
import org.example.database.repository.ProductVariantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервіс для керування бізнес-логікою, пов'язаною з товарами.
 * Інкапсулює роботу з репозиторіями товарів та їх варіантів.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    @Autowired
    public ProductService(ProductRepository productRepository, ProductVariantRepository variantRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
    }

    /**
     * Шукає товари, назва яких містить ключове слово.
     * @param nameKeyword Ключове слово для пошуку в назві товару.
     * @return Список знайдених товарів.
     */
    public List<Product> findProductsByName(String nameKeyword) {
        return productRepository.findByNameWithVariants(nameKeyword);
    }

    // У майбутньому тут можна додати методи для отримання інформації про наявність, ціни, варіанти тощо.
}