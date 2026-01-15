//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */

package ca.openosp.openo.hospitalReportManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import ca.openosp.OscarProperties;
import ca.openosp.openo.hospitalReportManager.dao.*;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.hospitalReportManager.model.HRMCategory;
import ca.openosp.openo.hospitalReportManager.model.HRMDocument;
import ca.openosp.openo.hospitalReportManager.model.HRMDocumentSubClass;
import ca.openosp.openo.hospitalReportManager.model.HRMDocumentToDemographic;
import ca.openosp.openo.hospitalReportManager.model.HRMSubClass;
import ca.openosp.openo.managers.NioFileManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PDFGenerationException;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.lab.ca.on.HRMResultsData;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.util.StringUtils;

public class HRMUtil {

    private static final Logger logger = MiscUtils.getLogger();

    public static final String DATE = "time_received";
    public static final String TYPE = " report_type";

    private static HRMDocumentDao hrmDocumentDao = (HRMDocumentDao) SpringUtils.getBean(HRMDocumentDao.class);
    private static HRMDocumentToDemographicDao hrmDocumentToDemographicDao = (HRMDocumentToDemographicDao) SpringUtils.getBean(HRMDocumentToDemographicDao.class);
    private static HRMSubClassDao hrmSubClassDao = (HRMSubClassDao) SpringUtils.getBean(HRMSubClassDao.class);
    private static HRMDocumentSubClassDao hrmDocumentSubClassDao = (HRMDocumentSubClassDao) SpringUtils.getBean(HRMDocumentSubClassDao.class);
    private static HRMCategoryDao hrmCategoryDao = SpringUtils.getBean(HRMCategoryDao.class);
    private static final NioFileManager nioFileManager = SpringUtils.getBean(NioFileManager.class);
    private static final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    public HRMUtil() {

    }

