package org.example.model;

import lombok.Data;

import java.util.List;

@Data
public class Content {
    private List<Part> parts;
    private String role;
}
