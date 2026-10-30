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

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsIdentityDecisionDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsIdentityDecisionDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsPatientIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsHealthCardAuthority;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Parameterized, bounded identity queries, repeated inside the actual demographic mutation unit. */
@Service
public class CdsPatientIdentityResolver {
    private final CdsFieldParser fields;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

    public CdsPatientIdentityResolver(CdsFieldParser fields) { this.fields = fields; }

    public CdsPatientIdentityDto source(CdsRecordDto record, CdsImportCommand command) throws IOException {
        if (!"Demographics".equals(record.section())) throw new CdsImportException("DEMOGRAPHICS_REQUIRED");
        String dob = text(record, "DateOfBirth", 64);
        if (dob == null) throw new CdsImportException("EXACT_BIRTH_DATE_REQUIRED");
        var birthValue = CdsTemporal.parse(null, null, dob, null, "DateOfBirth");
        if (command.synthetic()) birthValue = CdsTemporal.shift(birthValue, command.timeshiftDays(), "DateOfBirth");
        var birth = birthValue.earliest();
        String healthNumber = text(record, "HealthCard/Number", 20);
        if (healthNumber != null) healthNumber = healthNumber.replace(" ", "");
        String issuer = normalizedIssuer(text(record, "HealthCard/ProvinceCode", 20));
        String status = text(record, "PersonStatusCode/PersonStatusAsEnum", 1);
        if (status != null) status = switch (status) { case "A" -> "AC"; case "I" -> "IN"; case "D" -> "DE"; default -> status; };
        else status = text(record, "PersonStatusCode/PersonStatusAsPlainText", 50);
        return new CdsPatientIdentityDto(text(record, "UniqueVendorIdSequence", 20),
                text(record, "Names/LegalName/FirstName/Part", 50),
                text(record, "Names/LegalName/LastName/Part", 50), birth, healthNumber, issuer,
                text(record, "Gender", 2), status);
    }

    @Transactional(readOnly = true)
    public CdsIdentityDecisionDto resolve(CdsPatientIdentityDto source, CdsImportCommand command) {
        if (source.sourceId() == null || source.sourceId().isBlank() || source.birthDate() == null) return result(Outcome.INVALID_IDENTITY);
        // Each authorized learner receives an independent synthetic chart in a separate program.
        // Normal patient/HIN merge rules must not collapse those explicitly requested copies.
        if (command.synthetic()) return result(Outcome.NO_MATCH);
        if (source.healthNumber() == null || source.healthNumber().isBlank()) return nameFallback(source);
        String issuer = CdsHealthCardAuthority.known(source.issuer());
        if (issuer == null) {
            // An unknown source authority cannot distinguish any same-HIN native row.
            return anyHealthNumber(source.healthNumber()) ? result(Outcome.CONFLICTING_IDENTITY) : nameFallback(source);
        }
        // Unknown native authorities remain possible matches and must never be discarded to force uniqueness.
        if (unknownAuthority(source.healthNumber())) return result(Outcome.CONFLICTING_IDENTITY);
        List<Demographic> candidates = authorityCandidates(source.healthNumber(), issuer);
        if (candidates.isEmpty()) return result(Outcome.NO_MATCH);
        if (candidates.size() > 1) return result(Outcome.AMBIGUOUS);
        Demographic target = candidates.getFirst();
        if (!source.birthDate().equals(birthDate(target))) return result(Outcome.CONFLICTING_IDENTITY);
        if (!command.mergeExisting()) return result(Outcome.INELIGIBLE);
        Long merged = entityManager.createQuery(
                "select count(m.id) from DemographicMerged m where m.demographicNo=:target and m.deleted=0", Long.class)
                .setParameter("target", target.getDemographicNo()).getSingleResult();
        if (merged != 0 || !"AC".equals(target.getPatientStatus())) return result(Outcome.INELIGIBLE);
        if (source.status() != null && !"AC".equals(source.status())) return result(Outcome.CONFLICTING_IDENTITY);
        if (command.programId() != null) {
            Long admission = entityManager.createQuery("select count(a.id) from Admission a "
                    + "where a.clientId=:target and a.programId=:program and a.admissionStatus='current'", Long.class)
                    .setParameter("target", target.getDemographicNo()).setParameter("program", command.programId()).getSingleResult();
            if (admission == 0) return result(Outcome.INELIGIBLE);
        }
        return new CdsIdentityDecisionDto(Outcome.UNIQUE_ELIGIBLE, target.getDemographicNo());
    }

