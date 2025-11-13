package org.example.model.response;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.example.model.Content;

@Data
public class ResponseCandidate {
    private Content content;
    private String finishReason;
    private int index;

    // --- ДОДАНО ЦЕЙ БЛОК ---
    @SerializedName("groundingMetadata")
    private GroundingMetadata groundingMetadata;
    // --- КІНЕЦЬ ---

    // (Ваші існуючі safetyRatings тощо, якщо вони є, залиште)
}