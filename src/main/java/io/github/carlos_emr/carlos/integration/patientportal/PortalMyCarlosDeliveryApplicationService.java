/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.email.core.EmailComposeWorkingDirectory;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Current;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.DeviceKeys;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.DocumentIdCalculator;
import io.github.carlos_emr.carlos.utility.DocumentManifest;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFSigningConfig;
import io.github.carlos_emr.carlos.utility.PDFSigningUtil;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Staff application path for one eDoc and one locally accepted device per synthetic milestone send. */
@Service
public final class PortalMyCarlosDeliveryApplicationService {
    public static final String OBJECT = "_mycarlos_delivery";
    public static final String ENABLED = "patient_portal.mycarlos.sender.enabled";
    private final PortalMyCarlosDeviceApprovalTransactions transactions;
    private final EntityManagerFactory factory;
    private final SecurityInfoManager security;
    private final PropertyDao properties;
    private final ClinicDAO clinics;
    private final CtlDocumentDao ownership;
    private final DocumentDao documents;
    private final DocumentAttachmentManager renderer;
    private final DemographicManager demographics;
    private final Clock clock = Clock.systemUTC();

    public PortalMyCarlosDeliveryApplicationService(PortalMyCarlosDeviceApprovalTransactions transactions,
            EntityManagerFactory factory, SecurityInfoManager security, PropertyDao properties, ClinicDAO clinics,
            CtlDocumentDao ownership, @Qualifier("documentDao") DocumentDao documents,
            DocumentAttachmentManager renderer, DemographicManager demographics) {
        this.transactions = Objects.requireNonNull(transactions); this.factory = Objects.requireNonNull(factory);
        this.security = Objects.requireNonNull(security); this.properties = Objects.requireNonNull(properties);
        this.clinics = Objects.requireNonNull(clinics); this.ownership = Objects.requireNonNull(ownership);
        this.documents = Objects.requireNonNull(documents); this.renderer = Objects.requireNonNull(renderer);
        this.demographics = Objects.requireNonNull(demographics);
    }

    public record Device(String deviceKeyId, String fingerprint, Instant acceptedAt) {
        public String getDeviceKeyId() { return deviceKeyId; }
        public String getFingerprint() { return fingerprint; }
        public Instant getAcceptedAt() { return acceptedAt; }
        @Override public String toString() { return "DeliveryDevice[REDACTED]"; }
    }
    public record Delivery(String deliveryId, String deviceKeyId, Instant queuedAt, String status) {
        public String getDeliveryId() { return deliveryId; }
        public String getDeviceKeyId() { return deviceKeyId; }
        public Instant getQueuedAt() { return queuedAt; }
        public String getStatus() { return status; }
        @Override public String toString() { return "StaffDelivery[REDACTED]"; }
    }
    public record State(String title, List<Device> devices, List<Delivery> deliveries, String nextCursor, String syncStatus, String serviceDate) {
        public State(String title, List<Device> devices, List<Delivery> deliveries, String nextCursor, String syncStatus) {
            this(title, devices, deliveries, nextCursor, syncStatus, null);
        }
        public State { devices = List.copyOf(devices); deliveries = List.copyOf(deliveries); }
        @Override public String toString() { return "StaffDeliveryState[REDACTED]"; }
    }
    private record Runtime(Current current, PortalMyCarlosDeliveryOutboxDao outbox, PortalMyCarlosAcceptedDeviceDao devices,
            PortalMyCarlosPatientBindingDao bindings, PortalMyCarlosDeliveryOutboxService sender,
            PortalMyCarlosDeliverySyncService sync) {}
    private record Target(PortalMyCarlosPatientBinding binding, PortalMyCarlosAcceptedDevice accepted,
                          PatientPortalClinicKeysPublishRequest.Key signer) {}

