/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form.pdfservlet;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openpdf.text.DocumentException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Native template/config/servlet rendering, without record loading or database access. */
@Isolated("Temporarily redirects the native PDF form resource directory")
@Tag("unit")
@Tag("fast")
@Tag("pdf")
class FrmPDFServletContinuationUnitTest extends CarlosUnitTestBase {
    private static final String PROSE = "FAKE-PW1234567890abcdef BP 120/80 & HR 72; pt's c/o SOB";
    private static final String LABEL = "Examination by - full print name (continued from above)";
    private static final String REFERENCE = "See full name below";
    @TempDir Path directory;
    private String previousDirectory;

    @BeforeEach
    void installNativeResources() throws Exception {
        previousDirectory = CarlosProperties.getInstance().getProperty("pdfFORMDIR");
        for (String name : List.of("mentalHealthForm14.pdf", "mentalHealthForm14Print.txt",
                "mentalHealthForm42.pdf", "mentalHealthForm42Print.txt")) {
            try (var resource = getClass().getResourceAsStream("/oscar/form/prop/" + name)) {
                assertThat(resource).as(name).isNotNull();
                Files.copy(resource, directory.resolve(name));
            }
        }
        CarlosProperties.getInstance().setProperty("pdfFORMDIR", directory.toString());
    }

    @AfterEach
    void restoreDirectory() {
        if (previousDirectory == null) CarlosProperties.getInstance().remove("pdfFORMDIR");
        else CarlosProperties.getInstance().setProperty("pdfFORMDIR", previousDirectory);
    }

    private MockHttpServletRequest request(String form, String field, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/form/createpdf");
        Provider provider = new Provider(); provider.setFirstName("Synthetic"); provider.setLastName("Provider");
        LoggedInInfo info = new LoggedInInfo(); info.setLoggedInProvider(provider);
        request.getSession().setAttribute(info.LOGGED_IN_INFO_KEY, info);
        request.setParameter("demographic_no", "123");
        // This disallowed synthetic class intentionally disables record loading. The real servlet
        // still overlays the supplied values through its native configuration and template.
        request.setParameter("form_class", "FAKE-NO-RECORD");
        request.setParameter("__cfgfile", "mentalHealthForm" + form + "Print");
        request.setParameter("__template", "mentalHealthForm" + form);
        request.setParameter(field, value);
        return request;
    }

    private byte[] render(String form, String field, String value) throws Exception {
        MockHttpServletRequest request = request(form, field, value);
        FrmPDFServlet servlet = new FrmPDFServlet();
        MockServletContext context = new MockServletContext(); servlet.init(new MockServletConfig(context));
        return servlet.generatePDFDocumentBytes(request, context, new ByteArrayOutputStream(), 0).toByteArray();
    }

    private static final class Text extends PDFTextStripper {
        final List<TextPosition> glyphs = new ArrayList<>();
        Text() throws IOException { }
        @Override protected void processTextPosition(TextPosition position) {
            glyphs.add(position); super.processTextPosition(position);
        }
    }

