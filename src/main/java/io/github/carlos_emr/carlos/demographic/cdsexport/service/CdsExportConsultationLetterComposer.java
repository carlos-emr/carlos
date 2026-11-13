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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPrintedFormReadDao;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.ConsultationLetterDto;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.ConsultationPDFCreator;
import io.github.carlos_emr.carlos.utility.ClinicLogoUtility;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.openpdf.text.DocumentException;
import org.springframework.stereotype.Service;

/**
 * Prints each consultation request of the chart as CARLOS EMR prints it ({@link
 * ConsultationPDFCreator}, request-free), beside its data summary. The request's own values come
 * from the captured record (the request row, its extensions and the patient's captured
 * demographic row); the reference values the chart's Print looks up (clinic letterhead, service,
 * specialist directory or health-care-team contact, provider names and billing numbers, program
 * name, appointment instruction label, the patient's cell phone) are read when it is printed. The
 * stored signature is the patient's captured {@code DigitalSignature} the request names, and the
 * clinic logo is the configured file: neither is ever substituted, and when one cannot be included
 * the letter says so in its Notes and in its data summary's Notes. A configured logo that is not installed
 * is not an omission: the letter prints without a logo, as the clinic's own Print does.
 *
 * <p>A request whose consultant is a health-care-team contact prints that contact as the chart
 * does ({@link #consultant}). One the chart itself cannot print, because the contact it names is
 * gone or cannot be read, is not printed ({@value #CONTACT_UNAVAILABLE}); its data summary carries
 * every value and says why, and the patient is not held.
 *
 * @since 2026-11-13
 */
@Service
public class CdsExportConsultationLetterComposer implements CdsExportDataSummaryOccurrenceService.Printer {
    static final String TABLE = "consultationRequests";
    static final String SUB_CLASS = "Consultation request letter";
    /** A health-care-team consultant the chart's own Print cannot resolve (see {@link #consultant}). */
    static final String CONTACT_UNAVAILABLE = "CONSULTANT_CONTACT_UNAVAILABLE";

    /** The {@code Contact} subtype the chart's Print reads a professional team contact as. */
    private static final String PROFESSIONAL_CONTACT = "ProfessionalContact";
    static final String SIGNATURE_NOTE =
            "The provider's signature image could not be included in the printed letter"
                    + " (SIGNATURE_UNAVAILABLE).";
    static final String LOGO_NOTE =
            "The clinic logo could not be included in the printed letter (LOGO_UNAVAILABLE).";

    /** The largest logo file read. */
    private static final long MAXIMUM_LOGO_BYTES = 4L << 20;

    private final CdsExportPrintedFormReadDao references;

    public CdsExportConsultationLetterComposer(CdsExportPrintedFormReadDao references) {
        this.references = Objects.requireNonNull(references);
    }

    @Override
    public boolean prints(CdsExportDataSummaryPlanner.Group group) {
        return group.kind() == CdsExportDataSummaryRegistry.Kind.CONSULTATION_REQUEST
                && TABLE.equals(group.table())
                && !group.detached();
    }

    @Override
    public String readObject() {
        return "_con";
    }

    @Override
    public CdsExportDataSummaryOccurrenceService.Printed print(
            CdsExportDataSummaryPlanner.Group group, CdsExportDataSummaryOccurrenceService.Plan plan)
            throws IOException {
        var request = group.rows().getFirst();
        if (!TABLE.equals(request.table())) throw new IllegalStateException("CDS_EXPORT_PRINTED_FORM_SCOPE_INVALID");
        var referring = provider(request.text("providerNo"));
        // The logo the chart's Print uses: none unless faxLogoInConsultation is configured.
        Logo logo = CarlosProperties.getInstance().getProperty("faxLogoInConsultation") == null
                ? null
                : logo(IsPropertiesOn.isMultisitesEnable() ? request.text("site_name") : null);
        var letter = letter(group, plan, referring, logo);
        var output = new ByteArrayOutputStream();
        Set<ConsultationPDFCreator.Omission> omissions;
        try {
            omissions = new ConsultationPDFCreator(letter, output).printLetter();
        } catch (DocumentException failed) {
            throw new CdsExportDataSummaryOccurrenceService.PrintRefusal(
                    CdsExportDataSummaryOccurrenceService.RENDER_FAILED);
        }
        var notes = new ArrayList<String>();
        if (omissions.contains(ConsultationPDFCreator.Omission.SIGNATURE)) notes.add(SIGNATURE_NOTE);
        if (omissions.contains(ConsultationPDFCreator.Omission.LOGO)
                || (logo != null && logo.unreadable()))
            notes.add(LOGO_NOTE);
        var metadata = new LinkedHashMap<String, String>();
        CdsExportDateDto event = date(request.text("referalDate"));
        if (event != null) metadata.put("referalDate", "Reports/EventDateTime");
        String author = referring == null ? null : formatted(referring);
        if (author != null) metadata.put("providerNo", "Reports/SourceAuthorPhysician");
        return new CdsExportDataSummaryOccurrenceService.Printed(
                output.toByteArray(), SUB_CLASS, event, author, metadata, notes);
    }

