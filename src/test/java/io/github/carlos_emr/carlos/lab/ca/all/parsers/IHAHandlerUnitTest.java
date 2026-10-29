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
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link IHAHandler} across the HAPI HL7 v2 upgrade (issue #5414). No lab type in
 * {@code message_config.xml} uses this handler today; it is pinned because it walks HAPI's generic
 * model by segment name, the way the reachable generic handler does.
 */
@Tag("unit")
@Tag("lab")
@Tag("hl7")
@DisplayName("IHAHandler")
class IHAHandlerUnitTest {

    /** Synthetic: three requests, the last two adjacent, each with its own requested date (OBR-14). */
    private static final String THREE_REQUESTS = "MSH|^~\\&|SYNTHLIS|SYNTHETIC LAB|||20261029083000||ORU^R01|SYN-IHA-1|P|2.3\r"
            + "PID|1||9999999903||SYNTHETIC^IHA||19800101|F\r"
            + "OBR|1|P1|A1|CHEM^Chemistry^L|||20261028070000|||||||20261028071500\r"
            + "OBX|1|NM|NA^Sodium^L||140|mmol/L|135-145|N|||F\r"
            + "OBR|2|P2|A2|HEME^Haematology^L|||20261028070000|||||||20261028073000\r"
            + "OBR|3|P3|A3|COAG^Coagulation^L|||20261028070000|||||||20261028074500\r"
            + "OBX|1|NM|INR^INR^L||1.1||0.8-1.2|N|||F\r";

    @Test
    @DisplayName("should count every request when two requests are adjacent")
    void shouldCountEveryRequest_whenTwoRequestsAreAdjacent() throws Exception {
        // Matches HAPI 1.0.1. Without Hl7Parsers' 1.0.1 segment names, HAPI 2.x folds the two
        // adjacent OBRs into one name and the handler reports two requests.
        IHAHandler handler = new IHAHandler();
        handler.init(THREE_REQUESTS);

        assertThat(handler.getOBRCount()).isEqualTo(3);
        assertThat(handler.getOBRName(1)).isEqualTo("Haematology");
        assertThat(handler.getOBRName(2)).isEqualTo("Coagulation");
    }

    @Test
    @DisplayName("should report each request's own requested date for every request")
    void shouldReportEachRequestsOwnRequestedDate_forEveryRequest() throws Exception {
        // HAPI 1.0.1's Terser found "/.OBR2-14-1" for some messages and not others, falling back to
        // the message date when it did not. HAPI 2.x always finds it.
        IHAHandler handler = new IHAHandler();
        handler.init(THREE_REQUESTS);

        assertThat(handler.getRequestDate(0)).isEqualTo("2026-10-28 07:15:00");
        assertThat(handler.getRequestDate(1)).isEqualTo("2026-10-28 07:30:00");
        assertThat(handler.getRequestDate(2)).isEqualTo("2026-10-28 07:45:00");
    }
}
