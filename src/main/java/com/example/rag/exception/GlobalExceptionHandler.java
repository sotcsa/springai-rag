package com.example.rag.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<Map<String, String>> handleMaxSizeException(MaxUploadSizeExceededException e) {
                log.warn("File upload size exceeded: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                                .body(Map.of(
                                                "status", "ERROR",
                                                "message", "A fájl mérete meghaladja a megengedett maximumot (50MB)."));
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
                log.warn("Invalid argument: {}", e.getMessage());
                return ResponseEntity.badRequest()
                                .body(Map.of(
                                                "status", "ERROR",
                                                "message", e.getMessage()));
        }

        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException e) {
                if (e.getResourcePath() != null && e.getResourcePath().contains("favicon.ico")) {
                        log.trace("Favicon not found: {}", e.getMessage());
                } else {
                        log.warn("Resource not found: {}", e.getMessage());
                }
                return ResponseEntity.notFound().build();
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, String>> handleGenericException(Exception e) {
                log.error("Unexpected error: {}", e.getMessage(), e);
                return ResponseEntity.internalServerError()
                                .body(Map.of(
                                                "status", "ERROR",
                                                "message", "Váratlan hiba történt. Kérjük próbálja újra később."));
        }
}
