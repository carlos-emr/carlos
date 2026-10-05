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
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The catalogue download's address and response checks; no network. */
@Tag("unit")
@Tag("fast")
@DisplayName("National Vaccine Catalogue download")
class NationalVaccineCatalogueClientUnitTest {

    @Test
    @DisplayName("should download only over HTTPS from the National Vaccine Catalogue host")
    void shouldUseFixedHttpsAddress_whenBuildingTheRequest() {
        assertThat(NationalVaccineCatalogueClient.BUNDLE_URI.getScheme()).isEqualTo("https");
        assertThat(NationalVaccineCatalogueClient.BUNDLE_URI.getHost()).isEqualTo("nvc-cnv.canada.ca");
        assertThat(NationalVaccineCatalogueClient.BUNDLE_URI.getPath()).isEqualTo("/fhir/v2/Bundle/NVC");
    }

    @Test
    @DisplayName("should return the body when the catalogue answers 200")
    void shouldReturnBody_whenCatalogueAnswersOk() throws IOException {
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        response.setEntity(new StringEntity("{\"resourceType\":\"Bundle\"}", ContentType.APPLICATION_JSON));

        assertThat(read(response, mock(Cancellable.class))).isEqualTo("{\"resourceType\":\"Bundle\"}");
    }

    @Test
    @DisplayName("should cancel the request before closing the body when the body passes the cap")
    void shouldCancelBeforeClosingBody_whenBodyPassesTheCap() {
        List<String> events = new ArrayList<>();
        Cancellable request = () -> events.add("cancel");
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        response.setEntity(new InputStreamEntity(new ByteArrayInputStream(new byte[100]) {
            @Override
            public void close() {
                events.add("close");
            }
        }, -1, ContentType.APPLICATION_JSON));

        assertThatThrownBy(() -> NationalVaccineCatalogueClient.readBundle(response, request, 10, farFuture()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("larger than 10 bytes");
        assertThat(events).containsExactly("cancel", "close");
    }

    @Test
    @DisplayName("should cancel the request when the declared length is over the cap or the answer is not 200")
    void shouldCancelRequest_whenAnswerIsRefusedBeforeReading() {
        Cancellable tooLarge = mock(Cancellable.class);
        BasicClassicHttpResponse large = new BasicClassicHttpResponse(200);
        large.setEntity(new InputStreamEntity(new ByteArrayInputStream(new byte[0]), 11, ContentType.APPLICATION_JSON));
        Cancellable refused = mock(Cancellable.class);
        BasicClassicHttpResponse error = new BasicClassicHttpResponse(503);

        assertThatThrownBy(() -> NationalVaccineCatalogueClient.readBundle(large, tooLarge, 10, farFuture()))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> NationalVaccineCatalogueClient.readBundle(error, refused, 10, farFuture()))
                .isInstanceOf(IOException.class);
        verify(tooLarge).cancel();
        verify(refused).cancel();
    }

    @Test
    @DisplayName("should keep the connection when the answer is read")
    void shouldNotCancelRequest_whenAnswerIsRead() throws IOException {
        Cancellable request = mock(Cancellable.class);
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        response.setEntity(new StringEntity("{\"resourceType\":\"Bundle\"}", ContentType.APPLICATION_JSON));

        assertThat(read(response, request)).contains("Bundle");
        verify(request, never()).cancel();
    }

    @Test
    @DisplayName("should fail when the catalogue answers anything but 200")
    void shouldFail_whenCatalogueAnswersError() {
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(406);
        response.setEntity(new StringEntity("not acceptable", ContentType.TEXT_PLAIN));

        assertThatThrownBy(() -> read(response, mock(Cancellable.class)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 406");
    }

    @Test
    @DisplayName("should fail before reading when the declared length is over the limit")
    void shouldFail_whenDeclaredLengthIsTooLarge() {
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        response.setEntity(new InputStreamEntity(new ByteArrayInputStream(new byte[0]),
                NationalVaccineCatalogueClient.MAX_BUNDLE_BYTES + 1L, ContentType.APPLICATION_JSON));

        assertThatThrownBy(() -> read(response, mock(Cancellable.class)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("larger than");
    }

    @Test
    @DisplayName("should fail when the body is empty")
    void shouldFail_whenBodyIsEmpty() {
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(200);
        response.setEntity(new StringEntity("  ", ContentType.APPLICATION_JSON));

        assertThatThrownBy(() -> read(response, mock(Cancellable.class)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("should stop reading as soon as the body passes the limit")
    void shouldFail_whenBodyPassesTheLimit() {
        byte[] body = "0123456789".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> NationalVaccineCatalogueClient.readBounded(new ByteArrayInputStream(body), 9, farFuture()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("larger than 9 bytes");
    }

    @Test
    @DisplayName("should stop reading once the download deadline has passed")
    void shouldFail_whenDeadlinePassed() {
        byte[] body = "0123456789".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> NationalVaccineCatalogueClient.readBounded(new ByteArrayInputStream(body), 10,
                System.nanoTime() - 1))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("took longer than");
    }

    @Test
    @DisplayName("should read a body exactly at the limit")
    void shouldReadBody_whenAtTheLimit() throws IOException {
        byte[] body = "0123456789".getBytes(StandardCharsets.UTF_8);

        assertThat(NationalVaccineCatalogueClient.readBounded(new ByteArrayInputStream(body), 10, farFuture())).isEqualTo("0123456789");
    }

    private static String read(BasicClassicHttpResponse response, Cancellable request) throws IOException {
        return NationalVaccineCatalogueClient.readBundle(response, request,
                NationalVaccineCatalogueClient.MAX_BUNDLE_BYTES, farFuture());
    }

    private static long farFuture() {
        return System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
    }
}
