package org.example.model;

import lombok.Data;

import java.util.List;

@Data
public class RequestPayload {
    private List<Content> contents;
}
