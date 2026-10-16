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
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.OtherIdManager;
import io.github.carlos_emr.carlos.commn.dao.FileUploadCheckDao;
import io.github.carlos_emr.carlos.commn.dao.OscarKeyDao;
import io.github.carlos_emr.carlos.commn.dao.OtherIdDAO;
import io.github.carlos_emr.carlos.commn.dao.PublicKeyDao;
import io.github.carlos_emr.carlos.commn.model.OscarKey;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.HHSEmrDownloadHandler;
import io.github.carlos_emr.carlos.lab.ca.all.upload.HandlerClassFactory;
import io.github.carlos_emr.carlos.lab.ca.all.upload.handlers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.unit.RecordingTransactionManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Synthetic legacy-envelope fixtures exercise authentication before durable saving, then the
 * release storage helper's transaction lifecycle. Both sender modes retain the release's
 * action result, attributes and HTTP calls. These mock responses do not execute Struts/JSP
 * error dispatch; actual XML/HTML bodies and multipart interception need container checks.
 */
@Tag("unit")
@Tag("lab")
@Tag("security")
class LabUpload2ActionUnitTest extends CarlosUnitTestBase {
    private static final String SERVICE = "SYNTHETIC_LAB";
    private static final String AUDIT = "synthetic audit";
    private static final byte[] MESSAGE = "MSH|^~\\&|SYNTHETIC|LAB|||20261013||ORU^R01|1|P|2.3\r"
            .repeat(40).getBytes(StandardCharsets.UTF_8);
    private static KeyPair receiverKeys;
    private static KeyPair senderKeys;
    private static KeyPair strangerKeys;

    @TempDir Path fixtureRoot;
    private Path documentDir;
    private Path uploadedPath;
    private File uploadedFile;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private PublicKeyDao publicKeyDao;
    private OscarKeyDao oscarKeyDao;
    private FileUploadCheckDao uploadDao;
    private io.github.carlos_emr.carlos.commn.model.PublicKey senderRow;
    private OscarKey receiverRow;
    private MessageHandler handler;
    private CarlosProperties config;
    private RecordingTransactionManager transactions;
    private final List<Path> stagedFiles = new ArrayList<>();
    private final List<Path> savedFiles = new ArrayList<>();
    private final List<InputStream> encryptedStreams = new ArrayList<>();
    private int saveCalls;
    private boolean runRealMethods;
    private Case currentCase;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<PathValidationUtils> paths;
    private MockedStatic<CarlosProperties> properties;
    private MockedStatic<Utilities> utilities;
    private MockedStatic<HandlerClassFactory> handlers;

    // Before-action failures are expected to propagate. Their real mapped status/body belongs
    // to the container matrix, rather than a fabricated MockHttpServletResponse status500.
    private enum Case {
        ACCEPTED, DUPLICATE, PARSE_NULL, PARSE_THROW, LOOKUP_FAILED, UNKNOWN_COMMIT,
        CONFIRMED_ROLLBACK, SIGNATURE_BAD, SIGNATURE_MISSING, SIGNATURE_MALFORMED,
        SAVED_TRUNCATED, SAVE_NULL, WRONG_RECEIVER_KEY, BAD_AES_BLOCK, RECEIVER_KEY_MISSING,
        SERVICE_UNKNOWN, SERVICE_MISSING, SENDER_LOOKUP_FAILED, SENDER_KEY_INVALID,
        NO_FILE, INVALID_FILENAME, SOURCE_REJECTED, HHS_FILTERED, PRIVILEGE_DENIED,
        STAGED_SIGNATURE_READ_FAILURE, SAVED_SIGNATURE_READ_FAILURE, STAGING_CREATE_FAILURE,
        STAGING_INSIDE_DOCUMENT_DIR
    }

