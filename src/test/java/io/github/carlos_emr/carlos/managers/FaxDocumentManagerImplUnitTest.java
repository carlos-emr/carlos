/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FaxDocumentManagerImpl")
@Tag("unit")
@Tag("fast")
@Tag("manager")
class FaxDocumentManagerImplUnitTest extends CarlosUnitTestBase {

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private EformDataManager eformDataManager;

    @Mock
    private LoggedInInfo loggedInInfo;

    @TempDir
    private Path tempDir;

    private FaxDocumentManagerImpl manager;

    @BeforeEach
    void setUp() {
        manager = new FaxDocumentManagerImpl();
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "eformDataManager", eformDataManager);

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null)).thenReturn(true);
    }

    @Test
    @DisplayName("returns the PDF path created by the eForm manager")
    void shouldReturnGeneratedEformPdfPath() throws Exception {
        Path expectedPath = tempDir.resolve("eform.pdf");
        Files.writeString(expectedPath, "%PDF-1.4\n");
        when(eformDataManager.createEformPDF(loggedInInfo, 77)).thenReturn(expectedPath);

        Path result = manager.getEformFaxDocument(loggedInInfo, 77);

        assertThat(result).isSameAs(expectedPath);
    }

    @Test
    @DisplayName("wraps eForm PDF generation failures with a clear runtime exception")
    void shouldWrapPdfGenerationException() throws Exception {
        PDFGenerationException cause = new PDFGenerationException("could not render eForm");
        when(eformDataManager.createEformPDF(loggedInInfo, 77)).thenThrow(cause);

        assertThatThrownBy(() -> manager.getEformFaxDocument(loggedInInfo, 77))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to render eForm fax document.")
                .hasCause(cause);
    }
}
