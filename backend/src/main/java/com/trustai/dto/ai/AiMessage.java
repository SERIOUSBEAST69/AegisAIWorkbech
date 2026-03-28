package com.trustai.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiMessage {
    @NotBlank
    private String role; // user/assistant/system
    @NotBlank
    private String content;
}
