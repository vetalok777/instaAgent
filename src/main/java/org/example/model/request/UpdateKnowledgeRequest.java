package org.example.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


@Data
public class UpdateKnowledgeRequest {

    @NotBlank(message = "New content cannot be empty")
    private String newContent;

}
