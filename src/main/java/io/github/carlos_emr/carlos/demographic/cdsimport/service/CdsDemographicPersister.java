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

import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicNativeDto.Address;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicNativeDto.Phone;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsIdentityDecisionDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsPatientIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPhoneComponents;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Native demographics use only unambiguous full scalar projections prepared outside the clinical transaction. */
@Service
public class CdsDemographicPersister {
    private final CdsFieldParser fields;
    private final CdsPatientIdentityResolver identity;
    private final CdsProviderResolver providers;
    private final CdsNativePreparationService preparation;
    private final CdsDemographicDetailsService details;
    private final CdsDemographicRelationsService relations;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    public CdsDemographicPersister(
            CdsFieldParser fields,
            CdsPatientIdentityResolver identity,
            CdsProviderResolver providers,
            CdsNativePreparationService preparation,
            CdsDemographicDetailsService details,
            CdsDemographicRelationsService relations) {
        this.fields = fields;
        this.identity = identity;
        this.providers = providers;
        this.preparation = preparation;
        this.details = details;
        this.relations = relations;
    }

    public void validate(CdsRecordDto record, CdsImportCommand command) throws IOException {
        var source = identity.source(record, command);
        if (source.firstName() == null
                || source.lastName() == null
                || source.gender() == null
                || source.birthDate() == null)
            throw new CdsImportException("DEMOGRAPHIC_REQUIRED_VALUE_MISSING");
        details.validate(record, command);
        relations.prepare(record, command);
    }

