package com.starsoft.voint.common.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.starsoft.voint.provisioning.VapiAssistantProvisioner;
import com.starsoft.voint.settings.VapiSyncService;

import lombok.extern.slf4j.Slf4j;

/**
 * Global error handling using RFC 7807 ProblemDetail responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource not found");
        pd.setType(URI.create("https://voint.starsoft.com/errors/not-found"));
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setTitle("Invalid request");
        pd.setType(URI.create("https://voint.starsoft.com/errors/validation"));
        pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList());
        return pd;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setTitle("Authentication failed");
        pd.setType(URI.create("https://voint.starsoft.com/errors/unauthorized"));
        return pd;
    }

    @ExceptionHandler(TenantAccessDeniedException.class)
    public ProblemDetail handleTenantAccessDenied(TenantAccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Access denied");
        pd.setType(URI.create("https://voint.starsoft.com/errors/forbidden"));
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid request");
        pd.setType(URI.create("https://voint.starsoft.com/errors/bad-request"));
        return pd;
    }

    /**
     * Without this, the catch-all below swallows every deliberately-chosen status: a controller
     * that answers 422 "this key does not work, ElevenLabs keys start with sk_" reaches the user
     * as a bare 500 "Unexpected server error", which is exactly the message they cannot act on.
     * Advice runs before Spring's own ResponseStatusExceptionResolver, so it has to be handled here.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        pd.setTitle("Request failed");
        return pd;
    }

    /** A third party refusing us is not our bug; say which one and what it said. */
    @ExceptionHandler({VapiAssistantProvisioner.ProvisioningException.class,
            VapiSyncService.VapiSyncException.class})
    public ProblemDetail handleUpstream(RuntimeException ex) {
        log.error("Upstream provider call failed", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        pd.setTitle("Provider unavailable");
        pd.setType(URI.create("https://voint.starsoft.com/errors/upstream"));
        return pd;
    }

    /**
     * A path nobody mapped is a 404, not a server error. Without this the catch-all below turns
     * every typo into a 500, which reads as "we are broken" instead of "that does not exist".
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ProblemDetail handleNoHandler(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Belə ünvan yoxdur");
        pd.setTitle("Not found");
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
        pd.setTitle("Internal server error");
        pd.setType(URI.create("https://voint.starsoft.com/errors/internal"));
        return pd;
    }
}
