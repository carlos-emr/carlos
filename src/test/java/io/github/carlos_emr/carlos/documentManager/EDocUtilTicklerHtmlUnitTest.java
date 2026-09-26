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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerDocsDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerDocs;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.owasp.encoder.Encode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EDocUtil#getHtmlTicklers(LoggedInInfo, String)}.
 *
 * <p>Exercises the collaborators that the lazy-accessor refactor now resolves on demand
 * ({@code ticklerLinkDao()} / {@code ticklerManager()}) and pins the {@code getTickler(Integer)}
 * call shape (no {@code intValue()} unbox) plus the HTML-encoding of tickler messages.</p>
 *
 * @since 2026-06-01
 */
@Tag("unit")
@Tag("fast")
@DisplayName("EDocUtil.getHtmlTicklers")
class EDocUtilTicklerHtmlUnitTest extends CarlosUnitTestBase {

    @Mock private TicklerDocsDao mockTicklerDocsDao;
    @Mock private TicklerManager mockTicklerManager;
    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private CtlDocumentDao mockCtlDocumentDao;
    @Mock private LoggedInInfo mockLoggedInInfo;
    private AutoCloseable mockitoCloseable;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        // The lazy accessors resolve these via SpringUtils.getBean on first use.
        registerMock(TicklerDocsDao.class, mockTicklerDocsDao);
        registerMock(TicklerManager.class, mockTicklerManager);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(CtlDocumentDao.class, mockCtlDocumentDao);
        // The patient-scoped _tickler read passes unless a test denies it, and every document
        // is filed under patient 1001 unless a test re-files it.
        lenient().when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_tickler"), eq("r"), any()))
                .thenReturn(true);
        lenient().when(mockCtlDocumentDao.findByDocumentNoAndModule(any(), eq("demographic")))
                .thenReturn(List.of(filedUnder(1001)));
    }

    private static CtlDocument filedUnder(int demographicNo) {
        CtlDocument filing = new CtlDocument();
        filing.setId(new CtlDocumentPK("demographic", demographicNo, 0));
        return filing;
    }

    private static Tickler ticklerOf(int demographicNo, String message) {
        Tickler tickler = mock(Tickler.class);
        lenient().when(tickler.getDemographicNo()).thenReturn(demographicNo);
        lenient().when(tickler.getMessage()).thenReturn(message);
        return tickler;
    }

    @Test
    @DisplayName("should leave out a tickler attached before the document was re-filed to another patient")
    void shouldOmitTickler_whenDocumentNoLongerFiledUnderItsPatient() {
        TicklerDocs link = mock(TicklerDocs.class);
        when(link.getTicklerId()).thenReturn(7);
        when(mockTicklerDocsDao.findByDocument(42, TicklerDocs.DOCTYPE_DOC)).thenReturn(List.of(link));
        Tickler tickler = ticklerOf(1001, "old patient's recall");
        when(mockTicklerManager.getTickler(mockLoggedInInfo, 7)).thenReturn(tickler);
        when(mockCtlDocumentDao.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(filedUnder(2002)));

        assertThat(EDocUtil.getHtmlTicklers(mockLoggedInInfo, "42")).isEmpty();
        verify(mockSecurityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should leave out a tickler when tickler read is denied for its patient")
    void shouldOmitTickler_whenTicklerReadDeniedForPatient() {
        TicklerDocs link = mock(TicklerDocs.class);
        when(link.getTicklerId()).thenReturn(7);
        when(mockTicklerDocsDao.findByDocument(42, TicklerDocs.DOCTYPE_DOC)).thenReturn(List.of(link));
        Tickler tickler = ticklerOf(1001, "hidden");
        when(mockTicklerManager.getTickler(mockLoggedInInfo, 7)).thenReturn(tickler);
        // getTickler proved the global right only; the patient-specific denial wins.
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_tickler", "r", "1001")).thenReturn(false);

        assertThat(EDocUtil.getHtmlTicklers(mockLoggedInInfo, "42")).isEmpty();
        verify(tickler, never()).getMessage();
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoCloseable.close();
    }

    @Test
    @DisplayName("should render each linked tickler message HTML-encoded when links exist")
    void shouldRenderTicklerMessagesHtmlEncoded_whenLinksExist() {
        TicklerDocs link = mock(TicklerDocs.class);
        when(link.getTicklerId()).thenReturn(7);
        when(mockTicklerDocsDao.findByDocument(42, TicklerDocs.DOCTYPE_DOC)).thenReturn(List.of(link));

        String rawMessage = "Follow up <b>STAT</b> & re-test";
        Tickler tickler = ticklerOf(1001, rawMessage);
        when(mockTicklerManager.getTickler(mockLoggedInInfo, 7)).thenReturn(tickler);

        String html = EDocUtil.getHtmlTicklers(mockLoggedInInfo, "42");

        // Message is HTML-encoded (XSS-safe) and prefixed with <br>.
        assertThat(html).isEqualTo("<br>" + Encode.forHtml(rawMessage));
        // The Integer ticklerNo is passed straight to getTickler (post-refactor: no intValue() unbox).
        verify(mockTicklerManager).getTickler(mockLoggedInInfo, 7);
    }

    @Test
    @DisplayName("should concatenate multiple tickler messages, each on its own line")
    void shouldConcatenateMultipleTicklers_whenSeveralLinksExist() {
        TicklerDocs l1 = mock(TicklerDocs.class);
        TicklerDocs l2 = mock(TicklerDocs.class);
        when(l1.getTicklerId()).thenReturn(1);
        when(l2.getTicklerId()).thenReturn(2);
        when(mockTicklerDocsDao.findByDocument(5, TicklerDocs.DOCTYPE_DOC)).thenReturn(List.of(l1, l2));

        Tickler t1 = ticklerOf(1001, "first");
        Tickler t2 = ticklerOf(1001, "second");
        when(mockTicklerManager.getTickler(mockLoggedInInfo, 1)).thenReturn(t1);
        when(mockTicklerManager.getTickler(mockLoggedInInfo, 2)).thenReturn(t2);

        assertThat(EDocUtil.getHtmlTicklers(mockLoggedInInfo, "5")).isEqualTo("<br>first<br>second");
    }

    @Test
    @DisplayName("should return an empty string when the document has no tickler links")
    void shouldReturnEmpty_whenNoLinks() {
        when(mockTicklerDocsDao.findByDocument(99, TicklerDocs.DOCTYPE_DOC)).thenReturn(null);

        assertThat(EDocUtil.getHtmlTicklers(mockLoggedInInfo, "99")).isEmpty();
    }
}
