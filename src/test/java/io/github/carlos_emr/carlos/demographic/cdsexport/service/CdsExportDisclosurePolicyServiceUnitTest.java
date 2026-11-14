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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

@Tag("unit") @Tag("cds-export")
class CdsExportDisclosurePolicyServiceUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path ROOT = Path.of("/owned-policy");
    private static CdsExportRecipientFixture certificate;
    private final Map<Path, byte[]> files = new HashMap<>();
    private Properties settings;
    @BeforeAll static void certificate() throws Exception { certificate = new CdsExportRecipientFixture(); }
    @BeforeEach void setup() throws Exception {
        files.clear(); settings = new Properties(); settings.setProperty("cds.export.policyRoot", ROOT.toString());
        put("sender.json", Map.of("protocol", 1, "enabled", true, "namespace", "owned-fixture", "physicianGroup", "Owned Clinic", "groupContactInformation", "fixture@example.test"));
        put("recipients/receiver.json", Map.of("protocol", 1, "enabled", true, "id", "receiver", "certificateId", "receiver-key", "certificateSha256", certificate.fingerprint, "profiles", List.of("omd-5.2", "pcon-2024-02")));
        files.put(ROOT.resolve("certificates/receiver-key.der"), certificate.certificate.getEncoded());
        put("audit/approved.json", Map.of("protocol", 1, "enabled", true, "id", "approved", "description", "Owned recipient has separately approved audit access", "recipientIds", List.of("receiver"), "providerNos", List.of("12"), "profiles", List.of("omd-5.2", "pcon-2024-02")));
        manifest();
    }
    private void put(String path, Object value) throws Exception { files.put(ROOT.resolve(path), JSON.writeValueAsBytes(value)); }
    private void change(String path, String key, Object value) throws Exception {
        var object = JSON.readTree(files.get(ROOT.resolve(path))); ((com.fasterxml.jackson.databind.node.ObjectNode) object).set(key, JSON.valueToTree(value)); put(path, object);
    }
    private void manifest() throws Exception {
        put("policy.json", Map.of("protocol", 1, "enabled", true, "senderSha256", hash(files.get(ROOT.resolve("sender.json"))),
                "recipients", Map.of("receiver", hash(files.get(ROOT.resolve("recipients/receiver.json")))),
                "certificates", Map.of("receiver-key", hash(files.get(ROOT.resolve("certificates/receiver-key.der")))),
                "auditPlans", Map.of("approved", hash(files.get(ROOT.resolve("audit/approved.json"))))));
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private CdsExportCommand command() { return command(CdsLogicalProfile.ONTARIO_MD_5_2, "12"); }
    private CdsExportCommand command(CdsLogicalProfile profile, String provider) {
        return new CdsExportCommand("policy-fixture", provider, null, profile, CdsExportCommand.Selection.SELECTED_PATIENTS, List.of(), List.of(27), Set.of(CdsCategory.DEMOGRAPHICS), "receiver", "approved");
    }
    private CdsExportDisclosurePolicyService service() { return new CdsExportDisclosurePolicyService(settings, (path, maximum) -> {
        byte[] bytes = files.get(path); if (bytes == null) throw new IOException("UNAVAILABLE"); return bytes;
    }); }
    private CdsExportDisclosurePolicyService.Resolved resolve() { return service().resolve(command(), CdsExportRecipientFixture.NOW); }
    private CdsExportJobDocumentDto accepted() {
        var policy = resolve(); return new CdsExportJobDocumentDto(1, new CdsExportActorDto("agent", "12", null, null), command(), "a".repeat(64), policy.senderNamespace(), policy.recipientFingerprint(), policy.auditPlanDigest());
    }
    @Test void shouldResolveOnlyTheApprovedRegisteredArrangement_withTheExactRecipientCertificate() {
        var result = resolve(); assertThat(result.senderNamespace()).isEqualTo("owned-fixture"); assertThat(result.certificate()).isEqualTo(certificate.certificate);
        assertThat(result.recipientFingerprint()).isEqualTo(certificate.fingerprint); assertThat(result.auditPlanDigest()).matches("[0-9a-f]{64}");
        assertThat(result.toString()).doesNotContain("Clinic", "fixture@example", "approved audit");
    }
    @Test void shouldAcceptEitherExplicitlyApprovedProfile_withoutEquatingRecognitionWithEngineQualification() {
        assertThat(service().resolve(command(CdsLogicalProfile.PCON_2024_02, "12"), CdsExportRecipientFixture.NOW).certificate()).isEqualTo(certificate.certificate);
    }
    @Test void shouldRequireTheActualProviderAndRecipientPair_beforeAcceptingAuditAccess() throws Exception {
        assertThatThrownBy(() -> service().resolve(command(CdsLogicalProfile.ONTARIO_MD_5_2, "13"), CdsExportRecipientFixture.NOW)).hasMessage("CDS_EXPORT_AUDIT_ACCESS_UNAVAILABLE");
        change("audit/approved.json", "recipientIds", List.of("someone-else")); manifest(); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_AUDIT_ACCESS_UNAVAILABLE");
    }
    @Test void shouldRefuseARevokedRecipient_evenWhenItsCertificateIsStillValid() throws Exception {
        change("recipients/receiver.json", "enabled", false); manifest(); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_RECIPIENT_UNAVAILABLE");
    }
    @Test void shouldRefuseARevokedAuditPlan_withoutReturningItsOldDescription() throws Exception {
        change("audit/approved.json", "enabled", false); manifest(); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_AUDIT_ACCESS_UNAVAILABLE");
    }
    @Test void shouldRejectUnknownAndDuplicateJsonProperties_insteadOfGuessingWhichApprovalWasIntended() throws Exception {
        change("sender.json", "unexpected", "ignored?"); manifest(); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_AUDIT_ACCESS_UNAVAILABLE");
        setup(); files.put(ROOT.resolve("sender.json"), "{\"protocol\":1,\"protocol\":2}".getBytes(StandardCharsets.UTF_8)); manifest(); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_AUDIT_ACCESS_UNAVAILABLE");
    }
    @Test void shouldRejectTrailingJsonDocuments_beforeInterpretingAValidFirstApproval() throws Exception {
        var first = files.get(ROOT.resolve("audit/approved.json")); files.put(ROOT.resolve("audit/approved.json"), (new String(first, StandardCharsets.UTF_8) + " {}").getBytes(StandardCharsets.UTF_8)); manifest();
        assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_AUDIT_ACCESS_UNAVAILABLE");
    }
    @Test void shouldRefuseMismatchedRegistrationIds_andCertificatePathTraversal() throws Exception {
        change("recipients/receiver.json", "id", "different"); manifest(); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_RECIPIENT_UNAVAILABLE");
        setup(); change("recipients/receiver.json", "certificateId", "../other"); manifest(); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_RECIPIENT_UNAVAILABLE");
    }
    @Test void shouldRejectCertificatePinChanges_beforeAnyJobCanUseANewKey() throws Exception {
        change("recipients/receiver.json", "certificateSha256", "a".repeat(64)); manifest(); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_RECIPIENT_UNAVAILABLE");
    }
    @Test void shouldRejectAppendedCertificateBytes_evenWhenTheirWholeFilePinWasUpdated() throws Exception {
        byte[] original = certificate.certificate.getEncoded(), appended = Arrays.copyOf(original, original.length + 1); files.put(ROOT.resolve("certificates/receiver-key.der"), appended);
        change("recipients/receiver.json", "certificateSha256", hash(appended)); manifest(); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_RECIPIENT_UNAVAILABLE");
    }
    @Test void shouldRecheckCertificateExpiry_whenAnAcceptedJobIsResumed() {
        var accepted = accepted(); assertThatThrownBy(() -> service().resolveAccepted(accepted, CdsExportRecipientFixture.NOW.plusSeconds(86401))).hasMessage("CDS_EXPORT_RECIPIENT_UNAVAILABLE");
    }
    @Test void shouldBindSenderAndAuditText_toTheAcceptedArrangementDigest() throws Exception {
        var firstAcceptance = accepted(); change("sender.json", "groupContactInformation", "new@example.test"); manifest();
        assertThatThrownBy(() -> service().resolveAccepted(firstAcceptance, CdsExportRecipientFixture.NOW)).hasMessage("CDS_EXPORT_SOURCE_CHANGED");
        setup(); var accepted = accepted(); change("audit/approved.json", "description", "New terms"); manifest(); var original = accepted;
        assertThatThrownBy(() -> service().resolveAccepted(original, CdsExportRecipientFixture.NOW)).hasMessage("CDS_EXPORT_SOURCE_CHANGED");
    }
    @Test void shouldRefuseAnUnapprovedFileRevision_evenWhenTheIndividualDocumentIsWellFormed() throws Exception {
        change("audit/approved.json", "description", "Unapproved terms"); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_AUDIT_ACCESS_UNAVAILABLE");
    }
    @Test void shouldRefuseAChangedManifest_duringTheSameResolution() {
        var reads = new AtomicInteger(); var service = new CdsExportDisclosurePolicyService(settings, (path, maximum) -> {
            byte[] bytes = files.get(path); if (path.equals(ROOT.resolve("policy.json")) && reads.incrementAndGet() > 1) return "{}".getBytes(StandardCharsets.UTF_8); return bytes;
        });
        assertThatThrownBy(() -> service.resolve(command(), CdsExportRecipientFixture.NOW)).hasMessage("CDS_EXPORT_SOURCE_CHANGED");
    }
    @Test void shouldRefuseUnavailableFiles_andMissingPolicyConfiguration() {
        files.remove(ROOT.resolve("certificates/receiver-key.der")); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_RECIPIENT_UNAVAILABLE");
        settings.clear(); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_AUDIT_ACCESS_UNAVAILABLE");
    }
    @Test void shouldRefuseOversizedPolicyBytes_evenIfAnAlternateReaderIgnoredItsLimit() {
        files.put(ROOT.resolve("policy.json"), new byte[32769]); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_AUDIT_ACCESS_UNAVAILABLE");
    }
    @Test void shouldRejectUnknownProfilesAndDuplicateApprovals_withoutBroadeningTheRequestedScope() throws Exception {
        change("recipients/receiver.json", "profiles", List.of("future-profile")); manifest(); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_RECIPIENT_UNAVAILABLE");
        setup(); change("audit/approved.json", "providerNos", List.of("12", "12")); manifest(); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_AUDIT_ACCESS_UNAVAILABLE");
    }
    @Test void shouldAvoidPolicyReadsDuringConstruction_forMetadataOnlyRecovery() {
        var calls = new AtomicInteger(); new CdsExportDisclosurePolicyService(new Properties(), (path, maximum) -> { calls.incrementAndGet(); throw new IOException(); }); assertThat(calls.get()).isZero();
    }
    @Test void shouldRejectNumericProviderIdentifiers_withoutCoercingThemIntoApprovedStrings() throws Exception {
        change("audit/approved.json", "providerNos", List.of(12)); manifest(); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_AUDIT_ACCESS_UNAVAILABLE");
    }
    @Test void shouldRejectMalformedUnicodeMetadata_beforeProducingSupportingFileValues() throws Exception {
        change("sender.json", "physicianGroup", "\ud800"); manifest(); assertThatThrownBy(this::resolve).hasMessage("CDS_EXPORT_AUDIT_ACCESS_UNAVAILABLE");
    }

}
