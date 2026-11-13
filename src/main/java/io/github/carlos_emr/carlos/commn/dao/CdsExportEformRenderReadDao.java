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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the eForms the CDS export renders for one patient: the current eForms the chart lists
 * ({@code eform_data} rows of the patient with {@code status} set and not patient-independent,
 * exactly the rows {@code EFormDataDao.findByDemographicIdCurrent(patient, true)} shows), each
 * with a digest of every stored column a render reads and the name of the provider who saved it.
 *
 * <p>Called twice per capture: once in its own read-only transaction before the browser renders
 * (the renders cannot run inside the source snapshot), and once inside the capture's
 * repeatable-read snapshot, where the digest proves each render was made from the very row the
 * capture holds. A row edited, replaced or added in between is told apart and never exported as
 * rendered. Parameterized SQL only; no clinical value is logged.
 *
 * @since 2026-11-13
 */
@Repository
@Transactional(readOnly = true)
public class CdsExportEformRenderReadDao {
    /** More current eForms than this on one chart is not an eForm list a clinic keeps. */
    public static final int MAXIMUM = 100_000;

    private static final String CURRENT =
            "select cast(e.fdid as char), cast(e.fid as char), e.form_name, e.subject,"
                    + " cast(e.demographic_no as char), cast(e.status as char),"
                    + " cast(e.form_date as char), cast(e.form_time as char), e.form_provider,"
                    + " cast(e.patient_independent as char), cast(e.showLatestFormOnly as char),"
                    + " e.roleType, sha2(e.form_data, 256), e.form_data is null"
                    + " from eform_data e"
                    + " where e.demographic_no = :patient and e.status <> 0"
                    + " and e.patient_independent = 0"
                    + " order by e.fdid";

    private static final String COUNT =
            "select count(*), 1 from eform_data e"
                    + " where e.demographic_no = :patient and e.status <> 0"
                    + " and e.patient_independent = 0";

    private static final String PROVIDER =
            "select p.first_name, p.last_name from provider p where p.provider_no = :provider";

    /** The columns of {@link #CURRENT}, in order, as they enter the digest. */
    private static final String[] DIGESTED = {
        "fdid", "fid", "form_name", "subject", "demographic_no", "status", "form_date", "form_time",
        "form_provider", "patient_independent", "showLatestFormOnly", "roleType", "form_data_sha256",
        "form_data_null"
    };

    @PersistenceContext private EntityManager entityManager;

    /**
     * One current eForm: its identity, the digest of every stored column a render reads, and the
     * saving provider's name (SQL NULL when no provider row matches).
     */
    public record Current(int fdid, String digest, String authorFirstName, String authorLastName) {
        public Current {
            if (fdid < 1 || digest == null || !digest.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("CDS_EXPORT_EFORM_RENDER_ROW_INVALID");
        }

        @Override
        public String toString() {
            return "CdsExportEformRenderReadDao.Current[protected]";
        }
    }

    /** The patient's current eForms in identity order. */
    public List<Current> current(int patient) {
        requirePatient(patient);
        List<Object[]> rows =
                rows(
                        CdsExportPatientTableReadSupport.query(entityManager, CURRENT)
                                .setParameter("patient", patient)
                                .setMaxResults(MAXIMUM + 1));
        if (rows.size() > MAXIMUM) throw new IllegalStateException("CDS_EXPORT_EFORM_RENDER_LIMIT");
        var providers = new HashMap<String, String[]>();
        var result = new ArrayList<Current>(rows.size());
        int previous = 0;
        for (Object[] row : rows) {
            if (row.length != DIGESTED.length) throw invalid();
            int fdid;
            try {
                fdid = Integer.parseInt(text(row[0]));
            } catch (NumberFormatException malformed) {
                throw invalid();
            }
            if (fdid <= previous) throw invalid();
            previous = fdid;
            String provider = text(row[8]);
            String[] author =
                    provider == null
                            ? new String[2]
                            : providers.computeIfAbsent(provider, this::author);
            result.add(new Current(fdid, digest(row), author[0], author[1]));
        }
        return List.copyOf(result);
    }

    /** The number of rows {@link #current(int)} returns, read independently. */
    public long count(int patient) {
        requirePatient(patient);
        List<Object[]> found =
                rows(
                        CdsExportPatientTableReadSupport.query(entityManager, COUNT)
                                .setParameter("patient", patient));
        if (found.size() != 1 || !(found.getFirst()[0] instanceof Number number)) throw invalid();
        try {
            long count = new BigDecimal(number.toString()).longValueExact();
            if (count < 0) throw invalid();
            return count;
        } catch (ArithmeticException malformed) {
            throw invalid();
        }
    }

    private String[] author(String provider) {
        List<Object[]> found =
                rows(
                        CdsExportPatientTableReadSupport.query(entityManager, PROVIDER)
                                .setParameter("provider", provider)
                                .setMaxResults(2));
        // provider_no is the primary key; a collation-equal second row cannot name one author.
        if (found.size() != 1) return new String[2];
        return new String[] {text(found.getFirst()[0]), text(found.getFirst()[1])};
    }

    /**
     * SHA-256 over every column a render reads, each labelled, with SQL NULL distinct from empty
     * text; the form HTML enters through its own SHA-256 so it is never transferred twice.
     */
    static String digest(Object[] row) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        for (int i = 0; i < DIGESTED.length; i++) {
            String value = text(row[i]);
            byte[] label = DIGESTED[i].getBytes(StandardCharsets.US_ASCII);
            digest.update(label);
            if (value == null) {
                digest.update((byte) 0);
            } else {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update((byte) 1);
                digest.update(Long.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(bytes);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String text(Object value) {
        if (value == null) return null;
        if (value instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(jakarta.persistence.Query query) {
        return query.getResultList();
    }

    private static void requirePatient(int patient) {
        if (patient < 1) throw invalid();
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("CDS_EXPORT_EFORM_RENDER_SOURCE_INVALID");
    }
}