    public static boolean isEnabled() {
        String value = PatientPortalSettings.rawProperty(ENABLED);
        if (value == null || value.isBlank() || "false".equals(value)) return false;
        if (!"true".equals(value)) throw new PatientPortalConfigurationException(ENABLED + " must be true or false");
        return PortalMyCarlosStaffApprovalService.isEnabled();
    }
    private Current current() {
        if (!isEnabled()) throw unavailable();
        String publicBase = PortalMyCarlosStaffApprovalService.canonicalDemoPublicBase(
                PatientPortalSettings.rawProperty(PortalInviteSettings.PUBLIC_BASE_URL_KEY));
        return Current.fromValidated(PatientPortalSettings.fromCarlosProperties(), publicBase, true, true);
    }
    public void requireStaff(LoggedInInfo info, int patient) {
        if (patient <= 0 || info == null || info.getLoggedInSecurity() == null || info.getLoggedInSecurity().getSecurityNo() == null
                || info.getLoggedInSecurity().getSecurityNo() <= 0 || info.getLoggedInProviderNo() == null
                || !security.hasPrivilege(info, "_demographic", SecurityInfoManager.READ, Integer.toString(patient))
                || !security.isAllowedAccessToPatientRecord(info, patient)
                || !security.hasPrivilege(info, OBJECT, SecurityInfoManager.WRITE, Integer.toString(patient)))
            throw new SecurityException("missing required sec object (_mycarlos_delivery)");
    }

    private Runtime runtime(PatientPortalService portal) {
        Current cfg = current();
        if (portal == null) throw unavailable();
        var signer = PortalJobAssertionSigner.from(PortalJobKey.fromCarlosProperties(PortalJob.DELIVERY_SYNC).orElseThrow(PortalMyCarlosDeliveryApplicationService::unavailable));
        var client = new PortalMyCarlosDeliveryClient(portal, signer);
        if (!client.configuredFor(cfg.clinic, cfg.internalOrigin)) throw unavailable();
        var cipher = PortalMyCarlosDeliveryKeyring.load(PatientPortalSettings.rawProperty(PortalMyCarlosDeliveryKeyring.PROPERTY));
        var outbox = new PortalMyCarlosDeliveryOutboxDao(factory);
        var devices = new PortalMyCarlosAcceptedDeviceDao(factory); var bindings = new PortalMyCarlosPatientBindingDao(factory);
        var sender = new PortalMyCarlosDeliveryOutboxService(transactions, bindings, devices, outbox, cipher, this::current,
                portal, client, security, clock);
        return new Runtime(cfg, outbox, devices, bindings, sender,
                new PortalMyCarlosDeliverySyncService(transactions, new PortalMyCarlosDeliveryEventDao(factory), outbox,
                        sender, devices, bindings, client, cipher, this::current, clock, properties));
    }

    private Document document(LoggedInInfo info, int patient, int documentId) {
        if (documentId <= 0 || !security.hasPrivilege(info, "_edoc", SecurityInfoManager.READ, Integer.toString(patient))
                || !security.hasPrivilege(info, "_newCasemgmt.documents", SecurityInfoManager.READ, null)
                || !ownership.findDocumentNosForDemographic(patient, List.of(documentId)).contains(documentId))
            throw new SecurityException("missing required sec object (_edoc)");
        Document document = documents.find(documentId);
        if (document == null || document.getStatus() == 'D') throw unavailable();
        return document;
    }
    private static String sourceId(int documentId) {
        return DocumentIdCalculator.fromCarlosProperties().orElseThrow(PortalMyCarlosDeliveryApplicationService::unavailable)
                .documentId("document", Integer.toString(documentId));
    }

    /** Read paths perform no upload or portal collection. */
    public State read(LoggedInInfo info, int patient, int documentId, long before, PatientPortalService portal) {
        requireStaff(info, patient);
        var document = document(info, patient, documentId);
        var runtime = runtime(portal);
        String source = sourceId(documentId);
        List<Device> devices = transactions.withApprovalState(() -> {
            var binding = runtime.bindings().findByClinicPatientForUpdate(runtime.current().clinic, patient);
            if (binding.isEmpty()) return List.of();
            requireBinding(runtime.current(), binding.get(), patient);
            var rows = runtime.devices().historyForUpdate(binding.get().id(), 0, 51);
            if (rows.size() > 50) throw unavailable();
            return rows.stream().filter(row -> row.current() && !runtime.outbox().deviceFenced(runtime.current().clinic, row.kid()))
                    .map(row -> new Device(row.kid(), HexFormat.of().formatHex(PortalMyCarlosDeviceApprovalCodec.exact32(row.kid())), row.acceptedAt())).toList();
        });
        var references = transactions.withApprovalState(() -> runtime.outbox().historyReferences(runtime.current().clinic, patient, before, 21));
        List<Delivery> deliveries = new ArrayList<>();
        for (var reference : references.stream().limit(20).toList()) {
            var snapshot = transactions.withApprovalState(() -> runtime.sender().load(runtime.outbox().find(reference.id()).orElseThrow(PortalMyCarlosDeliveryApplicationService::unavailable)));
            if (!source.equals(snapshot.sourceDocumentId())) continue;
            String state = runtime.sender().status(info, patient, snapshot.deliveryId(), snapshot.device().kid()).name().toLowerCase(java.util.Locale.ROOT);
            deliveries.add(new Delivery(snapshot.deliveryId(), snapshot.device().kid(), Instant.ofEpochSecond(snapshot.createdAt()), state));
        }
        String next = references.size() > 20 ? Long.toString(references.get(19).id()) : null;
        return new State(document.getDocdesc(), devices, deliveries, next, null, serviceDate(document));
    }

