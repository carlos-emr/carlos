/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 * <p>
 * Modifications made by Magenta Health in 2024.
 */

package io.github.carlos_emr.carlos.commn.dao;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.inbox.InboxItem;
import io.github.carlos_emr.carlos.commn.model.inbox.InboxItemDemographicCount;
import io.github.carlos_emr.carlos.commn.model.inbox.InboxQueryParameters;
import io.github.carlos_emr.carlos.commn.model.inbox.InboxResponse;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class InboxResultsRepositoryImpl implements InboxResultsRepository {
    private static final Map<String, String> sortBySqlList = new HashMap<String, String>() {{
        put("healthNumber", "health_number");
        put("sex", "sex");
        put("dateTime", "order_by_date");
        put("dateReceived", "date_received");
        put("requestingClient", "requesting_client");
        put("discipline", "discipline");
        put("status", "status");
    }};

    private static final Map<String, String> sortOrderSqlList = new HashMap<String, String>() {{
        put("descending", "DESC");
        put("ascending", "ASC");
    }};

    @PersistenceContext(unitName = "entityManagerFactory")
    protected EntityManager entityManager = null;

    private final Logger logger = MiscUtils.getLogger();

    public InboxResponse getInboxItems(InboxQueryParameters queryParameters) {
        return getInboxItems(queryParameters.getLoggedInProvider(), queryParameters.getProviderNumber(), queryParameters.getFirstName(), queryParameters.getLastName(), queryParameters.getHin(),
                queryParameters.getStartDate(), queryParameters.getEndDate(), queryParameters.getStatus(), queryParameters.getAbnormalStatus(), queryParameters.getMatchedStatus(),
                queryParameters.getSortBy(), queryParameters.getSortOrder(), queryParameters.getPage(), queryParameters.getResultsPerPage(),
                queryParameters.getShowDocuments(), queryParameters.getShowLabs(), queryParameters.getShowHrm(), queryParameters.getGetCounts(), queryParameters.getGetDemographicCounts());
    }

    /**
     * Gets a list of items in the inbox based on the supplied parameters
     *
     * @param loggedInProvider     The currently logged in providers
     * @param providerNumber       The provider number to search items for, empty string will return items for all providers, 0 will return all unmatched items
     * @param firstName            String containing a demographic first name
     * @param lastName             String containing a demographic last name
     * @param hin                  String containing a demographic HIN
     * @param startDate            Date to select all inbox items that came in after, may be empty
     * @param endDate              Date to select all inbox items that came in before, may be empty
     * @param status               Status of the inbox items to search for
     * @param abnormalStatus       Abnormal status of the inbox items to search for
     * @param matchedStatus        Criteria for if the results should be matched to a demographic
     * @param sortBy               String
     * @param sortOrder            <code>ASC</code> or <code>DESC</code>
     * @param page                 Starting point to select items from (Based on the resultsPerPage param)
     * @param resultsPerPage       Number of items to return
     * @param showDocuments        Whether documents should be included in the search
     * @param showLabs             Whether Labs should be included in the search
     * @param showHrm              Whether HRM should be included in the search
     * @param getCounts            Whether to get the total inbox counts based on the supplied parameters
     * @param getDemographicCounts Whether to get the inbox counts for demographics based on the supplied parameters
     * @return {@code InboxResponse} Response containing the items that matched the supplied parameters, may include counts
     */
    public InboxResponse getInboxItems(Provider loggedInProvider, String providerNumber, String firstName, String lastName, String hin, String startDate, String endDate, String status, String abnormalStatus, InboxQueryParameters.MatchedStatus matchedStatus, String sortBy, String sortOrder,
                                       Integer page, Integer resultsPerPage, Boolean showDocuments, Boolean showLabs, Boolean showHrm, Boolean getCounts, Boolean getDemographicCounts) {
        String sortOrderSql = sortOrderSqlList.containsKey(sortOrder) ? sortOrderSqlList.get(sortOrder) : "";
        String sortBySql = sortBySqlList.containsKey(sortBy) ? sortBySqlList.get(sortBy) : "";
        return getInboxDetails(loggedInProvider, providerNumber, firstName, lastName, hin, startDate, endDate, status, abnormalStatus, matchedStatus, sortBySql, sortOrderSql, page, resultsPerPage, showDocuments, showLabs, showHrm, getCounts, getDemographicCounts);
    }

    public InboxResponse getInboxDetails(Provider loggedInProvider, String providerNumber, String firstName, String lastName, String hin, String startDate,
                                         String endDate, String status, String abnormalStatus, InboxQueryParameters.MatchedStatus matchedStatus, String sortBy, String sortOrder, Integer page, Integer resultsPerPage,
                                         Boolean showDocuments, Boolean showLabs, Boolean showHrm, Boolean getCounts, Boolean getDemographicCounts) {
        InboxResponse inbox = new InboxResponse();
        if (getCounts) {
            Query countQuery = generateSelectQuery(loggedInProvider.getProviderNo(), providerNumber, firstName, lastName, hin, startDate, endDate, status, abnormalStatus, matchedStatus, sortBy, sortOrder, page, resultsPerPage, showDocuments, showLabs, showHrm, true, false);
            List<InboxResponse> inboxList = countQuery.getResultList();
            inbox = inboxList.get(0);
        }
        if (getDemographicCounts) {
            Query countQuery = generateSelectQuery(loggedInProvider.getProviderNo(), providerNumber, firstName, lastName, hin, startDate, endDate, status, abnormalStatus, matchedStatus, sortBy, sortOrder, page, resultsPerPage, showDocuments, showLabs, showHrm, false, true);
            List<InboxItemDemographicCount> inboxList = countQuery.getResultList();
            inbox.setInboxDemographicCounts(inboxList);
        }

        if (showDocuments || showLabs || showHrm) {
            Query itemQuery = generateSelectQuery(loggedInProvider.getProviderNo(), providerNumber, firstName, lastName, hin, startDate, endDate, status, abnormalStatus, matchedStatus, sortBy, sortOrder, page, resultsPerPage, showDocuments, showLabs, showHrm, false, false);
            inbox.setInboxItems(itemQuery.getResultList());
        } else {
            inbox.setInboxItems(new ArrayList<InboxItem>());
        }
        return inbox;
    }

    private Query generateSelectQuery(String loggedInProviderNo, String providerNumber, String firstName, String lastName, String hin, String startDate,
                                      String endDate, String status, String abnormalStatus, InboxQueryParameters.MatchedStatus matchedStatus, String sortBy, String sortOrder, Integer page, Integer resultsPerPage,
                                      Boolean showDocuments, Boolean showLabs, Boolean showHrm, Boolean getCounts, Boolean getDemographicCounts) {

        Map<String, String> whereValues = new HashMap<String, String>();

        if ("A".equals(abnormalStatus)) {
            showHrm = false;
        }

        String documentSql = showDocuments ? getDocumentsSql(loggedInProviderNo, providerNumber, firstName, lastName, hin, startDate, endDate, status, abnormalStatus, matchedStatus, getCounts, getDemographicCounts, whereValues) : "";
        String labSql = showLabs ? getLabsSql(loggedInProviderNo, providerNumber, firstName, lastName, hin, startDate, endDate, status, abnormalStatus, matchedStatus, getCounts, getDemographicCounts, whereValues) : "";
        String hrmReportSql = showHrm ? getHRMReportsSql(loggedInProviderNo, providerNumber, firstName, lastName, hin, startDate, endDate, status, matchedStatus, getCounts, getDemographicCounts, whereValues) : "";


        Query query;
        if (!getCounts && !getDemographicCounts) {
            String sql = "SELECT * FROM ("
                    + documentSql
                    + (showDocuments && showLabs ? " UNION " : "")
                    + labSql
                    + ((showDocuments || showLabs) && showHrm ? " UNION " : "")
                    + hrmReportSql
                    + ") AS x "
                    + " ORDER BY " + sortBy + " " + sortOrder
                    + ", segment_id DESC, lab_type ASC "; // add ordering for type and segment id to make sure sort is deterministic (eg sorted rows with duplicate values swapping order on pagination)

            query = entityManager.createNativeQuery(sql, InboxItem.class);
            query.setFirstResult(page * resultsPerPage);
            query.setMaxResults(resultsPerPage);
        } else if (getCounts) {
            documentSql = (showDocuments ? ", (SELECT COUNT(*) FROM (" + documentSql + ") AS d) AS documentCount " : ", 0 AS documentCount ");
            labSql = (showLabs ? ",  (" + labSql + ") AS labCount " : ", 0 AS labCount ");
            hrmReportSql = (showHrm ? ", (SELECT COUNT(*) FROM (" + hrmReportSql + ") AS h) AS hrmCount " : ", 0 AS hrmCount ");
            String sql = "SELECT 1 AS id " + documentSql + labSql + hrmReportSql;
            query = entityManager.createNativeQuery(sql, InboxResponse.class);
        } else {
            String sql = "SELECT * FROM (" + documentSql
                    + (showDocuments && showLabs ? " UNION " : "") + labSql
                    + ((showDocuments || showLabs) && showHrm ? " UNION " : "") + hrmReportSql
                    + ") AS x ORDER BY x.last_name ASC, x.first_name ASC, x.lab_patient_id ASC";
            query = entityManager.createNativeQuery(sql, InboxItemDemographicCount.class);
        }

        for (Map.Entry<String, String> entry : whereValues.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }

        return query;
    }


    private String getDocumentsSql(String loggedInProviderNo, String providerNumber, String firstName, String lastName, String hin,
                                   String startDate, String endDate, String status, String abnormalStatus, InboxQueryParameters.MatchedStatus matchedStatus, Boolean getCounts, Boolean getDemographicCounts, Map<String, String> whereValues) {
        String whereSql = getDocumentsWhereSql(loggedInProviderNo, providerNumber, firstName, lastName, hin, startDate, endDate, status, abnormalStatus, matchedStatus, whereValues);

        // Add provider lab routing only if relevant to search parameters
        boolean addProviderJoin = !providerNumber.isEmpty() || !status.equals("L");

        String select;
        if (getCounts) {
            select = "COUNT(*) ";
        } else if (!getDemographicCounts) {
            select = "doc.document_no AS segment_id, ctl.module_id AS lab_patient_id, "
                    + (addProviderJoin ? "plr.status" : "NULL") + " AS status,"
                    + " '' AS accession_number,  "
                    + "  hin AS health_number, last_name AS last_name, first_name AS first_name, sex AS sex, 'N' AS result_status,  "
                    + "  0 AS final_results_count,  updatedatetime AS last_update_date, updatedatetime AS order_by_date, DATE(observationdate) AS date_time, doc.contentdatetime AS date_received, '----' AS priority,  "
                    + "  '' AS requesting_client, doc.docType AS discipline, 'F' AS report_status, doc.docdesc AS report_description, doc.contenttype AS content_type, 'DOC' AS lab_type, true AS final_res,  "
                    + "  IF(last_name IS "
                    + "	 NOT NULL, TRUE, FALSE) AS is_matched_to_patient, docdesc AS description, false AS cancelled_report, '' AS label, "
                    + "				   doc.abnormal AS is_abnormal, "
                    + "				   (SELECT COUNT(*) FROM providerLabRouting plr3 WHERE plr3.lab_no = doc.document_no AND plr3.lab_type = 'DOC' AND plr3.status = 'A') AS acknowledge_count, "
                    + "				   NULL AS past_acknowledge_count, IF(r.lab_id IS NOT NULL, TRUE, FALSE) as `read` ";
        } else {
            select = "'DOC' AS `lab_type`, IF(lab_patient_id IS NULL, 'Unmatched', lab_patient_id) AS lab_patient_id, last_name, first_name, COUNT(*) `count` FROM (SELECT d.demographic_no AS lab_patient_id, d.last_name, d.first_name ";
        }


        String sql = "SELECT " + select
                + "FROM document doc "
                + "LEFT JOIN ctl_document ctl ON ctl.document_no = doc.document_no AND ctl.module = 'demographic' "
                + (addProviderJoin ? "LEFT JOIN providerLabRouting plr ON plr.lab_no = doc.document_no AND plr.lab_type = 'DOC' " : "")
                + "LEFT JOIN patientLabRouting plr2 ON plr2.lab_no = doc.document_no AND plr2.lab_type = 'DOC' "
                + "LEFT JOIN demographic d ON d.demographic_no = ctl.module_id "
                + "LEFT JOIN read_lab r ON r.provider_no = :docReadProviderNumber AND r.lab_id = doc.document_no AND r.lab_type = 'DOC' "
                + whereSql
                + " GROUP BY doc.document_no";

        if (getDemographicCounts) { // add grouping to query
            sql += " ) AS docGrouped GROUP BY lab_patient_id ";
        }

        return sql;
    }

    private String getDocumentsWhereSql(String loggedInProviderNo, String providerNumber, String firstName, String lastName, String hin,
                                        String startDate, String endDate, String status, String abnormalStatus, InboxQueryParameters.MatchedStatus matchedStatus, Map<String, String> whereValues) {
        String whereSql = "";
        whereValues.put("docReadProviderNumber", loggedInProviderNo);

        if (!providerNumber.isEmpty()) {
            whereSql += " WHERE plr.provider_no = :docProviderNumber";
            whereValues.put("docProviderNumber", providerNumber);
        }

        if (!firstName.isEmpty()) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " d.first_name LIKE :docFirstName";
            whereValues.put("docFirstName", firstName + "%");
        }

        if (!lastName.isEmpty()) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " d.last_name LIKE :docLastName";
            whereValues.put("docLastName", lastName + "%");
        }

        if (!hin.isEmpty()) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " d.hin LIKE :docHin";
            whereValues.put("docHin", hin + "%");
        }

        if (matchedStatus != InboxQueryParameters.MatchedStatus.ALL) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            if (matchedStatus == InboxQueryParameters.MatchedStatus.MATCHED) {
                whereSql += " plr2.demographic_no IS NOT NULL";
            } else if (matchedStatus == InboxQueryParameters.MatchedStatus.NOT_MATCHED) {
                whereSql += " plr2.demographic_no IS NULL";
            }
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        if (!startDate.isEmpty()) {
            try {
                String formattedDate = sdf.format(sdf.parse(startDate));

                whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
                whereSql += " DATE(observationdate) >= :docStartDate";
                whereValues.put("docStartDate", formattedDate);
            } catch (ParseException e) {
                logger.warn("Error parsing inbox search date: " + startDate);
            }
        }

        if (!endDate.isEmpty()) {
            try {
                String formattedDate = sdf.format(sdf.parse(endDate));

                whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
                whereSql += " DATE(observationdate) <= :docEndDate";
                whereValues.put("docEndDate", formattedDate);
            } catch (ParseException e) {
                logger.warn("Error parsing inbox search date: " + endDate);
            }
        }

        if (!status.equals("L")) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " plr.status = :docStatus";
            whereValues.put("docStatus", status);
        }

        if (!abnormalStatus.equals("L")) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            if (abnormalStatus.equals("A")) {
                whereSql += " doc.abnormal IS TRUE";
            } else {
                whereSql += " (doc.abnormal IS FALSE OR doc.abnormal IS NULL)";
            }
        }
        return whereSql;
    }

    private String getLabsSql(String loggedInProviderNo, String providerNumber, String firstName, String lastName, String hin,
                              String startDate, String endDate, String status, String abnormalStatus, InboxQueryParameters.MatchedStatus matchedStatus, Boolean getCounts, Boolean getDemographicCounts, Map<String, String> whereValues) {

        String whereSql = getLabsWhereSql(loggedInProviderNo, providerNumber, firstName, lastName, hin, startDate, endDate, status, abnormalStatus, matchedStatus, whereValues);

        String select;
        if (getCounts) {
            select = "COUNT(*) ";
        } else if (!getDemographicCounts) {
            select = "info.lab_no AS segment_id, plr2.demographic_no AS lab_patient_id, "
                    + "  groupedLabs.status AS status, info.accessionNum AS accession_number, "
                    + "  info.health_no AS health_number, info.last_name AS last_name, info.first_name AS first_name, info.sex AS sex, info.result_status AS result_status, "
                    + "  info.final_result_count AS final_results_count, h.created AS last_update_date, info.obr_date AS date_time, info.obr_date AS order_by_date, h.created AS date_received, info.priority AS priority, "
                    + "  info.requesting_client AS requesting_client, info.discipline AS discipline, info.report_status AS report_status, '' AS report_description, '' AS content_type, 'HL7' AS lab_type, true AS final_res, "
                    + "  IF(d.demographic_no IS NOT NULL, TRUE, FALSE) AS is_matched_to_patient, '' AS description, IF(info.report_status IS NOT NULL AND info.report_status = 'X', TRUE, FALSE) AS cancelled_report, info.label AS label, "
                    + "  IF(info.result_status = 'A', TRUE, FALSE) AS is_abnormal, "
                    + "  (SELECT COUNT(*) FROM providerLabRouting plr3 WHERE plr3.lab_no = info.lab_no AND plr3.lab_type = 'HL7' AND plr3.status = 'A') AS acknowledge_count, "
                    + "  (SELECT IF( "
                    + "	(SELECT COUNT(DISTINCT plr.lab_no) FROM hl7TextInfo aka, hl7TextInfo akb "
                    + "	  INNER JOIN providerLabRouting plr ON akb.lab_no = plr.lab_no "
                    + "	   LEFT JOIN provider prov ON plr.provider_no = prov.provider_no "
                    + "	  WHERE aka.lab_no = info.lab_no AND aka.accessionNum = akb.accessionNum AND prov.status = '1') = 1, NULL, "
                    + "  (SELECT COUNT(DISTINCT plr.provider_no) FROM hl7TextInfo aka, hl7TextInfo akb "
                    + "	 INNER JOIN providerLabRouting plr ON akb.lab_no = plr.lab_no "
                    + "	   LEFT JOIN provider prov ON plr.provider_no = prov.provider_no "
                    + "	 WHERE aka.lab_no = info.lab_no AND aka.accessionNum = akb.accessionNum AND plr.status = 'A' AND prov.status = '1') "
                    + "  )) AS past_acknowledge_count, IF(r.lab_id IS NOT NULL, TRUE, FALSE) as `read` ";
        } else {
            select = "'HL7' AS `lab_type`, IF(d.demographic_no IS NULL, 'Unmatched', d.demographic_no) AS lab_patient_id, d.last_name, d.first_name, COUNT(*) `count` ";
        }

        String sql = "SELECT " + select
                + "FROM hl7TextInfo info "
                + "LEFT JOIN hl7TextMessage h ON h.lab_id = info.lab_no "
                + "LEFT JOIN patientLabRouting plr2 ON plr2.lab_no = info.lab_no AND plr2.lab_type = 'HL7' "
                + "LEFT JOIN read_lab r ON r.provider_no = :labReadProviderNumber AND r.lab_id = info.lab_no AND r.lab_type = 'HL7' "
                + "LEFT JOIN demographic d ON d.demographic_no = plr2.demographic_no "
                + "INNER JOIN (SELECT MAX(CONCAT(COALESCE(info.obr_date,''), '#', info.id)) as max_date_id, info.accessionNum, plr.status AS status, IFNULL(info.accessionNum, CONCAT(info.id, UUID())) as groupBy FROM hl7TextInfo info "
                + "	  LEFT JOIN patientLabRouting plr2 ON plr2.lab_no = info.lab_no AND plr2.lab_type = 'HL7' "
                + "	  LEFT JOIN providerLabRouting plr ON plr.lab_no = info.lab_no AND plr.lab_type = 'HL7' "
                + "	  LEFT JOIN read_lab r ON r.provider_no = :labReadProviderNumber AND r.lab_id = info.lab_no AND r.lab_type = 'HL7' "
                + "	  LEFT JOIN demographic d ON d.demographic_no = plr2.demographic_no "
                + getLabsGroupedWhereSql(providerNumber, firstName, lastName, hin, status, matchedStatus, whereValues)
                + "	  GROUP BY groupBy) groupedLabs "
                + "ON info.accessionNum <=> groupedLabs.accessionNum AND info.id = SUBSTR(groupedLabs.max_date_id,LOCATE('#',groupedLabs.max_date_id) + 1) "
                + whereSql;

        if (getDemographicCounts) { // add grouping to query
            sql += " GROUP BY plr2.demographic_no ";
        }

        return sql;
    }

    private String getLabsWhereSql(String loggedInProviderNo, String providerNumber, String firstName, String lastName, String hin,
                                   String startDate, String endDate, String status, String abnormalStatus, InboxQueryParameters.MatchedStatus matchedStatus, Map<String, String> whereValues) {
        String whereSql = "";
        whereValues.put("labReadProviderNumber", loggedInProviderNo);

        if (!firstName.isEmpty()) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " d.first_name LIKE :labFirstName";
            whereValues.put("labFirstName", firstName + "%");
        }

        if (!lastName.isEmpty()) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " d.last_name LIKE :labLastName";
            whereValues.put("labLastName", lastName + "%");
        }

        if (!hin.isEmpty()) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " d.hin LIKE :labHin";
            whereValues.put("labHin", hin + "%");
        }

        if (matchedStatus != InboxQueryParameters.MatchedStatus.ALL) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            if (matchedStatus == InboxQueryParameters.MatchedStatus.MATCHED) {
                whereSql += " plr2.demographic_no IS NOT NULL";
            } else if (matchedStatus == InboxQueryParameters.MatchedStatus.NOT_MATCHED) {
                whereSql += " plr2.demographic_no IS NULL";
            }
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        if (!startDate.isEmpty()) {
            try {
                String formattedDate = sdf.format(sdf.parse(startDate));

                whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
                whereSql += " DATE(info.obr_date) >= :labStartDate";
                whereValues.put("labStartDate", formattedDate);
            } catch (ParseException e) {
                logger.warn("Error parsing inbox search date: " + startDate);
            }
        }

        if (!endDate.isEmpty()) {
            try {
                String formattedDate = sdf.format(sdf.parse(endDate));

                whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
                whereSql += " DATE(info.obr_date) <= :labEndDate";
                whereValues.put("labEndDate", formattedDate);
            } catch (ParseException e) {
                logger.warn("Error parsing inbox search date: " + endDate);
            }
        }

        if (!status.equals("L")) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            if (providerNumber.equals("0")) {
                whereSql += " (groupedLabs.status = :labStatus OR groupedLabs.status IS NULL)";
            } else {
                whereSql += " groupedLabs.status = :labStatus";
            }
            whereValues.put("labStatus", status);
        }
        if (!abnormalStatus.equals("L")) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            if (abnormalStatus.equals("A")) {
                whereSql += " info.result_status = 'A'";
            } else {
                whereSql += " (info.result_status != 'A' OR result_status IS NULL)";
            }
        }
        return whereSql;
    }

    private String getLabsGroupedWhereSql(String providerNumber, String firstName, String lastName, String hin, String status,
                                          InboxQueryParameters.MatchedStatus matchedStatus, Map<String, String> whereValues) {
        String whereSql = "";

        if (!providerNumber.isEmpty()) {
            if (providerNumber.equals("0")) {
                whereSql += "WHERE (plr.provider_no = :labProviderNumber OR plr.provider_no IS NULL)";
            } else {
                whereSql += " WHERE plr.provider_no = :labProviderNumber";
            }
            whereValues.put("labProviderNumber", providerNumber);
        }

        // Add provider lab routing if status is specified, if no providers specified find labs with at least one plr entry for that lab
        if (!status.equals("L")) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " plr.status = :labStatus";
        }

        if (!firstName.isEmpty()) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " d.first_name LIKE :labFirstName";
        }

        if (!lastName.isEmpty()) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " d.last_name LIKE :labLastName";
        }

        if (!hin.isEmpty()) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " d.hin LIKE :labHin";
        }

        if (matchedStatus != InboxQueryParameters.MatchedStatus.ALL) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            if (matchedStatus == InboxQueryParameters.MatchedStatus.MATCHED) {
                whereSql += " plr2.demographic_no IS NOT NULL";
            } else if (matchedStatus == InboxQueryParameters.MatchedStatus.NOT_MATCHED) {
                whereSql += " plr2.demographic_no IS NULL";
            }
        }
        return whereSql;
    }

    private String getHRMReportsSql(String loggedInProviderNo, String providerNumber, String firstName, String lastName, String hin, String startDate, String endDate,
                                    String status, InboxQueryParameters.MatchedStatus matchedStatus, Boolean getCounts, Boolean getDemographicCounts, Map<String, String> whereValues) {

        String whereSql = getHRMReportsWhereSql(loggedInProviderNo, providerNumber, firstName, lastName, hin, startDate, endDate, status, matchedStatus, whereValues);

        String select;
        if (getCounts) {
            select = "COUNT(DISTINCT h.id) ";
        } else if (!getDemographicCounts) {
            select = "h.id AS segment_id, htd.demographicNo AS lab_patient_id, 'N' AS status, '' AS accession_number,  "
                    + "  d.hin AS health_number, d.last_name AS last_name, d.first_name AS first_name, d.sex AS sex, '' AS result_status, "
                    + "  0 AS final_results_count, NULL AS last_update_date, reportDate AS date_time, reportDate AS order_by_date, timeReceived AS date_received, '----' AS priority,  "
                    + "  '' AS requesting_client, 'HRM' AS discipline, '' AS report_status, '' AS report_description, '' AS content_type, 'HRM' AS lab_type, true AS final_res, "
                    + "  IF(d.first_name IS NOT NULL, TRUE, FALSE) AS is_matched_to_patient, '' AS description, '' AS cancelled_report, '' AS label, "
                    + "  FALSE as is_abnormal, "
                    + "  (SELECT COUNT(*) FROM HRMDocumentToProvider dtp WHERE dtp.hrmDocumentId = h.id AND dtp.signedOff IS TRUE) AS acknowledge_count, "
                    + "  NULL AS past_acknowledge_count, IF(r.lab_id IS NOT NULL, TRUE, FALSE) as `read` ";
        } else {
            select = "'HRM' AS `lab_type`, IF(lab_patient_id IS NULL, 'Unmatched', lab_patient_id) AS lab_patient_id, last_name, first_name, COUNT(*) `count` FROM (SELECT htd.demographicNo AS lab_patient_id, d.last_name, d.first_name ";
        }

        String sql = "SELECT " + select
                + "FROM HRMDocument h "
                + "LEFT JOIN HRMDocumentToDemographic htd ON htd.hrmDocumentId = h.id "
                + "LEFT JOIN HRMDocumentToProvider htp ON htp.hrmDocumentId = h.id "
                + "LEFT JOIN demographic d ON d.demographic_no = htd.demographicNo "
                + "LEFT JOIN read_lab r ON r.provider_no = :hrmReadProviderNumber AND r.lab_id = h.id AND r.lab_type = 'HRM' "
                + whereSql
                + " GROUP BY h.id";

        if (getDemographicCounts) { // add grouping to query
            sql += ") AS hrmGrouped GROUP BY lab_patient_id ";
        }

        return sql;
    }

    private String getHRMReportsWhereSql(String loggedInProviderNo, String providerNumber, String firstName, String lastName, String hin, String startDate, String endDate, String status, InboxQueryParameters.MatchedStatus matchedStatus, Map<String, String> whereValues) {
        String whereSql = "";
        whereValues.put("hrmReadProviderNumber", loggedInProviderNo);

        if (!providerNumber.isEmpty()) {
            whereSql += " WHERE htp.providerNo = :hrmProviderNumber";
            if (providerNumber.equals("0")) {
                whereValues.put("hrmProviderNumber", "-1");
            } else {
                whereValues.put("hrmProviderNumber", providerNumber);
            }
        }

        if (!firstName.isEmpty()) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " d.first_name LIKE :hrmFirstName";
            whereValues.put("hrmFirstName", firstName + "%");
        }

        if (!lastName.isEmpty()) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " d.last_name LIKE :hrmLastName";
            whereValues.put("hrmLastName", lastName + "%");
        }

        if (!hin.isEmpty()) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " d.hin LIKE :hrmHin";
            whereValues.put("hrmHin", hin + "%");
        }

        if (matchedStatus != InboxQueryParameters.MatchedStatus.ALL) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            if (matchedStatus == InboxQueryParameters.MatchedStatus.MATCHED) {
                whereSql += " htd.demographicNo IS NOT NULL";
            } else if (matchedStatus == InboxQueryParameters.MatchedStatus.NOT_MATCHED) {
                whereSql += " htd.demographicNo IS NULL";
            }
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        if (!startDate.isEmpty()) {
            try {
                String formattedDate = sdf.format(sdf.parse(startDate));

                whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
                whereSql += " DATE(timeReceived) >= :hrmStartDate";
                whereValues.put("hrmStartDate", formattedDate);
            } catch (ParseException e) {
                logger.warn("Error parsing inbox search date: " + startDate);
            }
        }

        if (!endDate.isEmpty()) {
            try {
                String formattedDate = sdf.format(sdf.parse(endDate));

                whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
                whereSql += " DATE(timeReceived) <= :hrmEndDate";
                whereValues.put("hrmEndDate", formattedDate);
            } catch (ParseException e) {
                logger.warn("Error parsing inbox search date: " + endDate);
            }
        }

        if (!status.equals("L") && !status.equals("X") && !status.equals("F")) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " htp.signedOff = :hrmStatus";
            whereValues.put("hrmStatus", String.valueOf(status.equals("N") ? 0 : 1));
        }

        if (!status.equals("F")) {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " htp.filed = false";
        } else {
            whereSql += whereSql.isEmpty() ? " WHERE" : " AND";
            whereSql += " htp.filed = true";
        }
        return whereSql;
    }

}
