package org.example.database.repository;

import org.example.database.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByNameContainingIgnoreCase(String name);

    /**
     * Find products with a name and searching their variants.
     * @param name key word for the searching.
     * @return List of products with variants.
     */
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.variants WHERE lower(p.name) LIKE lower(concat('%', :name, '%'))")
    List<Product> findByNameWithVariants(String name);
}