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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnEditPrivateCodeViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.service.ServiceCodePersister;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Manage Private Billing Code (issue #5140): {@link ViewBillingOnEditPrivateCode2Action} saves or deletes
 * only when the posted {@code submit} value is "Save" or "Delete", so the page's buttons must post those
 * values in every language and translate only their labels. With a translated Save value, French, Spanish,
 * Polish and Portuguese users could not add or change a private code. The browser check
 * {@code translated-button-values} drives the page in French.
 */
@Tag("unit")
@Tag("billing")
@DisplayName("Manage Private Billing Code button values")
class BillingOnEditPrivateCodeButtonValuesUnitTest {

    private static final Path JSP = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/ON/billingONEditPrivateCode.jsp");
    private static final Pattern SUBMIT_CONTROL = Pattern.compile("<(?:input|button)\\b[^>]*\\bname=\"submit\"[^>]*>");
    private static final Pattern VALUE = Pattern.compile("\\bvalue=\"([^\"]*)\"");

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private BillingOnEditPrivateCodeViewModelAssembler mockAssembler;
    @Mock private ServiceCodePersister mockPersister;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenAnswer(invocation -> mockRequest);
        mockResponse = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    /** The value each control named "submit" posts, as written in the page. */
    private static List<String> postedValues() throws IOException {
        String page = Files.readString(JSP, StandardCharsets.UTF_8);
        return SUBMIT_CONTROL.matcher(page).results().map(control -> {
            Matcher value = VALUE.matcher(control.group());
            assertThat(value.find()).as("%s has no value", control.group()).isTrue();
            return value.group(1);
        }).toList();
    }

    private String post(String submit) throws Exception {
        mockRequest = new MockHttpServletRequest("POST", "/billing/CA/ON/ViewBillingONEditPrivateCode");
        mockRequest.addParameter("submit", submit);
        return new ViewBillingOnEditPrivateCode2Action(mockSecurityInfoManager, mockAssembler, mockPersister).execute();
    }

    @Test
    @DisplayName("should post fixed values from every submit control, the same in every language")
    void shouldPostFixedValues_inEveryLanguage() throws IOException {
        assertThat(postedValues()).containsExactlyInAnyOrder("Search", "Search", "Delete", "Save");
    }

    @Test
    @DisplayName("should translate the Save label, not its posted value")
    void shouldTranslateSaveLabel_insteadOfItsValue() throws IOException {
        assertThat(Files.readString(JSP, StandardCharsets.UTF_8)).containsPattern(
                "<button[^>]*name=\"submit\" value=\"Save\"[^>]*>\\s*<fmt:message key=\"admin\\.resourcebaseurl\\.btnSave\"/>\\s*</button>");
    }

    @Test
    @DisplayName("should save or delete for every mutation value the page posts, and only search for Search")
    void shouldMutate_forEachPostedMutationValue() throws Exception {
        List<String> mutations = postedValues().stream().filter(value -> !"Search".equals(value)).toList();
        assertThat(mutations).containsExactlyInAnyOrder("Delete", "Save");

        for (String value : mutations) {
            clearInvocations(mockPersister);
            assertThat(post(value)).isEqualTo(ActionSupport.SUCCESS);
            ArgumentCaptor<ServiceCodePersister.PrivateCodeMutationRequest> request =
                    ArgumentCaptor.forClass(ServiceCodePersister.PrivateCodeMutationRequest.class);
            verify(mockPersister).saveOrDeletePrivateCode(request.capture());
            assertThat(request.getValue().submit()).isEqualTo(value);
        }

        clearInvocations(mockPersister);
        assertThat(post("Search")).isEqualTo(ActionSupport.SUCCESS);
        verify(mockPersister, never()).saveOrDeletePrivateCode(any());
    }

    static java.util.stream.Stream<RuntimeException> concurrentSaves() {
        return java.util.stream.Stream.of(
                new jakarta.persistence.OptimisticLockException("Concurrent change"),
                new jakarta.persistence.PessimisticLockException("Concurrent change"),
                new jakarta.persistence.LockTimeoutException("Concurrent change"),
                new org.springframework.dao.OptimisticLockingFailureException("Concurrent change"));
    }

    @ParameterizedTest
    @MethodSource("concurrentSaves")
    void shouldReturnConflictWithoutRendering_whenTheSaveTransactionWasRefused(RuntimeException conflict)
            throws Exception {
        when(mockPersister.saveOrDeletePrivateCode(any())).thenThrow(conflict);
        assertThat(post("Save")).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(409);
        assertThat(mockResponse.getErrorMessage()).contains("search the service code first");
        verify(mockAssembler, never()).assemble(any(), any(), any());
    }

    @Test
    void shouldPropagateUnexpectedStorageFailures_insteadOfCallingThemConflicts() {
        RuntimeException failure = new IllegalStateException("Injected storage failure");
        when(mockPersister.saveOrDeletePrivateCode(any())).thenThrow(failure);
        assertThatThrownBy(() -> post("Save")).isSameAs(failure);
    }
}