    /** The letter as the chart prints it, from the captured request and current reference values. */
    ConsultationLetterDto letter(
            CdsExportDataSummaryPlanner.Group group,
            CdsExportDataSummaryOccurrenceService.Plan plan,
            CdsExportPrintedFormReadDao.Provider referring,
            Logo logo)
            throws IOException {
        var request = group.rows().getFirst();
        var props = CarlosProperties.getInstance();
        var demographic = plan.demographic();
        boolean multisites = IsPropertiesOn.isMultisitesEnable();
        String letterheadTitle = "";
        for (var row : group.rows())
            if ("consultationRequestExt".equals(row.table()) && "letterheadTitle".equals(row.text("name"))) {
                letterheadTitle = text(row.text("value"));
                break;
            }
        var clinic = references.clinic();
        String clinicName = clinic == null ? null : clinic.name();
        boolean lookup = props.getBooleanProperty("CONSULTATION_APPOINTMENT_INSTRUCTIONS_LOOKUP", "true");
        String instruction = request.text("appointmentInstructions");
        String label = lookup && instruction != null ? references.appointmentInstruction(instruction) : null;
        Integer serviceId = number(request.text("serviceId"));
        String service = serviceId == null ? null : references.service(serviceId);
        var familyDoctor = provider(demographic.get("provider_no"));
        boolean printReferring = props.getBooleanProperty("printPDF_referring_prac", "yes");
        boolean printFamilyDoctor = props.getBooleanProperty("mrp_model", "yes");
        String signatureId = request.text("signature_img");
        boolean signatureRequested = signatureId != null && !signatureId.isBlank();
        String[] time = appointmentTime(request.text("appointmentDate"), request.text("appointmentTime"));
        return new ConsultationLetterDto(
                Locale.ENGLISH,
                new ConsultationLetterDto.Letterhead(
                        letterheadName(text(request.text("letterheadName")), letterheadTitle, clinicName),
                        text(request.text("letterheadAddress")),
                        text(request.text("letterheadPhone")),
                        text(request.text("letterheadFax")),
                        clinicName,
                        clinic == null ? null : clinic.address(),
                        clinic == null ? null : clinic.city(),
                        clinic == null ? null : clinic.province(),
                        clinic == null ? null : clinic.postal(),
                        clinic == null ? null : clinic.phone(),
                        clinic == null ? null : clinic.fax()),
                new ConsultationLetterDto.Request(
                        "1".equals(request.text("patientWillBook")) ? "1" : "0",
                        text(request.text("referalDate")),
                        request.text("urgency"),
                        text(service),
                        text(label),
                        lookup,
                        multisites),
                consultant(request),
                new ConsultationLetterDto.Patient(
                        name(demographic),
                        text(demographic.get("address")) + "\n" + text(demographic.get("city")) + ","
                                + text(demographic.get("province")) + "\n" + text(demographic.get("postal")),
                        text(demographic.get("phone")),
                        text(cellPhone(demographic.get("demographic_no"))),
                        text(demographic.get("phone2")),
                        text(demographic.get("email")),
                        birth(demographic),
                        demographic.get("sex"),
                        text(demographic.get("hc_type")),
                        text(demographic.get("hin")),
                        text(demographic.get("ver")),
                        time[0],
                        time[1],
                        time[2],
                        time[3],
                        text(demographic.get("chart_no"))),
                new ConsultationLetterDto.Clinical(
                        request.text("reason"),
                        request.text("clinicalInfo"),
                        request.text("concurrentProblems"),
                        request.text("currentMeds"),
                        request.text("allergies"),
                        props.getProperty("significantConcurrentProblemsTitle", ""),
                        props.getProperty("currentMedicationsTitle", "")),
                new ConsultationLetterDto.Footer(
                        printReferring,
                        referring == null ? "" : formatted(referring),
                        referring == null ? "" : text(referring.billingNumber()),
                        printFamilyDoctor,
                        familyDoctor == null ? "" : formatted(familyDoctor),
                        familyDoctor == null ? "" : text(familyDoctor.billingNumber())),
                new ConsultationLetterDto.Images(
                        signatureRequested,
                        signatureRequested ? signature(plan, signatureId.strip()) : null,
                        logo != null,
                        null,
                        logo == null ? null : logo.bytes()));
    }

