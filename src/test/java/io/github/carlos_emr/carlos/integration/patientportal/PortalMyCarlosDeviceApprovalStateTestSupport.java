/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPrivateKeySpec;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory DAO state and real owner lifecycle only; never a durable database/HTTP/CSRF proof. */
final class PortalMyCarlosDeviceApprovalStateTestSupport {
    static final ObjectMapper JSON = new ObjectMapper();
    private PortalMyCarlosDeviceApprovalStateTestSupport() {}
    static JsonNode fixture() throws Exception {
        try (var input = PortalMyCarlosDeviceApprovalStateTestSupport.class.getResourceAsStream("/patientportal/device-approval-contract-v1.json")) {
            if (input == null) throw new IllegalStateException("shared fixture missing");
            byte[] bytes = input.readAllBytes();
            if (bytes.length != PortalMyCarlosDeviceApprovalCodecUnitTest.FIXTURE_LENGTH
                    || !PortalMyCarlosDeviceApprovalCrypto.sha256(bytes).equals(PortalMyCarlosDeviceApprovalCodecUnitTest.FIXTURE_SHA256)) {
                throw new IllegalStateException("shared fixture changed");
            }
            return JSON.readTree(bytes);
        }
    }
    static Bytes bytes(String encoded) { return new Bytes(PortalMyCarlosDeviceApprovalCodec.exact32(encoded));
    }
    static String compact(JsonNode flow, String kind) { return flow.path(kind).path("compact").asText();
    }
    static Map<String, Object> claims(JsonNode node) {
        var result = new LinkedHashMap<String, Object>();
        node.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().isNull() ? null
                : e.getValue().isIntegralNumber() ? e.getValue().longValue() : e.getValue().textValue()));
        return result;
    }
    /** Every FAKE seed text in the shared contract fixtures starts so. */
    static final String SEED_PREFIX = "FAKE delivery contract test key: ";

    /**
     * The FAKE private key of a fixture key, derived at run time from its seed text. The fixture keeps
     * seed texts and public values only, never private key bytes (Ben's D120).
     *
     * @param field which key: the private-key field the fixture used to carry
     *        ({@code FAKE_ed25519_private_pkcs8_b64url}, {@code FAKE_x25519_private_pkcs8_b64url} or
     *        {@code FAKE_private_pkcs8_b64url}), so existing callers read unchanged
     * @param algorithm {@code Ed25519}, {@code X25519} or {@code EC}, matching the field
     */
    static PrivateKey privateKey(JsonNode key, String field, String algorithm) throws Exception {
        String seedField = switch (field) {
            case "FAKE_ed25519_private_pkcs8_b64url" -> "ed25519_seed_text";
            case "FAKE_x25519_private_pkcs8_b64url" -> "x25519_seed_text";
            case "FAKE_private_pkcs8_b64url" -> "seed_text";
            default -> throw new IllegalArgumentException("unknown FAKE key field");
        };
        String expected = seedField.equals("seed_text") ? "EC" : seedField.startsWith("ed") ? "Ed25519" : "X25519";
        if (!expected.equals(algorithm)) throw new IllegalArgumentException("FAKE key field and algorithm differ");
        return seedPrivateKey(key.path(seedField).textValue(), algorithm);
    }

    /**
     * The shared rule (carlos-portal tests/delivery_contract_reference.py): key bytes = SHA-256 of the
     * seed text; the Ed25519 seed and the X25519 key are those bytes; the P-256 scalar is their
     * big-endian value mod (n - 1), plus 1.
     */
    static PrivateKey seedPrivateKey(String seedText, String algorithm) throws Exception {
        if (seedText == null || !seedText.startsWith(SEED_PREFIX)) throw new IllegalArgumentException("not a FAKE contract seed text");
        byte[] seed = seedBytes(seedText);
        return switch (algorithm) {
            case "Ed25519" -> KeyFactory.getInstance("Ed25519").generatePrivate(new EdECPrivateKeySpec(NamedParameterSpec.ED25519, seed));
            case "X25519" -> KeyFactory.getInstance("X25519").generatePrivate(new XECPrivateKeySpec(NamedParameterSpec.X25519, seed));
            case "EC" -> {
                AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
                parameters.init(new ECGenParameterSpec("secp256r1"));
                ECParameterSpec curve = parameters.getParameterSpec(ECParameterSpec.class);
                BigInteger scalar = new BigInteger(1, seed).mod(curve.getOrder().subtract(BigInteger.ONE)).add(BigInteger.ONE);
                yield KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(scalar, curve));
            }
            default -> throw new IllegalArgumentException("unknown FAKE key algorithm");
        };
    }

    /** SHA-256 of a seed text: the FAKE key bytes it names. */
    static byte[] seedBytes(String seedText) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(seedText.getBytes(StandardCharsets.UTF_8));
    }
    static String key(Bytes id, String kind) { return PortalMyCarlosDeviceApprovalCodec.base64(id.copy()) + "/" + kind;
    }
    static RuntimeException conflict() { return new IllegalStateException("FAKE actual-owner unit conflict", new SQLException("FAKE", "HY000", 1020));
    }
    static final class MutableClock extends Clock {
        Instant value;
        MutableClock(Instant value) { this.value = value;
        }
        @Override public ZoneId getZone() { return ZoneOffset.UTC;
        }
        @Override public Clock withZone(ZoneId zone) { return this;
        }
        @Override public Instant instant() { return value;
        }
    }
    static final class Fixture {
        final JsonNode data;
        final JsonNode first;
        final JsonNode later;
        final Scope scope;
        final MutableClock clock;
        final PortalMyCarlosPatientBindingDao bindings;
        final PortalMyCarlosDeviceApprovalChallengeDao challenges;
        final PortalMyCarlosAcceptedDeviceDao devices;
        final PortalMyCarlosDeviceApprovalOutboxDao outbox;
        final PropertyDao properties = mock(PropertyDao.class);
        final ProviderLabRoutingDao coordination = mock(ProviderLabRoutingDao.class);
        final PortalMyCarlosInventoryTestSupport.Fixture owner = PortalMyCarlosInventoryTestSupport.fixture(properties, coordination);
        final SecurityInfoManager security = mock(SecurityInfoManager.class);
        final LoggedInInfo session;
        final PortalMyCarlosDeviceApprovalService service;
        final List<PortalMyCarlosDeviceApprovalService.Audit> audits = new ArrayList<>();
        final List<Bytes> attemptedBodies = new ArrayList<>();
        final AtomicInteger signs = new AtomicInteger();
        final Map<Bytes, PortalMyCarlosDeviceApprovalChallenge> challengeRows = new HashMap<>();
        final Map<String, PortalMyCarlosAcceptedDevice> deviceRows = new HashMap<>();
        final Map<String, PortalMyCarlosDeviceApprovalOutboxEntry> outboxRows = new HashMap<>();
        PortalMyCarlosPatientBinding binding;
        PortalMyCarlosPatientBinding savedBinding;
        Map<Bytes, PortalMyCarlosDeviceApprovalChallenge> savedChallenges;
        Map<String, PortalMyCarlosAcceptedDevice> savedDevices;
        Map<String, PortalMyCarlosDeviceApprovalOutboxEntry> savedOutbox;
        boolean write = true;
        boolean patientAccess = true;
        boolean enabled = true;
        boolean trustedSource = true;
        boolean keyActive = true;
        boolean failNextOutbox;
        boolean failAudit;
        Runnable lastLock = () -> {};
        PortalMyCarlosDeviceApprovalService.Current current;
        Bytes nextChallenge;
        RuntimeException signerFailure;
        Fixture() throws Exception {
            bindings = mock(PortalMyCarlosPatientBindingDao.class, withSettings().useConstructor(owner.factory));
            challenges = mock(PortalMyCarlosDeviceApprovalChallengeDao.class, withSettings().useConstructor(owner.factory));
            devices = mock(PortalMyCarlosAcceptedDeviceDao.class, withSettings().useConstructor(owner.factory));
            outbox = mock(PortalMyCarlosDeviceApprovalOutboxDao.class, withSettings().useConstructor(owner.factory));
            data = fixture();
            first = data.path("flows").path("first");
            later = data.path("flows").path("later");
            scope = new Scope(data.path("clinic_id").asText(), data.path("recipient_id").asText(), data.path("public_portal_base_url").asText());
            clock = new MutableClock(Instant.ofEpochSecond(data.path("now").longValue()));
            nextChallenge = bytes(first.path("challenge").path("claims").path("challenge_id").asText());
            var configured = mock(PatientPortalSettings.class);
            when(configured.clinicId()).thenReturn(scope.clinicId());
            when(configured.baseUrl()).thenReturn("https://internal.example.invalid");
            current = PortalMyCarlosDeviceApprovalService.Current.fromValidated(configured, scope.canonicalPublicBase(), true, true);
            var provider = mock(Provider.class);
            when(provider.getProviderNo()).thenReturn("123");
            var identity = new Security();
            identity.setSecurityNo(7);
            session = new LoggedInInfo(null, null, provider, identity, Locale.ENGLISH);
            when(security.hasPrivilege(any(), anyString(), anyString(), anyString())).thenAnswer(a -> !a.getArgument(2).equals(SecurityInfoManager.WRITE) || write);
            when(security.isAllowedAccessToPatientRecord(any(), anyInt())).thenAnswer(a -> patientAccess);
            when(properties.findByNameForUpdate(PortalMyCarlosKeyInventoryService.PROPERTY_NAME)).thenAnswer(a -> {
                var key = data.path("keys").path("clinic");
                var inventory = PortalMyCarlosKeyInventory.create(scope.clinicId(), List.of(new PatientPortalClinicKeysPublishRequest.Key(
                        key.path("kid").asText(), key.path("spki").asText(), keyActive ? "active" : "revoked", null, null)));
                var root = JSON.createObjectNode();
                root.put("schema_version", 1);
                root.set("catalogue", JSON.readTree(inventory.toStoredJson()));
                root.putNull("publication");
                var row = new Property(PortalMyCarlosKeyInventoryService.PROPERTY_NAME);
                row.setValue(root.toString());
                return List.of(row);
            });
            owner.manager.onBegin = this::snapshot;
            owner.manager.onRollback = this::restore;
            when(bindings.findByClinicPatientForUpdate(anyString(), anyInt())).thenAnswer(a -> Optional.ofNullable(binding)
                    .filter(b -> b.clinicId().equals(a.getArgument(0)) && b.demographicNo() == (Integer) a.getArgument(1)));
            when(bindings.findByClinicRecipientForUpdate(anyString(), anyString())).thenAnswer(a -> Optional.ofNullable(binding)
                    .filter(b -> b.clinicId().equals(a.getArgument(0)) && b.recipientId().equals(a.getArgument(1))));
            when(bindings.insert(any())).thenAnswer(a -> {
                var b = (PortalMyCarlosPatientBinding) a.getArgument(0);
                if (binding != null) throw new IllegalStateException("FAKE unique binding conflict");
                binding = new PortalMyCarlosPatientBinding(1, b.clinicId(), b.demographicNo(), b.recipientId(), b.publicBase(), b.internalOrigin(), b.configHash(), b.rootKid(), b.createdAt(), b.version());
                return binding;
            });
            when(challenges.findByChallengeForUpdate(any())).thenAnswer(a -> Optional.ofNullable(challengeRows.get(a.getArgument(0))));
            when(challenges.findByRequestForUpdate(anyString(), any())).thenAnswer(a -> challengeRows.values().stream()
                    .filter(c -> c.clinicId().equals(a.getArgument(0)) && c.requestId().equals(a.getArgument(1))).findFirst());
            when(challenges.findByConsumedJtiForUpdate(anyLong(), any())).thenAnswer(a -> challengeRows.values().stream()
                    .filter(c -> objectsEqual(c.predecessorId(), a.getArgument(0)) && objectsEqual(c.approvalJti(), a.getArgument(1))).findFirst());
            when(challenges.findOpenForPatientForUpdate(anyString(), anyInt())).thenAnswer(a -> challengeRows.values().stream()
                    .filter(c -> c.clinicId().equals(a.getArgument(0)) && c.demographicNo() == (Integer) a.getArgument(1) && c.pending()).toList());
            when(challenges.insert(any())).thenAnswer(a -> { var row = (PortalMyCarlosDeviceApprovalChallenge) a.getArgument(0);
                challengeRows.put(row.challengeId(), row);
                return row;
            });
            doAnswer(a -> { var before = (PortalMyCarlosDeviceApprovalChallenge) a.getArgument(0);
                var after = (PortalMyCarlosDeviceApprovalChallenge) a.getArgument(1);
                if (!before.equals(challengeRows.get(before.challengeId()))) throw new IllegalStateException("FAKE stale challenge");
                challengeRows.put(after.challengeId(), after);
                return null;
            }).when(challenges).terminal(any(), any());
            when(devices.findByKidForUpdate(anyString())).thenAnswer(a -> Optional.ofNullable(deviceRows.get(a.getArgument(0))));
            when(devices.insert(any())).thenAnswer(a -> {
                var d = (PortalMyCarlosAcceptedDevice) a.getArgument(0);
                if (deviceRows.containsKey(d.kid())) throw new IllegalStateException("FAKE duplicate kid");
                var inserted = new PortalMyCarlosAcceptedDevice(deviceRows.size() + 1, d.bindingId(), d.kid(), d.x25519(), d.ed25519(), d.kind(), d.challengeId(), d.providerNo(), d.securityNo(), d.staffMethod(), d.predecessorId(), d.predecessorVersion(), d.predecessorApproval(), d.acceptance(), d.acceptanceHash(), d.acceptedAt(), d.removedAt(), d.removalId(), d.removalReason(), d.removedBy(), d.version());
                deviceRows.put(d.kid(), inserted);
                return inserted;
            });
            doAnswer(a -> { var d = (PortalMyCarlosAcceptedDevice) a.getArgument(1);
                deviceRows.put(d.kid(), d);
                return null;
            }).when(devices).markRemoved(any(), any());
            when(devices.historyForUpdate(anyLong(), anyLong(), anyInt())).thenAnswer(a -> deviceRows.values().stream()
                    .filter(d -> d.bindingId() == (Long) a.getArgument(0) && d.id() > (Long) a.getArgument(1))
                    .sorted(java.util.Comparator.comparingLong(PortalMyCarlosAcceptedDevice::id)).limit((Integer) a.getArgument(2)).toList());
            when(outbox.findMessageForUpdate(any(), anyString())).thenAnswer(a -> { lastLock.run();
                return Optional.ofNullable(outboxRows.get(key((Bytes) a.getArgument(0), (String) a.getArgument(1))));
            });
            when(outbox.insert(any())).thenAnswer(a -> {
                var e = (PortalMyCarlosDeviceApprovalOutboxEntry) a.getArgument(0);
                attemptedBodies.add(e.body());
                outboxRows.put(key(e.challengeId(), e.messageKind()), e);
                if (failNextOutbox) { failNextOutbox = false;
                    throw conflict();
                }
                return e;
            });
            PrivateKey privateKey = privateKey(data.path("keys").path("clinic"), "FAKE_private_pkcs8_b64url", "EC");
            var signer = new PortalMyCarlosDeviceApprovalService.Signer() {
                @Override public PortalMyCarlosDeviceApprovalService.SigningIdentity currentIdentity() {
                    return new PortalMyCarlosDeviceApprovalService.SigningIdentity(data.path("keys").path("clinic").path("kid").asText(), data.path("keys").path("clinic").path("spki").asText());
                }
                @Override public String sign(Kind kind, Map<String, Object> claims, Scope scope, PortalMyCarlosDeviceApprovalService.SigningIdentity identity) {
                    signs.incrementAndGet();
                    if (signerFailure != null) throw signerFailure;
                    return PortalMyCarlosDeviceApprovalCrypto.sign(kind, claims, scope, privateKey);
                }
            };
            var approval = new PortalMyCarlosDeviceApprovalTransactions(owner.transactions, new PortalMyCarlosKeyInventoryWorker(properties));
            service = new PortalMyCarlosDeviceApprovalService(approval, bindings, challenges, devices, outbox, security,
                    () -> enabled ? current : null, p -> { if (!trustedSource) throw new SecurityException("FAKE import denied");
            },
                    signer, () -> nextChallenge, () -> UUID.fromString("30dd3b1a-7440-4acd-bbf1-54a59da72336"), clock,
                    a -> { audits.add(a);
                if (failAudit) throw conflict();
            });
        }
        private static boolean objectsEqual(Object left, Object right) { return java.util.Objects.equals(left, right);
        }
        void snapshot() { savedBinding = binding;
            savedChallenges = new HashMap<>(challengeRows);
            savedDevices = new HashMap<>(deviceRows);
            savedOutbox = new HashMap<>(outboxRows);
        }
        void restore() { binding = savedBinding;
            challengeRows.clear();
            challengeRows.putAll(savedChallenges);
            deviceRows.clear();
            deviceRows.putAll(savedDevices);
            outboxRows.clear();
            outboxRows.putAll(savedOutbox);
        }
        PortalMyCarlosDeviceApprovalChallenge install(JsonNode flow, boolean later) {
            var c = flow.path("challenge").path("claims");
            var i = flow.path("intent").path("claims");
            var row = new PortalMyCarlosDeviceApprovalChallenge(bytes(c.path("challenge_id").asText()), UUID.fromString(c.path("request_id").asText()), later ? 1L : null,
                    scope.clinicId(), 1, scope.recipientId(), scope.canonicalPublicBase(), current.hash(), c.path("candidate_device_key_id").asText(),
                    bytes(c.path("candidate_x25519_public_key").asText()), bytes(c.path("candidate_ed25519_public_key").asText()), compact(flow, "intent"),
                    PortalMyCarlosDeviceApprovalService.Checks.hash(compact(flow, "intent")), compact(flow, "challenge"), PortalMyCarlosDeviceApprovalService.Checks.hash(compact(flow, "challenge")),
                    later ? "accepted_device" : "staff_first", later ? 1L : null, later ? c.path("predecessor_device_key_id").asText() : null,
                    later ? Long.parseLong(c.path("predecessor_version").asText()) : null,
                    Instant.ofEpochSecond(c.path("issued_at").longValue()), Instant.ofEpochSecond(c.path("expires_at").longValue()), null, null, null, null, null, null);
            challengeRows.put(row.challengeId(), row);
            return row;
        }
        PortalMyCarlosDeviceApprovalService.Decision acceptFirst(PortalMyCarlosDeviceApprovalChallenge row) {
            String fingerprint = java.util.HexFormat.of().formatHex(row.candidateKid() == null ? new byte[0] : PortalMyCarlosDeviceApprovalCodec.exact32(row.candidateKid()));
            return service.acceptFirstFromStaff(session, 1, row.challengeId(), row.challengeHash(), fingerprint);
        }
        /** Build a valid prior local root using the product core and fixture FAKE keys, not a new oracle/vector. */
        void seedPredecessor() throws Exception {
            var key = data.path("keys").path("predecessor");
            var values = claims(first.path("intent").path("claims"));
            values.put("request_id", "bb1954ee-579e-4dd3-9c7c-0c6f05dd8acf");
            values.put("candidate_device_key_id", key.path("device_key_id").asText());
            values.put("candidate_x25519_public_key", key.path("x25519_public_key").asText());
            values.put("candidate_ed25519_public_key", key.path("ed25519_public_key").asText());
            String intent = PortalMyCarlosDeviceApprovalCrypto.sign(Kind.INTENT, values, scope, privateKey(key, "FAKE_ed25519_private_pkcs8_b64url", "Ed25519"));
            nextChallenge = new Bytes("R".repeat(32).getBytes(StandardCharsets.US_ASCII));
            var challenge = service.createOrReadExactChallenge(1, intent);
            acceptFirst(challenge);
            signs.set(0);
            audits.clear();
            attemptedBodies.clear();
        }
    }
}
