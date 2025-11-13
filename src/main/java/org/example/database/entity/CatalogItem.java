package org.example.database.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Represents a structured item in a client's catalog (e.g., a product, a service).
 * This entity serves as the "source of truth" for dynamic data like price and quantity.
 */
@Entity
@Table(name = "catalog_items")
@Getter
@Setter
public class CatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    @JsonIgnore
    private Client client;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private int quantity;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> attributes;

    @Column(name = "doc_version", columnDefinition = "integer default 0")
    private int docVersion = 0;
}