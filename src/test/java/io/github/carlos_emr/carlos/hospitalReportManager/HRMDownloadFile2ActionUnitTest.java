/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.hospitalReportManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Content-Disposition contract for the HRM report body.
 *
 * <p>The report viewer frames this endpoint to show the report in place. An attachment
 * disposition makes the browser download it from that frame instead of rendering it, leaving
 * the preview blank and starting a download nobody asked for — so the frame opts in to inline.
 * Everything else must keep downloading, and HTML must never go inline: served from the
 * application's own origin it would execute there.
 */
@DisplayName("HRMDownloadFile2Action Content-Disposition")
@Tag("unit")
@Tag("hrm")
class HRMDownloadFile2ActionUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {"application/pdf", "image/tiff", "image/jpeg", "image/gif", "image/png"})
    @DisplayName("should serve inline when the frame asks and the type renders as data")
    void shouldServeInline_forRequestedRenderableTypes(String contentType) {
        assertThat(HRMDownloadFile2Action.dispositionFor("inline", contentType)).isEqualTo("inline");
    }

    /**
     * The security property: inline HTML from this origin would execute in it.
     */
    @ParameterizedTest
    @ValueSource(strings = {"text/html", "application/octet-stream", "text/enriched"})
    @DisplayName("should keep downloading types that are unsafe to render inline")
    void shouldForceAttachment_forTypesUnsafeInline(String contentType) {
        assertThat(HRMDownloadFile2Action.dispositionFor("inline", contentType)).isEqualTo("attachment");
    }

    @ParameterizedTest
    @ValueSource(strings = {"attachment", "INLINE", "inline ", "", "anything"})
    @DisplayName("should keep downloading unless inline is asked for exactly")
    void shouldForceAttachment_unlessInlineRequestedExactly(String requested) {
        assertThat(HRMDownloadFile2Action.dispositionFor(requested, "application/pdf")).isEqualTo("attachment");
    }

    @Test
    @DisplayName("should keep downloading when no disposition is requested")
    void shouldForceAttachment_whenNoDispositionRequested() {
        assertThat(HRMDownloadFile2Action.dispositionFor(null, "application/pdf")).isEqualTo("attachment");
    }
}
