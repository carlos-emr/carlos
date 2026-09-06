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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ScheduleNav}.
 *
 * @since 2026-09-06
 */
@DisplayName("ScheduleNav")
@Tag("unit")
class ScheduleNavUnitTest {

    private static MockHttpServletRequest requestWith(String scheduleNavValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (scheduleNavValue != null) {
            request.addParameter(ScheduleNav.PARAM, scheduleNavValue);
        }
        return request;
    }

    @Test
    @DisplayName("should report active when scheduleNav=1 is present")
    void shouldReportActive_whenFlagIsOne() {
        assertThat(ScheduleNav.isActive(requestWith("1"))).isTrue();
    }

    @Test
    @DisplayName("should report inactive when the flag is absent, empty or not 1")
    void shouldReportInactive_whenFlagIsNotOne() {
        assertThat(ScheduleNav.isActive(requestWith(null))).isFalse();
        assertThat(ScheduleNav.isActive(requestWith(""))).isFalse();
        assertThat(ScheduleNav.isActive(requestWith("0"))).isFalse();
        assertThat(ScheduleNav.isActive(requestWith("true"))).isFalse();
        assertThat(ScheduleNav.isActive(null)).isFalse();
    }

    @Test
    @DisplayName("should append with ? when the url carries no query string")
    void shouldAppendWithQuestionMark_whenUrlHasNoQuery() {
        assertThat(ScheduleNav.append("/carlos/documentManager/ViewDocumentReport", requestWith("1")))
                .isEqualTo("/carlos/documentManager/ViewDocumentReport?scheduleNav=1");
    }

    @Test
    @DisplayName("should append with & when the url already has a query string")
    void shouldAppendWithAmpersand_whenUrlHasQuery() {
        assertThat(ScheduleNav.append("/carlos/documentManager/ViewDocumentReport?function=providers",
                requestWith("1")))
                .isEqualTo("/carlos/documentManager/ViewDocumentReport?function=providers&scheduleNav=1");
    }

    @Test
    @DisplayName("should insert the flag before a fragment rather than after it")
    void shouldInsertBeforeFragment_whenUrlHasFragment() {
        // A parameter written after '#' never reaches the server, so appending naively would
        // silently drop the flag on any redirect target carrying a fragment.
        assertThat(ScheduleNav.append("/carlos/documentManager/ViewDocumentReport#docs",
                requestWith("1")))
                .isEqualTo("/carlos/documentManager/ViewDocumentReport?scheduleNav=1#docs");
        assertThat(ScheduleNav.append("/carlos/documentManager/ViewDocumentReport?function=providers#docs",
                requestWith("1")))
                .isEqualTo("/carlos/documentManager/ViewDocumentReport?function=providers&scheduleNav=1#docs");
    }

    @Test
    @DisplayName("should leave the url untouched when the shell is not active")
    void shouldLeaveUrlUnchanged_whenShellInactive() {
        String url = "/carlos/documentManager/ViewDocumentReport?function=providers";
        assertThat(ScheduleNav.append(url, requestWith(null))).isEqualTo(url);
        assertThat(ScheduleNav.append(null, requestWith("1"))).isNull();
    }

    @Test
    @DisplayName("should yield a skippable null param value when the shell is not active")
    void shouldYieldNullParamValue_whenShellInactive() {
        // The documentManager redirect builders drop null-valued parameters, so this is what
        // lets the delete/undelete actions add the flag unconditionally.
        assertThat(ScheduleNav.paramValue(requestWith("1"))).isEqualTo("1");
        assertThat(ScheduleNav.paramValue(requestWith(null))).isNull();
    }
}
