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
package io.github.carlos_emr.carlos.demographic.cdsimport.web;

import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportAccessService;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("unit")
class CdsWebErrorProjectionUnitTest {
    private final CdsWebService web = new CdsWebService(org.mockito.Mockito.mock(CdsImportAccessService.class));
    @Test void shouldRecognizeTypedSubclasses_withoutSerializingProtectedCauses() throws Exception {
        var response=new MockHttpServletResponse();
        web.failure(response,new CdsImportException("OWNER_FENCED",false,new IllegalStateException("/private/source.xml")) { });
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("OWNER_FENCED").doesNotContain("/private","IllegalStateException");
    }
    @Test void shouldRejectForeignSameNamedException_insteadOfTrustingItsSimpleClassName() throws Exception {
        var response=new MockHttpServletResponse();web.failure(response,new Foreign.CdsImportException("SECRET_SOURCE_VALUE"));
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("IMPORT_REQUEST_UNAVAILABLE").doesNotContain("SECRET_SOURCE_VALUE");
    }
    @Test void shouldKeepUnclassifiedWrapperDetailsPrivate_whenProjectingFailure() throws Exception {
        var response=new MockHttpServletResponse();web.failure(response,new RuntimeException("/private/path",new CdsImportException("OWNER_FENCED")));
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).doesNotContain("/private","OWNER_FENCED");
    }

    @Test void shouldReplaceAnUncommittedDownload_withACompleteJsonError() throws Exception {
        var body = new ByteArrayOutputStream();
        var coyote = new org.apache.coyote.Response();
        var coyoteRequest = new org.apache.coyote.Request();
        var request = new org.apache.catalina.connector.Request(
                new org.apache.catalina.connector.Connector(), coyoteRequest);
        coyoteRequest.setNote(1, request);
        coyote.setRequest(coyoteRequest);
        coyote.setOutputBuffer(new org.apache.coyote.OutputBuffer() {
            @Override public int doWrite(ByteBuffer bytes) {
                int length = bytes.remaining();
                byte[] chunk = new byte[length];
                bytes.get(chunk);
                body.writeBytes(chunk);
                return length;
            }
            @Override public long getBytesWritten() { return body.size(); }
        });
        var response = new org.apache.catalina.connector.Response(coyote);
        response.setContentType("application/octet-stream");
        response.setContentLengthLong(4096);
        response.setHeader("Content-Disposition", "attachment; filename=\"source.bin\"");
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setHeader("Content-Security-Policy", "sandbox");
        response.getOutputStream().write("partial source value".getBytes(StandardCharsets.UTF_8));

        web.failure(response, new IOException("/private/source.bin"));
        response.flushBuffer();

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(response.getContentLength()).isIn(-1, body.size());
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo("sandbox");
        var error = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body.toByteArray());
        assertThat(error.path("ok").asBoolean()).isFalse();
        assertThat(error.path("code").asText()).isEqualTo("IMPORT_REQUEST_UNAVAILABLE");
        assertThat(body.toString(StandardCharsets.UTF_8)).doesNotContain("partial source", "/private");
    }

    @Test void shouldPropagateFailureAfterCommit_insteadOfCompletingATruncatedDownload() {
        var response = new MockHttpServletResponse();
        response.setCommitted(true);
        var failure = new IOException("/private/source.bin");

        assertThatThrownBy(() -> web.failure(response, failure))
                .isInstanceOf(IOException.class).hasCause(failure);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }
    @Test void shouldRefuseEverySensitiveSummaryKeyCase_withLinearSubstringChecks() {
        for(String key:new String[]{"reportLocation","DIRECTORY","sourcePassword","aSeCrEtValue","sourceXPath"}) {
            assertThatThrownBy(()->web.summary(Map.of("outer",Map.of(key,"private"))))
                    .hasMessage("IMPORT_SUMMARY_INVALID");
        }
        assertThat(web.summary(Map.of("lastCheckpoint","2026-01-01","pendingUnits",2)))
                .isEqualTo(Map.of("lastCheckpoint","2026-01-01","pendingUnits",2));
    }
    private static final class Foreign {
        private static final class CdsImportException extends RuntimeException {
            private static final long serialVersionUID=1L;
            private CdsImportException(String code) { super(code); }
        }
    }
}
