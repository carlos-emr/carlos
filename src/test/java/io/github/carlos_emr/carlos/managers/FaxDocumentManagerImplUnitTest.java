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
package io.github.carlos_emr.carlos.managers;

import java.nio.file.Path;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FaxDocumentManagerImpl")
@Tag("unit")
@Tag("fast")
class FaxDocumentManagerImplUnitTest extends CarlosUnitTestBase {

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private EformDataManager eformDataManager;
    @Mock private LoggedInInfo loggedInInfo;

    private AutoCloseable mocks;
    private FaxDocumentManagerImpl manager;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        manager = new FaxDocumentManagerImpl();
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "eformDataManager", eformDataManager);

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_fax"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) mocks.close();
    }

    @Test
    @DisplayName("should return the created eForm PDF path for fax rendering")
    void shouldReturnCreatedEformPdfPath_whenPdfGenerationSucceeds() throws Exception {
        Path expectedPath = Path.of("/tmp/fax-eform.pdf");
        when(eformDataManager.createEformPDF(loggedInInfo, 77)).thenReturn(expectedPath);

        Path actualPath = manager.getEformFaxDocument(loggedInInfo, 77);

        assertThat(actualPath).isEqualTo(expectedPath);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null);
        verify(eformDataManager).createEformPDF(loggedInInfo, 77);
    }

    @Test
    @DisplayName("should return null when eForm PDF generation fails")
    void shouldReturnNull_whenPdfGenerationFails() throws Exception {
        when(eformDataManager.createEformPDF(loggedInInfo, 77))
                .thenThrow(new PDFGenerationException("boom"));

        Path actualPath = manager.getEformFaxDocument(loggedInInfo, 77);

        assertThat(actualPath).isNull();
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.READ, null);
        verify(eformDataManager).createEformPDF(loggedInInfo, 77);
    }
}
