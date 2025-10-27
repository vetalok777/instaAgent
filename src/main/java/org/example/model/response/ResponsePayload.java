package org.example.model.response;

import lombok.Data;

import java.util.List;

@Data
public class ResponsePayload {
    private List<ResponseCandidate> candidates;
}
