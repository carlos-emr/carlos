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
import org.springframework.mock.web.MockHttpSession;
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
        MockHttpServletRequest request = previewRequest();
        String signatureRequestId = DigitalSignatureUtils.generateSignatureRequestId(request.getSession());
        request.addParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, signatureRequestId);
        tempSignaturePath = Path.of(DigitalSignatureUtils.getTempFilePath(signatureRequestId));
        Files.write(tempSignaturePath, PNG_BYTES);
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet().doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(response.getContentAsByteArray()).containsExactly(PNG_BYTES);
    }

    @Test
    @DisplayName("does not return a temp signature whose request id another session was issued")
    void shouldNotReturnTempSignature_whenRequestIdBelongsToAnotherSession() throws Exception {
        String otherSessionsId = DigitalSignatureUtils.generateSignatureRequestId(new MockHttpSession());
        tempSignaturePath = Path.of(DigitalSignatureUtils.getTempFilePath(otherSessionsId));
        Files.write(tempSignaturePath, PNG_BYTES);
        MockHttpServletRequest request = previewRequest();
        request.addParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY, otherSessionsId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // With the web root to find it, the preview answers with the blank "not signed yet" GIF.
        servlet(new MockServletContext("file:src/main/webapp")).doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/gif");
        assertThat(response.getContentAsByteArray())
                .isNotEqualTo(PNG_BYTES)
                .containsExactly(Files.readAllBytes(Path.of("src/main/webapp/images/1x1.gif")));
    }

    private static ImageRenderingServlet servlet() throws Exception {
        return servlet(new MockServletContext());
    }

    private static ImageRenderingServlet servlet(MockServletContext context) throws Exception {
        ImageRenderingServlet servlet = new ImageRenderingServlet();
        servlet.init(new MockServletConfig(context));
        return servlet;
    }

    private static MockHttpServletRequest previewRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/imageRenderingServlet");
        request.addParameter("source", ImageRenderingServlet.Source.signature_preview.name());
        request.getSession().setAttribute(SessionConstants.LOGGED_IN_PROVIDER, new Provider());
        return request;
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
