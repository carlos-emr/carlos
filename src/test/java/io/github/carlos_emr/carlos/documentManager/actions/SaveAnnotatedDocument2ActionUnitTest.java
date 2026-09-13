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
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.documentManager.annotation.AnnotatedDocumentService;
import io.github.carlos_emr.carlos.documentManager.annotation.DocumentAnnotationParser;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Pins the POST-only contract on the annotation save endpoint.
 *
 * <p>The verb gate has to fire before anything else, including the privilege lookup, so a
 * crafted link cannot drive a document write through a browser GET. The aggregated
 * contract test drives this class too; these cases add the positive path and the
 * dependency-untouched assertion that the aggregate cannot express.
 */
@Tag("unit")
@Tag("documentManager")
@DisplayName("SaveAnnotatedDocument2Action")
class SaveAnnotatedDocument2ActionUnitTest extends CarlosUnitTestBase {

    private SecurityInfoManager securityInfoManager;
    private AnnotatedDocumentService service;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        service = mock(AnnotatedDocumentService.class);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    private SaveAnnotatedDocument2Action action() {
        return new SaveAnnotatedDocument2Action(
                securityInfoManager, new DocumentAnnotationParser(), service);
    }

    @Test
    @DisplayName("should reject GET with 405 before touching any dependency")
    void shouldReject_whenMethodIsGet() throws Exception {
        assertRefusedWithoutSideEffects("GET");
    }

    @Test
    @DisplayName("should reject HEAD with 405 before touching any dependency")
    void shouldReject_whenMethodIsHead() throws Exception {
        assertRefusedWithoutSideEffects("HEAD");
    }

    @Test
    @DisplayName("should check patient access before parsing the source PDF")
    void shouldRejectUnauthorizedPatient_beforePdfParsing() throws Exception {
        request.setMethod("POST");
        request.setParameter("docId", "42");
        org.mockito.Mockito.when(securityInfoManager.hasPrivilege(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("_edoc"),
                org.mockito.ArgumentMatchers.eq("w"), org.mockito.ArgumentMatchers.isNull())).thenReturn(true);
        var doc = new io.github.carlos_emr.carlos.documentManager.EDoc();
        doc.setFileName("synthetic.pdf");
        doc.setModule("demographic");
        doc.setModuleId("10");
        try (var servlet = mockStatic(ServletActionContext.class);
             var documents = mockStatic(io.github.carlos_emr.carlos.documentManager.EDocUtil.class);
             var pdf = mockStatic(AnnotatedDocumentService.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            documents.when(() -> io.github.carlos_emr.carlos.documentManager.EDocUtil.getDoc("42")).thenReturn(doc);
            action().execute();
            assertThat(response.getStatus()).isEqualTo(403);
            pdf.verifyNoInteractions();
            verifyNoInteractions(service);
        }
    }

    private void assertRefusedWithoutSideEffects(String verb) throws Exception {
        request.setMethod(verb);
        request.setParameter("docId", "42");

        try (MockedStatic<ServletActionContext> servletContext = mockStatic(ServletActionContext.class)) {
            servletContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletContext.when(ServletActionContext::getResponse).thenReturn(response);

            String result = action().execute();

            assertThat(result).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }

        // The gate must precede authorisation and the service, or by the time the request is
        // refused a document write has already been attempted.
        verifyNoInteractions(service);
        verifyNoInteractions(securityInfoManager);
    }
}
