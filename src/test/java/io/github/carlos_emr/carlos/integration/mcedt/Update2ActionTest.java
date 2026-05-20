/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.integration.mcedt;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Update2Action")
@Tag("unit")
class Update2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("toUpdateRequest should surface upload validation failures as security errors")
    void toUpdateRequestShouldSurfaceValidationFailure() {
        Update2Action action = new Update2Action();
        action.setResourceId("123");

        UploadedFile uploaded = mock(UploadedFile.class);
        when(uploaded.getInputName()).thenReturn("content");
        when(uploaded.getContent()).thenReturn(new Object());

        action.withUploadedFiles(List.of(uploaded));

        assertThatThrownBy(action::toUpdateRequest)
                .isInstanceOf(SecurityException.class)
                .hasMessage("Invalid upload file path");
    }

    @Test
    @DisplayName("toUpdateRequest should keep upload field unchanged on validation errors")
    void toUpdateRequestShouldKeepContentFieldNullAfterValidationError() {
        Update2Action action = new Update2Action();
        action.setResourceId("123");

        UploadedFile uploaded = mock(UploadedFile.class);
        when(uploaded.getInputName()).thenReturn("content");
        when(uploaded.getContent()).thenReturn(new Object());

        action.withUploadedFiles(List.of(uploaded));

        assertThatThrownBy(action::toUpdateRequest)
                .isInstanceOf(SecurityException.class)
                .hasMessage("Invalid upload file path");
        assertThat(action.getContent()).isNull();
    }
}
