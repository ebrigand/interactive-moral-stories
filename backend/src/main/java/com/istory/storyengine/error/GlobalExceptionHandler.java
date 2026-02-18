package com.istory.storyengine.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
            IllegalStateException.class
    })
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException e, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, e, req);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception e, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, e, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception e, HttpServletRequest req) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, e, req);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, Exception e, HttpServletRequest req) {
        String errorId = UUID.randomUUID().toString().substring(0, 10);
        String reqId = (String) req.getAttribute("reqId");

        // ✅ LOG SERVEUR avec stacktrace
        log.error("[reqId={}] [errorId={}] {} {} -> {} : {}",
                reqId, errorId, req.getMethod(), req.getRequestURI(), status.value(), e.toString(), e);

        ApiError body = new ApiError(
                errorId,
                reqId == null ? "" : reqId,
                status.value(),
                status.getReasonPhrase(),
                safeMessage(e),
                req.getRequestURI(),
                OffsetDateTime.now().toString()
        );

        return ResponseEntity.status(status).body(body);
    }

    private String safeMessage(Exception e) {
        // Évite d’exposer trop d’infos en prod; ici on garde simple.
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) return e.getClass().getSimpleName();
        return msg.length() > 400 ? msg.substring(0, 400) + "…" : msg;
    }
}
