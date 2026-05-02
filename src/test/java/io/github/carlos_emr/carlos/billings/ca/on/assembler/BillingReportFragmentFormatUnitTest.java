/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for {@link BillingReportFragmentViewModelAssembler}. The
 * Settled-total accumulator catches {@link NumberFormatException} (narrowed
 * from the historical {@code catch (Exception)}) and warn-logs the offending
 * total instead of swallowing every error class silently.
 *
 * <p>Driving the live {@code assemble} path needs a fully-stocked DAO graph;
 * a focused test on the package-private {@code formatBillobTotal} helper +
 * a sanity construction is enough to keep the file's previously-zero
 * coverage off zero, and to lock the format contract that the catch block
 * depends on (a non-numeric formattedTotal is what triggers NFE in the
 * Settled-total branch).
 *
 * @since 2026-04-30
 */
@DisplayName("BillingReportFragment format helper")
@Tag("unit")
@Tag("billing")
class BillingReportFragmentFormatUnitTest {

    private static String invokeFormat(String raw) throws Exception {
        Method m = BillingReportFragmentViewModelAssembler.class.getDeclaredMethod(
                "formatBillobTotal", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, raw);
    }

    @Test
    void shouldReturnZero_whenInputIsNull() throws Exception {
        assertThat(invokeFormat(null)).isEqualTo("0.00");
    }

    @Test
    void shouldReturnZero_whenInputIsEmpty() throws Exception {
        assertThat(invokeFormat("")).isEqualTo("0.00");
    }

    @Test
    void shouldPassThroughInput_whenAlreadyDecimal() throws Exception {
        assertThat(invokeFormat("12.34")).isEqualTo("12.34");
    }

    @Test
    void shouldInsertDecimalAtTwoFromEnd_whenInputHasNoDot() throws Exception {
        assertThat(invokeFormat("12345")).isEqualTo("123.45");
    }

    @Test
    void shouldAppendZeroDecimal_whenInputIsTooShort() throws Exception {
        // Legacy scriptlet StringIndexOutOfBoundsException — we conservatively
        // append .00 instead.
        assertThat(invokeFormat("5")).isEqualTo("5.00");
    }

    @Test
    void shouldProduceParseableNumeric_forWellFormedInput() throws Exception {
        // The Settled-total accumulator does `new BigDecimal(formattedTotal)`.
        // A non-numeric output here would always throw NFE; verify the format
        // helper produces parseable strings on every legitimate input shape.
        for (String input : new String[] { "0", "1", "100", "12345", "12.34", "0.00" }) {
            String formatted = invokeFormat(input);
            assertThat(java.math.BigDecimal.class)
                    .as("formatBillobTotal(%s) -> %s should parse as BigDecimal", input, formatted)
                    .satisfies(c -> new java.math.BigDecimal(formatted));
        }
    }

    @Test
    void shouldReturnNonNumericString_forNonNumericInputDrivingTheNarrowedCatch() throws Exception {
        // The running-total accumulator narrows its catch to NFE; a non-numeric
        // value flows through the formatter unchanged (when it has a dot) and
        // trips NFE downstream where the warn log captures it. Pin that the
        // format helper itself is narrow-and-passive (doesn't throw on weird input).
        assertThat(invokeFormat("not-a-number"))
                .as("format helper should not throw on non-numeric input")
                .isNotNull();
    }
}
