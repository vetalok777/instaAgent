package org.example.model.request;

import lombok.Data;
import org.example.model.Content;

import java.util.List;

import com.google.gson.JsonArray;

@Data
public class RequestPayload {
    private List<Content> contents;
    private JsonArray tools;
}
