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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicPharmacyDao;
import io.github.carlos_emr.carlos.commn.dao.PharmacyInfoDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalContactDao;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDemographicDocumentFixture;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsIdentityDecisionDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsPatientIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsProviderMatchDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The demographic values no Demographics element holds, sent from one CARLOS to another and
 * stored through the patient record: the exporter's own document is read by the importer, whose
 * persister writes the new chart with the real patient, custom and extension DAOs, and the stored
 * row is read back. The receiving chart holds the values the sender's did.
 */
@Tag("integration")
@Tag("cds-import")
@Transactional
class CdsDemographicNativeColumnsIntegrationTest extends CarlosTestBase {
    @TempDir Path directory;

    @Autowired private DemographicDao demographics;
    @Autowired private DemographicCustDao custom;
    @Autowired private DemographicExtDao extensions;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private final CdsFieldParser fields = new CdsFieldParser();

    @Test
    void shouldStoreTheColumnsNoElementHolds_asTheSenderStoredThem() throws Exception {
        var row = CdsExportDemographicDocumentFixture.row();
        // The form defaults: a province and an issuer without an address or card, newsletter
        // "Unknown", country of origin "-1" and the empty referral-doctor wrapper.
        row.put("province", "ON");
        row.put("residentialProvince", "OT");
        row.put("hc_type", "ON");
        row.put("newsletter", "Unknown");
        row.put("country_of_origin", "-1");
        row.put("family_doctor", "<rdohip></rdohip><rd></rd>");
        // The columns no element states, Date Joined the sender's own.
        row.put("alias", "Robbie");
        row.put("pref_name", "Robbie");
        row.put("pronoun", "they/them");
        row.put("gender", "Two-Spirit");
        row.put("date_joined", "2009-04-01");
        row.put("eff_date", "2024-01-15");
        row.put("end_date", "2031-12-31");
        row.put("children", "Two");
        row.put("citizenship", "Canadian");
        row.put("consentToUseEmailForCare", "0");
        row.put("pcn_indicator", "PCN-7");
        row.put("previousAddress", "1 Old Road");
        row.put("sourceOfIncome", "Pension");
        // A current roster state that is not an enrolment event, its provider number the
        // sender's own: the provider is matched from the public columns carried with it.
        row.put("roster_status", "FS");
        row.put("roster_date", "2021-05-06");
        row.put("roster_enrolled_to", "501");
        var providers = mock(CdsProviderResolver.class);
        when(providers.resolve(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(
                        new CdsProviderMatchDto(
                                null, CdsProviderMatchDto.Outcome.SOURCE_IDENTITY_ABSENT));
        when(providers.resolve(eq("123456"), any(), any(), any(), anyBoolean()))
                .thenReturn(
                        new CdsProviderMatchDto(
                                "999998", CdsProviderMatchDto.Outcome.MATCHED_IDENTIFIERS));
        var document =
                CdsExportDemographicDocumentFixture.document(
                        row,
                        List.of(
                                CdsExportDemographicDocumentFixture.property(
                                        1, "rxInteractionWarningLevel", "0")),
                        Map.of("cust1", "501"),
                        List.of(
                                CdsExportDemographicDocumentFixture.provider(
                                        "501", "Nora", "Nurse", "123456", "", "")));
        int patient = write(document, providers);
        entityManager.flush();
        entityManager.clear();
        var stored =
                (Object[])
                        entityManager
                                .createNativeQuery(
                                        "SELECT province, residentialProvince, hc_type, newsletter,"
                                                + " country_of_origin, family_doctor, alias,"
                                                + " pref_name, pronoun, gender, date_joined,"
                                                + " eff_date, end_date, children, citizenship,"
                                                + " consentToUseEmailForCare, pcn_indicator,"
                                                + " previousAddress, sourceOfIncome,"
                                                + " roster_status, roster_date, roster_enrolled_to"
                                                + " FROM demographic WHERE demographic_no = :id")
                                .setParameter("id", patient)
                                .getSingleResult();
        assertThat(stored[0]).isEqualTo("ON");
        assertThat(stored[1]).isEqualTo("OT");
        assertThat(stored[2]).isEqualTo("ON");
        assertThat(stored[3]).isEqualTo("Unknown");
        assertThat(stored[4]).isEqualTo("-1");
        assertThat(stored[5]).isEqualTo("<rdohip></rdohip><rd></rd>");
        assertThat(stored[6]).isEqualTo("Robbie");
        assertThat(stored[7]).isEqualTo("Robbie");
        assertThat(stored[8]).isEqualTo("they/them");
        assertThat(stored[9]).isEqualTo("Two-Spirit");
        assertThat(stored[10]).hasToString("2009-04-01");
        assertThat(stored[11]).hasToString("2024-01-15");
        assertThat(stored[12]).hasToString("2031-12-31");
        assertThat(stored[13]).isEqualTo("Two");
        assertThat(stored[14]).isEqualTo("Canadian");
        assertThat(stored[15]).isIn(false, 0, (byte) 0, (short) 0);
        assertThat(stored[16]).isEqualTo("PCN-7");
        assertThat(stored[17]).isEqualTo("1 Old Road");
        assertThat(stored[18]).isEqualTo("Pension");
        assertThat(stored[19]).isEqualTo("FS");
        assertThat(stored[20]).hasToString("2021-05-06");
        assertThat(stored[21]).isEqualTo("999998");
        assertThat(custom.find(patient).getNurse()).isEqualTo("999998");
        assertThat(extensions.getAllValuesForDemo(patient))
                .containsEntry("rxInteractionWarningLevel", "0");
    }

    /** Validates, prepares and writes the new chart from the document; returns its number. */
    private int write(byte[] document, CdsProviderResolver providers) throws Exception {
        Path root = Files.createTempDirectory(directory, "demographic-");
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        Files.write(root.resolve("patient.xml"), document),
                        root.resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        var record =
                records.stream()
                        .filter(found -> found.section().equals("Demographics"))
                        .findFirst()
                        .orElseThrow();
        var preparation = mock(CdsNativePreparationService.class);
        var prepared = new AtomicReference<CdsDemographicNativeDto>();
        doAnswer(
                        call -> {
                            prepared.set(call.getArgument(5));
                            return null;
                        })
                .when(preparation)
                .store(any(), anyLong(), any(), any(), any(), any());
        var identity =
                new CdsPatientIdentityResolver(fields) {
                    @Override
                    public CdsIdentityDecisionDto resolve(
                            CdsPatientIdentityDto source, CdsImportCommand ignored) {
                        return new CdsIdentityDecisionDto(
                                CdsIdentityDecisionDto.Outcome.NO_MATCH, null);
                    }
                };
        var persister =
                new CdsDemographicPersister(
                        fields,
                        identity,
                        providers,
                        preparation,
                        new CdsDemographicDetailsService(fields, providers),
                        new CdsDemographicRelationsService(
                                fields,
                                mock(ProfessionalContactDao.class),
                                mock(DemographicContactDao.class),
                                mock(PharmacyInfoDao.class),
                                mock(DemographicPharmacyDao.class)),
                        demographics,
                        custom,
                        extensions,
                        mock(AdmissionDao.class));
        var command =
                new CdsImportCommand(
                        directory,
                        "owned-submission",
                        "999998",
                        null,
                        null,
                        "clinic",
                        "pcon-2024-02",
                        false,
                        false,
                        true,
                        0,
                        null,
                        false);
        persister.validate(record, command);
        persister.prepare("run", 1, "unit", record, command);
        when(preparation.load(
                        eq("run"),
                        eq(1L),
                        eq("unit"),
                        any(),
                        eq(command),
                        eq(CdsDemographicNativeDto.class)))
                .thenReturn(prepared.get());
        var notices = new ArrayList<CdsClinicalDiagnosticDto>();
        var references = persister.write("run", 1, "unit", record, command, notices::add);
        assertThat(notices)
                .extracting(CdsClinicalDiagnosticDto::code)
                .doesNotContain(CdsDemographicCarriedValuesResolver.NOT_RESTORED);
        return Integer.parseInt(references.get("demographic"));
    }
}
