/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

/** Bound legacy lab matching queries; reads join the same transaction as lab/routing writes. */
@Repository
public class LabRoutingLookupDao extends AbstractDaoImpl<Demographic> {
    private static final Set<String> PROVIDER_COLUMNS = Set.of(
            "ohip_no", "provider_no", "last_name", "first_name", "practitionerno", "hso_no");

    public LabRoutingLookupDao() { super(Demographic.class); }

    public record Patient(int demographicNo, String providerNo) { }
    public record ProviderIdentifier(String providerNo, String ohipNo) { }

    public List<String> findProviders(String column, String identifier, boolean ontario,
                                      Integer limit, boolean orderByLength) {
        String selected = column == null ? "ohip_no" : column.trim().toLowerCase(Locale.ROOT);
        if (!PROVIDER_COLUMNS.contains(selected)) selected = "ohip_no";
        String value = identifier;
        if (ontario) {
            value = value.trim();
            if (selected.equals("ohip_no")) value = StringUtils.leftPad(value, 6, '0');
        }
        Query query = entityManager.createNativeQuery("SELECT provider_no FROM provider WHERE " + selected
                + (ontario ? " = ?1" : " LIKE ?1")
                + (orderByLength ? " ORDER BY LENGTH(first_name)" : ""));
        query.setParameter(1, value);
        if (limit != null && limit > 0) query.setMaxResults(limit);
        List<?> rows = query.getResultList();
        return rows.stream().map(row -> StringUtils.trimToEmpty((String) row)).toList();
    }

    public List<ProviderIdentifier> findCmlProviderIdentifiers() {
        List<?> rows = entityManager.createNativeQuery(
                "SELECT provider_no, ohip_no FROM provider WHERE ohip_no != ''").getResultList();
        return rows.stream().map(value -> {
            Object[] row = (Object[]) value;
            return new ProviderIdentifier(StringUtils.trimToEmpty((String) row[0]),
                    StringUtils.trimToEmpty((String) row[1]));
        }).toList();
    }

    public List<Patient> findHl7Patients(String lastName, String firstName, String sex, String dob,
                                       String hin, boolean ignoreNames) {
        // Legacy HL7 matching rejects null names before it can broaden a name-prefix query.
        java.util.Objects.requireNonNull(lastName, "HL7 patient surname");
        java.util.Objects.requireNonNull(firstName, "HL7 patient given name");
        String health = hin;
        if ("UNKNOWN".equalsIgnoreCase(health)) health = "";
        if (health != null && health.length() == 12 && StringUtils.isNumeric(health.substring(0, 10))) {
            health = health.substring(0, 10);
        }
        String year = "%", month = "%", day = "%";
        boolean hasDob = dob != null && !dob.isEmpty() && !dob.equalsIgnoreCase("UNKNOWN");
        if (hasDob) {
            String[] parts = dob.trim().split("-");
            year = parts[0]; month = parts[1]; day = parts[2];
        }
        StringBuilder sql = new StringBuilder("SELECT demographic_no, provider_no FROM demographic WHERE ");
        List<String> params = new ArrayList<>();
        if (health == null || health.trim().isEmpty()) {
            if (!hasDob || StringUtils.isBlank(lastName) || StringUtils.isBlank(firstName)
                    || StringUtils.isBlank(sex) || "UNKNOWN".equalsIgnoreCase(sex)) return List.of();
            sql.append("year_of_birth LIKE ?1 AND month_of_birth LIKE ?2 AND date_of_birth LIKE ?3"
                    + " AND (sex LIKE ?4 OR sex NOT IN ('F','M')) AND last_name = ?5 AND first_name = ?6");
            params.addAll(List.of(year, month, day, sex + "%", lastName, firstName));
        } else {
            sql.append("hin=?1"); params.add(health);
            if (!ignoreNames) {
                sql.append(" AND last_name LIKE ?2 AND first_name LIKE ?3");
                params.add(prefix(lastName)); params.add(prefix(firstName));
            }
            int i = params.size() + 1;
            sql.append(" AND year_of_birth LIKE ?").append(i++)
                    .append(" AND month_of_birth LIKE ?").append(i++)
                    .append(" AND date_of_birth LIKE ?").append(i++)
                    .append(" AND (sex LIKE ?").append(i).append(" OR sex NOT IN ('F','M'))");
            params.addAll(List.of(year, month, day, sex + "%"));
        }
        return patients(sql.toString(), params);
    }

    public List<Patient> findCmlPatients(String hin, String year, String month, String day, String sex,
                                       String lastName, String firstName, boolean ignoreNames) {
        StringBuilder sql = new StringBuilder("SELECT demographic_no, provider_no FROM demographic WHERE hin=?1");
        List<String> params = new ArrayList<>(); params.add(hin);
        if (!ignoreNames) {
            sql.append(" AND last_name LIKE ?2 AND first_name LIKE ?3");
            params.add(prefix(lastName)); params.add(prefix(firstName));
        }
        int i = params.size() + 1;
        sql.append(" AND year_of_birth LIKE ?").append(i++).append(" AND month_of_birth LIKE ?").append(i++)
                .append(" AND date_of_birth LIKE ?").append(i++).append(" AND sex LIKE ?").append(i);
        params.addAll(List.of(year, month, day, sex + "%"));
        return patients(sql.toString(), params);
    }

    private List<Patient> patients(String sql, List<String> params) {
        Query query = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.size(); i++) query.setParameter(i + 1, params.get(i));
        List<?> rows = query.getResultList();
        return rows.stream().map(value -> {
            Object[] row = (Object[]) value;
            return new Patient(((Number) row[0]).intValue(), StringUtils.trimToEmpty((String) row[1]));
        }).toList();
    }

    private static String prefix(String value) {
        return value == null || value.isEmpty() ? "%" : value.substring(0, 1) + "%";
    }
}
