package com.starsoft.voint.llm;

/** Unchecked wrapper for any Gemini API failure (network, non-2xx, unexpected response shape). */
public class GeminiApiException extends RuntimeException {

    public GeminiApiException(String message) {
        super(message);
    }

    public GeminiApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
