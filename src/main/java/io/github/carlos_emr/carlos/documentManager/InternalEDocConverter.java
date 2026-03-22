package io.github.carlos_emr.carlos.documentManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

import org.apache.commons.io.IOUtils;

import io.woo.htmltopdf.HtmlToPdf;
import io.woo.htmltopdf.HtmlToPdfObject;
import io.woo.htmltopdf.PdfPageSize;

/**
 * HTML-to-PDF converter implementation using the wkhtmltopdf library (via {@code io.woo.htmltopdf}).
 *
 * <p>This is the primary PDF converter for the CARLOS EMR document management system. It uses
 * a bundled native wkhtmltopdf shared library to convert HTML content to PDF with Letter-size
 * pages, print media type enabled, and JavaScript disabled for security.
 *
 * <p>Configuration is applied via a settings map including intelligent shrinking, minimum font
 * size, UTF-8 encoding, and page margins. Local file access is enabled to allow resolution of
 * resources referenced in the HTML.
 *
 * @see EDocConverterInterface
 * @see ConvertToEdoc
 * @since 2018-10-15
 */
public class InternalEDocConverter implements EDocConverterInterface {
    /**
     * Converts HTML to PDF using the internal io.woo.htmltopdf library.
     * Requires the required native .so file to be bundled (e.g.,
     * libwkhtmltox.ubuntu.noble.amd64.so).
     * This is now the only converter used; no configuration property is required.
     * 
     * @param document the complete HTML string to convert to PDF
     * @param os       the {@link ByteArrayOutputStream} where the generated PDF
     *                 content will be written
     * @throws Exception if the external process fails or PDF conversion is
     *                   unsuccessful
     */
    @Override
    public void convert(String document, OutputStream os) throws IOException {
        HashMap<String, String> htmlToPdfSettings = new HashMap<String, String>() {{
            put("load.blockLocalFileAccess", "false");
            put("web.enableIntelligentShrinking", "true");
            put("web.minimumFontSize", "10");
            // put("load.zoomFactor", "0.92");
            put("web.printMediaType", "true");
            put("web.defaultEncoding", "utf-8");
            put("T", "10mm");
            put("L", "8mm");
            put("R", "8mm");
            put("web.enableJavascript", "false");
        }};
        try (InputStream in = HtmlToPdf.create()
                .object(HtmlToPdfObject.forHtml(document, htmlToPdfSettings))
                .pageSize(PdfPageSize.Letter)
                .convert()) {
            IOUtils.copy(in, os);
        }
    }
}