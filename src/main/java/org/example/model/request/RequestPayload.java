package org.example.model.request;

import lombok.Data;
import org.example.model.Content;

import java.util.List;

@Data
public class RequestPayload {
    private List<Content> contents;
}
