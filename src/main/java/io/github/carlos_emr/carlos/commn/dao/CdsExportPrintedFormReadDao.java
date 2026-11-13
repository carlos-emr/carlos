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
package io.github.carlos_emr.carlos.commn.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reference values a printed consultation letter shows that are not part of the patient's
 * record: the clinic letterhead, the consultation service and specialist directory, a
 * health-care-team consultant (its {@code DemographicContact} link and {@code Contact} row),
 * provider names and billing numbers, program names and the appointment-instruction label, plus
 * the patient's current cell phone (kept in {@code demographicExt}). These are the lookups the
 * clinician's own Print makes, read once per letter in a short read-only transaction with bound
 * parameters only. Every method answers {@code null} (never a guess) when the row is absent or the
 * match is not unique.
 *
 * @since 2026-11-13
 */
@Repository
@Transactional(readOnly = true)
public class CdsExportPrintedFormReadDao {
    @PersistenceContext private EntityManager entityManager;

    /** The clinic letterhead: the first clinic row, by number. */
    public record Clinic(
            String name, String address, String city, String province, String postal, String phone, String fax) {}

    /** A directory specialist as the letter prints it. */
    public record Specialist(
            String salutation, String firstName, String lastName, String letters, String address, String phone,
            String fax) {}

    /** A provider's name and billing number. */
    public record Provider(String firstName, String lastName, String billingNumber) {}

    /**
     * A health-care-team link ({@code DemographicContact}) as the chart's consultation Print reads
     * it: the kind of row it names ({@code type}, null when not stored), its category and the id
     * of the row it names, as stored.
     */
    public record TeamLink(Integer type, String category, String contactId) {}

    /**
     * A {@code Contact} row: its stored subtype (the {@code type} discriminator, {@code
     * ProfessionalContact} for a professional) and the values a consultation letter shows.
     */
    public record Contact(
            String subtype, String firstName, String lastName, String address, String address2,
            String city, String postal, String province, String country, String workPhone,
            String workPhoneExtension, String fax, String cpso) {}

    public Clinic clinic() {
        var rows = rows(CdsExportPatientTableReadSupport.query(entityManager,
                "select clinic_name,clinic_address,clinic_city,clinic_province,clinic_postal,clinic_phone,"
                        + "clinic_fax from clinic order by clinic_no")
                .setMaxResults(1));
        if (rows.isEmpty()) return null;
        Object[] row = rows.getFirst();
        return new Clinic(text(row[0]), text(row[1]), text(row[2]), text(row[3]), text(row[4]), text(row[5]),
                text(row[6]));
    }

    public String service(int serviceId) {
        var rows = rows(CdsExportPatientTableReadSupport.query(entityManager,
                "select serviceDesc,1 from consultationServices where serviceId=:id")
                .setParameter("id", serviceId)
                .setMaxResults(2));
        return rows.size() == 1 ? text(rows.getFirst()[0]) : null;
    }

    public Specialist specialist(int specId) {
        var rows = rows(CdsExportPatientTableReadSupport.query(entityManager,
                "select salutation,fName,lName,proLetters,address,phone,fax from professionalSpecialists"
                        + " where specId=:id")
                .setParameter("id", specId)
                .setMaxResults(2));
        if (rows.size() != 1) return null;
        Object[] row = rows.getFirst();
        return new Specialist(text(row[0]), text(row[1]), text(row[2]), text(row[3]), text(row[4]), text(row[5]),
                text(row[6]));
    }

    public Provider provider(String providerNo) {
        var rows = rows(CdsExportPatientTableReadSupport.query(entityManager,
                "select first_name,last_name,ohip_no from provider where provider_no=:id")
                .setParameter("id", providerNo)
                .setMaxResults(2));
        if (rows.size() != 1) return null;
        Object[] row = rows.getFirst();
        return new Provider(text(row[0]), text(row[1]), text(row[2]));
    }

    /** The health-care-team link a consultation request names, by its id. */
    public TeamLink teamLink(int id) {
        var rows = rows(CdsExportPatientTableReadSupport.query(entityManager,
                "select type,category,contactId from DemographicContact where id=:id")
                .setParameter("id", id)
                .setMaxResults(2));
        if (rows.size() != 1) return null;
        Object[] row = rows.getFirst();
        return new TeamLink(row[0] == null ? null : ((Number) row[0]).intValue(), text(row[1]), text(row[2]));
    }

    /** The contact a health-care-team link names, by its id, whatever its subtype. */
    public Contact contact(int id) {
        var rows = rows(CdsExportPatientTableReadSupport.query(entityManager,
                "select type,firstName,lastName,address,address2,city,postal,province,country,workPhone,"
                        + "workPhoneExtension,fax,cpso from Contact where id=:id")
                .setParameter("id", id)
                .setMaxResults(2));
        if (rows.size() != 1) return null;
        Object[] row = rows.getFirst();
        return new Contact(text(row[0]), text(row[1]), text(row[2]), text(row[3]), text(row[4]), text(row[5]),
                text(row[6]), text(row[7]), text(row[8]), text(row[9]), text(row[10]), text(row[11]),
                text(row[12]));
    }

    public String program(int programId) {
        var rows = rows(CdsExportPatientTableReadSupport.query(entityManager,
                "select name,1 from program where id=:id")
                .setParameter("id", programId)
                .setMaxResults(2));
        return rows.size() == 1 ? text(rows.getFirst()[0]) : null;
    }

    /**
     * The label of the appointment instruction the request names. The request binds the label
     * only when its value matches exactly one list item.
     */
    public String appointmentInstruction(String value) {
        var rows = rows(CdsExportPatientTableReadSupport.query(entityManager,
                "select label,1 from LookupListItem where value=:value")
                .setParameter("value", value)
                .setMaxResults(2));
        return rows.size() == 1 ? text(rows.getFirst()[0]) : null;
    }

    /** The patient's current cell phone, chosen as the chart chooses it (newest first). */
    public String cellPhone(int patient) {
        var rows = rows(CdsExportPatientTableReadSupport.query(entityManager,
                "select value,1 from demographicExt where demographic_no=:patient and key_val='demo_cell'"
                        + " order by date_time desc, id desc")
                .setParameter("patient", patient)
                .setMaxResults(1));
        return rows.isEmpty() ? null : text(rows.getFirst()[0]);
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(jakarta.persistence.Query query) {
        return query.getResultList();
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
