/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada

 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 * Modifications by CARLOS Contributors, 2026.
 */

package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.mds.data.PatientInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Inbox count SQL and typed projections. Query state is local to each invocation.
 * @since 2026-11-06
 */
@Repository
@Transactional(readOnly = true)
public class InboxCategoryDao {
    private static final java.time.format.DateTimeFormatter INBOX_DATE =
            java.time.format.DateTimeFormatter.ofPattern("uuuu-M-d")
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT);

    /**
     * Parses an inclusive inbox date bound shared by count and content requests.
     * @param date optional padded or unpadded ISO calendar date
     * @return the calendar date, or null for a missing/blank bound
     * @throws java.time.format.DateTimeParseException if a nonblank bound is invalid
     */
    public static java.time.LocalDate parseDateBound(String date) {
        return StringUtils.isBlank(date) ? null : java.time.LocalDate.parse(date, INBOX_DATE);
    }

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;
    private final SystemPreferencesDao preferences;

    /** @param preferences SystemPreferencesDao supplying inbox date-search preferences */
    public InboxCategoryDao(SystemPreferencesDao preferences) { this.preferences = preferences; }

    /**
     * Immutable request filters; SQL values are bound separately from the query shape.
     * @param lastName String optional surname fragment
     * @param firstName String optional given-name fragment
     * @param healthNumber String optional HIN fragment
     * @param patientSearch boolean whether patient filters apply
     * @param providerSearch boolean whether provider filtering applies
     * @param providerNo String provider identifier
     * @param status String optional routing status
     * @param abnormalStatus String optional normal/abnormal restriction
     * @param startDate String optional inclusive start date
     * @param endDate String optional inclusive end date
     */
    public record Filter(String lastName, String firstName, String healthNumber, boolean patientSearch,
                         boolean providerSearch, String providerNo, String status, String abnormalStatus,
                         String startDate, String endDate) { }
    /** Selects the lab, document or HRM count and patient-summary operation. */
    public enum Operation { LAB_UNMATCHED, ABNORMAL, NORMAL, DOCUMENT_UNMATCHED, LAB_PATIENT,
                            LAB_DEMOGRAPHIC, DOCUMENT_PATIENT, HRM_PATIENT, HRM_UNMATCHED }
    /**
     * One patient summary returned by a count operation.
     * @param id int demographic identifier
     * @param firstName String given name
     * @param lastName String surname
     * @param labs int lab count
     * @param documents int document count
     * @param hrm int HRM count
     */
    public record PatientCount(int id, String firstName, String lastName, int labs, int documents, int hrm) { }
    /**
     * Immutable count result.
     * @param total int total matching items
     * @param patients List&lt;PatientCount&gt; associated patient summaries
     */
    public record Counts(int total, List<PatientCount> patients) {
        /** Copies the patient list to prevent caller mutation. */
        public Counts { patients = List.copyOf(patients); }
    }

    /** @return String current date-search preference, defaulting to serviceObservation */
    public String getDateSearchType() {
        var preference = preferences.findPreferenceByName(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType);
        return preference == null || StringUtils.isEmpty(preference.getValue()) ? "serviceObservation" : preference.getValue();
    }

    /**
     * Counts one category using a consistent per-invocation date preference.
     * @param filter Filter bound search values
     * @param operation Operation count category to execute
     * @param demographicNo String patient identifier for LAB_DEMOGRAPHIC, otherwise unused
     * @return Counts immutable total and patient summaries
     * @throws SQLException if the count cannot be read
     * @throws IllegalArgumentException if LAB_DEMOGRAPHIC has a blank demographicNo
     */
    public Counts count(Filter filter, Operation operation, String demographicNo) throws SQLException {
        QueryState state = new QueryState(entityManager, preferences, filter.lastName(), filter.firstName(),
                filter.healthNumber(), filter.patientSearch(), filter.providerSearch(), filter.providerNo(),
                filter.status(), filter.abnormalStatus(), filter.startDate(), filter.endDate());
        int total = switch (operation) {
            case LAB_UNMATCHED -> state.getLabCountForUnmatched();
            case ABNORMAL -> state.getAbnormalCount(true);
            case NORMAL -> state.getAbnormalCount(false);
            case DOCUMENT_UNMATCHED -> state.getDocumentCountForUnmatched();
            case LAB_PATIENT -> state.getLabCountForPatientSearch();
            case LAB_DEMOGRAPHIC -> state.getLabCountForDemographic(demographicNo);
            case DOCUMENT_PATIENT -> state.getDocumentCountForPatientSearch();
            case HRM_PATIENT -> state.getHRMDocumentCountForPatient();
            case HRM_UNMATCHED -> state.getHRMDocumentCountForUnmatched();
        };
        return new Counts(total, state.patients.values().stream().map(patient -> new PatientCount(patient.getId(),
                patient.getFirstName(), patient.getLastName(), patient.getLabCount(), patient.getDocCount(), patient.getHrmCount())).toList());
    }

    private static final class QueryState {
        private static final String COUNT_COLUMN = "count";
        private static final String SQL_EQUALS_PARAM = " = ? ";
        private static final String SQL_LIKE_PARAM = " like ? ";
        private static final String SQL_IS_NOT_NULL = " IS NOT NULL ";
        private static final String SQL_IS_NOT_NULL_NO_PREFIX = "IS NOT NULL ";

        private final String dateSearchType;

        private final EntityManager entityManager;

        private final HashMap<Integer, PatientInfo> patients;

        private final String patientLastName;
        private final String searchProviderNo;
        private final String status;
        private final String abnormalStatus;
        private final String patientFirstName;
        private final String patientHealthNumber;
        private final boolean patientSearch;
        private final boolean providerSearch;
        private String documentDateSql = "";
        private String documentAbnormalSql = "";
        private String documentJoinSql = "";
        private String labDateSql = "";
        private String labAbnormalSql = "";
        private String hrmDateSql = "";
        private String hrmProviderSql = "";
        private String hrmSignedOff = "";

        private final List<String> labDateParams = new ArrayList<>();
        private final List<String> documentDateParams = new ArrayList<>();
        private final java.util.Map<String, Object> hrmParams = new java.util.LinkedHashMap<>();

        /**
         * Prepares inbox count filters without executing the count queries.
         *
         * @param patientLastName optional patient surname search
         * @param patientFirstName optional patient given-name search
         * @param patientHealthNumber optional patient health-number search
         * @param patientSearch whether patient search restrictions apply
         * @param providerSearch whether to restrict results to the selected provider
         * @param searchProviderNo provider identifier; "0" selects unassigned HRM reports
         * @param status HRM: empty selects all, null/N unsigned, A/F signed off;
         *               document and lab status semantics are retained
         * @param abnormalStatus optional normal/abnormal result restriction
         * @param startDate optional inclusive start date
         * @param endDate optional inclusive end date
         */
        // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
        @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
        private QueryState(EntityManager entityManager, SystemPreferencesDao systemPreferencesDao, String patientLastName, String patientFirstName, String patientHealthNumber, boolean patientSearch,
                            boolean providerSearch, String searchProviderNo, String status, String abnormalStatus,
                            String startDate, String endDate) {

            startDate = normalizeDate(startDate);
            endDate = normalizeDate(endDate);

            this.entityManager = entityManager;
            this.patientLastName = patientLastName;
            this.searchProviderNo = searchProviderNo;
            this.status = status;
            this.patientFirstName = patientFirstName;
            this.patientHealthNumber = patientHealthNumber;
            this.patientSearch = patientSearch;
            this.providerSearch = providerSearch;
            this.abnormalStatus = abnormalStatus;

            String dateSearchType = "serviceObservation";

            SystemPreferences systemPreferences = systemPreferencesDao.findPreferenceByName(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.inboxDateSearchType);
            if (systemPreferences != null) {
                if (systemPreferences.getValue() != null && !systemPreferences.getValue().isEmpty()) {
                    dateSearchType = systemPreferences.getValue();
                }
            }

            this.dateSearchType = dateSearchType;

            if (startDate != null && !startDate.equals("")) {
                if (dateSearchType.equals("receivedCreated")) {
                    documentDateSql += "AND doc.contentdatetime >= ? ";
                    documentDateParams.add(startDate + " 00:00:00");
                    labDateSql += "AND message.created >= ? ";
                    labDateParams.add(startDate);
                } else {
                    documentDateSql += "AND doc.observationdate >= ? ";
                    documentDateParams.add(startDate + " 00:00:00");
                    labDateSql += "AND info.obr_date >= ? ";
                    labDateParams.add(startDate);
                }

                hrmDateSql += "AND h.timeReceived >= :hrmStartDate ";
                hrmParams.put("hrmStartDate", startDate + " 00:00:00");
            }

            if (endDate != null && !endDate.equals("")) {
                if (dateSearchType.equals("receivedCreated")) {
                    documentDateSql += "AND doc.contentdatetime <= ? ";
                    documentDateParams.add(endDate + " 23:59:59");
                    labDateSql += "AND message.created < ? ";
                    labDateParams.add(java.time.LocalDate.parse(endDate).plusDays(1).toString());
                } else {
                    documentDateSql += "AND doc.observationdate <= ? ";
                    documentDateParams.add(endDate + " 23:59:59");
                    labDateSql += "AND info.obr_date < ? ";
                    labDateParams.add(java.time.LocalDate.parse(endDate).plusDays(1).toString());
                }

                hrmDateSql += "AND h.timeReceived <= :hrmEndDate ";
                hrmParams.put("hrmEndDate", endDate + " 23:59:59");
            }

            if (abnormalStatus != null && !abnormalStatus.equals("all")) {
                if (abnormalStatus.equals("abnormalOnly")) {
                    documentAbnormalSql = " AND doc.abnormal = TRUE ";
                    labAbnormalSql = " AND info.result_status = 'A' ";
                } else if (abnormalStatus.equals("normalOnly")) {
                    documentAbnormalSql = " AND (doc.abnormal = FALSE OR doc.abnormal IS NULL) ";
                    labAbnormalSql = " AND (info.result_status IS NULL OR info.result_status != 'A') ";
                }
            }

            if (!documentDateSql.equals("") || !documentAbnormalSql.equals("")) {
                documentJoinSql = " LEFT JOIN document doc ON cd.document_no = doc.document_no";
            }

            if (providerSearch) {
                if (searchProviderNo.equals("0")) {
                    hrmProviderSql += " AND hp.providerNo ='-1'";
                } else {
                    hrmProviderSql += " AND hp.providerNo = :hrmProviderNo ";
                    hrmParams.put("hrmProviderNo", searchProviderNo);
                }
            }

            // Match HRMResultsData: only an empty status selects all HRM rows;
            // null defaults to unsigned, and viewing is independent of sign-off.
            if (status != null && status.isEmpty()) {
                hrmSignedOff = "";
            } else {
                hrmSignedOff = " AND hp.signedOff = :hrmSignedOff ";
                hrmParams.put("hrmSignedOff", "A".equalsIgnoreCase(status) || "F".equalsIgnoreCase(status) ? 1 : 0);
            }

            patients = new HashMap<Integer, PatientInfo>();

        }

        private static String normalizeDate(String date) {
            var parsed = parseDateBound(date);
            return parsed == null ? null : parsed.toString();
        }

        /** Builds the shared patient predicate in the same order as addPatientSearchParams. */
        private String patientFiltersSql() {
            return "d.last_name" + (StringUtils.isEmpty(patientLastName) ? SQL_IS_NOT_NULL : SQL_LIKE_PARAM)
                    + " AND d.first_name" + (StringUtils.isEmpty(patientFirstName) ? SQL_IS_NOT_NULL : SQL_LIKE_PARAM)
                    + hinFilter();
        }

        private boolean matchesAnyStatus() {
            return status == null || status.isEmpty();
        }

        private boolean bindsStatusParameter() {
            return !matchesAnyStatus();
        }

        private String getDateSearchType() { return dateSearchType; }

        /** Counts distinct unmatched HL7 reports within this request's provider, status and date filters. */
        public int getLabCountForUnmatched()
                throws SQLException {
            String dateSearchType = getDateSearchType();
            String sql = " SELECT HIGH_PRIORITY COUNT(DISTINCT info.lab_no) as count "
                    + " FROM providerLabRouting plr "
                    + " LEFT JOIN patientLabRouting plr2 ON plr.lab_no = plr2.lab_no AND plr2.lab_type = 'HL7'"
                    + " RIGHT JOIN hl7TextInfo info ON plr.lab_no = info.lab_no"
                    + (dateSearchType.equals("receivedCreated") ? " RIGHT JOIN hl7TextMessage message ON plr.lab_no = message.lab_id" : "")
                    + " WHERE plr.lab_type = 'HL7' "
                    + (providerSearch ? " AND plr.provider_no = ? " : "")
                    + " AND plr.status " + (matchesAnyStatus() ? SQL_IS_NOT_NULL : SQL_EQUALS_PARAM)
                    + labAbnormalSql
                    + labDateSql
                    + " AND (plr2.demographic_no IS NULL"
                    + " OR plr2.demographic_no = '0')";


            List<Object> params = new ArrayList<>();
            if (providerSearch) params.add(searchProviderNo);
            if (bindsStatusParameter()) params.add(status);
            params.addAll(labDateParams);

            return queryTrustedCount(sql, params);
        }

        /** Counts distinct scoped reports; a missing abnormal flag belongs to the normal partition. */
        public int getAbnormalCount(boolean isAbnormal) throws SQLException {
            String sql = " SELECT HIGH_PRIORITY COUNT(DISTINCT info.lab_no) as count "
                    + " FROM providerLabRouting plr JOIN hl7TextInfo info ON info.lab_no = plr.lab_no "
                    + (getDateSearchType().equals("receivedCreated")
                        ? " JOIN hl7TextMessage message ON message.lab_id = info.lab_no " : "")
                    + (patientSearch ? " JOIN patientLabRouting cd ON cd.lab_no = plr.lab_no AND cd.lab_type = 'HL7' "
                        + " JOIN demographic d ON d.demographic_no = cd.demographic_no " : "")
                    + " WHERE plr.lab_type = 'HL7' "
                    + (patientSearch ? " AND " + patientFiltersSql() : "")
                    + " AND plr.status " + (matchesAnyStatus() ? SQL_IS_NOT_NULL : SQL_EQUALS_PARAM)
                    + (providerSearch ? " AND plr.provider_no = ? " : "")
                    + (isAbnormal ? " AND info.result_status = 'A' "
                        : " AND (info.result_status IS NULL OR info.result_status != 'A') ")
                    + labAbnormalSql + labDateSql;
            List<Object> params = new ArrayList<>();
            if (patientSearch) addPatientSearchParams(params);
            if (bindsStatusParameter()) params.add(status);
            if (providerSearch) params.add(searchProviderNo);
            params.addAll(labDateParams);
            return queryTrustedCount(sql, params);
        }

        public int getDocumentCountForUnmatched()
                throws SQLException {
            String sql = " SELECT HIGH_PRIORITY COUNT(1) as count "
                    + " FROM ctl_document cd "
                    + "LEFT JOIN providerLabRouting plr ON plr.lab_no = cd.document_no"
                    + documentJoinSql
                    + " WHERE plr.lab_type = 'DOC' "
                    + " AND plr.status " + (matchesAnyStatus() ? SQL_IS_NOT_NULL : SQL_EQUALS_PARAM)
                    + (providerSearch ? " AND plr.provider_no = ? " : "")
                    + " AND cd.module_id = -1 "
                    + documentAbnormalSql
                    + documentDateSql;
            List<Object> params = new ArrayList<>();
            if (bindsStatusParameter()) params.add(status);
            if (providerSearch) params.add(searchProviderNo);
            params.addAll(documentDateParams);

            return queryTrustedCount(sql, params);
        }

        /** Accumulates one patient summary per patient, counting each matching raw report once. */
        public int getLabCountForPatientSearch() throws SQLException {
            PatientInfo info;
            String dateSearchType = getDateSearchType();
            String sql = " SELECT HIGH_PRIORITY d.demographic_no, d.last_name, d.first_name, COUNT(DISTINCT info.lab_no) as count "
                    + " FROM patientLabRouting cd"
                    + " LEFT JOIN demographic d ON cd.demographic_no = d.demographic_no"
                    + " LEFT JOIN providerLabRouting plr ON cd.lab_no = plr.lab_no"
                    + " LEFT JOIN hl7TextInfo info ON cd.lab_no = info.lab_no"
                    + (dateSearchType.equals("receivedCreated") ? " LEFT JOIN hl7TextMessage message ON cd.lab_no = message.lab_id" : "")
                    + " WHERE " + patientFiltersSql()
                    + " AND plr.lab_type = 'HL7' "
                    + " AND cd.lab_type = 'HL7' "
                    + " AND plr.status " + (matchesAnyStatus() ? SQL_IS_NOT_NULL : SQL_EQUALS_PARAM)
                    + (dateSearchType.equals("receivedCreated") ? " AND message.lab_id" + SQL_IS_NOT_NULL : " AND info.lab_no" + SQL_IS_NOT_NULL)
                    + (providerSearch ? " AND plr.provider_no = ? " : "")
                    + labAbnormalSql
                    + labDateSql
                    // Qualified: both patientLabRouting (cd) and demographic (d) carry demographic_no.
                    // MariaDB resolves the bare name to the select-list d.demographic_no, but H2 and
                    // stricter SQL engines reject it as ambiguous; d. states the intended column.
                    + " GROUP BY d.demographic_no, d.last_name, d.first_name ";

            List<Object> params = new ArrayList<>();
            addPatientSearchParams(params);
            if (bindsStatusParameter()) params.add(status);
            if (providerSearch) params.add(searchProviderNo);
            params.addAll(labDateParams);

            {
                List<Tuple> rows = queryRows(sql, params);
                int totalCount = 0;
                for (Tuple row : rows) {
                    int id = number(row.get("demographic_no"));
                    int count = number(row.get(COUNT_COLUMN));
                    // Updating patient info if it already exists.
                    if (patients.containsKey(id)) {
                        info = patients.get(id);
                        info.setLabCount(count);
                    }
                    // Otherwise adding a new patient record.
                    else {
                        info = new PatientInfo(id, row.get("first_name", String.class), row.get("last_name", String.class));
                        info.setLabCount(count);
                        patients.put(info.getId(), info);
                    }
                    totalCount += count;
                }
                return totalCount;
            }
        }

        /** Counts distinct routed reports for one demographic without multiplying duplicate provider routes. */
        public int getLabCountForDemographic(String demographicNo) throws SQLException {
            if (demographicNo == null || demographicNo.trim().isEmpty()) {
                throw new IllegalArgumentException("demographicNo cannot be null or empty");
            }

            // Build SQL with placeholders
            StringBuilder sql = new StringBuilder();
            sql.append(" SELECT HIGH_PRIORITY d.demographic_no, last_name, first_name, COUNT(DISTINCT cd.lab_no) as count ");
            sql.append(" FROM patientLabRouting cd, demographic d, providerLabRouting plr ");
            sql.append(" WHERE d.demographic_no = ? ");
            sql.append(" AND cd.demographic_no = d.demographic_no ");
            sql.append(" AND cd.lab_no = plr.lab_no ");
            sql.append(" AND plr.lab_type = 'HL7' ");
            sql.append(" AND cd.lab_type = 'HL7' ");

            if (matchesAnyStatus()) {
                sql.append(" AND plr.status").append(SQL_IS_NOT_NULL);
            } else {
                sql.append(" AND plr.status").append(SQL_EQUALS_PARAM);
            }

            if (providerSearch) {
                sql.append(" AND plr.provider_no = ? ");
            }

            // Qualified for the same reason as getLabCountForPatientSearch: cd and d both have demographic_no.
            sql.append(" GROUP BY d.demographic_no, d.last_name, d.first_name ");

            List<Object> params = new ArrayList<>();
            params.add(demographicNo);
            if (bindsStatusParameter()) {
                params.add(status);
            }
            if (providerSearch) {
                params.add(searchProviderNo);
            }

            return queryTrustedCount(sql.toString(), params);
        }

        /*
         * This will return the total documents found for this patients.
         * it will also add to the patients map (demographicNo, PatientInfo) with a document count for the patient.
         *
         */
        public int getDocumentCountForPatientSearch() throws SQLException {
            PatientInfo info;

            String sql = " SELECT HIGH_PRIORITY demographic_no, last_name, first_name, COUNT( distinct cd.document_no) as count "
                    + " FROM ctl_document cd "
                    + "LEFT JOIN demographic d  ON cd.module_id = d.demographic_no "
                    + "LEFT JOIN providerLabRouting plr ON cd.document_no = plr.lab_no "
                    + documentJoinSql
                    + " WHERE   d.last_name" + (StringUtils.isEmpty(patientLastName) ? SQL_IS_NOT_NULL : " like ?  ")
                    + hinFilter()
                    + " AND d.first_name" + (StringUtils.isEmpty(patientFirstName) ? SQL_IS_NOT_NULL : SQL_LIKE_PARAM)
                    + " AND plr.lab_type = 'DOC' "
                    + " AND plr.status " + (matchesAnyStatus() ? SQL_IS_NOT_NULL : SQL_EQUALS_PARAM)
                    + (providerSearch ? "AND plr.provider_no = ? " : "")
                    + documentAbnormalSql
                    + documentDateSql
                    + " GROUP BY demographic_no ";
            List<Object> params = new ArrayList<>();
            if (!StringUtils.isEmpty(patientLastName)) params.add("%" + patientLastName + "%");
            if (!StringUtils.isEmpty(patientHealthNumber)) params.add("%" + patientHealthNumber + "%");
            if (!StringUtils.isEmpty(patientFirstName)) params.add("%" + patientFirstName + "%");
            if (bindsStatusParameter()) params.add(status);
            if (providerSearch) params.add(searchProviderNo);
            params.addAll(documentDateParams);

            {
                List<Tuple> rows = queryRows(sql, params);
                int count = 0;
                for (Tuple row : rows) {
                    info = new PatientInfo(number(row.get("demographic_no")), row.get("first_name", String.class), row.get("last_name", String.class));
                    info.setDocCount(number(row.get(COUNT_COLUMN)));
                    patients.put(info.getId(), info);
                    count += info.getDocCount();
                }

                return count;
            }
        }

        private int queryTrustedCount(String sql, List<Object> params) {
            List<Tuple> rows = queryRows(sql, params);
            return rows.isEmpty() ? 0 : number(rows.get(0).get(COUNT_COLUMN));
        }

        private List<Tuple> queryRows(String sql, List<Object> params) {
            // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string -- private SQL shapes use only fixed clauses; every patient/provider/date/status value is bound below.
            Query query = entityManager.createNativeQuery(sql, Tuple.class);
            for (int i = 0; i < params.size(); i++) query.setParameter(i + 1, params.get(i));
            return query.getResultList();
        }

        private static int number(Object value) { return value == null ? 0 : ((Number) value).intValue(); }

        /**
         * The HIN condition of a patient search. A blank HIN field adds none: {@code d.hin} is nullable
         * (uninsured, newborn, out-of-province or imported patients), and {@code d.hin IS NOT NULL}
         * would leave those patients out of the Inbox counts and patient list while the result rows,
         * which treat a NULL HIN as empty, still show their labs and documents.
         */
        private String hinFilter() {
            return hinFilter("like ?");
        }

        /** As {@link #hinFilter()}, with the given condition (a named parameter for JPA queries). */
        private String hinFilter(String condition) {
            return StringUtils.isEmpty(patientHealthNumber) ? "" : " AND d.hin " + condition + " ";
        }

        private void addPatientSearchParams(List<Object> params) {
            if (!StringUtils.isEmpty(patientLastName)) params.add("%" + patientLastName + "%");
            if (!StringUtils.isEmpty(patientFirstName)) params.add("%" + patientFirstName + "%");
            if (!StringUtils.isEmpty(patientHealthNumber)) params.add("%" + patientHealthNumber + "%");
        }

        /**
         * Counts matching HRM documents and updates the patient summaries.
         *
         * @return total matching documents across the grouped patient rows
         * @throws SQLException if a database count operation fails
         */
        public int getHRMDocumentCountForPatient() throws SQLException {
            int count = 0;
            PatientInfo info;

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT HIGH_PRIORITY d.demographic_no, d.first_name, d.last_name, COUNT(DISTINCT h.id) AS count ")
               .append(" FROM HRMDocumentToDemographic hd ")
               .append(" LEFT JOIN HRMDocumentToProvider hp ON hd.hrmDocumentId = hp.hrmDocumentId ")
               .append(" LEFT JOIN HRMDocument h ON hd.hrmDocumentId = h.id ")
               .append(" JOIN demographic d ON hd.demographicNo = d.demographic_no ")
               .append(" WHERE 1=1 ")
               .append(" AND d.last_name ").append(StringUtils.isNotEmpty(patientLastName) ? "LIKE :patientLastName " : SQL_IS_NOT_NULL_NO_PREFIX)
               .append(hinFilter("LIKE :patientHealthNumber"))
               .append(" AND d.first_name ").append(StringUtils.isNotEmpty(patientFirstName) ? "LIKE :patientFirstName " : SQL_IS_NOT_NULL_NO_PREFIX)
               .append(hrmSignedOff).append(hrmDateSql).append(hrmProviderSql)
               .append(" GROUP BY d.demographic_no ");

            Query query = entityManager.createNativeQuery(sql.toString(), Tuple.class);

            // Set parameters only if they are not empty
            if (StringUtils.isNotEmpty(patientLastName)) {
                query.setParameter("patientLastName", "%" + patientLastName + "%");
            }
            if (StringUtils.isNotEmpty(patientHealthNumber)) {
                query.setParameter("patientHealthNumber", "%" + patientHealthNumber + "%");
            }
            if (StringUtils.isNotEmpty(patientFirstName)) {
                query.setParameter("patientFirstName", "%" + patientFirstName + "%");
            }
            hrmParams.forEach(query::setParameter);

            // Executing the query and processing the results
            List<Tuple> results = query.getResultList();

            for (Tuple result : results) {
                    int hrmCount = ((Number) result.get(COUNT_COLUMN)).intValue(); // Extracting count
                Integer id = result.get("demographic_no", Integer.class); // Extracting demographicNo as Integer

                // Updating patient info if it already exists
                if (patients.containsKey(id)) {
                    info = patients.get(id);
                    info.setDocCount(info.getDocCount() + hrmCount);
                    info.setHrmCount(hrmCount);
                } else {
                    // Otherwise, adding a new patient record
                    info = new PatientInfo(id, result.get("first_name", String.class), result.get("last_name", String.class));
                    info.setDocCount(hrmCount);
                    info.setHrmCount(hrmCount);
                    patients.put(info.getId(), info);
                }

                count += hrmCount;
            }

            return count;
        }

        /**
         * Counts HRM documents without a patient match using the same sign-off filter.
         *
         * @return number of matching unassigned documents
         * @throws SQLException if a database count operation fails
         */
        public int getHRMDocumentCountForUnmatched() throws SQLException {
            int count = 0;

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT HIGH_PRIORITY COUNT(DISTINCT h.id) AS count ")
                .append(" FROM HRMDocumentToProvider hp ")
                .append(" LEFT JOIN HRMDocument h ON h.id = hp.hrmDocumentId ")
                .append(" LEFT JOIN HRMDocumentToDemographic hd ON hd.hrmDocumentId = hp.hrmDocumentId ")
                .append(" WHERE hd.hrmDocumentId IS NULL ")
                .append(hrmSignedOff)
                .append(hrmDateSql)
                .append(hrmProviderSql);

            // Create a native query using the entity manager
            Query query = entityManager.createNativeQuery(sql.toString(), Tuple.class);
            hrmParams.forEach(query::setParameter);

            // Execute the query and retrieve the results
            List<Tuple> results = query.getResultList();

            // Process the results
            for (Tuple result : results) {
                    int hrmCount = ((Number) result.get(COUNT_COLUMN)).intValue(); // Extracting count
                count += hrmCount; // Accumulate the total count
            }

            return count;
        }

    }
}
