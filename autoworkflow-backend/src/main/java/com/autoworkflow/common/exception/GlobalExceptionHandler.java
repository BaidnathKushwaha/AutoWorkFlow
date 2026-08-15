package com.autoworkflow.common.exception;

import com.autoworkflow.common.response.ErrorResponse;
import com.autoworkflow.integration.http.IntegrationApiExecutor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.autoworkflow.common.llm.AiException;
import com.autoworkflow.common.llm.AiProviderException;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Endpoint or resource not found: " + ex.getResourcePath(), req, null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), req, null);
    }

    @ExceptionHandler({BadCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleBadCredentials(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid email or password", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", req, null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    @ExceptionHandler(WorkflowException.class)
    public ResponseEntity<ErrorResponse> handleWorkflow(WorkflowException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    @ExceptionHandler(NodeExecutionException.class)
    public ResponseEntity<ErrorResponse> handleNodeExecution(NodeExecutionException ex, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY,
                "Node '" + ex.getNodeType() + "' (" + ex.getNodeId() + ") failed: " + ex.getMessage(), req, null);
    }

    /**
     * More specific than the plain IntegrationException handler below — Spring
     * routes IntegrationApiException here since it's a subclass. Surfaces
     * provider/operation/retryable in `details` so the frontend can decide
     * whether to offer a "Retry" button or a "Reconnect" prompt.
     */
    @ExceptionHandler(IntegrationApiException.class)
    public ResponseEntity<ErrorResponse> handleIntegrationApi(IntegrationApiException ex, HttpServletRequest req) {
        List<String> details = List.of(
                "provider=" + ex.getProvider(),
                "operation=" + ex.getOperation(),
                "retryable=" + ex.isRetryable()
        );
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), req, details);
    }

    @ExceptionHandler(IntegrationException.class)
    public ResponseEntity<ErrorResponse> handleIntegration(IntegrationException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), req, null);
    }

    @ExceptionHandler(OpenAIException.class)
    public ResponseEntity<ErrorResponse> handleOpenAI(OpenAIException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, details);
    }

    @ExceptionHandler(AiProviderException.class)
public ResponseEntity<ErrorResponse> handleAiProvider(
        AiProviderException ex,
        HttpServletRequest req
) {
    return build(
            HttpStatus.BAD_GATEWAY,
            ex.getMessage(),
            req,
            null
    );
}

@ExceptionHandler(AiException.class)
public ResponseEntity<ErrorResponse> handleAi(
        AiException ex,
        HttpServletRequest req
) {
    return build(
            HttpStatus.BAD_GATEWAY,
            "AI provider request failed. Please try again.",
            req,
            null
    );
}

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).error("Unhandled exception at " + req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred: " + ex.getMessage(), req, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest req, List<String> details) {
        ErrorResponse body = new ErrorResponse(message, req.getRequestURI(), status.value(), details);
        return ResponseEntity.status(status).body(body);
    }
}
