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

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The style sheet the pipeline injects as the first child of {@code <head>} (step k), which makes
 * Flying Saucer's output match wkhtmltopdf's for the documents CARLOS prints.
 *
 * <p>Placed first, its rules lose to the author's rules of equal specificity, so an author class
 * still wins over a default. The {@code @page} rule is {@code !important}, so an author's own page
 * size (A4 landscape, say) loses: every document is placed on US Letter. Everything it does, and
 * why:</p>
 *
 * <ul>
 *   <li>{@code @page}: pass 1 of the layout is rendered on a page of Letter and 10 mm margins divided
 *       by the default zoom of 0.8, so the later scaling by 0.8 gives Letter with 10 mm margins (see
 *       {@code PageZoomCalculator}); {@link #pageRule(double, double, double)} writes the pass-2 rule;</li>
 *   <li>{@code line-height: 1.164}, WebKit's {@code normal} for DejaVu Sans, which Flying Saucer
 *       would otherwise compute differently;</li>
 *   <li>no top margin at the start of the page, as WebKit drops it;</li>
 *   <li>{@code word-wrap: break-word} on {@code .carlos-pdf-wrap}, the class step n gives to a block
 *       that holds a word of 40 characters or more: one 600-character URL or value otherwise forces
 *       the whole document to be shrunk to fit (zoom 0.145 instead of 0.8). Flying Saucer 10.7.0
 *       ignores {@code overflow-wrap} but supports {@code word-wrap}. The property is not set on the
 *       whole body: Flying Saucer then takes the minimum width of every table cell to be one
 *       character, and the label columns of an ordinary form (the Annual form's lifestyle block)
 *       collapse into vertical strips of letters. The property is inherited, so a table nested in the
 *       marked block gets {@code word-wrap: normal} ({@code .carlos-pdf-wrap table}): a long signature
 *       line or URL beside a nested table would otherwise break its words ("Smokin" / "g"). A cell
 *       of that table that holds a long word is marked itself and breaks it;</li>
 *   <li>{@code -fs-table-paginate} for the tables step g marks, which repeats their headers;</li>
 *   <li>the printed form controls ({@code carlos-pdf-control}, see {@code PrintFormControls}):
 *       bordered boxes that grow to fit their content, keep their line breaks and wrap long values;
 *       the bars of a progress (10em wide) and a meter (5em), the size browsers give them, drawn as a grey
 *       track whose fill is blue for a progress and green, amber or red for a meter by how good its value
 *       is;</li>
 *   <li>the placeholder box for an image that cannot be shown and the document note.</li>
 * </ul>
 *
 * @since 2026-10-29
 */
public final class PrintStylesheet {

    /**
     * The {@code @page} rule of layout pass 1: Letter (612 x 792 pt) and 10 mm margins divided by the
     * default zoom of 0.8, that is 765 x 990 pt with 35.4331 pt margins.
     */
    public static final String PASS_1_PAGE_RULE = "@page { size: 765pt 990pt !important; margin: 35.4331pt !important }";

    private static final String CSS = PASS_1_PAGE_RULE + """
             /* pass 1; see geometry */
            html { line-height: 1.164 }                     /* WebKit "normal" for DejaVu Sans */
            html, body { margin-top: 0 !important }          /* WebKit drops the top margin at the page start */
            body > *:first-child { margin-top: 0 !important }
            .carlos-pdf-wrap { word-wrap: break-word }       /* a block with a very long word (step n): wrap, not shrink */
            .carlos-pdf-wrap table { word-wrap: normal }     /* word-wrap is inherited: a table inside would break its words */
            table.carlos-pdf-paginate { -fs-table-paginate: paginate }  /* repeat thead (step g) */
            .carlos-pdf-control { display: inline-block; vertical-align: baseline; font-size: 10pt;
              text-align: left; text-indent: 0; text-transform: none; letter-spacing: normal;
              word-spacing: normal }
            .carlos-pdf-text, .carlos-pdf-password, .carlos-pdf-select { border: 1px solid #000;
              padding: 1px 2px; min-height: 1.2em; white-space: pre-wrap; word-wrap: break-word }
            .carlos-pdf-textarea { border: 1px solid #000; padding: 1px 2px; white-space: pre-wrap;
              word-wrap: break-word }
            .carlos-pdf-checkbox, .carlos-pdf-radio { font-size: 13px }
            .carlos-pdf-progress, .carlos-pdf-meter { height: 1em; border: 1px solid #777;
              background-color: #e8e8e8; vertical-align: -0.2em }
            .carlos-pdf-progress { width: 10em }
            .carlos-pdf-meter { width: 5em }
            .carlos-pdf-gauge-fill { display: block; height: 100%; background-color: #2f6fbf }
            .carlos-pdf-gauge-optimum { background-color: #3f9d3f }
            .carlos-pdf-gauge-suboptimal { background-color: #e3b100 }
            .carlos-pdf-gauge-poor { background-color: #c53030 }
            .carlos-pdf-image-unavailable { display: inline-block; border: 1px dashed #000;
              padding: 2px; font-size: 8pt; text-align: left; white-space: nowrap; overflow: hidden }
            .carlos-pdf-note { border: 1px solid #000; padding: 4px; margin: 0 0 6px 0; font-size: 8pt }
            """;

    private PrintStylesheet() {
    }

    /**
     * The injected style sheet, ready to be the text of a {@code <style>} element.
     *
     * @return the CSS, beginning with {@link #PASS_1_PAGE_RULE}
     */
    public static String css() {
        return CSS;
    }

    /**
     * The {@code @page} rule of layout pass 2, which sets the page to the width the content needs.
     * Numbers are written with a decimal point whatever the server's locale, to four decimals at
     * most, without trailing zeros.
     *
     * @param widthPt the page width in points
     * @param heightPt the page height in points
     * @param marginPt the margin on every side in points
     * @return for example {@code @page { size: 918.5pt 1188.75pt !important; margin: 33.6pt !important }}
     */
    public static String pageRule(double widthPt, double heightPt, double marginPt) {
        return "@page { size: " + points(widthPt) + " " + points(heightPt) + " !important; margin: "
                + points(marginPt) + " !important }";
    }

    private static String points(double value) {
        return new BigDecimal(Double.toString(value)).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString() + "pt";
    }
}
