package org.example.database.repository;

import org.example.database.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Пошук товарів, назва яких містить переданий рядок (без урахування регістру)
    List<Product> findByNameContainingIgnoreCase(String name);

    /**
     * Знаходить товари за назвою та одразу завантажує їх варіанти, щоб уникнути проблеми N+1.
     * @param name ключове слово для пошуку в назві.
     * @return список товарів з їх варіантами.
     */
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.variants WHERE lower(p.name) LIKE lower(concat('%', :name, '%'))")
    List<Product> findByNameWithVariants(String name);
}