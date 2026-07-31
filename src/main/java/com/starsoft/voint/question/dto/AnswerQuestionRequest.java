package com.starsoft.voint.question.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Operatorun təsdiqlədiyi cavab. Mətn bilik bazasına olduğu kimi düşür — AI qaralaması deyil,
 * operatorun görüb saxladığı versiya.
 */
public record AnswerQuestionRequest(
        @NotBlank String content,
        String category,
        String source
) {
}
