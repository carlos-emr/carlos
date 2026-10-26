/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.util.PdfSigningTestSupport;
import io.github.carlos_emr.carlos.utility.DocumentManifest;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFSigningUtil;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class PortalMyCarlosSenderApplicationUnitTest {
    @TempDir Path temporary;
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final DocumentAttachmentManager renderer = mock(DocumentAttachmentManager.class);
    private final CtlDocumentDao ownership = mock(CtlDocumentDao.class);
    private final DocumentDao documents = mock(DocumentDao.class);
    private final PortalMyCarlosDeviceApprovalTransactions transactions = mock(PortalMyCarlosDeviceApprovalTransactions.class);
    private PortalMyCarlosDeliveryApplicationService application() {
        return new PortalMyCarlosDeliveryApplicationService(transactions, mock(EntityManagerFactory.class), security,
                mock(PropertyDao.class), mock(ClinicDAO.class), ownership, documents, renderer, mock(DemographicManager.class));
    }
    private static LoggedInInfo staff() {
        var info = new LoggedInInfo(); var provider = new Provider(); provider.setProviderNo("FAKE");
        var security = new Security(); security.setSecurityNo(1); info.setLoggedInProvider(provider); info.setLoggedInSecurity(security); return info;
    }
    @Test void shouldRefuseAnonymousAndUnauthorizedStaff_beforeReadingSourceOrPreparingPdf() {
        var application = application();
        assertThrows(SecurityException.class, () -> application.read(null, 6001, 44, 0, null));
        assertThrows(SecurityException.class, () -> application.send(staff(), 6001, 44,
                "00000000-0000-4000-8000-000000000001", "A".repeat(43), null));
        verifyNoInteractions(renderer, ownership, documents, transactions);
    }
    @Test void shouldRefuseDocumentOwnedByAnotherPatient_beforeConfigSigningQueueOrHttp() {
        when(security.hasPrivilege(any(), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
        when(ownership.findDocumentNosForDemographic(6001, List.of(44))).thenReturn(List.of());
        assertThrows(SecurityException.class, () -> application().read(staff(), 6001, 44, 0, null));
        verify(ownership).findDocumentNosForDemographic(6001, List.of(44));
        verifyNoInteractions(documents, renderer, transactions);
    }
    @Test void shouldRefuseMissingExistingRendererPermission_beforeSourceConfigOrRendering() throws Exception {
        when(security.hasPrivilege(any(), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
        when(security.hasPrivilege(any(), eq("_newCasemgmt.documents"), eq(SecurityInfoManager.READ), isNull())).thenReturn(false);
        var application = application();
        assertThrows(SecurityException.class, () -> application.read(staff(), 6001, 44, 0, null));
        assertThrows(SecurityException.class, () -> application.send(staff(), 6001, 44,
                "00000000-0000-4000-8000-000000000001", "A".repeat(43), null));
        verifyNoInteractions(ownership, documents, renderer, transactions);
    }

    @Test void shouldParseDedicatedKeyringAndRefuseUnknownDuplicateOrMissingKeys() {
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        String valid = "format=1\ncurrent=1\nkey.1=" + key + "\n";
        var cipher = PortalMyCarlosDeliveryKeyring.parse(valid.getBytes(StandardCharsets.US_ASCII));
        var context = new PortalMyCarlosDeliveryCipher.Context("clinic-a", "1", "", "event_page");
        assertArrayEquals(new byte[] {1}, cipher.open(context, cipher.seal(context, new byte[] {1})));
        for (String invalid : List.of(valid + "current=1\n", valid + "unknown=1\n", valid.replace("format=1", "format=2"),
                valid.replace("current=1", "current=2"), valid.replace("key.1", "key.01"), valid.replace(key, key + "="))) {
            var error = assertThrows(IllegalStateException.class,
                    () -> PortalMyCarlosDeliveryKeyring.parse(invalid.getBytes(StandardCharsets.US_ASCII)));
            assertFalse(error.toString().contains(key));
        }
        assertThrows(IllegalStateException.class, () -> PortalMyCarlosDeliveryKeyring.load(temporary.resolve("missing").toString()));
    }

    @Test void shouldUseExistingConfiguredP256Signer_whenComposingWithoutExportingPrivateMaterial() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC"); generator.initialize(new ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        var certificate = PdfSigningTestSupport.createSelfSignedCertificate(pair, "SHA256withECDSA");
        var config = PdfSigningTestSupport.enabledConfig(temporary.resolve("FAKE-sender.p12"), pair.getPrivate(), certificate);
        var identity = PortalMyCarlosSigningIdentity.fromValidatedCertificate(certificate);
        var f = PortalMyCarlosDeliveryProducerUnitTest.fixture(); var example = PortalMyCarlosDeliveryProducerUnitTest.compose(f);
        var document = new PortalMyCarlosDeliveryComposer.Document("clinic-a", "FAKE Clinic", "FAKE Provider", "FAKE Patient",
                "1990-01-01", "FAKE Report", "document", "2026-10-26");
        var copy = PortalMyCarlosDeliveryComposer.composeWithSigner(example.deliveryId(), example.recipientId(), example.device(),
                document, example.originalPdf().copy(), example.createdAt(), example.expiresAt(), identity,
                (payload, kid) -> PDFSigningUtil.signMyCarlosPayload(payload, kid, config));
        assertEquals(identity.kid(), copy.signerKid());
        var envelope = PortalMyCarlosDeviceApprovalCodec.envelope(copy.manifest());
        var verifier = java.security.Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(certificate.getPublicKey()); verifier.update(envelope.payload().copy());
        assertTrue(verifier.verify(envelope.signature().copy()));
    }

    @Test void shouldRestoreOldAndDocumentMappedSnapshots_withoutChangingTransportBytes() throws Exception {
        var old = PortalMyCarlosDeliveryStorageUnitTest.snapshot();
        assertNull(PortalMyCarlosDeliverySnapshot.decode(old.encode()).sourceDocumentId());
        String source = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        var mapped = new PortalMyCarlosDeliverySnapshot(old.clinicId(), old.demographicNo(), old.recipientId(),
                old.publicBase(), old.internalTarget(), old.configSha256(), old.acceptedDeviceId(), old.acceptanceVersion(),
                old.acceptanceSha256(), old.deliveryId(), old.device(), old.signerKid(), old.documentSha256(),
                old.ciphertextSha256(), old.createdAt(), old.expiresAt(), old.bodySha256(), old.body(), source);
        assertEquals(mapped, PortalMyCarlosDeliverySnapshot.decode(mapped.encode()));
        assertEquals(old.body(), mapped.body());
        assertEquals(old.receiptBinding(), mapped.receiptBinding());
    }

    @TestFactory List<DynamicTest> shouldRequireActualBoundSignedOriginal_whenConsumingExistingJavaPdfFixtures() throws Exception {
        ObjectMapper json = new ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode fixture;
        try (var input = getClass().getResourceAsStream("/patientportal/mycarlos-signed-pdf-fixtures.json")) { fixture = json.readTree(input); }
        var positive = fixture.at("/documents/0");
        var manifest = json.readTree(Base64.getDecoder().decode(positive.get("manifest_bytes_b64").textValue()));
        var expected = new DocumentManifest(manifest.get("document_id").textValue(), manifest.get("version").intValue(),
                manifest.get("issued_at").longValue(), manifest.get("clinic_id").textValue(), manifest.get("clinic_name").textValue(),
                manifest.get("provider_name").textValue(), manifest.get("recipient_id").textValue(), manifest.at("/patient/name").textValue(),
                manifest.at("/patient/birth_date").textValue(), manifest.at("/document/title").textValue(),
                manifest.at("/document/type").textValue(), manifest.at("/document/service_date").textValue());
        String spki = Base64.getUrlEncoder().withoutPadding().encodeToString(Base64.getDecoder().decode(fixture.at("/keys/signer/spki_der_b64").textValue()));
        var identity = new PatientPortalClinicKeysPublishRequest.Key(fixture.at("/link/signer_kid").textValue(), spki, "active", null, null);
        List<DynamicTest> tests = new ArrayList<>();
        for (var document : fixture.get("documents")) {
            String name = document.get("name").textValue();
            byte[] bytes = Base64.getDecoder().decode(document.get("pdf_b64").textValue());
            tests.add(DynamicTest.dynamicTest(name, () -> {
                if (name.equals("signed-send-to-mycarlos.pdf")) assertDoesNotThrow(() -> PortalMyCarlosSignedOriginal.verify(bytes, expected, identity));
                else assertThrows(java.io.IOException.class, () -> PortalMyCarlosSignedOriginal.verify(bytes, expected, identity));
            }));
        }
        return tests;
    }
}
