//CHECKSTYLE:OFF
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
 */


package ca.openosp.openo.documentManager.actions;

import ca.openosp.openo.commn.dao.*;
import ca.openosp.openo.daos.security.SecObjectNameDao;
import ca.openosp.openo.model.security.Secobjectname;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.dao.SecUserRoleDao;
import ca.openosp.openo.PMmodule.model.SecUserRole;
import ca.openosp.openo.PMmodule.utility.UtilDateUtilities;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.ProviderInboxItem;
import ca.openosp.openo.commn.model.Queue;
import ca.openosp.openo.commn.model.QueueDocumentLink;
import ca.openosp.openo.documentManager.EDoc;
import ca.openosp.openo.documentManager.EDocUtil;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.lab.ca.all.Hl7textResultsData;
import ca.openosp.openo.lab.ca.on.CommonLabResultData;
import ca.openosp.openo.lab.ca.on.HRMResultsData;
import ca.openosp.openo.lab.ca.on.LabResultData;
import ca.openosp.openo.mds.data.CategoryData;
import ca.openosp.openo.util.OscarRoleObjectPrivilege;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class DmsInboxManage2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static final Logger logger = MiscUtils.getLogger();

    private ProviderInboxRoutingDao providerInboxRoutingDAO = SpringUtils.getBean(ProviderInboxRoutingDao.class);
    private QueueDocumentLinkDao queueDocumentLinkDAO = SpringUtils.getBean(QueueDocumentLinkDao.class);
    private SecObjectNameDao secObjectNameDao = SpringUtils.getBean(SecObjectNameDao.class);
    private SecUserRoleDao secUserRoleDao = SpringUtils.getBean(SecUserRoleDao.class);
    private QueueDao queueDAO = SpringUtils.getBean(QueueDao.class);

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() {
        String mtd = request.getParameter("method");
        if ("previewPatientDocLab".equals(mtd)) {
            return previewPatientDocLab();
        } else if ("prepareForIndexPage".equals(mtd)) {
            return prepareForIndexPage();
        } else if ("prepareForContentPage".equals(mtd)) {
            return prepareForContentPage();
        } else if ("addNewQueue".equals(mtd)) {
            return addNewQueue();
        } else if ("isDocumentLinkedToDemographic".equals(mtd)) {
            return isDocumentLinkedToDemographic();
        } else if ("isLabLinkedToDemographic".equals(mtd)) {
            return isLabLinkedToDemographic();
        } else if ("updateDocStatusInQueue".equals(mtd)) {
            return updateDocStatusInQueue();
        } else if ("getDocumentsInQueues".equals(mtd)) {
            return getDocumentsInQueues();
        }
        return null;
    }


    private void addQueueSecObjectName(String queuename, String queueid) {
        String q = "_queue.";
        if (queuename != null && queueid != null) {
            q += queueid;
            Secobjectname sbn = new Secobjectname();
            sbn.setObjectname(q);
            sbn.setDescription(queuename);
            sbn.setOrgapplicable(0);
            secObjectNameDao.saveOrUpdate(sbn);
        }
    }

