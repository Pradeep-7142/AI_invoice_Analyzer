package com.invoiceiq.exception;

import com.invoiceiq.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Translates every exception type the API can throw into a consistent
 * ApiError body. Stack traces are never returned to clients; they are
 * logged server-side with the correlation id instead.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
            .body(ApiError.of(ex.getStatus().value(), ex.getErrorCode(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage()))
            .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of(400, "VALIDATION_ERROR", "One or more fields are invalid.", request.getRequestURI(), violations));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of(400, "MALFORMED_REQUEST", "Request body could not be parsed.", request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(ApiError.of(413, "FILE_TOO_LARGE", "File exceeds the maximum allowed upload size.", request.getRequestURI()));
    }

    @ExceptionHandler({AccessDeniedException.class})
    public ResponseEntity<ApiError> handleAccessDenied(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiError.of(403, "ACCESS_DENIED", "You do not have permission to perform this action.", request.getRequestURI()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiError.of(401, "INVALID_CREDENTIALS", "Invalid email or password.", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.of(500, "INTERNAL_ERROR", "An unexpected error occurred. Please try again.", request.getRequestURI()));
    }
}