    @BeforeAll
    static void generateSyntheticKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        receiverKeys = generator.generateKeyPair();
        senderKeys = generator.generateKeyPair();
        strangerKeys = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() throws Exception {
        stagedFiles.clear();
        savedFiles.clear();
        encryptedStreams.clear();
        saveCalls = 0;
        runRealMethods = false;
        documentDir = Files.createDirectory(fixtureRoot.resolve("documents"));
        request = new MockHttpServletRequest("POST", "/lab/newLabUpload");
        request.setParameter("service", SERVICE);
        response = new MockHttpServletResponse();
        LoggedInInfo info = mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_lab"), eq("w"), isNull())).thenReturn(true);
        publicKeyDao = createAndRegisterMock(PublicKeyDao.class);
        senderRow = mock(io.github.carlos_emr.carlos.commn.model.PublicKey.class);
        when(senderRow.getBase64EncodedPublicKey()).thenReturn(
                Base64.getEncoder().encodeToString(senderKeys.getPublic().getEncoded()));
        when(senderRow.getType()).thenReturn("SYNTHETIC");
        when(publicKeyDao.find(SERVICE)).thenReturn(senderRow);
        oscarKeyDao = createAndRegisterMock(OscarKeyDao.class);
        receiverRow = mock(OscarKey.class);
        when(receiverRow.getPrivateKey()).thenReturn(
                Base64.getEncoder().encodeToString(receiverKeys.getPrivate().getEncoded()));
        when(oscarKeyDao.find("oscar")).thenReturn(receiverRow);
        createAndRegisterMock(OtherIdDAO.class);
        uploadDao = createAndRegisterMock(FileUploadCheckDao.class);
        when(uploadDao.findByMd5Sum(anyString())).thenReturn(List.of());
        doAnswer(call -> {
            call.<io.github.carlos_emr.carlos.commn.model.FileUploadCheck>getArgument(0).setId(7);
            return null;
        }).when(uploadDao).persist(any());
        transactions = new RecordingTransactionManager();
        registerMock(PlatformTransactionManager.class, transactions);
        handler = mock(MessageHandler.class);
        when(handler.parse(any(), anyString(), anyString(), anyInt(), any())).thenReturn(AUDIT);
        config = mock(CarlosProperties.class);
        when(config.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(config.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
        properties = mockStatic(CarlosProperties.class);
        properties.when(CarlosProperties::getInstance).thenReturn(config);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        // Keep stubbing inert: CALLS_REAL_METHODS during static when(...) registration
        // could itself create an untracked temp file or invoke a saver with null matchers.
        paths = mockStatic(PathValidationUtils.class, call -> runRealMethods
                ? call.callRealMethod() : RETURNS_DEFAULTS.answer(call));
        paths.when(() -> PathValidationUtils.createSecureTempFile("LabUploadVerify", ".tmp"))
                .thenAnswer(call -> {
                    if (!runRealMethods) return null;
                    File staged = (File) call.callRealMethod();
                    stagedFiles.add(staged.toPath());
                    return staged;
                });
        paths.when(() -> PathValidationUtils.openValidatedUploadInputStream(any(File.class)))
                .thenAnswer(call -> {
                    if (!runRealMethods) return null;
                    InputStream encrypted = spy((InputStream) call.callRealMethod());
                    encryptedStreams.add(encrypted);
                    return encrypted;
                });
        utilities = mockStatic(Utilities.class, call -> runRealMethods
                ? call.callRealMethod() : RETURNS_DEFAULTS.answer(call));
        utilities.when(() -> Utilities.saveFile(any(InputStream.class), anyString()))
                .thenAnswer(call -> {
                    if (!runRealMethods) return null;
                    saveCalls++;
                    // The staged signature must have succeeded before this real saver runs.
                    assertThat(stagedFiles).hasSize(1);
                    assertThat(stagedFiles.get(0).toAbsolutePath().startsWith(documentDir.toAbsolutePath())).isFalse();
                    assertThat(Files.readAllBytes(stagedFiles.get(0))).isEqualTo(MESSAGE);
                    if (Files.getFileStore(stagedFiles.get(0)).supportsFileAttributeView("posix")) {
                        assertThat(Files.getPosixFilePermissions(stagedFiles.get(0))).isEqualTo(
                                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
                    }
                    assertThat(LabUpload2Action.validateSignature(senderKeys.getPublic(),
                            request.getParameter("signature"), stagedFiles.get(0).toFile())).isTrue();
                    assertThat(documentDir).isEmptyDirectory();
                    String savedPath = (String) call.callRealMethod();
                    if (savedPath != null) {
                        Path saved = Path.of(savedPath);
                        savedFiles.add(saved);
                        if (currentCase == Case.SAVED_TRUNCATED) {
                            Files.write(saved, Arrays.copyOf(MESSAGE, 100));
                        }
                    }
                    return savedPath;
                });
        handlers = mockStatic(HandlerClassFactory.class);
        handlers.when(() -> HandlerClassFactory.getHandler(anyString())).thenReturn(handler);
        SecretKey messageKey = newMessageKey();
        byte[] ciphertext = encrypt(messageKey, MESSAGE);
        // Surefire user properties may update java.io.tmpdir after the JDK's default
        // temp root was cached. Choose the CURRENT validator root explicitly.
        Path uploadRoot = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        uploadedPath = Files.createTempFile(uploadRoot, "labupload-unit", ".enc");
        assertThat(uploadedPath.toRealPath().startsWith(uploadRoot)).isTrue();
        Files.write(uploadedPath, ciphertext);
        uploadedFile = uploadedPath.toFile();
        request.setParameter("key", wrap(messageKey));
        request.setParameter("signature", sign(senderKeys, MESSAGE));
    }

    static Stream<Arguments> responseCases() {
        return Arrays.stream(Case.values()).flatMap(testCase -> Stream.of(
                Arguments.of(testCase, false), Arguments.of(testCase, true)));
    }

    @ParameterizedTest(name = "{0}, use_http_response_code={1}")
    @MethodSource("responseCases")
    void shouldPreserveReleaseContracts_forEveryUploadCase(Case testCase, boolean useHttpResponseCode) throws Exception {
        currentCase = testCase;
        if (useHttpResponseCode) request.setParameter("use_http_response_code", "true");
        switch (testCase) {
            case DUPLICATE -> when(uploadDao.findByMd5Sum(anyString())).thenReturn(
                    List.of(new io.github.carlos_emr.carlos.commn.model.FileUploadCheck()));
            case PARSE_NULL -> when(handler.parse(any(), anyString(), anyString(), anyInt(), any())).thenReturn(null);
            case PARSE_THROW -> when(handler.parse(any(), anyString(), anyString(), anyInt(), any()))
                    .thenThrow(new IllegalStateException("synthetic parser fault"));
            case LOOKUP_FAILED -> when(uploadDao.findByMd5Sum(anyString()))
                    .thenThrow(new IllegalStateException("synthetic lookup fault"));
            case UNKNOWN_COMMIT -> transactions.failCommit = true;
            case CONFIRMED_ROLLBACK -> transactions.rollBackOnCommit = true;
            case SIGNATURE_BAD -> request.setParameter("signature", sign(strangerKeys, MESSAGE));
            case SIGNATURE_MISSING -> request.removeParameter("signature");
            case SIGNATURE_MALFORMED -> request.setParameter("signature", "AAAA");
            case SAVE_NULL -> utilities.when(() -> Utilities.saveFile(any(InputStream.class), anyString()))
                    .thenReturn(null);
            case WRONG_RECEIVER_KEY -> when(receiverRow.getPrivateKey()).thenReturn(
                    Base64.getEncoder().encodeToString(strangerKeys.getPrivate().getEncoded()));
            case BAD_AES_BLOCK -> {
                byte[] ciphertext = Files.readAllBytes(uploadedPath);
                Files.write(uploadedPath, Arrays.copyOf(ciphertext, ciphertext.length - 1));
            }
            case RECEIVER_KEY_MISSING -> when(oscarKeyDao.find("oscar")).thenReturn(null);
            case SERVICE_UNKNOWN -> request.setParameter("service", "SYNTHETIC_UNKNOWN");
            case SERVICE_MISSING -> request.removeParameter("service");
            case SENDER_LOOKUP_FAILED -> when(publicKeyDao.find(SERVICE))
                    .thenThrow(new IllegalStateException("synthetic sender key lookup fault"));
            case SENDER_KEY_INVALID -> when(senderRow.getBase64EncodedPublicKey()).thenReturn("AQA=");
            case NO_FILE -> uploadedFile = null;
            case SOURCE_REJECTED -> paths.when(() -> PathValidationUtils.validateUpload(uploadedFile))
                    .thenThrow(new SecurityException("synthetic invalid source"));
            case HHS_FILTERED -> {
                when(senderRow.getType()).thenReturn("HHSEMR");
                when(config.getProperty("lab.hhsemr.filter_ordering_provider", "false")).thenReturn("true");
            }
            case PRIVILEGE_DENIED -> when(security.hasPrivilege(any(), eq("_lab"), eq("w"), isNull()))
                    .thenReturn(false);
            case STAGING_CREATE_FAILURE -> paths.when(() ->
                    PathValidationUtils.createSecureTempFile("LabUploadVerify", ".tmp"))
                    .thenThrow(new IOException("synthetic staging fault"));
            case STAGING_INSIDE_DOCUMENT_DIR -> paths.when(() ->
                    PathValidationUtils.createSecureTempFile("LabUploadVerify", ".tmp"))
                    .thenAnswer(call -> {
                        Path staged = Files.createTempFile(documentDir, "unsafe-stage", ".tmp");
                        stagedFiles.add(staged);
                        return staged.toFile();
                    });
            default -> { }
        }
        try (MockedConstruction<HHSEmrDownloadHandler> filters = mockConstruction(
                    HHSEmrDownloadHandler.class, (filter, context) ->
                            when(filter.getClientRef()).thenReturn("SYNTHETIC_REF"));
                MockedStatic<OtherIdManager> otherIds = mockStatic(OtherIdManager.class);
                MockedStatic<LabUpload2Action> signatures = mockStatic(LabUpload2Action.class, call -> runRealMethods
                        ? call.callRealMethod() : RETURNS_DEFAULTS.answer(call))) {
            if (testCase == Case.STAGED_SIGNATURE_READ_FAILURE || testCase == Case.SAVED_SIGNATURE_READ_FAILURE) {
                signatures.when(() -> LabUpload2Action.validateSignature(any(), anyString(), any(File.class)))
                        .thenAnswer(call -> {
                            File file = call.getArgument(2);
                            List<Path> targets = testCase == Case.STAGED_SIGNATURE_READ_FAILURE ? stagedFiles : savedFiles;
                            if (targets.contains(file.toPath())) Files.deleteIfExists(file.toPath());
                            return call.callRealMethod();
                        });
            }
            runRealMethods = true;
            LabUpload2Action action = new LabUpload2Action();
            action.setImportFile(uploadedFile);
            if (testCase == Case.INVALID_FILENAME) {
                UploadedFile part = mock(UploadedFile.class);
                when(part.getContent()).thenReturn(uploadedFile);
                when(part.getOriginalName()).thenReturn("../synthetic.enc");
                action.withUploadedFiles(List.of(part));
            }
            if (testCase == Case.PRIVILEGE_DENIED) {
                assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
                assertBeforeActionFailure();
                return;
            }
            if (EnumSet.of(Case.SERVICE_UNKNOWN, Case.SERVICE_MISSING, Case.SENDER_LOOKUP_FAILED,
                    Case.SENDER_KEY_INVALID).contains(testCase)) {
                assertThatThrownBy(action::execute).isInstanceOf(IndexOutOfBoundsException.class);
                assertBeforeActionFailure();
                return;
            }
            String result = action.execute();
            // A passing failure response must come from the intended phase, not an earlier
            // source-validation rejection that happens to share its outcome or status.
            boolean noStage = EnumSet.of(Case.NO_FILE, Case.INVALID_FILENAME,
                    Case.SOURCE_REJECTED, Case.STAGING_CREATE_FAILURE).contains(testCase);
            assertThat(stagedFiles).hasSize(noStage ? 0 : 1);
            boolean opensEncrypted = !noStage && testCase != Case.STAGING_INSIDE_DOCUMENT_DIR;
            assertThat(encryptedStreams).hasSize(opensEncrypted ? 1 : 0);
            int code = switch (testCase) {
                case ACCEPTED, NO_FILE, INVALID_FILENAME, SOURCE_REJECTED, HHS_FILTERED -> 200;
                case DUPLICATE -> 409;
                case SIGNATURE_BAD, SIGNATURE_MISSING, SIGNATURE_MALFORMED, SAVED_TRUNCATED,
                        STAGED_SIGNATURE_READ_FAILURE, SAVED_SIGNATURE_READ_FAILURE -> 406;
                default -> 500;
            };
            String outcome = switch (testCase) {
                case ACCEPTED, HHS_FILTERED -> "uploaded";
                case DUPLICATE -> "uploaded previously";
                case PARSE_NULL -> "upload failed";
                case SIGNATURE_BAD, SIGNATURE_MISSING, SIGNATURE_MALFORMED, SAVED_TRUNCATED,
                        STAGED_SIGNATURE_READ_FAILURE, SAVED_SIGNATURE_READ_FAILURE -> "validation failed";
                default -> "exception";
            };
            boolean sendsError = useHttpResponseCode && code >= 400;
            assertThat(result).isEqualTo(sendsError ? null : ActionSupport.SUCCESS);
            assertThat(response.getStatus()).isEqualTo(useHttpResponseCode ? code : 200);
            assertThat(response.getErrorMessage()).isEqualTo(sendsError ? outcome : null);
            assertThat(request.getAttribute("outcome")).isEqualTo(outcome);
            // On release, parse-null leaves audit empty; HHS early acknowledgement leaves it absent.
            assertThat(request.getAttribute("audit")).isEqualTo(testCase == Case.ACCEPTED ? AUDIT
                    : testCase == Case.HHS_FILTERED ? null : "");
            boolean retainsSaved = EnumSet.of(Case.ACCEPTED, Case.HHS_FILTERED, Case.UNKNOWN_COMMIT)
                    .contains(testCase);
            try (Stream<Path> files = Files.list(documentDir)) {
                assertThat(files.toList()).hasSize(retainsSaved ? 1 : 0);
            }
            for (Path saved : savedFiles) {
                if (retainsSaved) assertThat(Files.readAllBytes(saved)).isEqualTo(MESSAGE);
                else assertThat(saved).doesNotExist();
            }
            boolean parses = EnumSet.of(Case.ACCEPTED, Case.PARSE_NULL, Case.PARSE_THROW,
                    Case.UNKNOWN_COMMIT, Case.CONFIRMED_ROLLBACK).contains(testCase);
            if (parses) {
                verify(handler).parse(any(), eq(SERVICE), anyString(), eq(7), any());
                verify(uploadDao).persist(any());
            } else {
                verifyNoInteractions(handler);
                verify(uploadDao, never()).persist(any());
            }
            // A duplicate still completes its read-only transaction. A failed DAO lookup
            // throws INSIDE that transaction and rolls it back, before recording any row.
            assertThat(transactions.commits).isEqualTo(
                    EnumSet.of(Case.ACCEPTED, Case.DUPLICATE).contains(testCase) ? 1 : 0);
            assertThat(transactions.rollbacks).isEqualTo(EnumSet.of(Case.LOOKUP_FAILED,
                    Case.PARSE_NULL, Case.PARSE_THROW, Case.CONFIRMED_ROLLBACK).contains(testCase) ? 1 : 0);
            boolean callsSaver = EnumSet.of(Case.ACCEPTED, Case.DUPLICATE, Case.PARSE_NULL,
                    Case.PARSE_THROW, Case.LOOKUP_FAILED, Case.UNKNOWN_COMMIT, Case.CONFIRMED_ROLLBACK,
                    Case.SAVED_TRUNCATED, Case.SAVE_NULL, Case.HHS_FILTERED,
                    Case.SAVED_SIGNATURE_READ_FAILURE).contains(testCase);
            if (callsSaver) {
                utilities.verify(() -> Utilities.saveFile(any(InputStream.class), eq(uploadedFile.getName())));
            } else {
                utilities.verify(() -> Utilities.saveFile(any(InputStream.class), anyString()), never());
            }
            if (testCase == Case.HHS_FILTERED) {
                assertThat(filters.constructed()).hasSize(1);
                verify(filters.constructed().get(0)).init(anyString());
                otherIds.verify(() -> OtherIdManager.searchTable(OtherIdManager.PROVIDER, "STAR", "SYNTHETIC_REF"));
                verifyNoInteractions(uploadDao);
            }
            if (EnumSet.of(Case.SIGNATURE_BAD, Case.SIGNATURE_MISSING, Case.SIGNATURE_MALFORMED,
                    Case.STAGED_SIGNATURE_READ_FAILURE, Case.STAGING_CREATE_FAILURE, Case.STAGING_INSIDE_DOCUMENT_DIR,
                    Case.WRONG_RECEIVER_KEY,
                    Case.BAD_AES_BLOCK, Case.RECEIVER_KEY_MISSING, Case.NO_FILE, Case.INVALID_FILENAME,
                    Case.SOURCE_REJECTED).contains(testCase)) {
                assertThat(saveCalls).isZero();
                verifyNoInteractions(uploadDao);
            }
        }
    }

    private void assertBeforeActionFailure() {
        assertThat(request.getAttribute("outcome")).isNull();
        assertThat(request.getAttribute("audit")).isNull();
        assertThat(stagedFiles).isEmpty();
        assertThat(encryptedStreams).isEmpty();
        assertThat(saveCalls).isZero();
        verifyNoInteractions(uploadDao, handler);
    }

    @Test
    void shouldKeepOwnerOnlyPermissions_whenStagingDecryptedBytes() throws Exception {
        assumeTrue(Files.getFileStore(fixtureRoot).supportsFileAttributeView("posix"));
        Path staged = Files.createTempFile(fixtureRoot, "stage-mode", ".tmp",
                java.nio.file.attribute.PosixFilePermissions.asFileAttribute(EnumSet.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
        InputStream decrypted = spy(new ByteArrayInputStream(MESSAGE));
        try {
            LabUpload2Action.stageDecrypted(decrypted, staged.toFile());
            assertThat(Files.readAllBytes(staged)).isEqualTo(MESSAGE);
            assertThat(Files.getPosixFilePermissions(staged)).isEqualTo(
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            verify(decrypted).close();
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    @Test
    void shouldCloseDecryptedStream_whenStagingOutputCannotOpen() throws Exception {
        Path directory = Files.createDirectory(fixtureRoot.resolve("cannot-open-as-file"));
        InputStream decrypted = spy(new ByteArrayInputStream(MESSAGE));
        assertThatThrownBy(() -> LabUpload2Action.stageDecrypted(decrypted, directory.toFile()))
                .isInstanceOf(IOException.class);
        verify(decrypted).close();
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            for (Path staged : stagedFiles) assertThat(staged).doesNotExist();
            for (InputStream stream : encryptedStreams) verify(stream, atLeastOnce()).close();
        } finally {
            for (MockedStatic<?> open : Arrays.asList(handlers, utilities, paths, servlet, properties)) {
                if (open != null) open.close();
            }
            if (uploadedPath != null) Files.deleteIfExists(uploadedPath);
            for (Path staged : stagedFiles) Files.deleteIfExists(staged);
        }
    }

    private static SecretKey newMessageKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        return generator.generateKey();
    }

    private static byte[] encrypt(SecretKey messageKey, byte[] plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES"); // synthetic legacy transport fixture
        cipher.init(Cipher.ENCRYPT_MODE, messageKey);
        return cipher.doFinal(plaintext);
    }

    private static String wrap(SecretKey messageKey) throws Exception {
        // Synthetic sender fixture: keep the receiver's existing legacy PKCS1 wire format.
        // This is test-only usage, not approval or dismissal of a code-scanning finding.
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, receiverKeys.getPublic());
        return Base64.getEncoder().encodeToString(cipher.doFinal(messageKey.getEncoded()));
    }

    private static String sign(KeyPair signer, byte[] plaintext) throws Exception {
        Signature signature = Signature.getInstance("MD5WithRSA"); // synthetic legacy transport fixture
        signature.initSign(signer.getPrivate());
        signature.update(plaintext);
        return Base64.getEncoder().encodeToString(signature.sign());
    }
}
