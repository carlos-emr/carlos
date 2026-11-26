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
package io.github.carlos_emr.carlos.documentManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.lab.ca.all.AcknowledgementData;
import io.github.carlos_emr.carlos.mds.data.ReportStatus;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the HTML fragments {@link EDocUtil} builds for the document-information view:
 * {@link EDocUtil#getHtmlAcknowledgement(Locale, String)} and
 * {@link EDocUtil#getHtmlAnnotation(String)} (issue #3057, leaf #2656).
 *
 * <p>Both fragments are written into the page as HTML by
 * {@code ManageDocument2Action#doViewDocumentInfo}, so each stored value in them must be
 * HTML-encoded, and a value that is missing must print as nothing: the raw OWASP encoder turns
 * {@code null} into the four characters {@code null}, which then showed as a provider name, a
 * date or an annotation.</p>
 *
 * @since 2026-11-26
 */
@Tag("unit")
@Tag("fast")
@Tag("document")
@Tag("security")
@DisplayName("EDocUtil document-information HTML fragments")
class EDocUtilDocumentInfoHtmlUnitTest extends CarlosUnitTestBase {

    private static final String MARKUP = "<i data-xp=\"1\">q</i> \"dq\" 'sq' &amp;";
    private static final String ENCODED_MARKUP = "&lt;i data-xp=&#34;1&#34;&gt;q&lt;/i&gt; &#34;dq&#34; &#39;sq&#39; &amp;amp;";

    @Mock private CaseManagementNoteLinkDAO mockNoteLinkDao;
    @Mock private CaseManagementNoteDAO mockNoteDao;
    private AutoCloseable mockitoCloseable;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        registerMock(CaseManagementNoteLinkDAO.class, mockNoteLinkDao);
        registerMock(CaseManagementNoteDAO.class, mockNoteDao);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoCloseable.close();
    }

    @Nested
    @DisplayName("getHtmlAcknowledgement")
    class Acknowledgement {

        private String render(ReportStatus... reports) {
            try (MockedStatic<AcknowledgementData> acks = mockStatic(AcknowledgementData.class)) {
                acks.when(() -> AcknowledgementData.getAcknowledgements("DOC", "42"))
                        .thenReturn(new ArrayList<>(List.of(reports)));
                return EDocUtil.getHtmlAcknowledgement(Locale.ENGLISH, "42");
            }
        }

        private ReportStatus report(String providerName, String status, String timestamp, String comment) {
            ReportStatus report = new ReportStatus();
            report.setProviderName(providerName);
            report.setStatus(status);
            report.setTimestamp(timestamp);
            report.setComment(comment);
            return report;
        }

        @Test
        @DisplayName("should HTML-encode the stored provider name, timestamp and comment")
        void shouldHtmlEncodeStoredValues_whenAcknowledgementCarriesMarkup() {
            String html = render(report("Dr " + MARKUP, "A", "2026-11-26 " + MARKUP, "comment " + MARKUP));

            assertThat(html)
                    .doesNotContain("<i ")
                    .isEqualTo("Dr " + ENCODED_MARKUP + ": Acknowledged on 2026-11-26 " + ENCODED_MARKUP
                            + " comment " + ENCODED_MARKUP + "<br>");
        }

        @Test
        @DisplayName("should render a record without a status as unacknowledged and omit missing text")
        void shouldRenderMissingValuesEmpty_whenAcknowledgementFieldsAreNull() {
            String html = render(report(null, null, null, null));

            assertThat(html)
                    .doesNotContainIgnoringCase("null")
                    .isEqualTo(": Not Acknowledged since  <br>");
        }
    }

    @Nested
    @DisplayName("getHtmlAnnotation")
    class Annotation {

        private void linkDocumentToNote(String providerNo, String noteText) {
            CaseManagementNoteLink link = new CaseManagementNoteLink();
            link.setNoteId(7L);
            CaseManagementNote first = new CaseManagementNote();
            first.setUuid("uuid-7");
            CaseManagementNote latest = new CaseManagementNote();
            latest.setUuid("uuid-7");
            latest.setProviderNo(providerNo);
            latest.setNote(noteText);
            when(mockNoteLinkDao.getLastLinkByTableId(CaseManagementNoteLink.DOCUMENT, 42L)).thenReturn(link);
            when(mockNoteDao.getNote(7L)).thenReturn(first);
            when(mockNoteDao.getMostRecentNote("uuid-7")).thenReturn(latest);
        }

        @Test
        @DisplayName("should HTML-encode the latest revision of the annotation note")
        void shouldHtmlEncodeAnnotation_whenNoteCarriesMarkup() {
            linkDocumentToNote("999998", "annotation " + MARKUP);

            assertThat(EDocUtil.getHtmlAnnotation("42")).isEqualTo("annotation " + ENCODED_MARKUP);
        }

        @Test
        @DisplayName("should print an annotation whose note text is missing as nothing")
        void shouldRenderEmpty_whenAnnotationNoteIsNull() {
            linkDocumentToNote("999998", null);

            assertThat(EDocUtil.getHtmlAnnotation("42")).isEmpty();
        }

        @Test
        @DisplayName("should not use the document's own note as an annotation")
        void shouldReturnEmpty_whenLatestNoteIsTheDocumentNote() {
            linkDocumentToNote("-1", "document note " + MARKUP);

            assertThat(EDocUtil.getHtmlAnnotation("42")).isEmpty();
        }
    }
}
