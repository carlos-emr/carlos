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

public class LabDataController {

    private boolean providerSearch;
    private boolean patientSearch;

    public LabDataController() {
        providerSearch = true;
        patientSearch = true;
    }

    //Converts given string date to date object. Returns null if not in yyyy-MM-dd format or blank.
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

    //Grabs lab link for specific inbox item.
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

    //Gets inbox CategoryData for given query. CategoryData includes document counts for all document types & patient lists.
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

    //Returns lab data based on the query. If lab, doc, and hrm flags are not set in the query returns all data from all data types.
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

    public void setInboxFormQueryUnclaimed(InboxhubQuery query, String unclaimed) {
        if (unclaimed == null || !unclaimed.equals("1")) { return; }
        query.reset(null);
        query.setSearchAll(ProviderSearchFilter.NO_PROVIDER.getValue());
        query.setStatus(StatusFilter.NEW.getValue());
    }

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