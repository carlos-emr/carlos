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
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.OscarKeyDao;
import io.github.carlos_emr.carlos.commn.dao.PublicKeyDao;
import io.github.carlos_emr.carlos.commn.model.OscarKey;
import io.github.carlos_emr.carlos.lab.FileUploadCheck;
import io.github.carlos_emr.carlos.lab.ca.all.upload.HandlerClassFactory;
import io.github.carlos_emr.carlos.lab.ca.all.upload.handlers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Pins the receiver-side outcomes of the legacy lab upload envelope: the decrypted message is
 * staged owner-only, verified, and only then stored; sender-side failures share one rejection;
 * receiver-side faults stay retryable 5xx; and a stored copy that differs from the verified
 * bytes is never parsed. All keys and lab content are synthetic.
 *
 * @since 2026-09-18
 */
@Tag("unit")
@Tag("lab")
@Tag("security")
@DisplayName("LabUpload2Action verification ordering and response mapping")
class LabUpload2ActionUnitTest extends CarlosUnitTestBase {

    private static final String SERVICE = "SYNTHETIC_LAB";
    private static final byte[] MESSAGE = "MSH|^~\\&|SYNTHETIC|LAB|||20260918||ORU^R01|1|P|2.3\r"
            .repeat(40).getBytes(StandardCharsets.UTF_8);

    private static KeyPair receiverKeys;
    private static KeyPair senderKeys;
    private static KeyPair strangerKeys;

    @TempDir
    Path documentDir;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private PublicKeyDao publicKeyDao;
    private OscarKeyDao oscarKeyDao;
    private MessageHandler messageHandler;
    private File uploadedFile;

    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> session;
    private MockedStatic<CarlosProperties> properties;
    private MockedStatic<Utilities> utilities;
    private MockedStatic<FileUploadCheck> uploadCheck;
    private MockedStatic<HandlerClassFactory> handlers;

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
        request = new MockHttpServletRequest("POST", "/lab/newLabUpload");
        request.setParameter("service", SERVICE);
        request.setParameter("use_http_response_code", "true");
        response = new MockHttpServletResponse();

        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_lab"), eq("w"), isNull())).thenReturn(true);

        publicKeyDao = createAndRegisterMock(PublicKeyDao.class);
        io.github.carlos_emr.carlos.commn.model.PublicKey senderRow =
                mock(io.github.carlos_emr.carlos.commn.model.PublicKey.class);
        when(senderRow.getBase64EncodedPublicKey())
                .thenReturn(Base64.getEncoder().encodeToString(senderKeys.getPublic().getEncoded()));
        when(senderRow.getType()).thenReturn("SYNTHETIC");
        when(publicKeyDao.find(SERVICE)).thenReturn(senderRow);

        oscarKeyDao = createAndRegisterMock(OscarKeyDao.class);
        OscarKey receiverRow = mock(OscarKey.class);
        when(receiverRow.getPrivateKey())
                .thenReturn(Base64.getEncoder().encodeToString(receiverKeys.getPrivate().getEncoded()));
        when(oscarKeyDao.find("oscar")).thenReturn(receiverRow);

        messageHandler = mock(MessageHandler.class);
        when(messageHandler.parse(any(), anyString(), anyString(), anyInt(), any())).thenReturn("audit-ok");

        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        session = mockStatic(LoggedInInfo.class);
        session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(mock(LoggedInInfo.class));

        CarlosProperties carlosProperties = mock(CarlosProperties.class);
        when(carlosProperties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
        when(carlosProperties.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        properties = mockStatic(CarlosProperties.class);
        properties.when(CarlosProperties::getInstance).thenReturn(carlosProperties);

        utilities = mockStatic(Utilities.class);
        storeWith(Long.MAX_VALUE);
        uploadCheck = mockStatic(FileUploadCheck.class);
        uploadCheck.when(() -> FileUploadCheck.addFile(anyString(), any(), anyString())).thenReturn(7);
        handlers = mockStatic(HandlerClassFactory.class);
        handlers.when(() -> HandlerClassFactory.getHandler("SYNTHETIC")).thenReturn(messageHandler);
    }

    @AfterEach
    void tearDown() throws Exception {
        for (MockedStatic<?> open : Arrays.asList(handlers, uploadCheck, utilities, properties, session, servlet)) {
            if (open != null) {
                open.close();
            }
        }
        if (uploadedFile != null) {
            Files.deleteIfExists(uploadedFile.toPath());
        }
    }

    @Test
    @DisplayName("should store and parse the message when the envelope and signature are valid")
    void shouldStoreAndParse_whenEnvelopeAndSignatureAreValid() throws Exception {
        SecretKey messageKey = newMessageKey();
        upload(encrypt(messageKey, MESSAGE), wrap(messageKey), sign(senderKeys, MESSAGE));

        String result = executeUpload();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute("audit")).isEqualTo("audit-ok");
        assertThat(Files.readAllBytes(storedDocument())).isEqualTo(MESSAGE);
        assertThat(leftoverStagingFiles()).isEmpty();
    }

