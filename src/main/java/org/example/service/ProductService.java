package org.example.service;

import org.example.database.entity.Product;
import org.example.database.repository.ProductRepository;
import org.example.database.repository.ProductVariantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for managing the business logic related ith products.
 * Encapsulate work with the products repositories and their variants.
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
     * Searching for products whose names contain key word.
     * @param nameKeyword Key word for searching in the product name.
     * @return List of found products.
     */
    public List<Product> findProductsByName(String nameKeyword) {
        return productRepository.findByNameWithVariants(nameKeyword);
    }
}