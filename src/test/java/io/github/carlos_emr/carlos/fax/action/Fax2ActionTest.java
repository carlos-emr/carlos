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
package io.github.carlos_emr.carlos.fax.action;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.FaxManager.TransactionType;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression tests for fax preview preparation method restrictions.
 *
 * @since 2026-05-29
 */
@DisplayName("Fax2Action Tests")
@Tag("unit")
@Tag("fax")
class Fax2ActionTest extends CarlosWebTestBase {

    @Mock
    private FaxManager mockFaxManager;

    @Mock
    private DocumentAttachmentManager mockDocumentAttachmentManager;

    private Fax2Action action;

    @BeforeEach
    void setUp() {
        replaceSpringUtilsBean(FaxManager.class, mockFaxManager);
        replaceSpringUtilsBean(DocumentAttachmentManager.class, mockDocumentAttachmentManager);
        action = new Fax2Action();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE", "PATCH"})
    @DisplayName("should reject non-POST prepareFax with 405 before preview side effects")
    void shouldRejectPrepareFax_whenRequestMethodIsNotPost(String requestMethod) throws Exception {
        allowPrivilege("_fax", "w");
        getMockRequest().setMethod(requestMethod);
        addRequestParameter("method", "prepareFax");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(getMockResponse().getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(mockFaxManager, mockDocumentAttachmentManager);
    }

    @Test
    @DisplayName("should allow POST prepareFax to create preview attributes")
    void shouldPrepareFaxPreview_whenRequestIsPost() throws Exception {
        Path pdfPath = Path.of("preview.pdf");
        allowPrivilege("_fax", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", "prepareFax");
        action.setTransactionType(TransactionType.EFORM.name());
        action.setTransactionId(123);
        action.setDemographicNo(456);
        action.setRecipient("Specialist");
        action.setRecipientFaxNumber("5551234567");
        action.setLetterheadFax("clinic");
        when(mockFaxManager.getFaxGatewayAccounts(mockLoggedInInfo)).thenReturn(List.of(new FaxConfig()));
        when(mockDocumentAttachmentManager.renderEFormWithAttachments(getMockRequest(), getMockResponse()))
                .thenReturn(pdfPath);

        String result = executeAction(action);

        assertThat(result).isEqualTo("preview");
        assertThat(getMockRequest().getAttribute("faxFilePath")).isEqualTo(pdfPath);
        assertThat(getMockRequest().getAttribute("transactionType")).isEqualTo(TransactionType.EFORM.name());
        assertThat(getMockRequest().getAttribute("transactionId")).isEqualTo(123);
        verify(mockDocumentAttachmentManager).renderEFormWithAttachments(getMockRequest(), getMockResponse());
    }
}
