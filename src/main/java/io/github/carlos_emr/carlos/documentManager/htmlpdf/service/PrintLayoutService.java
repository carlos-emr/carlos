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
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontResolver;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.LocalOnlyUserAgent;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.PrintStylesheet;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.openpdf.text.Document;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfImportedPage;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStamper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.extend.FontResolver;
import org.xhtmlrenderer.layout.SharedContext;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Lays an XHTML document out with Flying Saucer and places the result on US Letter with 10 mm
 * margins, so the output matches the pages wkhtmltopdf made (spec §Output geometry).
 *
 * <ol>
 *   <li><b>Pass 1</b> renders on the page the injected style sheet declares ({@link
 *       PrintStylesheet#PASS_1_PAGE_RULE}: Letter and margins divided by the default zoom of 0.8),
 *       with scale-to-fit, so content wider than the page widens the page instead of being clipped.
 *       Scale-to-fit widens every page to the same width, which is {@code W}.</li>
 *   <li>If {@code W} is within {@link PageZoomCalculator#PASS_1_MAX_WIDTH_PT}, pass 1 is used at zoom
 *       0.8. Otherwise <b>pass 2</b> replaces the {@code @page} rule with one sized from {@code W}
 *       ({@link PageZoomCalculator}) and renders again without scale-to-fit. If pass 2 fails, pass 1
 *       is used at {@code 612 / W}, whose margins come out smaller than 10 mm
 *       ({@code MARGINS_REDUCED}): the complete content, on a tighter page, rather than no document.</li>
 *   <li><b>Placement</b> flattens any form fields (the pipeline leaves none; this is defensive), then
 *       imports each page onto a 612 x 792 pt page scaled by the zoom, anchored at the top left, and
 *       copies the document title. Link annotations, bookmarks and tagged structure do not survive
 *       the import; no consumer of these PDFs (fax, print, email attachment, document store) uses them.</li>
 * </ol>
 *
 * <p>A failure of pass 1 or of the placement is reported as an {@link HtmlPdfRenderException} for
 * the caller's ladder; one of pass 2 is absorbed as above. A zoom below
 * {@link PageZoomCalculator#LOW_ZOOM_WARNING} is logged at WARN with the zoom only. Renderers are
 * built per pass; the service keeps no state.</p>
 *
 * @since 2026-10-29
 */
@Service
class PrintLayoutService {

    private static final Logger logger = MiscUtils.getLogger();

    /** Builds a restricted renderer from the allowed directories and the font resolver; replaced in tests. */
    private final BiFunction<List<Path>, FontResolver, ITextRenderer> rendererFactory;

    /** Production wiring: the restricted renderer of the module. */
    @Autowired
    PrintLayoutService() {
        this(LocalOnlyUserAgent::createRestrictedRenderer);
    }

    /**
     * For tests, which inject a renderer that fails on a chosen pass.
     *
     * @param rendererFactory builds the renderer for one pass
     */
    PrintLayoutService(BiFunction<List<Path>, FontResolver, ITextRenderer> rendererFactory) {
        this.rendererFactory = Objects.requireNonNull(rendererFactory, "rendererFactory");
    }

    /**
     * Lays the document out and places it on US Letter.
     *
     * @param mode the rung of the ladder, for the exception if this fails
     * @param xhtml the document, with the injected style sheet in its head
     * @param baseDirectory the directory relative URLs in the document resolve against
     * @param allowedDirectories the directories the renderer may read from
     * @param fonts the bundled fonts every pass prints with
     * @param degradations the attempt's degradations; {@code MARGINS_REDUCED} is added if pass 2 fails
     * @return a PDF whose every page is 612 x 792 pt
     * @throws HtmlPdfRenderException with phase {@code LAYOUT_PASS_1} or {@code PLACE} if the
     *         document cannot be laid out or placed
     */
    byte[] render(PrintMode mode, String xhtml, Path baseDirectory, List<Path> allowedDirectories, BundledFontSet fonts,
            Set<HtmlPdfRenderDegradation> degradations) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(xhtml, "xhtml");
        Objects.requireNonNull(baseDirectory, "baseDirectory");
        Objects.requireNonNull(allowedDirectories, "allowedDirectories");
        Objects.requireNonNull(fonts, "fonts");
        Objects.requireNonNull(degradations, "degradations");

        byte[] pass1 = layout(mode, HtmlPdfRenderException.Phase.LAYOUT_PASS_1, xhtml, baseDirectory, allowedDirectories,
                fonts, true);
        float width = widestPage(mode, pass1);
        byte[] chosen = pass1;
        float zoom = PageZoomCalculator.DEFAULT_ZOOM;
        if (width > PageZoomCalculator.PASS_1_MAX_WIDTH_PT) {
            PageGeometry geometry = PageZoomCalculator.pass2Geometry(width);
            try {
                chosen = layout(mode, HtmlPdfRenderException.Phase.LAYOUT_PASS_2, pass2Document(xhtml, geometry),
                        baseDirectory, allowedDirectories, fonts, false);
                zoom = geometry.zoom();
            } catch (HtmlPdfRenderException | IllegalStateException e) {
                // The message of an HtmlPdfRenderException names only mode, phase and class; ours is fixed text.
                logger.warn("Layout pass 2 failed ({}); using pass 1 on a wider page", e.getMessage());
                degradations.add(HtmlPdfRenderDegradation.MARGINS_REDUCED);
                chosen = pass1;
                zoom = PageZoomCalculator.LETTER_WIDTH_PT / width;
            }
        }
        if (zoom < PageZoomCalculator.LOW_ZOOM_WARNING) {
            logger.warn("A document is printed at a low zoom of {}", String.format(Locale.ROOT, "%.3f", zoom));
        }
        return place(mode, chosen, zoom);
    }

    // ---- layout -----------------------------------------------------------------------------

    private byte[] layout(PrintMode mode, HtmlPdfRenderException.Phase phase, String xhtml, Path baseDirectory,
            List<Path> allowedDirectories, BundledFontSet fonts, boolean scaleToFit) {
        try {
            ITextRenderer renderer = rendererFactory.apply(allowedDirectories, new BundledFontResolver(fonts));
            SharedContext context = renderer.getSharedContext();
            context.setPrint(true);
            context.setInteractive(false);
            context.getTextRenderer().setSmoothingThreshold(0);
            renderer.setScaleToFit(scaleToFit);
            renderer.setDocumentFromString(xhtml, baseUrl(baseDirectory));
            renderer.layout();
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            renderer.createPDF(out, true);
            return out.toByteArray();
        } catch (Exception | LinkageError e) {
            throw new HtmlPdfRenderException(mode, phase, e);
        }
    }

    /** The base directory as a URL that ends in a slash, so relative references resolve inside it. */
    private static String baseUrl(Path baseDirectory) {
        String url = baseDirectory.toUri().toString();
        return url.endsWith("/") ? url : url + "/";
    }

    /** The document with the pass-1 {@code @page} rule replaced by the pass-2 one. */
    private static String pass2Document(String xhtml, PageGeometry geometry) {
        if (!xhtml.contains(PrintStylesheet.PASS_1_PAGE_RULE)) {
            throw new IllegalStateException("The pass-1 page rule is missing from the document");
        }
        return xhtml.replace(PrintStylesheet.PASS_1_PAGE_RULE,
                PrintStylesheet.pageRule(geometry.widthPt(), geometry.heightPt(), geometry.marginPt()));
    }

    private static float widestPage(PrintMode mode, byte[] pdf) {
        try {
            PdfReader reader = new PdfReader(pdf);
            try {
                float widest = 0f;
                for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                    widest = Math.max(widest, reader.getPageSize(page).getWidth());
                }
                return widest > 0f ? widest : PageZoomCalculator.PASS_1_MAX_WIDTH_PT;
            } finally {
                reader.close();
            }
        } catch (Exception | LinkageError e) {
            throw new HtmlPdfRenderException(mode, HtmlPdfRenderException.Phase.LAYOUT_PASS_1, e);
        }
    }

    // ---- placement --------------------------------------------------------------------------

    /** Scales every page by the zoom and puts it at the top left of a 612 x 792 pt page. */
    private static byte[] place(PrintMode mode, byte[] source, float zoom) {
        try {
            PdfReader reader = flattened(new PdfReader(source));
            try {
                String title = reader.getInfo().get("Title");
                Document placed = new Document(new Rectangle(PageZoomCalculator.LETTER_WIDTH_PT,
                        PageZoomCalculator.LETTER_HEIGHT_PT), 0, 0, 0, 0);
                ByteArrayOutputStream out = new ByteArrayOutputStream(source.length + 4096);
                PdfWriter writer = PdfWriter.getInstance(placed, out);
                if (title != null) {
                    placed.addTitle(title);
                }
                placed.open();
                PdfContentByte canvas = writer.getDirectContent();
                for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                    if (page > 1) {
                        placed.newPage();
                    }
                    PdfImportedPage imported = writer.getImportedPage(reader, page);
                    float height = reader.getPageSize(page).getHeight();
                    canvas.addTemplate(imported, zoom, 0, 0, zoom, 0, PageZoomCalculator.LETTER_HEIGHT_PT - height * zoom);
                }
                placed.close();
                return out.toByteArray();
            } finally {
                reader.close();
            }
        } catch (Exception | LinkageError e) {
            throw new HtmlPdfRenderException(mode, HtmlPdfRenderException.Phase.PLACE, e);
        }
    }

    /** The reader itself, or a reader of a copy with every form field flattened into page content. */
    private static PdfReader flattened(PdfReader reader) throws Exception {
        if (reader.getAcroForm() == null) {
            return reader;
        }
        ByteArrayOutputStream flat = new ByteArrayOutputStream();
        PdfStamper stamper = new PdfStamper(reader, flat);
        stamper.setFormFlattening(true);
        stamper.close();
        reader.close();
        return new PdfReader(flat.toByteArray());
    }
}
