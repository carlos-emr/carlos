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
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.ConsultationPDFCreator;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequestUtil;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.openpdf.text.Document;
import org.openpdf.text.Image;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Exercises the real old/new logo paths and actual embedded PDF image bytes. */
@Tag("unit")
class ClinicLogoSiteResolutionUnitTest extends CarlosUnitTestBase {
    @TempDir
    Path temporary;
    private final Map<String, String> settings = new HashMap<>();
    private CarlosProperties properties;
    private SiteDao sites;
    private DocumentDao documents;
    private MockedStatic<CarlosProperties> propertyMock;
    private Path store;
    private Path fax;
    private Path clinic;
    private String testName;
    private static final AtomicInteger PDF_INDEX = new AtomicInteger();

    @BeforeEach
    void configure(TestInfo information) throws Exception {
        testName = information.getDisplayName().replaceAll("[^A-Za-z0-9]+", "_");
        properties = mock(CarlosProperties.class);
        when(properties.getProperty(anyString())).thenAnswer(call -> settings.get(call.getArgument(0)));
        propertyMock = mockStatic(CarlosProperties.class);
        propertyMock.when(CarlosProperties::getInstance).thenReturn(properties);
        sites = mock(SiteDao.class);
        documents = mock(DocumentDao.class);
        registerMock(SiteDao.class, sites);
        registerMock(DocumentDao.class, documents);
        store = Files.createDirectory(temporary.resolve("documents"));
        fax = image(temporary.resolve("fax.png"), Color.RED);
        clinic = image(temporary.resolve("clinic.png"), Color.GREEN);
        settings.put("multisites", "on");
        settings.put("DOCUMENT_DIR", store.toString());
        settings.put("faxLogoInConsultation", fax.toString());
        settings.put("clinicLetterheadLogo", clinic.toString());
        when(sites.getAllSites()).thenReturn(List.of());
        // Isolate the old implementation's global context between test cases;
        // the new implementation deliberately has no such field.
        try { ReflectionTestUtils.setField(ClinicLogoUtility.class, "ectConsultationFormRequestUtil", null); }
        catch (IllegalArgumentException absentInFixedCode) { /* expected */ }
    }

    @AfterEach
    void closeProperties() {
        if (propertyMock != null) propertyMock.close();
    }

    private Path image(Path target, Color color) throws Exception {
        BufferedImage pixels = new BufferedImage(320, 160, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < pixels.getWidth(); x++) for (int y = 0; y < pixels.getHeight(); y++) pixels.setRGB(x, y, color.getRGB());
        ImageIO.write(pixels, "png", target.toFile());
        return target;
    }

    private Site site(int id, String name, int logo, Color color) throws Exception {
        Path file = image(store.resolve("site-" + id + ".png"), color);
        Site site = new Site(); site.setSiteId(id); site.setName(name); site.setSiteLogoId(logo);
        var document = new io.github.carlos_emr.carlos.commn.model.Document();
        document.setDocfilename(file.getFileName().toString());
        when(documents.getDocument(String.valueOf(logo))).thenReturn(document);
        when(sites.getById(id)).thenReturn(site);
        return site;
    }

    private EctConsultationFormRequestUtil form(String value) {
        var form = mock(EctConsultationFormRequestUtil.class);
        form.siteName = value;
        return form;
    }

    private PdfPTable consultation(String value) {
        var creator = mock(ConsultationPDFCreator.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(creator, "props", properties);
        ReflectionTestUtils.setField(creator, "reqFrm", form(value));
        return ReflectionTestUtils.invokeMethod(creator, "createLogoHeader");
    }

    private int embeddedColor(PdfPTable table) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var pdf = new Document(); PdfWriter.getInstance(pdf, bytes);
        pdf.open(); pdf.add(table); pdf.close();
        String evidence = System.getProperty("pr5044.logo.pdfEvidenceDir");
        if (evidence != null) {
            Path directory = Files.createDirectories(Path.of(evidence));
            Files.write(directory.resolve(testName + "_" + PDF_INDEX.incrementAndGet() + ".pdf"), bytes.toByteArray());
        }
        try (var parsed = Loader.loadPDF(bytes.toByteArray())) {
            var resources = parsed.getPage(0).getResources();
            for (var name : resources.getXObjectNames()) {
                if (resources.getXObject(name) instanceof PDImageXObject image) {
                    BufferedImage pixels = image.getImage();
                    return pixels.getRGB(pixels.getWidth() / 2, pixels.getHeight() / 2) & 0xffffff;
                }
            }
        }
        throw new AssertionError("Actual rendered PDF does not contain its expected logo image");
    }

