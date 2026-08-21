/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins that the outbound email archive guard engages in EDocUtil.
 *
 * <p>EDocUtil is the legacy static gateway most eDoc operations still route through, so a missing
 * call site here reopens the archive to the whole document surface. These tests drive the entry
 * points directly with an archive-backed identifier.</p>
 */
@DisplayName("EDocUtil outbound email archive guard")
@Tag("unit")
@Tag("security")
@Tag("email")
class EDocUtilArchiveGuardUnitTest extends CarlosUnitTestBase {

    private static final String ARCHIVE_DOC_NO = "321";
    private static final String ARCHIVE_FILE_NAME = "20260707120000_outbound-email-44.eml";
    private static final String ARCHIVE_MESSAGE =
            "Outbound email archive eDocs must be managed through the controlled archive workflow";

    private OutboundEmailArchiveDao outboundEmailArchiveDao;

    @BeforeEach
    void setUp() {
        outboundEmailArchiveDao = mock(OutboundEmailArchiveDao.class);
        registerMock(OutboundEmailArchiveDao.class, outboundEmailArchiveDao);
        when(outboundEmailArchiveDao.existsByDocumentNo(321)).thenReturn(true);
        when(outboundEmailArchiveDao.existsByFileName(ARCHIVE_FILE_NAME)).thenReturn(true);
    }

    @Test
    @DisplayName("should refuse to attach an archive artifact to a consultation")
    void shouldRefuseToAttachArchiveArtifact_toConsultation() {
        assertThatThrownBy(() -> EDocUtil.attachDocConsult("999998", ARCHIVE_DOC_NO, "77"))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ARCHIVE_MESSAGE);
    }

    @Test
    @DisplayName("should refuse to detach an archive artifact from a consultation")
    void shouldRefuseToDetachArchiveArtifact_fromConsultation() {
        // Detach matters as much as attach: unlinking an archive from the consultation it was
        // sent with loses the association without going through controlled deletion.
        assertThatThrownBy(() -> EDocUtil.detachDocConsult(ARCHIVE_DOC_NO, "77"))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ARCHIVE_MESSAGE);
    }

    @Test
    @DisplayName("should refuse to attach an archive artifact to an eForm")
    void shouldRefuseToAttachArchiveArtifact_toEForm() {
        assertThatThrownBy(() -> EDocUtil.attachDocEForm("999998", ARCHIVE_DOC_NO, "77"))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ARCHIVE_MESSAGE);
    }

    @Test
    @DisplayName("should refuse to undelete an archive artifact")
    void shouldRefuseToUndeleteArchiveArtifact() {
        assertThatThrownBy(() -> EDocUtil.undeleteDocument(ARCHIVE_DOC_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ARCHIVE_MESSAGE);
    }

    @Test
    @DisplayName("should refuse to subtract a page from an archive artifact")
    void shouldRefuseToSubtractPage_fromArchiveArtifact() {
        assertThatThrownBy(() -> EDocUtil.subtractOnePage(ARCHIVE_DOC_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ARCHIVE_MESSAGE);
    }

    @Test
    @DisplayName("should refuse to render annotations for an archive artifact")
    void shouldRefuseToRenderAnnotations_forArchiveArtifact() {
        assertThatThrownBy(() -> EDocUtil.getHtmlAnnotation(ARCHIVE_DOC_NO))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ARCHIVE_MESSAGE);
    }

    @Test
    @DisplayName("should refuse to refile an archive artifact")
    void shouldRefuseToRefileArchiveArtifact() {
        assertThatThrownBy(() -> EDocUtil.refileDocument(ARCHIVE_DOC_NO, "5"))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ARCHIVE_MESSAGE);
    }

    @Test
    @DisplayName("should refuse to overwrite an archive artifact file")
    void shouldRefuseToOverwriteArchiveArtifactFile() {
        assertThatThrownBy(() -> EDocUtil.writeDocContent(ARCHIVE_FILE_NAME, new byte[]{1, 2, 3}))
                .isInstanceOf(SecurityException.class)
                .hasMessage(ARCHIVE_MESSAGE);
    }

    @Test
    @DisplayName("should surface an archive refusal from readContent as SecurityException, not IOException")
    void shouldSurfaceArchiveRefusal_fromReadContent_asSecurityException() {
        // The subtle one. readContent converts SecurityException from a rejected filesystem path
        // into IOException to honour its declared throws clause. An archive refusal must not be
        // folded into that conversion: it is an authorization outcome, not a missing file, and a
        // caller seeing IOException would report and audit the wrong thing. If the private
        // OutboundEmailArchiveSecurityException subtype or its rethrow is ever removed, this
        // assertion is what notices.
        assertThatThrownBy(() -> EDocUtil.readContent(ARCHIVE_FILE_NAME))
                .isInstanceOf(SecurityException.class)
                .isNotInstanceOf(IOException.class)
                .hasMessage(ARCHIVE_MESSAGE);
    }

    @Test
    @DisplayName("should let ordinary documents through the guard")
    void shouldLetOrdinaryDocumentsThrough_pastTheGuard() {
        // Narrowness check. An ordinary id must not be refused; it may still fail further in for
        // unrelated reasons, so this asserts only that whatever comes back is not the archive
        // refusal.
        when(outboundEmailArchiveDao.existsByDocumentNo(999)).thenReturn(false);
        when(outboundEmailArchiveDao.existsByFileName(anyString())).thenReturn(false);

        assertThatThrownBy(() -> EDocUtil.refileDocument("999", "5"))
                .satisfies(thrown -> org.assertj.core.api.Assertions
                        .assertThat(ARCHIVE_MESSAGE.equals(thrown.getMessage()))
                        .as("ordinary document must not hit the archive refusal")
                        .isFalse());
    }
}
