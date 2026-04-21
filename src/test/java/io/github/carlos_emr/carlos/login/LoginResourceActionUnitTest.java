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
package io.github.carlos_emr.carlos.login;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.mockito.Mockito.*;

@Tag("unit")
@DisplayName("LoginResourceAction")
class LoginResourceActionUnitTest {

    private LoginResourceAction servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private ServletConfig servletConfig;
    private ServletContext servletContext;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        servletContext = mock(ServletContext.class);
        servletConfig = mock(ServletConfig.class);
        when(servletConfig.getServletContext()).thenReturn(servletContext);

        servlet = new LoginResourceAction() {
            @Override
            public ServletContext getServletContext() {
                return servletContext;
            }

            @Override
            protected String getImagesDir() {
                return tempDir.toFile().getAbsolutePath();
            }
        };

        servlet.init(servletConfig);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @Test
    @DisplayName("should return 204 when logo file does not exist")
    void shouldReturn204_whenLogoFileDoesNotExist() throws Exception {
        when(request.getPathInfo()).thenReturn("/clinicLogo.png");

        servlet.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_NO_CONTENT);
        verify(response, never()).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    @DisplayName("should return 404 when path info is null")
    void shouldReturn404_whenPathInfoIsNull() throws Exception {
        when(request.getPathInfo()).thenReturn(null);

        servlet.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    @DisplayName("should return 400 when filename is empty after sanitization")
    void shouldReturn400_whenPathContainsDirectoryTraversal() throws Exception {
        // A path that results in an empty filename after sanitization
        when(request.getPathInfo()).thenReturn("/");

        servlet.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
    }

    @Test
    @DisplayName("should serve image when logo file exists")
    void shouldServeImage_whenLogoFileExists() throws Exception {
        // Create a real temp image file
        File logoFile = tempDir.resolve("clinicLogo.png").toFile();
        logoFile.createNewFile();

        when(request.getPathInfo()).thenReturn("/clinicLogo.png");
        when(servletContext.getMimeType("clinicLogo.png")).thenReturn("image/png");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        when(response.getWriter()).thenReturn(pw);

        jakarta.servlet.ServletOutputStream sos = mock(jakarta.servlet.ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(sos);

        servlet.doGet(request, response);

        verify(response).setContentType("image/png");
        verify(response, never()).sendError(anyInt(), anyString());
        verify(response, never()).setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}