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

import io.github.carlos_emr.carlos.commn.dao.TicklerLinkDao;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerLink;
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
import static org.mockito.Mockito.mock;
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

    @Mock private TicklerLinkDao mockTicklerLinkDao;
    @Mock private TicklerManager mockTicklerManager;
    @Mock private LoggedInInfo mockLoggedInInfo;
    private AutoCloseable mockitoCloseable;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        // The lazy accessors resolve these via SpringUtils.getBean on first use.
        registerMock(TicklerLinkDao.class, mockTicklerLinkDao);
        registerMock(TicklerManager.class, mockTicklerManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoCloseable.close();
    }

    @Test
    @DisplayName("should render each linked tickler message HTML-encoded when links exist")
    void shouldRenderTicklerMessagesHtmlEncoded_whenLinksExist() {
        TicklerLink link = mock(TicklerLink.class);
        when(link.getTicklerNo()).thenReturn(7);
        when(mockTicklerLinkDao.getLinkByTableId("DOC", 42L)).thenReturn(List.of(link));

        Tickler tickler = mock(Tickler.class);
        String rawMessage = "Follow up <b>STAT</b> & re-test";
        when(tickler.getMessage()).thenReturn(rawMessage);
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
        TicklerLink l1 = mock(TicklerLink.class);
        TicklerLink l2 = mock(TicklerLink.class);
        when(l1.getTicklerNo()).thenReturn(1);
        when(l2.getTicklerNo()).thenReturn(2);
        when(mockTicklerLinkDao.getLinkByTableId("DOC", 5L)).thenReturn(List.of(l1, l2));

        Tickler t1 = mock(Tickler.class);
        Tickler t2 = mock(Tickler.class);
        when(t1.getMessage()).thenReturn("first");
        when(t2.getMessage()).thenReturn("second");
        when(mockTicklerManager.getTickler(mockLoggedInInfo, 1)).thenReturn(t1);
        when(mockTicklerManager.getTickler(mockLoggedInInfo, 2)).thenReturn(t2);

        assertThat(EDocUtil.getHtmlTicklers(mockLoggedInInfo, "5")).isEqualTo("<br>first<br>second");
    }

    @Test
    @DisplayName("should return an empty string when the document has no tickler links")
    void shouldReturnEmpty_whenNoLinks() {
        when(mockTicklerLinkDao.getLinkByTableId("DOC", 99L)).thenReturn(null);

        assertThat(EDocUtil.getHtmlTicklers(mockLoggedInInfo, "99")).isEmpty();
    }
}