    /** The chart's letterhead name: a program ("prog_n"), a provider by number, or the clinic. */
    private String letterheadName(String stored, String title, String clinicName) {
        if (stored.startsWith("prog_")) {
            Integer program = number(stored.substring(5));
            return program == null ? null : references.program(program);
        }
        if (!"-1".equals(stored) && !stored.equals(clinicName)) {
            var provider = stored.isEmpty() ? null : references.provider(stored);
            if (provider != null && provider.lastName() != null) {
                String first = text(provider.firstName());
                if (!"Dr".equals(title)) first = first.replace("Dr. ", "");
                return first + " " + provider.lastName();
            }
        }
        return clinicName;
    }

    /**
     * The consultant the chart's consultation Print shows ({@code
     * EctConsultationFormRequestUtil.estRequestFromId}). With {@code
     * ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS} on, a request whose health-care-team link
     * ({@code demographicContactId}) is a professional {@code Contact} (type {@code
     * TYPE_CONTACT}, category "professional", any letter case) shows that contact ({@link
     * #teamContact}); a link to a directory specialist, a provider or a personal contact, or to a
     * contact row that is gone, shows the directory specialist the request's {@code specId} names,
     * as every other request does. The link's deleted, active and consent flags are not read: the
     * chart's Print does not read them either.
     *
     * <p>Refused ({@value #CONTACT_UNAVAILABLE}) exactly where the chart's Print fails: the link
     * row is gone or has no type (the request cannot be loaded), or the id it stores is not a
     * number.
     */
    // FindSecBugs IMPROPER_UNICODE: the category is compared case-insensitively exactly as the chart's Print compares it; not a security decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "the category is compared case-insensitively exactly as the chart's Print compares it; not a security decision")
    ConsultationLetterDto.Specialist consultant(CdsExportDataSummaryPlanner.Row request) throws IOException {
        String link = request.text("demographicContactId");
        if (link != null
                && CarlosProperties.getInstance()
                        .getBooleanProperty("ENABLE_HEALTH_CARE_TEAM_IN_CONSULTATION_REQUESTS", "true")) {
            Integer id;
            try {
                id = Integer.valueOf(link);
            } catch (NumberFormatException notAnId) {
                id = null;
            }
            var team = id == null ? null : references.teamLink(id);
            if (team == null || team.type() == null) throw unavailable();
            int contactId;
            try {
                // The chart parses the stored id before it reads anything else.
                contactId = Integer.parseInt(team.contactId());
            } catch (NumberFormatException unreadable) {
                throw unavailable();
            }
            if (team.type() == DemographicContact.TYPE_CONTACT
                    && DemographicContact.CATEGORY_PROFESSIONAL.equalsIgnoreCase(team.category())) {
                var contact = references.contact(contactId);
                if (contact != null) return teamContact(contactId, contact);
            }
        }
        return specialist(number(request.text("specId")));
    }

    /**
     * A professional health-care-team contact as the chart's Print shows it: {@code
     * DemographicContactCreator} starts from the directory specialist whose id is the contact's id
     * (none for an id below 1), keeping its salutation and professional letters, and applies the
     * contact's names, address, phone and fax ({@link
     * ProfessionalSpecialist#applyProfessionalContact}, the same code). Refused ({@value
     * #CONTACT_UNAVAILABLE}) where that fails: the row is not a professional contact (the chart's
     * cast fails), no directory specialist has the contact's id, or the contact has no CPSO
     * number.
     */
    private ConsultationLetterDto.Specialist teamContact(
            int contactId, CdsExportPrintedFormReadDao.Contact found) throws IOException {
        if (!PROFESSIONAL_CONTACT.equals(found.subtype())) throw unavailable();
        var specialist = new ProfessionalSpecialist();
        if (contactId > 0) {
            var directory = references.specialist(contactId);
            if (directory == null) throw unavailable();
            specialist.setSalutation(directory.salutation());
            specialist.setProfessionalLetters(directory.letters());
        }
        if (found.cpso() == null) throw unavailable();
        var contact = new ProfessionalContact();
        contact.setId(contactId);
        contact.setFirstName(found.firstName());
        contact.setLastName(found.lastName());
        contact.setAddress(found.address());
        contact.setAddress2(found.address2());
        contact.setCity(found.city());
        contact.setPostal(found.postal());
        contact.setProvince(found.province());
        contact.setCountry(found.country());
        contact.setWorkPhone(found.workPhone());
        contact.setWorkPhoneExtension(found.workPhoneExtension());
        contact.setFax(found.fax());
        contact.setCpso(found.cpso());
        specialist.applyProfessionalContact(contact);
        return new ConsultationLetterDto.Specialist(
                specialist.getFormattedTitle(),
                specialist.getPhoneNumber(),
                specialist.getFaxNumber(),
                specialist.getStreetAddress());
    }

    private static CdsExportDataSummaryOccurrenceService.PrintRefusal unavailable() {
        return new CdsExportDataSummaryOccurrenceService.PrintRefusal(CONTACT_UNAVAILABLE);
    }

