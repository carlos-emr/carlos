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
package io.github.carlos_emr.carlos.fax.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The fax clients must never buffer an unbounded provider response: with
 * the packaged JVM's -XX:+ExitOnOutOfMemoryError, one oversized body used
 * to take the whole EMR down, and the scheduler retrying the same item
 * turned it into a deterministic crash loop. These tests pin the contract
 * that an oversized body — declared, chunked, or lying about its length —
 * fails as an IOException on that one request instead.
 */
class BoundedResponseReaderUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("body under the cap is returned intact with its declared charset")
    void underCapReturnsBody() throws IOException {
        byte[] body = "{\"Status\":\"Success\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(BoundedResponseReader.read(
                new ByteArrayEntity(body, ContentType.APPLICATION_JSON), 1024))
                .isEqualTo("{\"Status\":\"Success\"}");
    }

    @Test
    @DisplayName("an honestly-declared oversized body fails fast, before any read")
    void declaredOversizeFailsFast() {
        byte[] body = new byte[64];
        BasicHttpEntity entity = new BasicHttpEntity(
                new ByteArrayInputStream(body), 10_000_000L, ContentType.APPLICATION_JSON);
        assertThatThrownBy(() -> BoundedResponseReader.read(entity, 1024))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("fax response over")
                .hasMessageContaining(BoundedResponseReader.MAX_RESPONSE_MB_PROPERTY);
    }

    @Test
    @DisplayName("a chunked body (no Content-Length) is capped on the bytes actually read")
    void chunkedOversizeIsCapped() {
        InputStream endless = new InputStream() {
            @Override
            public int read() {
                return 'x';
            }
        };
        BasicHttpEntity entity = new BasicHttpEntity(endless, ContentType.APPLICATION_JSON, true);
        assertThatThrownBy(() -> BoundedResponseReader.read(entity, 4096))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("fax response over");
    }

    @Test
    @DisplayName("a malformed Content-Type charset falls back to UTF-8 instead of throwing unchecked")
    void malformedCharsetFallsBack() throws IOException {
        byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        org.apache.hc.core5.http.HttpEntity entity =
                org.mockito.Mockito.mock(org.apache.hc.core5.http.HttpEntity.class);
        org.mockito.Mockito.when(entity.getContentType())
                .thenReturn("application/json; charset=not-a-real-charset");
        org.mockito.Mockito.when(entity.getContentLength()).thenReturn((long) body.length);
        org.mockito.Mockito.when(entity.getContent()).thenReturn(new ByteArrayInputStream(body));
        assertThat(BoundedResponseReader.read(entity, 1024)).isEqualTo("{\"ok\":true}");
    }

    @Test
    @DisplayName("an overstated Content-Length does not pre-allocate the cap (tiny body, huge header)")
    void overstatedContentLengthDoesNotPreallocate() throws IOException {
        // 10-byte body claiming 256 MiB: must read fine and allocate for the
        // body, never for the header. (Regression: the pre-size once trusted
        // this header and reserved the whole cap.)
        byte[] body = "{\"ok\":1}".getBytes(StandardCharsets.UTF_8);
        org.apache.hc.core5.http.HttpEntity entity =
                org.mockito.Mockito.mock(org.apache.hc.core5.http.HttpEntity.class);
        org.mockito.Mockito.when(entity.getContentType())
                .thenReturn("application/json");
        org.mockito.Mockito.when(entity.getContentLength()).thenReturn(256L * 1024 * 1024);
        org.mockito.Mockito.when(entity.getContent()).thenReturn(new ByteArrayInputStream(body));
        // cap comfortably above the (false) declared length; the point is the
        // read succeeds without OOM despite the header, on a small heap.
        assertThat(BoundedResponseReader.read(entity, 512L * 1024 * 1024))
                .isEqualTo("{\"ok\":1}");
    }

    @Test
    @DisplayName("a body whose Content-Length understates its real size is still capped")
    void lyingContentLengthIsCapped() {
        byte[] body = new byte[8192];
        BasicHttpEntity entity = new BasicHttpEntity(
                new ByteArrayInputStream(body), 10L, ContentType.APPLICATION_JSON);
        assertThatThrownBy(() -> BoundedResponseReader.read(entity, 1024))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("fax response over");
    }
}