    private static String visibleText(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) { return new PDFTextStripper().getText(document); }
    }

    private static void assertComplete(String extracted, String value) {
        // Line wrapping may add whitespace; every submitted non-whitespace character must survive.
        assertThat(extracted.replaceAll("\\s+", "")).contains(value.replaceAll("\\s+", ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Dr Alice Smith", "Nguyễn Élodie", "王小明", "Αλέξανδρος Иванов", "陈小明 さくら 김민준 👍 🤒"})
    void fittingLegalNameStaysInOriginalPrintNameBox(String value) throws Exception {
        byte[] pdf = render("14", "physicianName", value);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            Text text = new Text(); String extracted = text.getText(document);
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertComplete(extracted, value);
            assertThat(extracted).doesNotContain(REFERENCE, LABEL);
            List<TextPosition> inline = text.glyphs.stream()
                    .filter(p -> p.getXDirAdj() >= 414 && p.getYDirAdj() >= 161 && p.getYDirAdj() <= 164).toList();
            assertThat(inline).isNotEmpty().allSatisfy(p -> {
                assertThat(p.getFontSizeInPt()).isEqualTo(10);
                assertThat(p.getXDirAdj() + p.getWidthDirAdj()).isLessThanOrEqualTo(590.1f);
            });
        }
    }

    @Test
    void completeObservedProseContinuesOnSamePageWithoutCoveringStatutoryText() throws Exception {
        byte[] pdf = render("14", "physicianName", PROSE);
        String extracted = visibleText(pdf);
        assertComplete(extracted, PROSE);
        assertThat(extracted).contains(REFERENCE, LABEL, "(print name)", "(signature)", "(witness)", "7530");
        assertContinuationGeometry(pdf);
    }

    @ParameterizedTest
    @ValueSource(strings = {"W", "王"})
    void fullSixtyCharacterCapacityFitsAtTenPointsWithoutClipping(String character) throws Exception {
        String name = character.repeat(60);
        byte[] pdf = render("14", "physicianName", name);
        String text = visibleText(pdf);
        assertComplete(text, name); assertThat(text).contains(REFERENCE, LABEL);
        assertContinuationGeometry(pdf);
    }

    private static void assertContinuationGeometry(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            Text text = new Text(); text.getText(document);
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            List<TextPosition> continuation = text.glyphs.stream().filter(p -> p.getYDirAdj() > 390 && p.getYDirAdj() < 460).toList();
            assertThat(continuation).isNotEmpty().allSatisfy(p -> {
                assertThat(p.getFontSizeInPt()).isEqualTo(10);
                assertThat(p.getXDirAdj()).isGreaterThanOrEqualTo(95);
                assertThat(p.getXDirAdj() + p.getWidthDirAdj()).isLessThanOrEqualTo(590.1f);
                assertThat(p.getYDirAdj()).isGreaterThanOrEqualTo(400).isLessThanOrEqualTo(450);
            });
        }
    }

    @Test
    void lineBreakDoesNotDiscardTheSecondPartOfAName() throws Exception {
        String value = "First legal name\nSecond legal name";
        String text = visibleText(render("14", "physicianName", value));
        assertComplete(text, value); assertThat(text).contains(REFERENCE, LABEL);
    }

    @Test
    void blankNameDoesNotInventAReferenceOrContinuation() throws Exception {
        assertThat(visibleText(render("14", "physicianName", ""))).doesNotContain(REFERENCE, LABEL);
    }

    @Test
    void overCapacityValueFailsExplicitlyInsteadOfProducingATruncatedLegalForm() {
        assertThatThrownBy(() -> render("14", "physicianName", "W".repeat(10000)))
                .isInstanceOf(DocumentException.class).hasMessageContaining("does not fit");
    }

    @Test
    void unsupportedGlyphFailsExplicitlyInsteadOfSilentlyChangingTheLegalName() {
        assertThatThrownBy(() -> render("14", "physicianName", "Name\u0378Suffix"))
                .isInstanceOf(DocumentException.class).hasMessageContaining("unavailable");
    }

    @Test
    void malformedContinuationConfigurationFailsInsteadOfOmittingTheName() throws Exception {
        Path config = directory.resolve("mentalHealthForm14Print.txt");
        Files.writeString(config, Files.readString(config).replace("_continued, 590", "_continued, NaN"));
        assertThatThrownBy(() -> render("14", "physicianName", PROSE))
                .isInstanceOf(DocumentException.class).hasMessageContaining("coordinate");
    }

    @Test
    void nativeHttpPrintReturnsTheCompleteLegalForm() throws Exception {
        MockHttpServletRequest request = request("14", "physicianName", PROSE);
        authorize(request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FrmPDFServlet servlet = new FrmPDFServlet();
        servlet.init(new MockServletConfig(new MockServletContext()));
        servlet.doPost(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertComplete(visibleText(response.getContentAsByteArray()), PROSE);
    }

    @Test
    void nativeHttpPrintRefusesAnUnavailableGlyphWithoutSendingAPdf() throws Exception {
        MockHttpServletRequest request = request("14", "physicianName", "Name\u0378Suffix");
        authorize(request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FrmPDFServlet servlet = new FrmPDFServlet();
        servlet.init(new MockServletConfig(new MockServletContext()));
        servlet.doPost(request, response);
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).isNotEqualTo("application/pdf");
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    private void authorize(MockHttpServletRequest request) {
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        LoggedInInfo info = LoggedInInfo.getLoggedInInfoFromSession(request);
        when(security.hasPrivilege(info, "_form", "r", "123")).thenReturn(true);
    }

    @Test
    void otherNativeFormConfigurationRetainsItsOriginalRendering() throws Exception {
        byte[] pdf = render("42", "name", PROSE);
        assertComplete(visibleText(pdf), PROSE);
        assertThat(visibleText(pdf)).doesNotContain(REFERENCE, LABEL);
        try (PDDocument document = Loader.loadPDF(pdf)) { assertThat(document.getNumberOfPages()).isEqualTo(2); }
    }
}
