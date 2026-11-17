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
package io.github.carlos_emr.carlos.form.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRTextElement;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.fonts.FontFamily;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.pdf.JRPdfExporter;
import net.sf.jasperreports.pdf.SimplePdfExporterConfiguration;

/**
 * Prints the four fixed Rourke 2017 pages ({@code /oscar/form/rourke2017/}) with the same font
 * rules as {@link Rourke2020PdfReport}: every text prints in the bundled DejaVu family, and a
 * character DejaVu cannot draw or text a field would clip refuses the whole PDF
 * ({@link Rourke2020PdfReport.RenderingException}, naming Rourke 2017) instead of printing an
 * incomplete chart. The templates name no font, so they take the bundled default.
 *
 * <p>The record values are the form's own properties ({@code FormRourke2017.toProperties()} with
 * the chart's demographic values), exactly as the clinician's print fills them. Only compiled
 * templates, which hold no patient data, are cached.
 *
 * @since 2026-11-13
 */
public final class Rourke2017PdfReport {
    private static final String RESOURCE_PATH = "/oscar/form/rourke2017/";
    private static final String FORM = "Rourke 2017";
    private static final Map<Integer, JasperReport> TEMPLATES = new HashMap<>();

    private Rourke2017PdfReport() { }

    /**
     * Writes the selected pages, in order.
     *
     * @param output receives the PDF only after every page filled and validated
     * @param recordData the form's properties as the clinician's print fills them
     * @param pagesToPrint page numbers 1 to 4
     * @throws JRException when a template cannot be loaded or the PDF cannot be written
     * @throws Rourke2020PdfReport.RenderingException when a value cannot be printed completely
     */
    public static void write(OutputStream output, Properties recordData, List<Integer> pagesToPrint)
            throws JRException {
        FontFamily family = Rourke2020PdfReport.fontFamily(FORM);
        Map<String, Object> parameters = new HashMap<>();
        recordData.forEach((key, value) -> parameters.put((String) key, value));
        parameters.put("check_image", resourceUrl("check.png"));
        parameters.put("cross_image", resourceUrl("cross.png"));
        List<JasperPrint> pages = new ArrayList<>();
        for (Integer page : pagesToPrint) {
            JasperReport template = template(page);
            // The 2017 templates take the background as a location, as the clinician's print passes it.
            parameters.put("background_image", resourceUrl("page" + page + ".png"));
            pages.add(Rourke2020PdfReport.fill(template, parameters, family, FORM));
        }
        JRPdfExporter exporter = new JRPdfExporter(Rourke2020PdfReport.context());
        SimplePdfExporterConfiguration configuration = new SimplePdfExporterConfiguration();
        configuration.setCompressed(true);
        exporter.setConfiguration(configuration);
        exporter.setExporterInput(SimpleExporterInput.getInstance(pages));
        // An exporter failure must not commit a background-only or partial clinical PDF.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(bytes));
        exporter.exportReport();
        try {
            bytes.writeTo(output);
        } catch (IOException e) {
            throw new JRException("Could not write Rourke 2017 PDF", e);
        }
    }

    private static synchronized JasperReport template(Integer page) throws JRException {
        if (page == null || page < 1 || page > 4) {
            throw new IllegalArgumentException("Invalid Rourke 2017 page");
        }
        JasperReport cached = TEMPLATES.get(page);
        if (cached == null) {
            try (InputStream input = Rourke2017PdfReport.class.getResourceAsStream(RESOURCE_PATH + "page" + page + ".jrxml")) {
                if (input == null) throw new JRException("Missing Rourke 2017 template");
                cached = JasperCompileManager.getInstance(Rourke2020PdfReport.context()).compile(input);
            } catch (IOException e) {
                throw new JRException("Could not load Rourke 2017 template", e);
            }
            // Preserve the original text and cut index so validation can detect silent clipping.
            cached.getPropertiesMap().setProperty(JRTextElement.PROPERTY_PRINT_KEEP_FULL_TEXT, "true");
            TEMPLATES.put(page, cached);
        }
        return cached;
    }

    private static String resourceUrl(String name) throws JRException {
        URL url = Rourke2017PdfReport.class.getResource(RESOURCE_PATH + name);
        if (url == null) throw new JRException("Missing Rourke 2017 resource");
        return url.toString();
    }
}
