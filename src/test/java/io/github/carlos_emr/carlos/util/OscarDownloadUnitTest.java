package io.github.carlos_emr.carlos.util;

import io.github.carlos_emr.carlos.report.pageUtil.Obec2Action;
import io.github.carlos_emr.carlos.utility.BoundedSessionAttributes;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnFileAccessService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnFileAccessService.DownloadAccess;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingFilenameImportDao;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingFilenameImportDao.FileReview;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingImportedFiles;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingFileWriteException;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OscarDownload filename validation")
@Tag("unit")
@Tag("security")
class OscarDownloadUnitTest {

    private BillingOnFileAccessService fileAccess;
    private BillingFilenameImportDao imports;
    private MockedStatic<SpringUtils> spring;

    @BeforeEach
    void permitExistingIntegrityFixtures() {
        fileAccess = Mockito.mock(BillingOnFileAccessService.class);
        imports = Mockito.mock(BillingFilenameImportDao.class);
        Mockito.when(fileAccess.downloadAccess(ArgumentMatchers.any(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyBoolean())).thenReturn(DownloadAccess.GRANTED);
        Mockito.when(fileAccess.canDownload(ArgumentMatchers.any(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyBoolean())).thenReturn(true);
        Mockito.doCallRealMethod().when(fileAccess).openScopedDownload(ArgumentMatchers.any());
        spring = Mockito.mockStatic(SpringUtils.class);
        spring.when(() -> SpringUtils.getBean(BillingFilenameImportDao.class)).thenReturn(imports);
        spring.when(() -> SpringUtils.getBean(
                BillingOnFileAccessService.class)).thenReturn(fileAccess);
    }

    @AfterEach
    void closeSpring() {
        spring.close();
    }

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {"missing", "unknown"})
    void shouldRefuseMissingOrUnknownSessionDirectoryKeys_beforeOpeningAFile(String mode) throws Exception {
        var request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
        request.addParameter("filename", "report.bin");
        if ("unknown".equals(mode)) request.addParameter("homepath", "not-an-approved-key");
        request.getSession().setAttribute("user", "999998");
        var response = new MockHttpServletResponse();
        new OscarDownload().service(request, response);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getHeader("Content-Disposition")).isNull();
    }

