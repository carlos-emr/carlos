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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.util.ListResourceBundle;
import java.util.ResourceBundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #5853: a graph opened as a window that cannot be drawn says so in the viewer's language, with a
 * Retry button and the same status; an image load keeps the bare status its page already handles.
 */
@DisplayName("Graph failure page")
@Tag("unit")
@Tag("measurement")
class GraphFailurePageUnitTest {

    private static MockHttpServletRequest request(String destination, String accept) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/encounter/GraphMeasurements");
        if (destination != null) {
            request.addHeader("Sec-Fetch-Dest", destination);
        }
        if (accept != null) {
            request.addHeader("Accept", accept);
        }
        return request;
    }

    @ParameterizedTest(name = "Sec-Fetch-Dest {0}")
    @ValueSource(strings = {"document", "iframe", "frame"})
    @DisplayName("should treat a window or frame as a page load")
    void shouldTreatAWindowOrFrame_asAPageLoad(String destination) {
        assertThat(GraphFailurePage.isPageLoad(request(destination, "image/png"))).isTrue();
    }

    @Test
    @DisplayName("should treat an image load as an image, whatever it accepts")
    void shouldTreatAnImageLoad_asAnImage() {
        assertThat(GraphFailurePage.isPageLoad(request("image", "text/html,*/*"))).isFalse();
        assertThat(GraphFailurePage.isPageLoad(request(null, "image/avif,image/webp,image/*,*/*;q=0.8"))).isFalse();
        assertThat(GraphFailurePage.isPageLoad(request(null, null))).isFalse();
    }

    @Test
    @DisplayName("should fall back to Accept when the browser does not name the destination")
    void shouldFallBackToAccept_withoutADestination() {
        assertThat(GraphFailurePage.isPageLoad(request(null, "text/html,application/xhtml+xml,*/*;q=0.8"))).isTrue();
        assertThat(GraphFailurePage.isPageLoad(request(" ", "text/html"))).isTrue();
    }

    @Test
    @DisplayName("should answer an image load with the bare status, as before")
    void shouldAnswerAnImageLoad_withTheBareStatus() throws Exception {
        MockHttpServletResponse busy = new MockHttpServletResponse();
        GraphFailurePage.send(request("image", null), busy, 503, "Graph unavailable; retry shortly.");
        assertThat(busy.getStatus()).isEqualTo(503);
        assertThat(busy.getErrorMessage()).isEqualTo("Graph unavailable; retry shortly.");
        assertThat(busy.getHeader("Retry-After")).isEqualTo("5");
        assertThat(busy.getContentAsString()).isEmpty();

        MockHttpServletResponse invalid = new MockHttpServletResponse();
        GraphFailurePage.send(request("image", null), invalid, 422, "Graph unavailable");
        assertThat(invalid.getStatus()).isEqualTo(422);
        assertThat(invalid.getHeader("Retry-After")).isNull();
    }

    @Test
    @DisplayName("should answer a page load with a short page: status, message and Retry")
    void shouldAnswerAPageLoad_withAShortPage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        GraphFailurePage.send(request("document", null), response, 503, "ignored for a page");

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getErrorMessage()).as("not the container's error page").isNull();
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentType()).startsWith("text/html");
        assertThat(response.getContentAsString())
                .contains("<html lang=\"en\">")
                .contains("<p role=\"alert\" id=\"graphUnavailable\">Graph unavailable. Retry or contact your administrator.</p>")
                .contains("<button type=\"button\" onclick=\"window.location.reload()\">Retry</button>")
                .doesNotContain("CARLOS Error");
    }

    @Test
    @DisplayName("should say a data problem is in the stored values, with its status and no Retry")
    void shouldKeepTheStatus_forADataProblem() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        GraphFailurePage.send(request("document", null), response, 422, "ignored");

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getHeader("Retry-After")).isNull();
        assertThat(response.getContentAsString())
                .contains("<p role=\"alert\" id=\"graphUnavailable\">This graph cannot be drawn from the stored values."
                        + " Contact your administrator.</p>")
                .doesNotContain("<button")
                .doesNotContain("Retry");
    }

    @Test
    @DisplayName("should say a data problem in the viewer's language too")
    void shouldWriteTheDataProblem_inTheViewersLanguage() throws Exception {
        MockHttpServletRequest request = request("document", null);
        request.addHeader("Accept-Language", "fr-CA,fr;q=0.9");
        request.addPreferredLocale(java.util.Locale.CANADA_FRENCH);
        MockHttpServletResponse response = new MockHttpServletResponse();

        GraphFailurePage.send(request, response, 422, "ignored");

        assertThat(response.getContentAsString())
                .contains("Ce graphique ne peut pas être tracé à partir des valeurs enregistrées.")
                .doesNotContain("<button");
    }

    @Test
    @DisplayName("should write the page in the viewer's language")
    void shouldWriteThePage_inTheViewersLanguage() throws Exception {
        MockHttpServletRequest request = request("document", null);
        request.addHeader("Accept-Language", "fr-CA,fr;q=0.9");
        request.addPreferredLocale(java.util.Locale.CANADA_FRENCH);
        MockHttpServletResponse response = new MockHttpServletResponse();

        GraphFailurePage.send(request, response, 503, "ignored");

        assertThat(response.getContentAsString())
                .contains("<html lang=\"fr\">")
                .contains("Graphique indisponible.")
                .contains(">Réessayer</button>");
    }

    @Test
    @DisplayName("should fall back to English words for a label the bundle lacks")
    void shouldFallBackToEnglish_forAMissingLabel() {
        ResourceBundle empty = new ListResourceBundle() {
            @Override
            protected Object[][] getContents() {
                return new Object[0][];
            }
        };
        assertThat(GraphFailurePage.label(empty, GraphFailurePage.RETRY_KEY, "Retry")).isEqualTo("Retry");
    }
}
