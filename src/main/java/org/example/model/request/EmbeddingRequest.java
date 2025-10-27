package org.example.model.request;

import lombok.Data;
import org.example.model.Content;

@Data
public class EmbeddingRequest {
    private Content content;
}