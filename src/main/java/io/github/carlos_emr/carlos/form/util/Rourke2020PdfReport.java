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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.pdf.JRPdfExporter;
import net.sf.jasperreports.pdf.SimplePdfExporterConfiguration;
import net.sf.jasperreports.renderers.Renderable;
import net.sf.jasperreports.renderers.SimpleDataRenderer;

/** Prints the fixed Rourke 2020 templates, caching only resources without patient data. */
public final class Rourke2020PdfReport {
    private static final String RESOURCE_PATH = "/oscar/form/rourke2020/";
    private static final List<String> NOTE_FIELDS = List.of("p2_development2m", "p2_development4m",
            "p2_development6m", "p2_physical2m", "p2_physical4m", "p2_physical6m", "p2_immunization6m");
    private static final Map<Integer, Template> TEMPLATES = new HashMap<>();
    private static JasperReport notesReport;

    private record Template(JasperReport report, Renderable background) { }

    private Rourke2020PdfReport() { }

    /** Writes selected pages, followed by expandable page II notes when page II is selected. */
    public static void write(OutputStream output, Properties recordData, List<Integer> pagesToPrint)
            throws JRException {
        Map<String, Object> parameters = new HashMap<>();
        recordData.forEach((key, value) -> parameters.put((String) key, value));
        parameters.put("check_image", Rourke2020PdfReport.class.getResource(RESOURCE_PATH + "check.png").toString());
        parameters.put("cross_image", Rourke2020PdfReport.class.getResource(RESOURCE_PATH + "cross.png").toString());
        List<JasperPrint> pages = new ArrayList<>();
        for (Integer page : pagesToPrint) {
            Template template = template(page);
            parameters.put("background_image", template.background());
            pages.add(JasperFillManager.fillReport(template.report(), parameters, new JREmptyDataSource()));
        }
        if (pagesToPrint.contains(2) && NOTE_FIELDS.stream()
                .anyMatch(field -> !recordData.getProperty(field, "").isBlank())) {
            pages.add(JasperFillManager.fillReport(notesReport(), parameters, new JREmptyDataSource()));
        }
        JRPdfExporter exporter = new JRPdfExporter();
        SimplePdfExporterConfiguration configuration = new SimplePdfExporterConfiguration();
        configuration.setCompressed(true);
        exporter.setConfiguration(configuration);
        exporter.setExporterInput(SimpleExporterInput.getInstance(pages));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(output));
        exporter.exportReport();
    }

    private static synchronized Template template(Integer page) throws JRException {
        if (page == null || page < 1 || page > 4) {
            throw new IllegalArgumentException("Invalid Rourke 2020 page");
        }
        Template cached = TEMPLATES.get(page);
        if (cached == null) {
            cached = new Template(compile("page" + page + ".jrxml"), background(page));
            TEMPLATES.put(page, cached);
        }
        return cached;
    }

    private static synchronized JasperReport notesReport() throws JRException {
        if (notesReport == null) {
            notesReport = compile("page2-notes.jrxml");
        }
        return notesReport;
    }

    private static JasperReport compile(String name) throws JRException {
        try (InputStream input = resource(name)) {
            return JasperCompileManager.compileReport(input);
        } catch (IOException e) {
            throw new JRException("Could not load Rourke 2020 template", e);
        }
    }

    private static InputStream resource(String name) throws IOException {
        InputStream input = Rourke2020PdfReport.class.getResourceAsStream(RESOURCE_PATH + name);
        if (input == null) {
            throw new IOException("Missing Rourke 2020 resource");
        }
        return input;
    }

    private static Renderable background(int page) throws JRException {
        // Keep the original 300 dpi source and geometry. JPEG encodes only the static
        // scanned background; patient text and marks remain separate PDF elements.
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (InputStream input = resource("page" + page + ".png");
             ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream imageOutput = new MemoryCacheImageOutputStream(bytes)) {
            BufferedImage image = ImageIO.read(input);
            ImageWriteParam options = writer.getDefaultWriteParam();
            options.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            options.setCompressionQuality(0.90f);
            writer.setOutput(imageOutput);
            writer.write(null, new IIOImage(image, null, null), options);
            imageOutput.flush();
            return SimpleDataRenderer.getInstance(bytes.toByteArray());
        } catch (IOException e) {
            throw new JRException("Could not load Rourke 2020 background", e);
        } finally {
            writer.dispose();
        }
    }
}
