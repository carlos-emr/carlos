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
package io.github.carlos_emr.carlos.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@Tag("fast")
@Tag("security")
@DisplayName("SqlIdentifierValidator")
class SqlIdentifierValidatorUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "code",
            "_code",
            "code_2026",
            "schema.table",
            "schema.table.column"
    })
    @DisplayName("should accept dotted SQL identifiers")
    void shouldAcceptDottedSqlIdentifiers_whenIdentifierIsValid(String identifier) {
        assertThat(SqlIdentifierValidator.isValidIdentifier(identifier)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "1code",
            ".code",
            "code.",
            "schema..table",
            "schema.table;drop",
            "schema.table /*",
            "schema table",
            "schema.table\nwhere"
    })
    @DisplayName("should reject invalid SQL identifiers")
    void shouldRejectInvalidSqlIdentifiers_whenIdentifierIsInvalid(String identifier) {
        assertThat(SqlIdentifierValidator.isValidIdentifier(identifier)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "formBCAR",
            "schema.formBCAR",
            "formBCAR f",
            "schema.formBCAR AS f",
            "schema.formBCAR as f",
            "formBCAR f, formBCNewBorn n",
            " formBCAR , schema.formBCNewBorn AS n "
    })
    @DisplayName("should accept report table reference lists")
    void shouldAcceptReportTableReferenceLists_whenReferenceListIsValid(String tableReferences) {
        assertThat(SqlIdentifierValidator.isValidTableReferenceList(tableReferences)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "formBCAR,",
            ",formBCAR",
            "schema.one.two",
            "formBCAR AS",
            "formBCAR f extra",
            "formBCAR;drop",
            "formBCAR /*",
            "formBCAR f, demographic d OR 1=1"
    })
    @DisplayName("should reject invalid report table reference lists")
    void shouldRejectInvalidReportTableReferenceLists_whenReferenceListIsInvalid(String tableReferences) {
        assertThat(SqlIdentifierValidator.isValidTableReferenceList(tableReferences)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "code",
            "s.code",
            "IFNULL(buf1,'')",
            "IFNULL(buf1, 'none')",
            "IFNULL(s.buf1, 0)",
            "IFNULL(s.buf1, 0.0)",
            "IFNULL(buf1, ',')",
            "IFNULL(buf1, 'some (value)')",
            "IFNULL(buf1, 'O''Connor')",
            "IFNULL(TRIM(buf1), '')"
    })
    @DisplayName("should accept lookup field expressions")
    void shouldAcceptLookupFieldExpressions_whenExpressionIsValid(String expression) {
        assertThat(SqlIdentifierValidator.isValidFieldExpression(expression)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "42",
            "'literal'",
            "1code",
            "buf1 + 1",
            "IFNULL(buf1,''); drop table x",
            "IFNULL(buf1,'unterminated)",
            "IFNULL(buf1,(select x))",
            "IFNULL(buf1, 'x-y')",
            "IFNULL(buf1, 'O'';drop')",
            "schema.function_name(value)",
            "code alias"
    })
    @DisplayName("should reject invalid lookup field expressions")
    void shouldRejectInvalidLookupFieldExpressions_whenExpressionIsInvalid(String expression) {
        assertThat(SqlIdentifierValidator.isValidFieldExpression(expression)).isFalse();
    }

    @Test
    @DisplayName("should reject deeply nested function calls when expression nesting exceeds limit")
    void shouldRejectDeeplyNestedFunctionCalls_whenExpressionNestingExceedsLimit() {
        String expression = "f(".repeat(128) + "code" + ")".repeat(128);

        assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                assertThat(SqlIdentifierValidator.isValidFieldExpression(expression)).isFalse());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "identifier",
            "tableReferenceList",
            "fieldExpression"
    })
    @DisplayName("should handle long adversarial inputs without regex backtracking")
    void shouldHandleLongAdversarialInputs_withoutRegexBacktracking(String validationType) {
        String adversarialInput = "a".repeat(20_000) + ".".repeat(2_000) + "!";

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            boolean result = switch (validationType) {
                case "identifier" -> SqlIdentifierValidator.isValidIdentifier(adversarialInput);
                case "tableReferenceList" -> SqlIdentifierValidator.isValidTableReferenceList(adversarialInput);
                case "fieldExpression" -> SqlIdentifierValidator.isValidFieldExpression(adversarialInput);
                default -> throw new IllegalArgumentException(validationType);
            };
            assertThat(result).isFalse();
        });
    }
}