    public void prepare(
            String run, long epoch, String unit, CdsRecordDto record, CdsImportCommand command)
            throws IOException {
        var source = identity.source(record, command);
        Map<String, CdsClinicalDiagnosticDto> warnings = new LinkedHashMap<>();
        Consumer<CdsClinicalDiagnosticDto> notice =
                n -> warnings.putIfAbsent(n.code() + "/" + n.field(), n);
        Map<String, String> scalars = new LinkedHashMap<>();
        Map<String, Integer> scalarLimits =
                Map.of(
                        "HealthCard/Version",
                        3,
                        "ChartNumber",
                        Demographic.CHART_NO_MAX_LENGTH,
                        "Email",
                        Demographic.EMAIL_MAX_LENGTH,
                        "Names/NamePrefix",
                        20,
                        "PreferredOfficialLanguage",
                        3,
                        "SIN",
                        Demographic.SIN_MAX_LENGTH,
                        "PrimaryPhysician/Name/FirstName",
                        250,
                        "PrimaryPhysician/Name/LastName",
                        250,
                        "PrimaryPhysician/OHIPPhysicianId",
                        20,
                        "PrimaryPhysician/PrimaryPhysicianCPSO",
                        20);
        java.util.Set<String> seenScalars = new java.util.HashSet<>();
        Candidate<Address> mailing = new Candidate<>(), residential = new Candidate<>();
        Candidate<Phone> home = new Candidate<>(),
                work = new Candidate<>(),
                cell = new Candidate<>();
        Projection[] current = {null};
        try {
            fields.forEachField(
                    record,
                    field -> {
                        String path = field.path();
                        if (known(field)) {
                            String scalarPath = path.replaceAll("\\[[0-9]+\\]", "");
                            Integer maximum = scalarLimits.get(scalarPath);
                            if (maximum != null && seenScalars.add(scalarPath)) {
                                try {
                                    String value = fields.readLogicalText(record, field, maximum);
                                    if (value != null) scalars.put(scalarPath, value);
                                } catch (CdsInputException capacity) {
                                    if (!"CDS_TARGET_TEXT_CAPACITY_EXCEEDED"
                                            .equals(capacity.code()))
                                        throw new UncheckedIOException(capacity);
                                    notice.accept(
                                            new CdsClinicalDiagnosticDto(
                                                    "CDS_NATIVE_SCALAR_SOURCE_ONLY", scalarPath));
                                } catch (IOException failure) {
                                    throw new UncheckedIOException(failure);
                                }
                            }
                        }
                        boolean address = path.startsWith("Address[");
                        boolean phone = path.startsWith("PhoneNumber[");
                        if (!known(field) || (!address && !phone)) return;
                        String group = path.substring(0, path.indexOf(']') + 1);
                        try {
                            if (current[0] == null || !group.equals(current[0].group)) {
                                if (current[0] != null)
                                    finish(
                                            current[0],
                                            mailing,
                                            residential,
                                            home,
                                            work,
                                            cell,
                                            notice);
                                current[0] = new Projection(group, address);
                            }
                            current[0].accept(record, field, notice);
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                    });
            if (current[0] != null)
                finish(current[0], mailing, residential, home, work, cell, notice);
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        String first = scalars.get("PrimaryPhysician/Name/FirstName"),
                last = scalars.get("PrimaryPhysician/Name/LastName");
        var provider =
                providers.resolve(
                        scalars.get("PrimaryPhysician/OHIPPhysicianId"),
                        scalars.get("PrimaryPhysician/PrimaryPhysicianCPSO"),
                        first,
                        last,
                        command.matchProviderNames());
        if (!provider.matched()
                && provider.outcome()
                        != io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsProviderMatchDto
                                .Outcome.SOURCE_IDENTITY_ABSENT) {
            notice.accept(
                    new CdsClinicalDiagnosticDto(
                            "CDS_PRIMARY_PROVIDER_" + provider.outcome(), "PrimaryPhysician"));
        }
        var detail = details.prepare(record, command);
        var related = relations.prepare(record, command);
        detail.diagnostics().forEach(notice);
        related.diagnostics().forEach(notice);
        var dto =
                new CdsDemographicNativeDto(
                        source.sourceId(),
                        source.firstName(),
                        source.lastName(),
                        source.birthDate().toString(),
                        source.healthNumber(),
                        source.issuer(),
                        source.gender(),
                        source.status(),
                        scalars,
                        mailing.value(),
                        residential.value(),
                        home.value(),
                        work.value(),
                        cell.value(),
                        provider.localProviderNo(),
                        detail,
                        related,
                        List.copyOf(warnings.values()));
        preparation.store(run, epoch, unit, record.effectiveSha256(), command, dto);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Map<String, String> write(
            String run,
            long epoch,
            String unit,
            CdsRecordDto record,
            CdsImportCommand command,
            Consumer<CdsClinicalDiagnosticDto> notices)
            throws IOException {
        var dto =
                preparation.load(
                        run,
                        epoch,
                        unit,
                        record.effectiveSha256(),
                        command,
                        CdsDemographicNativeDto.class);
        dto.diagnostics().forEach(notices);
        var source =
                new CdsPatientIdentityDto(
                        dto.sourceId(),
                        dto.firstName(),
                        dto.lastName(),
                        LocalDate.parse(dto.birthDate()),
                        dto.healthNumber(),
                        dto.issuer(),
                        dto.gender(),
                        dto.status());
        var decision = identity.resolve(source, command);
        if (decision.outcome() == Outcome.UNIQUE_ELIGIBLE) {
            notices.accept(
                    new CdsClinicalDiagnosticDto(
                            "CDS_EXISTING_DEMOGRAPHIC_RETAINED", "Demographics"));
            return Map.of("demographic", decision.targetDemographicNo().toString());
        }
        if (decision.outcome() != Outcome.NO_MATCH)
            throw new CdsImportException("IDENTITY_" + decision.outcome());
        Demographic patient = new Demographic();
        // Hibernate binds this field directly; the nullable getter fallback does not apply.
        // Empty represents an absent preference without inventing an alternate clinical name.
        patient.setPrefName("");
        patient.setFirstName(dto.firstName());
        patient.setLastName(dto.lastName());
        LocalDate birth = LocalDate.parse(dto.birthDate());
        patient.setYearOfBirth(String.format(java.util.Locale.ROOT, "%04d", birth.getYear()));
        patient.setMonthOfBirth(
                String.format(java.util.Locale.ROOT, "%02d", birth.getMonthValue()));
        patient.setDateOfBirth(String.format(java.util.Locale.ROOT, "%02d", birth.getDayOfMonth()));
        patient.setHin(dto.healthNumber() == null ? "" : dto.healthNumber());
        patient.setHcType(dto.issuer() == null ? "" : dto.issuer());
        patient.setSex(dto.gender());
        patient.setPatientStatus(dto.status() == null ? "" : dto.status());
        patient.setProviderNo(dto.primaryProvider() == null ? "" : dto.primaryProvider());
        patient.setDateJoined(new Date());
        patient.setLastUpdateDate(new Date());
        patient.setLastUpdateUser(command.providerNo());
        patient.setVer(dto.scalars().get("HealthCard/Version"));
        patient.setChartNo(dto.scalars().getOrDefault("ChartNumber", ""));
        patient.setEmail(dto.scalars().get("Email"));
        patient.setTitle(dto.scalars().get("Names/NamePrefix"));
        patient.setSin(dto.scalars().get("SIN"));
        String language = dto.scalars().get("PreferredOfficialLanguage");
        patient.setOfficialLanguage(
                "ENG".equals(language) ? "English" : "FRE".equals(language) ? "French" : language);
        patient.setSpokenLanguage(dto.details().spokenLanguage());
        patient.setPatientStatusDate(nativeDate(dto.details().statusDate()));
        patient.setHcRenewDate(nativeDate(dto.details().healthCardExpiry()));
        var roster = dto.details().roster();
        if (roster != null) {
            patient.setRosterStatus(roster.status());
            patient.setRosterDate(nativeDate(roster.date()));
            patient.setRosterTerminationDate(nativeDate(roster.terminationDate()));
            patient.setRosterTerminationReason(roster.terminationReason());
            patient.setRosterEnrolledTo(roster.enrolledProvider());
        }
        applyAddress(patient, dto.mailing(), false);
        applyAddress(patient, dto.residential(), true);
        if (dto.home() != null) patient.setPhone(dto.home().number());
        if (dto.work() != null) patient.setPhone2(dto.work().number());
        entityManager.persist(patient);
        entityManager.flush();
        Map<String, String> result = new LinkedHashMap<>();
        result.put("demographic", patient.getDemographicNo().toString());
        if (dto.details().patientNote() != null) {
            var custom = new DemographicCust();
            custom.setId(patient.getDemographicNo());
            custom.setNotes(DemographicCust.wrapPatientNotes(dto.details().patientNote()));
            entityManager.persist(custom);
            entityManager.flush();
            result.put("demographiccust", patient.getDemographicNo().toString());
        }
        if (dto.relations().familyPhysician() != null
                || dto.relations().referringPhysician() != null
                || dto.relations().preferredPharmacy() != null)
            result.putAll(
                    relations.write(
                            patient.getDemographicNo(), command.providerNo(), dto.relations()));
        if (dto.home() != null)
            extension(patient, command, "hPhoneExt", dto.home().extension(), result);
        if (dto.work() != null)
            extension(patient, command, "wPhoneExt", dto.work().extension(), result);
        if (dto.cell() != null) {
            extension(patient, command, "demo_cell", dto.cell().number(), result);
            if (dto.cell().extension() != null)
                notices.accept(
                        new CdsClinicalDiagnosticDto(
                                "CDS_CELL_EXTENSION_SOURCE_ONLY", "PhoneNumber"));
        }
        if (command.programId() != null) {
            Admission admission = new Admission();
            admission.setClientId(patient.getDemographicNo());
            admission.setProgramId(command.programId());
            admission.setProviderNo(command.providerNo());
            admission.setAdmissionStatus(Admission.STATUS_CURRENT);
            admission.setAdmissionDate(new Date());
            entityManager.persist(admission);
            entityManager.flush();
            result.put("admission", admission.getId().toString());
        }
        return result;
    }

    private static java.sql.Date nativeDate(String value) {
        return value == null ? null : java.sql.Date.valueOf(LocalDate.parse(value));
    }

    private void extension(
            Demographic patient,
            CdsImportCommand command,
            String key,
            String value,
            Map<String, String> references) {
        if (value == null || value.isEmpty()) return;
        DemographicExt row = new DemographicExt();
        row.setDemographicNo(patient.getDemographicNo());
        row.setProviderNo(command.providerNo());
        row.setKey(key);
        row.setValue(value);
        row.setHidden(false);
        entityManager.persist(row);
        entityManager.flush();
        references.put("demographicExt#" + references.size(), row.getId().toString());
    }

    private static void applyAddress(Demographic patient, Address address, boolean residential) {
        if (address == null) return;
        if (residential) {
            patient.setResidentialAddress(address.address());
            patient.setResidentialCity(address.city());
            patient.setResidentialProvince(address.province());
            patient.setResidentialPostal(address.postal());
        } else {
            patient.setAddress(address.address());
            patient.setCity(address.city());
            patient.setProvince(address.province());
            patient.setPostal(address.postal());
        }
    }

    private static boolean known(CdsFieldDto f) {
        return f.namespace().equals("cds")
                || f.namespace().equals("cds_dt")
                || f.kind().equals("ATTRIBUTE") && f.namespace().isEmpty();
    }

    private static <T> void offer(
            Candidate<T> target,
            T value,
            String digest,
            String path,
            Consumer<CdsClinicalDiagnosticDto> notice) {
        target.offer(value, digest);
        if (target.repeated)
            notice.accept(
                    new CdsClinicalDiagnosticDto(
                            target.conflict
                                    ? "CDS_REPEATED_NATIVE_VALUE_AMBIGUOUS"
                                    : "CDS_REPEATED_IDENTICAL_NATIVE_VALUE",
                            path));
    }

    private void finish(
            Projection p,
            Candidate<Address> mailing,
            Candidate<Address> residential,
            Candidate<Phone> home,
            Candidate<Phone> work,
            Candidate<Phone> cell,
            Consumer<CdsClinicalDiagnosticDto> notice) {
        String digest = CdsPaths.hex(p.digest.digest());
        if (p.address) {
            String street = p.values.get("Formatted");
            if (street == null)
                street =
                        java.util.stream.Stream.of(
                                        "Structured/Line1", "Structured/Line2", "Structured/Line3")
                                .map(p.values::get)
                                .filter(Objects::nonNull)
                                .collect(java.util.stream.Collectors.joining("\n"));
            if (street.isEmpty()) street = null;
            if (street != null
                    && street.codePointCount(0, street.length()) > Demographic.ADDRESS_MAX_LENGTH) {
                notice.accept(
                        new CdsClinicalDiagnosticDto("CDS_ADDRESS_STREET_SOURCE_ONLY", "Address"));
                street = null;
            }
            String sourceProvince = p.values.get("Structured/CountrySubdivisionCode");
            String normalizedProvince = CdsPatientIdentityResolver.normalizedIssuer(sourceProvince);
            if (!Objects.equals(sourceProvince, normalizedProvince))
                notice.accept(
                        new CdsClinicalDiagnosticDto(
                                "CDS_CANADIAN_PROVINCE_NORMALIZED", "Address"));
            String postal = p.values.get("Structured/PostalZipCode/PostalCode");
            if (postal == null) postal = p.values.get("Structured/PostalZipCode/ZipCode");
            Address value =
                    new Address(
                            street, p.values.get("Structured/City"), normalizedProvince, postal);
            if ("M".equals(p.kind)) offer(mailing, value, digest, "Address", notice);
            else if ("R".equals(p.kind)) offer(residential, value, digest, "Address", notice);
            else
                notice.accept(
                        new CdsClinicalDiagnosticDto("CDS_ADDRESS_TYPE_SOURCE_ONLY", "Address"));
        } else {
            String number = p.values.get("phoneNumber");
            if (number == null && !p.numberComponentUnavailable) {
                number =
                        CdsPhoneComponents.combine(
                                p.values.get("areaCode"),
                                p.values.get("exchange"),
                                p.values.get("number"));
                notice.accept(
                        new CdsClinicalDiagnosticDto(
                                number == null
                                        ? "CDS_PHONE_COMPONENTS_SOURCE_ONLY"
                                        : "CDS_PHONE_COMPONENTS_COMBINED",
                                "PhoneNumber"));
            }
            if (p.numberComponentUnavailable) number = null;
            if (number != null
                    && number.codePointCount(0, number.length()) > Demographic.PHONE_MAX_LENGTH) {
                number = null;
                notice.accept(
                        new CdsClinicalDiagnosticDto(
                                "CDS_PHONE_NUMBER_SOURCE_ONLY", "PhoneNumber"));
            }
            Phone value = number == null ? null : new Phone(number, p.values.get("extension"));
            if ("R".equals(p.kind)) offer(home, value, digest, "PhoneNumber", notice);
            else if ("W".equals(p.kind)) offer(work, value, digest, "PhoneNumber", notice);
            else if ("C".equals(p.kind)) offer(cell, value, digest, "PhoneNumber", notice);
            else
                notice.accept(
                        new CdsClinicalDiagnosticDto("CDS_PHONE_TYPE_SOURCE_ONLY", "PhoneNumber"));
        }
    }

    private static final class Candidate<T> {
        T value;
        String digest;
        boolean repeated, conflict;

        void offer(T next, String hash) {
            if (digest == null) {
                value = next;
                digest = hash;
            } else {
                repeated = true;
                if (!digest.equals(hash)) {
                    conflict = true;
                    value = null;
                }
            }
        }

        T value() {
            return conflict ? null : value;
        }
    }

    private final class Projection {
        final String group;
        final boolean address;
        final Map<String, String> values = new LinkedHashMap<>();
        final MessageDigest digest = CdsPaths.sha256();
        String kind;
        boolean numberComponentUnavailable;

        Projection(String group, boolean address) {
            this.group = group;
            this.address = address;
        }

        void accept(
                CdsRecordDto record, CdsFieldDto field, Consumer<CdsClinicalDiagnosticDto> notice)
                throws IOException {
            String relative = CdsFieldParser.unindexed(field.path().substring(group.length() + 1));
            if (relative.startsWith("@")) {
                kind = fields.readToken(record, field, 1);
                fingerprint(relative, field.namespace(), kind);
                return;
            }
            int maximum =
                    switch (relative) {
                        case "Formatted" -> Demographic.ADDRESS_MAX_LENGTH;
                        case "Structured/Line1", "Structured/Line2", "Structured/Line3" -> 50;
                        case "Structured/City" -> Demographic.CITY_MAX_LENGTH;
                        case "Structured/CountrySubdivisionCode" -> Demographic.PROVINCE_MAX_LENGTH;
                        case "Structured/PostalZipCode/PostalCode",
                                "Structured/PostalZipCode/ZipCode" ->
                                Demographic.POSTAL_MAX_LENGTH;
                        case "extension" -> 5;
                        case "phoneNumber", "areaCode", "number", "exchange" -> 25;
                        default -> 0;
                    };
            if (maximum == 0) {
                fingerprint(relative, field.namespace(), field.sha256() + record.effectiveSha256());
                return;
            }
            try {
                String logical = fields.readLogicalText(record, field, maximum);
                values.put(relative, logical);
                fingerprint(relative, field.namespace(), logical);
            } catch (CdsInputException capacity) {
                if (!capacity.code().equals("CDS_TARGET_TEXT_CAPACITY_EXCEEDED")) throw capacity;
                fingerprint(relative, field.namespace(), field.sha256() + record.effectiveSha256());
                if (!address
                        && java.util.Set.of("phoneNumber", "areaCode", "number", "exchange")
                                .contains(relative)) numberComponentUnavailable = true;
                notice.accept(
                        new CdsClinicalDiagnosticDto(
                                "CDS_NATIVE_SCALAR_SOURCE_ONLY",
                                address ? "Address" : "PhoneNumber"));
            }
        }

        private void fingerprint(String relative, String namespace, String logical) {
            String valueHash =
                    CdsPaths.hex(
                            CdsPaths.sha256().digest(logical.getBytes(StandardCharsets.UTF_8)));
            digest.update(
                    (relative + "|" + namespace + "|" + valueHash + "\n")
                            .getBytes(StandardCharsets.UTF_8));
        }
    }
}
