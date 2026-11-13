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
import java.util.Locale;
import java.util.ResourceBundle;

import io.github.carlos_emr.carlos.eform.util.ChartRenderFailure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #5853: a graph opened as a window that cannot be drawn says what went wrong in the viewer's language, with
 * the same status, and a Retry button where retrying can help; an image load keeps the bare status its page
 * already handles.
 */
@DisplayName("Graph failure page")
@Tag("unit")
@Tag("measurement")
class GraphFailurePageUnitTest {

    private static final ChartRenderFailure BUSY = new ChartRenderFailure(ChartRenderFailure.Reason.BUSY, "busy");

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
    @DisplayName("should match the headers ignoring case, as media types are case-insensitive")
    void shouldMatchTheHeaders_ignoringCase() {
        assertThat(GraphFailurePage.isPageLoad(request(null, "TEXT/HTML,*/*"))).isTrue();
        assertThat(GraphFailurePage.isPageLoad(request(null, "Text/Html"))).isTrue();
        assertThat(GraphFailurePage.isPageLoad(request("Document", "image/png"))).isTrue();
        assertThat(GraphFailurePage.isPageLoad(request("IMAGE", "TEXT/HTML"))).isFalse();
    }

    @Test
    @DisplayName("should answer an image load with the bare status, as before")
    void shouldAnswerAnImageLoad_withTheBareStatus() throws Exception {
        MockHttpServletResponse busy = new MockHttpServletResponse();
        GraphFailurePage.send(request("image", null), busy, BUSY, "Graph unavailable; retry shortly.");
        assertThat(busy.getStatus()).isEqualTo(503);
        assertThat(busy.getErrorMessage()).isEqualTo("Graph unavailable; retry shortly.");
        assertThat(busy.getHeader("Retry-After")).isEqualTo("5");
        assertThat(busy.getContentAsString()).isEmpty();

        MockHttpServletResponse invalid = new MockHttpServletResponse();
        GraphFailurePage.send(request("image", null), invalid, new IllegalArgumentException("FAKE"), "Graph unavailable");
        assertThat(invalid.getStatus()).isEqualTo(422);
        assertThat(invalid.getHeader("Retry-After")).isNull();
    }

