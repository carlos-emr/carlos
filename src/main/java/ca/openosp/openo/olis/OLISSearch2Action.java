//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */
package ca.openosp.openo.olis;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.dao.OscarLogDao;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.OscarLog;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.olis1.Driver;
import ca.openosp.openo.olis1.parameters.OBR16;
import ca.openosp.openo.olis1.parameters.OBR22;
import ca.openosp.openo.olis1.parameters.OBR25;
import ca.openosp.openo.olis1.parameters.OBR28;
import ca.openosp.openo.olis1.parameters.OBR4;
import ca.openosp.openo.olis1.parameters.OBR7;
import ca.openosp.openo.olis1.parameters.OBX3;
import ca.openosp.openo.olis1.parameters.ORC21;
import ca.openosp.openo.olis1.parameters.PID3;
import ca.openosp.openo.olis1.parameters.PID51;
import ca.openosp.openo.olis1.parameters.PID52;
import ca.openosp.openo.olis1.parameters.PID7;
import ca.openosp.openo.olis1.parameters.PID8;
import ca.openosp.openo.olis1.parameters.PV117;
import ca.openosp.openo.olis1.parameters.PV17;
import ca.openosp.openo.olis1.parameters.QRD7;
import ca.openosp.openo.olis1.parameters.ZBE4;
import ca.openosp.openo.olis1.parameters.ZBE6;
import ca.openosp.openo.olis1.parameters.ZBR3;
import ca.openosp.openo.olis1.parameters.ZBR4;
import ca.openosp.openo.olis1.parameters.ZBR6;
import ca.openosp.openo.olis1.parameters.ZBR8;
import ca.openosp.openo.olis1.parameters.ZBX1;
import ca.openosp.openo.olis1.parameters.ZPD1;
import ca.openosp.openo.olis1.parameters.ZPD3;
import ca.openosp.openo.olis1.parameters.ZRP1;
import ca.openosp.openo.olis1.queries.Query;
import ca.openosp.openo.olis1.queries.QueryType;
import ca.openosp.openo.olis1.queries.Z01Query;
import ca.openosp.openo.olis1.queries.Z02Query;
import ca.openosp.openo.olis1.queries.Z04Query;
import ca.openosp.openo.olis1.queries.Z05Query;
import ca.openosp.openo.olis1.queries.Z06Query;
import ca.openosp.openo.olis1.queries.Z07Query;
import ca.openosp.openo.olis1.queries.Z08Query;
import ca.openosp.openo.olis1.queries.Z50Query;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts 2 action for OLIS (Ontario Laboratory Information System) search queries.
 * <p>
 * This action handles the construction and submission of various OLIS HL7 query types
 * to retrieve laboratory results from the Ontario provincial laboratory system.
 * It supports multiple query types with different search criteria and parameters:
 * <ul>
 * <li>Z01 - Patient-centric lab result query (most common)</li>
 * <li>Z02 - Retrieve specific test results by placer group number</li>
 * <li>Z04 - Provider-centric query for all patients</li>
 * <li>Z05 - Query by destination laboratory</li>
 * <li>Z06 - Query by ordering facility</li>
 * <li>Z07 - Query by time period only</li>
 * <li>Z08 - Query with minimal criteria</li>
 * <li>Z50 - Patient demographic search (find patients)</li>
 * </ul>
 * <p>
 * The action processes request parameters to build HL7 query objects with appropriate
 * PID, OBR, OBX, PV1, ZRP, ZBR, and other HL7 segments based on the query type.
 * <p>
 * Key features:
 * <ul>
 * <li>Query UUID tracking for result correlation</li>
 * <li>Consent override logging with OscarLog audit trail</li>
 * <li>Patient consent handling (blocked information, consent block all)</li>
 * <li>Date range parsing and normalization</li>
 * <li>Provider credential mapping from UserProperty</li>
 * <li>Demographic data integration from patient records</li>
 * <li>Support for quantity-limited queries</li>
 * <li>Multiple practitioner role filtering (ordering, attending, admitting, copied-to)</li>
 * </ul>
 * <p>
 * All properties are automatically populated by Struts 2 property injection from
 * request parameters before the execute() method is called.
 *
 * @since 2006-04-20
 */
