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
package io.github.carlos_emr.carlos.form.gate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FormShortcutRouteResolver")
@Tag("unit")
@Tag("form")
class FormShortcutRouteResolverUnitTest {

    @Test
    void shouldResolveDirectFormRoute_whenShortcutHasTrailingDemographicParameter() {
        String route = FormShortcutRouteResolver.resolve(
                new String[] {"../form/formannual.jsp?demographic_no=", "9"},
                "3",
                "9",
                null,
                null);

        assertThat(route).isEqualTo("/form/formannual?demographic_no=3&formId=9");
    }

    @Test
    void shouldUseLatestFormId_whenFormIdRequestsLatest() {
        String route = FormShortcutRouteResolver.resolve(
                new String[] {"../form/formannual.jsp?demographic_no=", "9"},
                "3",
                "LaTeSt",
                null,
                null);

        assertThat(route).isEqualTo("/form/formannual?demographic_no=3&formId=9");
    }

    @Test
    void shouldPreserveNewFormRoute_whenFormIdIsZero() {
        String route = FormShortcutRouteResolver.resolve(
                new String[] {"../form/formannual.jsp?demographic_no=", "9"},
                "3",
                "0",
                null,
                null);

        assertThat(route).isEqualTo("/form/formannual?demographic_no=3&formId=0");
    }

    @Test
    void shouldPreserveHistoryWarning_whenRequestedFormIsOlderThanLatest() {
        String route = FormShortcutRouteResolver.resolve(
                new String[] {"../form/formannual.jsp?demographic_no=", "9"},
                "3",
                "6",
                "123",
                "456");

        assertThat(route)
                .isEqualTo("/form/formannual?demographic_no=3&formId=6&warning=history&appointmentNo=123&provNo=456");
    }

    @Test
    void shouldRemoveReservedSelectionParameters_whenShortcutPathAlreadyHasQueryValues() {
        String route = FormShortcutRouteResolver.resolve(
                new String[] {"../form/formannual.jsp?demographic_no=999&formId=111&foo=bar&demographicNo=888", "9"},
                "3",
                "6",
                null,
                null);

        assertThat(route).isEqualTo("/form/formannual?foo=bar&demographic_no=3&formId=6&warning=history");
    }

    @Test
    void shouldEncodeQueryValues_whenOptionalParametersContainSpaces() {
        String route = FormShortcutRouteResolver.resolve(
                new String[] {"../form/formannual.jsp?demographic_no=", "9"},
                "3",
                "9",
                "appt 1",
                "prov/1");

        assertThat(route)
                .isEqualTo("/form/formannual?demographic_no=3&formId=9&appointmentNo=appt+1&provNo=prov%2F1");
    }

    @Test
    void shouldRejectUnsafeShortcutPath_whenOutsideFormRoutes() {
        assertThatThrownBy(() -> FormShortcutRouteResolver.resolve(
                new String[] {"../admin/index.jsp", "9"},
                "3",
                "9",
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid form path");
    }

    @Test
    void shouldRejectDemographicNumber_whenValueIsBlank() {
        assertThatThrownBy(() -> FormShortcutRouteResolver.resolve(
                new String[] {"../form/formannual.jsp?demographic_no=", "9"},
                " ",
                "9",
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid demographic number");
    }

    @Test
    void shouldRejectDemographicNumber_whenValueIsZero() {
        assertThatThrownBy(() -> FormShortcutRouteResolver.resolve(
                new String[] {"../form/formannual.jsp?demographic_no=", "9"},
                "0",
                "9",
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid demographic number");
    }

    @Test
    void shouldRejectFormId_whenValueIsNegative() {
        assertThatThrownBy(() -> FormShortcutRouteResolver.resolve(
                new String[] {"../form/formannual.jsp?demographic_no=", "9"},
                "3",
                "-1",
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid form id");
    }

    @Test
    void shouldRejectFormName_whenValueIsBlank() {
        assertThatThrownBy(() -> FormShortcutRouteResolver.resolve(
                "3",
                " ",
                "9",
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid form name");
    }
}
