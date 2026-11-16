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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.support;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * The text the pipeline prints into a document: the document notes that tell the reader a document
 * was simplified, and the label of the box that replaces an image.
 *
 * <p>The sentences are entries of the {@code oscarResources} bundle ({@code htmlpdf.note.*} and
 * {@code htmlpdf.imageUnavailable}), so translators work on them like any other message. They are
 * always read in English, with {@link Locale#ENGLISH} and a control that never falls back to the
 * server's default locale, so the same document prints the same words on every server. Printing in
 * the reader's language needs callers to pass a locale (a follow-up). If the bundle cannot be read,
 * built-in English text with the same wording is printed instead: a note must never be the reason a
 * render fails.</p>
 *
 * @since 2026-10-29
 */
public final class PrintMessages {

    private static final Logger logger = MiscUtils.getLogger();

    static final String IMAGE_UNAVAILABLE_KEY = "htmlpdf.imageUnavailable";
    static final String SAFE_MODE_KEY = "htmlpdf.note.safeMode";
    static final String TEXT_ONLY_KEY = "htmlpdf.note.textOnly";
    static final String IMAGES_OMITTED_KEY = "htmlpdf.note.imagesOmittedForMemory";
    static final String RTL_BEST_EFFORT_KEY = "htmlpdf.note.rtlBestEffort";

    /** The English wording, used if the bundle is unavailable; a test keeps it equal to the bundle's. */
    static final String IMAGE_UNAVAILABLE_ENGLISH = "[image unavailable]";
    static final String SAFE_MODE_ENGLISH =
            "This document was printed with simplified formatting because its layout could not be rendered.";
    static final String TEXT_ONLY_ENGLISH =
            "This document was printed as plain text because its layout could not be rendered.";
    static final String IMAGES_OMITTED_ENGLISH =
            "Images were omitted because the server was busy. Print again to include them.";
    static final String RTL_BEST_EFFORT_ENGLISH = "Right-to-left text was converted for printing. Check the line order"
            + " of longer right-to-left passages against the original.";

    private PrintMessages() {
    }

    /**
     * The label inside the box that stands for an image that could not be shown.
     *
     * @return {@code [image unavailable]}
     */
    public static String imageUnavailable() {
        return lookup(IMAGE_UNAVAILABLE_KEY, IMAGE_UNAVAILABLE_ENGLISH);
    }

    /**
     * The sentence the document note carries for a degradation.
     *
     * @param degradation what happened to the render
     * @return the sentence, or empty when the degradation changes no content the reader needs to be
     *         told about (a downscaled image, smaller margins) or is visible where it happens (the box)
     */
    public static Optional<String> noteFor(HtmlPdfRenderDegradation degradation) {
        return switch (degradation) {
            case SAFE_MODE -> Optional.of(lookup(SAFE_MODE_KEY, SAFE_MODE_ENGLISH));
            case TEXT_ONLY -> Optional.of(lookup(TEXT_ONLY_KEY, TEXT_ONLY_ENGLISH));
            case IMAGES_OMITTED_FOR_MEMORY -> Optional.of(lookup(IMAGES_OMITTED_KEY, IMAGES_OMITTED_ENGLISH));
            case RTL_BEST_EFFORT -> Optional.of(lookup(RTL_BEST_EFFORT_KEY, RTL_BEST_EFFORT_ENGLISH));
            default -> Optional.empty();
        };
    }

    /** The English bundle's entry for the key, or the built-in English text when it is missing or blank. */
    static String lookup(String key, String builtIn) {
        return lookup(EnglishBundle.INSTANCE, key, builtIn);
    }

    /** The bundle's entry for the key, or the built-in text when there is no bundle or the entry is missing or blank. */
    static String lookup(ResourceBundle bundle, String key, String builtIn) {
        if (bundle != null) {
            try {
                String value = bundle.getString(key);
                if (!value.isBlank()) {
                    return value;
                }
            } catch (MissingResourceException e) {
                // Falls through to the built-in wording.
            }
        }
        return builtIn;
    }

    /** Loads the bundle on first use: {@code oscarResources_en.properties}, never the default locale's. */
    private static final class EnglishBundle {
        static final ResourceBundle INSTANCE = load();

        private static ResourceBundle load() {
            try {
                return ResourceBundle.getBundle("oscarResources", Locale.ENGLISH,
                        ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
            } catch (RuntimeException e) {
                logger.warn("The oscarResources bundle could not be read; document notes use built-in English ({})",
                        e.getClass().getSimpleName());
                return null;
            }
        }
    }
}