public class OLISSearch2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    private ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);

    public static HashMap<String, Query> searchQueryMap = new HashMap<String, Query>();

    /**
     * Main execution method for OLIS search query construction and submission.
     * <p>
     * This method handles two main workflows:
     * <ul>
     * <li>Redo existing query - Retrieves a previously constructed query by UUID and resubmits it,
     *     optionally with consent override if forced</li>
     * <li>New query construction - Builds a new HL7 query object based on queryType parameter
     *     (Z01, Z02, Z04, Z05, Z06, Z07, Z08, Z50) with all relevant search parameters</li>
     * </ul>
     * <p>
     * For new queries, the method:
     * <ul>
     * <li>Parses and validates all request parameters specific to the query type</li>
     * <li>Constructs appropriate HL7 segments (PID, OBR, OBX, PV1, ZRP, ZBR, etc.)</li>
     * <li>Integrates demographic and provider data from the database</li>
     * <li>Generates a unique UUID for query tracking</li>
     * <li>Stores the query in searchQueryMap for potential re-execution</li>
     * <li>Logs consent overrides if applicable</li>
     * <li>Submits the query via Driver.submitOLISQuery()</li>
     * </ul>
     * <p>
     * Expected request parameters vary by query type but commonly include:
     * <ul>
     * <li>queryType - String OLIS query type (Z01, Z02, Z04, Z05, Z06, Z07, Z08, Z50)</li>
     * <li>redo - String "true" to resubmit existing query by UUID</li>
     * <li>uuid - String query UUID for redo operations</li>
     * <li>force - String "true" to override consent blocks</li>
     * <li>demographic - String demographic number for patient queries</li>
     * <li>requestingHic - String provider number for requesting HIC</li>
     * <li>startTimePeriod / endTimePeriod - String date range (yyyy-MM-dd)</li>
     * <li>quantityLimitedQuery - String "true" to enable quantity limit</li>
     * <li>quantityLimit - String number of results to limit</li>
     * <li>blockedInformationConsent - String consent code (Y/N/Z for override)</li>
     * <li>testRequestCode[] - String array of test request codes (OBR.4)</li>
     * <li>testResultCode[] - String array of test result codes (OBX.3)</li>
     * <li>orderingPractitioner - String provider number for ordering practitioner</li>
     * <li>copiedToPractitioner - String provider number for copied-to practitioner</li>
     * <li>attendingPractitioner - String provider number for attending practitioner</li>
     * <li>admittingPractitioner - String provider number for admitting practitioner</li>
     * <li>performingLaboratory - String laboratory identifier</li>
     * <li>reportingLaboratory - String laboratory identifier</li>
     * <li>excludePerformingLaboratory - String laboratory identifier to exclude</li>
     * <li>excludeReportingLaboratory - String laboratory identifier to exclude</li>
     * </ul>
     * <p>
     * Z50-specific parameters:
     * <ul>
     * <li>z50firstName - String patient first name</li>
     * <li>z50lastName - String patient last name</li>
     * <li>z50sex - String patient sex (M/F)</li>
     * <li>z50dateOfBirth - String patient date of birth (yyyy-MM-dd)</li>
     * </ul>
     *
     * @return String "results" to forward to results page
     */
    public String execute() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String queryType = request.getParameter("queryType");
        boolean redo = "true".equals(request.getParameter("redo"));
        if (redo) {
            String uuid = request.getParameter("uuid");
            request.setAttribute("searchUuid", uuid);
            boolean force = "true".equals(request.getParameter("force"));
            Query q = (Query) searchQueryMap.get(uuid).clone();
            if (force) {
                q.setConsentToViewBlockedInformation(new ZPD1("Z"));

                String blockedInfoIndividual = request.getParameter("blockedInformationIndividual");
                // Log the consent override
                OscarLogDao logDao = (OscarLogDao) SpringUtils.getBean(OscarLogDao.class);
                OscarLog logItem = new OscarLog();
                logItem.setAction("OLIS");
                logItem.setContent("consent override");
                //logItem.setContentId("demographicNo=" + q.getDemographicNo() + ",givenby=" + blockedInfoIndividual);
                logItem.setContentId(uuid);
                logItem.setProviderNo(loggedInInfo.getLoggedInProviderNo());

                StringBuilder data = new StringBuilder();
                data.append("Initiating Provider: " + providerDao.getProvider(loggedInInfo.getLoggedInProviderNo()).getFormattedName() + "\n");
                data.append("Requesting HIC: " + providerDao.getProviderByPractitionerNo(q.getRequestingHICProviderNo()) + "\n");
                data.append("Authorized by:" + blockedInfoIndividual + "\n");

                logItem.setData(data.toString());

                logItem.setIp(request.getRemoteAddr());


                if (q.getQueryType() == QueryType.Z01) {
                    String demographicNo = ((Z01Query) q).getDemographicNo();
                    if (!StringUtils.isEmpty(demographicNo)) {
                        logItem.setDemographicId(Integer.parseInt(demographicNo));
                    }
                }

                logDao.persist(logItem);

            }
            Driver.submitOLISQuery(loggedInInfo, request, q);

        } else if (queryType != null) {
            UserPropertyDAO userPropertyDAO = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
            Query query = null;

            String[] dateFormat = new String[]{
                    "yyyy-MM-dd"
            };

            if (queryType.equalsIgnoreCase("Z01")) {
                query = new Z01Query();
                String startTimePeriod = request.getParameter("startTimePeriod");
                String endTimePeriod = request.getParameter("endTimePeriod");


                try {
                    if (startTimePeriod != null && startTimePeriod.trim().length() > 0) {
                        Date startTime = DateUtils.parseDate(startTimePeriod, dateFormat);
                        if (endTimePeriod != null && endTimePeriod.trim().length() > 0) {
                            Date endTime = changeToEndOfDay(DateUtils.parseDate(endTimePeriod, dateFormat));

                            List<Date> dateList = new LinkedList<Date>();
                            dateList.add(startTime);
                            dateList.add(endTime);

                            OBR22 obr22 = new OBR22();
                            obr22.setValue(dateList);

                            ((Z01Query) query).setStartEndTimestamp(obr22);
                        } else {
                            OBR22 obr22 = new OBR22();
                            obr22.setValue(startTime);

                            ((Z01Query) query).setStartEndTimestamp(obr22);
                        }
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't parse date given for OLIS query", e);
                }


                String observationStartTimePeriod = request.getParameter("observationStartTimePeriod");
                String observationEndTimePeriod = request.getParameter("observationEndTimePeriod");

                try {
                    if (observationStartTimePeriod != null && observationStartTimePeriod.trim().length() > 0) {
                        Date observationStartTime = DateUtils.parseDate(observationStartTimePeriod, dateFormat);
                        if (observationEndTimePeriod != null && observationEndTimePeriod.trim().length() > 0) {
                            Date observationEndTime = changeToEndOfDay(DateUtils.parseDate(observationEndTimePeriod, dateFormat));

                            List<Date> dateList = new LinkedList<Date>();
                            dateList.add(observationStartTime);
                            dateList.add(observationEndTime);

                            OBR7 obr7 = new OBR7();
                            obr7.setValue(dateList);

                            ((Z01Query) query).setEarliestLatestObservationDateTime(obr7);
                        } else {
                            OBR7 obr7 = new OBR7();
                            obr7.setValue(observationStartTime);

                            ((Z01Query) query).setEarliestLatestObservationDateTime(obr7);
                        }
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't parse date given for OLIS query", e);
                }


                String quantityLimitedQuery = request.getParameter("quantityLimitedQuery");
                String quantityLimit = request.getParameter("quantityLimit");

                try {
                    if (quantityLimitedQuery != null && quantityLimitedQuery.trim().length() > 0) {
                        // Checked
                        ((Z01Query) query).setQuantityLimitedRequest(new QRD7(Integer.parseInt(quantityLimit)));
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't parse the number given for quantity limit in OLIS query", e);
                }


                String blockedInformationConsent = request.getParameter("blockedInformationConsent");

                if (blockedInformationConsent != null && blockedInformationConsent.trim().length() > 0) {
                    ((Z01Query) query).setConsentToViewBlockedInformation(new ZPD1(blockedInformationConsent));
                }


                String consentBlockAllIndicator = request.getParameter("consentBlockAllIndicator");

                if (consentBlockAllIndicator != null && consentBlockAllIndicator.trim().length() > 0) {
                    ((Z01Query) query).setPatientConsentBlockAllIndicator(new ZPD3("Y"));
                }


                String specimenCollector = request.getParameter("specimenCollector");

                if (specimenCollector != null && specimenCollector.trim().length() > 0) {
                    ((Z01Query) query).setSpecimenCollector(new ZBR3(specimenCollector, "ISO"));
                }


                String performingLaboratory = request.getParameter("performingLaboratory");

                if (performingLaboratory != null && performingLaboratory.trim().length() > 0) {
                    ((Z01Query) query).setPerformingLaboratory(new ZBR6(performingLaboratory, "ISO"));
                }


                String excludePerformingLaboratory = request.getParameter("excludePerformingLaboratory");

                if (excludePerformingLaboratory != null && excludePerformingLaboratory.trim().length() > 0) {
                    ((Z01Query) query).setExcludePerformingLaboratory(new ZBE6(excludePerformingLaboratory, "ISO"));
                }


                String reportingLaboratory = request.getParameter("reportingLaboratory");

                if (reportingLaboratory != null && reportingLaboratory.trim().length() > 0) {
                    ((Z01Query) query).setReportingLaboratory(new ZBR4(reportingLaboratory, "ISO"));
                }


                String excludeReportingLaboratory = request.getParameter("excludeReportingLaboratory");

                if (excludeReportingLaboratory != null && excludeReportingLaboratory.trim().length() > 0) {
                    ((Z01Query) query).setExcludeReportingLaboratory(new ZBE4(excludeReportingLaboratory, "ISO"));
                }


                // Patient Identifier (PID.3 -- pull data from db and add to query)
                String demographicNo = request.getParameter("demographic");
                query.setDemographicNo(demographicNo);
                try {
                    if (demographicNo != null && demographicNo.trim().length() > 0) {
                        Demographic demo = demographicDao.getDemographic(demographicNo);

                        PID3 pid3 = new PID3(demo.getHin(), null, null, "JHN", demo.getHcType(), "HL70347", demo.getSex(), null);
                        pid3.setValue(7, DateUtils.parseDate(demo.getYearOfBirth() + "-" + demo.getMonthOfBirth() + "-" + demo.getDateOfBirth(), dateFormat));

                        ((Z01Query) query).setPatientIdentifier(pid3);
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't add requested patient data to OLIS query", e);
                }


                // Requesting HIC (ZRP.1 -- pull data from db and add to query)
                String requestingHicProviderNo = request.getParameter("requestingHic");

                try {
                    if (requestingHicProviderNo != null && requestingHicProviderNo.trim().length() > 0) {
                        Provider provider = providerDao.getProvider(requestingHicProviderNo);

                        ZRP1 zrp1 = new ZRP1(provider.getPractitionerNo(), userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_OLIS_IDTYPE), "ON", "HL70347",
                                userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_LAST_NAME),
                                userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_FIRST_NAME),
                                userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_SECOND_NAME));

                        ((Z01Query) query).setRequestingHic(zrp1);
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't add requested requesting HIC data to OLIS query", e);
                }


                // OBR.16
                String orderingPractitionerProviderNo = request.getParameter("orderingPractitioner");

                try {
                    if (orderingPractitionerProviderNo != null && orderingPractitionerProviderNo.trim().length() > 0) {
                        OBR16 obr16 = new OBR16(orderingPractitionerProviderNo, "MDL", "ON", "HL70347");

                        ((Z01Query) query).setOrderingPractitioner(obr16);
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't add requested ordering practitioner data to OLIS query", e);
                }


                String copiedToPractitionerProviderNo = request.getParameter("copiedToPractitioner");

                try {
                    if (copiedToPractitionerProviderNo != null && copiedToPractitionerProviderNo.trim().length() > 0) {
                        OBR28 obr28 = new OBR28(copiedToPractitionerProviderNo, "MDL", "ON", "HL70347");

                        ((Z01Query) query).setCopiedToPractitioner(obr28);
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't add requested copied to practitioner data to OLIS query", e);
                }


                String attendingPractitionerProviderNo = request.getParameter("attendingPractitioner");

                try {
                    if (attendingPractitionerProviderNo != null && attendingPractitionerProviderNo.trim().length() > 0) {
                        PV17 pv17 = new PV17(attendingPractitionerProviderNo, "MDL", "ON", "HL70347");

                        ((Z01Query) query).setAttendingPractitioner(pv17);
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't add requested attending practitioner data to OLIS query", e);
                }


                String admittingPractitionerProviderNo = request.getParameter("admittingPractitioner");

                try {
                    if (admittingPractitionerProviderNo != null && admittingPractitionerProviderNo.trim().length() > 0) {
                        PV117 pv117 = new PV117(admittingPractitionerProviderNo, "MDL", "ON", "HL70347");

                        ((Z01Query) query).setAdmittingPractitioner(pv117);
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't add requested admitting practitioner data to OLIS query", e);
                }


                // TODO: Add placer group number

                String[] testRequestStatusList = request.getParameterValues("testRequestStatus");

                if (testRequestStatusList != null) {
                    for (String testRequestStatus : testRequestStatusList) {
                        ((Z01Query) query).addToTestRequestStatusList(new OBR25(testRequestStatus));
                    }
                }

                String[] testResultCodeList = request.getParameterValues("testResultCode");

                if (testResultCodeList != null) {
                    for (String testResultCode : testResultCodeList) {
                        ((Z01Query) query).addToTestResultCodeList(new OBX3(testResultCode, "HL79902"));
                    }
                }


                String[] testRequestCodeList = request.getParameterValues("testRequestCode");

                if (testRequestCodeList != null) {
                    for (String testRequestCode : testRequestCodeList) {
                        ((Z01Query) query).addToTestRequestCodeList(new OBR4(testRequestCode, "HL79901"));
                    }
                }

                String blockedInfoConsent = request.getParameter("blockedInformationConsent");
                String blockedInfoIndividual = request.getParameter("blockedInformationIndividual");

                if (blockedInfoConsent != null && blockedInfoConsent.equalsIgnoreCase("Z")) {
                    // Log the consent override
                    OscarLogDao logDao = (OscarLogDao) SpringUtils.getBean(OscarLogDao.class);
                    OscarLog logItem = new OscarLog();
                    logItem.setAction("OLIS search");
                    logItem.setContent("consent override");
                    logItem.setContentId("demographicNo=" + demographicNo + ",givenby=" + blockedInfoIndividual);
                    if (loggedInInfo.getLoggedInProvider() != null)
                        logItem.setProviderNo(loggedInInfo.getLoggedInProviderNo());
                    else
                        logItem.setProviderNo("-1");

                    logItem.setIp(request.getRemoteAddr());

                    logDao.persist(logItem);

                }

            } else if (queryType.equalsIgnoreCase("Z02")) {
                query = new Z02Query();

                String retrieveAllResults = request.getParameter("retrieveAllResults");

                try {
                    if (retrieveAllResults != null && retrieveAllResults.trim().length() > 0) {
                        // Checked
                        ((Z02Query) query).setRetrieveAllTestResults(new ZBX1("*"));
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't set retrieve all results option on OLIS query", e);
                }


                String blockedInformationConsent = request.getParameter("blockedInformationConsent");

                if (blockedInformationConsent != null && blockedInformationConsent.trim().length() > 0) {
                    ((Z02Query) query).setConsentToViewBlockedInformation(new ZPD1(blockedInformationConsent));
                }


                String consentBlockAllIndicator = request.getParameter("consentBlockAllIndicator");

                if (consentBlockAllIndicator != null && consentBlockAllIndicator.trim().length() > 0) {
                    ((Z02Query) query).setPatientConsentBlockAllIndicator(new ZPD3("Y"));
                }


                // Requesting HIC (ZRP.1 -- pull data from db and add to query)
                String requestingHicProviderNo = request.getParameter("requestingHic");

                try {
                    if (requestingHicProviderNo != null && requestingHicProviderNo.trim().length() > 0) {
                        Provider provider = providerDao.getProvider(requestingHicProviderNo);

                        ZRP1 zrp1 = new ZRP1(provider.getPractitionerNo(), "MDL", "ON", "HL70347",
                                userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_LAST_NAME),
                                userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_FIRST_NAME),
                                userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_SECOND_NAME));

                        ((Z02Query) query).setRequestingHic(zrp1);
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't add requested requesting HIC data to OLIS query", e);
                }


                // Patient Identifier (PID.3 -- pull data from db and add to query)
                String demographicNo = request.getParameter("demographic");
                query.setDemographicNo(demographicNo);

                try {
                    if (demographicNo != null && demographicNo.trim().length() > 0) {
                        Demographic demo = demographicDao.getDemographic(demographicNo);

                        PID3 pid3 = new PID3(demo.getHin(), null, null, "JHN", demo.getHcType(), "HL70347", demo.getSex(), null);
                        pid3.setValue(7, DateUtils.parseDate(demo.getYearOfBirth() + "-" + demo.getMonthOfBirth() + "-" + demo.getDateOfBirth(), dateFormat));

                        ((Z02Query) query).setPatientIdentifier(pid3);
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't add requested patient data to OLIS query", e);
                }


                // TODO: Add placer group number


                String blockedInfoConsent = request.getParameter("blockedInformationConsent");
                String blockedInfoIndividual = request.getParameter("blockedInformationIndividual");

                if (blockedInfoConsent != null && blockedInfoConsent.equalsIgnoreCase("Z")) {
                    // Log the consent override
                    OscarLogDao logDao = (OscarLogDao) SpringUtils.getBean(OscarLogDao.class);
                    OscarLog logItem = new OscarLog();
                    logItem.setAction("OLIS search");
                    logItem.setContent("consent override");
                    logItem.setContentId("demographicNo=" + demographicNo + ",givenby=" + blockedInfoIndividual);
                    if (loggedInInfo.getLoggedInProvider() != null)
                        logItem.setProviderNo(loggedInInfo.getLoggedInProviderNo());
                    else
                        logItem.setProviderNo("-1");

                    logItem.setIp(request.getRemoteAddr());

                    logDao.persist(logItem);
                }


            } else if (queryType.equalsIgnoreCase("Z04")) {
                query = new Z04Query();

                String startTimePeriod = request.getParameter("startTimePeriod");
                String endTimePeriod = request.getParameter("endTimePeriod");

                try {
                    if (startTimePeriod != null && startTimePeriod.trim().length() > 0) {
                        Date startTime = DateUtils.parseDate(startTimePeriod, dateFormat);
                        if (endTimePeriod != null && endTimePeriod.trim().length() > 0) {
                            Date endTime = changeToEndOfDay(DateUtils.parseDate(endTimePeriod, dateFormat));

                            List<Date> dateList = new LinkedList<Date>();
                            dateList.add(startTime);
                            dateList.add(endTime);

                            OBR22 obr22 = new OBR22();
                            obr22.setValue(dateList);

                            ((Z04Query) query).setStartEndTimestamp(obr22);
                        } else {
                            OBR22 obr22 = new OBR22();
                            obr22.setValue(startTime);

                            ((Z04Query) query).setStartEndTimestamp(obr22);
                        }
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't parse date given for OLIS query", e);
                }


                String quantityLimitedQuery = request.getParameter("quantityLimitedQuery");
                String quantityLimit = request.getParameter("quantityLimit");

                try {
                    if (quantityLimitedQuery != null && quantityLimitedQuery.trim().length() > 0) {
                        // Checked
                        ((Z04Query) query).setQuantityLimitedRequest(new QRD7(Integer.parseInt(quantityLimit)));
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't parse the number given for quantity limit in OLIS query", e);
                }


                // Requesting HIC (ZRP.1 -- pull data from db and add to query)
                String requestingHicProviderNo = request.getParameter("requestingHic");

                try {
                    if (requestingHicProviderNo != null && requestingHicProviderNo.trim().length() > 0) {
                        Provider provider = providerDao.getProvider(requestingHicProviderNo);

                        ZRP1 zrp1 = new ZRP1(provider.getPractitionerNo(), "MDL", "ON", "HL70347",
                                userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_LAST_NAME),
                                userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_FIRST_NAME),
                                userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_SECOND_NAME));

                        ((Z04Query) query).setRequestingHic(zrp1);
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't add requested requesting HIC data to OLIS query", e);
                }


                String[] testResultCodeList = request.getParameterValues("testResultCode");

                if (testResultCodeList != null) {
                    for (String testResultCode : testResultCodeList) {
                        ((Z04Query) query).addToTestResultCodeList(new OBX3(testResultCode, "HL79902"));
                    }
                }


                String[] testRequestCodeList = request.getParameterValues("testRequestCode");

                if (testRequestCodeList != null) {
                    for (String testRequestCode : testRequestCodeList) {
                        ((Z04Query) query).addToTestRequestCodeList(new OBR4(testRequestCode, "HL79901"));
                    }
                }


            } else if (queryType.equalsIgnoreCase("Z05")) {
                query = new Z05Query();


                String startTimePeriod = request.getParameter("startTimePeriod");
                String endTimePeriod = request.getParameter("endTimePeriod");

                try {
                    if (startTimePeriod != null && startTimePeriod.trim().length() > 0) {
                        Date startTime = DateUtils.parseDate(startTimePeriod, dateFormat);
                        if (endTimePeriod != null && endTimePeriod.trim().length() > 0) {
                            Date endTime = changeToEndOfDay(DateUtils.parseDate(endTimePeriod, dateFormat));

                            List<Date> dateList = new LinkedList<Date>();
                            dateList.add(startTime);
                            dateList.add(endTime);

                            OBR22 obr22 = new OBR22();
                            obr22.setValue(dateList);

                            ((Z05Query) query).setStartEndTimestamp(obr22);
                        } else {
                            OBR22 obr22 = new OBR22();
                            obr22.setValue(startTime);

                            ((Z05Query) query).setStartEndTimestamp(obr22);
                        }
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't parse date given for OLIS query", e);
                }


                String quantityLimitedQuery = request.getParameter("quantityLimitedQuery");
                String quantityLimit = request.getParameter("quantityLimit");

                try {
                    if (quantityLimitedQuery != null && quantityLimitedQuery.trim().length() > 0) {
                        // Checked
                        ((Z05Query) query).setQuantityLimitedRequest(new QRD7(Integer.parseInt(quantityLimit)));
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't parse the number given for quantity limit in OLIS query", e);
                }


                String destinationLaboratory = request.getParameter("destinationLaboratory");

                if (destinationLaboratory != null && destinationLaboratory.trim().length() > 0) {
                    ((Z05Query) query).setDestinationLaboratory(new ZBR8(destinationLaboratory, "ISO"));
                }

            } else if (queryType.equalsIgnoreCase("Z06")) {
                query = new Z06Query();


                String startTimePeriod = request.getParameter("startTimePeriod");
                String endTimePeriod = request.getParameter("endTimePeriod");

                try {
                    if (startTimePeriod != null && startTimePeriod.trim().length() > 0) {
                        Date startTime = DateUtils.parseDate(startTimePeriod, dateFormat);
                        if (endTimePeriod != null && endTimePeriod.trim().length() > 0) {
                            Date endTime = changeToEndOfDay(DateUtils.parseDate(endTimePeriod, dateFormat));

                            List<Date> dateList = new LinkedList<Date>();
                            dateList.add(startTime);
                            dateList.add(endTime);

                            OBR22 obr22 = new OBR22();
                            obr22.setValue(dateList);

                            ((Z06Query) query).setStartEndTimestamp(obr22);
                        } else {
                            OBR22 obr22 = new OBR22();
                            obr22.setValue(startTime);

                            ((Z06Query) query).setStartEndTimestamp(obr22);
                        }
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't parse date given for OLIS query", e);
                }


                String quantityLimitedQuery = request.getParameter("quantityLimitedQuery");
                String quantityLimit = request.getParameter("quantityLimit");

                try {
                    if (quantityLimitedQuery != null && quantityLimitedQuery.trim().length() > 0) {
                        // Checked
                        ((Z06Query) query).setQuantityLimitedRequest(new QRD7(Integer.parseInt(quantityLimit)));
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't parse the number given for quantity limit in OLIS query", e);
                }


                String orderingFacility = request.getParameter("orderingFacility");

                if (orderingFacility != null && orderingFacility.trim().length() > 0) {
                    ((Z06Query) query).setOrderingFacilityId(new ORC21(orderingFacility, "^ISO"));
                }

            } else if (queryType.equalsIgnoreCase("Z07")) {
                query = new Z07Query();


                String startTimePeriod = request.getParameter("startTimePeriod");
                String endTimePeriod = request.getParameter("endTimePeriod");

                try {
                    if (startTimePeriod != null && startTimePeriod.trim().length() > 0) {
                        Date startTime = DateUtils.parseDate(startTimePeriod, dateFormat);
                        if (endTimePeriod != null && endTimePeriod.trim().length() > 0) {
                            Date endTime = changeToEndOfDay(DateUtils.parseDate(endTimePeriod, dateFormat));

                            List<Date> dateList = new LinkedList<Date>();
                            dateList.add(startTime);
                            dateList.add(endTime);

                            OBR22 obr22 = new OBR22();
                            obr22.setValue(dateList);

                            ((Z07Query) query).setStartEndTimestamp(obr22);
                        } else {
                            OBR22 obr22 = new OBR22();
                            obr22.setValue(startTime);

                            ((Z07Query) query).setStartEndTimestamp(obr22);
                        }
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't parse date given for OLIS query", e);
                }


                String quantityLimitedQuery = request.getParameter("quantityLimitedQuery");
                String quantityLimit = request.getParameter("quantityLimit");

                try {
                    if (quantityLimitedQuery != null && quantityLimitedQuery.trim().length() > 0) {
                        // Checked
                        ((Z07Query) query).setQuantityLimitedRequest(new QRD7(Integer.parseInt(quantityLimit)));
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't parse the number given for quantity limit in OLIS query", e);
                }

            } else if (queryType.equalsIgnoreCase("Z08")) {
                query = new Z08Query();

                String startTimePeriod = request.getParameter("startTimePeriod");
                String endTimePeriod = request.getParameter("endTimePeriod");

                try {
                    if (startTimePeriod != null && startTimePeriod.trim().length() > 0) {
                        Date startTime = DateUtils.parseDate(startTimePeriod, dateFormat);
                        if (endTimePeriod != null && endTimePeriod.trim().length() > 0) {
                            Date endTime = changeToEndOfDay(DateUtils.parseDate(endTimePeriod, dateFormat));

                            List<Date> dateList = new LinkedList<Date>();
                            dateList.add(startTime);
                            dateList.add(endTime);

                            OBR22 obr22 = new OBR22();
                            obr22.setValue(dateList);

                            ((Z08Query) query).setStartEndTimestamp(obr22);
                        } else {
                            OBR22 obr22 = new OBR22();
                            obr22.setValue(startTime);

                            ((Z08Query) query).setStartEndTimestamp(obr22);
                        }
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't parse date given for OLIS query", e);
                }


                String quantityLimitedQuery = request.getParameter("quantityLimitedQuery");
                String quantityLimit = request.getParameter("quantityLimit");

                try {
                    if (quantityLimitedQuery != null && quantityLimitedQuery.trim().length() > 0) {
                        // Checked
                        ((Z08Query) query).setQuantityLimitedRequest(new QRD7(Integer.parseInt(quantityLimit)));
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Can't parse the number given for quantity limit in OLIS query", e);
                }


            } else if (queryType.equalsIgnoreCase("Z50")) {
                query = new Z50Query();


                String firstName = request.getParameter("z50firstName");

                if (firstName != null && firstName.trim().length() > 0) {
                    ((Z50Query) query).setFirstName(new PID52(firstName));
                }


                String lastName = request.getParameter("z50lastName");

                if (lastName != null && lastName.trim().length() > 0) {
                    ((Z50Query) query).setLastName(new PID51(lastName));
                }


                String sex = request.getParameter("z50sex");

                if (sex != null && sex.trim().length() > 0) {
                    ((Z50Query) query).setSex(new PID8(sex));
                }


                String dateOfBirth = request.getParameter("z50dateOfBirth");
                try {
                    if (dateOfBirth != null && dateOfBirth.trim().length() > 0) {
                        PID7 pid7 = new PID7();
                        pid7.setValue(DateUtils.parseDate(dateOfBirth, dateFormat));
                        ((Z50Query) query).setDateOfBirth(pid7);
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Couldn't parse date given for OLIS query", e);
                }
            }

            String searchUuid = UUID.randomUUID().toString();
            query.setUuid(searchUuid);
            searchQueryMap.put(searchUuid, query);
            request.setAttribute("searchUuid", searchUuid);

            if (queryType.equals("Z04") && request.getParameterValues("requestingHic") != null && request.getParameterValues("requestingHic").length > 1) {
                for (String providerNo : request.getParameterValues("requestingHic")) {
                    Provider provider = providerDao.getProvider(providerNo);
                    ZRP1 zrp1 = new ZRP1(provider.getPractitionerNo(), "MDL", "ON", "HL70347",
                            userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_LAST_NAME),
                            userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_FIRST_NAME),
                            userPropertyDAO.getStringValue(provider.getProviderNo(), UserProperty.OFFICIAL_SECOND_NAME));
                    ((Z04Query) query).setRequestingHic(zrp1);
                    Driver.submitOLISQuery(loggedInInfo, request, query);
                }
            } else {
                Driver.submitOLISQuery(loggedInInfo, request, query);
            }

        }

        return "results";

    }

    /**
     * Adjusts a date to the end of day (23:59:59) for date range queries.
     * <p>
     * This helper method is used to normalize end date parameters to include
     * all results up to the last second of the specified day.
     *
     * @param d Date the date to adjust
     * @return Date the same date set to 23:59:59
     */
    private Date changeToEndOfDay(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        return c.getTime();
    }


}
