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
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Current;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The delivery status feed passes over an event about something CARLOS has no record of, once it is
 * too old to belong to an approval request still in progress, and keeps every check for anything
 * CARLOS does know. Before this, one such event (for example after a link's records were removed)
 * held the feed for the whole clinic.
 *
 * <p>The collector's transactions, DAOs and Portal client are mocked here, so this runs in every
 * build; PortalMyCarlosDeliverySyncMariaIntegrationTest covers the same rule on a real database
 * when its owned test credentials are given. FAKE identifiers only.
 *
 * @since 2026-11-10
 */
@Tag("unit")
@Tag("fast")
@DisplayName("myCarlos delivery feed: events CARLOS has no record of")
class PortalMyCarlosDeliverySyncSkipUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CLINIC = "clinic-a";
    private static final long NOW = 1792454400L;
    private static final String KNOWN_RECIPIENT = "rcp-FAKE0001aaaaaaaaaaaa";
    private static final String UNKNOWN_RECIPIENT = "rcp-FAKE0009zzzzzzzzzzzz";
    private static final String KID = "A".repeat(43);
    private static final String DELIVERY = "00000000-0000-4000-8000-0000000000aa";
    private static final String CIPHERTEXT = "c".repeat(64);

    private final PortalMyCarlosDeviceApprovalTransactions transactions = mock(PortalMyCarlosDeviceApprovalTransactions.class);
    private final PortalMyCarlosDeliveryEventDao events = mock(PortalMyCarlosDeliveryEventDao.class);
    private final PortalMyCarlosDeliveryOutboxDao outbox = mock(PortalMyCarlosDeliveryOutboxDao.class);
    private final PortalMyCarlosDeliveryOutboxService sender = mock(PortalMyCarlosDeliveryOutboxService.class);
    private final PortalMyCarlosAcceptedDeviceDao devices = mock(PortalMyCarlosAcceptedDeviceDao.class);
    private final PortalMyCarlosPatientBindingDao bindings = mock(PortalMyCarlosPatientBindingDao.class);
    private final PortalMyCarlosDeliveryClient client = mock(PortalMyCarlosDeliveryClient.class);
    private final PortalMyCarlosDeliveryCipher cipher = mock(PortalMyCarlosDeliveryCipher.class);
    private final PropertyDao properties = mock(PropertyDao.class);

    // The checkpoint row and what the collector wrote, as the DAO would hold them.
    private long committed;
    private long acknowledged;
    private String writerId;
    private Long writerUntil;
    private byte[] page;
    private final List<Property> collectorRows = new ArrayList<>();
    private final List<String> recorded = new ArrayList<>();
    private final List<String> quarantined = new ArrayList<>();
    private final List<Long> acks = new ArrayList<>();
    private final List<String> audited = new ArrayList<>();

    @BeforeEach
    void wireFakeCollectorState() throws Exception {
        when(transactions.withApprovalState(any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
        when(events.checkpoint(CLINIC)).thenAnswer(call ->
                new PortalMyCarlosDeliveryEventDao.Checkpoint(committed, acknowledged, writerId, writerUntil));
        doAnswer(call -> { writerId = call.getArgument(1); writerUntil = call.getArgument(2); return null; })
                .when(events).writer(eq(CLINIC), anyString(), anyLong());
        when(events.keepPage(eq(CLINIC), anyLong(), anyString(), any(), anyLong(), any())).thenReturn(1L);
        doAnswer(call -> {
            PortalMyCarlosDeliveryClient.Event event = call.getArgument(1);
            recorded.add(event.sequence() + ":" + event.kind() + ":" + call.getArgument(3));
            return null;
        }).when(events).event(eq(CLINIC), any(), eq(1L), anyBoolean());
        doAnswer(call -> { quarantined.add(call.getArgument(1)); return null; }).when(events).quarantinePage(eq(1L), anyString());
        doAnswer(call -> {
            assertEquals(committed, (long) call.getArgument(2));
            committed = call.getArgument(3);
            return null;
        }).when(events).commitThrough(eq(CLINIC), anyString(), anyLong(), anyLong());
        doAnswer(call -> { acknowledged = call.getArgument(2); writerId = null; writerUntil = null; return null; })
                .when(events).acknowledged(eq(CLINIC), anyString(), anyLong());
        doAnswer(call -> { writerId = null; writerUntil = null; return null; }).when(events).releaseWriter(eq(CLINIC), anyString());
        when(properties.findByNameForUpdate("mycarlos.delivery.collector.binding.v1")).thenAnswer(call -> List.copyOf(collectorRows));
        doAnswer(call -> { collectorRows.add(call.getArgument(0)); return null; }).when(properties).persist(any(Property.class));
        when(client.configuredFor(eq(CLINIC), any())).thenReturn(true);
        when(client.fetchEvents(anyLong())).thenAnswer(call -> page);
        when(client.acknowledge(anyLong())).thenAnswer(call -> { long through = call.getArgument(0); acks.add(through); return through; });
        when(cipher.seal(any(), any())).thenReturn(new byte[41]);
        when(bindings.findByClinicRecipientForUpdate(eq(CLINIC), anyString())).thenReturn(Optional.empty());
        when(bindings.findByClinicRecipientForUpdate(CLINIC, KNOWN_RECIPIENT))
                .thenReturn(Optional.of(mock(PortalMyCarlosPatientBinding.class)));
        when(devices.findByKidForUpdate(anyString())).thenReturn(Optional.empty());
        when(outbox.find(eq(CLINIC), anyString(), anyString())).thenReturn(Optional.empty());
        when(outbox.findForDelivery(eq(CLINIC), anyString())).thenReturn(List.of());
    }

    private PortalMyCarlosDeliverySyncService collector() {
        var settings = PatientPortalSettings.fromProperties(Map.of(PatientPortalSettings.BASE_URL_KEY, "https://portal.example.test",
                PatientPortalSettings.CLINIC_ID_KEY, CLINIC, PatientPortalSettings.SERVICE_TOKEN_KEY, "FAKE-service-token-only-00000001",
                PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY, PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "FAKE-staff",
                PatientPortalSettings.CERTIFICATE_PINS_KEY, PortalTestKeys.UNUSED_TLS_PIN));
        Current current = Current.fromValidated(settings, "https://portal.example.test", true, true);
        return new PortalMyCarlosDeliverySyncService(transactions, events, outbox, sender, devices, bindings, client, cipher,
                () -> current, Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC), properties,
                (clinic, sequence, kind) -> audited.add(clinic + ":" + sequence + ":" + kind));
    }

    private static ObjectNode envelope(long sequence, String kind, long occurredAt, String recipient) {
        var event = JSON.createObjectNode();
        event.put("sequence", Long.toString(sequence));
        event.put("kind", kind);
        event.put("occurred_at", occurredAt);
        event.put("recipient_id", recipient);
        event.put("device_key_id", KID);
        return event;
    }
    private static ObjectNode deviceRemoved(long sequence, long occurredAt, String recipient) {
        var event = envelope(sequence, "device_removed", occurredAt, recipient);
        event.putNull("delivery_id");
        event.putObject("payload");
        return event;
    }
    private static ObjectNode anchor(long sequence, long occurredAt, String recipient) {
        var event = envelope(sequence, "anchor", occurredAt, recipient);
        event.putNull("delivery_id");
        var payload = event.putObject("payload");
        payload.put("vault_head_link", "a".repeat(64));
        // Well-formed only: a passed-over anchor's proof is never checked.
        payload.put("request_assertion", "e30." + "A".repeat(86));
        return event;
    }
    private static ObjectNode expired(long sequence, long occurredAt, String recipient) {
        var event = envelope(sequence, "expired", occurredAt, recipient);
        event.put("delivery_id", DELIVERY);
        var payload = event.putObject("payload");
        payload.put("ciphertext_sha256", CIPHERTEXT);
        payload.put("size", 4096);
        payload.put("created_at", NOW - 86400);
        payload.put("expires_at", NOW - 86400 + 3600);
        return event;
    }
    private void portalSends(ObjectNode... list) throws Exception {
        var root = JSON.createObjectNode();
        root.putArray("events").addAll(List.of(list));
        root.put("last_sequence", list[list.length - 1].get("sequence").textValue());
        root.putNull("next_cursor");
        page = JSON.writeValueAsBytes(root);
    }

    @Test
    @DisplayName("should pass over settled events CARLOS has no record of, record them unverified and audit them")
    void shouldPassOverSettledEvents_whenCarlosHasNoRecordOfThem() throws Exception {
        portalSends(deviceRemoved(1, NOW - 3600, UNKNOWN_RECIPIENT), expired(2, NOW - 3600, UNKNOWN_RECIPIENT));
        assertEquals(PortalMyCarlosDeliverySyncService.Result.COLLECTED, collector().poll());
        assertEquals(2, committed);
        assertEquals(List.of(2L), acks);
        assertEquals(2, acknowledged);
        assertEquals(List.of("1:device_removed:false", "2:expired:false"), recorded);
        assertEquals(List.of("clinic-a:1:device_removed", "clinic-a:2:expired"), audited);
        assertTrue(quarantined.isEmpty());
        // The removal is applied all the same: its key stays fenced if it is ever accepted.
        verify(outbox).fenceDevice(CLINIC, KID);
        verify(outbox, never()).terminal(any(), anyString());
    }

    @Test
    @DisplayName("should pass over a settled anchor CARLOS has no record of without applying anything")
    void shouldPassOverSettledAnchor_whenCarlosHasNoRecordOfIt() throws Exception {
        portalSends(anchor(1, NOW - 3600, UNKNOWN_RECIPIENT));
        assertEquals(PortalMyCarlosDeliverySyncService.Result.COLLECTED, collector().poll());
        assertEquals(List.of(1L), acks);
        assertEquals(List.of("1:anchor:false"), recorded);
        assertEquals(List.of("clinic-a:1:anchor"), audited);
        verify(outbox, never()).fenceDevice(anyString(), anyString());
    }

    @Test
    @DisplayName("should keep every check for a delivery CARLOS holds under another device key")
    void shouldKeepEveryCheck_forADeliveryHeldUnderAnotherKey() throws Exception {
        when(outbox.findForDelivery(CLINIC, DELIVERY))
                .thenReturn(List.of(mock(PortalMyCarlosDeliveryOutboxDao.Row.class)));
        portalSends(expired(1, NOW - 86400, UNKNOWN_RECIPIENT));
        assertEquals(PortalMyCarlosDeliverySyncService.Result.QUARANTINED, collector().poll());
        assertEquals(0, committed);
        assertTrue(acks.isEmpty());
        assertEquals(List.of("unknown_binding"), quarantined);
        assertTrue(audited.isEmpty());
    }

    @Test
    @DisplayName("should keep every check for an accepted device named under a recipient CARLOS has no binding for")
    void shouldKeepEveryCheck_forAnAcceptedDeviceBoundElsewhere() throws Exception {
        when(devices.findByKidForUpdate(KID)).thenReturn(Optional.of(mock(PortalMyCarlosAcceptedDevice.class)));
        portalSends(deviceRemoved(1, NOW - 86400, UNKNOWN_RECIPIENT));
        assertEquals(PortalMyCarlosDeliverySyncService.Result.QUARANTINED, collector().poll());
        assertEquals(0, committed);
        assertTrue(acks.isEmpty());
        assertEquals(List.of("unknown_binding"), quarantined);
        assertTrue(audited.isEmpty());
        verify(outbox, never()).fenceDevice(anyString(), anyString());
    }

    @Test
    @DisplayName("should hold an unknown event while the approval request it may belong to can still be open")
    void shouldHoldUnknownEvent_whileItsRequestMayStillBeOpen() throws Exception {
        portalSends(deviceRemoved(1, NOW - 60, UNKNOWN_RECIPIENT));
        assertEquals(PortalMyCarlosDeliverySyncService.Result.QUARANTINED, collector().poll());
        assertEquals(0, committed);
        assertTrue(acks.isEmpty());
        assertEquals(List.of("unknown_binding"), quarantined);
        assertTrue(recorded.isEmpty());
        assertTrue(audited.isEmpty());
    }

    @ParameterizedTest(name = "an event {0} s old is passed over: {1}")
    @CsvSource({"900, true", "899, false"})
    @DisplayName("should pass over an unknown event from exactly the settled age, and hold one a second younger")
    void shouldPassOverUnknownEvent_fromExactlyTheSettledAge(long age, boolean passedOver) throws Exception {
        assertEquals(900, PortalMyCarlosDeliverySyncService.UNKNOWN_SETTLED_SECONDS);
        portalSends(deviceRemoved(1, NOW - age, UNKNOWN_RECIPIENT));
        assertEquals(passedOver ? PortalMyCarlosDeliverySyncService.Result.COLLECTED : PortalMyCarlosDeliverySyncService.Result.QUARANTINED,
                collector().poll());
        assertEquals(passedOver ? 1 : 0, committed);
        assertEquals(passedOver ? List.of(1L) : List.of(), acks);
        assertEquals(passedOver ? List.of("clinic-a:1:device_removed") : List.of(), audited);
        assertEquals(passedOver ? List.of() : List.of("unknown_binding"), quarantined);
        verify(outbox, times(passedOver ? 1 : 0)).fenceDevice(CLINIC, KID);
    }

    @Test
    @DisplayName("should keep every check for a recipient CARLOS knows, however old the event")
    void shouldKeepEveryCheck_forARecipientCarlosKnows() throws Exception {
        portalSends(deviceRemoved(1, NOW - 86400, KNOWN_RECIPIENT));
        assertEquals(PortalMyCarlosDeliverySyncService.Result.QUARANTINED, collector().poll());
        assertEquals(0, committed);
        assertTrue(acks.isEmpty());
        assertEquals(List.of("unknown_binding"), quarantined);
        assertTrue(audited.isEmpty());
    }

    @Test
    @DisplayName("should apply a delivery CARLOS holds even when its recipient has no binding")
    void shouldApplyKnownDelivery_whenItsRecipientHasNoBinding() throws Exception {
        var row = mock(PortalMyCarlosDeliveryOutboxDao.Row.class);
        var snapshot = mock(PortalMyCarlosDeliverySnapshot.class);
        when(outbox.findForDelivery(CLINIC, DELIVERY)).thenReturn(List.of(row));
        when(outbox.find(CLINIC, DELIVERY, KID)).thenReturn(Optional.of(row));
        when(sender.load(row)).thenReturn(snapshot);
        when(snapshot.recipientId()).thenReturn(UNKNOWN_RECIPIENT);
        when(snapshot.ciphertextSha256()).thenReturn(CIPHERTEXT);
        when(snapshot.createdAt()).thenReturn(NOW - 86400);
        when(snapshot.expiresAt()).thenReturn(NOW - 86400 + 3600);
        when(snapshot.sealedSize()).thenReturn(4096);
        portalSends(expired(1, NOW - 3600, UNKNOWN_RECIPIENT));
        assertEquals(PortalMyCarlosDeliverySyncService.Result.COLLECTED, collector().poll());
        verify(outbox).terminal(row, "expired");
        assertEquals(List.of("1:expired:false"), recorded);
        assertTrue(audited.isEmpty());
    }

    @Test
    @DisplayName("should acknowledge the passed-over prefix and hold the first event that may still become known")
    void shouldAcknowledgePassedOverPrefix_beforeAYoungUnknownEvent() throws Exception {
        portalSends(deviceRemoved(1, NOW - 3600, UNKNOWN_RECIPIENT), deviceRemoved(2, NOW - 30, UNKNOWN_RECIPIENT));
        assertEquals(PortalMyCarlosDeliverySyncService.Result.QUARANTINED, collector().poll());
        assertEquals(1, committed);
        assertEquals(List.of(1L), acks);
        assertEquals(List.of("1:device_removed:false"), recorded);
        assertEquals(List.of("clinic-a:1:device_removed"), audited);
        assertEquals(List.of("unknown_binding"), quarantined);
    }
}
