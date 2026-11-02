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

package io.github.carlos_emr.carlos.commn.model.inbox;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pins the historical numeric-count semantics of the inbox result conversion. */
@Tag("unit")
class InboxResponseUnitTest extends CarlosUnitTestBase {

    @ParameterizedTest
    @MethodSource("resultCounts")
    void shouldPreserveResultCount_whenConvertingInboxItems(String input, int expected) {
        assertThat(responseWithCount(input).getLabResultData(null).getFirst().finalResultsCount).isEqualTo(expected);
    }

    static Stream<Arguments> resultCounts() {
        return Stream.of(
                Arguments.of(null, 0), Arguments.of("", 0), Arguments.of(" ", 0),
                Arguments.of("\t\n", 0), Arguments.of(" 12", 0), Arguments.of("12 ", 0),
                Arguments.of("+12", 0), Arguments.of("1.2", 0), Arguments.of("1e2", 0),
                Arguments.of("--1", 0), Arguments.of("- 1", 0), Arguments.of("abc", 0),
                Arguments.of("0", 0), Arguments.of("0012", 12), Arguments.of("-12", -12),
                Arguments.of("2147483647", Integer.MAX_VALUE), Arguments.of("-2147483648", Integer.MIN_VALUE),
                Arguments.of("１２", 12), Arguments.of("-١٢", -12), Arguments.of("१२", 12),
                Arguments.of("\uD835\uDFD9", 0), Arguments.of("\u00a0", 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-", "2147483648", "-2147483649", "999999999999999999999"})
    void shouldPreserveParseFailure_whenCountIsNotAnInteger(String input) {
        assertThatThrownBy(() -> responseWithCount(input).getLabResultData(null))
                .isInstanceOf(NumberFormatException.class);
    }

    private static InboxResponse responseWithCount(String count) {
        InboxItemPK id = new InboxItemPK();
        id.setSegmentId(1);
        id.setLabType("HL7");
        InboxItem item = new InboxItem();
        item.setId(id);
        item.setFirstName("TEST");
        item.setLastName("SYNTHETIC");
        item.setFinalResultsCount(count);
        InboxResponse response = new InboxResponse();
        response.setInboxItems(List.of(item));
        return response;
    }
}
