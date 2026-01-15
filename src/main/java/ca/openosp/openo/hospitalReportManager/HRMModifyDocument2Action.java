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

import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.IncomingLabRulesDao;
import ca.openosp.openo.commn.model.IncomingLabRules;
import ca.openosp.openo.hospitalReportManager.dao.HRMDocumentCommentDao;
import ca.openosp.openo.hospitalReportManager.dao.HRMDocumentDao;
import ca.openosp.openo.hospitalReportManager.dao.HRMDocumentSubClassDao;
import ca.openosp.openo.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import ca.openosp.openo.hospitalReportManager.dao.HRMDocumentToProviderDao;
import ca.openosp.openo.hospitalReportManager.model.HRMDocument;
import ca.openosp.openo.hospitalReportManager.model.HRMDocumentComment;
import ca.openosp.openo.hospitalReportManager.model.HRMDocumentSubClass;
import ca.openosp.openo.hospitalReportManager.model.HRMDocumentToDemographic;
import ca.openosp.openo.hospitalReportManager.model.HRMDocumentToProvider;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class HRMModifyDocument2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    HRMDocumentDao hrmDocumentDao = (HRMDocumentDao) SpringUtils.getBean(HRMDocumentDao.class);
    HRMDocumentToDemographicDao hrmDocumentToDemographicDao = (HRMDocumentToDemographicDao) SpringUtils.getBean(HRMDocumentToDemographicDao.class);
    HRMDocumentToProviderDao hrmDocumentToProviderDao = (HRMDocumentToProviderDao) SpringUtils.getBean(HRMDocumentToProviderDao.class);
    HRMDocumentSubClassDao hrmDocumentSubClassDao = (HRMDocumentSubClassDao) SpringUtils.getBean(HRMDocumentSubClassDao.class);
    HRMDocumentCommentDao hrmDocumentCommentDao = (HRMDocumentCommentDao) SpringUtils.getBean(HRMDocumentCommentDao.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {
        String method = request.getParameter("method");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        if (method != null) {
            if (method.equalsIgnoreCase("makeIndependent"))
                return makeIndependent();
            else if (method.equalsIgnoreCase("signOff"))
                return signOff();
            else if (method.equalsIgnoreCase("assignProvider"))
                return assignProvider();
            else if (method.equalsIgnoreCase("removeDemographic"))
                return removeDemographic();
            else if (method.equalsIgnoreCase("assignDemographic"))
                return assignDemographic();
            else if (method.equalsIgnoreCase("makeActiveSubClass"))
                return makeActiveSubClass();
            else if (method.equalsIgnoreCase("removeProvider"))
                return removeProvider();
            else if (method.equalsIgnoreCase("addComment"))
                return addComment();
            else if (method.equalsIgnoreCase("deleteComment"))
                return deleteComment();
            else if (method.equalsIgnoreCase("setDescription"))
                return setDescription();
            else if (method.equalsIgnoreCase("updateCategory"))
                return updateCategory();
        }

        return "ajax";
    }

    public String makeIndependent() {
        String reportId = request.getParameter("reportId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            HRMDocument document = hrmDocumentDao.find(Integer.parseInt(reportId));
            if (document.getParentReport() != null && !document.getParentReport().equals(Integer.parseInt(reportId))) {
                // There is a parent report that isn't itself, implies this is a child document
                document.setParentReport(null);
                hrmDocumentDao.merge(document);
            } else {
                // This is a parent document so we need to find and disassociate all the children documents (if any)
                List<HRMDocument> documentChildren = hrmDocumentDao.getAllChildrenOf(document.getId());
                if (documentChildren != null && documentChildren.size() > 0) {
                    // If there's children, choose the first child (which has the earliest id) and mark its parent as null
                    HRMDocument newParentDoc = documentChildren.get(0);
                    newParentDoc.setParentReport(null);
                    hrmDocumentDao.merge(newParentDoc);

                    // update all children to have this first child as their parent instead
                    for (HRMDocument childDoc : documentChildren) {
                        if (childDoc.getId().intValue() != newParentDoc.getId().intValue()) {
                            childDoc.setParentReport(newParentDoc.getId());
                            hrmDocumentDao.merge(childDoc);
                        }
                    }
                }
            }

            request.setAttribute("success", true);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to set make document independent but failed.", e);
            request.setAttribute("success", false);
        }

        return "ajax";
    }

    public String signOff() {
        String[] reportIds = request.getParameterValues("reportId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        for (int i = 0; i < reportIds.length; i++) {
            try {
                Integer reportId = Integer.parseInt(reportIds[i]);
                String signedOff = request.getParameterValues("signedOff")[i];
                HRMDocumentToProvider providerMapping = hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(reportId, providerNo);
                if (providerMapping == null) {
                    //check for unclaimed record, if that exists..update that one
                    providerMapping = hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(reportId, "-1");
                    if (providerMapping != null) {
                        providerMapping.setProviderNo(providerNo);
                    }
                }

                if (providerMapping != null) {
                    providerMapping.setSignedOff(Integer.parseInt(signedOff));
                    providerMapping.setSignedOffTimestamp(new Date());
                    hrmDocumentToProviderDao.merge(providerMapping);
                } else {
                    HRMDocumentToProvider hrmDocumentToProvider = new HRMDocumentToProvider();
                    hrmDocumentToProvider.setHrmDocumentId(reportId);
                    hrmDocumentToProvider.setProviderNo(providerNo);
                    hrmDocumentToProvider.setSignedOff(Integer.parseInt(signedOff));
                    hrmDocumentToProvider.setSignedOffTimestamp(new Date());
                    hrmDocumentToProviderDao.persist(hrmDocumentToProvider);
                }

                request.setAttribute("success", true);
            } catch (Exception e) {
                MiscUtils.getLogger().error("Tried to set signed off status on document but failed.", e);
                request.setAttribute("success", false);
            }
        }


        return "ajax";
    }

    public String assignProvider() {
        //Gets the Dao for incoming lab rules
        IncomingLabRulesDao incomingLabRulesDao = SpringUtils.getBean(IncomingLabRulesDao.class);
        String providerNo = request.getParameter("providerNo");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }


        try {
            HRMDocumentToProvider providerMapping = new HRMDocumentToProvider();
            Integer hrmDocumentId = Integer.valueOf(request.getParameter("reportId"));
            providerMapping.setHrmDocumentId(hrmDocumentId);
            providerMapping.setProviderNo(providerNo);
            providerMapping.setSignedOff(0);

            hrmDocumentToProviderDao.merge(providerMapping);

            //Gets the list of IncomingLabRules pertaining to the current providers
            List<IncomingLabRules> incomingLabRules = incomingLabRulesDao.findCurrentByProviderNo(providerNo);
            //If the list is not null
            if (incomingLabRules != null) {
                //For each labRule in the list
                for (IncomingLabRules labRule : incomingLabRules) {
                    if (labRule.getForwardTypeStrings().contains("HRM")) {
                        //Creates a string of the providers number that the lab will be forwarded to
                        String forwardProviderNumber = labRule.getFrwdProviderNo();
                        //Checks to see if this providers is already linked to this lab
                        HRMDocumentToProvider hrmDocumentToProvider = hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(hrmDocumentId, forwardProviderNumber);
                        //If a record was not found
                        if (hrmDocumentToProvider == null) {
                            //Puts the information into the HRMDocumentToProvider object
                            hrmDocumentToProvider = new HRMDocumentToProvider();
                            hrmDocumentToProvider.setHrmDocumentId(hrmDocumentId);
                            hrmDocumentToProvider.setProviderNo(forwardProviderNumber);
                            hrmDocumentToProvider.setSignedOff(0);
                            //Stores it in the table
                            hrmDocumentToProviderDao.persist(hrmDocumentToProvider);
                        }
                    }
                }
            }


            //we want to remove any unmatched entries when we do a manual match like this. -1 means unclaimed in this table.
            HRMDocumentToProvider existingUnmatched = hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(hrmDocumentId, "-1");
            if (existingUnmatched != null) {
                hrmDocumentToProviderDao.remove(existingUnmatched.getId());
            }

            request.setAttribute("success", true);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to assign HRM document to providers but failed.", e);
            request.setAttribute("success", false);
        }

        return "ajax";
    }

    public String removeDemographic() {
        String hrmDocumentId = request.getParameter("reportId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            List<HRMDocumentToDemographic> currentMappingList = hrmDocumentToDemographicDao.findByHrmDocumentId(Integer.parseInt(hrmDocumentId));

            if (currentMappingList != null) {
                for (HRMDocumentToDemographic currentMapping : currentMappingList) {
                    hrmDocumentToDemographicDao.remove(currentMapping.getId());
                }
            }

            request.setAttribute("success", true);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to remove HRM document from demographic but failed.", e);
            request.setAttribute("success", false);
        }

        return "ajax";

    }

    public String assignDemographic() {
        String hrmDocumentId = request.getParameter("reportId");
        String demographicNo = request.getParameter("demographicNo");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            try {
                List<HRMDocumentToDemographic> currentMappingList = hrmDocumentToDemographicDao.findByHrmDocumentId(Integer.parseInt(hrmDocumentId));

                if (currentMappingList != null) {
                    for (HRMDocumentToDemographic currentMapping : currentMappingList) {
                        hrmDocumentToDemographicDao.remove(currentMapping);
                    }
                }
            } catch (Exception e) {
                // Do nothing
            }

            HRMDocumentToDemographic demographicMapping = new HRMDocumentToDemographic();

            demographicMapping.setHrmDocumentId(Integer.valueOf(hrmDocumentId));
            demographicMapping.setDemographicNo(Integer.valueOf(demographicNo));
            demographicMapping.setTimeAssigned(new Date());

            hrmDocumentToDemographicDao.merge(demographicMapping);

            request.setAttribute("success", true);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to assign HRM document to demographic but failed.", e);
            request.setAttribute("success", false);
        }

        return "ajax";
    }

    public String makeActiveSubClass() {
        String hrmDocumentId = request.getParameter("reportId");
        String subClassId = request.getParameter("subClassId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            hrmDocumentSubClassDao.setAllSubClassesForDocumentAsInactive(Integer.parseInt(hrmDocumentId));

            HRMDocumentSubClass newActiveSubClass = hrmDocumentSubClassDao.find(Integer.parseInt(subClassId));
            if (newActiveSubClass != null) {
                newActiveSubClass.setActive(true);
                hrmDocumentSubClassDao.merge(newActiveSubClass);
            }

            request.setAttribute("success", true);

        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to change active subclass but failed.", e);
            request.setAttribute("success", false);
        }


        return "ajax";
    }

    public String removeProvider() {
        String providerMappingId = request.getParameter("providerMappingId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            hrmDocumentToProviderDao.remove(Integer.parseInt(providerMappingId));

            request.setAttribute("success", true);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to remove providers from HRM document but failed.", e);
            request.setAttribute("success", false);
        }

        return "ajax";
    }

    public String addComment() {
        String documentId = request.getParameter("reportId");
        String commentString = request.getParameter("comment");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            HRMDocumentComment comment = new HRMDocumentComment();

            comment.setHrmDocumentId(Integer.parseInt(documentId));
            comment.setComment(commentString);
            comment.setCommentTime(new Date());
            comment.setProviderNo(loggedInInfo.getLoggedInProviderNo());

            hrmDocumentCommentDao.merge(comment);
            request.setAttribute("success", true);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Couldn't add a comment for HRM document", e);
            request.setAttribute("success", false);
        }

        return "ajax";
    }

    public String deleteComment() {
        String commentId = request.getParameter("commentId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            hrmDocumentCommentDao.deleteComment(Integer.parseInt(commentId));
            request.setAttribute("success", true);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Couldn't delete comment on HRM document", e);
            request.setAttribute("success", false);
        }

        return "ajax";
    }

    public String setDescription() {
        String documentId = request.getParameter("reportId");
        String descriptionString = request.getParameter("description");

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            boolean updated = false;
            HRMDocument document = hrmDocumentDao.find(Integer.parseInt(documentId));
            if (document != null) {
                document.setDescription(descriptionString);
                hrmDocumentDao.merge(document);
                updated = true;
            }
            request.setAttribute("success", updated);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Couldn't set description for HRM document", e);
            request.setAttribute("success", false);
        }

        return "ajax";
    }

    public String updateCategory() {
        String hrmDocumentId = request.getParameter("reportId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_hrm", "w", null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }

        try {
            try {
                Integer categoryId = Integer.valueOf(request.getParameter("categoryId"));
                HRMDocument document = hrmDocumentDao.find(Integer.parseInt(hrmDocumentId));
                if (document != null) {
                    document.setHrmCategoryId(categoryId);
                    hrmDocumentDao.merge(document);
                }
            } catch (Exception e) {
                // Do nothing
            }
            request.setAttribute("success", true);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Tried to assign HRM document to category but failed.", e);
            request.setAttribute("success", false);
        }

        return "ajax";
    }
}