    @Test
    @DisplayName("should reject with 406 and store nothing when the signature does not verify")
    void shouldStoreNothing_whenSignatureDoesNotVerify() throws Exception {
        SecretKey messageKey = newMessageKey();
        upload(encrypt(messageKey, MESSAGE), wrap(messageKey), sign(strangerKeys, MESSAGE));

        executeUpload();

        assertThat(response.getStatus()).isEqualTo(406);
        utilities.verifyNoInteractions();
        assertThat(documentDir).isEmptyDirectory();
    }

    @Test
    @DisplayName("should give one rejected outcome for an unknown service, a bad key unwrap, and a bad AES block")
    void shouldShareOneRejection_forEverySenderSideDecryptFailure() throws Exception {
        SecretKey messageKey = newMessageKey();
        byte[] ciphertext = encrypt(messageKey, MESSAGE);

        // Unknown service.
        upload(ciphertext, wrap(messageKey), sign(senderKeys, MESSAGE));
        request.setParameter("service", "NO_SUCH_SERVICE");
        executeUpload();
        assertRejected();

        // Wrapped key that was not produced for this receiver.
        resetResponse();
        request.setParameter("service", SERVICE);
        Cipher foreignWrap = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        foreignWrap.init(Cipher.ENCRYPT_MODE, strangerKeys.getPublic());
        upload(ciphertext, Base64.getEncoder().encodeToString(foreignWrap.doFinal(messageKey.getEncoded())),
                sign(senderKeys, MESSAGE));
        executeUpload();
        assertRejected();

        // Key unwraps, but the final AES block carries invalid PKCS#5 padding (0x00). The cipher
        // stream only reports this lazily, mid-copy, which used to surface as a distinct 500.
        resetResponse();
        Cipher noPadding = Cipher.getInstance("AES/ECB/NoPadding");
        noPadding.init(Cipher.ENCRYPT_MODE, messageKey);
        upload(noPadding.doFinal(new byte[32]), wrap(messageKey), sign(senderKeys, MESSAGE));
        executeUpload();
        assertRejected();

        utilities.verifyNoInteractions();
        assertThat(leftoverStagingFiles()).isEmpty();
    }

    @Test
    @DisplayName("should answer 500, not a sender rejection, when the receiver private key is unavailable")
    void shouldAnswerServerError_whenReceiverKeyIsUnavailable() throws Exception {
        when(oscarKeyDao.find("oscar")).thenReturn(null);
        SecretKey messageKey = newMessageKey();
        upload(encrypt(messageKey, MESSAGE), wrap(messageKey), sign(senderKeys, MESSAGE));

        executeUpload();

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(request.getAttribute("outcome")).isEqualTo("exception");
    }

