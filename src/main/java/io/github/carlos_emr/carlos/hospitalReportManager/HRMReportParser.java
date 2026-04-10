/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.hospitalReportManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import javax.xml.XMLConstants;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;


//Replaced old CXF FileUtils and DOM parser imports with Java NIO for simpler UTF-8 file reads
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;


import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.IncomingLabRulesDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.commn.model.IncomingLabRules;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentSubClassDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToProviderDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentSubClass;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToProvider;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.sax.SAXSource;

import org.springframework.core.io.ClassPathResource;

import org.xml.sax.SAXException;

import io.github.carlos_emr.carlos.hospitalReportManager.xsd.OmdCds;

import io.github.carlos_emr.CarlosProperties;


public class HRMReportParser {

    private static Logger logger = MiscUtils.getLogger();

    private HRMReportParser() {
    }

    public static HRMReport parseReport(LoggedInInfo loggedInInfo, Integer hrmDocumentId) {
        HRMDocumentDao hrmDocumentDao = SpringUtils.getBean(HRMDocumentDao.class);
        HRMDocument hrmDocument = hrmDocumentDao.find(hrmDocumentId);
        if (hrmDocument != null) {
            return parseReport(loggedInInfo, hrmDocument.getReportFile());
        }
        return null;
    }

    public static HRMReport parseReport(LoggedInInfo loggedInInfo, String hrmReportFileLocation) {
        return parseReport(loggedInInfo, hrmReportFileLocation, null);
    }

    /*
     * Called when a report is added to system
     */
    public static HRMReport parseReport(LoggedInInfo loggedInInfo, String hrmReportFileLocation, List<Throwable> errors) {
        OmdCds root = null;

        logger.info("Parsing the Report in the location:" + hrmReportFileLocation);

        String fileData = null;
        if (hrmReportFileLocation != null) {
            try {
                // a lot of the parsers need to refer to a file and even when they provide
                // parse(String text) it treats the text as a URL, so we load from disk
                File tmpXMLholder = new File(hrmReportFileLocation);

                // check DOCUMENT_DIR if not found
                if (!tmpXMLholder.exists()) {
                    String place = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
                    tmpXMLholder = new File(place + File.separator + hrmReportFileLocation);
                }

                if (!tmpXMLholder.exists()) {
                    logger.warn("unable to find the HRM report. checked "
                        + hrmReportFileLocation + ", and in the document_dir");
                }

                // read file into UTF-8 String using NIO
                if (tmpXMLholder.exists()) {
                    fileData = Files.readString(
                        tmpXMLholder.toPath(),
                        StandardCharsets.UTF_8
                    );
                }

                // Load and compile the XSD schema
                SchemaFactory factory = XmlUtils.createSecureSchemaFactory(
                    XMLConstants.W3C_XML_SCHEMA_NS_URI);
                File schemaFile = new ClassPathResource("/xsd/hrm/1.1.2/ontariomd_hrm.xsd").getFile();
                Source schemaSource = new StreamSource(schemaFile);
                Schema schema = factory.newSchema(schemaSource);

                // Unmarshal into JAXB model
                JAXBContext jc = JAXBContext.newInstance(OmdCds.class);
                Unmarshaller u = jc.createUnmarshaller();
                u.setSchema(schema);
                try (FileInputStream fileInputStream = new FileInputStream(tmpXMLholder)) {
                    SAXSource secureSource = XmlUtils.createSecureJaxbSource(fileInputStream);
                    root = (OmdCds) u.unmarshal(secureSource);
                }

                tmpXMLholder = null;
            } catch (FileNotFoundException e) {
                logger.error("File Not Found " + e);
                if (errors != null) errors.add(e);
            } catch (SAXException | ParserConfigurationException e) {
                logger.error("SAX ERROR PARSING XML " + e);
                if (errors != null) errors.add(e);
            } catch (JAXBException e) {
                String msg = (e.getLinkedException() != null)
                    ? e.getLinkedException().getMessage()
                    : e.getMessage();
                logger.error("HRM JAXB parse error: " + msg, e);
                if (errors != null) errors.add(e);
            } catch (IOException e) {
                logger.error("ERROR READING report_manager_cds.xsd RESOURCE", e);
                if (errors != null) errors.add(e);
            }

            if (root != null && hrmReportFileLocation != null && fileData != null) {
                return new HRMReport(root, hrmReportFileLocation, fileData);
            }
        }

        return null;
    }

    private static void routeReportToDemographic(HRMReport report, HRMDocument mergedDocument) {

        if (report == null) {
            logger.info("routeReportToDemographic cannot continue, report parameter is null");
            return;
        }


        logger.info("Routing Report To Demographic, for file:" + report.getFileLocation());

        // Search the demographics on the system for a likely match and route it to them automatically
        DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);

