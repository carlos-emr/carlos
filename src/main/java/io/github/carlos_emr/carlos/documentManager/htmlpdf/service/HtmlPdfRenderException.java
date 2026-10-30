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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.service;

import java.util.Objects;

/**
 * A failure inside one attempt of the render pipeline, stripped of anything that could identify a
 * patient. Thrown and caught only inside the service package: the render service turns an exhausted
 * ladder into a {@code PDFGenerationException} with the last one of these as its cause.
 *
 * <p>Flying Saucer, Jsoup and OpenPDF put document text in their exception messages (a parse error
 * quotes the markup around it, a font error names the character), so the original exception is never
 * chained and its message is never copied. This exception records the mode, the phase and the
 * original exception's class name, and takes over the original's stack trace, which keeps the useful
 * line numbers for whoever has to fix the defect. A caller that logs the whole exception therefore
 * logs no PHI.</p>
 *
 * @since 2026-10-29
 */
final class HtmlPdfRenderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Where in the attempt it failed. */
    enum Phase {
        /** Parsing and the document rules. */
        PREPARE,
        /** Planning and applying image handling. */
        IMAGES,
        /** The first layout pass, which also decides the page width. */
        LAYOUT_PASS_1,
        /** The second layout pass, on the page size the first one calls for. */
        LAYOUT_PASS_2,
        /** Placing the pages on US Letter. */
        PLACE,
        /** The checks on the finished PDF. */
        VALIDATE
    }

    private final PrintMode mode;
    private final Phase phase;

    /**
     * Records a failure.
     *
     * @param mode the rung of the ladder the attempt ran on
     * @param phase where it failed
     * @param original what was thrown; its class name and stack trace are kept, nothing else
     */
    HtmlPdfRenderException(PrintMode mode, Phase phase, Throwable original) {
        super("Rendering failed in " + Objects.requireNonNull(mode, "mode") + " mode during "
                + Objects.requireNonNull(phase, "phase") + " (" + Objects.requireNonNull(original, "original").getClass().getName() + ")");
        this.mode = mode;
        this.phase = phase;
        setStackTrace(original.getStackTrace());
    }

    Phase phase() {
        return phase;
    }

    PrintMode mode() {
        return mode;
    }
}