    /*
     * Return information about each HRM document associated with a particular demographic
     * Because multiple versions of a single HRM document can be received,
     */
    public static ArrayList<HashMap<String, ? extends Object>> listHRMDocuments(LoggedInInfo loggedInInfo, String sortBy, boolean sortAsc, String demographicNo, boolean filterDuplicates) {
        if (!OscarProperties.getInstance().isOntarioBillingRegion()) {
            return new ArrayList<>();
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", SecurityInfoManager.READ, null)) {
            LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), "HRMUtil.listHRMDocuments", "UNAUTHORIZED", "missing required security object (_hrm)", loggedInInfo.getIp(), demographicNo, null);
            logger.warn("missing required security object (_hrm)");
            return new ArrayList<>();
        }

        ArrayList<HashMap<String, ? extends Object>> hrmdocslist = new ArrayList<HashMap<String, ?>>();

        //get a list of all HRM documents linked to a particular demographic
        List<HRMDocumentToDemographic> hrmDocResultsDemographic = hrmDocumentToDemographicDao.findByDemographicNo(demographicNo);
        List<HRMDocument> hrmDocumentsAll = new LinkedList<HRMDocument>();
        HashMap<String, ArrayList<Integer>> duplicateLabIds = new HashMap<String, ArrayList<Integer>>();

        //multiple versions of essentially the same HRM document are possible
        //create a list of documents to display that include / exclude duplicates based on filterDuplicates parameter
        HashMap<String, HRMDocument> docsToDisplay = null;
        if (filterDuplicates) {
            docsToDisplay = filterDuplicates(loggedInInfo, hrmDocResultsDemographic, duplicateLabIds);
        } else {
            docsToDisplay = noFilterDuplicates(loggedInInfo, hrmDocResultsDemographic);
        }

        //iterate over this set to generate a hashmap of summary information for each HRM document
        //(e.g. id, time received, type, status, etc...)
        //the set of HRM documents should not be changed, this is simply generating the most appropriate summary in the circumstances
        for (Map.Entry<String, HRMDocument> entry : docsToDisplay.entrySet()) {
            String duplicateKey = entry.getKey();
            HRMDocument hrmDocument = entry.getValue();
            HRMReport hrmReport = HRMReportParser.parseReport(loggedInInfo, hrmDocument.getReportFile());
            if (hrmReport == null) {
                continue;
            }

            String categoryName = "";
            if (hrmDocument.getHrmCategoryId() != null) {
                HRMCategory category = hrmCategoryDao.find(hrmDocument.getHrmCategoryId());
                categoryName = category.getCategoryName();
            }

            String dispSubClass = "";
            HRMSubClass hrmSubClass;
            List<HRMDocumentSubClass> subClassList = hrmDocumentSubClassDao.getSubClassesByDocumentId(hrmDocument.getId());
            if (hrmReport.getFirstReportClass().equalsIgnoreCase("Diagnostic Imaging Report") || hrmReport.getFirstReportClass().equalsIgnoreCase("Cardio Respiratory Report")) {
                //Get first sub class to display on eChart
                if (subClassList != null && subClassList.size() > 0) {
                    HRMDocumentSubClass firstSubClass = subClassList.get(0);
                    hrmSubClass = hrmSubClassDao.findApplicableSubClassMapping(hrmReport.getFirstReportClass(), firstSubClass.getSubClass(), firstSubClass.getSubClassMnemonic(), hrmReport.getSendingFacilityId());
                    dispSubClass = hrmSubClass != null ? hrmSubClass.getSubClassDescription() : "";
                }

                if (StringUtils.isNullOrEmpty(dispSubClass) && hrmReport.getAccompanyingSubclassList().size() > 0) {
                    // if sub class doesn't exist, display the accompanying subclass
                    dispSubClass = hrmReport.getFirstAccompanyingSubClass();
                }
            } else {
                //Medical Records Report
                String[] reportSubClass = hrmReport.getFirstReportSubClass() != null ? hrmReport.getFirstReportSubClass().split("\\^") : null;
                if (reportSubClass != null && reportSubClass.length > 1) {
                    dispSubClass = reportSubClass[1];
                }
            }

            String reportDate = "";
            if (hrmDocument.getReportDate() != null) {
                reportDate = hrmDocument.getReportDate().toString();
            }

            String displayHRMDocumentName = getHRMDocumentDisplayName(hrmDocument.getDescription(), dispSubClass, hrmDocument.getReportType(), hrmDocument.getReportStatus());

            HashMap<String, Object> curht = new HashMap<String, Object>();
            curht.put("id", hrmDocument.getId());
            curht.put("time_received", hrmDocument.getTimeReceived().toString());
            curht.put("report_type", hrmDocument.getReportType());
            curht.put("report_status", hrmDocument.getReportStatus());
            curht.put("report_date", reportDate);
            curht.put("category", categoryName);
            curht.put("description", hrmDocument.getDescription());
            curht.put("class_subclass", dispSubClass);
            curht.put("name", displayHRMDocumentName);

            if (filterDuplicates) {
                StringBuilder duplicateLabIdQueryString = new StringBuilder();
                ArrayList<Integer> duplicateIdList = duplicateLabIds.get(duplicateKey);
                if (duplicateIdList != null) {
                    for (Integer duplicateLabIdTemp : duplicateIdList) {
                        if (duplicateLabIdQueryString.length() > 0) duplicateLabIdQueryString.append(',');
                        duplicateLabIdQueryString.append(duplicateLabIdTemp);
                    }
                }
                curht.put("duplicateLabIds", duplicateLabIdQueryString.toString());
            }

            hrmdocslist.add(curht);
            hrmDocumentsAll.add(hrmDocument);
        }

        if ("report_name".equals(sortBy)) {
            Collections.sort(hrmdocslist, new Comparator<HashMap<String, ? extends Object>>() {
                public int compare(HashMap<String, ? extends Object> o1, HashMap<String, ? extends Object> o2) {
                    return ((String) o1.get("report_type")).compareTo((String) o2.get("report_type"));
                }
            });
        } else if ("report_date".equals(sortBy)) {
            Collections.sort(hrmdocslist, new Comparator<HashMap<String, ? extends Object>>() {
                public int compare(HashMap<String, ? extends Object> o1, HashMap<String, ? extends Object> o2) {
                    if (((String) o1.get("report_date")).equals((String) o2.get("report_date"))) {
                        //if sorting by report date, use the time received as a secondary sort if the report dates are equal
                        return ((String) o1.get("time_received")).compareTo((String) o2.get("time_received"));
                    }
                    return ((String) o1.get("report_date")).compareTo((String) o2.get("report_date"));
                }
            });
        } else if ("time_received".equals(sortBy)) {
            Collections.sort(hrmdocslist, new Comparator<HashMap<String, ? extends Object>>() {
                public int compare(HashMap<String, ? extends Object> o1, HashMap<String, ? extends Object> o2) {
                    if (((String) o1.get("time_received")).equals((String) o2.get("time_received"))) {
                        //if sorting by time received, use the report date as a secondary sort if the time received are equal
                        return ((String) o1.get("report_date")).compareTo((String) o2.get("report_date"));
                    }
                    return ((String) o1.get("time_received")).compareTo((String) o2.get("time_received"));
                }
            });
        } else if ("category".equals(sortBy)) {
            Collections.sort(hrmdocslist, new Comparator<HashMap<String, ? extends Object>>() {
                public int compare(HashMap<String, ? extends Object> o1, HashMap<String, ? extends Object> o2) {
                    return ((String) o1.get("category")).compareTo((String) o2.get("category"));
                }
            });
        }

        if (!sortAsc) {
            Collections.reverse(hrmdocslist);
        }

        return hrmdocslist;

    }

    private static HashMap<String, HRMDocument> noFilterDuplicates(LoggedInInfo loggedInInfo, List<HRMDocumentToDemographic> hrmDocumentToDemographics) {
        HashMap<String, HRMDocument> docsToDisplay = new HashMap<String, HRMDocument>();
        for (HRMDocumentToDemographic hrmDocumentToDemographic : hrmDocumentToDemographics) {
            int id = hrmDocumentToDemographic.getHrmDocumentId() != null ? hrmDocumentToDemographic.getHrmDocumentId() : 0;
            List<HRMDocument> hrmDocuments = hrmDocumentDao.findById(id);
            for (HRMDocument hrmDocument : hrmDocuments) {
                docsToDisplay.put(Integer.toString(id), hrmDocument);
            }
        }
        return (docsToDisplay);
    }


    private static HashMap<String, HRMDocument> filterDuplicates(LoggedInInfo loggedInInfo, List<HRMDocumentToDemographic> hrmDocumentToDemographics, HashMap<String, ArrayList<Integer>> duplicateLabIds) {

        HashMap<String, HRMDocument> docsToDisplay = new HashMap<String, HRMDocument>();
        HashMap<String, HRMReport> labReports = new HashMap<String, HRMReport>();

        for (HRMDocumentToDemographic hrmDocumentToDemographic : hrmDocumentToDemographics) {
            int id = hrmDocumentToDemographic.getHrmDocumentId() != null ? hrmDocumentToDemographic.getHrmDocumentId() : 0;
            List<HRMDocument> hrmDocuments = hrmDocumentDao.findById(id);

            for (HRMDocument hrmDocument : hrmDocuments) {
                HRMReport hrmReport = HRMReportParser.parseReport(loggedInInfo, hrmDocument.getReportFile());
                if (hrmReport == null) continue;
                hrmReport.setHrmDocumentId(hrmDocument.getId());
                String duplicateKey = hrmReport.getSendingFacilityId() + ':' + hrmReport.getSendingFacilityReportNo() + ':' + hrmReport.getDeliverToUserId();

                // if no duplicate
                if (!docsToDisplay.containsKey(duplicateKey)) {
                    docsToDisplay.put(duplicateKey, hrmDocument);
                    labReports.put(duplicateKey, hrmReport);
                } else // there exists an entry like this one
                {
                    HRMReport previousHrmReport = labReports.get(duplicateKey);

                    logger.debug("Duplicate report found : previous=" + previousHrmReport.getHrmDocumentId() + ", current=" + hrmReport.getHrmDocumentId());

                    Integer duplicateIdToAdd;

                    // if the current entry is newer than the previous one then replace it, other wise just keep the previous entry
                    if (HRMResultsData.isNewer(hrmReport, previousHrmReport)) {
                        HRMDocument previousHRMDocument = docsToDisplay.get(duplicateKey);
                        duplicateIdToAdd = previousHRMDocument.getId();

                        docsToDisplay.put(duplicateKey, hrmDocument);
                        labReports.put(duplicateKey, hrmReport);
                    } else {
                        duplicateIdToAdd = hrmDocument.getId();
                    }

                    ArrayList<Integer> duplicateIds = duplicateLabIds.get(duplicateKey);
                    if (duplicateIds == null) {
                        duplicateIds = new ArrayList<Integer>();
                        duplicateLabIds.put(duplicateKey, duplicateIds);
                    }

                    duplicateIds.add(duplicateIdToAdd);
                }
            }
        }

        return (docsToDisplay);
    }

    public static ArrayList<HashMap<String, ? extends Object>> listMappings() {
        ArrayList<HashMap<String, ? extends Object>> hrmdocslist = new ArrayList<HashMap<String, ?>>();

        List<HRMSubClass> hrmSubClasses = hrmSubClassDao.listAll();

        for (HRMSubClass hrmSubClass : hrmSubClasses) {

            HashMap<String, Object> curht = new HashMap<String, Object>();
            curht.put("id", hrmSubClass.getSendingFacilityId());
            curht.put("sub_class", hrmSubClass.getSubClassName());
            curht.put("class", hrmSubClass.getClassName());
            curht.put("category", hrmSubClass.getHrmCategory());
            curht.put("mnemonic", (hrmSubClass.getSubClassMnemonic() != null ? hrmSubClass.getSubClassMnemonic() : ""));
            curht.put("description", (hrmSubClass.getSubClassDescription() != null ? hrmSubClass.getSubClassDescription() : ""));
            curht.put("mappingId", hrmSubClass.getId());

            hrmdocslist.add(curht);

        }

        return hrmdocslist;

    }

    public static HRMDocument getHRMDocumentById(LoggedInInfo loggedInInfo, Integer hrmId) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "r", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        HRMDocument hrmDocument = hrmDocumentDao.find(hrmId);
        HRMReport hrmReport = HRMReportParser.parseReport(loggedInInfo, hrmDocument.getReportFile());
        if (hrmReport == null) {
            return null;
        }
        String dispSubClass = "";
        HRMSubClass hrmSubClass;
        List<HRMDocumentSubClass> subClassList = new ArrayList<>(hrmDocument.getAccompanyingSubClasses());
        if (hrmReport.getFirstReportClass().equalsIgnoreCase("Diagnostic Imaging Report") || hrmReport.getFirstReportClass().equalsIgnoreCase("Cardio Respiratory Report")) {
            //Get first sub class to display on eChart
            if (subClassList != null && subClassList.size() > 0) {
                HRMDocumentSubClass firstSubClass = subClassList.get(0);
                hrmSubClass = hrmSubClassDao.findApplicableSubClassMapping(hrmReport.getFirstReportClass(), firstSubClass.getSubClass(), firstSubClass.getSubClassMnemonic(), hrmReport.getSendingFacilityId());
                dispSubClass = hrmSubClass != null ? hrmSubClass.getSubClassDescription() : "";
            }

            if (StringUtils.isNullOrEmpty(dispSubClass) && hrmReport.getAccompanyingSubclassList().size() > 0) {
                // if sub class doesn't exist, display the accompanying subclass
                dispSubClass = hrmReport.getFirstAccompanyingSubClass();
            }
        } else {
            //Medical Records Report
            String[] reportSubClass = hrmReport.getFirstReportSubClass() != null ? hrmReport.getFirstReportSubClass().split("\\^") : null;
            if (reportSubClass != null && reportSubClass.length > 1) {
                dispSubClass = reportSubClass[1];
            }
        }

        String hrmDocumentDisplayName = getHRMDocumentDisplayName(hrmDocument.getDescription(), dispSubClass, hrmDocument.getReportType(), hrmDocument.getReportStatus());
        hrmDocument.setDisplayName(hrmDocumentDisplayName);
        return hrmDocument;
    }

    private static String getHRMDocumentDisplayName(String description, String subClass, String reportType, String reportStatus) {
        String displayHRMName = "";
        if (!StringUtils.isNullOrEmpty(description)) {
            displayHRMName = description;
        } else if (!StringUtils.isNullOrEmpty(subClass)) {
            displayHRMName = subClass;
        } else {
            displayHRMName = reportType;
        }

        if (!StringUtils.isNullOrEmpty(reportStatus) && reportStatus.equalsIgnoreCase("C")) {
            displayHRMName = "(Cancelled) " + displayHRMName;
        }
        return displayHRMName;
    }

    public static Path renderHRM(LoggedInInfo loggedInInfo, Integer hrmId) throws PDFGenerationException {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "r", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        Path path = null;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            HRMPDFCreator hrmpdfCreator = new HRMPDFCreator(outputStream, hrmId, loggedInInfo);
            hrmpdfCreator.printPdf();
            path = nioFileManager.saveTempFile("temporaryPDF" + new Date().getTime(), outputStream);
        } catch (IOException e) {
            throw new PDFGenerationException("Error Details: HRM [" + getHRMDocumentDisplayName(hrmId) + "] could not be converted into a PDF", e);
        }
        return path;
    }

    private static String getHRMDocumentDisplayName(Integer hrmId) {
        HRMDocument hrmDocument = hrmDocumentDao.find(hrmId);
        return getHRMDocumentDisplayName(hrmDocument.getDescription(), "", hrmDocument.getReportType(), hrmDocument.getReportStatus());
    }
}
