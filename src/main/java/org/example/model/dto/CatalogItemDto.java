package org.example.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class CatalogItemDto {

    @NotBlank(message = "SKU cannot be empty")
    private String sku;

    @NotBlank(message = "Name cannot be empty")
    private String name;

    private String description;

    @NotNull(message = "Price cannot be empty")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price should be > 0")
    private BigDecimal price;

    @Min(value = 0, message = "Quanitity cannot be negative")
    private int quantity;

    private Map<String, String> attributes;
}
