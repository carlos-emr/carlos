//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */


package ca.openosp.openo.lab.ca.on;

import java.util.*;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.hospitalReportManager.HRMReport;
import ca.openosp.openo.hospitalReportManager.HRMReportParser;
import ca.openosp.openo.hospitalReportManager.dao.HRMDocumentDao;
import ca.openosp.openo.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import ca.openosp.openo.hospitalReportManager.dao.HRMDocumentToProviderDao;
import ca.openosp.openo.hospitalReportManager.model.HRMDocument;
import ca.openosp.openo.hospitalReportManager.model.HRMDocumentToDemographic;
import ca.openosp.openo.hospitalReportManager.model.HRMDocumentToProvider;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

public class HRMResultsData {

    private static Logger logger = MiscUtils.getLogger();
    private HRMDocumentDao hrmDocumentDao = (HRMDocumentDao) SpringUtils.getBean(HRMDocumentDao.class);
    private HRMDocumentToProviderDao hrmDocumentToProviderDao = (HRMDocumentToProviderDao) SpringUtils.getBean(HRMDocumentToProviderDao.class);
    private HRMDocumentToDemographicDao hrmDocumentToDemographicDao = (HRMDocumentToDemographicDao) SpringUtils.getBean(HRMDocumentToDemographicDao.class);
    private DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);

    public HRMResultsData() {
    }

    public Collection<LabResultData> populateHRMdocumentsResultsData(LoggedInInfo loggedInInfo, String providerNo, String status, Date newestDate, Date oldestDate,
                                                                     boolean isPaged, Integer page, Integer pageSize) {
        return populateHRMdocumentsResultsData(loggedInInfo, providerNo, "", "", "", null, status, newestDate, oldestDate, isPaged, page, pageSize);
    }


    public Collection<LabResultData> populateHRMdocumentsResultsData(LoggedInInfo loggedInInfo, String providerNo, String firstName, String lastName, String hin, String demographicNumber, String status, Date newestDate, Date oldestDate,
                                                                     boolean isPaged, Integer page, Integer pageSize) {
        String firstNameSearch = firstName != null ? firstName.toUpperCase() : "";
        String lastNameSearch = lastName != null ? lastName.toUpperCase() : "";
        String hinSearch = hin != null ? hin.toUpperCase() : "";

        if (providerNo == null || "".equals(providerNo)) {
            providerNo = "%";
        } else if (providerNo.equalsIgnoreCase("0")) {
            providerNo = "-1";
        }

        int viewed = 1;
        int signedOff = 0;
        if (status == null || status.equalsIgnoreCase("N")) {
            viewed = 2;
        } else if (status != null && (status.equalsIgnoreCase("A") || status.equalsIgnoreCase("F"))) {
            signedOff = 1;
        }

        if (status != null && status.equalsIgnoreCase("")) {
            viewed = 2;
            signedOff = 2;
        }

        List<Integer> demographicNumbers = new ArrayList<>();
		boolean patientSearch = false;
        if (demographicNumber == null && (!firstNameSearch.isEmpty() || !lastNameSearch.isEmpty() || !hinSearch.isEmpty())) {
			patientSearch = true;
            List<Demographic> matchedDemographics = demographicManager.searchDemographicsByAttributes(loggedInInfo, hin, firstName, lastName,
                    null, null, null, null, null, null, null, 0, 100);
            for (Demographic matchedDemographic : matchedDemographics) {
                if (matchedDemographic.isActive()) {
                    demographicNumbers.add(matchedDemographic.getDemographicNo());
                }
            }
        } else if (demographicNumber != null) {
            demographicNumbers.add(Integer.valueOf(demographicNumber));
        }

		List<HRMDocumentToProvider> hrmDocResultsProvider = hrmDocumentToProviderDao.findByProviderNoLimit(providerNo, demographicNumbers, patientSearch, newestDate, oldestDate, viewed, signedOff,
                isPaged, page, pageSize);

        // the key = SendingFacility+':'+ReportNumber+':'+DeliverToUserID as per HRM spec can be used to signify duplicate report
        HashMap<String, LabResultData> labResults = new HashMap<String, LabResultData>();
        HashMap<String, HRMReport> labReports = new HashMap<String, HRMReport>();

        for (HRMDocumentToProvider hrmDocResult : hrmDocResultsProvider) {
            Integer id = hrmDocResult.getHrmDocumentId();
            LabResultData lbData = new LabResultData(LabResultData.HRM);

            List<HRMDocument> hrmDocument = hrmDocumentDao.findById(id);

            lbData.dateTime = hrmDocument.get(0).getTimeReceived().toString();
            lbData.acknowledgedStatus = "U";
            lbData.reportStatus = hrmDocument.get(0).getReportStatus();
            lbData.segmentID = hrmDocument.get(0).getId().toString();
            lbData.setDateObj(hrmDocument.get(0).getReportDate());
            lbData.patientName = "Not, Assigned";

            // check if patient is matched
            List<HRMDocumentToDemographic> hrmDocResultsDemographic = hrmDocumentToDemographicDao.findByHrmDocumentId(hrmDocument.get(0).getId());
            HRMReport hrmReport = HRMReportParser.parseReport(loggedInInfo, hrmDocument.get(0).getReportFile());
            if (hrmReport == null) continue;

            hrmReport.setHrmDocumentId(id);

            if (hrmDocResultsDemographic.size() > 0) {
                Demographic demographic = demographicManager.getDemographic(loggedInInfo, hrmDocResultsDemographic.get(0).getDemographicNo());
                if (demographic != null) {
                    lbData.patientName = demographic.getLastName() + "," + demographic.getFirstName();
                    lbData.sex = demographic.getSex();
                    lbData.healthNumber = demographic.getHin();
                    lbData.isMatchedToPatient = true;
                }
            } else {
                lbData.sex = hrmReport.getGender();
                lbData.healthNumber = hrmReport.getHCN();
                lbData.patientName = hrmReport.getLegalName();

            }

            lbData.reportStatus = hrmReport.getResultStatus();
            lbData.priority = "----";
            lbData.requestingClient = "";
            lbData.discipline = "HRM";
            lbData.resultStatus = hrmReport.getResultStatus();

            String duplicateKey = hrmReport.getSendingFacilityId() + ':' + hrmReport.getSendingFacilityReportNo() + ':' + hrmReport.getDeliverToUserId();
            String[] patientName = lbData.patientName.split(",");
            if (lbData.healthNumber.contains(hinSearch) || patientName[0].contains(lastNameSearch) || patientName[1].contains(firstNameSearch)) {
                // if no duplicate
                if (!labResults.containsKey(duplicateKey)) {
                    labResults.put(duplicateKey, lbData);
                    labReports.put(duplicateKey, hrmReport);
                } else // there exists an entry like this one
                {
                    HRMReport previousHrmReport = labReports.get(duplicateKey);

                    logger.debug("Duplicate report found : previous=" + previousHrmReport.getHrmDocumentId() + ", current=" + hrmReport.getHrmDocumentId());

                    // if the current entry is newer than the previous one then replace it, other wise just keep the previous entry
                    if (isNewer(hrmReport, previousHrmReport)) {
                        LabResultData olderLabData = labResults.get(duplicateKey);

                        lbData.getDuplicateLabIds().addAll(olderLabData.getDuplicateLabIds());
                        lbData.getDuplicateLabIds().add(previousHrmReport.getHrmDocumentId());

                        labResults.put(duplicateKey, lbData);
                        labReports.put(duplicateKey, hrmReport);
                    } else {
                        LabResultData newerLabData = labResults.get(duplicateKey);
                        newerLabData.getDuplicateLabIds().add(hrmReport.getHrmDocumentId());
                    }
                }
            }

        }

        if (logger.isDebugEnabled()) {
            for (LabResultData temp : labResults.values()) {
                logger.debug("------------------");
                logger.debug(ReflectionToStringBuilder.toString(temp));
            }
        }

        return labResults.values();
    }

    public static LabResultData populateHrmResultWithDemographicFromReport(LoggedInInfo loggedInInfo, LabResultData resultData) {
        HRMReport report = HRMReportParser.parseReport(loggedInInfo, Integer.parseInt(resultData.segmentID));
        if (report != null) {
            if (report.getLegalName() != null && !report.getLegalName().replaceAll(",", "").trim().isEmpty()) {
                resultData.patientName = report.getLegalName();
            }
        }

        return resultData;
    }

    public static String getMessageDate(String messageUniqueId) {
        String[] parts = messageUniqueId.split("\\^");
        if (parts.length > 5) {
            return parts[5];
        }
        return null;
    }

    /**
     * @return true if the currentEntry is deemed to be newer than the previousEntry
     * <Hospital Report Manager Process Date>^<Accession Number>^<Sending Facility>^<Report Class>^<Report Number>^<Message Date>^<Environment Mode>^<Site Instance>^<Report Status>^<Visit Number>
     */
    public static boolean isNewer(HRMReport currentEntry, HRMReport previousEntry) {
        // try to parse messageUniqueId for date portion to compare, no gurantees it exists or is well formed.
        try {
            String currentUid = currentEntry.getMessageUniqueId();
            String previousUid = previousEntry.getMessageUniqueId();
            String currentDatePart = getMessageDate(currentUid);
            String previousDatePart = getMessageDate(previousUid);
            long currentDateNum = Long.parseLong(currentDatePart);
            long previousDateNum = Long.parseLong(previousDatePart);

            if (currentDateNum > previousDateNum) return (true);
            if (currentDateNum < previousDateNum) return (false);
            // if they are equal, then we can not determine it based on this field
        } catch (Exception e) {
            // can ignore, messageUniqueId's are note guranteed to exist, nor their format.
            logger.debug("Error attempting to use messageUniqueId, currentUid=" + currentEntry.getMessageUniqueId() + ", prevUId=" + previousEntry.getMessageUniqueId(), e);
        }

        logger.debug("could not determine newer based on messageUniqueId");

        // try to pick the one that's not canceled.
        if (!"C".equals(currentEntry.getResultStatus()) && "C".equals(previousEntry.getResultStatus())) return (true);
        if ("C".equals(currentEntry.getResultStatus()) && !"C".equals(previousEntry.getResultStatus())) return (false);
        // if both canceled or neither canceled then we can't figure it out from this field.

        // at this point I have to make a random guess, we know it's a duplicate but we can't tell which is newer.
        return (currentEntry.getHrmDocumentId() > previousEntry.getHrmDocumentId());
    }

}