    private void color(PdfPTable table, Color expected) throws Exception {
        assertThat(embeddedColor(table)).isEqualTo(expected.getRGB() & 0xffffff);
    }

    private Image cellImage(PdfPCell cell) {
        if (cell.getImage() != null) return cell.getImage();
        if (cell.getCompositeElements() != null) for (var element : cell.getCompositeElements()) {
            if (element instanceof Image image) return image;
            if (element instanceof PdfPTable table) for (var row : table.getRows()) for (var child : row.getCells()) {
                if (child != null) {
                    try { return cellImage(child); } catch (AssertionError absent) { /* try next cell */ }
                }
            }
        }
        throw new AssertionError("Logo image missing from actual table geometry");
    }

    @Test
    void actualNameValuedConsultationResolvesItsRealSiteLogo() throws Exception {
        Site beach = site(1, "MHI Beaches", 101, Color.BLUE);
        when(sites.getAllSites()).thenReturn(List.of(beach));
        color(consultation("MHI Beaches"), Color.BLUE);
    }

    @Test
    void exactRawSiteNameIsNotTrimmedIntoAnotherSite() throws Exception {
        Site spaced = site(1, " MHI Beaches ", 101, Color.BLUE);
        Site plain = site(2, "MHI Beaches", 102, Color.MAGENTA);
        when(sites.getAllSites()).thenReturn(List.of(spaced, plain));
        color(consultation(" MHI Beaches "), Color.BLUE);
    }

    @Test
    void numericNameAndLegacyNumericIdKeepTheirExplicitDifferentContracts() throws Exception {
        Site idTwo = site(2, "Main", 102, Color.BLUE);
        Site namedTwo = site(5, "2", 105, Color.MAGENTA);
        when(sites.getAllSites()).thenReturn(List.of(idTwo, namedTwo));
        color(consultation("2"), Color.MAGENTA);
        color(ClinicLogoUtility.createLogoHeader(form("2")), Color.BLUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "002", "+2"})
    void legacyNumericFallbackRemainsSupportedWhenNoExactNameExists(String value) throws Exception {
        Site idTwo = site(2, "Main", 102, Color.BLUE);
        when(sites.getAllSites()).thenReturn(List.of(idTwo));
        color(consultation(value), Color.BLUE);
        color(ClinicLogoUtility.createLogoHeader(form(value)), Color.BLUE);
    }