    private ConsultationLetterDto.Specialist specialist(Integer specId) {
        var found = specId == null ? null : references.specialist(specId);
        if (found == null) return null;
        var title = new ProfessionalSpecialist();
        title.setSalutation(found.salutation());
        title.setFirstName(found.firstName());
        title.setLastName(found.lastName());
        title.setProfessionalLetters(found.letters());
        return new ConsultationLetterDto.Specialist(
                title.getFormattedTitle(), found.phone(), found.fax(), found.address());
    }

    private CdsExportPrintedFormReadDao.Provider provider(String providerNo) {
        return providerNo == null || providerNo.isBlank() ? null : references.provider(providerNo);
    }

    private String cellPhone(String patient) {
        Integer number = number(patient);
        return number == null ? null : references.cellPhone(number);
    }

    /**
     * The stored signature the request names: the patient's captured {@code DigitalSignature}
     * row with that id, as stored. Null (reported, never substituted) when the id is not a stored
     * signature of this chart.
     */
    static byte[] signature(CdsExportDataSummaryOccurrenceService.Plan plan, String id) {
        if (!id.matches("[1-9][0-9]{0,9}")) return null;
        for (var group : plan.groups())
            if ("DigitalSignature".equals(group.table()) && id.equals(group.id()) && !group.rows().isEmpty()) {
                var image = group.rows().getFirst().values().get("signatureImage");
                return image == null ? null : image.content();
            }
        return null;
    }

    /** The configured consultation logo file, or null (reported) when it cannot be read. */
    /**
     * The configured logo: its bytes, or {@code unreadable} when an installed file could not be
     * read (larger than {@link #MAXIMUM_LOGO_BYTES} or an I/O failure). No installed file is
     * neither: the letter prints without a logo and nothing is reported.
     */
    static final class Logo {
        private final byte[] bytes;
        private final boolean unreadable;

        Logo(byte[] bytes, boolean unreadable) {
            this.bytes = bytes;
            this.unreadable = unreadable;
        }

        byte[] bytes() {
            return bytes;
        }

        boolean unreadable() {
            return unreadable;
        }
    }

    private static Logo logo(String siteName) {
        File file;
        try {
            file = ClinicLogoUtility.resolveConsultationLogo(siteName);
        } catch (RuntimeException unresolved) {
            return new Logo(null, true);
        }
        if (file == null) return new Logo(null, false);
        try {
            if (Files.size(file.toPath()) > MAXIMUM_LOGO_BYTES) return new Logo(null, true);
            return new Logo(Files.readAllBytes(file.toPath()), false);
        } catch (IOException | RuntimeException unreadable) {
            return new Logo(null, true);
        }
    }

    /** "LAST, FIRST", with the alias in brackets when there is one. */
    static String name(Map<String, String> demographic) {
        String name = demographic.get("last_name") + ", " + demographic.get("first_name");
        String alias = demographic.get("alias");
        return alias == null || alias.isEmpty() ? name : name + " (" + alias + ")";
    }

    /** The date of birth as the chart formats it (yyyy-MM-dd), or empty. */
    static String birth(Map<String, String> demographic) {
        Integer year = number(demographic.get("year_of_birth"));
        Integer month = number(demographic.get("month_of_birth"));
        Integer day = number(demographic.get("date_of_birth"));
        if (year == null || month == null || day == null) return "";
        try {
            return LocalDate.of(year, month, day).toString();
        } catch (java.time.DateTimeException invalid) {
            return "";
        }
    }

    /** Date, hour (1-12), minute (two digits) and AM/PM, as the chart's request form splits them. */
    static String[] appointmentTime(String date, String time) {
        if (date == null || date.isEmpty()) return new String[] {"", "", "", ""};
        if (time == null) return new String[] {date, "", "", ""};
        try {
            var clock = LocalTime.parse(time);
            int hour = clock.getHour() % 12;
            return new String[] {
                date,
                hour == 0 ? "12" : Integer.toString(hour),
                String.format(Locale.ROOT, "%02d", clock.getMinute()),
                clock.getHour() < 12 ? "AM" : "PM"
            };
        } catch (DateTimeParseException invalid) {
            return new String[] {date, "", "", ""};
        }
    }

    private static CdsExportDateDto date(String value) {
        if (value == null) return null;
        try {
            return new CdsExportDateDto(CdsExportDateDto.Precision.DATE, LocalDate.parse(value).toString());
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    private static String formatted(CdsExportPrintedFormReadDao.Provider provider) {
        return provider.lastName() + ", " + provider.firstName();
    }

    private static Integer number(String value) {
        if (value == null || !value.matches("-?[0-9]{1,9}")) return null;
        return Integer.valueOf(value);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
