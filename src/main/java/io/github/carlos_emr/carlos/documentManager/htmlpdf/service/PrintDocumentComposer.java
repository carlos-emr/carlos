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

import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.FallbackFontRuns;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.LegacyBodyMargins;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.PlainTextDocument;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.PrintFormControls;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.PrintMessages;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.PrintStylesheet;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.RtlTextShaping;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.SafeModeMarkup;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.SystemColors;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.XhtmlCleanup;
import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Applies the document rules of the render pipeline in their fixed order: parses the HTML and turns
 * it into a document ready for images and layout ({@link #prepare}), then adds the style sheet and
 * the document note and writes the XHTML Flying Saucer reads ({@link #finish}). Pure DOM work: no
 * file, network or clock access, no state, so one instance serves every render.
 *
 * <p>The steps, in order (the letters are the spec's):</p>
 *
 * <ol>
 *   <li>{@link #prepare}: [safe mode: author styles and inline graphics removed] a. scripts and
 *       comments; b. soft hyphens, bidi controls and XML-invalid characters; c. form controls;
 *       d. {@code position: fixed}; e. {@code <ol type>}; f. row and column spans; g. paginated
 *       tables; h. right-to-left text; i. {@code alt} text. [Text-only mode: the cleaned document
 *       is then reduced to its text.] Last, n. the blocks that hold a very long word are marked and
 *       o. the CSS2 system colour keywords Word writes ({@code windowtext}) become colours, in every mode.</li>
 *   <li>Image handling (step j), by {@code PrintImageService}, runs between the two methods because
 *       it is the only step with side effects.</li>
 *   <li>{@link #finish}: k. the {@code carlos-pdf-defaults} style sheet as the first child of
 *       {@code <head>} (with the body's legacy {@code leftmargin} and {@code topmargin} attributes as
 *       a rule, see {@code LegacyBodyMargins}) and the document note as the first child of
 *       {@code <body>}; l. fallback
 *       fonts, after the note and the boxes so their text is covered too; m. serialisation.</li>
 * </ol>
 *
 * <p>The order matters, and each dependency is tested: control characters go before the form
 * controls copy a value and before right-to-left text is reordered; the form controls are replaced
 * before reordering, so a Hebrew value is reordered like any text; spans are clamped before tables
 * are marked for pagination; fallback fonts come last, because the spans they add would split the
 * runs that right-to-left shaping reorders.</p>
 *
 * @since 2026-10-29
 */
@Service
class PrintDocumentComposer {

    /** The {@code id} of the injected style sheet. */
    static final String DEFAULTS_STYLE_ID = "carlos-pdf-defaults";

    /** The class of the document note. */
    static final String NOTE_CLASS = "carlos-pdf-note";

    /** The order the note lists its reasons in. */
    private static final List<HtmlPdfRenderDegradation> NOTE_ORDER = List.of(HtmlPdfRenderDegradation.SAFE_MODE,
            HtmlPdfRenderDegradation.TEXT_ONLY, HtmlPdfRenderDegradation.IMAGES_OMITTED_FOR_MEMORY,
            HtmlPdfRenderDegradation.RTL_BEST_EFFORT);

    /**
     * Parses the HTML and applies steps a to i for the mode.
     *
     * @param html the document's HTML
     * @param mode the rung of the ladder; safe mode first strips the author's styles and inline
     *        graphics, text-only mode returns a new document holding only the text
     * @param degradations the attempt's degradations; {@code RTL_BEST_EFFORT} is added when a long
     *        right-to-left run is found
     * @return the document, ready for the image step
     */
    Document prepare(String html, PrintMode mode, Set<HtmlPdfRenderDegradation> degradations) {
        Objects.requireNonNull(html, "html");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(degradations, "degradations");
        Document document = Jsoup.parse(html);
        if (mode == PrintMode.SAFE) {
            SafeModeMarkup.simplify(document);
        }
        XhtmlCleanup.removeScriptsAndComments(document);
        XhtmlCleanup.removeInvisibleCharacters(document);
        PrintFormControls.replaceControls(document);
        XhtmlCleanup.rewriteFixedPositioning(document);
        XhtmlCleanup.mapOrderedListTypes(document);
        XhtmlCleanup.clampRowspans(document);
        XhtmlCleanup.clampColspans(document);
        XhtmlCleanup.markPaginatedTables(document);
        RtlTextShaping.shape(document, degradations);
        XhtmlCleanup.addMissingAltText(document);
        Document prepared = mode == PrintMode.TEXT_ONLY ? PlainTextDocument.from(document) : document;
        XhtmlCleanup.markLongWords(prepared);
        SystemColors.replace(prepared);
        return prepared;
    }

    /**
     * Applies steps k to m: the style sheet, the note, the fallback fonts and the serialisation.
     *
     * @param document the document after {@link #prepare} and the image step
     * @param mode the rung of the ladder (the note's wording comes from the degradations)
     * @param fonts the bundled fonts, for the fallback-font step
     * @param degradations the attempt's degradations: the note lists the reasons among them that
     *        need one, and {@code FALLBACK_FONT_UNAVAILABLE} is added if a fallback face is missing
     * @return XHTML that a namespace-aware XML parser accepts
     */
    String finish(Document document, PrintMode mode, BundledFontSet fonts, Set<HtmlPdfRenderDegradation> degradations) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(fonts, "fonts");
        Objects.requireNonNull(degradations, "degradations");
        insertDefaultStyles(document);
        insertNote(document, degradations);
        FallbackFontRuns.apply(document, fonts, degradations);
        return XhtmlCleanup.serialize(document);
    }

    /**
     * The defaults go first so that author rules of equal specificity still win; the {@code @page}
     * rule wins anyway because it is {@code !important}. The body's legacy margin attributes
     * ({@code leftmargin} and the like) follow them in the same sheet, as WebKit's presentational hints
     * did: they lose to the author's rules, not to ours.
     */
    private static void insertDefaultStyles(Document document) {
        Element style = new Element("style");
        style.attr("id", DEFAULTS_STYLE_ID);
        style.appendChild(new DataNode(LegacyBodyMargins.rule(document)
                .map(rule -> PrintStylesheet.css() + rule + "\n")
                .orElseGet(PrintStylesheet::css)));
        headOf(document).prependChild(style);
    }

    private static void insertNote(Document document, Set<HtmlPdfRenderDegradation> degradations) {
        List<String> sentences = new ArrayList<>();
        for (HtmlPdfRenderDegradation degradation : NOTE_ORDER) {
            if (degradations.contains(degradation)) {
                Optional<String> sentence = PrintMessages.noteFor(degradation);
                sentence.ifPresent(sentences::add);
            }
        }
        if (sentences.isEmpty()) {
            return;
        }
        Element note = new Element("div");
        note.addClass(NOTE_CLASS);
        for (String sentence : sentences) {
            note.appendElement("div").appendText(sentence);
        }
        bodyOf(document).prependChild(note);
    }

    private static Element headOf(Document document) {
        Element head = document.head();
        if (head == null) {
            head = new Element("head");
            htmlOf(document).prependChild(head);
        }
        return head;
    }

    private static Element bodyOf(Document document) {
        Element body = document.body();
        if (body == null) {
            body = new Element("body");
            htmlOf(document).appendChild(body);
        }
        return body;
    }

    private static Element htmlOf(Document document) {
        Element html = document.firstElementChild();
        return html != null && html.normalName().equals("html") ? html : document.appendElement("html");
    }
}
