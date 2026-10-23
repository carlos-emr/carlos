/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.printing;

import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRStaticText;
import net.sf.jasperreports.engine.JRTextElement;
import net.sf.jasperreports.engine.JRTextField;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.fonts.FontFace;
import net.sf.jasperreports.engine.fonts.FontFamily;
import net.sf.jasperreports.engine.fonts.FontInfo;
import net.sf.jasperreports.engine.fonts.FontUtil;
import net.sf.jasperreports.engine.type.LineSpacingEnum;
import net.sf.jasperreports.engine.type.RotationEnum;
import net.sf.jasperreports.engine.type.TextAdjustEnum;
import net.sf.jasperreports.engine.util.JRElementsVisitor;
import net.sf.jasperreports.engine.util.JRVisitorSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the CARLOS report font policy (issue #5046) for every Jasper template the application
 * ships, so a new or edited template cannot reintroduce host-dependent output.
 *
 * <p>Before the policy, 40 of the 53 shipped templates named no font and measured text with the
 * host's "SansSerif" face. A field whose box was shorter than that face's line height printed
 * nothing, and the default PDF font (non-embedded Helvetica, Cp1252) dropped characters outside
 * Western European Latin. These checks compile each template through the real default
 * JasperReports context, so they see exactly what {@code jasperreports.properties} and
 * {@code fonts/carlos-jasper-fonts.xml} resolve each element to.
 */
@Tag("unit")
@Tag("report")
class JasperTemplateFontPolicyUnitTest {
    /** Bundled classpath faces: the DejaVu extension jar and CARLOS's Liberation Mono copies. */
    private static final List<String> BUNDLED_FACE_PREFIXES =
            List.of("net/sf/jasperreports/fonts/dejavu/", "fonts/liberation/");
    private static final FontRenderContext MEASURING = new FontRenderContext(null, true, true);
    // Templates kept as copies beside their Java service as well as on the classpath.
    private static final List<Path> TEMPLATE_ROOTS = List.of(Path.of("src/main/resources"), Path.of("src/main/java"));

    private static List<Template> templates;

    private record Template(Path path, JasperReport report) { }

    private record TextBox(Path template, JRTextElement element, String text) {
        @Override
        public String toString() {
            return template + " [" + element.getX() + "," + element.getY() + " " + element.getWidth() + "x"
                    + element.getHeight() + "] " + text.strip().replaceAll("\\s+", " ");
        }
    }

    @BeforeAll
    static void compileShippedTemplates() throws IOException, JRException {
        templates = new ArrayList<>();
        for (Path root : TEMPLATE_ROOTS) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(JasperTemplateFontPolicyUnitTest::isJasperTemplate).sorted().toList()) {
                    try (InputStream input = Files.newInputStream(file)) {
                        templates.add(new Template(file, JasperCompileManager.compileReport(input)));
                    }
                }
            }
        }
    }

    private static boolean isJasperTemplate(Path file) {
        String name = file.getFileName().toString();
        if (!name.endsWith(".jrxml") && !name.endsWith(".xml")) {
            return false;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8).contains("<jasperReport");
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
    }

    private static List<TextBox> textBoxes() {
        List<TextBox> boxes = new ArrayList<>();
        for (Template template : templates) {
            JRElementsVisitor.visitReport(template.report(), new JRVisitorSupport() {
                @Override
                public void visitStaticText(JRStaticText text) {
                    if (text.getText() != null && !text.getText().isBlank()) {
                        boxes.add(new TextBox(template.path(), text, text.getText()));
                    }
                }

                @Override
                public void visitTextField(JRTextField field) {
                    if (field.getExpression() != null && !field.getExpression().getText().isBlank()) {
                        boxes.add(new TextBox(template.path(), field, field.getExpression().getText()));
                    }
                }
            });
        }
        return boxes;
    }

    private static FontFace face(JRTextElement element) {
        FontInfo info = FontUtil.getInstance(DefaultJasperReportsContext.getInstance())
                .getFontInfo(element.getFontName(), Locale.ROOT);
        if (info == null) {
            return null;
        }
        FontFamily family = info.getFontFamily();
        if (element.isBold()) {
            return element.isItalic() ? family.getBoldItalicFace() : family.getBoldFace();
        }
        return element.isItalic() ? family.getItalicFace() : family.getNormalFace();
    }

    @Test
    @DisplayName("should find every shipped template, so the policy checks below are never vacuous")
    void shouldDiscoverShippedTemplates_forTheWholeApplication() {
        assertThat(templates).hasSizeGreaterThanOrEqualTo(50);
        assertThat(templates).extracting(t -> t.path().getFileName().toString())
                .contains("end_year_statement_report.jrxml", "page1.jrxml", "BCAR2020_pg1.jrxml",
                        "label.xml", "reflabel.xml", "pdf_rep_wo.jrxml", "billDaySheet.xml");
        assertThat(textBoxes()).as("text elements across the shipped templates").hasSizeGreaterThan(1500);
    }

    @Test
    @DisplayName("should print every template's text in a bundled face embedded as Identity-H")
    void shouldResolveTextToBundledEmbeddedFonts_forEveryShippedTemplate() {
        List<String> violations = new ArrayList<>();
        for (TextBox box : textBoxes()) {
            String font = box.element().getFontName();
            FontInfo info = FontUtil.getInstance(DefaultJasperReportsContext.getInstance()).getFontInfo(font, Locale.ROOT);
            FontFace face = face(box.element());
            if (info == null || face == null || face.getTtf() == null
                    || BUNDLED_FACE_PREFIXES.stream().noneMatch(face.getTtf()::startsWith)) {
                violations.add("font '" + font + "' is not a bundled face: " + box);
            } else if (!"Identity-H".equals(info.getFontFamily().getPdfEncoding())
                    || !Boolean.TRUE.equals(info.getFontFamily().isPdfEmbedded())) {
                violations.add("font '" + font + "' is not embedded as Identity-H: " + box);
            }
        }
        assertThat(violations).as("text that would use a host font or a non-embedded PDF font").isEmpty();
    }

    @Test
    @DisplayName("should fit at least one line of the bundled face in every fixed-height text box")
    void shouldFitOneLineOfBundledFont_inEveryFixedHeightTextBox() {
        List<String> violations = new ArrayList<>();
        for (TextBox box : textBoxes()) {
            JRTextElement element = box.element();
            if (element instanceof JRTextField field && field.getTextAdjust() != TextAdjustEnum.CUT_TEXT) {
                // Stretching boxes grow to their text; scaled boxes shrink the font to fit.
                continue;
            }
            FontFace face = face(element);
            if (face == null || face.getFont() == null) {
                continue; // reported by the font-resolution check
            }
            LineMetrics metrics = face.getFont().deriveFont(element.getFontSize()).getLineMetrics("Hg", MEASURING);
            float line = lineHeight(element, metrics.getAscent() + metrics.getDescent() + metrics.getLeading());
            boolean rotated = element.getRotation() == RotationEnum.LEFT || element.getRotation() == RotationEnum.RIGHT;
            float available = (rotated ? element.getWidth() : element.getHeight())
                    - padding(element.getLineBox().getTopPadding()) - padding(element.getLineBox().getBottomPadding());
            if (line > available + 0.01f) {
                violations.add(String.format(Locale.ROOT, "%.2fpt line in %.2fpt: %s", line, available, box));
            }
        }
        // Jasper prints nothing at all, without an error, when not even one line fits.
        assertThat(violations).as("text boxes too short for one line of their bundled face").isEmpty();
    }

    private static float lineHeight(JRTextElement element, float natural) {
        LineSpacingEnum spacing = element.getParagraph().getLineSpacing();
        float size = element.getParagraph().getLineSpacingSize() == null ? 1f : element.getParagraph().getLineSpacingSize();
        if (spacing == null) {
            return natural;
        }
        return switch (spacing) {
            case SINGLE -> natural;
            case ONE_AND_HALF -> natural * 1.5f;
            case DOUBLE -> natural * 2f;
            case PROPORTIONAL -> natural * size;
            case AT_LEAST -> Math.max(natural, size);
            case FIXED -> size;
        };
    }

    private static int padding(Integer value) {
        return value == null ? 0 : value;
    }
}
