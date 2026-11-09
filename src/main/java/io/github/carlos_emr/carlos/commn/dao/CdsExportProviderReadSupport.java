/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawCellDto;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/** Finite public lookup projection; no provider private values or entities enter export capture. */
public final class CdsExportProviderReadSupport {
    private static final Set<String> HISTORY =
            Set.of(
                    "PersonalHistory",
                    "FamilyHistory",
                    "PastHealth",
                    "ProblemList",
                    "RiskFactors",
                    "AlertsAndSpecialNeeds",
                    "ClinicalNotes");
    private static final List<String> COLUMNS =
            List.of(
                    "first_name",
                    "last_name",
                    "ohip_no",
                    "practitionerNo",
                    "practitionerNoType",
                    "provider_no");

    private CdsExportProviderReadSupport() {}

    public static long count(EntityManager em, int patient, Set<String> categories) {
        var rows =
                rows(
                        CdsExportPatientTableReadSupport.query(
                                        em,
                                        "select count(*),1 from provider d where "
                                                + scope(patient, categories))
                                .setParameter("patient", patient));
        if (rows.size() != 1) invalid();
        long result = number(rows.getFirst()[0]);
        if (result < 0) invalid();
        return result;
    }

    public static List<CdsExportProviderRowDto> page(
            EntityManager em, int patient, Set<String> categories, String after, int limit) {
        if (limit < 1 || limit > 32 || after != null && !after.matches("p(?:[0-9a-f]{2}){0,24}"))
            invalid();
        var sql = new StringBuilder("select d.provider_no");
        for (String column : COLUMNS) {
            String value = "CAST(d.`" + column + "` AS BINARY)";
            sql.append(",d.`")
                    .append(column)
                    .append("` IS NULL,OCTET_LENGTH(")
                    .append(value)
                    .append("),SHA2(")
                    .append(value)
                    .append(",256),")
                    .append(value);
        }
        sql.append(" from provider d where ").append(scope(patient, categories));
        if (after != null) sql.append(" and CAST(d.provider_no AS BINARY)>:after");
        sql.append(" order by CAST(d.provider_no AS BINARY)");
        var query =
                CdsExportPatientTableReadSupport.query(em, sql.toString())
                        .setParameter("patient", patient)
                        .setMaxResults(limit);
        if (after != null) query.setParameter("after", HexFormat.of().parseHex(after.substring(1)));
        var result = new ArrayList<CdsExportProviderRowDto>();
        String previous = after;
        for (var row : rows(query)) {
            if (row.length != 1 + 4 * COLUMNS.size() || !(row[0] instanceof String)) invalid();
            String id = (String) row[0];
            String key = "p" + HexFormat.of().formatHex(id.getBytes(StandardCharsets.UTF_8));
            if (previous != null && key.compareTo(previous) <= 0) invalid();
            previous = key;
            var cells = new TreeMap<String, CdsExportRawCellDto>();
            int index = 1;
            for (String column : COLUMNS) {
                long flag = row[index] instanceof Boolean b ? b ? 1 : 0 : number(row[index]);
                if (flag != 0 && flag != 1) invalid();
                boolean missing = flag == 1;
                if (missing
                                && (row[index + 1] != null
                                        || row[index + 2] != null
                                        || row[index + 3] != null)
                        || !missing
                                && (!(row[index + 3] instanceof byte[])
                                        || number(row[index + 1]) > 1024)) invalid();
                cells.put(
                        column,
                        new CdsExportRawCellDto(
                                missing,
                                missing ? 0 : number(row[index + 1]),
                                missing
                                        ? CdsExportRawCellDto.EMPTY_SHA256
                                        : (String) row[index + 2],
                                (byte[]) row[index + 3]));
                index += 4;
            }
            result.add(new CdsExportProviderRowDto(id, cells));
        }
        return List.copyOf(result);
    }

    private static String scope(int patient, Set<String> categories) {
        var supported = new java.util.HashSet<>(HISTORY);
        supported.addAll(
                Set.of(
                        "Demographics",
                        "AllergiesAndAdverseReactions",
                        "Appointments",
                        "MedicationsAndTreatments"));
        if (patient < 1
                || categories == null
                || categories.isEmpty()
                || !supported.containsAll(categories)
                || !java.util.Collections.disjoint(categories, HISTORY)
                        && !categories.containsAll(HISTORY)) invalid();
        var selected = new ArrayList<String>();
        if (categories.contains("Demographics"))
            selected.add(
                    "(exists (select 1 from demographic p where p.demographic_no=:patient"
                            + " and (p.provider_no=d.provider_no or p.roster_enrolled_to=d.provider_no))"
                            + " or exists (select 1 from demographicArchive p where p.demographic_no=:patient"
                            + " and (p.provider_no=d.provider_no or p.roster_enrolled_to=d.provider_no)))");
        if (categories.contains("AllergiesAndAdverseReactions"))
            selected.add(
                    "exists (select 1 from allergies a where a.demographic_no=:patient and a.providerNo=d.provider_no)");
        if (categories.contains("Appointments"))
            selected.add(
                    "(exists (select 1 from appointment a where a.demographic_no=:patient and a.provider_no=d.provider_no)"
                            + " or exists (select 1 from appointmentArchive a where a.demographic_no=:patient and a.provider_no=d.provider_no))");
        if (categories.contains("MedicationsAndTreatments"))
            selected.add(
                    "exists (select 1 from drugs a where a.demographic_no=:patient and a.provider_no=d.provider_no)");
        if (categories.containsAll(HISTORY))
            selected.add(
                    "exists (select 1 from casemgmt_note n where n.demographic_no=:patient"
                            + " and (n.provider_no=d.provider_no or n.signing_provider_no=d.provider_no))");
        // Native collation matches are captured as candidates; the mapper must still prove exact
        // raw reference identity.
        return "(d.provider_no<>'' and (" + String.join(" or ", selected) + "))";
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(jakarta.persistence.Query query) {
        return query.getResultList();
    }

    private static long number(Object value) {
        if (!(value instanceof Number)) invalid();
        try {
            return new BigDecimal(value.toString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException failure) {
            throw new IllegalStateException("CDS_EXPORT_PUBLIC_PROVIDER_SOURCE_INVALID");
        }
    }

    private static void invalid() {
        throw new IllegalStateException("CDS_EXPORT_PUBLIC_PROVIDER_SOURCE_INVALID");
    }
}
