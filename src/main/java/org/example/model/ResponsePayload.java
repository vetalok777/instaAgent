package org.example.model;

import lombok.Data;

import java.util.List;

@Data
public class ResponsePayload {
    private List<ResponseCandidate> candidates;
}
