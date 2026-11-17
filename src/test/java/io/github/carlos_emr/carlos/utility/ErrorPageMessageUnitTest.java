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
package io.github.carlos_emr.carlos.utility;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ErrorPageMessage")
@Tag("unit")
@Tag("fast")
class ErrorPageMessageUnitTest {

    @Test
    @DisplayName("should set the message key for the error page and send the status with no message text")
    void shouldSetKeyAndSendStatus_whenKeyAndStatusAreValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ErrorPageMessage.sendError(request, response, HttpServletResponse.SC_NOT_FOUND, "messenger.ViewPDFFile.noAttachment");

        assertThat(request.getAttribute(ErrorPageMessage.ATTRIBUTE)).isEqualTo("messenger.ViewPDFFile.noAttachment");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        assertThat(response.getErrorMessage()).isNull();
        assertThat(response.isCommitted()).isTrue();
    }

    @ParameterizedTest(name = "key={0}")
    @NullSource
    @ValueSource(strings = {"", "noDot", "has space.key", "${param.x}", "messenger.ViewPDFFile.<b>", ".leadingDot"})
    @DisplayName("should refuse anything that is not a dotted message key, and leave the response alone")
    void shouldRefuse_whenKeyIsNotAMessageKey(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> ErrorPageMessage.sendError(request, response, HttpServletResponse.SC_BAD_REQUEST, key))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(request.getAttribute(ErrorPageMessage.ATTRIBUTE)).isNull();
        assertThat(response.isCommitted()).isFalse();
    }

    @Test
    @DisplayName("should refuse a status that is not an error")
    void shouldRefuse_whenStatusIsNotAnError() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> ErrorPageMessage.sendError(request, response, HttpServletResponse.SC_OK, "messenger.ViewPDFFile.noAttachment"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(response.isCommitted()).isFalse();
    }
}
