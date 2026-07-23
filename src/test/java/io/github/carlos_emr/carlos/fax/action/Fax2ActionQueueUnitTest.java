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

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link Fax2Action#queue()} covering the copy-to recipient fax-number
 * requirement (item 26a). A copy-to recipient with an empty or absent fax number must be
 * rejected up front by {@code validateFaxInputs}, instead of silently proceeding to queue a
 * recipient that can never actually receive the fax.
 */
@DisplayName("Fax2Action queue() copy-to recipient validation unit tests")
@Tag("unit")
@Tag("fast")
class Fax2ActionQueueUnitTest extends CarlosUnitTestBase {

    private static final String APP_TEMP_ROOT =
            java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "carlos-temp").toString();

    private FaxManager faxManager;
    private DocumentAttachmentManager documentAttachmentManager;
    private SecurityInfoManager securityInfoManager;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private void setUpCommonMocks() {
        faxManager = mock(FaxManager.class);
        documentAttachmentManager = mock(DocumentAttachmentManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq("w"), isNull()))
                .thenReturn(true);

        request = new MockHttpServletRequest();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        response = new MockHttpServletResponse();

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
    }

    @Test
    @DisplayName("should reject queue when a copy-to recipient has an empty fax number")
    void shouldRejectQueue_whenCopyToRecipientFaxNumberEmpty() {
        setUpCommonMocks();

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("EFORM");
            action.setRecipientFaxNumber("1234567890");
            action.setFaxFilePath(APP_TEMP_ROOT + "/fax.pdf");
            action.setCopyToRecipients(new String[] {"\"name\":\"Jane Doe\",\"fax\":\"\""});

            // The deliberate rejection must propagate with its own honest message — not get
            // swallowed by the JSON-parse catch and re-labeled "Invalid copy-to recipient format".
            assertThatThrownBy(action::queue)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Copy-to recipient fax number is required");

            assertThat(action.getActionErrors()).contains("Copy-to recipient fax number is required");
            // queue() bridges Struts action errors onto the request attribute securityError.jsp
            // actually renders, so the user sees the specific reason on the mapped error page.
            assertThat(request.getAttribute("actionErrors"))
                    .asInstanceOf(LIST)
                    .contains("Copy-to recipient fax number is required");
            verify(faxManager, never()).createAndSaveFaxJob(any(LoggedInInfo.class), anyMap());
        }
    }

    @Test
    @DisplayName("should reject queue when a copy-to recipient is missing the fax field entirely")
    void shouldRejectQueue_whenCopyToRecipientFaxFieldAbsent() {
        setUpCommonMocks();

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("EFORM");
            action.setRecipientFaxNumber("1234567890");
            action.setFaxFilePath(APP_TEMP_ROOT + "/fax.pdf");
            action.setCopyToRecipients(new String[] {"\"name\":\"Jane Doe\""});

            assertThatThrownBy(action::queue)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Copy-to recipient fax number is required");

            assertThat(action.getActionErrors()).contains("Copy-to recipient fax number is required");
            verify(faxManager, never()).createAndSaveFaxJob(any(LoggedInInfo.class), anyMap());
        }
    }

    @Test
    @DisplayName("should reject queue when a copy-to recipient entry is blank")
    void shouldRejectQueue_whenCopyToRecipientEntryBlank() {
        setUpCommonMocks();

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("EFORM");
            action.setRecipientFaxNumber("1234567890");
            action.setFaxFilePath(APP_TEMP_ROOT + "/fax.pdf");
            action.setCopyToRecipients(new String[] {"   "});

            // A blank entry used to skip validation entirely and then fail inside
            // createAndSaveFaxJob — after the preview had been destructively promoted.
            assertThatThrownBy(action::queue)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("blank");

            assertThat(action.getActionErrors()).contains("Copy-to recipient entry 1 is empty");
            verify(faxManager, never()).createAndSaveFaxJob(any(LoggedInInfo.class), anyMap());
        }
    }

    @Test
    @DisplayName("should reject queue when a copy-to recipient entry is not parseable JSON")
    void shouldRejectQueue_whenCopyToRecipientJsonMalformed() {
        setUpCommonMocks();

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("EFORM");
            action.setRecipientFaxNumber("1234567890");
            action.setFaxFilePath(APP_TEMP_ROOT + "/fax.pdf");
            action.setCopyToRecipients(new String[] {"\"name\":\"Jane Doe\", NOT-JSON"});

            // Only a genuine parse failure gets the format label now.
            assertThatThrownBy(action::queue)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Invalid copy-to recipient format");

            assertThat(action.getActionErrors())
                    .contains("Copy-to recipient entry 1 is not in a valid format");
            verify(faxManager, never()).createAndSaveFaxJob(any(LoggedInInfo.class), anyMap());
        }
    }

    @Test
    @DisplayName("should accept queue when every copy-to recipient carries a fax number")
    void shouldAcceptQueue_whenCopyToRecipientFaxNumberPresent() {
        setUpCommonMocks();
        when(faxManager.createAndSaveFaxJob(any(LoggedInInfo.class), anyMap()))
                .thenReturn(java.util.List.of());

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.setTransactionType("EFORM");
            action.setRecipientFaxNumber("1234567890");
            action.setFaxFilePath(APP_TEMP_ROOT + "/fax.pdf");
            action.setCopyToRecipients(new String[] {"\"name\":\"Jane Doe\",\"fax\":\"9876543210\""});

            String result = action.queue();

            assertThat(result).isEqualTo("preview");
            assertThat(action.getActionErrors()).isEmpty();
            verify(faxManager).createAndSaveFaxJob(any(LoggedInInfo.class), anyMap());
        }
    }
}
