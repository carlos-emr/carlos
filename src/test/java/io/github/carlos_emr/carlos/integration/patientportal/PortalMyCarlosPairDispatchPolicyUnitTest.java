/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosPairDispatchPolicy.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Synthetic DATA and reported-observation fixtures; no verifier, DB, signature or send proof. */
@Tag("unit")
class PortalMyCarlosPairDispatchPolicyUnitTest {
    private static final String PAIR = "10000000-0000-4000-8000-000000000001";
    private static final String B = "20000000-0000-4000-8000-000000000002";
    private static final String C = "30000000-0000-4000-8000-000000000003";
    private static final String HB = "1".repeat(64);
    private static final String HC = "2".repeat(64);
    private static final String HP = "3".repeat(64);
    private static final String SOURCE = "4".repeat(64);
    private static final String OTHER_SOURCE = "5".repeat(64);
    private static final String RAW = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

    private static PortalMyCarlosManagedPairContext context() {
        return new PortalMyCarlosManagedPairContext(PAIR, "clinic-1", "recipient_1",
                new PortalMyCarlosManagedPairContext.Member(B, "original-document", HB, 101),
                new PortalMyCarlosManagedPairContext.Member(C, "reading-aid-document", HC, 202), HP,
                new PortalMyCarlosManagedPairContext.Capture(RAW, SOURCE, "selected-signer", "registry-1"));
    }

    private static PortalMyCarlosManagedPairContext altered(PortalMyCarlosManagedPairContext value,
            String clinic, PortalMyCarlosManagedPairContext.Member original,
            PortalMyCarlosManagedPairContext.Member aid, String payload,
            PortalMyCarlosManagedPairContext.Capture capture) {
        return new PortalMyCarlosManagedPairContext(value.pairId(), clinic, value.recipientId(), original, aid, payload, capture);
    }

    /** These MATCH values are fabricated observations, deliberately unable to authorize upload. */
    private static Observations reported(PortalMyCarlosManagedPairContext value, String source, String signer) {
        return new Observations(Classification.MANAGED_READING_AID, value, Report.MATCH, Report.MATCH,
                Report.MATCH, Report.MATCH, Report.MATCH, source, signer);
    }

    private static Assessment managed(Operation operation, PortalMyCarlosManagedPairContext value, Observations observations) {
        return assess(operation, Role.READING_AID, value, observations, RetrySourcePolicy.UNRESOLVED);
    }

    private static void refuses(String json) {
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosManagedPairContext.decode(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void shouldRetainDistinctIdentityDomains_whenCanonicalContextRoundTrips() {
        var value = context();
        assertEquals(value, PortalMyCarlosManagedPairContext.decode(value.encode()));
        assertArrayEquals(value.encode(), PortalMyCarlosManagedPairContext.decode(value.encode()).encode());
        assertNotEquals(value.original().deliveryId(), value.readingAid().deliveryId());
        assertNotEquals(value.original().documentId(), value.readingAid().documentId());
        assertNotEquals(value.capture().rawSourceDocumentId(), value.original().documentId());
    }

    @Test void shouldRefuseContext_withUnknownMissingDuplicateOrDecodedVerificationFlags() {
        String json = new String(context().encode(), StandardCharsets.UTF_8);
        refuses(json.substring(0, json.length()-1) + ",\"verified\":true}");
        refuses(json.replace("\"v\":1", "\"v\":1,\"v\":1"));
        refuses(json.replace("\"payload_sha256\":\"" + HP + "\",", ""));
        refuses(json.replace("\"size\":101", "\"size\":101,\"paired\":true"));
    }

    @Test void shouldRefuseContext_withNoncanonicalWhitespaceNumericTypesVersionsOrTrailingData() {
        String json = new String(context().encode(), StandardCharsets.UTF_8);
        for (String changed : new String[] {" " + json, json + "\n", json + "{}",
                json.replace("\"v\":1", "\"v\":1.0"), json.replace("\"v\":1", "\"v\":2"),
                json.replace("\"size\":101", "\"size\":1.01e2"),
                json.replace("carlos-managed-pair-context", "other-context")}) refuses(changed);
    }

    @Test void shouldRefuseBeforeContextCreation_whenUtf8IsMalformedTooDeepOrOversize() {
        assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosManagedPairContext.decode(new byte[] {(byte)0xc3, 0x28}));
        refuses("{\"nested\":".repeat(9) + "{}" + "}".repeat(9));
        byte[] oversized = new byte[PortalMyCarlosManagedPairContext.MAX_CONTEXT_BYTES + 1];
        Arrays.fill(oversized, (byte)' ');
        assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosManagedPairContext.decode(oversized));
    }

