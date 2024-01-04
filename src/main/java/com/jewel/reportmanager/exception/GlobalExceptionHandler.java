package com.jewel.reportmanager.exception;

import com.jewel.reportmanager.dto.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

import javax.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;

import static com.jewel.reportmanager.enums.OperationType.Failure;
import static com.jewel.reportmanager.utils.ReportResponseConstants.INVALID_DATA;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.validation.BindException.class)
    public ResponseEntity<Object> validationError(org.springframework.validation.BindException ex) {
        Map<String, String> errorMap = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errorMap.put(error.getField(), error.getDefaultMessage());
        });
        log.error("Exception occurred: {}",ex.getAllErrors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Response(errorMap, INVALID_DATA, Failure));
    }

    @ExceptionHandler({NullPointerException.class, IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Object> handleValidationExceptions(Exception ex) {
        log.error("Exception occurred: {}",ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Response(null, INVALID_DATA, Failure));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> requiredParamMissing(MissingServletRequestParameterException ex) {
        log.error("Exception occurred: {}",ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Response(null, "Missing required parameter: " + ex.getParameterName(), Failure));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> requiredParamInvalid(ConstraintViolationException ex) {
        String error = ex.getMessage();
        log.error("Exception occurred: {}",ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Response(null, error.split("\\.")[1], Failure));
    }
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Object> requiredHeaderMissing(MissingRequestHeaderException ex) {
        log.error("Exception occurred: {}",ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Response(null, "Missing required header: " + ex.getHeaderName(), Failure));
    }

    @ExceptionHandler(HttpClientErrorException.BadRequest.class)
    public ResponseEntity<Object> clientEntityBadRequest(HttpClientErrorException.BadRequest ex) {
        log.error("Exception occurred: {}",ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Response(null, "Invalid ProjectId or azure state", Failure));
    }

    @ExceptionHandler(HttpClientErrorException.Unauthorized.class)
    public ResponseEntity<Object> clientEntityUnauthorized(HttpClientErrorException.Unauthorized ex) {
        log.error("Exception occurred: {}",ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Response(null, "Invalid email or access Token", Failure));
    }

    @ExceptionHandler({Exception.class})
    public ResponseEntity<Object> handleAll(final Exception ex) {
        log.error("Exception occurred: ",ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new Response(null, "Exception occurred. Please try again later.", Failure));
    }
}
