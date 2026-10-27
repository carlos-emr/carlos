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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.dao.CdsImportIdentityDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportIdentity;
import io.github.carlos_emr.carlos.commn.model.CdsImportRun;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsPatientIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsHealthCardAuthority;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;

@Tag("unit")
class CdsIdentityAuthorityUnitTest {
    @TempDir Path temp;
    private CdsImportIdentityDao identities;
    private CdsImportLedgerService ledger;
    private CdsPreflightIdentityService service;
    private Map<String, CdsImportIdentity> inventory;
    private CdsImportRun owner;

    @BeforeEach
    void setup() {
        identities = mock(CdsImportIdentityDao.class);
        ledger = mock(CdsImportLedgerService.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        service = new CdsPreflightIdentityService(ledger, identities, manager);
        inventory = new HashMap<>();
        owner = new CdsImportRun();
        when(ledger.lockPlanningOwned("run", 1)).thenReturn(owner);
        when(identities.get(anyString())).thenAnswer(call -> inventory.get(call.getArgument(0)));
        doAnswer(
                        call -> {
                            CdsImportIdentity value = call.getArgument(0);
                            inventory.put(value.getId(), value);
                            return null;
                        })
                .when(identities)
                .persist(any());
    }

    @Test
    void authorityVocabularyMatchesEveryPinnedXsdCodeAndAllCanadianAliases() throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        Set<String> found = new HashSet<>();
        boolean type = false;
        try (var input =
                getClass()
                        .getResourceAsStream(
                                "/cds-import/schema/EMR_Data_Migration_Schema_DT.xsd")) {
            assertThat(input).isNotNull();
            var xml = factory.createXMLStreamReader(input);
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamConstants.START_ELEMENT
                        && xml.getLocalName().equals("simpleType"))
                    type = "healthCardProvinceCode".equals(xml.getAttributeValue(null, "name"));
                if (type
                        && event == XMLStreamConstants.START_ELEMENT
                        && xml.getLocalName().equals("enumeration"))
                    found.add(xml.getAttributeValue(null, "value"));
                if (type
                        && event == XMLStreamConstants.END_ELEMENT
                        && xml.getLocalName().equals("simpleType")) break;
            }
            xml.close();
        }
        assertThat(found).hasSize(71);
        Set<String> expected = new HashSet<>(CdsHealthCardAuthority.PINNED_US);
        CdsHealthCardAuthority.CANADIAN.forEach(code -> expected.add("CA-" + code));
        expected.addAll(Set.of("-50", "-70", "-90"));
        assertThat(found).isEqualTo(expected);
        for (String code : CdsHealthCardAuthority.CANADIAN) {
            assertThat(CdsHealthCardAuthority.known(code)).isEqualTo(code);
            assertThat(CdsHealthCardAuthority.known("CA-" + code)).isEqualTo(code);
        }
        for (String code : CdsHealthCardAuthority.PINNED_US)
            assertThat(CdsHealthCardAuthority.known(code)).isEqualTo(code);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"-50", "-70", "-90", "CA-XX", "US-XX", "NY", "on", "ON ", "CA-ON "})
    void unknownSentinelsAndUnrecognizedCodesNeverDisambiguateIdentity(String value) {
        assertThat(CdsHealthCardAuthority.known(value)).isNull();
        assertThat(CdsHealthCardAuthority.normalize(value)).isEqualTo(value);
    }

    @Test
    void firstSeenIsIdempotentAndStoresCanonicalAuthorityWithoutAnInodeMarker() {
        var source = source("CA-ON");
        assertThat(service.register("run", 1, "patient", "clinical", source)).isTrue();
        assertThat(service.register("run", 1, "patient", "clinical", source)).isFalse();
        assertThat(inventory.values())
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.getIssuer()).isEqualTo("ON");
                            assertThat(row.isComparable()).isTrue();
                        });
        verify(identities, times(1)).persist(any());
        assertThatThrownBy(() -> service.register("run", 1, "patient", "clinical", source("BC")))
                .hasMessageContaining("SOURCE_IDENTITY_REVISION_REVIEW_REQUIRED");
    }

    @Test
    void declinedOrTypedInvalidOccurrencesRemainSeenWithoutPoisoningActiveIdentityChecks() {
        assertThat(service.register("run", 1, "declined", "clinical", source("ON"), false))
                .isTrue();
        assertThat(service.register("run", 1, "declined", "clinical", source("ON"), false))
                .isFalse();
        verify(identities, never())
                .healthNumberCollision(anyString(), anyString(), anyString(), anyString(), any());
        verify(identities, never())
                .nameCollision(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyBoolean());
        assertThat(inventory.values())
                .singleElement()
                .satisfies(row -> assertThat(row.isComparable()).isFalse());
    }

    @Test
    void batchLookupReceivesKnownAuthorityAndNeverANameDisambiguator() {
        service.register("run", 1, "one", "clinical", source("CA-ON"));
        service.register("run", 1, "two", "clinical", source("BC"));
        verify(identities).healthNumberCollision("run", "clinical", "one", "123456", "ON");
        verify(identities).healthNumberCollision("run", "clinical", "two", "123456", "BC");
        when(identities.healthNumberCollision("run", "clinical", "unknown", "123456", null))
                .thenReturn(true);
        assertThatThrownBy(() -> service.register("run", 1, "unknown", "clinical", source("-90")))
                .hasMessageContaining("SOURCE_BATCH_IDENTITY_COLLISION");
    }

    @Test
    void freshScanClearsOnlyItsOwnedInventoryInBoundedPages() {
        List<CdsImportIdentity> full = new ArrayList<>();
        for (int i = 0; i < 200; i++) full.add(new CdsImportIdentity());
        when(identities.page("run", 200)).thenReturn(full, List.of(new CdsImportIdentity()));
        service.beginScan("run", 1);
        verify(identities, times(201)).remove(any(CdsImportIdentity.class));
        verify(ledger, times(2)).lockPlanningOwned("run", 1);
        verify(identities, times(2)).flushAndClear();
    }

    @Test
    void cancellationStopsBeforeTheNextInventoryPage() {
        owner.setCancelRequested(true);
        assertThatThrownBy(() -> service.beginScan("run", 1))
                .hasMessageContaining("IMPORT_CANCELLED");
        verifyNoInteractions(identities);
    }

    @Test
    void sourceBirthDateShiftCannotOverflowTheNativeCalendar() throws Exception {
        var record =
                CdsClinicalFixture.record(
                        temp, "Demographics", Map.of("DateOfBirth", "9999-12-31"));
        var command =
                new CdsImportCommand(
                        temp,
                        "identity",
                        "999998",
                        null,
                        null,
                        "identity-test",
                        "cds-5.2",
                        false,
                        false,
                        false,
                        1,
                        1,
                        true);
        assertThatThrownBy(
                        () ->
                                new CdsPatientIdentityResolver(new CdsFieldParser())
                                        .source(record, command))
                .hasMessageContaining("CDS_DATE_OUTSIDE_TARGET_CALENDAR");
    }

    private static CdsPatientIdentityDto source(String issuer) {
        return new CdsPatientIdentityDto(
                "source",
                "Synthetic",
                "Patient",
                LocalDate.of(2000, 1, 2),
                "123456",
                issuer,
                "F",
                "AC");
    }
}