    public State send(LoggedInInfo info, int patient, int documentId, String deliveryId, String deviceKid,
            PatientPortalService portal) throws IOException {
        requireStaff(info, patient);
        PortalMyCarlosDeliveryCodec.uuid(deliveryId, true); PortalMyCarlosDeviceApprovalCodec.exact32(deviceKid);
        var document = document(info, patient, documentId);
        var runtime = runtime(portal);
        String source = sourceId(documentId);
        var existing = transactions.withApprovalState(() -> runtime.outbox().findForDelivery(runtime.current().clinic, deliveryId));
        long rowId;
        if (!existing.isEmpty()) {
            if (existing.size() != 1) throw unavailable();
            var row = existing.getFirst(); var saved = runtime.sender().load(row);
            if (row.demographicNo() != patient || !deviceKid.equals(row.deviceKid()) || !source.equals(saved.sourceDocumentId())) throw unavailable();
            rowId = row.id(); // Retry the exact already staged bytes, including after a lost result.
        } else {
            Target target = transactions.withSigningInventory(inventory -> {
                var binding = runtime.bindings().findByClinicPatientForUpdate(runtime.current().clinic, patient).orElseThrow(PortalMyCarlosDeliveryApplicationService::unavailable);
                requireBinding(runtime.current(), binding, patient);
                var device = runtime.devices().findByKidForUpdate(deviceKid).orElseThrow(PortalMyCarlosDeliveryApplicationService::unavailable);
                if (!device.current() || device.bindingId() != binding.id() || runtime.outbox().deviceFenced(runtime.current().clinic, deviceKid)) throw unavailable();
                var identity = PortalMyCarlosSigningIdentity.currentPublicKey();
                var catalogue = inventory.orElseThrow(PortalMyCarlosDeliveryApplicationService::unavailable);
                if (!catalogue.clinicId().equals(runtime.current().clinic) || catalogue.publication().keys().stream().noneMatch(
                        key -> key.kid().equals(identity.kid()) && key.spki().equals(identity.spki()) && "active".equals(key.status()))) throw unavailable();
                return new Target(binding, device, identity);
            });
            long created = clock.instant().getEpochSecond();
            if (created < 0 || created > Long.MAX_VALUE - PortalMyCarlosDeliveryCodec.MAX_LIFETIME) throw unavailable();
            DocumentManifest manifest = manifest(info, patient, document, source, target.binding().recipientId(), runtime.current().clinic, created);
            // Validate all frozen wire metadata before rendering or signing the original.
            var details = deliveryDocument(manifest);
            var signing = PDFSigningConfig.fromCarlosProperties();
            try (var working = EmailComposeWorkingDirectory.create()) {
                // Reuse the existing permission-aware renderer and private temp custody, with no email log or SMTP work.
                var rendered = working.adoptGeneratedPdf(renderer.renderDocument(info, DocumentType.DOC, documentId));
                byte[] original = PortalMyCarlosSignedOriginal.sign(rendered, manifest, signing, target.signer(), working);
                var keys = new DeviceKeys(target.accepted().kid(), target.accepted().x25519(), target.accepted().ed25519());
                var copy = PortalMyCarlosDeliveryComposer.composeWithSigner(deliveryId, target.binding().recipientId(), keys, details,
                        original, created, created + PortalMyCarlosDeliveryCodec.MAX_LIFETIME, target.signer(),
                        (payload, kid) -> PDFSigningUtil.signMyCarlosPayload(payload, kid, signing));
                var snapshot = new PortalMyCarlosDeliverySnapshot(runtime.current().clinic, patient, target.binding().recipientId(),
                        runtime.current().publicBase, portal.myCarlosDeliveryTarget(patient).toASCIIString(), hex(runtime.current().hash()),
                        target.accepted().id(), target.accepted().version(), hex(target.accepted().acceptanceHash()), deliveryId,
                        keys, target.signer().kid(), copy.documentSha256(), copy.ciphertextSha256(), created,
                        copy.expiresAt(), copy.bodySha256(), copy.uploadBody(), source);
                try { rowId = runtime.sender().stage(info, copy, snapshot); }
                catch (PortalMyCarlosDeliveryOutboxService.ImmutableConflict race) {
                    var winner = transactions.withApprovalState(() -> runtime.outbox().find(runtime.current().clinic, deliveryId, deviceKid).orElseThrow(() -> race));
                    var saved = runtime.sender().load(winner);
                    if (winner.demographicNo() != patient || !source.equals(saved.sourceDocumentId())) throw race;
                    rowId = winner.id();
                }
            } catch (io.github.carlos_emr.carlos.utility.PDFGenerationException failure) {
                throw new IOException("myCarlos document could not be rendered");
            }
        }
        runtime.sender().send(rowId);
        return read(info, patient, documentId, 0, portal);
    }