    @Test void shouldRefuseContext_withInvalidIdsDigestsSizesOrAmbiguousMemberIdentities() {
        var value = context();
        assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosManagedPairContext.Member("invalid", "doc", HB, 1));
        assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosManagedPairContext.Member(B, "doc", "A".repeat(64), 1));
        for (long size : new long[] {0, -1, PortalMyCarlosDeliveryCodec.MAX_SEALED_BYTES + 1L}) {
            assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosManagedPairContext.Member(B, "doc", HB, size));
        }
        var sameDelivery = new PortalMyCarlosManagedPairContext.Member(B, "aid", HC, 2);
        var sameDocument = new PortalMyCarlosManagedPairContext.Member(C, value.original().documentId(), HC, 2);
        for (var aid : new PortalMyCarlosManagedPairContext.Member[] {sameDelivery, sameDocument}) {
            assertThrows(IllegalArgumentException.class,
                    () -> altered(value, value.clinicId(), value.original(), aid, HP, value.capture()));
        }
    }

    @Test void shouldRejectControlsInvalidMappingAndBlankProvenance_inLabelAndCaptureBounds() {
        for (String name : new String[] {"", " ", "bad\nlabel", "x".repeat(257)}) {
            assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosManagedPairContext.Member(B, name, HB, 1));
        }
        assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosManagedPairContext.Capture("raw-id", SOURCE, "kid", "registry"));
        assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosManagedPairContext.Capture(RAW, SOURCE, "kid", ""));
        var longest = new PortalMyCarlosManagedPairContext(PAIR, "c".repeat(64), "r".repeat(64),
                new PortalMyCarlosManagedPairContext.Member(B, "b".repeat(256), HB, PortalMyCarlosDeliveryCodec.MAX_SEALED_BYTES),
                new PortalMyCarlosManagedPairContext.Member(C, "c".repeat(256), HC, PortalMyCarlosDeliveryCodec.MAX_SEALED_BYTES), HP,
                new PortalMyCarlosManagedPairContext.Capture(RAW, SOURCE, "k".repeat(256), "v".repeat(256)));
        assertTrue(longest.encode().length <= PortalMyCarlosManagedPairContext.MAX_CONTEXT_BYTES);
        assertEquals(longest, PortalMyCarlosManagedPairContext.decode(longest.encode()));
    }

    @Test void shouldApplyAggregateUtf8ByteLimit_duringConstruction() {
        String wide = "\uD83D\uDE00".repeat(256);
        assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosManagedPairContext(PAIR, "c".repeat(64), "r",
                new PortalMyCarlosManagedPairContext.Member(B, wide, HB, 1),
                new PortalMyCarlosManagedPairContext.Member(C, "\uD83D\uDE80".repeat(256), HC, 1), HP,
                new PortalMyCarlosManagedPairContext.Capture(RAW, SOURCE, wide, wide)));
    }

    @Test void shouldPreserveImmutableData_throughSerializationOwnershipAndRedactedDescriptions() {
        var value = context(); byte[] before = value.encode(); byte[] callerCopy = value.encode();
        callerCopy[0] = 0;
        assertArrayEquals(before, value.encode());
        assertEquals("ManagedPairContext[REDACTED]", value.toString());
        assertEquals("ManagedPairMember[REDACTED]", value.original().toString());
        assertEquals("ManagedPairCapture[REDACTED]", value.capture().toString());
    }

    @Test void shouldKeepOwnManifestBoundedRoleSpecificAndImmutable_withoutVerifiedAuthority() {
        String manifest = "AA." + "A".repeat(86); // Explicit synthetic unsigned two-component data.
        var b = new PortalMyCarlosManagedPairContext.Item(PortalMyCarlosManagedPairContext.MemberRole.ORIGINAL, context(), manifest);
        var c = new PortalMyCarlosManagedPairContext.Item(PortalMyCarlosManagedPairContext.MemberRole.READING_AID, context(), manifest);
        assertEquals(context().original(), b.member());
        assertEquals(context().readingAid(), c.member());
        assertEquals(b, PortalMyCarlosManagedPairContext.Item.decode(b.encode()));
        assertEquals(c, PortalMyCarlosManagedPairContext.Item.decode(c.encode()));
        assertThrows(IllegalArgumentException.class, () -> b.requireSameIntent(c));
        var changedManifest = new PortalMyCarlosManagedPairContext.Item(b.role(), b.core(), "AAA." + "A".repeat(86));
        assertThrows(IllegalArgumentException.class, () -> b.requireSameIntent(changedManifest));
        String json = new String(c.encode(), StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosManagedPairContext.Item.decode(json.replace("\"role\":\"reading_aid\"", "\"role\":\"unknown\"").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosManagedPairContext.Item.decode((json.substring(0, json.length()-1) + ",\"verified\":true}").getBytes(StandardCharsets.UTF_8)));
        assertEquals(Disposition.HOLD, managed(Operation.CLAIM, c.core(), null).disposition());
    }

    @Test void shouldAcceptLargestFormatCompatibleManifest_whileRefusingOverLimitOrJwtForms() {
        //64-byte signature takes86base64url chars.8192total is impossible because the payload
        // would have base64 length1mod4;8191 is the largest valid length within the8192bound.
        String largest = "A".repeat(8104) + "." + "A".repeat(86);
        assertEquals(8191, largest.length());
        var item = new PortalMyCarlosManagedPairContext.Item(PortalMyCarlosManagedPairContext.MemberRole.READING_AID, context(), largest);
        assertEquals(item, PortalMyCarlosManagedPairContext.Item.decode(item.encode()));
        for (String bad : new String[] {"A".repeat(8106) + "." + "A".repeat(86), "AA.AA.AA", "AA.AA", "AA+." + "A".repeat(86)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new PortalMyCarlosManagedPairContext.Item(PortalMyCarlosManagedPairContext.MemberRole.READING_AID, context(), bad));
        }
    }

    @Test void shouldRejectRetryAsSameIntent_whenScopePayloadPdfIdentityOrCaptureChanges() {
        var value = context(); value.requireSameIntent(PortalMyCarlosManagedPairContext.decode(value.encode()));
        var changedPdf = new PortalMyCarlosManagedPairContext.Member(B, value.original().documentId(), HC, value.original().size());
        var changedCapture = new PortalMyCarlosManagedPairContext.Capture(RAW, OTHER_SOURCE, "selected-signer", "registry-1");
        for (var attempted : new PortalMyCarlosManagedPairContext[] {
                altered(value, "other-clinic", value.original(), value.readingAid(), HP, value.capture()),
                altered(value, value.clinicId(), changedPdf, value.readingAid(), HP, value.capture()),
                altered(value, value.clinicId(), value.original(), value.readingAid(), HB, value.capture()),
                altered(value, value.clinicId(), value.original(), value.readingAid(), HP, changedCapture),
                altered(value, value.clinicId(), value.readingAid(), value.original(), HP, value.capture())}) {
            assertThrows(IllegalArgumentException.class, () -> value.requireSameIntent(attempted));
        }
    }

    @Test void shouldNotFallBackToOrdinary_whenClassificationIsMissingOrUnknown() {
        for (Operation operation : Operation.values()) {
            if (operation != Operation.HISTORICAL_RECONCILIATION) {
                assertEquals(Reason.CLASSIFICATION_UNAVAILABLE, managed(operation, context(), null).reason());
            }
        }
    }

    @Test void shouldOnlyDeferOrdinaryRoute_andRefuseItForReadingAidRole() {
        var ordinary = new Observations(Classification.ORDINARY_ORIGINAL, null, Report.UNAVAILABLE, Report.UNAVAILABLE,
                Report.UNAVAILABLE, Report.UNAVAILABLE, Report.UNAVAILABLE, null, null);
        assertEquals(Disposition.DEFER_TO_EXISTING_ORDINARY_PATH,
                assess(Operation.CLAIM, Role.ORIGINAL, null, ordinary, null).disposition());
        assertEquals(Reason.CLASSIFICATION_CONFLICT, managed(Operation.CLAIM, context(), ordinary).reason());
        assertEquals(Reason.CLASSIFICATION_CONFLICT,
                assess(Operation.CLAIM, Role.ORIGINAL, context(), reported(context(), SOURCE, "selected-signer"), null).reason());
    }

    @Test void shouldRequireContextForReadingAid_andRefuseASwappedRetainedPair() {
        var value = context();
        var missing = reported(null, SOURCE, "selected-signer");
        assertEquals(Reason.CONTEXT_UNAVAILABLE, managed(Operation.STAGE, value, missing).reason());
        var different = altered(value, "other-clinic", value.original(), value.readingAid(), HP, value.capture());
        assertEquals(Reason.CONTEXT_CONFLICT, managed(Operation.STAGE, value, reported(different, SOURCE, "selected-signer")).reason());
    }

    @Test void shouldHold_whenPairArtifactsApprovalScopeOrCapabilitiesAreUnavailableOrConflicting() {
        var value = context();
        for (Report refusal : new Report[] {Report.UNAVAILABLE, Report.CONFLICT}) {
            for (int index = 0; index < 5; index++) {
                Report[] rows = {Report.MATCH, Report.MATCH, Report.MATCH, Report.MATCH, Report.MATCH}; rows[index] = refusal;
                var observations = new Observations(Classification.MANAGED_READING_AID, value,
                        rows[0], rows[1], rows[2], rows[3], rows[4], SOURCE, "selected-signer");
                assertEquals(Disposition.HOLD, managed(Operation.STAGE, value, observations).disposition());
                assertNotEquals(Reason.PRODUCTION_ADAPTERS_UNAVAILABLE, managed(Operation.STAGE, value, observations).reason());
            }
        }
    }

    @Test void shouldHold_whenPreparationSourceMismatchesOrIsUnobserved() {
        var value = context();
        assertEquals(Reason.SOURCE_EVIDENCE_UNAVAILABLE, managed(Operation.STAGE, value, reported(value, null, "selected-signer")).reason());
        assertEquals(Reason.SOURCE_CHANGED_DURING_PREPARATION, managed(Operation.STAGE, value, reported(value, OTHER_SOURCE, "selected-signer")).reason());
    }

    @Test void shouldHoldWithoutChangingIntent_whenSignerChangesOrIsUnobserved() {
        var value = context(); byte[] before = value.encode();
        assertEquals(Reason.SIGNER_EVIDENCE_UNAVAILABLE, managed(Operation.STAGE, value, reported(value, SOURCE, null)).reason());
        assertEquals(Reason.SIGNER_CHANGED, managed(Operation.STAGE, value, reported(value, SOURCE, "different-signer")).reason());
        assertArrayEquals(before, value.encode());
    }

    @Test void shouldHoldEveryQueuedUploadPath_whenRetrySourcePolicyIsUnresolved() {
        var value = context(); byte[] before = value.encode();
        for (Operation operation : new Operation[] {Operation.CLAIM, Operation.TRANSMIT, Operation.RESUME, Operation.REFRESH_RETRY}) {
            for (String laterSource : new String[] {SOURCE, OTHER_SOURCE, null}) {
                var assessment = managed(operation, value, reported(value, laterSource, "selected-signer"));
                assertEquals(new Assessment(Disposition.HOLD, Reason.RETRY_POLICY_UNRESOLVED), assessment);
            }
        }
        assertArrayEquals(before, value.encode());
    }

    @Test void shouldNotAuthorizeManagedUpload_evenWithFabricatedAllMatchingReports() {
        var value = context();
        assertEquals(new Assessment(Disposition.HOLD, Reason.PRODUCTION_ADAPTERS_UNAVAILABLE),
                managed(Operation.STAGE, value, reported(value, SOURCE, "selected-signer")));
        assertEquals(Disposition.HOLD, assess(Operation.RESUME, Role.READING_AID, value,
                reported(value, SOURCE, "selected-signer"), null).disposition());
    }

    @Test void shouldKeepHistoricalReconciliationSeparate_fromNewUploadAndPreserveIntent() {
        var value = context(); byte[] before = value.encode();
        assertEquals(new Assessment(Disposition.RECONCILIATION_ONLY, Reason.HISTORICAL_ONLY),
                managed(Operation.HISTORICAL_RECONCILIATION, value, null));
        assertEquals(Disposition.HOLD, managed(Operation.REFRESH_RETRY, value, null).disposition());
        assertArrayEquals(before, value.encode());
    }
}
