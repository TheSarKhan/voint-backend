package com.starsoft.voint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Voint - Voice Intelligence backend.
 *
 * Vapi orchestrates telephony + STT + TTS; this backend is the "brain":
 * it receives the Vapi custom-LLM webhook, runs RAG over tenant data,
 * calls the LLM (Gemini Flash) and returns the answer to be spoken.
 *
 * BOOTSTRAP STAGE: skeleton only - webhook returns a mock response.
 */
@SpringBootApplication
public class VointApplication {

    public static void main(String[] args) {
        SpringApplication.run(VointApplication.class, args);
    }
}
