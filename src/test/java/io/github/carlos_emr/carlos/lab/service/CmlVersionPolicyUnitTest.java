/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.CMLHandler;

/** Real HAPI source messages exercise the version order of a CML report: total, and never a refusal.
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
    private static List<Integer> order(Object... idsAndHandlers) {
        var sources=new LinkedHashMap<Integer,CMLHandler>();
        for(int i=0;i<idsAndHandlers.length;i+=2)sources.put((Integer)idsAndHandlers[i],(CMLHandler)idsAndHandlers[i+1]);
        return CmlVersionPolicy.order(sources);
    }
    @Test void shouldRankACorrectionAfterItsOriginal_whateverTheArrivalIds() throws Exception {
        // #5819: the correction arrived with the lower id and must still be the newest version.
        assertThat(order(90,handler("20260310080000","F","7010=5.1"),11,handler("20260310120000","C","7010=12.7")))
                .containsExactly(90,11);
    }
    @Test void shouldRankAnOlderVersionFirst_whenItIsDeliveredLast() throws Exception {
        // #5820: delivery order original, correction, older.
        assertThat(order(1,handler("20260310080000","F","7010=5.1"),2,handler("20260310120000","C","7010=12.7"),
                3,handler("20260310070000","F","7010=4.3"))).containsExactly(3,1,2);
    }
    @Test void shouldPreferAnExplicitCorrection_whenTheClinicalTimestampIsUnchanged() throws Exception {
        assertThat(order(2,handler("20260310080000","C","7010=12.7"),1,handler("20260310080000","F","7010=5.1")))
                .containsExactly(1,2);
        assertThat(order(1,handler("20260310080000","C","7010=12.7"),2,handler("20260310080000","F","7010=5.1")))
                .containsExactly(2,1);
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
        CMLHandler replay=new CMLHandler();
        replay.init(body("SYNTHETIC-ACCESSION","20260310070000","F","7010=4.3")
                .replace("CML|||20260310070000", "CML|||20260410190000"));
        assertThat(order(99,replay,1,handler("20260310120000","C","7010=12.7"))).containsExactly(99,1);
    }
    @Test void shouldKeepAFinalResultNewest_whenALaterPreliminaryVersionArrives() throws Exception {
        assertThat(order(1,handler("20260310080000","F","7010=5.1"),2,handler("20260310120000","P","7010=12.7")))
                .containsExactly(2,1);
    }
    @Test void shouldTreatTheLaterArrivalAsNewer_whenTheLabFieldsDoNotSeparateVersions() throws Exception {
        // The real CML layout dates a report by day only. Two finals of one day are ordered as
        // OSCAR always ordered them: by arrival.
        for(String time:List.of("20260310","20260310120000")) {
            assertThat(order(2,handler(time,"F","7010=12.7"),1,handler(time,"F","7010=5.1"))).containsExactly(1,2);
            assertThat(order(1,handler(time,"C","7010=12.7"),2,handler(time,"C","7010=13.1"))).containsExactly(1,2);
        }
    }
    @Test void shouldNeverRefuseToOrder_whenAVersionIsUndatedOrItsStatusIsUnknown() throws Exception {
        assertThat(order(7,handler("","F","7010=5.1"))).containsExactly(7);
        // An undated or unrecognised version is simply the oldest; nothing is dropped.
        assertThat(order(1,handler("20260310080000","F","7010=5.1"),2,handler("","F","7010=6.0"))).containsExactly(2,1);
        assertThat(order(1,handler("20260310080000","F","7010=5.1"),2,handler("20260310120000","X","7010=13.1")))
                .containsExactly(2,1);
        assertThat(order(1,handler("20260310080000","F","7010=5.1","7010=5.2"),2,handler("20260310120000","C","7010=6.0")))
                .containsExactly(1,2);
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"F", "f", "C", "c"})
    void shouldCountAsciiFinalAndCorrectionStatuses_withoutUnicodeCaseFolding(String status) throws Exception {
        assertThat(handler("20260310120000", status, "7010=12.7").getOBXFinalResultCount()).isEqualTo(1);
    }
}