    @Test
    @DisplayName("should answer 500, not a sender rejection, when the sender key lookup fails")
    void shouldAnswerServerError_whenSenderKeyLookupFails() throws Exception {
        when(publicKeyDao.find(SERVICE)).thenThrow(new IllegalStateException("synthetic outage"));
        SecretKey messageKey = newMessageKey();
        upload(encrypt(messageKey, MESSAGE), wrap(messageKey), sign(senderKeys, MESSAGE));

        String result = executeUpload();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    @DisplayName("should not parse and should remove the stored copy when it differs from the verified bytes")
    void shouldNotParse_whenStoredCopyIsTruncated() throws Exception {
        storeWith(100); // mirrors Utilities.saveFile logging an IOException and still returning the path
        SecretKey messageKey = newMessageKey();
        upload(encrypt(messageKey, MESSAGE), wrap(messageKey), sign(senderKeys, MESSAGE));

        executeUpload();

        assertThat(response.getStatus()).isEqualTo(500);
        verify(messageHandler, never()).parse(any(), any(), any(), anyInt(), any());
        uploadCheck.verifyNoInteractions();
        assertThat(documentDir).isEmptyDirectory();
    }

    @Test
    @DisplayName("should pass a null audit through so the view keeps rendering its failure default")
    void shouldPassNullAuditThrough_whenHandlerParseFails() throws Exception {
        when(messageHandler.parse(any(), anyString(), anyString(), anyInt(), any())).thenReturn(null);
        SecretKey messageKey = newMessageKey();
        upload(encrypt(messageKey, MESSAGE), wrap(messageKey), sign(senderKeys, MESSAGE));

        executeUpload();

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(request.getAttribute("outcome")).isEqualTo("upload failed");
        assertThat(request.getAttribute("audit")).isNull();
    }

    @Test
    @DisplayName("should keep the staged cleartext owner-only after the decrypted bytes are written")
    void shouldKeepStagedFileOwnerOnly_afterWritingDecryptedBytes() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        File staged = PathValidationUtils.createSecureTempFile("LabUploadVerifyTest", ".tmp");
        try {
            LabUpload2Action.stageDecrypted(new ByteArrayInputStream(MESSAGE), staged);

            assertThat(Files.readAllBytes(staged.toPath())).isEqualTo(MESSAGE);
            assertThat(Files.getPosixFilePermissions(staged.toPath()))
                    .isEqualTo(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } finally {
            Files.deleteIfExists(staged.toPath());
        }
    }

    // ---- fixtures ---------------------------------------------------------------------------

    /** Stands in for Utilities.saveFile, copying at most {@code limit} bytes into DOCUMENT_DIR. */
    private void storeWith(long limit) {
        utilities.when(() -> Utilities.saveFile(any(InputStream.class), anyString())).thenAnswer(call -> {
            byte[] all = call.<InputStream>getArgument(0).readAllBytes();
            Path stored = documentDir.resolve("LabUpload.synthetic." + System.nanoTime());
            Files.write(stored, Arrays.copyOf(all, (int) Math.min(limit, all.length)));
            return stored.toString();
        });
    }

    private void upload(byte[] ciphertext, String wrappedKey, String signature) throws Exception {
        if (uploadedFile != null) {
            Files.deleteIfExists(uploadedFile.toPath());
        }
        uploadedFile = Files.createTempFile("labupload-unit", ".enc").toFile();
        Files.write(uploadedFile.toPath(), ciphertext);
        request.setParameter("key", wrappedKey);
        request.setParameter("signature", signature);
    }

    private String executeUpload() {
        LabUpload2Action action = new LabUpload2Action();
        action.setImportFile(uploadedFile);
        return action.execute();
    }

    private void resetResponse() {
        response = new MockHttpServletResponse();
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
    }

    private void assertRejected() {
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorMessage()).isEqualTo("rejected");
        assertThat(request.getAttribute("outcome")).isEqualTo("rejected");
    }

    private Path storedDocument() throws Exception {
        try (var files = Files.list(documentDir)) {
            return files.findFirst().orElseThrow();
        }
    }

    private static java.util.List<Path> leftoverStagingFiles() throws Exception {
        try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(path -> path.getFileName().toString().startsWith("LabUploadVerify")
                    && !path.getFileName().toString().startsWith("LabUploadVerifyTest")).toList();
        }
    }

    private static SecretKey newMessageKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        return generator.generateKey();
    }

    private static byte[] encrypt(SecretKey messageKey, byte[] plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES"); // legacy wire format under test
        cipher.init(Cipher.ENCRYPT_MODE, messageKey);
        return cipher.doFinal(plaintext);
    }

    private static String wrap(SecretKey messageKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, receiverKeys.getPublic());
        return Base64.getEncoder().encodeToString(cipher.doFinal(messageKey.getEncoded()));
    }

    private static String sign(KeyPair signer, byte[] plaintext) throws Exception {
        Signature signature = Signature.getInstance("MD5WithRSA"); // legacy wire format under test
        signature.initSign(signer.getPrivate());
        signature.update(plaintext);
        return Base64.getEncoder().encodeToString(signature.sign());
    }
}
