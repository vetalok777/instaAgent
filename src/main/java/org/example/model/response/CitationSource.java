package org.example.model.response;

import lombok.Data;

@Data
public class CitationSource {
    private int startIndex;
    private int endIndex;
    private String uri;
    private String license;
}