    @Test
    void shouldRefuseANonDirectorySessionPath_beforeOpeningAFile() throws Exception {
        var request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
        request.addParameter("filename", "report.bin");
        request.addParameter("homepath", "obecdownload");
        request.getSession().setAttribute("user", "999998");
        request.getSession().setAttribute("obecdownload", Files.writeString(tempDir.resolve("not-a-directory"), "fixture").toString());
        var response = new MockHttpServletResponse();
        new OscarDownload().service(request, response);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getHeader("Content-Disposition")).isNull();
    }

    @Test
    @DisplayName("should reject GET when filename is hidden")
    void shouldRejectGet_whenFilenameIsHidden() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
        request.addParameter("filename", ".env");
        request.addParameter("homepath", "homepath");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingOscarDownload servlet = new RecordingOscarDownload();

        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(servlet.transferCalled).isFalse();
    }

    @ParameterizedTest
    @DisplayName("should reject GET when filename contains path components")
    @ValueSource(strings = {"../report.txt", "..\\report.txt", "/tmp/report.txt"})
    void shouldRejectGet_whenFilenameContainsPathComponents(String filename) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
        request.addParameter("filename", filename);
        request.addParameter("homepath", "homepath");
        request.getSession().setAttribute("homepath", tempDir.toString());
        request.getSession().setAttribute("user", "999998");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingOscarDownload servlet = new RecordingOscarDownload();

        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(servlet.transferCalled).isFalse();
    }

    @Test
    @DisplayName("should use normalized filename when filename contains spaces")
    void shouldUseNormalizedFilename_whenFilenameContainsSpaces() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
        request.addParameter("filename", "report final.txt");
        request.addParameter("homepath", "homepath");
        request.getSession().setAttribute("homepath", tempDir.toString());
        request.getSession().setAttribute("user", "999998");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingOscarDownload servlet = new RecordingOscarDownload();

        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(servlet.transferCalled).isTrue();
        assertThat(servlet.transferDir).isEqualTo(tempDir.toString());
        assertThat(servlet.transferFilename).isEqualTo("report_final.txt");
    }

    @Test
    void shouldRefuseDownload_whenImportedFileIsAmbiguous() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
        request.addParameter("filename", "o19-claim-2021-2-0.claim");
        request.addParameter("homepath", "ohipdownload");
        request.getSession().setAttribute("ohipdownload", tempDir.toString());
        request.getSession().setAttribute("user", "999998");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingOscarDownload servlet = new RecordingOscarDownload();
        servlet.rejectBilling = true;
        servlet.service(request, response);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getErrorMessage()).contains("requires reconciliation");
        assertThat(servlet.transferCalled).isFalse();
    }

    @Test
    void shouldTransferDownload_whenImportedFileIsReviewed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
        request.addParameter("filename", "o19-claim-2021-2-0.claim");
        request.addParameter("homepath", "ohipdownload");
        request.getSession().setAttribute("ohipdownload", tempDir.toString());
        request.getSession().setAttribute("user", "999998");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingOscarDownload servlet = new RecordingOscarDownload();
        servlet.service(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(servlet.billingChecked).isTrue();
        assertThat(servlet.transferCalled).isTrue();
    }

    /** Replaces the pathname at response opening; only the verified snapshot may be sent. */
    @Test
    void shouldServeReviewedBytes_whenBillingFileIsReplacedBeforeStreaming() throws Exception {
        String filename = "o19-claim-2021-2-0.claim";
        byte[] reviewedBytes = "reviewed submission".getBytes(StandardCharsets.UTF_8);
        Path file = tempDir.resolve(filename);
        Files.write(file, reviewedBytes);
        Path replacement = Files.writeString(tempDir.resolve("replacement"), "different unreviewed submission");
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(reviewedBytes));
        FileReview review = new FileReview("shared.001", filename, "reconciled", sha, "billing-admin", true);
        OscarDownload servlet = new OscarDownload() {
            @Override
            protected BillingImportedFiles.ReviewedFile reviewedBillingFile(String name, Path path) {
                return BillingImportedFiles.openReviewed(List.of(review), name, path);
            }
        };
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
        request.addParameter("filename", filename);
        request.addParameter("homepath", "ohipdownload");
        request.getSession().setAttribute("ohipdownload", tempDir.toString());
        request.getSession().setAttribute("user", "999998");
        MockHttpServletResponse response = new MockHttpServletResponse() {
            @Override
            public ServletOutputStream getOutputStream() {
                try {
                    Files.move(replacement, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException failure) {
                    throw new java.io.UncheckedIOException(failure);
                }
                return super.getOutputStream();
            }
        };

        servlet.service(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEqualTo(reviewedBytes);
        assertThat(response.getContentLengthLong()).isEqualTo(reviewedBytes.length);
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(Files.readString(file)).isEqualTo("different unreviewed submission");
    }

    @Test
    void shouldRefuseDownload_whenAlternateSessionKeyPointsToBillingDirectory() throws Exception {
        try (MockedStatic<CarlosProperties> properties =
                     Mockito.mockStatic(CarlosProperties.class)) {
            CarlosProperties props = Mockito.mock(CarlosProperties.class);
            Mockito.when(props.getProperty("HOME_DIR")).thenReturn(tempDir.toString());
            properties.when(CarlosProperties::getInstance).thenReturn(props);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
            request.addParameter("filename", "shared.001");
            request.addParameter("homepath", "homepath");
            request.getSession().setAttribute("homepath", tempDir.resolve(".").toString());
            request.getSession().setAttribute("user", "999998");
            MockHttpServletResponse response = new MockHttpServletResponse();
            RecordingOscarDownload servlet = new RecordingOscarDownload();
            servlet.rejectBilling = true;
            servlet.service(request, response);
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(servlet.billingChecked).isTrue();
            assertThat(servlet.transferCalled).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ON ", " on", "", "unsupported", "ＢＣ", "BÇ"})
    void shouldRefuseUnownedBillingFileBeforeResponse_whenOntarioOrProvinceIsAmbiguous(String region) throws Exception {
        var config = CarlosProperties.getInstance();
        Object oldRegion = config.put("billregion", region);
        Object oldHome = config.put("HOME_DIR", tempDir.toString());
        try {
            var login = new LoggedInInfo();
            Mockito.when(fileAccess.downloadAccess(login, "mixed.001", false)).thenReturn(DownloadAccess.DENIED);
            for (String alias : List.of("ohipdownload", "homepath", "obecdownload")) {
                var request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
                LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
                request.getSession().setAttribute("user", "999998");
                request.getSession().setAttribute(alias, tempDir.toString());
                request.addParameter("homepath", alias);
                request.addParameter("filename", "mixed.001");
                var response = new MockHttpServletResponse();
                var servlet = new RecordingOscarDownload();
                servlet.service(request, response);
                assertThat(response.getStatus()).as(alias).isEqualTo(403);
                assertThat(response.getHeader("Content-Disposition")).isNull();
                assertThat(response.getContentAsByteArray()).isEmpty();
                assertThat(servlet.billingChecked).isFalse();
            }
        } finally {
            if (oldRegion == null) config.remove("billregion"); else config.put("billregion", oldRegion);
            if (oldHome == null) config.remove("HOME_DIR"); else config.put("HOME_DIR", oldHome);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"BC", "Bc", "bC", " bc ", "obec", "not-issued", "unregistered-flag", "evicted"})
    void shouldKeepTeleplanAndPreviouslyGeneratedNonbillingObecDownloads_whenUsingSharedKeys(String mode) throws Exception {
        boolean bc = List.of("BC", "Bc", "bC", " bc ").contains(mode);
        boolean allowed = bc || mode.equals("obec");
        var config = CarlosProperties.getInstance();
        Object oldRegion = config.put("billregion", bc ? mode : "ON");
        Object oldHome = config.put("HOME_DIR", tempDir.toString());
        try {
            var request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
            var login = new LoggedInInfo();
            LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
            request.getSession().setAttribute("user", "999998");
            String key = bc ? "ohipdownload" : "obecdownload";
            String name = bc ? "teleplan.001" : "OBECE100.TXT";
            request.getSession().setAttribute(key, tempDir.toString());
            String entry = Obec2Action.DOWNLOAD_PREFIX + name;
            if (mode.equals("obec") || mode.equals("evicted")) {
                BoundedSessionAttributes.put(request.getSession(), Obec2Action.DOWNLOAD_CATEGORY, entry, Boolean.TRUE);
            }
            if (mode.equals("evicted")) {
                for (int i = 0; i < BoundedSessionAttributes.MAX_ENTRIES; i++) {
                    BoundedSessionAttributes.put(request.getSession(), Obec2Action.DOWNLOAD_CATEGORY,
                            Obec2Action.DOWNLOAD_PREFIX + "OBECE" + (1000 + i) + ".TXT", Boolean.TRUE);
                }
            }
            if (mode.equals("unregistered-flag")) request.getSession().setAttribute(entry, Boolean.TRUE);
            request.addParameter("homepath", key);
            request.addParameter("filename", name);
            Mockito.when(fileAccess.downloadAccess(login, name, false)).thenReturn(DownloadAccess.DENIED);
            var response = new MockHttpServletResponse();
            var servlet = new RecordingOscarDownload();
            servlet.service(request, response);
            assertThat(response.getStatus()).isEqualTo(allowed ? 200 : 403);
            assertThat(servlet.transferCalled).isEqualTo(allowed);
            if (bc) Mockito.verifyNoInteractions(fileAccess);
            else Mockito.verify(fileAccess).downloadAccess(login, name, mode.equals("obec"));
        } finally {
            if (oldRegion == null) config.remove("billregion"); else config.put("billregion", oldRegion);
            if (oldHome == null) config.remove("HOME_DIR"); else config.put("HOME_DIR", oldHome);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRequireBothFileIdentities_whenTheBillingNameNormalizes(boolean originalPermitted) throws Exception {
        var properties = Mockito.mock(CarlosProperties.class);
        Mockito.when(properties.getProperty("billregion", "")).thenReturn("ON");
        Mockito.when(properties.getProperty("HOME_DIR")).thenReturn(tempDir.toString());
        Mockito.when(properties.getProperty("HOME_DIR", "")).thenReturn(tempDir.toString());
        try (var config = Mockito.mockStatic(CarlosProperties.class)) {
            config.when(CarlosProperties::getInstance).thenReturn(properties);
            var request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
            var login = new LoggedInInfo();
            LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
            request.getSession().setAttribute("user", "999998");
            request.getSession().setAttribute("ohipdownload", tempDir.toString());
            request.addParameter("homepath", "ohipdownload");
            request.addParameter("filename", "old drawing.html");
            Files.writeString(tempDir.resolve("old_drawing.html"), "owned historical bytes");
            Mockito.when(fileAccess.downloadAccess(login, "old_drawing.html", false)).thenReturn(DownloadAccess.VERIFY_OWNERS);
            Mockito.when(fileAccess.canDownload(login, "old drawing.html", false)).thenReturn(originalPermitted);
            var response = new MockHttpServletResponse();
            var servlet = new RecordingOscarDownload();
            servlet.service(request, response);
            assertThat(response.getStatus()).isEqualTo(originalPermitted ? 200 : 403);
            assertThat(response.getContentAsString()).isEqualTo(originalPermitted ? "owned historical bytes" : "");
            Mockito.verify(fileAccess).canDownload(login, "old_drawing.html", false);
            Mockito.verify(fileAccess).canDownload(login, "old drawing.html", false);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"writer-busy", "ordinary", "reconciled", "unresolved", "missing", "symlink", "outside"})
    void shouldCheckOwnershipAndOpenTheSelectedBytes_beforeStreamingAScopedBillingDownload(String mode) throws Exception {
        String name = "scoped-test.001";
        byte[] original = "recorded fixture".getBytes(StandardCharsets.UTF_8);
        Path path = tempDir.resolve(name);
        if (mode.equals("symlink")) {
            Files.createSymbolicLink(path, Files.writeString(tempDir.resolve("other-provider.001"), "unrelated fixture"));
        } else if (!mode.equals("missing")) Files.write(path, original);
        if (mode.equals("reconciled") || mode.equals("unresolved")) {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
            Mockito.when(imports.findFileReviews(name)).thenReturn(List.of(new FileReview("older.001", name,
                    mode, digest, "billing-admin", true)));
        }
        var login = new LoggedInInfo();
        var request = new MockHttpServletRequest("GET", "/servlet/OscarDownload");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        request.getSession().setAttribute("user", "999998");
        request.getSession().setAttribute("ohipdownload", tempDir.toString());
        request.addParameter("homepath", "ohipdownload");
        request.addParameter("filename", name);
        Mockito.when(fileAccess.downloadAccess(login, name, false)).thenReturn(DownloadAccess.VERIFY_OWNERS);
        Mockito.when(fileAccess.canDownload(login, name, false)).thenReturn(!mode.equals("outside"));
        Path lockFile = Files.createFile(tempDir.resolve(".carlos-ohip-disk.lock"));
        var response = new MockHttpServletResponse() {
            @Override public ServletOutputStream getOutputStream() {
                try (var writer = FileChannel.open(lockFile, StandardOpenOption.WRITE); var lock = writer.tryLock()) {
                    assertThat(lock).isNotNull();
                    Path replacement = Files.writeString(tempDir.resolve("replacement"), "later fixture");
                    Files.move(replacement, path, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException failure) {
                    throw new AssertionError(failure);
                }
                return super.getOutputStream();
            }
        };
        var config = CarlosProperties.getInstance();
        Object oldHome = config.put("HOME_DIR", tempDir.toString());
        Object oldRegion = config.put("billregion", "ON");
        try (var writer = FileChannel.open(lockFile, StandardOpenOption.WRITE);
             var held = mode.equals("writer-busy") ? writer.lock() : null) {
            new OscarDownload().service(request, response);
        } finally {
            if (oldRegion == null) config.remove("billregion"); else config.put("billregion", oldRegion);
            if (oldHome == null) config.remove("HOME_DIR"); else config.put("HOME_DIR", oldHome);
        }
        if (mode.equals("ordinary") || mode.equals("reconciled")) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsByteArray()).isEqualTo(original);
            assertThat(Files.readString(path)).isEqualTo("later fixture");
        } else {
            int status = switch (mode) { case "outside" -> 403; case "missing", "symlink" -> 404; default -> 409; };
            assertThat(response.getStatus()).isEqualTo(status);
            assertThat(response.getHeader("Content-Disposition")).isNull();
            assertThat(response.getContentAsByteArray()).isEmpty();
        }
        if (mode.equals("writer-busy")) {
            Mockito.verify(fileAccess, Mockito.never()).canDownload(login, name, false);
            assertThat(response.getErrorMessage()).contains("already in progress");
        }
    }

    private static final class RecordingOscarDownload extends OscarDownload {
        private boolean rejectBilling;
        private boolean billingChecked;
        private boolean transferCalled;
        private String transferDir;
        private String transferFilename;

        @Override
        protected void requireBillingFile(String filename, Path path) {
            billingChecked = true;
            if (rejectBilling) throw BillingFileWriteException.forReason(
                    BillingFileWriteException.Reason.IMPORTED_FILE_UNRESOLVED, null);
        }

        @Override
        protected BillingImportedFiles.ReviewedFile reviewedBillingFile(String filename, Path path) {
            requireBillingFile(filename, path);
            return null;
        }

        @Override
        protected void transferFile(HttpServletResponse res, ServletOutputStream stream, String dir, String filename)
                throws IOException {
            transferCalled = true;
            transferDir = dir;
            transferFilename = filename;
        }
    }
}
