package com.semih.randevuapi.common;

import com.semih.randevuapi.common.exception.CancellationNotAllowedException;
import com.semih.randevuapi.common.exception.ConflictException;
import com.semih.randevuapi.common.exception.EmailAlreadyUsedException;
import com.semih.randevuapi.common.exception.InvalidCredentialsException;
import com.semih.randevuapi.common.exception.InvalidRequestException;
import com.semih.randevuapi.common.exception.ResourceNotFoundException;
import com.semih.randevuapi.common.exception.SlotNotAvailableException;
import com.semih.randevuapi.payment.InvalidWebhookException;
import com.semih.randevuapi.payment.PaymentProviderException;
import com.semih.randevuapi.payment.PaymentsDisabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into RFC 9457 "problem detail" JSON responses, so every
 * error the API returns has the same shape: type, title, status, detail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 400 with a field -> message map, e.g. {"errors": {"email": "must be a well-formed email address"}}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Validation error");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    ProblemDetail handleEmailAlreadyUsed(EmailAlreadyUsedException ex) {
        return problem(HttpStatus.CONFLICT, "Email already in use", ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid credentials", ex.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
    }

    @ExceptionHandler(SlotNotAvailableException.class)
    ProblemDetail handleSlotNotAvailable(SlotNotAvailableException ex) {
        return problem(HttpStatus.CONFLICT, "Slot not available", ex.getMessage());
    }

    @ExceptionHandler(CancellationNotAllowedException.class)
    ProblemDetail handleCancellationNotAllowed(CancellationNotAllowedException ex) {
        return problem(HttpStatus.CONFLICT, "Cancellation not allowed", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getTitle(), ex.getMessage());
    }

    @ExceptionHandler(InvalidWebhookException.class)
    ProblemDetail handleInvalidWebhook(InvalidWebhookException ex) {
        log.warn("Rejected payment webhook: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, "Invalid webhook", ex.getMessage());
    }

    /** The provider's own error message may contain details meant for us, so it is only logged. */
    @ExceptionHandler(PaymentProviderException.class)
    ProblemDetail handlePaymentProvider(PaymentProviderException ex) {
        log.error("Payment provider error", ex);
        return problem(HttpStatus.BAD_GATEWAY, "Payment provider error", "The payment provider could not be reached");
    }

    @ExceptionHandler(PaymentsDisabledException.class)
    ProblemDetail handlePaymentsDisabled(PaymentsDisabledException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Online payment unavailable", ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    /** E.g. two concurrent registrations with the same email: the unique constraint wins. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflict", "The request conflicts with existing data");
    }

    /** Last resort: log the details, but never leak stack traces or internals to the client. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "Something went wrong");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
