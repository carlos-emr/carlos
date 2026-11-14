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

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportProtectedFiles;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException.Reason;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Root-controlled sender registration, recipient certificate and approved audit arrangement.
 * All policy bytes enter the accepted audit-plan digest. Rotation or revocation requires a
 * new acceptance; an existing job cannot silently adopt replacement disclosure terms.
 * Construction performs no file I/O, so metadata recovery remains available without a policy.
 */
@Service
public class CdsExportDisclosurePolicyService {
    private static final int MAXIMUM_POLICY_BYTES = 32768;
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    static {
        // ALLOW_COERCION_OF_SCALARS does not cover numeric/boolean values assigned to String.
        JSON.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        JSON.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(8).maxStringLength(16384).maxNumberLength(16).build());
    }
    @FunctionalInterface interface PolicyFiles { byte[] read(Path path, int maximum) throws IOException; }
    private final Properties settings = new Properties();
    private final PolicyFiles files;

    @Autowired public CdsExportDisclosurePolicyService(@Qualifier("cdsExportProperties") Properties supplied) {
        this(supplied, CdsExportProtectedFiles::readRootFile);
    }
    CdsExportDisclosurePolicyService(Properties supplied, PolicyFiles files) {
        settings.putAll(Objects.requireNonNull(supplied)); this.files = Objects.requireNonNull(files);
    }
    private record ManifestDocument(int protocol, boolean enabled, String senderSha256, Map<String, String> recipients, Map<String, String> certificates, Map<String, String> auditPlans) { }
    private record SenderDocument(int protocol, boolean enabled, String namespace, String physicianGroup, String groupContactInformation) { }
    private record RecipientDocument(int protocol, boolean enabled, String id, String certificateId, String certificateSha256, List<String> profiles) { }
    private record AuditDocument(int protocol, boolean enabled, String id, String description, List<String> recipientIds,
            List<String> providerNos, List<String> profiles) { }
    public record Resolved(String senderNamespace, String physicianGroup, String groupContactInformation,
            String recipientFingerprint, X509Certificate certificate, String auditPlanDigest, String auditAccessDescription) {
        @Override public String toString() { return "CdsExportDisclosurePolicyService.Resolved[protected]"; }
    }

    /** Resolves only registered opaque identifiers; request-supplied paths and certificates are never inputs. */
    public Resolved resolve(CdsExportCommand command, Instant now) {
        Objects.requireNonNull(command); Objects.requireNonNull(now); Path root;
        try { root = CdsExportProtectedFiles.absolute(settings.getProperty("cds.export.policyRoot", "")); }
        catch (IOException invalid) { throw new CdsExportReviewException(Reason.AUDIT_ACCESS_UNAVAILABLE); }
        byte[] manifestBytes = read(root.resolve("policy.json"), MAXIMUM_POLICY_BYTES, Reason.AUDIT_ACCESS_UNAVAILABLE);
        var manifest = parse(manifestBytes, ManifestDocument.class, Reason.AUDIT_ACCESS_UNAVAILABLE);
        if (manifest.protocol() != 1 || !manifest.enabled() || !hash(manifest.senderSha256()) || !pins(manifest.recipients(), 64)
                || !pins(manifest.certificates(), 64) || !pins(manifest.auditPlans(), 100)) unavailable(Reason.AUDIT_ACCESS_UNAVAILABLE);
        byte[] senderBytes = read(root.resolve("sender.json"), MAXIMUM_POLICY_BYTES, Reason.AUDIT_ACCESS_UNAVAILABLE);
        requirePin(senderBytes, manifest.senderSha256(), Reason.AUDIT_ACCESS_UNAVAILABLE);
        var sender = parse(senderBytes, SenderDocument.class, Reason.AUDIT_ACCESS_UNAVAILABLE);
        if (sender.protocol() != 1 || !sender.enabled() || !namespace(sender.namespace())
                || !text(sender.physicianGroup(), 512) || !text(sender.groupContactInformation(), 4096)) unavailable(Reason.AUDIT_ACCESS_UNAVAILABLE);
        byte[] recipientBytes = read(root.resolve("recipients").resolve(command.recipientId() + ".json"), MAXIMUM_POLICY_BYTES, Reason.RECIPIENT_UNAVAILABLE);
        requirePin(recipientBytes, manifest.recipients().get(command.recipientId()), Reason.RECIPIENT_UNAVAILABLE);
        var recipient = parse(recipientBytes, RecipientDocument.class, Reason.RECIPIENT_UNAVAILABLE);
        if (recipient.protocol() != 1 || !recipient.enabled() || !command.recipientId().equals(recipient.id()) || !identifier(recipient.certificateId())
                || !hash(recipient.certificateSha256()) || !profiles(recipient.profiles(), command.profile())) unavailable(Reason.RECIPIENT_UNAVAILABLE);
        byte[] certificateBytes = read(root.resolve("certificates").resolve(recipient.certificateId() + ".der"), MAXIMUM_POLICY_BYTES, Reason.RECIPIENT_UNAVAILABLE);
        requirePin(certificateBytes, manifest.certificates().get(recipient.certificateId()), Reason.RECIPIENT_UNAVAILABLE);
        X509Certificate certificate;
        try {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(certificateBytes));
            if (!MessageDigest.isEqual(certificateBytes, certificate.getEncoded())) unavailable(Reason.RECIPIENT_UNAVAILABLE);
            CdsExportDeliveryComposer.validateRecipient(certificate, recipient.certificateSha256(), now);
        } catch (CertificateException | IOException invalid) { throw new CdsExportReviewException(Reason.RECIPIENT_UNAVAILABLE); }
        byte[] auditBytes = read(root.resolve("audit").resolve(command.auditPlanId() + ".json"), MAXIMUM_POLICY_BYTES, Reason.AUDIT_ACCESS_UNAVAILABLE);
        requirePin(auditBytes, manifest.auditPlans().get(command.auditPlanId()), Reason.AUDIT_ACCESS_UNAVAILABLE);
        var audit = parse(auditBytes, AuditDocument.class, Reason.AUDIT_ACCESS_UNAVAILABLE);
        if (audit.protocol() != 1 || !audit.enabled() || !command.auditPlanId().equals(audit.id()) || !text(audit.description(), 4096)
                || !identifiers(audit.recipientIds()) || !audit.recipientIds().contains(command.recipientId())
                || !providers(audit.providerNos()) || !audit.providerNos().contains(command.providerNo()) || !profiles(audit.profiles(), command.profile())) unavailable(Reason.AUDIT_ACCESS_UNAVAILABLE);
        if (!MessageDigest.isEqual(manifestBytes, read(root.resolve("policy.json"), MAXIMUM_POLICY_BYTES, Reason.AUDIT_ACCESS_UNAVAILABLE))) unavailable(Reason.SOURCE_CHANGED);
        String digest = arrangementDigest(manifestBytes, senderBytes, recipientBytes, certificateBytes, auditBytes);
        return new Resolved(sender.namespace(), sender.physicianGroup(), sender.groupContactInformation(), recipient.certificateSha256(), certificate, digest, audit.description());
    }

    /** Rechecks expiry, revocation and exact accepted policy bytes before capture, encryption and publication. */
    public Resolved resolveAccepted(CdsExportJobDocumentDto accepted, Instant now) {
        Objects.requireNonNull(accepted); var current = resolve(accepted.command(), now);
        if (!Objects.equals(current.senderNamespace(), accepted.senderNamespace()) || !Objects.equals(current.recipientFingerprint(), accepted.recipientFingerprint())
                || !Objects.equals(current.auditPlanDigest(), accepted.auditPlanDigest())) throw new CdsExportReviewException(Reason.SOURCE_CHANGED);
        return current;
    }
    private byte[] read(Path path, int maximum, Reason reason) {
        try {
            byte[] value = files.read(path, maximum); if (value == null || value.length == 0 || value.length > maximum) unavailable(reason); return value;
        } catch (IOException invalid) { throw new CdsExportReviewException(reason); }
    }
    private static <T> T parse(byte[] bytes, Class<T> type, Reason reason) {
        try { T result = JSON.readValue(bytes, type); if (result == null) unavailable(reason); return result; }
        catch (IOException invalid) { throw new CdsExportReviewException(reason); }
    }
    private static boolean identifier(String value) { return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"); }
    private static boolean namespace(String value) { return value != null && value.matches("[A-Za-z0-9_][A-Za-z0-9_.:-]{0,127}"); }
    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean text(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum && CdsExportNativeFieldAssessments.legal(value, maximum) && value.codePoints().noneMatch(Character::isISOControl);
    }
    private static boolean identifiers(List<String> values) {
        return values != null && !values.isEmpty() && values.size() <= 64 && values.stream().allMatch(CdsExportDisclosurePolicyService::identifier)
                && new HashSet<>(values).size() == values.size();
    }
    private static boolean providers(List<String> values) {
        return values != null && !values.isEmpty() && values.size() <= 100 && values.stream().allMatch(value -> value != null && value.matches("[A-Za-z0-9_-]{1,16}"))
                && new HashSet<>(values).size() == values.size();
    }
    private static boolean profiles(List<String> values, CdsLogicalProfile selected) {
        if (values == null || values.isEmpty() || values.size() > CdsLogicalProfile.values().length || new HashSet<>(values).size() != values.size()) return false;
        var known = Arrays.stream(CdsLogicalProfile.values()).map(CdsLogicalProfile::identifier).collect(java.util.stream.Collectors.toSet());
        return known.containsAll(values) && values.contains(selected.identifier());
    }
    private static boolean pins(Map<String, String> pins, int maximum) {
        return pins != null && !pins.isEmpty() && pins.size() <= maximum && pins.entrySet().stream().allMatch(entry -> identifier(entry.getKey()) && hash(entry.getValue()));
    }
    private static void requirePin(byte[] bytes, String expected, Reason reason) {
        try {
            if (!hash(expected) || !MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(bytes), HexFormat.of().parseHex(expected))) unavailable(reason);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String arrangementDigest(byte[]... documents) {
        try {
            var digest = MessageDigest.getInstance("SHA-256"); digest.update("CARLOS-CDS-DISCLOSURE-ARRANGEMENT-1\n".getBytes(StandardCharsets.US_ASCII));
            for (byte[] document : documents) { digest.update(ByteBuffer.allocate(8).putLong(document.length).array()); digest.update(document); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void unavailable(Reason reason) { throw new CdsExportReviewException(reason); }
}
