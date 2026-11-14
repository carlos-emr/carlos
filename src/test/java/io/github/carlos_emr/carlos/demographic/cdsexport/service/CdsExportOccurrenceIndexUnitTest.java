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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDispositionDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * The packed occurrence index answers exactly as the sets and maps of identities, dispositions
 * and field ids it replaced, in the same order, whatever the number of occurrences.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportOccurrenceIndexUnitTest {
    private static final String PATIENT = "c".repeat(64), CAPTURE = "a".repeat(64);

    private static CdsExportIdentityDto identity(CdsCategory category, int number) {
        return new CdsExportIdentityDto(
                PATIENT, category, CdsImportKeys.hash("fixture", category.name(), "" + number));
    }

    private static CdsExportDispositionDto emitted(CdsExportIdentityDto identity) {
        return new CdsExportDispositionDto(
                CAPTURE,
                identity,
                CdsExportDispositionDto.Disposition.EMITTED,
                CdsImportKeys.hash("output", identity.occurrenceKey()),
                null);
    }

    @Test
    void shouldReadTheIdentitiesInIdentityOrder_asTheSortedSetItReplaced() {
        var index = new CdsExportOccurrenceIndex(PATIENT);
        var expected = new TreeSet<CdsExportIdentityDto>();
        for (var category : CdsCategory.values())
            for (int number = 0; number < 40; number++) {
                var identity = identity(category, number);
                expected.add(identity);
                index.expect(identity);
            }
        assertThat(index.seal()).isTrue();
        assertThat(index.occurrences()).isEqualTo(expected.size());
        assertThat(new ArrayList<>(index.identities())).isEqualTo(new ArrayList<>(expected));
        assertThat(index.identities()).containsAll(expected);
        assertThat(index.occurrences(CdsCategory.REPORTS)).isEqualTo(40);
        assertThat(index.expects(identity(CdsCategory.REPORTS, 40))).isFalse();
        assertThat(
                        index.expects(
                                new CdsExportIdentityDto(
                                        "d".repeat(64),
                                        CdsCategory.REPORTS,
                                        identity(CdsCategory.REPORTS, 1).occurrenceKey())))
                .isFalse();
        assertThat(index.identities().contains("not an identity")).isFalse();
    }

    @Test
    void shouldReportAnOccurrenceOrFieldExpectedTwice_whenTheIndexIsSealed() {
        var twice = new CdsExportOccurrenceIndex(PATIENT);
        twice.expect(identity(CdsCategory.MEDICATIONS_AND_TREATMENTS, 1));
        twice.expect(identity(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS, 1));
        twice.expect(identity(CdsCategory.MEDICATIONS_AND_TREATMENTS, 1));
        assertThat(twice.seal()).isFalse();
        var field = new CdsExportOccurrenceIndex(PATIENT);
        field.expectField("1".repeat(64));
        field.expectField("1".repeat(64));
        assertThat(field.seal()).isFalse();
        // The same key in two categories is two occurrences.
        var apart = new CdsExportOccurrenceIndex(PATIENT);
        String key = "2".repeat(64);
        apart.expect(new CdsExportIdentityDto(PATIENT, CdsCategory.REPORTS, key));
        apart.expect(new CdsExportIdentityDto(PATIENT, CdsCategory.NEW_CATEGORY, key));
        assertThat(apart.seal()).isTrue();
    }

    @Test
    void shouldRecordOneDispositionAndOneReceipt_forEachExpectedOccurrence() {
        var index = new CdsExportOccurrenceIndex(PATIENT);
        var first = identity(CdsCategory.LABORATORY_RESULTS, 1);
        var second = identity(CdsCategory.LABORATORY_RESULTS, 2);
        var blocked = identity(CdsCategory.REPORTS, 3);
        var stranger = identity(CdsCategory.REPORTS, 4);
        for (var identity : List.of(first, second, blocked)) index.expect(identity);
        index.expectField("3".repeat(64));
        index.expectField("4".repeat(64));
        assertThat(index.seal()).isTrue();
        // A receipt needs its occurrence's disposition first.
        assertThat(index.receive(first)).isFalse();
        assertThat(index.dispose(emitted(first))).isTrue();
        assertThat(index.dispose(emitted(first))).isFalse();
        assertThat(index.dispose(emitted(stranger))).isFalse();
        assertThat(index.receive(stranger)).isFalse();
        assertThat(index.receive(first)).isTrue();
        assertThat(index.receive(first)).isFalse();
        assertThat(index.received(first)).isTrue();
        assertThat(index.received(second)).isFalse();
        var refusal =
                new CdsExportDispositionDto(
                        CAPTURE,
                        blocked,
                        CdsExportDispositionDto.Disposition.BLOCKED,
                        null,
                        CdsExportDispositionDto.Blocker.RESOURCE_LIMIT);
        assertThat(index.dispose(refusal)).isTrue();
        // The blocked occurrence has no receipt yet and no field is claimed.
        assertThat(index.reconciled()).isFalse();
        assertThat(index.receive(blocked)).isTrue();
        assertThat(index.claimField("3".repeat(64))).isTrue();
        assertThat(index.claimField("3".repeat(64))).isFalse();
        assertThat(index.claimField("5".repeat(64))).isFalse();
        assertThat(index.claimField("not an id")).isFalse();
        assertThat(index.reconciled()).isFalse();
        assertThat(index.claimField("4".repeat(64))).isTrue();
        assertThat(index.reconciled()).isTrue();
        assertThat(index.fieldIds()).containsExactly("3".repeat(64), "4".repeat(64));
        assertThat(index.fieldIds().contains("4".repeat(64))).isTrue();
        // The dispositions read back in identity order, exactly as they were recorded; the
        // occurrence nothing disposed of is left out.
        var read = new ArrayList<CdsExportDispositionDto>();
        index.dispositions(CAPTURE).forEachRemaining(read::add);
        var recorded = new ArrayList<>(List.of(emitted(first), refusal));
        recorded.sort(Comparator.comparing(CdsExportDispositionDto::identity));
        assertThat(read).isEqualTo(recorded);
    }

    @Test
    void shouldHoldAMillionOccurrences_inTheBytesItStates() {
        // 1,048,576 occurrences and as many retained fields: more than the 131,072 that used to
        // stop the job, in 66 and 33 bytes each.
        int count = 1 << 20;
        Runtime.getRuntime().gc();
        long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        var index = new CdsExportOccurrenceIndex(PATIENT);
        byte[] key = new byte[32];
        var random = new Random(27);
        for (int i = 0; i < count; i++) {
            random.nextBytes(key);
            String hex = HexFormat.of().formatHex(key);
            index.expect(new CdsExportIdentityDto(PATIENT, CdsCategory.CARE_ELEMENTS, hex));
            index.expectField(hex);
        }
        assertThat(index.seal()).isTrue();
        Runtime.getRuntime().gc();
        long held = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() - before;
        assertThat(index.occurrences()).isEqualTo(count);
        assertThat(index.fields()).isEqualTo(count);
        // 99 bytes for each pair, and room for the measurement itself to be imprecise.
        assertThat(held).isLessThan(count * 160L);
        var first = index.identities().iterator().next();
        assertThat(index.dispose(emitted(first))).isTrue();
        assertThat(index.receive(first)).isTrue();
    }

    @Test
    void shouldRefuseAnotherPatientsOccurrence_orAnAdditionAfterTheIndexIsSealed() {
        var index = new CdsExportOccurrenceIndex(PATIENT);
        assertThatThrownBy(
                        () ->
                                index.expect(
                                        new CdsExportIdentityDto(
                                                "d".repeat(64), CdsCategory.REPORTS, "1".repeat(64))))
                .hasMessage("CDS_EXPORT_OCCURRENCE_INDEX_INVALID");
        assertThatThrownBy(() -> index.expects(identity(CdsCategory.REPORTS, 1)))
                .hasMessage("CDS_EXPORT_OCCURRENCE_INDEX_INVALID");
        index.seal();
        assertThatThrownBy(() -> index.expect(identity(CdsCategory.REPORTS, 1)))
                .hasMessage("CDS_EXPORT_OCCURRENCE_INDEX_INVALID");
        assertThatThrownBy(() -> index.expectField("1".repeat(64)))
                .hasMessage("CDS_EXPORT_OCCURRENCE_INDEX_INVALID");
        assertThatThrownBy(() -> new CdsExportOccurrenceIndex("short"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
