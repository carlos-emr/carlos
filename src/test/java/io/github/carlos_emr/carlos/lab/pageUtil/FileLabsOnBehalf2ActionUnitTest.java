/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.pageUtil;

import io.github.carlos_emr.carlos.lab.service.LabFilingService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("unit")
class FileLabsOnBehalf2ActionUnitTest extends CarlosUnitTestBase {
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final LabFilingService service = mock(LabFilingService.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> session;
    @BeforeEach void fixture() {
        registerMock(LabFilingService.class, service);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        session = mockStatic(LoggedInInfo.class);
        session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
        request.setMethod("POST"); request.setParameter("labNo", "30"); request.setParameter("labType", "HL7");
        request.setParameter("providerNo", new String[]{"100002", "100003"});
    }
    @AfterEach void release() { session.close(); servlet.close(); }
    @ParameterizedTest @ValueSource(strings={"GET", "HEAD", "PUT", "DELETE"})
    void shouldRejectBeforeServiceLookup_whenMethodIsNotPost(String method) throws Exception {
        request.setMethod(method);
        assertThat(new FileLabsOnBehalf2Action().execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405); assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(service);
    }
    @Test void shouldUseSessionIdentityAndSingleBatch_whenClientPostsProviders() throws Exception {
        request.setParameter("onBehalfOfOtherProvider", "false");
        when(service.fileForProviders(info, 30, "HL7", List.of("100002", "100003"), null, true)).thenReturn(2);
        new FileLabsOnBehalf2Action().execute();
        assertThat(response.getContentAsString()).contains("\"success\":true", "\"filed\":2");
        verify(service).fileForProviders(info, 30, "HL7", List.of("100002", "100003"), null, true);
    }
    @Test void shouldRefuseAmbiguousLab_whenDuplicateParametersPosted() throws Exception {
        request.setParameter("labNo", new String[]{"30", "90"}); new FileLabsOnBehalf2Action().execute();
        assertThat(response.getStatus()).isEqualTo(400); verifyNoInteractions(service);
    }
    @Test void shouldHidePrivateFailureDetails_whenTargetIsRefused() throws Exception {
        when(service.fileForProviders(any(), anyInt(), anyString(), anyList(), isNull(), eq(true)))
                .thenThrow(new SecurityException("PRIVATE clinical text"));
        new FileLabsOnBehalf2Action().execute();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).doesNotContain("PRIVATE");
    }
}
