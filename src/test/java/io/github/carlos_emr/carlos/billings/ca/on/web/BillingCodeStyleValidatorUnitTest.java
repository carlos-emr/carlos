/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("billing")
class BillingCodeStyleValidatorUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "color:#abc", "color:#abcd;", "color:#123456;", "color:#12345678;", " COLOR : ReBeccaPurple ; ",
            "background-color:transparent;color:currentColor;", "font-size:medium;", "font-size:12px;", "font-size:1.25em;",
            "font-size:100%;", "font-style:oblique;font-variant:small-caps;font-weight:bolder;", "font-weight:400;",
            "text-decoration:underline overline;", "color:red;color:blue;", "text-decoration:none;"})
    void shouldAcceptSupportedDeclarations_withoutRequiringPickerFormatting(String text) {
        assertThat(BillingCodeStyleValidator.isSupported(text)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", ";;;", "color", "color:", "color:#12", "color:#12345", "color:#1234567", "color:unknown",
            "color:red!important", "background:url(https://example.invalid)", "color:expression(alert(1))",
            "color:r\\65 d", "color:red/*comment*/", "color:red;position:fixed", "font-size:-10px", "font-weight:10000",
            "text-decoration:garbage", "font-size:12px trailing", "color:red;broken"})
    void shouldRejectUnsupportedOrMalformedInput(String text) {
        assertThat(BillingCodeStyleValidator.isSupported(text)).isFalse();
    }

    @Test
    void shouldRejectDeclarations_whenTextExceedsLimit() {
        assertThat(BillingCodeStyleValidator.isSupported("color:red;".repeat(500))).isFalse();
    }
}
