package com.starsoft.voint.rbac;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reports, at startup, every endpoint that declares neither {@link RequirePermission} nor
 * {@link PublicEndpoint}.
 *
 * <p>This exists because of how authorisation actually fails in practice: not by someone writing
 * a wrong rule, but by someone adding an endpoint and forgetting the rule entirely. Nothing about
 * that is visible - the endpoint works, the tests pass, and it stays open until someone notices.
 *
 * <p>It warns rather than refuses to start. Refusing would mean a single missed annotation takes
 * production down, which trades a quiet risk for a loud outage; the log line survives in the
 * deploy output where it gets read.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EndpointCoverageReporter {

    /** Infrastructure Spring registers itself; not ours to annotate. */
    private static final List<String> IGNORED_PACKAGES = List.of(
            "org.springframework", "org.springdoc");

    private final RequestMappingHandlerMapping handlerMapping;

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        List<String> unguarded = new ArrayList<>();

        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod method = entry.getValue();

            String declaringClass = method.getBeanType().getName();
            if (IGNORED_PACKAGES.stream().anyMatch(declaringClass::startsWith)) {
                continue;
            }
            if (method.hasMethodAnnotation(RequirePermission.class)
                    || method.hasMethodAnnotation(PublicEndpoint.class)) {
                continue;
            }
            unguarded.add(info.toString() + "  ->  "
                    + method.getBeanType().getSimpleName() + "." + method.getMethod().getName());
        }

        if (unguarded.isEmpty()) {
            log.info("Authorisation: every endpoint declares a permission or is marked public");
            return;
        }
        log.warn("Authorisation: {} endpoint(s) declare NO permission and are not marked public. "
                + "They are reachable by any authenticated user:", unguarded.size());
        unguarded.stream().sorted().forEach(e -> log.warn("    {}", e));
    }
}
