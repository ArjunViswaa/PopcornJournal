package com.popcornjournal.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Every error leaves the API as an RFC 9457 problem document. Messages are written for
 * people; stack traces and SQL never reach the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record FieldProblem(String field, String message) {
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail noRoute(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", "No endpoint at " + ex.getResourcePath());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex) {
        ProblemDetail body = problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
        if (!ex.getConflictsWith().isEmpty()) {
            body.setProperty("conflictsWith", ex.getConflictsWith());
        }
        return body;
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalid(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        List<FieldProblem> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldProblem(
                        error instanceof org.springframework.validation.FieldError fe ? fe.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
        return validationProblem(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail parameterValidation(HandlerMethodValidationException ex) {
        List<FieldProblem> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldProblem(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return validationProblem(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException ex) {
        List<FieldProblem> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldProblem(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return validationProblem(errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    ProblemDetail unreadable(Exception ex) {
        String detail = switch (ex) {
            case MethodArgumentTypeMismatchException mismatch ->
                    "Parameter '" + mismatch.getName() + "' has an invalid value";
            case MissingServletRequestParameterException missing ->
                    "Parameter '" + missing.getParameterName() + "' is required";
            case MissingServletRequestPartException missing ->
                    "Request part '" + missing.getRequestPartName() + "' is required";
            default -> "The request body could not be read. Check the JSON and enum values.";
        };
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", "Spreadsheets up to 5 MB can be uploaded");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", ex.getMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail mediaType(HttpMediaTypeNotSupportedException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException ex) {
        // The constraint name is useful in the log but would leak schema details to the client.
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The change conflicts with existing data (for example a duplicate entry)");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error",
                "Something went wrong on the server. Details are in the server log.");
    }

    private static ProblemDetail validationProblem(List<FieldProblem> errors) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "Some fields need attention");
        body.setProperty("errors", errors);
        return body;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        return body;
    }
}