    @Test
    @DisplayName("should answer a page load with a short page: status, message and Retry")
    void shouldAnswerAPageLoad_withAShortPage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        GraphFailurePage.send(request("document", null), response, BUSY, "ignored for a page");

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getErrorMessage()).as("not the container's error page").isNull();
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentType()).startsWith("text/html");
        assertThat(response.getContentAsString())
                .contains("<html lang=\"en\">")
                .contains("<p role=\"alert\" id=\"graphUnavailable\" class=\"alert alert-warning\">The chart drawing"
                        + " service is busy. Try again in a moment.</p>")
                .contains("<button type=\"button\" class=\"btn btn-secondary\" onclick=\"window.location.reload()\">"
                        + "Retry</button>")
                .doesNotContain("CARLOS Error");
    }

    @Test
    @DisplayName("should look like the CARLOS error page: the bundled Bootstrap, a grey background, a centred box")
    void shouldStyleThePage_likeTheCarlosErrorPage() throws Exception {
        MockHttpServletRequest request = request("document", null);
        request.setContextPath("/carlos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        GraphFailurePage.send(request, response, BUSY, "ignored");

        assertThat(response.getContentAsString())
                .contains("<link rel=\"stylesheet\" href=\"/carlos/library/bootstrap/5.3.8/css/bootstrap.min.css\">")
                .contains("<link rel=\"shortcut icon\" href=\"/carlos/images/favicon.ico\">")
                .contains("<style>body { background-color: #F5F5F5; }</style>")
                .contains("<div class=\"container text-center mt-5\">");
    }

    @Test
    @DisplayName("should encode the context path in the stylesheet and icon links")
    void shouldEncodeTheContextPath_inTheLinks() throws Exception {
        MockHttpServletRequest request = request("document", null);
        request.setContextPath("/c\"x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        GraphFailurePage.send(request, response, BUSY, "ignored");

        assertThat(response.getContentAsString())
                .contains("href=\"/c&#34;x/library/bootstrap/5.3.8/css/bootstrap.min.css\"")
                .contains("href=\"/c&#34;x/images/favicon.ico\"")
                .doesNotContain("/c\"x");
    }

    @Test
    @DisplayName("should say a data problem is in the stored values, with its status and no Retry")
    void shouldKeepTheStatus_forADataProblem() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        GraphFailurePage.send(request("document", null), response, new IllegalArgumentException("FAKE"), "ignored");

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getHeader("Retry-After")).isNull();
        assertThat(response.getContentAsString())
                .contains("<p role=\"alert\" id=\"graphUnavailable\" class=\"alert alert-danger\">This graph cannot"
                        + " be drawn from the stored values. Contact your administrator.</p>")
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

        GraphFailurePage.send(request, response, new java.text.ParseException("FAKE", 0), "ignored");

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

        GraphFailurePage.send(request, response, BUSY, "ignored");

        assertThat(response.getContentAsString())
                .contains("<html lang=\"fr\">")
                .contains("Le service de tracé des graphiques est occupé.")
                .contains(">Réessayer</button>");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ChartRenderFailure.Reason.class, names = {"UNAVAILABLE", "INTERRUPTED"})
    @DisplayName("should say the drawing service is not available, with Retry, when it is down")
    void shouldSayTheServiceIsDown_withRetry(ChartRenderFailure.Reason reason) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        GraphFailurePage.send(request("document", null), response, new ChartRenderFailure(reason, "down"), "ignored");

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString())
                .contains("class=\"alert alert-warning\">The chart drawing service is not available")
                .contains("The chart drawing service is not available right now. Try again in a moment, and contact"
                        + " your administrator if this keeps happening.")
                .contains(">Retry</button>");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"draw", "internal"})
    @DisplayName("should say an unexpected problem was logged, with Retry, for anything else")
    void shouldSayAnUnexpectedProblemWasLogged_withRetry(String kind) throws Exception {
        Throwable failure = "draw".equals(kind)
                ? new ChartRenderFailure(ChartRenderFailure.Reason.DRAW_FAILED, "draw failed")
                : new IllegalStateException("FAKE");
        MockHttpServletResponse response = new MockHttpServletResponse();

        GraphFailurePage.send(request("document", null), response, failure, "ignored");

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeader("Retry-After")).isNull();
        assertThat(response.getContentAsString())
                .contains("class=\"alert alert-warning\">This graph could not be drawn")
                .contains("This graph could not be drawn because of an unexpected problem. The problem has been logged;"
                        + " contact your administrator if it keeps happening.")
                .contains(">Retry</button>");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "BLOOD_PRESSURE_FORMAT|Blood pressure|This graph cannot be drawn: some stored Blood pressure values are not in the form 120/80.",
            "NO_DATE|Weight|This graph cannot be drawn: a stored Weight value has no date.",
            "UNREADABLE_DATE|Synthetic potassium|This graph cannot be drawn: a stored Synthetic potassium result has a date that cannot be read.",
            "REFERENCE_RANGE|Synthetic potassium|This graph cannot be drawn: a stored reference range for Synthetic potassium cannot be read.",
            "PRESCRIPTION_START||The medication graph cannot be drawn: a prescription has no start date."})
    @DisplayName("should name what is wrong with the stored values, and the type, with no Retry")
    void shouldNameTheDataProblem_andItsType(GraphDataException.Problem problem, String subject, String sentence)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        GraphFailurePage.send(request("document", null), response, new GraphDataException(problem, subject, "FAKE"),
                "ignored");

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentAsString())
                .contains("<p role=\"alert\" id=\"graphUnavailable\" class=\"alert alert-danger\">" + sentence + "</p>")
                .doesNotContain("<button");
    }

    @Test
    @DisplayName("should say a data problem in general when the graph does not know the type")
    void shouldSayTheGeneralDataProblem_withoutAType() {
        for (String subject : new String[]{null, " "}) {
            GraphFailurePage.Message message = GraphFailurePage.messageFor(
                    new GraphDataException(GraphDataException.Problem.NO_DATE, subject, "FAKE"));

            assertThat(message.key()).isEqualTo(GraphFailurePage.INVALID_DATA_KEY);
            assertThat(message.retry()).isFalse();
        }
    }

    @Test
    @DisplayName("should encode the type's name as text, never markup")
    void shouldEncodeTheTypesName_asText() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        GraphFailurePage.send(request("document", null), response, new GraphDataException(
                GraphDataException.Problem.NO_DATE, "<b>FAKE</b>", "FAKE"), "ignored");

        assertThat(response.getContentAsString()).contains("a stored &lt;b&gt;FAKE&lt;/b&gt; value has no date")
                .doesNotContain("<b>");
    }

    @Test
    @DisplayName("should name the data problem in the viewer's language, apostrophes intact")
    void shouldNameTheDataProblem_inTheViewersLanguage() throws Exception {
        MockHttpServletRequest request = request("document", null);
        request.addHeader("Accept-Language", "fr-CA,fr;q=0.9");
        request.addPreferredLocale(Locale.CANADA_FRENCH);
        MockHttpServletResponse response = new MockHttpServletResponse();

        GraphFailurePage.send(request, response, new GraphDataException(GraphDataException.Problem.NO_DATE, "Poids",
                "FAKE"), "ignored");

        assertThat(response.getContentAsString())
                .contains("Ce graphique ne peut pas être tracé : une valeur enregistrée de Poids n'a pas de date.");
    }

    @Test
    @DisplayName("should fall back to the English sentence, with the type, for a message the bundle lacks")
    void shouldFallBackToEnglish_forAMissingMessage() {
        ResourceBundle empty = new ListResourceBundle() {
            @Override
            protected Object[][] getContents() {
                return new Object[0][];
            }
        };
        GraphFailurePage.Message message = GraphFailurePage.messageFor(
                new GraphDataException(GraphDataException.Problem.NO_DATE, "Weight", "FAKE"));

        assertThat(GraphFailurePage.text(empty, Locale.ENGLISH, message))
                .isEqualTo("This graph cannot be drawn: a stored Weight value has no date.");
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
