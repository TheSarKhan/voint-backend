package com.starsoft.voint.voice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads {@code ai/prompts/system-prompt.md} and {@code ai/prompts/boundaries.md} once at startup
 * and caches their contents (they're static files, no need to re-read per request).
 *
 * Read from the filesystem (relative to the working directory - {@code voint.ai.folder}, default
 * {@code ./ai}), NOT the classpath: the Dockerfile copies {@code ai/} to sit next to the jar, and
 * locally {@code mvnw spring-boot:run} runs with working directory {@code voint-backend/}.
 *
 * If a file is missing, the app still starts - a minimal built-in default is used and the problem
 * is logged at ERROR level rather than crashing the app over a missing prompt file.
 */
@Slf4j
@Component
@Getter
public class PromptLoader {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            Sən Voint platformasının telefon səs agentisən. Zəng edən müştərilərlə səmimi, qısa və \
            təbii telefon danışığı tərzində danış (yazı dilində deyil - qısa cümlələr, siyahı yox). \
            Yalnız sənə verilən MƏLUMAT BAZASI kontekstinə əsaslanaraq cavab ver, bilmədiyini uydurma.
            """;

    private static final String DEFAULT_BOUNDARIES = """
            Qiymət danışığı aparma (yalnız məlumat bazasındakı rəsmi qiymətləri söylə), qəti öhdəlik \
            götürmə ("sorğunuz qeydə alındı" de, "təsdiqləndi" demə), tibbi/hüquqi məsləhət vermə, \
            şikayətləri özün həll etməyə çalışma - insan operatora yönləndir, bilmədiyin cavabı uydurma, \
            ödəniş məlumatı (kart, köçürmə) qəbul etmə.
            """;

    private final String systemPrompt;
    private final String boundaries;

    public PromptLoader(@Value("${voint.ai.folder:./ai}") String aiFolder) {
        this.systemPrompt = load(Path.of(aiFolder, "prompts", "system-prompt.md"), DEFAULT_SYSTEM_PROMPT, "system-prompt.md");
        this.boundaries = load(Path.of(aiFolder, "prompts", "boundaries.md"), DEFAULT_BOUNDARIES, "boundaries.md");
    }

    private String load(Path path, String fallback, String label) {
        try {
            String content = Files.readString(path);
            log.info("Loaded {} ({} chars) from {}", label, content.length(), path.toAbsolutePath());
            return content;
        } catch (IOException e) {
            log.error("Could not read {} from {} - falling back to a minimal built-in default prompt. "
                    + "Voice answers will be lower quality until this file is available.",
                    label, path.toAbsolutePath(), e);
            return fallback;
        }
    }
}
