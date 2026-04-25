/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link ErrorPageLogger}.
 *
 * <p>These exercise the exception-extraction logic (explicit page-context
 * exception vs servlet-error attribute fallback vs no-op) without spinning
 * up a JSP container. The actual log output goes to log4j; the assertions
 * verify only that the helper runs without throwing for each input shape.</p>
 *
 * @since 2026-04-25
 */
@DisplayName("ErrorPageLogger")
@Tag("unit")
class ErrorPageLoggerUnitTest {

    @Test
    void shouldNoOp_whenNoExceptionAvailable() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        assertThatCode(() -> ErrorPageLogger.logIfPresent(null, req))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldLogExplicitException_whenProvidedDirectly() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/carlos/billing/CA/ON/billingView");
        req.setMethod("GET");
        Throwable t = new RuntimeException("boom");
        assertThatCode(() -> ErrorPageLogger.logIfPresent(t, req))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldFallBackToServletErrorAttribute_whenExplicitExceptionIsNull() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute("jakarta.servlet.error.exception",
                new IllegalStateException("from container"));
        req.setAttribute("jakarta.servlet.error.request_uri",
                "/carlos/billing/CA/ON/billingONReview");
        req.setAttribute("jakarta.servlet.error.status_code", 500);
        req.setMethod("POST");
        assertThatCode(() -> ErrorPageLogger.logIfPresent(null, req))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldHandleNullRequest_withoutThrowing() {
        assertThatCode(() -> ErrorPageLogger.logIfPresent(new RuntimeException("x"), null))
                .doesNotThrowAnyException();
    }
}
