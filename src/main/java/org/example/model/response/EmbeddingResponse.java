package org.example.model.response;

import lombok.Data;

import java.util.List;

@Data
public class EmbeddingResponse {
    private Embedding embedding;

    @Data
    public static class Embedding {
        private List<Float> values;
    }
}