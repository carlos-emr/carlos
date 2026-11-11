/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The version query on the "Send to myCarlos" scripts and stylesheet: a hash of their bytes, worked
 * out once per application start.
 *
 * @since 2026-11-10
 */
@Tag("unit")
@Tag("fast")
@DisplayName("Send to myCarlos asset version")
class MyCarlosSendAssetsUnitTest {

    /** A context serving the given files and keeping attributes, as a servlet container does. */
    private static ServletContext context(Map<String, String> files) {
        ServletContext context = mock(ServletContext.class);
        Map<String, Object> attributes = new HashMap<>();
        when(context.getResourceAsStream(anyString())).thenAnswer(call -> {
            String body = files.get(call.<String>getArgument(0));
            return body == null ? null : new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        });
        when(context.getAttribute(anyString())).thenAnswer(call -> attributes.get(call.<String>getArgument(0)));
        doAnswer(call -> attributes.put(call.getArgument(0), call.getArgument(1)))
                .when(context).setAttribute(anyString(), any());
        return context;
    }

    private static Map<String, String> files(String send) {
        return Map.of(MyCarlosSendAssets.PATHS.get(0), send,
                MyCarlosSendAssets.PATHS.get(1), "FAKE dialog script",
                MyCarlosSendAssets.PATHS.get(2), "FAKE stylesheet");
    }

    @Test
    @DisplayName("should give 12 hex characters that change when an asset's bytes change")
    void shouldChangeVersion_whenAnAssetChanges() {
        String before = MyCarlosSendAssets.version(context(files("FAKE send script v1")));
        String after = MyCarlosSendAssets.version(context(files("FAKE send script v2")));
        assertThat(before).matches("[0-9a-f]{12}");
        assertThat(after).matches("[0-9a-f]{12}").isNotEqualTo(before);
        assertThat(MyCarlosSendAssets.version(context(files("FAKE send script v1")))).isEqualTo(before);
    }

    @Test
    @DisplayName("should work the hash out once per application and keep it")
    void shouldKeepVersion_forTheApplicationsLife() {
        ServletContext context = context(files("FAKE send script v1"));
        String first = MyCarlosSendAssets.version(context);
        assertThat(MyCarlosSendAssets.version(context)).isEqualTo(first);
        verify(context, times(MyCarlosSendAssets.PATHS.size())).getResourceAsStream(anyString());
        assertThat(context.getAttribute(MyCarlosSendAssets.ATTRIBUTE)).isEqualTo(first);
    }

    @Test
    @DisplayName("should still give a version when an asset is missing or cannot be read")
    void shouldGiveVersion_whenAnAssetIsMissingOrUnreadable() throws IOException {
        ServletContext missing = context(Map.of());
        assertThat(MyCarlosSendAssets.version(missing)).matches("[0-9a-f]{12}");

        ServletContext unreadable = context(files("FAKE send script v1"));
        InputStream broken = mock(InputStream.class);
        when(broken.readAllBytes()).thenThrow(new IOException("FAKE read failure"));
        when(unreadable.getResourceAsStream(MyCarlosSendAssets.PATHS.get(0))).thenReturn(broken);
        assertThat(MyCarlosSendAssets.version(unreadable)).matches("[0-9a-f]{12}");
    }

    @Test
    @DisplayName("should name the three files the pages load, which exist in the webapp")
    void shouldNameShippedAssets_forTheirPaths() {
        assertThat(MyCarlosSendAssets.PATHS).hasSize(3);
        for (String path : MyCarlosSendAssets.PATHS) {
            assertThat(Files.isRegularFile(Path.of("src/main/webapp" + path))).as(path).isTrue();
        }
    }
}