//	private boolean isSegmentIDUnique(ArrayList<LabResultData> doclabs, LabResultData data) {
//		boolean unique = true;
//		String sID = (data.segmentID).trim();
//		for (int i = 0; i < doclabs.size(); i++) {
//			LabResultData lrd = doclabs.get(i);
//			if (sID.equals((lrd.segmentID).trim())) {
//				unique = false;
//				break;
//			}
//		}
//		return unique;
//	}

    public String previewPatientDocLab() {
        String demographicNo = request.getParameter("demog");
        String docs = request.getParameter("docs");
        String labs = request.getParameter("labs");
        String providerNo = request.getParameter("providerNo");
        String searchProviderNo = request.getParameter("searchProviderNo");
        String ackStatus = request.getParameter("ackStatus");
        ArrayList<EDoc> docPreview = new ArrayList<EDoc>();
        ArrayList<LabResultData> labPreview = new ArrayList<LabResultData>();

        if (docs.length() == 0) {
            // do nothing
        } else {
            String[] did = docs.split(",");
            List<String> didList = new ArrayList<String>();
            for (int i = 0; i < did.length; i++) {
                if (did[i].length() > 0) {
                    didList.add(did[i]);
                }
            }
            if (didList.size() > 0) docPreview = EDocUtil.listDocsPreviewInbox(didList);

        }

        if (labs.length() == 0) {
            // do nothing
        } else {
            String[] labids = labs.split(",");
            List<String> ls = new ArrayList<String>();
            for (int i = 0; i < labids.length; i++) {
                if (labids.length > 0) ls.add(labids[i]);
            }

            if (ls.size() > 0) labPreview = Hl7textResultsData.getNotAckLabsFromLabNos(ls);
        }

        request.setAttribute("docPreview", docPreview);
        request.setAttribute("labPreview", labPreview);
        request.setAttribute("providerNo", providerNo);
        request.setAttribute("searchProviderNo", searchProviderNo);
        request.setAttribute("ackStatus", ackStatus);
        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
        Demographic demographic = demographicManager.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demographicNo);
        String demoName = "Not,Assigned";
        if (demographic != null) demoName = demographic.getFirstName() + "," + demographic.getLastName();
        request.setAttribute("demoName", demoName);
        return "doclabPreview";
    }

    public String prepareForIndexPage() {
        HttpSession session = request.getSession();
        try {
            if (session.getAttribute("userrole") == null)
                response.sendRedirect(request.getContextPath() + "/logout.jsp");
        } catch (Exception e) {
            MiscUtils.getLogger().error("error", e);
        }

        String providerNo = (String) session.getAttribute("user");
        String searchProviderNo = request.getParameter("searchProviderNo");
        boolean searchAll = request.getParameter("searchProviderAll") != null;
        String status = request.getParameter("status");
        List<String> abnormalStatusValues = Arrays.asList("all", "abnormalOnly", "normalOnly");
        String abnormalStatus = request.getParameter("abnormalStatus");
        String searchPage = request.getParameter("isSearchPage") == null ? "" : request.getParameter("isSearchPage");

        if (status == null) {
            status = "N";
        } // default to new labs only
        else if ("-1".equals(status)) {
            status = "";
        }
        if (abnormalStatus == null || !abnormalStatusValues.contains(abnormalStatus)) {
            abnormalStatus = "all";
        }
        if (providerNo == null) {
            providerNo = "";
        }

        if (searchAll) {
            searchProviderNo = request.getParameter("searchProviderAll");
        } else if (searchProviderNo == null) {
            searchProviderNo = providerNo;
        } // default to current providers

        boolean providerSearch = !"-1".equals(searchProviderNo);

        MiscUtils.getLogger().debug("SEARCH " + searchProviderNo);
        String patientFirstName = request.getParameter("fname");
        String patientLastName = request.getParameter("lname");
        String patientHealthNumber = request.getParameter("hnum");
        String startDate = request.getParameter("startDate");
        String endDate = request.getParameter("endDate");

        if (patientFirstName == null) {
            patientFirstName = "";
        }
        if (patientLastName == null) {
            patientLastName = "";
        }
        if (patientHealthNumber == null) {
            patientHealthNumber = "";
        }
        boolean patientSearch = !"".equals(patientFirstName) || !"".equals(patientLastName)
                || !"".equals(patientHealthNumber);
        try {
            CategoryData cData = new CategoryData(patientLastName, patientFirstName, patientHealthNumber,
                    patientSearch, providerSearch, searchProviderNo, status, abnormalStatus, startDate, endDate);
            cData.populateCountsAndPatients();
            MiscUtils.getLogger().debug("LABS " + cData.getTotalLabs());
            request.setAttribute("patientFirstName", patientFirstName);
            request.setAttribute("patientLastName", patientLastName);
            request.setAttribute("patientHealthNumber", patientHealthNumber);
            request.setAttribute("providerNo", providerNo);
            request.setAttribute("searchProviderNo", searchProviderNo);
            request.setAttribute("ackStatus", status);
            request.setAttribute("abnormalStatus", abnormalStatus);
            request.setAttribute("startDate", startDate);
            request.setAttribute("endDate", endDate);
            request.setAttribute("categoryData", cData);

            return "dms_index";
        } catch (SQLException e) {
            return "error";
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public String prepareForContentPage() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        HttpSession session = request.getSession();
        try {
            if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
        } catch (Exception e) {
            logger.error("Error", e);
        }

        // can't use userrole from session, because it changes if providers A search for providers B's documents

        // oscar.oscarMDS.data.MDSResultsData mDSData = new oscar.oscarMDS.data.MDSResultsData();
        CommonLabResultData comLab = new CommonLabResultData();
        // String providerNo = request.getParameter("providerNo");
        String providerNo = (String) session.getAttribute("user");
        String searchProviderNo = request.getParameter("searchProviderNo");
        String ackStatus = request.getParameter("status");
        String demographicNo = request.getParameter("demographicNo"); // used when searching for labs by patient instead of providers
        String scannedDocStatus = request.getParameter("scannedDocument");
        Integer page = 0;
        try {
            page = Integer.parseInt(request.getParameter("page"));
            if (page > 0) {
                page--;
            }
        } catch (NumberFormatException nfe) {
            page = 0;
        }
        Integer pageSize = 20;
        try {
            String tmp = request.getParameter("pageSize");
            pageSize = Integer.parseInt(tmp);
        } catch (NumberFormatException nfe) {
            pageSize = 20;
        }
        scannedDocStatus = "I";

        String startDateStr = request.getParameter("startDate");
        String endDateStr = request.getParameter("endDate");


        String view = request.getParameter("view");
        if (view == null || "".equals(view)) {
            view = "all";
        }

        boolean mixLabsAndDocs = "normal".equals(view) || "all".equals(view);

        Date startDate = null;
        Date endDate = null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        //Tries to convert the start date to a Date object, if it fails then sets the date to null so it doesn't pass other checks
        try {
            startDate = sdf.parse(startDateStr);
        } catch (Exception e) {
            startDate = null;
        }
        //Tries to convert the end date to a Date object, if it fails then sets the date to null so it doesn't pass other checks
        try {
            endDate = sdf.parse(endDateStr);
            endDate.setTime(endDate.getTime() + ((1000 * 3600 * 24) - 1));
        } catch (Exception e) {
            endDate = null;
        }

        logger.debug("Got dates: " + startDate + "-" + endDate + " out of " + startDateStr + "-" + endDateStr);

        Boolean isAbnormal = null;
        if ("abnormal".equals(view)) {
            isAbnormal = true;
        } else if ("normal".equals(view)) {
            isAbnormal = false;
        }

        if (ackStatus == null) {
            ackStatus = "N";
        } // default to new labs only
        if (providerNo == null) {
            providerNo = "";
        }
        if (searchProviderNo == null) {
            searchProviderNo = providerNo;
        }
        String roleName = "";
        List<SecUserRole> roles = secUserRoleDao.getUserRoles(searchProviderNo);
        for (SecUserRole r : roles) {
            if (roleName.length() == 0) {
                roleName = r.getRoleName();

            } else {
                roleName += "," + r.getRoleName();
            }
        }
        roleName += "," + searchProviderNo;

        List<QueueDocumentLink> qd = queueDocumentLinkDAO.getQueueDocLinks();
        HashMap<String, String> docQueue = new HashMap<String, String>();
        for (QueueDocumentLink qdl : qd) {
            Integer i = qdl.getDocId();
            Integer n = qdl.getQueueId();
            docQueue.put(i.toString(), n.toString());
        }

        InboxResultsDao inboxResultsDao = (InboxResultsDao) SpringUtils.getBean(InboxResultsDao.class);
        String patientFirstName = request.getParameter("fname");
        String patientLastName = request.getParameter("lname");
        String patientHealthNumber = request.getParameter("hnum");

        ArrayList<LabResultData> labdocs = new ArrayList<LabResultData>();

        if (!"labs".equals(view)) {
            labdocs = inboxResultsDao.populateDocumentResultsData(searchProviderNo, demographicNo, patientFirstName,
                    patientLastName, patientHealthNumber, ackStatus, true, page, pageSize, mixLabsAndDocs, isAbnormal, startDate, endDate);
        }
        if (!"documents".equals(view)) {
            labdocs.addAll(comLab.populateLabResultsData(loggedInInfo, searchProviderNo, demographicNo, patientFirstName,
                    patientLastName, patientHealthNumber, ackStatus, true, page, pageSize,
                    mixLabsAndDocs, isAbnormal, startDate, endDate));
        }

        // Find the oldest lab returned in labdocs, use that as the limit date for the HRM query
        Date oldestLab = null;
        Date newestLab = null;
        if (request.getParameter("newestDate") != null) {
            try {
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                newestLab = formatter.parse(request.getParameter("newestDate"));
            } catch (Exception e) {
                logger.error("Couldn't parse date + " + request.getParameter("newestDate"), e);
            }
        }

        for (LabResultData result : labdocs) {
            if (result.getDateObj() != null) {
                if (oldestLab == null || oldestLab.compareTo(result.getDateObj()) > 0)
                    oldestLab = result.getDateObj();
                if (request.getParameter("newestDate") != null && (newestLab == null || newestLab.compareTo(result.getDateObj()) < 0))
                    newestLab = result.getDateObj();
            }
        }

        if (!"labs".equals(view) && !"abnormal".equals(view)) {
            HRMResultsData hrmResult = new HRMResultsData();

            Collection<LabResultData> hrmDocuments = hrmResult.populateHRMdocumentsResultsData(loggedInInfo, searchProviderNo, patientFirstName, patientLastName, patientHealthNumber, demographicNo, ackStatus,
                    endDate, startDate, true, page, pageSize);
            if (oldestLab == null) {
                for (LabResultData hrmDocument : hrmDocuments) {
                    if (oldestLab == null || (hrmDocument.getDateObj() != null && oldestLab.compareTo(hrmDocument.getDateObj()) > 0))
                        oldestLab = hrmDocument.getDateObj();
                }
            }

            labdocs.addAll(hrmDocuments);
        }
        Collections.sort(labdocs);

        HashMap<String, LabResultData> labMap = new HashMap<String, LabResultData>();
        LinkedHashMap<String, ArrayList<String>> accessionMap = new LinkedHashMap<String, ArrayList<String>>();

        int accessionNumCount = 0;
        for (LabResultData result : labdocs) {
            String segmentId = result.getSegmentID();
            if (result.isDocument())
                segmentId += "d";
            else if (result.isHRM())
                segmentId += "h";

            labMap.put(segmentId, result);
            ArrayList<String> labNums = new ArrayList<String>();

            if (result.accessionNumber == null || result.accessionNumber.equals("null") || result.accessionNumber.isEmpty()) {
                labNums.add(segmentId);
                accessionNumCount++;
                accessionMap.put("noAccessionNum" + accessionNumCount + result.labType, labNums);
            } else if (!accessionMap.containsKey(result.accessionNumber + result.labType)) {
                labNums.add(segmentId);
                accessionMap.put(result.accessionNumber + result.labType, labNums);

                // Different MDS Labs may have the same accession Number if they are seperated
                // by two years. So accession numbers are limited to matching only if their
                // labs are within one year of eachother
            } else {
                labNums = accessionMap.get(result.accessionNumber + result.labType);
                boolean matchFlag = false;
                for (int j = 0; j < labNums.size(); j++) {
                    LabResultData matchingResult = labMap.get(labNums.get(j));

                    Date dateA = result.getDateObj();
                    Date dateB = matchingResult.getDateObj();
                    int monthsBetween = 0;
                    if (dateA == null || dateB == null) {
                        monthsBetween = 5;
                    } else if (dateA.before(dateB)) {
                        monthsBetween = UtilDateUtilities.getNumMonths(dateA, dateB);
                    } else {
                        monthsBetween = UtilDateUtilities.getNumMonths(dateB, dateA);
                    }

                    if (monthsBetween < 4) {
                        matchFlag = true;
                        break;
                    }
                }
                if (!matchFlag) {
                    labNums.add(segmentId);
                    accessionMap.put(result.accessionNumber + result.labType, labNums);
                }
            }
        }

        labdocs.clear();

        for (ArrayList<String> labNums : accessionMap.values()) {
            // must sort through in reverse to keep the labs in the correct order
            for (int j = labNums.size() - 1; j >= 0; j--) {
                labdocs.add(labMap.get(labNums.get(j)));
            }
        }
        logger.debug("labdocs.size()=" + labdocs.size());

        /* find all data for the index.jsp page */
        Hashtable patientDocs = new Hashtable();
        Hashtable patientIdNames = new Hashtable();
        String patientIdNamesStr = "";
        Hashtable docStatus = new Hashtable();
        Hashtable docType = new Hashtable();
        Hashtable<String, List<String>> ab_NormalDoc = new Hashtable();

        for (int i = 0; i < labdocs.size(); i++) {
            LabResultData data = labdocs.get(i);

            List<String> segIDs = new ArrayList<String>();
            String labPatientId = data.getLabPatientId();
            if (labPatientId == null || labPatientId.equals("-1")) labPatientId = "-1";

            if (data.isAbnormal()) {
                List<String> abns = ab_NormalDoc.get("abnormal");
                if (abns == null) {
                    abns = new ArrayList<String>();
                    abns.add(data.getSegmentID());
                } else {
                    abns.add(data.getSegmentID());
                }
                ab_NormalDoc.put("abnormal", abns);
            } else {
                List<String> ns = ab_NormalDoc.get("normal");
                if (ns == null) {
                    ns = new ArrayList<String>();
                    ns.add(data.getSegmentID());
                } else {
                    ns.add(data.getSegmentID());
                }
                ab_NormalDoc.put("normal", ns);
            }
            if (patientDocs.containsKey(labPatientId)) {

                segIDs = (List) patientDocs.get(labPatientId);
                segIDs.add(data.getSegmentID());
                patientDocs.put(labPatientId, segIDs);
            } else {
                segIDs.add(data.getSegmentID());
                patientDocs.put(labPatientId, segIDs);
                patientIdNames.put(labPatientId, data.patientName);
                patientIdNamesStr += ";" + labPatientId + "=" + data.patientName;
            }
            docStatus.put(data.getSegmentID(), data.getAcknowledgedStatus());
            docType.put(data.getSegmentID(), data.labType);
        }

        Integer totalDocs = 0;
        Integer totalHL7 = 0;
        Hashtable<String, List<String>> typeDocLab = new Hashtable();
        Enumeration keys = docType.keys();
        while (keys.hasMoreElements()) {
            String keyDocLabId = ((String) keys.nextElement());
            String valType = (String) docType.get(keyDocLabId);

            if (valType.equalsIgnoreCase("DOC")) {
                if (typeDocLab.containsKey("DOC")) {
                    List<String> docids = typeDocLab.get("DOC");
                    docids.add(keyDocLabId);// add doc id to list
                    typeDocLab.put("DOC", docids);
                } else {
                    List<String> docids = new ArrayList<String>();
                    docids.add(keyDocLabId);
                    typeDocLab.put("DOC", docids);
                }
                totalDocs++;
            } else if (valType.equalsIgnoreCase("HL7")) {
                if (typeDocLab.containsKey("HL7")) {
                    List<String> hl7ids = typeDocLab.get("HL7");
                    hl7ids.add(keyDocLabId);
                    typeDocLab.put("HL7", hl7ids);
                } else {
                    List<String> hl7ids = new ArrayList<String>();
                    hl7ids.add(keyDocLabId);
                    typeDocLab.put("HL7", hl7ids);
                }
                totalHL7++;
            }
        }

        Hashtable patientNumDoc = new Hashtable();
        Enumeration patientIds = patientDocs.keys();
        String patientIdStr = "";
        Integer totalNumDocs = 0;
        while (patientIds.hasMoreElements()) {
            String key = (String) patientIds.nextElement();
            patientIdStr += key;
            patientIdStr += ",";
            List<String> val = (List<String>) patientDocs.get(key);
            Integer numDoc = val.size();
            patientNumDoc.put(key, numDoc);
            totalNumDocs += numDoc;
        }

        List<String> normals = ab_NormalDoc.get("normal");
        List<String> abnormals = ab_NormalDoc.get("abnormal");

        logger.debug("labdocs.size()=" + labdocs.size());

        // set attributes
        request.setAttribute("pageNum", page);
        request.setAttribute("docType", docType);
        request.setAttribute("patientDocs", patientDocs);
        request.setAttribute("providerNo", providerNo);
        request.setAttribute("searchProviderNo", searchProviderNo);
        request.setAttribute("patientIdNames", patientIdNames);
        request.setAttribute("docStatus", docStatus);
        request.setAttribute("patientIdStr", patientIdStr);
        request.setAttribute("typeDocLab", typeDocLab);
        request.setAttribute("demographicNo", demographicNo);
        request.setAttribute("ackStatus", ackStatus);
        request.setAttribute("labdocs", labdocs);
        request.setAttribute("patientNumDoc", patientNumDoc);
        request.setAttribute("totalDocs", totalDocs);
        request.setAttribute("totalHL7", totalHL7);
        request.setAttribute("normals", normals);
        request.setAttribute("abnormals", abnormals);
        request.setAttribute("totalNumDocs", totalNumDocs);
        request.setAttribute("patientIdNamesStr", patientIdNamesStr);
        request.setAttribute("oldestLab", oldestLab != null ? DateUtils.formatDate(oldestLab, "yyyy-MM-dd HH:mm:ss") : null);

        return "dms_page";
    }

    public String addNewQueue() {
        boolean success = false;
        try {
            String qn = request.getParameter("newQueueName");
            qn = qn.trim();
            if (qn != null && qn.length() > 0) {
                QueueDao queueDao = (QueueDao) SpringUtils.getBean(QueueDao.class);
                success = queueDao.addNewQueue(qn);
                addQueueSecObjectName(qn, queueDao.getLastId());
            }
        } catch (Exception e) {
            logger.error("Error", e);
        }

        HashMap<String, Boolean> hm = new HashMap<String, Boolean>();
        hm.put("addNewQueue", success);
        ObjectNode jsonObject = objectMapper.valueToTree(hm);
        try {
            response.getOutputStream().write(jsonObject.toString().getBytes());
        } catch (java.io.IOException ioe) {
            logger.error("Error", ioe);
        }
        return null;
    }

    public String isDocumentLinkedToDemographic() {
        boolean success = false;
        String demoId = null;
        try {
            String docId = request.getParameter("docId");
            logger.debug("DocId:" + docId);
            if (docId != null) {
                docId = docId.trim();
                if (docId.length() > 0) {
                    EDoc doc = EDocUtil.getDoc(docId);
                    demoId = doc.getModuleId();

                    if (demoId != null) {
                        logger.debug("DemoId:" + demoId);
                        Integer demographicId = Integer.parseInt(demoId);
                        if (demographicId > 0) {
                            logger.debug("Success true");
                            success = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error", e);
        }

        HashMap<String, Object> hm = new HashMap<String, Object>();
        hm.put("isLinkedToDemographic", success);
        hm.put("demoId", demoId);
        ObjectNode jsonObject = objectMapper.valueToTree(hm);
        try {
            response.getOutputStream().write(jsonObject.toString().getBytes());
        } catch (java.io.IOException ioe) {
            logger.error("Error", ioe);
        }

        return null;
    }

    public String isLabLinkedToDemographic() {
        boolean success = false;
        String demoId = null;
        try {
            String qn = request.getParameter("labid");
            if (qn != null) {
                qn = qn.trim();
                if (qn.length() > 0) {
                    CommonLabResultData c = new CommonLabResultData();
                    demoId = c.getDemographicNo(qn, "HL7");
                    success = (demoId != null && !"0".equals(demoId));
                }
            }
        } catch (Exception e) {
            logger.error("Error", e);
        }

        HashMap<String, Object> hm = new HashMap<>();
        hm.put("isLinkedToDemographic", success);
        hm.put("demoId", demoId);
        ObjectNode jsonObject = objectMapper.valueToTree(hm);
        try {
            response.getOutputStream().write(jsonObject.toString().getBytes());
        } catch (java.io.IOException ioe) {
            logger.error("Error", ioe);
        }
        return null;
    }

    public String updateDocStatusInQueue() {
        String docid = request.getParameter("docid");
        if (docid != null && !docid.isEmpty()) {
            queueDocumentLinkDAO.setStatusInactive(Integer.parseInt(docid));
        }
        return null;
    }

    // return a hastable containing queue id to queue name, a hashtable of queue id and a list of document nos.
    // forward to documentInQueus.jsp
    @SuppressWarnings({"unchecked", "rawtypes"})
    public String getDocumentsInQueues() {
        HttpSession session = request.getSession();
        try {
            if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
        } catch (Exception e) {
            logger.error("Error", e);
        }
        String providerNo = (String) session.getAttribute("user");
        String searchProviderNo = request.getParameter("searchProviderNo");
        String ackStatus = request.getParameter("status");

        if (ackStatus == null) {
            ackStatus = "N";
        } // default to new labs only
        if (providerNo == null) {
            providerNo = "";
        }
        if (searchProviderNo == null) {
            searchProviderNo = providerNo;
        }

        StringBuilder roleName = new StringBuilder();
        List<SecUserRole> roles = secUserRoleDao.getUserRoles(searchProviderNo);
        for (SecUserRole r : roles) {
            if (roleName.length() != 0) {
                roleName.append(',');
            }
            if (r.getRoleName() != null) {
                roleName.append(r.getRoleName());
            }
        }
        roleName.append("," + searchProviderNo);

        String patientIdNamesStr = "";
        List<QueueDocumentLink> qs = queueDocumentLinkDAO.getActiveQueueDocLink();
        HashMap<Integer, List<Integer>> queueDocNos = new HashMap<Integer, List<Integer>>();
        HashMap<Integer, String> docType = new HashMap<Integer, String>();
        HashMap<Integer, List<Integer>> patientDocs = new HashMap<Integer, List<Integer>>();
        DocumentDao documentDao = (DocumentDao) SpringUtils.getBean(DocumentDao.class);
        Demographic demo = new Demographic();
        List<Integer> docsWithPatient = new ArrayList<Integer>();
        HashMap<Integer, String> patientIdNames = new HashMap<Integer, String>();// lbData.patientName = demo.getLastName()+ ", "+demo.getFirstName();
        List<Integer> patientIds = new ArrayList<Integer>();
        Integer demoNo;
        HashMap<Integer, String> docStatus = new HashMap<Integer, String>();
        String patientIdStr = "";
        StringBuilder patientIdBud = new StringBuilder();
        HashMap<String, List<Integer>> typeDocLab = new HashMap<String, List<Integer>>();
        List<Integer> ListDocIds = new ArrayList<Integer>();
        for (QueueDocumentLink q : qs) {
            int qid = q.getQueueId();
            List<Object> vec = OscarRoleObjectPrivilege.getPrivilegeProp("_queue." + qid);
            // if queue is not default and providers doesn't have access to it,continue
            if (qid != Queue.DEFAULT_QUEUE_ID && !OscarRoleObjectPrivilege.checkPrivilege(roleName.toString(), (Properties) vec.get(0), (List) vec.get(1))) {
                continue;
            }
            int docid = q.getDocId();
            ListDocIds.add(docid);
            docType.put(docid, "DOC");
            demo = documentDao.getDemoFromDocNo(Integer.toString(docid));
            if (demo == null) demoNo = -1;
            else demoNo = demo.getDemographicNo();
            if (!patientIds.contains(demoNo)) patientIds.add(demoNo);
            if (!patientIdNames.containsKey(demoNo)) {
                if (demoNo == -1) {
                    patientIdNames.put(demoNo, "Not, Assigned");
                    patientIdNamesStr += ";" + demoNo + "=" + "Not, Assigned";
                } else {
                    patientIdNames.put(demoNo, demo.getLastName() + ", " + demo.getFirstName());
                    patientIdNamesStr += ";" + demoNo + "=" + demo.getLastName() + ", " + demo.getFirstName();
                }

            }
            List<ProviderInboxItem> providers = providerInboxRoutingDAO.getProvidersWithRoutingForDocument("DOC", docid);
            if (providers.size() > 0) {
                ProviderInboxItem pii = providers.get(0);
                docStatus.put(docid, pii.getStatus());
            }
            if (patientDocs.containsKey(demoNo)) {
                docsWithPatient = patientDocs.get(demoNo);
                docsWithPatient.add(docid);
                patientDocs.put(demoNo, docsWithPatient);
            } else {
                docsWithPatient = new ArrayList<Integer>();
                docsWithPatient.add(docid);
                patientDocs.put(demoNo, docsWithPatient);
            }
            if (queueDocNos.containsKey(qid)) {

                List<Integer> ds = queueDocNos.get(qid);
                ds.add(docid);
                queueDocNos.put(qid, ds);

            } else {
                List<Integer> ds = new ArrayList<Integer>();
                ds.add(docid);
                queueDocNos.put(qid, ds);
            }
        }
        Integer dn = 0;
        for (int i = 0; i < patientIds.size(); i++) {
            dn = patientIds.get(i);
            patientIdBud.append(dn);
            if (i != patientIds.size() - 1) patientIdBud.append(",");
        }
        patientIdStr = patientIdBud.toString();
        typeDocLab.put("DOC", ListDocIds);
        List<Integer> normals = ListDocIds;// assume all documents are normal
        List<Integer> abnormals = new ArrayList<Integer>();
        request.setAttribute("typeDocLab", typeDocLab);
        request.setAttribute("docStatus", docStatus);
        request.setAttribute("patientDocs", patientDocs);
        request.setAttribute("patientIdNames", patientIdNames);
        request.setAttribute("docType", docType);
        request.setAttribute("patientIds", patientIds);
        request.setAttribute("patientIdStr", patientIdStr);
        request.setAttribute("normals", normals);
        request.setAttribute("abnormals", abnormals);
        request.setAttribute("queueDocNos", queueDocNos);
        request.setAttribute("patientIdNamesStr", patientIdNamesStr);
        request.setAttribute("queueIdNames", queueDAO.getHashMapOfQueues());
        request.setAttribute("providerNo", providerNo);
        request.setAttribute("searchProviderNo", searchProviderNo);
        return "document_in_queues";

    }
}
