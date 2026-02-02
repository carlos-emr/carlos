/*
 *Copyright (c) 2023. Magenta Health Inc. All Rights Reserved.
 *
 *This software is published under the GPL GNU General Public License.
 *
 *This program is free software; you can redistribute it and/or
 *modify it under the terms of the GNU General Public License
 *as published by the Free Software Foundation; either version 2
 *of the License, or (at your option) any later version.
 *This program is distributed in the hope that it will be useful,
 *but WITHOUT ANY WARRANTY; without even the implied warranty of
 *MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *GNU General Public License for more details.
 *
 *You should have received a copy of the GNU General Public License
 *along with this program; if not, write to the Free Software
 *Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
 */

package ca.openosp.openo.inboxhub.inboxdata;

import org.apache.commons.lang3.time.DateFormatUtils;
import ca.openosp.openo.commn.dao.InboxResultsDao;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.inboxhub.query.InboxhubQuery;
import ca.openosp.openo.inboxhub.query.InboxhubQuery.ProviderSearchFilter;
import ca.openosp.openo.inboxhub.query.InboxhubQuery.StatusFilter;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.lab.ca.on.CommonLabResultData;
import ca.openosp.openo.lab.ca.on.HRMResultsData;
import ca.openosp.openo.lab.ca.on.LabResultData;
import ca.openosp.openo.mds.data.CategoryData;
import ca.openosp.openo.mds.data.ProviderData;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Controller for managing laboratory and medical document data in the InboxHub system.
 *
 * <p>This controller provides comprehensive functionality for retrieving, filtering, and processing
 * laboratory results, medical documents, and Hospital Report Manager (HRM) records. It serves as the
 * primary data access layer for the InboxHub feature, handling various types of medical data including:</p>
 *
 * <ul>
 *   <li>Laboratory results (CML, HL7 TEXT, MDS formats)</li>
 *   <li>Medical documents</li>
 *   <li>Hospital Report Manager (HRM) documents</li>
 *   <li>Reference and consultation documents (REF_I12, ORU_R01)</li>
 * </ul>
 *
 * <p>The controller supports advanced filtering capabilities including:</p>
 * <ul>
 *   <li>Provider-based filtering (specific provider, all providers, or unclaimed)</li>
 *   <li>Patient-based filtering (demographic matching, name, health number)</li>
 *   <li>Status filtering (new, acknowledged, filed)</li>
 *   <li>Date range filtering</li>
 *   <li>Abnormal results filtering</li>
 *   <li>Document type filtering (lab, doc, HRM, or all)</li>
 * </ul>
 *
 * <p>Key healthcare features include:</p>
 * <ul>
 *   <li>Duplicate lab version filtering based on accession numbers and date proximity</li>
 *   <li>URL generation for different lab display types with proper encoding</li>
 *   <li>Category-based document counting for inbox organization</li>
 *   <li>Support for matched and unmatched patient records</li>
 * </ul>
 *
 * @see InboxhubQuery
 * @see LabResultData
 * @see CategoryData
 * @see CommonLabResultData
 * @see HRMResultsData
 * @since 2026-01-24
 */
public class LabDataController {

    private boolean providerSearch;
    private boolean patientSearch;

    /**
     * Constructs a new LabDataController with default search settings.
     *
     * <p>Initializes the controller with both provider and patient search enabled by default.
     * These flags control whether provider-specific and patient-specific filtering is applied
     * when retrieving laboratory and document data.</p>
     */
    public LabDataController() {
        providerSearch = true;
        patientSearch = true;
    }