        List<Demographic> matchingDemographicListByHin = demographicDao.searchDemographicByHIN(report.getHCN());

        if (matchingDemographicListByHin.size() > 0) {
            if (CarlosProperties.getInstance().isPropertyActive("omd_hrm_demo_matching_criteria")) {
                for (Demographic d : matchingDemographicListByHin) {
                    if (report.getGender().equalsIgnoreCase(d.getSex())
                            && report.getDateOfBirthAsString().equalsIgnoreCase(d.getBirthDayAsString())
                            && report.getLegalLastName().equalsIgnoreCase(d.getLastName())) {
                        HRMReportParser.routeReportToDemographic(mergedDocument.getId(), d.getDemographicNo());
                        break;
                    }
                }
            } else {
                // if there is a matching record assign to variable
                Demographic demographic = matchingDemographicListByHin.get(0); // searchDemographicByHIN typically returns only one result where there is a match
                // if not empty and DOB matches as well, route report to Demographic
                if (report.getDateOfBirthAsString().equalsIgnoreCase(demographic.getBirthDayAsString())) {
                    HRMReportParser.routeReportToDemographic(mergedDocument.getId(), demographic.getDemographicNo());
                }
            }
        }
    }


    private static boolean hasSameStatus(HRMReport report, HRMReport loadedReport) {
        if (report.getResultStatus() != null) {
            return report.getResultStatus().equalsIgnoreCase(loadedReport.getResultStatus());
        }

        return true;
    }

    /*
     * this only gets called for new or changed reports being added to DB. We already know this isn't
     * an exact duplicate report.
     *
     * 1) If this report was sent to another patient before, then we set the parentId of this report to that one
     *
     */
    private static void doSimilarReportCheck(LoggedInInfo loggedInInfo, HRMReport report, HRMDocument mergedDocument) {

        if (report == null) {
            logger.info("doSimilarReportCheck cannot continue, report parameter is null");
            return;
        }
        logger.info("Identifying if this is a report that we received before, but was sent to the wrong demographic, for file:" + report.getFileLocation());

        HRMDocumentDao hrmDocumentDao = (HRMDocumentDao) SpringUtils.getBean(HRMDocumentDao.class);

        // Check #1: Identify if this is a report that we received before, but was sent to the wrong demographic.
        // we set the parent on those other reports to this one. this way we can display the other versions when viewing.
        List<Integer> parentReportList = hrmDocumentDao.findAllWithSameNoDemographicInfoHash(mergedDocument.getReportLessDemographicInfoHash());
        if (parentReportList != null && parentReportList.size() > 0) {
            for (Integer id : parentReportList) {
                if (id != null && id.intValue() != mergedDocument.getId().intValue()) {
                    mergedDocument.setParentReport(id);
                    hrmDocumentDao.merge(mergedDocument);
                    return;
                }
            }
        }

        // Load all the reports for this demographic into memory -- check by name only
        List<HRMReport> thisDemoHrmReportList = HRMReportParser.loadAllReportsRoutedToDemographic(loggedInInfo, report.getLegalName());

        for (HRMReport loadedReport : thisDemoHrmReportList) {
            boolean hasSameReportContent = report.getFirstReportTextContent().equalsIgnoreCase(loadedReport.getFirstReportTextContent());
            boolean hasSameStatus = hasSameStatus(report, loadedReport);
            boolean hasSameClass = report.getFirstReportClass().equalsIgnoreCase(loadedReport.getFirstReportClass());
            boolean hasSameDate = false;

            hasSameDate = HRMReportParser.getAppropriateDateFromReport(report).equals(HRMReportParser.getAppropriateDateFromReport(loadedReport));

            Integer threshold = 0;

            if (hasSameReportContent)
                threshold += 100;
            else
                threshold += 10;

            if (hasSameStatus)
                threshold += 5;
            else
                threshold += 10;

            if (hasSameClass)
                threshold += 10;
            else
                threshold += 10;

            if (hasSameDate)
                threshold += 20;
            else
                threshold += 5;

            if (threshold >= 45) {
                // This is probably a changed report addressed to the same demographic, so set the parent id (as long as this isn't the same report) and we're done!
                if (loadedReport.getHrmParentDocumentId() != null && loadedReport.getHrmDocumentId().intValue() != mergedDocument.getId().intValue()) {
                    mergedDocument.setParentReport(loadedReport.getHrmParentDocumentId());
                    hrmDocumentDao.merge(mergedDocument);
                    return;
                } else if (loadedReport.getHrmParentDocumentId() == null) {
                    mergedDocument.setParentReport(loadedReport.getHrmDocumentId());
                    hrmDocumentDao.merge(mergedDocument);
                    return;
                }
            }
        }
    }


    private static List<HRMReport> loadAllReportsRoutedToDemographic(LoggedInInfo loggedInInfo, String legalName) {
        DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
        HRMDocumentToDemographicDao hrmDocumentToDemographicDao = (HRMDocumentToDemographicDao) SpringUtils.getBean(HRMDocumentToDemographicDao.class);
        HRMDocumentDao hrmDocumentDao = (HRMDocumentDao) SpringUtils.getBean(HRMDocumentDao.class);

        List<Demographic> matchingDemographicListByName = demographicDao.searchDemographic(legalName);

        List<HRMReport> allRoutedReports = new LinkedList<HRMReport>();

        for (Demographic d : matchingDemographicListByName) {
            List<HRMDocumentToDemographic> matchingHrmDocumentList = hrmDocumentToDemographicDao.findByDemographicNo(d.getDemographicNo().toString());
            for (HRMDocumentToDemographic matchingHrmDocument : matchingHrmDocumentList) {
                HRMDocument hrmDocument = hrmDocumentDao.find(matchingHrmDocument.getHrmDocumentId());

                HRMReport hrmReport = HRMReportParser.parseReport(loggedInInfo, hrmDocument.getReportFile());
                if (hrmReport != null) {
                    hrmReport.setHrmDocumentId(hrmDocument.getId());
                    hrmReport.setHrmParentDocumentId(hrmDocument.getParentReport());
                    allRoutedReports.add(hrmReport);
                }
            }
        }

        return allRoutedReports;

    }


    public static void routeReportToSubClass(HRMReport report, Integer reportId) {
        if (report == null) {
            logger.info("routeReportToSubClass cannot continue, report parameter is null");
            return;
        }

        logger.info("Routing Report To SubClass, for file:" + report.getFileLocation());

        HRMDocumentSubClassDao hrmDocumentSubClassDao = (HRMDocumentSubClassDao) SpringUtils.getBean(HRMDocumentSubClassDao.class);

        if (report.getFirstReportClass().equalsIgnoreCase("Diagnostic Imaging Report") || report.getFirstReportClass().equalsIgnoreCase("Cardio Respiratory Report")) {
            List<List<Object>> subClassList = report.getAccompanyingSubclassList();

            boolean firstSubClass = true;

            for (List<Object> subClass : subClassList) {
                HRMDocumentSubClass newSubClass = new HRMDocumentSubClass();

                newSubClass.setSubClass((String) subClass.get(0));
                newSubClass.setSubClassMnemonic((String) subClass.get(1));
                newSubClass.setSubClassDescription((String) subClass.get(2));
                newSubClass.setSubClassDateTime((Date) subClass.get(3));
                newSubClass.setSendingFacilityId(report.getSendingFacilityId());
                if (firstSubClass) {
                    newSubClass.setActive(true);
                    firstSubClass = false;
                }
                newSubClass.setHrmDocumentId(reportId);

                hrmDocumentSubClassDao.merge(newSubClass);
            }
        } else {
            // There aren't subclasses on a Medical Records Report
        }
    }

    public static String getAppropriateDateStringFromReport(HRMReport report) {
        if (report.getFirstReportClass().equalsIgnoreCase("Diagnostic Imaging Report") || report.getFirstReportClass().equalsIgnoreCase("Cardio Respiratory Report")) {
            return (String) report.getAccompanyingSubclassList().get(0).get(4);
        }

        Calendar calendar = report.getFirstReportEventTime();
        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy");
        sdf.setTimeZone(calendar.getTimeZone());

        return sdf.format(calendar.getTime());
    }

    public static Date getAppropriateDateFromReport(HRMReport report) {
        if (report.getFirstReportClass().equalsIgnoreCase("Diagnostic Imaging Report") || report.getFirstReportClass().equalsIgnoreCase("Cardio Respiratory Report")) {
            return ((Date) (report.getAccompanyingSubclassList().get(0).get(3)));
        }

        // Medical Records Report
        return report.getFirstReportEventTime().getTime();
    }

    public static boolean routeReportToProvider(HRMReport report, Integer reportId) {
        if (report == null) {
            logger.info("routeReportToProvider cannot continue, report parameter is null");
            return false;
        }

        logger.info("Routing Report to Provider, for file:" + report.getFileLocation());

        HRMDocumentToProviderDao hrmDocumentToProviderDao = SpringUtils.getBean(HRMDocumentToProviderDao.class);
        ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
        IncomingLabRulesDao incomingLabRulesDao = SpringUtils.getBean(IncomingLabRulesDao.class);

        String practitionerNo = report.getDeliverToUserId();

        Provider sendToProvider = providerDao.getProviderByPractitionerNo(practitionerNo.substring(1));

        List<Provider> sendToProviderList = new LinkedList<Provider>();
        if (sendToProvider != null) {
            sendToProviderList.add(sendToProvider);
        }

        if (CarlosProperties.getInstance().isPropertyActive("queens_resident_tagging")) {
            DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
            List<Demographic> matchingDemographicListByHin = demographicDao.searchDemographicByHIN(report.getHCN());
            if (!matchingDemographicListByHin.isEmpty()) {
                Demographic demographic = demographicDao.searchDemographicByHIN(report.getHCN()).get(0);
                DemographicCustDao demographicCustDao = SpringUtils.getBean(DemographicCustDao.class);
                //add mrp if not already in list
                if (sendToProvider != null && !sendToProvider.getProviderNo().equals(demographic.getProviderNo()) && demographic.getProvider() != null) {
                    sendToProviderList.add(demographic.getProvider());
                }
                //get and add alt providers
                List<DemographicCust> demographicCust = demographicCustDao.findAllByDemographicNumber(demographic.getDemographicNo());
                if (demographicCust.size() > 0) {
                    ArrayList<String> residentIds = new ArrayList<String>();
                    residentIds.add(demographicCust.get(0).getMidwife());
                    residentIds.add(demographicCust.get(0).getNurse());
                    residentIds.add(demographicCust.get(0).getResident());
                    for (String residentId : residentIds) {
                        if (residentId != null && !residentId.equals("")) {
                            Provider p = providerDao.getProvider(residentId);
                            if (p != null) {
                                sendToProviderList.add(p);
                            }
                        }
                    }
                }
            }
        }

        for (Provider p : sendToProviderList) {

            List<HRMDocumentToProvider> existingHRMDocumentToProviders = hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNoList(reportId, p.getProviderNo());

            if (existingHRMDocumentToProviders == null || existingHRMDocumentToProviders.size() == 0) {
                HRMDocumentToProvider providerRouting = new HRMDocumentToProvider();
                providerRouting.setHrmDocumentId(reportId);

                providerRouting.setProviderNo(p.getProviderNo());
                providerRouting.setSignedOff(0);

                hrmDocumentToProviderDao.merge(providerRouting);
            }

            //Gets the list of IncomingLabRules pertaining to the current providers
            List<IncomingLabRules> incomingLabRules = incomingLabRulesDao.findCurrentByProviderNo(p.getProviderNo());
            //If the list is not null
            if (incomingLabRules != null) {
                //For each labRule in the list
                for (IncomingLabRules labRule : incomingLabRules) {
                    if (labRule.getForwardTypeStrings().contains("HRM")) {
                        //Creates a string of the providers number that the lab will be forwarded to
                        String forwardProviderNumber = labRule.getFrwdProviderNo();
                        //Checks to see if this providers is already linked to this lab
                        HRMDocumentToProvider hrmDocumentToProvider = hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(reportId, forwardProviderNumber);
                        //If a record was not found
                        if (hrmDocumentToProvider == null) {
                            //Puts the information into the HRMDocumentToProvider object
                            hrmDocumentToProvider = new HRMDocumentToProvider();
                            hrmDocumentToProvider.setHrmDocumentId(reportId);
                            hrmDocumentToProvider.setProviderNo(forwardProviderNumber);
                            hrmDocumentToProvider.setSignedOff(0);
                            //Stores it in the table
                            hrmDocumentToProviderDao.persist(hrmDocumentToProvider);
                        }
                    }
                }
            }
        }

        return sendToProviderList.size() > 0;

    }

    public static void routeReportToProvider(HRMDocument originalDocument, HRMReport newReport) {
        routeReportToProvider(newReport, originalDocument.getId());
    }

    public static void routeReportToProvider(Integer reportId, String providerNo) {
        HRMDocumentToProviderDao hrmDocumentToProviderDao = (HRMDocumentToProviderDao) SpringUtils.getBean(HRMDocumentToProviderDao.class);
        HRMDocumentToProvider providerRouting = new HRMDocumentToProvider();

        providerRouting.setHrmDocumentId(reportId);
        providerRouting.setProviderNo(providerNo);

        hrmDocumentToProviderDao.merge(providerRouting);

    }

    public static void routeReportToDemographic(Integer reportId, Integer demographicNo) {
        HRMDocumentToDemographicDao hrmDocumentToDemographicDao = (HRMDocumentToDemographicDao) SpringUtils.getBean(HRMDocumentToDemographicDao.class);

        HRMDocumentToDemographic demographicRouting = new HRMDocumentToDemographic();
        demographicRouting.setDemographicNo(demographicNo);
        demographicRouting.setHrmDocumentId(reportId);
        demographicRouting.setTimeAssigned(new Date());

        hrmDocumentToDemographicDao.merge(demographicRouting);

    }
}
