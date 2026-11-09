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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDemographicMappingDto;
import java.io.IOException;

/** Typed, schema-ordered base demographic writer; no source reads or implicit defaults. */
public final class CdsExportDemographicXmlWriter {
    public void write(
            CdsExportDemographicMappingDto patient, CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        java.util.Objects.requireNonNull(xml)
                .content(
                        guarded ->
                                writeValue(
                                        patient, guarded, null, java.util.List.of(), null, null));
    }

    public void writeWithPatientNote(
            io.github.carlos_emr.carlos.demographic.cdsexport.dto
                            .CdsExportDemographicNoteProjectionDto
                    value,
            CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        java.util.Objects.requireNonNull(xml)
                .content(
                        guarded -> {
                            if (value == null || !value.representable())
                                throw new IOException(
                                        "CDS_EXPORT_PATIENT_NOTE_MAPPING_REQUIRES_REVIEW");
                            if (value.note() != null
                                    && value.note().text() != null
                                    && !CdsExportNativeFieldAssessments.legal(
                                            value.note().text(), 64000))
                                throw new IOException(
                                        "CDS_EXPORT_PATIENT_NOTE_MAPPING_REQUIRES_REVIEW");
                            writeValue(
                                    value.demographic(),
                                    guarded,
                                    value.note() == null ? null : value.note().text(),
                                    java.util.List.of(),
                                    null,
                                    null);
                        });
    }

    /** The projection binds current roster dispositions to the same capture as its complete native history. */
    public void writeWithEnrolment(
            io.github.carlos_emr.carlos.demographic.cdsexport.dto
                            .CdsExportDemographicEnrolmentProjectionDto
                    value,
            CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        java.util.Objects.requireNonNull(xml)
                .content(
                        guarded -> {
                            if (value == null || !value.representable())
                                throw new IOException(
                                        "CDS_EXPORT_DEMOGRAPHIC_ENROLMENT_REQUIRES_REVIEW");
                            if (value.note() != null
                                    && value.note().text() != null
                                    && !CdsExportNativeFieldAssessments.legal(
                                            value.note().text(), 64000))
                                throw new IOException(
                                        "CDS_EXPORT_PATIENT_NOTE_MAPPING_REQUIRES_REVIEW");
                            writeValue(
                                    value.demographic(),
                                    guarded,
                                    value.note() == null ? null : value.note().text(),
                                    value.events(),
                                    null,
                                    null);
                        });
    }

    /** Both native components must belong to the same patient and the same authenticated capture. */
    public void writeWithPharmacy(
            io.github.carlos_emr.carlos.demographic.cdsexport.dto
                            .CdsExportDemographicEnrolmentProjectionDto
                    value,
            CdsExportPharmacyProjectionService.Result pharmacy,
            CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        java.util.Objects.requireNonNull(xml)
                .content(
                        guarded -> {
                            if (value == null
                                    || !value.representable()
                                    || pharmacy == null
                                    || !pharmacy.mapping().nativeRepresentable()
                                    || value.demographic().patient() != pharmacy.mapping().patient()
                                    || !value.sourceManifestSha256()
                                            .equals(pharmacy.sourceManifestSha256()))
                                throw new IOException(
                                        "CDS_EXPORT_DEMOGRAPHIC_PHARMACY_REQUIRES_REVIEW");
                            if (value.note() != null
                                    && value.note().text() != null
                                    && !CdsExportNativeFieldAssessments.legal(
                                            value.note().text(), 64000))
                                throw new IOException(
                                        "CDS_EXPORT_PATIENT_NOTE_MAPPING_REQUIRES_REVIEW");
                            writeValue(
                                    value.demographic(),
                                    guarded,
                                    value.note() == null ? null : value.note().text(),
                                    value.events(),
                                    pharmacy.mapping(),
                                    null);
                        });
    }

    /** Native associations are joined only after complete component scans, field checks and identical patient/generation bindings. */
    public void writeWithRelated(
            io.github.carlos_emr.carlos.demographic.cdsexport.dto
                            .CdsExportDemographicEnrolmentProjectionDto
                    value,
            CdsExportPharmacyProjectionService.Result pharmacy,
            CdsExportPersonalContactProjectionService.Result contacts,
            CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        writeWithRelated(value, pharmacy, contacts, null, xml);
    }

    /** The coordinator supplies the registered native patient identity; existing component callers retain their numeric identity. */
    public void writeWithRelated(
            io.github.carlos_emr.carlos.demographic.cdsexport.dto
                            .CdsExportDemographicEnrolmentProjectionDto
                    value,
            CdsExportPharmacyProjectionService.Result pharmacy,
            CdsExportPersonalContactProjectionService.Result contacts,
            String senderPatientId,
            CdsExportXmlComposer.ContentWriter xml)
            throws IOException {
        java.util.Objects.requireNonNull(xml)
                .content(
                        guarded -> {
                            if (value == null
                                    || !value.representable()
                                    || pharmacy == null
                                    || !pharmacy.mapping().nativeRepresentable()
                                    || contacts == null
                                    || !contacts.mapping().nativeRepresentable()
                                    || value.demographic().patient() != pharmacy.mapping().patient()
                                    || value.demographic().patient() != contacts.mapping().patient()
                                    || !value.sourceManifestSha256()
                                            .equals(pharmacy.sourceManifestSha256())
                                    || !value.sourceManifestSha256()
                                            .equals(contacts.sourceManifestSha256()))
                                throw new IOException(
                                        "CDS_EXPORT_DEMOGRAPHIC_RELATIONS_REQUIRES_REVIEW");
                            if (value.note() != null
                                    && value.note().text() != null
                                    && !CdsExportNativeFieldAssessments.legal(
                                            value.note().text(), 64000))
                                throw new IOException(
                                        "CDS_EXPORT_PATIENT_NOTE_MAPPING_REQUIRES_REVIEW");
                            if (senderPatientId != null
                                    && !senderPatientId.equals("C" + value.demographic().patient()))
                                throw new IOException("CDS_EXPORT_NATIVE_PATIENT_IDENTITY_INVALID");
                            writeValue(
                                    value.demographic(),
                                    guarded,
                                    value.note() == null ? null : value.note().text(),
                                    value.events(),
                                    pharmacy.mapping(),
                                    contacts.mapping(),
                                    senderPatientId);
                        });
    }

