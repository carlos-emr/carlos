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

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JRTextElement;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.SimpleJasperReportsContext;
import net.sf.jasperreports.engine.fonts.FontFace;
import net.sf.jasperreports.engine.fonts.FontFamily;
import net.sf.jasperreports.engine.fonts.FontInfo;
import net.sf.jasperreports.engine.fonts.FontUtil;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.pdf.JRPdfExporter;
import net.sf.jasperreports.pdf.SimplePdfExporterConfiguration;
import net.sf.jasperreports.renderers.Renderable;
import net.sf.jasperreports.renderers.SimpleDataRenderer;

/** Prints the fixed Rourke 2020 templates, caching only resources without patient data. */
public final class Rourke2020PdfReport {
    private static final String RESOURCE_PATH = "/oscar/form/rourke2020/";
    private static final String FONT_NAME = "DejaVu Sans";
    private static final JasperReportsContext REPORT_CONTEXT = reportContext();
    private static final List<String> NOTE_FIELDS = List.of("p2_development2m", "p2_development4m",
            "p2_development6m", "p2_physical2m", "p2_physical4m", "p2_physical6m", "p2_immunization6m");
    private static final Map<Integer, Template> TEMPLATES = new HashMap<>();
    private static JasperReport notesReport;

    private record Template(JasperReport report, Renderable background) { }

    private Rourke2020PdfReport() { }

    /** Fixed messages are safe to display; never include record values in rendering errors. */
    public static final class RenderingException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public enum Reason { FONT_UNAVAILABLE, UNSUPPORTED_CHARACTER, TEXT_OVERFLOW }

        public RenderingException(Reason reason) {
            super(messageFor(reason));
        }

        // PMD 7.13 cannot parse a switch expression directly inside super(...).
        private static String messageFor(Reason reason) {
            return switch (reason) {
                case FONT_UNAVAILABLE -> "Rourke 2020 PDF font unavailable. Ask your administrator to restore "
                        + "the bundled DejaVu report fonts and retry.";
                case UNSUPPORTED_CHARACTER -> "Rourke 2020 PDF cannot display one or more entered characters with DejaVu. "
                        + "Ask your administrator for assistance; no PDF was generated.";
                case TEXT_OVERFLOW -> "Rourke 2020 PDF text does not fit a form field. "
                        + "Ask your administrator to review the report layout; no PDF was generated.";
            };
        }
    }

    private static JasperReportsContext reportContext() {
        SimpleJasperReportsContext context = new SimpleJasperReportsContext();
        context.setProperty("net.sf.jasperreports.awt.ignore.missing.font", "false");
        return context;
    }

    /** Writes selected pages, followed by expandable page II notes when page II is selected. */
    public static void write(OutputStream output, Properties recordData, List<Integer> pagesToPrint)
            throws JRException {
        FontFamily family = fontFamily();
        Map<String, Object> parameters = new HashMap<>();
        recordData.forEach((key, value) -> parameters.put((String) key, value));
        parameters.put("check_image", Rourke2020PdfReport.class.getResource(RESOURCE_PATH + "check.png").toString());
        parameters.put("cross_image", Rourke2020PdfReport.class.getResource(RESOURCE_PATH + "cross.png").toString());
        List<JasperPrint> pages = new ArrayList<>();
        for (Integer page : pagesToPrint) {
            Template template = template(page);
            parameters.put("background_image", template.background());
            pages.add(fill(template.report(), parameters, family));
        }
        if (pagesToPrint.contains(2) && NOTE_FIELDS.stream()
                .anyMatch(field -> !recordData.getProperty(field, "").isBlank())) {
            pages.add(fill(notesReport(), parameters, family));
        }
        JRPdfExporter exporter = new JRPdfExporter(REPORT_CONTEXT);
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
            throw new JRException("Could not write Rourke 2020 PDF", e);
        }
    }

    private static FontFamily fontFamily() {
        try {
            return loadFontFamily();
        } catch (RenderingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RenderingException(RenderingException.Reason.FONT_UNAVAILABLE);
        }
    }

    private static FontFamily loadFontFamily() {
        FontInfo info = FontUtil.getInstance(REPORT_CONTEXT).getFontInfo(FONT_NAME, Locale.ROOT);
        if (info == null) throw new RenderingException(RenderingException.Reason.FONT_UNAVAILABLE);
        FontFamily family = info.getFontFamily();
        if (!"Identity-H".equals(family.getPdfEncoding()) || !Boolean.TRUE.equals(family.isPdfEmbedded())) {
            throw new RenderingException(RenderingException.Reason.FONT_UNAVAILABLE);
        }
        FontFace[] faces = {family.getNormalFace(), family.getBoldFace(),
                family.getItalicFace(), family.getBoldItalicFace()};
        for (FontFace face : faces) {
            // Require the classpath extension even if the OS happens to install the same family.
            if (face == null || face.getTtf() == null || !face.getTtf().startsWith("net/sf/jasperreports/fonts/dejavu/")) {
                throw new RenderingException(RenderingException.Reason.FONT_UNAVAILABLE);
            }
            if (face.getFont() == null) throw new RenderingException(RenderingException.Reason.FONT_UNAVAILABLE);
        }
        return family;
    }

    private static JasperPrint fill(JasperReport report, Map<String, Object> parameters, FontFamily family)
            throws JRException {
        JasperPrint print = JasperFillManager.getInstance(REPORT_CONTEXT)
                .fill(report, parameters, new JREmptyDataSource());
        for (var page : print.getPages()) validateText(page.getElements(), family);
        return print;
    }

    private static void validateText(List<JRPrintElement> elements, FontFamily family) {
        for (JRPrintElement element : elements) {
            if (element instanceof JRPrintFrame frame) validateText(frame.getElements(), family);
            if (!(element instanceof JRPrintText text)) continue;
            String original = text.getOriginalText();
            if (original == null || original.isBlank()) continue;
            if (!FONT_NAME.equals(text.getFontName())) {
                throw new RenderingException(RenderingException.Reason.FONT_UNAVAILABLE);
            }
            Font font = text.isBold()
                    ? (text.isItalic() ? family.getBoldItalicFace() : family.getBoldFace()).getFont()
                    : (text.isItalic() ? family.getItalicFace() : family.getNormalFace()).getFont();
            if (original.codePoints().anyMatch(cp -> cp != '\n' && cp != '\r' && cp != '\t' && !font.canDisplay(cp))) {
                throw new RenderingException(RenderingException.Reason.UNSUPPORTED_CHARACTER);
            }
            Integer cut = text.getTextTruncateIndex();
            if (cut != null && !original.substring(cut).isBlank()) {
                throw new RenderingException(RenderingException.Reason.TEXT_OVERFLOW);
            }
        }
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
            JasperReport report = JasperCompileManager.getInstance(REPORT_CONTEXT).compile(input);
            // Preserve the original string and cut index so validation can detect silent clipping.
            // Stretching continuation fields retain Jasper's normal multi-page overflow behavior.
            report.getPropertiesMap().setProperty(JRTextElement.PROPERTY_PRINT_KEEP_FULL_TEXT, "true");
            return report;
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
