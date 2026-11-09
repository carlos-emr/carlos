/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.CMLHandler;

/** Real HAPI source messages exercise revision identity and clinical ordering, independent of arrival IDs.
 * @since 2026-11-09
 */
@Tag("unit")
@Tag("lab")
class CmlVersionPolicyUnitTest {
    static String body(String accession, String time, String status, String... observations) {
        String[] orc = new String[16], obr = new String[26];
        Arrays.fill(orc, ""); Arrays.fill(obr, "");
        orc[0]="ORC"; orc[1]="RE"; orc[2]=accession; orc[5]=status; orc[15]=time;
        obr[0]="OBR"; obr[1]="1"; obr[2]=accession; obr[4]="ML70^SYNTHETIC PANEL";
        obr[6]=time; obr[7]=time; obr[22]=time; obr[25]=status;
        StringBuilder body = new StringBuilder("MSH|^~\\&|Reports|CML|||" + time
                + "||ORU^R01|SYNTHETIC|P|2.3\rPID|1||||FAKE-CML^Workflow||19800102|F\r")
                .append(String.join("|", orc)).append('\r').append(String.join("|", obr)).append('\r');
        for (int i=0;i<observations.length;i++) {
            String[] parts=observations[i].split("=",2);
            body.append("OBX|").append(i+1).append("|ST|").append(parts[0]).append("^SYNTHETIC RESULT|^^CHEMISTRY|")
                    .append(parts[1]).append("|mmol/L|3-8|N|||").append(status).append("|||").append(time).append('\r');
        }
        return body.toString();
    }
    static CMLHandler handler(String time, String status, String... rows) throws Exception {
        CMLHandler result = new CMLHandler(); result.init(body("SYNTHETIC-ACCESSION", time, status, rows)); return result;
    }
    private static List<CmlVersionPolicy.Observation> rows(int report, String time, String status, String... values) throws Exception {
        return CmlVersionPolicy.observations(report, handler(time,status,values));
    }
    @Test void shouldRetainCorrection_forEveryDeliveryOrderAndDifferentArrivalIds() throws Exception {
        var original=rows(90,"20260310080000","F","7010=5.1");
        var corrected=rows(11,"20260310120000","C","7010=12.7");
        var older=rows(100,"20260310070000","F","7010=4.3");
        for (var order:List.of(List.of(original,corrected,older),List.of(original,older,corrected),
                List.of(corrected,original,older),List.of(corrected,older,original),
                List.of(older,original,corrected),List.of(older,corrected,original))) {
            var winner=CmlVersionPolicy.winners(order.stream().flatMap(List::stream).toList());
            assertThat(winner.values()).singleElement().satisfies(row->assertThat(row.report()).isEqualTo(11));
        }
    }
    @Test void shouldRetainOmittedObservations_whenACorrectionIsPartial() throws Exception {
        var all=new ArrayList<>(rows(1,"20260310080000","F","7010=5.1","7020=9.2"));
        all.addAll(rows(2,"20260310120000","C","7010=12.7"));
        assertThat(CmlVersionPolicy.winners(all).values()).extracting(CmlVersionPolicy.Observation::report)
                .containsExactlyInAnyOrder(1,2);
    }
    @Test void shouldPreferAnExplicitCorrection_whenTheClinicalTimestampIsUnchanged() throws Exception {
        var all=new ArrayList<>(rows(1,"20260310080000","F","7010=5.1"));
        all.addAll(rows(2,"20260310080000","C","7010=12.7"));
        assertThat(CmlVersionPolicy.winners(all).values()).singleElement().satisfies(row->assertThat(row.report()).isEqualTo(2));
    }
    @Test void shouldRefuseConflictingCorrections_whenNeitherHasALaterClinicalRevision() throws Exception {
        var all=new ArrayList<>(rows(1,"20260310120000","C","7010=5.1"));
        all.addAll(rows(2,"20260310120000","C","7010=12.7"));
        assertThatThrownBy(()->CmlVersionPolicy.winners(all)).isInstanceOf(IllegalStateException.class);
    }
    @Test void shouldWithdrawOnlyTheNamedObservation_whenDIsExplicit() throws Exception {
        var all=new ArrayList<>(rows(1,"20260310080000","F","7010=5.1","7020=9.2"));
        all.addAll(rows(2,"20260310120000","D","7010="));
        var winners=CmlVersionPolicy.winners(all);
        assertThat(winners.values()).filteredOn(CmlVersionPolicy.Observation::withdrawn).hasSize(1);
        assertThat(winners.values()).filteredOn(row->!row.withdrawn()).singleElement()
                .satisfies(row->assertThat(row.report()).isEqualTo(1));
    }
    @Test void shouldKeepAFinalResult_whenALaterPreliminaryObservationArrives() throws Exception {
        var all=new ArrayList<>(rows(1,"20260310080000","F","7010=5.1"));
        all.addAll(rows(2,"20260310120000","P","7010=12.7"));
        assertThat(CmlVersionPolicy.winners(all).values()).singleElement().satisfies(row->assertThat(row.report()).isEqualTo(1));
    }
    @Test void shouldRefuseRepeatedKeys_whenTheSourceProvidesNoDistinctSubIdentifier() throws Exception {
        var all=rows(1,"20260310080000","F","7010=5.1","7010=12.7");
        assertThatThrownBy(()->CmlVersionPolicy.winners(all)).isInstanceOf(IllegalStateException.class);
    }
    @Test void shouldOrderAPartialCorrectionAfterACompleteFinal_withoutUsingResultCount() throws Exception {
        var sources=new LinkedHashMap<Integer,CMLHandler>();
        sources.put(100,handler("20260310080000","F","7010=5.1","7020=9.2"));
        sources.put(11,handler("20260310120000","C","7010=12.7"));
        assertThat(CmlVersionPolicy.order(sources)).containsExactly(100,11);
        assertThat(sources.get(11).getOBXFinalResultCount()).isEqualTo(1);
        assertThat(sources.get(11).getOBXResultStatus(0,0)).isEqualTo("C");
    }
    @Test void shouldIgnoreTransportReplayTime_whenOrderingClinicalRevisions() throws Exception {
        var sources=new LinkedHashMap<Integer,CMLHandler>();
        CMLHandler replay=new CMLHandler();
        replay.init(body("SYNTHETIC-ACCESSION","20260310070000","F","7010=4.3")
                .replace("CML|||20260310070000", "CML|||20260410190000"));
        sources.put(99,replay); sources.put(1,handler("20260310120000","C","7010=12.7"));
        assertThat(CmlVersionPolicy.order(sources)).containsExactly(99,1);
    }
    @Test void shouldParseExplicitOffsetsAndPrecision_withoutRoundingClinicalRevisionTime() {
        assertThat(CmlVersionPolicy.timestamp("20260310120000.123456789-0400"))
                .isEqualTo(Instant.parse("2026-03-10T16:00:00.123456789Z"));
        assertThat(CmlVersionPolicy.timestamp("20260310160000+0000"))
                .isEqualTo(Instant.parse("2026-03-10T16:00:00Z"));
        for(String invalid:List.of("20260230120000","20260310126000","20260310120000junk","")) {
            assertThatThrownBy(()->CmlVersionPolicy.timestamp(invalid)).isInstanceOf(java.time.DateTimeException.class);
        }
    }
}
