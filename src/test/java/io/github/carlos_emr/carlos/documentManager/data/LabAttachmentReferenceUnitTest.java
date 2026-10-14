// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.documentManager.data;

import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("fast")
class LabAttachmentReferenceUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"HL7", "MDS", "CML", "BCP"})
    void shouldRoundTripSourceAndId_whenSourcesNumberLabsIndependently(String source) {
        var reference = LabAttachmentReference.parse(source.toLowerCase(java.util.Locale.ROOT) + ":123");
        assertThat(reference.source()).isEqualTo(source);
        assertThat(reference.id()).isEqualTo(123);
        assertThat(reference.key()).isEqualTo(source + ":123");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123", "HL7:0", "HL7:-1", "HL7:01", "HL7:2147483648", "DOC:1", "HL7:1:2", " HL7:1", "HL7:1 "})
    void shouldRejectMalformedIdentity_whenSelectionCannotIdentifyOneLab(String value) {
        assertThatThrownBy(() -> LabAttachmentReference.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldResolveLegacyNumber_whenOnlyOneSourceBelongsToPatient() {
        PatientLabRoutingDao dao = mock(PatientLabRoutingDao.class);
        PatientLabRouting routing = new PatientLabRouting();
        routing.setDemographicNo(42);
        when(dao.findLabSourcesForPatient(123, 42)).thenReturn(List.of("MDS"));
        when(dao.findByLabNoAndLabType(123, "MDS")).thenReturn(List.of(routing));
        assertThat(LabAttachmentReference.resolve("123", 42, dao).key()).isEqualTo("MDS:123");
    }

    @Test
    void shouldRejectBareNumber_whenTwoSourcesBelongToSamePatient() {
        PatientLabRoutingDao dao = mock(PatientLabRoutingDao.class);
        when(dao.findLabSourcesForPatient(123, 42)).thenReturn(List.of("HL7", "MDS"));
        assertThatThrownBy(() -> LabAttachmentReference.resolve("123", 42, dao))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ambiguous");
    }

    @Test
    void shouldRejectQualifiedLab_whenItBelongsToAnotherPatient() {
        PatientLabRoutingDao dao = mock(PatientLabRoutingDao.class);
        PatientLabRouting routing = new PatientLabRouting();
        routing.setDemographicNo(43);
        when(dao.findByLabNoAndLabType(123, "HL7")).thenReturn(List.of(routing));
        assertThatThrownBy(() -> LabAttachmentReference.resolve("HL7:123", 42, dao))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("patient");
    }

    @Test
    void shouldPreserveUnknownSource_whenReadingLegacyRows() {
        assertThat(LabAttachmentReference.stored(null, 123).key()).isEqualTo("UNRESOLVED:123");
        assertThat(LabAttachmentReference.parse("UNRESOLVED:123").storageSource()).isNull();
    }
}
