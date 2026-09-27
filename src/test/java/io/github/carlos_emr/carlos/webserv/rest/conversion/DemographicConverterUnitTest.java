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
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import io.github.carlos_emr.carlos.webserv.transfer_objects.DemographicTransfer;

/**
 * Unit tests for the {@code rosterEnrolledTo} mapping on the demographic REST/SOAP transfer
 * objects (issue #3899). The value is the raw provider number stored in
 * {@code demographic.roster_enrolled_to}; the converters must carry it both ways so a REST
 * GET-then-PUT round trip no longer silently clears it.
 *
 * @since 2026-09-24
 */
@Tag("unit")
@Tag("converter")
@Tag("rest")
@DisplayName("DemographicConverter rosterEnrolledTo mapping")
class DemographicConverterUnitTest {

    private static final String PROVIDER_NO = "999998";

    private final DemographicConverter converter = new DemographicConverter();

    private static Demographic demographicWithValidBirthDate() {
        Demographic d = new Demographic();
        d.setDemographicNo(1);
        d.setYearOfBirth("1980");
        d.setMonthOfBirth("01");
        d.setDateOfBirth("15");
        return d;
    }

    @Test
    @DisplayName("should copy rosterEnrolledTo from the domain object to the transfer object")
    void shouldCopyRosterEnrolledTo_fromDomainToTransferObject() {
        Demographic d = demographicWithValidBirthDate();
        d.setRosterEnrolledTo(PROVIDER_NO);

        DemographicTo1 t = converter.getAsTransferObject(null, d);

        assertThat(t.getRosterEnrolledTo()).isEqualTo(PROVIDER_NO);
    }

    @Test
    @DisplayName("should leave rosterEnrolledTo null on the transfer object when the domain value is null")
    void shouldLeaveRosterEnrolledToNull_whenDomainValueIsNull() {
        Demographic d = demographicWithValidBirthDate();

        DemographicTo1 t = converter.getAsTransferObject(null, d);

        assertThat(t.getRosterEnrolledTo()).isNull();
    }

    @Test
    @DisplayName("should copy rosterEnrolledTo from the transfer object to the domain object")
    void shouldCopyRosterEnrolledTo_fromTransferToDomainObject() {
        DemographicTo1 t = new DemographicTo1();
        t.setRosterEnrolledTo(PROVIDER_NO);

        Demographic d = converter.getAsDomainObject(null, t);

        assertThat(d.getRosterEnrolledTo()).isEqualTo(PROVIDER_NO);
    }

    @Test
    @DisplayName("should leave rosterEnrolledTo null on the domain object when the transfer value is null")
    void shouldLeaveRosterEnrolledToNull_whenTransferValueIsNull() {
        DemographicTo1 t = new DemographicTo1();

        Demographic d = converter.getAsDomainObject(null, t);

        assertThat(d.getRosterEnrolledTo()).isNull();
    }

    @Test
    @DisplayName("should preserve rosterEnrolledTo across a domain to transfer to domain round trip")
    void shouldPreserveRosterEnrolledTo_acrossRoundTrip() {
        Demographic original = demographicWithValidBirthDate();
        original.setRosterEnrolledTo(PROVIDER_NO);

        Demographic roundTripped = converter.getAsDomainObject(null, converter.getAsTransferObject(null, original));

        assertThat(roundTripped.getRosterEnrolledTo()).isEqualTo(PROVIDER_NO);
    }

    @Test
    @DisplayName("should serialize and deserialize rosterEnrolledTo as a JSON string property")
    void shouldRoundTripRosterEnrolledTo_throughJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DemographicTo1 t = new DemographicTo1();
        t.setRosterEnrolledTo(PROVIDER_NO);

        String json = mapper.writeValueAsString(t);
        JsonNode node = mapper.readTree(json);
        DemographicTo1 parsed = mapper.readValue(json, DemographicTo1.class);

        assertThat(node.get("rosterEnrolledTo").isTextual()).isTrue();
        assertThat(node.get("rosterEnrolledTo").asText()).isEqualTo(PROVIDER_NO);
        assertThat(parsed.getRosterEnrolledTo()).isEqualTo(PROVIDER_NO);
    }

    @Test
    @DisplayName("should copy rosterEnrolledTo into the SOAP DemographicTransfer and honour the field filter")
    void shouldExposeRosterEnrolledTo_inSoapDemographicTransfer() {
        Demographic d = demographicWithValidBirthDate();
        d.setRosterEnrolledTo(PROVIDER_NO);

        DemographicTransfer transfer = DemographicTransfer.toTransfer(d);
        DemographicTransfer filtered = transfer.filter(new String[]{"rosterEnrolledTo"});

        assertThat(transfer.getRosterEnrolledTo()).isEqualTo(PROVIDER_NO);
        assertThat(filtered.getRosterEnrolledTo()).isEqualTo(PROVIDER_NO);
        assertThat(filtered.getDemographicNo()).isNull();
    }
}
