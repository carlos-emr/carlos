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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.DocumentBean;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OnRaImportService}. Pins the four contract branches:
 * <ol>
 *   <li>no DocumentBean → no-op success</li>
 *   <li>blank filename → no-op success</li>
 *   <li>{@link PathValidationUtils#validatePath} throws → false (blocked)</li>
 *   <li>{@code importRAFile} throws → false</li>
 * </ol>
 *
 * <p>The path-traversal guard at line 82 of {@code OnRaImportService} is the
 * load-bearing security check for OHIP RA file imports; if a refactor swaps
 * the order or drops the guard, these tests fail.</p>
 *
 * @since 2026-04-30
 */
@DisplayName("OnRaImportService")
@Tag("unit")
@Tag("billing")
class OnRaImportServiceUnitTest {

    private BillingOnRaService mockRaService;
    private OnRaImportService service;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        mockRaService = mock(BillingOnRaService.class);
        service = new OnRaImportService(mockRaService);
        request = new MockHttpServletRequest();
    }

    @AfterEach
    void tearDown() {
        // No static state to reset.
    }

    @Test
    void shouldReturnTrue_whenNoDocumentBeanAttribute() throws Exception {
        // No `documentBean` request attribute → no-op success.
        assertThat(service.importDocumentBeanFile(request)).isTrue();
        verify(mockRaService, never()).importRAFile(anyString());
    }

    @Test
    void shouldReturnTrue_whenDocumentBeanFilenameIsNull() throws Exception {
        DocumentBean db = mock(DocumentBean.class);
        when(db.getFilename()).thenReturn(null);
        request.setAttribute("documentBean", db);

        assertThat(service.importDocumentBeanFile(request)).isTrue();
        verify(mockRaService, never()).importRAFile(anyString());
    }

    @Test
    void shouldReturnTrue_whenDocumentBeanFilenameIsEmpty() throws Exception {
        DocumentBean db = mock(DocumentBean.class);
        when(db.getFilename()).thenReturn("");
        request.setAttribute("documentBean", db);

        assertThat(service.importDocumentBeanFile(request)).isTrue();
        verify(mockRaService, never()).importRAFile(anyString());
    }

    @Test
    void shouldReturnFalse_whenPathValidationBlocksTraversalAttempt() throws Exception {
        // The path-traversal guard MUST run before importRAFile is called.
        // PathValidationUtils.validatePath throws SecurityException on
        // attempts that escape the DOCUMENT_DIR root; the import must
        // surface false to the caller and not invoke the RA service.
        DocumentBean db = mock(DocumentBean.class);
        when(db.getFilename()).thenReturn("../../etc/passwd");
        request.setAttribute("documentBean", db);

        try (MockedStatic<PathValidationUtils> pathMock = mockStatic(PathValidationUtils.class)) {
            pathMock.when(() -> PathValidationUtils.validatePath(anyString(), any(File.class)))
                    .thenThrow(new SecurityException("path traversal"));

            assertThat(service.importDocumentBeanFile(request)).isFalse();
            verify(mockRaService, never()).importRAFile(anyString());
        }
    }

    @Test
    void shouldReturnFalse_whenImportRAFileThrows() throws Exception {
        DocumentBean db = mock(DocumentBean.class);
        when(db.getFilename()).thenReturn("ra-file.txt");
        request.setAttribute("documentBean", db);

        try (MockedStatic<PathValidationUtils> pathMock = mockStatic(PathValidationUtils.class)) {
            pathMock.when(() -> PathValidationUtils.validatePath(anyString(), any(File.class)))
                    .thenReturn(new File("/tmp/ra-file.txt"));
            when(mockRaService.importRAFile(anyString()))
                    .thenThrow(new RuntimeException("DAO crash mid-import"));

            assertThat(service.importDocumentBeanFile(request)).isFalse();
            verify(mockRaService).importRAFile(anyString());
        }
    }
}
