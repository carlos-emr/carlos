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
package io.github.carlos_emr.carlos.eform.actions;

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for the prepared-fax redirect helper in {@link AddEForm2Action}.
 *
 * <p>Both eForm fax branches (current and previous revision) route through the shared
 * {@code redirectToPreparedFax(...)} helper. These tests pin the security-relevant URL contract:
 * optional recipient parameters MUST be URL-encoded (a raw {@code &}/{@code #} would otherwise
 * split or truncate the redirect query string) and empty optional parameters MUST be omitted
 * rather than emitted as dangling {@code &recipient=} pairs.</p>
 *
 * @since 2026-06-06
 */
@DisplayName("AddEForm2Action prepared-fax redirect")
@Tag("unit")
@Tag("eform")
@Tag("security")
class AddEForm2ActionFaxRedirectTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private AddEForm2Action action;

    @BeforeEach
    void setUp() {
        // AddEForm2Action resolves these managers from SpringUtils in its field initializers,
        // so they must be registered before the action is constructed (none are used by the
        // redirect helper under test — they only need to satisfy construction).
        registerMock(SecurityInfoManager.class, Mockito.mock(SecurityInfoManager.class));
        registerMock(EformDataManager.class, Mockito.mock(EformDataManager.class));
        registerMock(DocumentAttachmentManager.class, Mockito.mock(DocumentAttachmentManager.class));
        registerMock(EmailManager.class, Mockito.mock(EmailManager.class));

        mockRequest = new MockHttpServletRequest();
        mockRequest.setContextPath("/carlos");
        mockResponse = new MockHttpServletResponse();

        // AddEForm2Action reads request/response from ServletActionContext at field-init time,
        // so the static mock must be active before the action is constructed.
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        action = new AddEForm2Action();
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    /** Invokes the private helper via reflection and returns the redirect target it wrote. */
    private String redirect(String fdid, String demoNo, String recipient, String faxNo, String letterhead)
            throws Exception {
        Method m = AddEForm2Action.class.getDeclaredMethod(
            "redirectToPreparedFax", String.class, String.class, String.class, String.class, String.class);
        m.setAccessible(true);
        m.invoke(action, fdid, demoNo, recipient, faxNo, letterhead);
        return mockResponse.getRedirectedUrl();
    }

    @Test
    @DisplayName("should target a same-origin fax path with the mandatory params")
    void shouldIncludeMandatoryParams_onFaxRedirect() throws Exception {
        String url = redirect("67", "12345", null, null, null);
        assertThat(url)
            .startsWith("/carlos/fax/faxAction?method=prepareFax")
            .contains("&transactionId=67")
            .contains("&transactionType=EFORM")
            .contains("&demographicNo=12345");
    }

    @Test
    @DisplayName("should URL-encode a recipient containing query-significant characters")
    void shouldEncodeRecipient_whenContainsSpecialChars() throws Exception {
        String url = redirect("67", "12345", "Dr & Smith", null, null);
        assertThat(url)
            .contains("&recipient=Dr+%26+Smith")
            .doesNotContain("Dr & Smith");
    }

    @Test
    @DisplayName("should omit optional params when null or empty")
    void shouldOmitOptionalParams_whenNullOrEmpty() throws Exception {
        String url = redirect("67", "12345", "", null, "");
        assertThat(url)
            .doesNotContain("&recipient=")
            .doesNotContain("&recipientFaxNumber=")
            .doesNotContain("&letterheadFax=");
    }

    @Test
    @DisplayName("should encode every optional fax param when all are provided")
    void shouldEncodeAllOptionalParams_whenProvided() throws Exception {
        String url = redirect("67", "12345", "Smith", "555 1234", "Clinic#1");
        assertThat(url)
            .contains("&recipient=Smith")
            .contains("&recipientFaxNumber=555+1234")
            .contains("&letterheadFax=Clinic%231");
    }
}