    private void writeValue(
            CdsExportDemographicMappingDto patient,
            CdsExportXmlComposer.ContentWriter xml,
            String patientNote,
            java.util.List<
                            io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                    .CdsExportEnrolmentEventDto>
                    enrolment,
            CdsExportPharmacyMapper.Result pharmacy,
            CdsExportPersonalContactMapper.Result contacts)
            throws IOException {
        writeValue(patient, xml, patientNote, enrolment, pharmacy, contacts, null);
    }

    private void writeValue(
            CdsExportDemographicMappingDto patient,
            CdsExportXmlComposer.ContentWriter xml,
            String patientNote,
            java.util.List<
                            io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                    .CdsExportEnrolmentEventDto>
                    enrolment,
            CdsExportPharmacyMapper.Result pharmacy,
            CdsExportPersonalContactMapper.Result contacts,
            String senderPatientId)
            throws IOException {
        if (patient == null || !patient.baseRowRepresentable())
            throw new IOException("CDS_EXPORT_DEMOGRAPHIC_MAPPING_REQUIRES_REVIEW");
        xml.start("cds", "Names");
        optional(xml, "cds_dt", "NamePrefix", patient.names().prefix());
        xml.start("cds_dt", "LegalName");
        xml.attribute("", "namePurpose", "L");
        name(xml, "FirstName", patient.names().first(), "GIV");
        name(xml, "LastName", patient.names().last(), "FAMC");
        if (patient.names().middle() != null)
            name(xml, "OtherName", patient.names().middle(), "GIV");
        xml.end();
        xml.end();
        xml.element("cds", "DateOfBirth", patient.birthDate().toString());
        if (patient.healthCard() != null) {
            var card = patient.healthCard();
            xml.start("cds", "HealthCard");
            xml.element("cds_dt", "Number", card.number());
            optional(xml, "cds_dt", "Version", card.version());
            optional(
                    xml,
                    "cds_dt",
                    "Expirydate",
                    card.expiry() == null ? null : card.expiry().toString());
            xml.element("cds_dt", "ProvinceCode", card.province());
            xml.end();
        }
        optional(xml, "cds", "ChartNumber", patient.chartNumber());
        xml.element("cds", "Gender", patient.administrativeGender());
        xml.element(
                "cds",
                "UniqueVendorIdSequence",
                senderPatientId == null ? Integer.toString(patient.patient()) : senderPatientId);
        for (var address : patient.addresses()) {
            xml.start("cds", "Address");
            xml.attribute("", "addressType", address.type());
            xml.start("cds_dt", "Structured");
            optional(xml, "cds_dt", "Line1", address.line1());
            optional(xml, "cds_dt", "City", address.city());
            optional(xml, "cds_dt", "CountrySubdivisionCode", address.subdivision());
            if (address.postalCode() != null) {
                xml.start("cds_dt", "PostalZipCode");
                xml.element(
                        "cds_dt",
                        address.subdivision() != null && address.subdivision().startsWith("US-")
                                ? "ZipCode"
                                : "PostalCode",
                        address.postalCode());
                xml.end();
            }
            xml.end();
            xml.end();
        }
        for (var phone : patient.phones()) {
            xml.start("cds", "PhoneNumber");
            xml.attribute("", "phoneNumberType", phone.type());
            xml.element("cds_dt", "phoneNumber", phone.number());
            optional(xml, "cds_dt", "extension", phone.extension());
            xml.end();
        }
        optional(xml, "cds", "PreferredOfficialLanguage", patient.officialLanguage());
        optional(xml, "cds", "PreferredSpokenLanguage", patient.spokenLanguage());
        if (contacts != null) new CdsExportPersonalContactXmlWriter().write(contacts, xml);
        optional(xml, "cds", "NoteAboutPatient", patientNote);
        new CdsExportEnrolmentXmlWriter().write(enrolment, xml);
        if (patient.primaryPhysician() != null) {
            xml.start("cds", "PrimaryPhysician");
            new CdsExportPublicProviderXmlWriter().write(patient.primaryPhysician(), xml, true);
            xml.end();
        }
        optional(xml, "cds", "Email", patient.email());
        xml.start("cds", "PersonStatusCode");
        xml.element(
                "cds",
                patient.enumeratedStatus() ? "PersonStatusAsEnum" : "PersonStatusAsPlainText",
                patient.personStatus());
        xml.end();
        optional(
                xml,
                "cds",
                "PersonStatusDate",
                patient.personStatusDate() == null ? null : patient.personStatusDate().toString());
        optional(xml, "cds", "SIN", patient.sin());
        if (pharmacy != null) new CdsExportPharmacyXmlWriter().write(pharmacy, xml);
    }

    private static void name(
            CdsExportXmlComposer.ContentWriter xml, String element, String value, String type)
            throws IOException {
        xml.start("cds_dt", element);
        xml.element("cds_dt", "Part", value);
        xml.element("cds_dt", "PartType", type);
        xml.end();
    }

    private static void optional(
            CdsExportXmlComposer.ContentWriter xml, String namespace, String element, String value)
            throws IOException {
        if (value != null) xml.element(namespace, element, value);
    }
}
