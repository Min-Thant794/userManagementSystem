package com.minthanttun.usermanagementsystem.common.exception;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("remaining-unit")
public class GlobalExceptionHandlerTest {
    static Stream<Arguments> exceptionCases() {
        var handler = new GlobalExceptionHandler();
        return Stream.of(Arguments.of("duplicate", (Supplier<ProblemDetail>) () ->
                handler.handleDuplicate(new DuplicateResourceException("duplicate")), 409, "Duplicate Resource", "duplicate", null),
                Arguments.of("credentials", (Supplier<ProblemDetail>)
                        () -> handler.handleInvalidCredentials(new InvalidCredentialsException("bad credentials")), 401, "Authentication Failed", "bad credentials", null),
                Arguments.of("suspended", (Supplier<ProblemDetail>)
                        () -> handler.handleSuspended(new AccountSuspendedException("suspended")), 403, "Account Suspended", "suspended", null),
                Arguments.of("missing", (Supplier<ProblemDetail>)
                        () -> handler.handleNotFound(new ResourceNotFoundException("missing")), 404, "Resource Not Found", "missing", null),
                Arguments.of("last-admin", (Supplier<ProblemDetail>)
                        () -> handler.handleLastAdmin(new LastAdminException("last admin")), 409, "Cannot Remove Last Admin", "last admin", null),
                Arguments.of("profile", (Supplier<ProblemDetail>)
                        () -> handler.handleProfileIncomplete(new ProfileIncompleteException("incomplete")), 403, "Profile Incomplete", "incomplete", "complete_profile"),
                Arguments.of("email", (Supplier<ProblemDetail>)
                        () -> handler.handleEmailNotVerified(new EmailNotVerifiedException("unverified")), 403, "Email Not Verified", "unverified", "resend_verification"),
                Arguments.of("reuse", (Supplier<ProblemDetail>)
                        () -> handler.handleRefreshTokenReuse(new RefreshTokenReuseException("reused")), 401, "Session Compromised", "reused", null),
                Arguments.of("malformed", (Supplier<ProblemDetail>)
                        () -> handler.handleMessageNotReadable(new HttpMessageNotReadableException("internal parser details", new MockHttpInputMessage(new byte[0]))), 400,
                        "Malformed Request", "Request body contains invalid or unrecognized fields", null),
                Arguments.of("illegal", (Supplier<ProblemDetail>)
                        () -> handler.handleIllegalArgument(new IllegalArgumentException("invalid")), 400, "Invalid Request", "invalid", null),
                Arguments.of("upload", (Supplier<ProblemDetail>)
                        () -> handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(10)), 400, "File Too Large", "File exceeds maximum upload size", null)
                );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exceptionCases")
    void mapsExceptionToProblem(String label, Supplier<ProblemDetail> action, int status, String title, String detail, String nextAction) {
        ProblemDetail problem = action.get();
        assertThat(problem.getStatus()).isEqualTo(status);
        assertThat(problem.getTitle()).isEqualTo(title);
        assertThat(problem.getDetail()).isEqualTo(detail);
        assertThat(problem.getType().toString()).isEqualTo("about:blank");
        if (nextAction != null) {
            assertThat(problem.getProperties()).containsEntry("action", nextAction);
        }
        else {
            assertThat(problem.getProperties()).isNullOrEmpty();
        }
    }

    @Test
    void validationFailureIncludesMessagesForEachField() {
        var result = new BeanPropertyBindingResult(new Object(), "request");
        result.addError(new FieldError("request", "email", "Invalid email"));
        result.addError(new FieldError("request", "password", "Password too short"));
        var ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(result);
        ProblemDetail problem = new GlobalExceptionHandler().handleValidation(ex);
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Validation Failed");
        assertThat(problem.getDetail()).isEqualTo("One or more fields failed validation");
        assertThat(problem.getProperties()).containsEntry("errors",
                Map.of("email", "Invalid email", "password", "Password too short"));
    }
}
