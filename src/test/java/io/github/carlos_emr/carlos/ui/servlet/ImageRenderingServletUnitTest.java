/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */
package io.github.carlos_emr.carlos.ui.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.carlos_emr.carlos.casemgmt.dao.ClientImageDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.DigitalSignatureUtils;
import io.github.carlos_emr.carlos.utility.SessionConstants;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;

@DisplayName("ImageRenderingServlet")
@Tag("unit")
class ImageRenderingServletUnitTest extends CarlosUnitTestBase {

    private static final byte[] PNG_BYTES = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3
    };

    private Path tempSignaturePath;

    @BeforeEach
    void setUp() {
        registerMock(ClientImageDAO.class, mock(ClientImageDAO.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempSignaturePath != null) {
            Files.deleteIfExists(tempSignaturePath);
        }
    }

    @Test
    @DisplayName("returns exact uploaded PNG bytes for a temp signature preview")
    void shouldReturnExactPngBytes_forTempSignaturePreview() throws Exception {
        String signatureRequestId = "999998123456789";
        tempSignaturePath = Path.of(DigitalSignatureUtils.getTempFilePath(signatureRequestId));
        Files.write(tempSignaturePath, PNG_BYTES);

        ImageRenderingServlet servlet = new ImageRenderingServlet();
        servlet.init(new MockServletConfig(new MockServletContext()));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/imageRenderingServlet");
        request.addParameter("source", ImageRenderingServlet.Source.signature_preview.name());
        request.addParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureRequestId);
        request.getSession().setAttribute(SessionConstants.LOGGED_IN_PROVIDER, new Provider());
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet.doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(response.getContentAsByteArray()).containsExactly(PNG_BYTES);
    }

    @Test
    @DisplayName("detects common signature image formats by magic bytes")
    void shouldDetectCommonSignatureImageTypes() {
        assertThat(ImageRenderingServlet.detectImageType(PNG_BYTES)).isEqualTo("png");
        assertThat(ImageRenderingServlet.detectImageType(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}))
                .isEqualTo("jpeg");
        assertThat(ImageRenderingServlet.detectImageType(new byte[]{'G', 'I', 'F', '8', '9', 'a'}))
                .isEqualTo("gif");
        assertThat(ImageRenderingServlet.detectImageType(new byte[]{1, 2, 3})).isEqualTo("jpeg");
    }
}
