/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.commn.dao.QueryPage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Pins {@link ServerTablePage}, the helper added with the server-paged tables (commit 9c2014ef3867,
 * #6336): {@code requested} accepts only 1 to 9 ASCII digits and rejects everything else, and
 * {@code publish} exposes the page, context-path action, method and a defensive copy of the
 * retained parameters to the table fragment.
 */
@Tag("unit")
@Tag("utility")
class ServerTablePageUnitTest {

    @Test
    void shouldReturnPageZero_whenTablePageIsAbsent() {
        assertThat(ServerTablePage.requested(new MockHttpServletRequest())).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "7", "007", "999999999"})
    void shouldReadNumericPage_whenTablePageIsOneToNineDigits(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("tablePage", value);

        assertThat(ServerTablePage.requested(request)).isEqualTo(Integer.parseInt(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-1", "+1", "1.5", "abc", " 1", "1 ", "1000000000", "١"})
    void shouldRejectTablePage_whenItIsNotOneToNineAsciiDigits(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("tablePage", value);

        assertThatThrownBy(() -> ServerTablePage.requested(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid table page");
    }

    @Test
    void shouldPublishNavigationAttributes_withContextPathAndParameterCopy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        QueryPage<String> page = new QueryPage<>(List.of("FAKE-row"), 31, 1, 15);
        Map<String, String[]> parameters = new LinkedHashMap<>();
        parameters.put("method", new String[] {"searchByName"});

        ServerTablePage.publish(request, page, "/admin/ManageBillingReferral", "get", parameters);
        parameters.put("search", new String[] {"added after publish"});

        assertThat(request.getAttribute("serverTablePage")).isSameAs(page);
        assertThat(request.getAttribute("serverTableAction")).isEqualTo("/carlos/admin/ManageBillingReferral");
        assertThat(request.getAttribute("serverTableMethod")).isEqualTo("get");
        Object published = request.getAttribute("serverTableParameters");
        assertThat(published).isInstanceOf(LinkedHashMap.class).isNotSameAs(parameters);
        @SuppressWarnings("unchecked")
        Map<String, String[]> copy = (Map<String, String[]>) published;
        assertThat(copy).containsOnlyKeys("method");
        assertThat(copy.get("method")).containsExactly("searchByName");
    }
}
