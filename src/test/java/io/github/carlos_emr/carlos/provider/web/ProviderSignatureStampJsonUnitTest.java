/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.provider.web;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** JSON consumers must receive the original URL, with valid JSON escapes. */
@Tag("unit")
class ProviderSignatureStampJsonUnitTest extends CarlosUnitTestBase {

    @TempDir
    Path imageDirectory;

    @ParameterizedTest
    @ValueSource(strings = {"/carlos", "/carlos-test", "/carlos space", "/quote\"context", "/日本語"})
    void shouldPreserveImageUrl_inUploadSuccessJson(String contextPath) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath(contextPath);
        String response = ReflectionTestUtils.invokeMethod(ProviderSignatureStamp2Action.class,
                "buildSuccessJson", request);

        var json = new ObjectMapper().readTree(response);
        assertThat(json.get("success").booleanValue()).isTrue();
        assertThat(json.get("imageUrl").textValue()).isEqualTo(contextPath + "/provider/providerSignatureImage");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/carlos", "/carlos-test", "/carlos space", "/quote\"context", "/日本語"})
    void shouldPreserveImageUrl_inExistingStampStatusJson(String contextPath) throws Exception {
        UserPropertyDAO properties = mock(UserPropertyDAO.class);
        registerMock(UserPropertyDAO.class, properties);
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        UserProperty stamp = mock(UserProperty.class);
        when(stamp.getValue()).thenReturn("synthetic-stamp");
        when(properties.getProp("999998", UserProperty.PROVIDER_CONSULT_SIGNATURE)).thenReturn(stamp);
        Files.writeString(imageDirectory.resolve(UserProperty.CONSULT_SIGNATURE_PREFIX + "999998.png"), "fixture");
        CarlosProperties configuration = mock(CarlosProperties.class);
        when(configuration.getEformImageDirectory()).thenReturn(imageDirectory.toString());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath(contextPath);
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<CarlosProperties> configured = mockStatic(CarlosProperties.class)) {
            configured.when(CarlosProperties::getInstance).thenReturn(configuration);
            ReflectionTestUtils.invokeMethod(new ProviderSignatureStamp2Action(), "handleCheck",
                    request, response, "999998");
        }

        var json = new ObjectMapper().readTree(response.getContentAsByteArray());
        assertThat(json.get("exists").booleanValue()).isTrue();
        assertThat(json.get("imageUrl").textValue()).isEqualTo(contextPath + "/provider/providerSignatureImage");
    }
}