    /**
     * Converts a string date representation to a Date object.
     *
     * <p>Parses a date string in ISO 8601 format (yyyy-MM-dd) and converts it to a java.util.Date
     * object. The conversion uses the system default time zone and sets the time to the start of
     * day (00:00:00). If the input string is empty or not in the expected format, returns null.</p>
     *
     * <p>This method is used throughout the InboxHub system to convert user-provided date strings
     * from query parameters into Date objects for database queries and date range filtering.</p>
     *
     * @param stringDate String the date string to convert in yyyy-MM-dd format
     * @return Date the converted Date object set to start of day in system default timezone,
     *         or null if the input is empty or cannot be parsed
     */
    public Date convertDate(String stringDate) {
        if (!stringDate.isEmpty()) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DateFormatUtils.ISO_DATE_FORMAT.getPattern());
            try {
                LocalDate localDate = LocalDate.parse(stringDate, formatter);
                Instant instant = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
                return Date.from(instant);
            } catch (DateTimeParseException e) {
                MiscUtils.getLogger().error(e);
                return null;
            }
        }
        return null;
    }

    /**
     * Generates display URLs for laboratory results and medical documents.
     *
     * <p>Creates properly formatted and URL-encoded links for viewing different types of laboratory
     * results and medical documents in the EMR system. The method determines the appropriate display
     * JSP or servlet based on the result type and constructs URLs with all necessary query parameters.</p>
     *
     * <p>Supported result types and their display destinations:</p>
     * <ul>
     *   <li><b>MDS</b>: Routed to /SegmentDisplay.jsp for Medical Data Systems results</li>
     *   <li><b>CML</b>: Routed to /lab/CA/ON/CMLDisplay.jsp for Ontario CML lab results</li>
     *   <li><b>HL7 TEXT</b>: Routes based on discipline/category:
     *     <ul>
     *       <li>REF_I12: Consultation/referral display via /oscarEncounter/ViewRequest.do</li>
     *       <li>ORU_R01: Observation results via /lab/CA/ALL/viewOruR01.jsp</li>
     *       <li>Other: Generic HL7 display via /lab/CA/ALL/labDisplay.jsp</li>
     *     </ul>
     *   </li>
     *   <li><b>Document</b>: Routed to /documentManager/showDocument.jsp for medical documents</li>
     *   <li><b>HRM</b>: Hospital Report Manager display via /hospitalReportManager/Display.do with duplicate handling</li>
     *   <li><b>Other</b>: Default BC lab display via /lab/CA/BC/labDisplay.jsp</li>
     * </ul>
     *
     * <p>All URLs include properly encoded parameters for segment ID, provider numbers, result status,
     * patient demographics, and search criteria to maintain context when viewing results.</p>
     *
     * @param results ArrayList&lt;LabResultData&gt; the list of laboratory results to generate links for
     * @param query InboxhubQuery the query object containing search parameters and filters
     * @param contextPath String the web application context path for URL construction
     * @param providerNo String the provider number of the currently logged-in user
     * @return ArrayList&lt;String&gt; list of fully constructed and encoded URLs for each lab result,
     *         in the same order as the input results
     */
    public ArrayList<String> getLabLink(ArrayList<LabResultData> results, InboxhubQuery query, String contextPath, String providerNo) {
        ArrayList<String> labLinks = new ArrayList<String>();
        for (int i = 0; i < results.size(); i++) {
            StringBuilder url = new StringBuilder(contextPath);
            LabResultData labResult = results.get(i);
            //Setting inbox item type:
            if (labResult.isMDS()) {
                url.append("/SegmentDisplay.jsp?");
            }
            else if (labResult.isCML()) {
                url.append("/lab/CA/ON/CMLDisplay.jsp?");
            }
            else if (labResult.isHL7TEXT()) {
                String categoryType = labResult.getDiscipline();
                if ("REF_I12".equals(categoryType)) {
                    url.append("/oscarEncounter/ViewRequest.do?");
                }
                else if (!categoryType.isEmpty() && categoryType.startsWith("ORU_R01:")) {
                    url.append("/lab/CA/ALL/viewOruR01.jsp?");
                }
                else {
                    url.append("/lab/CA/ALL/labDisplay.jsp?inWindow=true");
                    url.append("&showLatest=true");
                }
            }
            else if(labResult.isDocument()) {
                url.append("/documentManager/showDocument.jsp?inWindow=true");
            }
            else if(labResult.isHRM()) {
                url.append("/hospitalReportManager/Display.do?");
                StringBuilder duplicateLabIds=new StringBuilder();
                for (Integer duplicateLabId : labResult.getDuplicateLabIds())
                {
                    if (duplicateLabIds.length()>0) duplicateLabIds.append(',');
                    duplicateLabIds.append(duplicateLabId);
                }
                url.append("duplicateLabIds=");
                url.append(encodeURL(duplicateLabIds.toString()));
                url.append("&id=");
                url.append(encodeURL(labResult.getSegmentID()));
            }
            else {
                url.append("/lab/CA/BC/labDisplay.jsp?");
            }
            url.append("&segmentID=");
            url.append(encodeURL(labResult.getSegmentID()));
            url.append("&providerNo=");
            url.append(encodeURL(providerNo));
            url.append("&searchProviderNo=");
            url.append(encodeURL(query.getSearchProviderNo()));
            url.append("&status=");
            url.append(encodeURL(labResult.resultStatus));
            url.append("&demoName=");
            url.append(encodeURL(labResult.getPatientName()));
            labLinks.add(url.toString());
        }
        return labLinks;
    }

    /**
     * Retrieves category data and document counts for the InboxHub based on query parameters.
     *
     * <p>Creates and populates a CategoryData object containing comprehensive statistics about
     * laboratory results, medical documents, and HRM records matching the query criteria. The
     * category data includes document counts by type, patient lists, and filtering metadata.</p>
     *
     * <p>The method analyzes the query to determine search modes:</p>
     * <ul>
     *   <li><b>Provider search disabled</b>: When searchProviderNo is "-1" (all providers)</li>
     *   <li><b>Patient search disabled</b>: When no demographic number, patient name, or health number provided</li>
     * </ul>
     *
     * <p>The CategoryData object provides counts for:</p>
     * <ul>
     *   <li>Total documents (matched and unmatched)</li>
     *   <li>Total laboratory results (matched and unmatched)</li>
     *   <li>Total HRM documents (matched and unmatched)</li>
     *   <li>Patient lists associated with the results</li>
     * </ul>
     *
     * <p>These counts are essential for displaying inbox statistics, pagination controls,
     * and category filters in the InboxHub user interface.</p>
     *
     * @param query InboxhubQuery the query object containing search filters including patient demographics,
     *              provider number, status filter, abnormal filter, and date range
     * @return CategoryData the populated category data object containing document counts and patient lists,
     *         with counts set to zero if SQL errors occur during population
     */
    public CategoryData getCategoryData(InboxhubQuery query) {
        if (Objects.equals(query.getSearchProviderNo(), "-1")) {
            providerSearch = false;
        }
        if ((query.getDemographicNo() == null || query.getDemographicNo().equals("0")) && query.getPatientFirstName().isEmpty() && query.getPatientLastName().isEmpty() && query.getPatientHealthNumber().isEmpty()) {
            patientSearch = false;
        }

        CategoryData categoryData = new CategoryData(query.getPatientLastName(), query.getPatientFirstName(), query.getPatientHealthNumber(), patientSearch, providerSearch, query.getSearchProviderNo(), query.getStatusFilter().getValue(), query.getAbnormalFilter().getValue(), query.getStartDate(), query.getEndDate());
        try {
            categoryData.populateCountsAndPatients();
        } catch (SQLException e) {
            MiscUtils.getLogger().error(e);
        }
        return categoryData;
    }

    /**
     * Retrieves laboratory results, medical documents, and HRM data based on query parameters.
     *
     * <p>This is the primary data retrieval method for the InboxHub, aggregating results from multiple
     * data sources (laboratory results, medical documents, Hospital Report Manager records) into a
     * unified list. The method applies various filters including provider, patient demographics,
     * status, date range, and abnormal results flags.</p>
     *
     * <p>Data source selection logic:</p>
     * <ul>
     *   <li>If no type flags are set (lab, doc, hrm all false), retrieves data from all sources</li>
     *   <li>If specific type flags are set, retrieves only the selected types</li>
     *   <li>Mixed mode: When both lab and doc flags are set, results are interleaved</li>
     * </ul>
     *
     * <p>Special processing for laboratory results:</p>
     * <ul>
     *   <li>Uses increased page size (100) to reduce older lab versions in results</li>
     *   <li>Applies duplicate lab version filtering based on accession numbers and date proximity</li>
     *   <li>Filters out labs within 4 months of each other with matching accession numbers</li>
     * </ul>
     *
     * <p>HRM-specific behavior:</p>
     * <ul>
     *   <li>HRM results are excluded when abnormal filter is explicitly enabled</li>
     *   <li>Provider filtering for HRM uses empty string for "ANY_PROVIDER" mode</li>
     * </ul>
     *
     * <p>The method uses pagination (configurable page size, default handling) and supports
     * both matched and unmatched patient records. All date conversions use the convertDate
     * method for consistent ISO 8601 formatting.</p>
     *
     * @param loggedInInfo LoggedInInfo the logged-in user session information for security context
     * @param query InboxhubQuery the query object containing all search parameters, filters, pagination
     *              settings, and type flags (lab, doc, hrm)
     * @return ArrayList&lt;LabResultData&gt; unified list of laboratory results, documents, and HRM records
     *         matching the query criteria, with duplicate lab versions filtered and results from
     *         selected data sources combined
     */
    public ArrayList<LabResultData> getLabData(LoggedInInfo loggedInInfo, InboxhubQuery query) {
        Integer page = query.getPage() - 1;
        Integer pageSize = query.getPageSize();

        // Increase page size to 100 to reduce older versions
        Integer labPageSize = 100;

        //Whether to use the paging functionality. Currently setting this to false does not function and crashes the inbox.
        Boolean isPaged = true;
        Boolean mixLabsAndDocs = true;
        Date startDate = convertDate(query.getStartDate());
        Date endDate= convertDate(query.getEndDate());
        CommonLabResultData comLab = new CommonLabResultData();
        InboxResultsDao inboxResultsDao = (InboxResultsDao) SpringUtils.getBean("inboxResultsDao");
        ArrayList<LabResultData> labDocs = new ArrayList<LabResultData>();

        Boolean all = (!query.getDoc() && !query.getLab() && !query.getHrm());
        mixLabsAndDocs = query.getDoc() && query.getLab();
        if (query.getDoc() || all) {
            labDocs.addAll(inboxResultsDao.populateDocumentResultsData(query.getSearchProviderNo(), query.getDemographicNo(), query.getPatientFirstName(),
                query.getPatientLastName(), query.getPatientHealthNumber(), query.getStatusFilter().getValue(), isPaged, page, pageSize, mixLabsAndDocs, query.getAbnormalBool(), startDate , endDate));
        }
        if (query.getLab() || all) {
            List<LabResultData> labs = comLab.populateLabResultsData(loggedInInfo,query.getSearchProviderNo(), query.getDemographicNo(), query.getPatientFirstName(),
                                                query.getPatientLastName(), query.getPatientHealthNumber(), query.getStatusFilter().getValue(), isPaged, page, labPageSize, mixLabsAndDocs, query.getAbnormalBool(), startDate, endDate);
            labDocs.addAll(filterOldLabVersions(labs));
        }
        if ((query.getHrm() || all) && (query.getAbnormalBool() == null || !query.getAbnormalBool())) {
            HRMResultsData hrmResult = new HRMResultsData();
            String searchProvider = query.getProviderSearchFilter().equals(ProviderSearchFilter.ANY_PROVIDER) ? "" : query.getSearchProviderNo();
            labDocs.addAll(hrmResult.populateHRMdocumentsResultsData(loggedInInfo, searchProvider, query.getPatientFirstName(),
                query.getPatientLastName(), query.getPatientHealthNumber(), query.getDemographicNo(), query.getStatusFilter().getValue(), startDate, endDate, isPaged, page, pageSize));
        }
        return labDocs;
    }

    /**
     * Sanitizes and validates InboxHub query parameters from form submission.
     *
     * <p>This method processes and normalizes query parameters received from the InboxHub form,
     * applying business logic and security rules to ensure valid and consistent search criteria.
     * It handles provider filtering, patient matching, and document type selection based on
     * user input and session context.</p>
     *
     * <p>Provider filtering logic:</p>
     * <ul>
     *   <li><b>ANY_PROVIDER</b>: Sets searchProviderNo to "-1" for all providers</li>
     *   <li><b>NO_PROVIDER</b>: Sets searchProviderNo to "0" for unclaimed results</li>
     *   <li><b>Invalid values ("0" or "-1")</b>: Resets to logged-in provider for security</li>
     *   <li>Valid provider number: Preserved as-is</li>
     * </ul>
     *
     * <p>Patient filtering logic:</p>
     * <ul>
     *   <li><b>Unmatched flag set</b>: Forces demographicNo to "0" and clears all patient search fields</li>
     *   <li><b>demographicFilter provided</b>: Overrides query's demographicNo with filter value</li>
     * </ul>
     *
     * <p>Type filtering logic (when typeFilterValue provided):</p>
     * <ul>
     *   <li><b>DOC</b>: Sets doc=true, lab=false, hrm=false</li>
     *   <li><b>LAB</b>: Sets lab=true, doc=false, hrm=false</li>
     *   <li><b>HRM</b>: Sets hrm=true, doc=false, lab=false</li>
     *   <li><b>Other/null</b>: No changes to type flags</li>
     * </ul>
     *
     * <p>This method ensures that queries are properly scoped to the logged-in provider's
     * permissions and that patient privacy is maintained by preventing unauthorized access
     * to all-provider or unclaimed results without proper authorization.</p>
     *
     * @param loggedInInfo LoggedInInfo the logged-in user session containing provider number
     *                     and session attributes for security context
     * @param query InboxhubQuery the query object to sanitize and validate, modified in-place
     * @param demographicFilter String optional demographic number to override query's demographic
     *                          filter, or null to use query's existing value
     * @param typeFilterValue String optional document type filter ("doc", "lab", "hrm"),
     *                        or null to preserve existing type flags
     */
    public void sanitizeInboxFormQuery(LoggedInInfo loggedInInfo, InboxhubQuery query, String demographicFilter, String typeFilterValue) {
        String loggedInProviderNo = (String) loggedInInfo.getSession().getAttribute("user");
        Provider loggedInProvider = ProviderData.getProvider(loggedInProviderNo);

        //Checking unclaimed vs claimed physician. If no searchAll/providers search filter is provided reset search to logged in providers.
        if (Objects.equals(query.getProviderSearchFilter(), ProviderSearchFilter.ANY_PROVIDER)) {//All
            query.setSearchProviderNo("-1");
            query.setSearchProviderName("");
        }
        else if (Objects.equals(query.getProviderSearchFilter(), ProviderSearchFilter.NO_PROVIDER)) {
            query.setSearchProviderNo("0");
            query.setSearchProviderName("");
        }
        else if (Objects.equals(query.getSearchProviderNo(), "0") || Objects.equals(query.getSearchProviderNo(), "-1")) {
            query.setSearchProviderNo(loggedInProviderNo);
            query.setSearchProviderName(loggedInProvider == null ? "" : loggedInProvider.getFormattedName());
        }

        //checking unmatched vs matched patient. Setting demographic number to 0 will grab inbox objects with no patient attached. This should overwrite all current patient queries.
        if (query.getUnmatched()) {
            query.setDemographicNo("0");
            query.setPatientFirstName("");
            query.setPatientLastName("");
            query.setPatientHealthNumber("");
        }

        if (demographicFilter != null) {
            query.setDemographicNo(demographicFilter);
        }

        if (typeFilterValue != null) {
            InboxhubQuery.TypeFilter typeFilter = InboxhubQuery.TypeFilter.fromValue(typeFilterValue.toLowerCase());
            switch (typeFilter) {
                case DOC: query.setLab(false); query.setDoc(true); query.setHrm(false); break;
                case LAB: query.setLab(true); query.setDoc(false); query.setHrm(false); break;
                case HRM: query.setLab(false); query.setDoc(false); query.setHrm(true); break;
                default: break;
            }
        }
    }

    /**
     * Configures the query to display only unclaimed inbox results.
     *
     * <p>When the unclaimed parameter is set to "1", this method resets the query to a specific
     * configuration designed to show only unclaimed (unassigned) laboratory results and documents.
     * This is a common use case in healthcare workflows where results need to be triaged and
     * assigned to providers.</p>
     *
     * <p>Unclaimed query configuration:</p>
     * <ul>
     *   <li>Resets all existing query parameters to defaults (preserving null session)</li>
     *   <li>Sets provider filter to NO_PROVIDER mode (searchProviderNo = "0")</li>
     *   <li>Sets status filter to NEW only (excludes acknowledged and filed results)</li>
     * </ul>
     *
     * <p>If the unclaimed parameter is null or not "1", the method returns immediately without
     * modifying the query, allowing other filtering criteria to remain active.</p>
     *
     * @param query InboxhubQuery the query object to configure for unclaimed results, modified in-place
     * @param unclaimed String flag indicating whether to show unclaimed results, must be "1" to activate,
     *                  null or any other value leaves query unchanged
     */
    public void setInboxFormQueryUnclaimed(InboxhubQuery query, String unclaimed) {
        if (unclaimed == null || !unclaimed.equals("1")) { return; }
        query.reset(null);
        query.setSearchAll(ProviderSearchFilter.NO_PROVIDER.getValue());
        query.setStatus(StatusFilter.NEW.getValue());
    }

    /**
     * Calculates total result counts for documents, labs, and HRM records based on query filters.
     *
     * <p>This method computes comprehensive statistics about the number of results matching the
     * query criteria, broken down by document type (documents, laboratory results, HRM records).
     * The counts are calculated from pre-populated CategoryData and are essential for displaying
     * pagination controls, result summaries, and category tabs in the InboxHub interface.</p>
     *
     * <p>Type selection logic:</p>
     * <ul>
     *   <li>If no type flags are set (lab, doc, hrm all false), counts all three types</li>
     *   <li>If specific type flags are set, counts only the selected types</li>
     * </ul>
     *
     * <p>Special handling for HRM counts:</p>
     * <ul>
     *   <li>HRM results are excluded when abnormal filter is explicitly enabled</li>
     *   <li>This prevents HRM records from appearing in abnormal results views</li>
     * </ul>
     *
     * <p>The method delegates to private helper methods (getDocumentCount, getLabCount, getHRMCount)
     * which apply patient search and unmatched filtering logic to the CategoryData counts.</p>
     *
     * @param query InboxhubQuery the query object containing type flags (doc, lab, hrm) and
     *              abnormal filter settings
     * @param categoryData CategoryData pre-populated category data containing total, matched,
     *                     and unmatched counts for all document types
     * @return int[] array of four integers containing:
     *         [0] total documents count (excluding HRMs),
     *         [1] total laboratory results count,
     *         [2] total HRM records count,
     *         [3] combined total of all selected types
     */
    public int[] getTotalResultsCountBasedOnQuery(InboxhubQuery query, CategoryData categoryData) {
        int totalDocsCount = 0;
        int totalLabsCount = 0;
        int totalHRMCount = 0;
        int totalResultsCount = 0;

        Boolean all = (!query.getDoc() && !query.getLab() && !query.getHrm());

        // Documents
        if (query.getDoc() || all) {
            totalDocsCount = getDocumentCount(query, categoryData);
            totalResultsCount += totalDocsCount;
        }

        // Labs
        if (query.getLab() || all) {
            totalLabsCount = getLabCount(query, categoryData);
            totalResultsCount += totalLabsCount;
        }

        // HRMs
        if ((query.getHrm() || all) && (query.getAbnormalBool() == null || !query.getAbnormalBool())) {
            totalHRMCount = getHRMCount(query, categoryData);
            totalResultsCount += totalHRMCount;
        }

        int[] resultCounts = {0, 0, 0, 0};
        // Set values in the result array
        resultCounts[0] = totalDocsCount;   // Docs count
        resultCounts[1] = totalLabsCount;   // Labs count
        resultCounts[2] = totalHRMCount;    // HRMs count
        resultCounts[3] = totalResultsCount; // Total count

        return resultCounts;
    }

    private String encodeURL(String url) {
        String encodedUrl = "";
        try {
            encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            MiscUtils.getLogger().error(e);
        }
        return encodedUrl;
    }

    private List<LabResultData> filterOldLabVersions(List<LabResultData> labs) {
        HashMap<String, LabResultData> labMap = new HashMap<>();

        // Maps unique accession keys to a list of associated segment IDs
        LinkedHashMap<String, ArrayList<String>> accessionMap = new LinkedHashMap<>();

        // Counter to handle cases where accession numbers are missing
        int accessionNumCount = 0;

        for (LabResultData result : labs) {
            String segmentId = result.getSegmentID();
            labMap.put(segmentId, result);

            String accessionKey;
            ArrayList<String> labNums = new ArrayList<>();

            if (Objects.isNull(result.accessionNumber) || result.accessionNumber.equalsIgnoreCase("null") || result.accessionNumber.isEmpty()) {
                accessionNumCount++;
                accessionKey = "noAccessionNum" + accessionNumCount + result.labType;
                labNums.add(segmentId);
                accessionMap.put(accessionKey, labNums);
                continue;
            }

            accessionKey = result.accessionNumber + result.labType;
            labNums = accessionMap.getOrDefault(accessionKey, new ArrayList<>());
            
            boolean isMatchFound = false;

            // Compare the current lab result with existing ones in the same accession group
            for (String labSegmentId : labNums) {
                LabResultData matchingResult = labMap.get(labSegmentId);

                LocalDate dateA = result.getDateObj().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                LocalDate dateB = matchingResult.getDateObj().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

                // Calculate the difference in months between the two dates
                long monthsBetween = (dateA == null || dateB == null) ? 5 : Math.abs(ChronoUnit.MONTHS.between(dateA, dateB));

                // Skip if the difference in months is 4 or more
                if (monthsBetween >= 4) { continue; }

                // Mark a match as found and break the loop
                isMatchFound = true;
                break;
            }

            // Skip adding this result if a match is found
            if (isMatchFound) { continue; }

            labNums.add(segmentId);
            accessionMap.put(accessionKey, labNums);
        }

        labs.clear();

        // Collect filtered lab results based on the accessionMap
        for (ArrayList<String> labNums : accessionMap.values()) {
            for (int j = 0; j < labNums.size(); j++) {
                labs.add(labMap.get(labNums.get(j)));
            }
        }

        return labs;
    }

    /*
     * Subtracting MatchedHRMCount and UnmatchedHRMCount from total docs because totalDocs include both documents and HRMs
     */
    private int getDocumentCount(InboxhubQuery query, CategoryData categoryData) {
        int documentCount = 0;
        if (patientSearch) {
            documentCount = (categoryData.getTotalDocs() - categoryData.getUnmatchedDocs()) - categoryData.getMatchedHRMCount() - categoryData.getUnmatchedHRMCount();
        } else if (query.getUnmatched()) {
            documentCount = categoryData.getUnmatchedDocs();
        } else {
            documentCount = categoryData.getTotalDocs() - categoryData.getMatchedHRMCount() - categoryData.getUnmatchedHRMCount();
        }
        return documentCount;
    }

    private int getLabCount(InboxhubQuery query, CategoryData categoryData) {
        int labCount = 0;
        if (patientSearch) {
            labCount = categoryData.getTotalLabs() - categoryData.getUnmatchedLabs();
        } else if (query.getUnmatched()) {
            labCount = categoryData.getUnmatchedLabs();
        } else {
            labCount = categoryData.getTotalLabs();
        }
        return labCount;
    }

    private int getHRMCount(InboxhubQuery query, CategoryData categoryData) {
        int hrmCount = 0;
        if (patientSearch) {
            hrmCount = categoryData.getMatchedHRMCount();
        } else if (query.getUnmatched()) {
            hrmCount = categoryData.getUnmatchedHRMCount();
        } else {
            hrmCount = categoryData.getMatchedHRMCount() + categoryData.getUnmatchedHRMCount();
        }
        return hrmCount;
    }
}