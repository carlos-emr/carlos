/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ChartRenderFailureUnitTest {
    @Test
    void shouldDistinguishRetryableFailures_fromInvalidDataAndDrawBugs() {
        assertThat(ChartRenderFailure.status(new ChartRenderFailure(ChartRenderFailure.Reason.BUSY, "busy"))).isEqualTo(503);
        assertThat(ChartRenderFailure.status(new ChartRenderFailure(ChartRenderFailure.Reason.UNAVAILABLE, "down"))).isEqualTo(503);
        assertThat(ChartRenderFailure.status(new ChartRenderFailure(ChartRenderFailure.Reason.DRAW_FAILED, "draw"))).isEqualTo(500);
        var clinical = new IllegalArgumentException("SYNTHETIC clinical value must not escape");
        assertThat(ChartRenderFailure.status(clinical)).isEqualTo(422);
        assertThat(ChartRenderFailure.code(clinical)).isEqualTo("INVALID_DATA");
    }
}
