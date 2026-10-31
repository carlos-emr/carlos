/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

@Tag("unit")
class PortalMyCarlosDeviceEvidenceVerifierUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void shouldVerifyOriginalHistoricalAssertions_withAndWithoutIndependentAnchor() throws Exception {
        var f = PortalMyCarlosDeliveryProducerUnitTest.fixture();
        var snapshot = PortalMyCarlosDeliveryStorageUnitTest.snapshot();
        try (var input = getClass().getResourceAsStream("/patientportal/mailbox-contract-v1.json")) {
            var m4 = JSON.readTree(input);
            for (int index : new int[] {0, 4}) {
                var event = JSON.readTree(HexFormat.of().parseHex(m4.at("/events/" + index + "/json/hex").textValue()));
                String compact = event.at("/payload/request_assertion").textValue();
                var proof = PortalMyCarlosDeviceEvidenceVerifier.verify(compact, snapshot.device(), snapshot.recipientId());
                assertEquals(compact, proof.compact());
                if (index == 0) assertNull(proof.anchor());
                else assertEquals(event.at("/payload/vault_head_link").textValue(), proof.anchor());
            }
        }
    }

    @TestFactory List<DynamicTest> shouldRefuseAuthenticHostileAssertionSchemas_whenCollectedAsEvidence() throws Exception {
        var fixture = PortalMyCarlosDeliveryProducerUnitTest.fixture();
        var snapshot = PortalMyCarlosDeliveryStorageUnitTest.snapshot();
        ObjectNode claims = JSON.createObjectNode();
        claims.put("aud", "carlos-patient-portal-device-api");
        claims.put("iss", "mycarlos");
        claims.put("iat", 1792454400L);
        claims.put("exp", 1792454460L);
        claims.put("jti", "00000000-0000-4000-8000-000000000001");
        claims.put("kid", snapshot.device().kid());
        claims.put("recipient_id", snapshot.recipientId());
        claims.put("request_hash", "a".repeat(64));
        List<String> bad = List.of(claims.deepCopy().put("aud", "carlos-patient-portal-device-link").toString(),
                claims.deepCopy().put("recipient_id", "other-patient").toString(),
                claims.deepCopy().put("kid", "A".repeat(43)).toString(),
                claims.deepCopy().put("iat", true).toString(), claims.deepCopy().put("exp", 1792454521L).toString(),
                claims.deepCopy().put("exp", 1792454400L).toString(), claims.deepCopy().put("vault_head_link", "bad").toString(),
                claims.deepCopy().put("extra", "bad").toString(), claims.deepCopy().put("jti", "not-a-uuid").toString(),
                "{\"aud\":\"carlos-patient-portal-device-api\"," + claims.toString().substring(1));
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < bad.size(); i++) {
            String compact = PortalMyCarlosDeliveryProducerUnitTest.signDevicePayload(bad.get(i).getBytes(StandardCharsets.UTF_8), fixture);
            tests.add(DynamicTest.dynamicTest("authentic invalid device assertion " + i, () -> assertThrows(IllegalArgumentException.class,
                    () -> PortalMyCarlosDeviceEvidenceVerifier.verify(compact, snapshot.device(), snapshot.recipientId()))));
        }
        return tests;
    }
}