    /** Explicit staff check: retry pending exact bodies, then collect and verify delivery evidence. */
    public State refresh(LoggedInInfo info, int patient, int documentId, PatientPortalService portal) {
        requireStaff(info, patient); document(info, patient, documentId);
        var runtime = runtime(portal); String source = sourceId(documentId);
        var references = transactions.withApprovalState(() -> runtime.outbox().historyReferences(runtime.current().clinic, patient, 0, 20));
        for (var reference : references) {
            var saved = transactions.withApprovalState(() -> runtime.sender().load(runtime.outbox().find(reference.id()).orElseThrow(PortalMyCarlosDeliveryApplicationService::unavailable)));
            if (source.equals(saved.sourceDocumentId())) runtime.sender().send(reference.id());
        }
        String sync = runtime.sync().poll().name().toLowerCase(java.util.Locale.ROOT);
        var state = read(info, patient, documentId, 0, portal);
        return new State(state.title(), state.devices(), state.deliveries(), state.nextCursor(), sync, state.serviceDate());
    }

    /** PDF null and delivery empty string are the explicit v1 representations of an unknown date. */
    static PortalMyCarlosDeliveryComposer.Document deliveryDocument(DocumentManifest manifest) {
        return new PortalMyCarlosDeliveryComposer.Document(manifest.clinicId(), manifest.clinicName(), manifest.providerName(),
                manifest.patientName(), manifest.patientBirthDate(), manifest.documentTitle(), manifest.documentType(),
                manifest.serviceDate() == null ? "" : manifest.serviceDate());
    }

    private static String serviceDate(Document document) {
        if (document.getObservationdate() == null) return null;
        return document.getObservationdate() instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate().toString()
                : Instant.ofEpochMilli(document.getObservationdate().getTime()).atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }

    DocumentManifest manifest(LoggedInInfo info, int patient, Document document, String source,
            String recipient, String clinicId, long created) {
        String date = serviceDate(document);
        var chart = demographics.getDemographic(info, patient); var clinic = clinics.getClinic();
        if (chart == null || clinic == null || info.getLoggedInProvider() == null) throw unavailable();
        String name = required(chart.getLastName()) + ", " + required(chart.getFirstName());
        String birth;
        try { birth = LocalDate.of(Integer.parseInt(chart.getYearOfBirth()), Integer.parseInt(chart.getMonthOfBirth()), Integer.parseInt(chart.getDateOfBirth())).toString(); }
        catch (RuntimeException invalidChartDate) { throw unavailable(); }
        return new DocumentManifest(source, 1, created, clinicId, required(clinic.getClinicName()),
                required(info.getLoggedInProvider().getFormattedName()), recipient, name, birth,
                required(document.getDocdesc()), "document", date);
    }
    private static void requireBinding(Current cfg, PortalMyCarlosPatientBinding b, int patient) {
        if (!b.clinicId().equals(cfg.clinic) || b.demographicNo() != patient || !b.publicBase().equals(cfg.publicBase)
                || !b.internalOrigin().equals(cfg.internalOrigin) || !b.configHash().equals(cfg.hash())) throw unavailable();
    }
    private static String required(String value) { if (value == null || value.isBlank()) throw unavailable(); return value; }
    private static String hex(PortalMyCarlosDeviceApprovalTypes.Bytes bytes) { return HexFormat.of().formatHex(bytes.copy()); }
    private static IllegalStateException unavailable() { return new IllegalStateException("myCarlos delivery is unavailable or changed"); }
}
