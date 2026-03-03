package io.github.carlos_emr.carlos.documentManager;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.xhtmlrenderer.layout.SharedContext;
import org.xhtmlrenderer.pdf.ITextRenderer;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Converts HTML to PDF using Flying Saucer (xhtmlrenderer) with OpenPDF.
 *
 * <p>Flying Saucer requires well-formed XHTML input. This converter uses Jsoup
 * to parse and clean arbitrary HTML into strict XHTML before rendering to PDF.
 * It handles common HTML issues such as unclosed tags, missing attributes, and
 * script elements that Flying Saucer cannot process.</p>
 *
 * <p>This is a pure-Java PDF renderer with no native library dependencies,
 * replacing the previous wkhtmltopdf JNI wrapper (io.woo.htmltopdf).</p>
 *
 * @since 2026-03-03
 */
public class InternalEDocConverter implements EDocConverterInterface {

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Converts an HTML string to PDF and writes it to the given output stream.
     *
     * <p>The conversion pipeline:</p>
     * <ol>
     *   <li>Parse HTML with Jsoup (fixes malformed markup)</li>
     *   <li>Convert to strict XHTML output (self-closing tags, escaped entities)</li>
     *   <li>Remove script elements (not supported by Flying Saucer)</li>
     *   <li>Ensure XHTML-required attributes on img and input elements</li>
     *   <li>Render to PDF via Flying Saucer ITextRenderer</li>
     * </ol>
     *
     * @param document the HTML string to convert to PDF
     * @param os       the OutputStream where the generated PDF content will be written
     * @throws IOException if PDF rendering fails
     */
    @Override
    public void convert(String document, OutputStream os) throws IOException {
        Document doc = prepareXhtml(document);

        ITextRenderer renderer = new ITextRenderer();
        SharedContext sharedContext = renderer.getSharedContext();
        sharedContext.setPrint(true);
        sharedContext.setInteractive(false);
        sharedContext.setReplacedElementFactory(new ReplacedElementFactoryImpl());
        sharedContext.getTextRenderer().setSmoothingThreshold(0);

        renderer.setDocumentFromString(doc.outerHtml(), null);
        renderer.layout();
        try {
            renderer.createPDF(os, true);
        } catch (com.lowagie.text.DocumentException e) {
            logger.error("Flying Saucer PDF rendering failed", e);
            throw new IOException("PDF rendering failed: " + e.getMessage(), e);
        }
    }

    /**
     * Prepares an HTML string for Flying Saucer by converting it to strict XHTML.
     *
     * @param html the raw HTML string
     * @return a Jsoup Document configured for XHTML output
     */
    private static Document prepareXhtml(String html) {
        Document doc = Jsoup.parse(html);

        doc.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(Entities.EscapeMode.xhtml)
            .charset("UTF-8")
            .prettyPrint(false);

        // Flying Saucer cannot execute scripts
        doc.select("script").remove();

        // XHTML requires alt on img
        doc.select("img:not([alt])").attr("alt", "");

        // XHTML requires type on input
        doc.select("input:not([type])").attr("type", "text");

        return doc;
    }
}
