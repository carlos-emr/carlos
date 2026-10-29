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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.dto;

import static io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation.FALLBACK_FONT_UNAVAILABLE;
import static io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation.IMAGES_OMITTED_FOR_MEMORY;
import static io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation.IMAGE_CONVERTED;
import static io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation.IMAGE_DOWNSCALED;
import static io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE;
import static io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation.MARGINS_REDUCED;
import static io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation.RTL_BEST_EFFORT;
import static io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation.SAFE_MODE;
import static io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation.SVG_REFERENCES_REMOVED;
import static io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation.TEXT_ONLY;
import static io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation.WEBAPP_ROOT_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link HtmlPdfRenderDegradation}: each constant's log level is the one the
 * design's Observability table gives it, because an operator reads the level to decide whether to
 * act.
 *
 * @since 2026-10-29
 */
@DisplayName("HtmlPdfRenderDegradation")
@Tag("unit")
@Tag("htmlpdf")
class HtmlPdfRenderDegradationUnitTest {

    @Test
    void shouldMapLevels_perSpecObservabilityTable() {
        Map<Level, Set<HtmlPdfRenderDegradation>> byLevel = new HashMap<>();
        for (HtmlPdfRenderDegradation degradation : HtmlPdfRenderDegradation.values()) {
            byLevel.computeIfAbsent(degradation.logLevel(), level -> EnumSet.noneOf(HtmlPdfRenderDegradation.class))
                    .add(degradation);
        }

        // Content kept; nothing to do.
        assertThat(byLevel.get(Level.INFO)).containsExactlyInAnyOrder(
                IMAGE_DOWNSCALED, IMAGE_CONVERTED, SVG_REFERENCES_REMOVED, MARGINS_REDUCED, RTL_BEST_EFFORT);
        // Content visibly missing; check the source or the server's load.
        assertThat(byLevel.get(Level.WARN)).containsExactlyInAnyOrder(
                IMAGE_UNAVAILABLE, IMAGES_OMITTED_FOR_MEMORY, FALLBACK_FONT_UNAVAILABLE, WEBAPP_ROOT_UNAVAILABLE);
        // A template or engine defect to fix.
        assertThat(byLevel.get(Level.ERROR)).containsExactlyInAnyOrder(SAFE_MODE, TEXT_ONLY);
        // No constant is left on another level, so a new constant must be placed in the table above.
        assertThat(byLevel).containsOnlyKeys(Level.INFO, Level.WARN, Level.ERROR);
    }
}