    @Test
    void duplicateNumericNamesFallBackAndNeverSelectTheDifferentNumericId() throws Exception {
        Site idTwo = site(2, "Main", 102, Color.BLUE);
        Site first = site(5, "2", 105, Color.MAGENTA);
        Site second = site(6, "2", 106, Color.CYAN);
        when(sites.getAllSites()).thenReturn(List.of(idTwo, first, second));
        try (LogCapture log = LogCapture.forLogger(ClinicLogoUtility.class)) {
            color(consultation("2"), Color.RED);
            assertThat(log.messages()).anyMatch(message -> message.contains("ambiguous"));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unknown site", "999999999999999999999999", "-1"})
    void absentUnknownOrUnparseableSiteUsesConfiguredConsultationDefault(String value) throws Exception {
        color(consultation(value), Color.RED);
    }

    @Test
    void normalNoSiteAndGeneralHeaderDoNotWarn() throws Exception {
        try (LogCapture log = LogCapture.forLogger(ClinicLogoUtility.class)) {
            color(consultation(null), Color.RED);
            color(ClinicLogoUtility.createLogoHeader(), Color.GREEN);
            assertThat(log.events()).isEmpty();
        }
    }

    @Test
    void globalAndConsultationDefaultPrecedenceRemainDifferent() throws Exception {
        settings.put("multisites", "off");
        color(consultation("unrelated"), Color.RED);
        color(ClinicLogoUtility.createLogoHeader(), Color.GREEN);
        settings.remove("clinicLetterheadLogo");
        color(ClinicLogoUtility.createLogoHeader(), Color.RED);
    }

    @Test
    void siteWithoutSpecificLogoUsesTheMatchingExistingDefault() throws Exception {
        Site site = new Site(); site.setSiteId(2); site.setName("Main");
        when(sites.getAllSites()).thenReturn(List.of(site)); when(sites.getById(2)).thenReturn(site);
        color(consultation("Main"), Color.RED);
        color(ClinicLogoUtility.createLogoHeader(form("2")), Color.GREEN);
    }

    @Test
    void missingDocumentAndMissingFileUseDefaultWithoutNullDereference() throws Exception {
        Site site = site(2, "Main", 102, Color.BLUE);
        when(sites.getAllSites()).thenReturn(List.of(site));
        when(documents.getDocument("102")).thenReturn(null);
        color(consultation("2"), Color.RED);
        var dangling = new io.github.carlos_emr.carlos.commn.model.Document(); dangling.setDocfilename("absent.png");
        when(documents.getDocument("102")).thenReturn(dangling);
        color(consultation("2"), Color.RED);
    }

    @Test
    void traversalAndSymlinkEscapeNeverReadForeignImageAndRetainSecurityEvidence() throws Exception {
        Site site = site(2, "Main", 102, Color.BLUE);
        when(sites.getAllSites()).thenReturn(List.of(site));
        Path foreign = image(temporary.resolve("private-clinical-logo.png"), Color.MAGENTA);
        Files.createSymbolicLink(store.resolve("escaping.png"), foreign);
        for (String filename : List.of("../private-clinical-logo.png", "escaping.png")) {
            var document = new io.github.carlos_emr.carlos.commn.model.Document(); document.setDocfilename(filename);
            when(documents.getDocument("102")).thenReturn(document);
            try (LogCapture security = LogCapture.forLogger(PathValidationUtils.class);
                 LogCapture log = LogCapture.forLogger(ClinicLogoUtility.class)) {
                color(consultation("2"), Color.RED);
                assertThat(security.messages()).anyMatch(message -> message.contains("outside allowed directory"));
                assertThat(log.messages()).isNotEmpty().allSatisfy(message -> assertThat(message)
                        .doesNotContain(filename, "private-clinical-logo", "MHI", "\n", "\r"));
            }
        }
    }

    @Test
    void explicitSiteHeaderDoesNotPersistBrandingIntoLaterGlobalFaxCover() throws Exception {
        site(2, "Main", 102, Color.BLUE);
        color(ClinicLogoUtility.createLogoHeader(form("2")), Color.BLUE);
        color(ClinicLogoUtility.createLogoHeader(), Color.GREEN);
    }

    @Test
    void parallelGlobalCoverCannotInheritAnotherThreadsRequestedSite() throws Exception {
        Site site = site(2, "Main", 102, Color.BLUE);
        CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        when(sites.getById(2)).thenAnswer(call -> { entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); return site; });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> {
                try (MockedStatic<SpringUtils> beans = mockStatic(SpringUtils.class);
                     MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class)) {
                    configuration.when(CarlosProperties::getInstance).thenReturn(properties);
                    beans.when(() -> SpringUtils.getBean(SiteDao.class)).thenReturn(sites);
                    beans.when(() -> SpringUtils.getBean(DocumentDao.class)).thenReturn(documents);
                    return ClinicLogoUtility.createLogoHeader(form("2"));
                }
            });
            try { assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue(); color(ClinicLogoUtility.createLogoHeader(), Color.GREEN); }
            finally { release.countDown(); }
            color(future.get(5, TimeUnit.SECONDS), Color.BLUE);
        }
    }

    @Test
    void consultationAndCoverKeepTheirExistingLogoCellGeometry() throws Exception {
        settings.put("multisites", "off");
        PdfPCell consult = consultation(null).getRow(0).getCells()[0];
        Image consultImage = cellImage(consult);
        assertThat(consultImage.getScaledHeight()).isEqualTo(50f);
        assertThat(consult.getPaddingLeft()).isZero(); assertThat(consult.getFixedHeight()).isZero();
        PdfPCell cover = ClinicLogoUtility.createLogoHeader().getRow(0).getCells()[0];
        Image coverImage = cellImage(cover);
        assertThat(coverImage.getScaledHeight()).isEqualTo(65f);
        assertThat(cover.getPaddingLeft()).isEqualTo(-11f); assertThat(cover.getFixedHeight()).isEqualTo(70f);
    }

    @Test
    void nullOrBlankConfiguredLogosReturnAnEmptyHeader() {
        settings.remove("faxLogoInConsultation"); settings.remove("clinicLetterheadLogo");
        assertThat(consultation(null).size()).isZero();
        assertThat(ClinicLogoUtility.createLogoHeader().size()).isZero();
        settings.put("faxLogoInConsultation", " "); settings.put("clinicLetterheadLogo", " ");
        assertThat(consultation(null).size()).isZero();
        assertThat(ClinicLogoUtility.createLogoHeader().size()).isZero();
    }
}