    private CdsIdentityDecisionDto nameFallback(CdsPatientIdentityDto source) {
        String year = String.format(java.util.Locale.ROOT, "%04d", source.birthDate().getYear());
        String month = String.format(java.util.Locale.ROOT, "%02d", source.birthDate().getMonthValue());
        String day = String.format(java.util.Locale.ROOT, "%02d", source.birthDate().getDayOfMonth());
        List<Integer> names = entityManager.createQuery("select d.demographicNo from Demographic d "
                    + "where d.firstName=:first and d.lastName=:last and d.yearOfBirth=:year "
                    + "and d.monthOfBirth in (:month,:shortMonth) and d.dateOfBirth in (:day,:shortDay)", Integer.class)
                    .setParameter("first", source.firstName()).setParameter("last", source.lastName())
                    .setParameter("year", year).setParameter("month", month).setParameter("day", day)
                    .setParameter("shortMonth", Integer.toString(source.birthDate().getMonthValue()))
                    .setParameter("shortDay", Integer.toString(source.birthDate().getDayOfMonth()))
                    .setMaxResults(2).getResultList();
        return result(names.isEmpty() ? Outcome.NO_MATCH : Outcome.INVALID_IDENTITY);
    }
    private List<Demographic> authorityCandidates(String hin, String issuer) {
        // Inactive/merged rows count as contradictions too; DOB/name are never used to discard duplicates.
        return entityManager.createQuery("select d from Demographic d where d.cdsIdentityHin=:hin and d.cdsIdentityIssuer=:issuer", Demographic.class)
                .setParameter("hin", hin).setParameter("issuer", issuer).setMaxResults(2).getResultList();
    }
    private boolean unknownAuthority(String hin) {
        return !entityManager.createQuery("select d.demographicNo from Demographic d where d.cdsIdentityHin=:hin and d.cdsIdentityIssuer is null", Integer.class)
                .setParameter("hin", hin).setMaxResults(1).getResultList().isEmpty();
    }
    private boolean anyHealthNumber(String hin) {
        return !entityManager.createQuery("select d.demographicNo from Demographic d where d.cdsIdentityHin=:hin", Integer.class)
                .setParameter("hin", hin).setMaxResults(1).getResultList().isEmpty();
    }

    private String text(CdsRecordDto record, String path, int maximum) throws IOException {
        return fields.firstText(record, path, maximum);
    }

    /** Exact completed provenance replay, distinct from permission to merge into an ordinary existing chart. */
    @Transactional(readOnly = true)
    public boolean matchesCanonical(CdsPatientIdentityDto source, int targetNo, CdsImportCommand command) {
        Demographic target = entityManager.find(Demographic.class, targetNo);
        if (target == null || !Objects.equals(source.birthDate(), birthDate(target))
                || !Objects.equals(source.firstName(), target.getFirstName())
                || !Objects.equals(source.lastName(), target.getLastName())
                || !Objects.equals(source.gender(), target.getSex())
                || !Objects.equals(source.status() == null ? "" : source.status(), target.getPatientStatus())
                || !Objects.equals(source.healthNumber() == null ? "" : source.healthNumber(),
                        target.getHin() == null ? "" : target.getHin().replace(" ", ""))
                || !Objects.equals(source.issuer() == null ? "" : normalizedIssuer(source.issuer()),
                        target.getHcType() == null ? "" : normalizedIssuer(target.getHcType()))) return false;
        if (!command.synthetic() && source.healthNumber()!=null && !source.healthNumber().isBlank()) {
            String issuer=CdsHealthCardAuthority.known(source.issuer());
            if (issuer==null) {
                List<Integer> matches=entityManager.createQuery("select d.demographicNo from Demographic d where d.cdsIdentityHin=:hin", Integer.class)
                        .setParameter("hin", source.healthNumber()).setMaxResults(2).getResultList();
                if (matches.size()!=1 || !matches.getFirst().equals(targetNo)) return false;
            } else {
                if (unknownAuthority(source.healthNumber())) return false;
                List<Demographic> matches=authorityCandidates(source.healthNumber(), issuer);
                if (matches.size()!=1 || matches.getFirst().getDemographicNo()!=targetNo) return false;
            }
        }
        Long merged = entityManager.createQuery("select count(m.id) from DemographicMerged m "
                + "where m.demographicNo=:target and m.deleted=0", Long.class)
                .setParameter("target", targetNo).getSingleResult();
        if (merged != 0) return false;
        if (command.programId() != null) {
            Long admission = entityManager.createQuery("select count(a.id) from Admission a "
                    + "where a.clientId=:target and a.programId=:program and a.admissionStatus='current'", Long.class)
                    .setParameter("target", targetNo).setParameter("program", command.programId()).getSingleResult();
            if (admission == 0) return false;
        }
        return true;
    }

    /** Legacy rows can store 2 or 02. Invalid native dates are conflicting identity, never NO_MATCH. */
    static java.time.LocalDate birthDate(Demographic patient) {
        try {
            return java.time.LocalDate.of(Integer.parseInt(patient.getYearOfBirth()),
                    Integer.parseInt(patient.getMonthOfBirth()), Integer.parseInt(patient.getDateOfBirth()));
        } catch (java.time.DateTimeException | NumberFormatException invalid) { return null; }
    }

    /** Exact Canadian jurisdiction aliases, never a name-based or country-ambiguous guess. */
    static String normalizedIssuer(String value) {
        return CdsHealthCardAuthority.normalize(value);
    }

    private static CdsIdentityDecisionDto result(Outcome outcome) { return new CdsIdentityDecisionDto(outcome, null); }
}
