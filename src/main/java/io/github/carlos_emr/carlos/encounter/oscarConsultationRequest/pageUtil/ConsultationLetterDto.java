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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

/**
 * Everything one consultation request letter prints, already resolved: the values {@link
 * ConsultationPDFCreator} used to look up while drawing (letterhead, clinic, specialist, service,
 * provider names and numbers, the signature and the logo) are explicit here, so the letter can be
 * printed without an HTTP request, a session or a database lookup.
 *
 * <p>The request path builds one from the request it is given, exactly as it printed before. The
 * CDS export builds one from the record it captured. Text values are printed as given; {@code null}
 * text prints as the request path printed it. The signature and logo are bytes the caller read:
 * when a letter asks for one and the bytes are missing or not an image, the creator reports it
 * ({@link ConsultationPDFCreator.Omission}) instead of leaving it out silently.
 *
 * @since 2026-11-13
 */
public record ConsultationLetterDto(
        Locale locale,
        Letterhead letterhead,
        Request request,
        Specialist specialist,
        Patient patient,
        Clinical clinical,
        Footer footer,
        Images images) {

    public ConsultationLetterDto {
        Objects.requireNonNull(locale);
        Objects.requireNonNull(letterhead);
        Objects.requireNonNull(request);
        Objects.requireNonNull(patient);
        Objects.requireNonNull(clinical);
        Objects.requireNonNull(footer);
        Objects.requireNonNull(images);
    }

    /**
     * The letterhead: the resolved name (a program, a provider or the clinic), the letter's own
     * address, phone and fax when it has them, and the clinic's details printed otherwise.
     */
    public record Letterhead(
            String name,
            String address,
            String phone,
            String fax,
            String clinicName,
            String clinicAddress,
            String clinicCity,
            String clinicProvince,
            String clinicPostal,
            String clinicPhone,
            String clinicFax) {}

    /**
     * The request line and reply header: patient-will-book ("1" when set), the referral date as
     * printed, the urgency code, the appointment instruction label and the properties that choose
     * the reply wording.
     */
    public record Request(
            String patientWillBook,
            String referralDate,
            String urgency,
            String serviceName,
            String appointmentInstructionsLabel,
            boolean appointmentInstructionsLookup,
            boolean multisites) {}

    /** The consultant as printed; the whole specialist is {@code null} when the letter has none. */
    public record Specialist(String name, String phone, String fax, String address) {}

    /** The patient block and the appointment as the letter prints them. */
    public record Patient(
            String name,
            String address,
            String phone,
            String cellPhone,
            String workPhone,
            String email,
            String dateOfBirth,
            String sex,
            String healthCardType,
            String healthNumber,
            String healthCardVersion,
            String appointmentDate,
            String appointmentHour,
            String appointmentMinute,
            String appointmentPm,
            String chartNumber) {
        @Override
        public String toString() {
            return "ConsultationLetterDto.Patient[protected]";
        }
    }

    /**
     * The clinical sections, with the clinic's configured titles for concurrent problems and
     * current medications (blank uses the standard title).
     */
    public record Clinical(
            String reason,
            String clinicalInformation,
            String concurrentProblems,
            String currentMedications,
            String allergies,
            String concurrentProblemsTitle,
            String currentMedicationsTitle) {
        @Override
        public String toString() {
            return "ConsultationLetterDto.Clinical[protected]";
        }
    }

    /** The referring practitioner and family doctor lines, with their billing numbers. */
    public record Footer(
            boolean printReferringPractitioner,
            String referringPractitioner,
            String referringNumber,
            boolean printFamilyDoctor,
            String familyDoctor,
            String familyDoctorNumber) {}

    /**
     * The signature and logo. {@code signatureRequested}: the letter carries a signature that is
     * not suppressed. {@code logoRequested}: the clinic prints a logo on consultation letters. The
     * request path names the logo by its file; a request-free caller supplies its bytes.
     */
    public record Images(
            boolean signatureRequested,
            byte[] signature,
            boolean logoRequested,
            File logoFile,
            byte[] logo) {
        public Images {
            signature = signature == null ? null : signature.clone();
            logo = logo == null ? null : logo.clone();
        }

        @Override
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }

        @Override
        public byte[] logo() {
            return logo == null ? null : logo.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Images that
                    && signatureRequested == that.signatureRequested
                    && logoRequested == that.logoRequested
                    && Objects.equals(logoFile, that.logoFile)
                    && java.util.Arrays.equals(signature, that.signature)
                    && java.util.Arrays.equals(logo, that.logo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    signatureRequested,
                    logoRequested,
                    logoFile,
                    java.util.Arrays.hashCode(signature),
                    java.util.Arrays.hashCode(logo));
        }

        @Override
        public String toString() {
            return "ConsultationLetterDto.Images[protected]";
        }
    }

    @Override
    public String toString() {
        return "ConsultationLetterDto[protected]";
    }
}
