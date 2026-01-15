//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 */
package ca.openosp.openo.managers;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import ca.openosp.openo.PMmodule.dao.ProgramAccessDAO;
import ca.openosp.openo.PMmodule.dao.ProgramProviderDAO;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.PMmodule.model.ProgramAccess;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.commn.dao.CustomFilterDao;
import ca.openosp.openo.commn.dao.TicklerCategoryDao;
import ca.openosp.openo.commn.dao.TicklerCommentDao;
import ca.openosp.openo.commn.dao.TicklerDao;
import ca.openosp.openo.commn.dao.TicklerLinkDao;
import ca.openosp.openo.commn.dao.TicklerTextSuggestDao;
import ca.openosp.openo.commn.dao.TicklerUpdateDao;
import ca.openosp.openo.commn.model.CustomFilter;
import ca.openosp.openo.commn.model.Tickler;
import ca.openosp.openo.commn.model.TicklerCategory;
import ca.openosp.openo.commn.model.TicklerComment;
import ca.openosp.openo.commn.model.TicklerLink;
import ca.openosp.openo.commn.model.TicklerTextSuggest;
import ca.openosp.openo.commn.model.TicklerUpdate;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

import ca.openosp.OscarProperties;
import ca.openosp.openo.log.LogAction;

import ca.openosp.openo.model.security.Secrole;

@Service
public class TicklerManagerImpl implements TicklerManager {

    private static final String TICKLER_EMAIL_TEMPLATE_FILE = "/tickler_email_notification_template.txt";
    private static final String PRIVILEGE_READ = "r";
    private static final String PRIVILEGE_WRITE = "w";
    private static final String PRIVILEGE_UPDATE = "u";

    @Autowired
    private ProgramAccessDAO programAccessDAO;

    @Autowired
    private ProgramProviderDAO programProviderDAO;

    @Autowired
    private TicklerDao ticklerDao;

    @Autowired
    private TicklerLinkDao ticklerLinkDao;

    @Autowired
    private TicklerCommentDao ticklerCommentDao;

    @Autowired
    private TicklerUpdateDao ticklerUpdateDao;

    @Autowired
    private ProviderDao providerDao;

    @Autowired
    private ProgramManager programManager;

    @Autowired
    private CaseManagementManager caseManagementManager;

    @Autowired
    private CustomFilterDao customFilterDao;

    @Autowired
    private TicklerTextSuggestDao ticklerTextSuggestDao;

    @Autowired
    private SecurityInfoManager securityInfoManager;

    @Autowired
    private TicklerCategoryDao ticklerCategoryDao;

    @Override
    public List<TicklerCategory> getActiveTicklerCategories(LoggedInInfo loggedInInfo) {
        checkPrivilege(loggedInInfo, PRIVILEGE_READ);

        LogAction.addLogSynchronous(loggedInInfo, "TicklerManager.getActiveTicklerCategories", "All active categories");

        return ticklerCategoryDao.getActiveCategories();
    }

    @Override
    public boolean validateTicklerIsValid(Tickler tickler) {
        if (tickler == null)
            return false;
        if (StringUtils.isEmpty(tickler.getCreator()))
            return false;
        if (StringUtils.isEmpty(tickler.getTaskAssignedTo()))
            return false;
        if (tickler.getDemographicNo() == null || tickler.getDemographicNo().intValue() == 0)
            return false;
        return true;
    }

    @Override
    public boolean addTicklerLink(LoggedInInfo loggedInInfo, TicklerLink ticklerLink) {
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);
        ticklerDao.persist(ticklerLink);

        // --- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "TicklerManager.addTicklerLink",
                "ticklerLinkId=" + ticklerLink.getId());

        return true;
    }

    @Override
    public boolean addTickler(LoggedInInfo loggedInInfo, Tickler tickler) {
        checkPrivilege(loggedInInfo, PRIVILEGE_WRITE);

        if (!validateTicklerIsValid(tickler)) {
            return false;
        }

        ticklerDao.persist(tickler);

        // --- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "TicklerManager.addtickler", "ticklerId=" + tickler.getId());

        return true;
    }

    @Override
    public boolean updateTickler(LoggedInInfo loggedInInfo, Tickler tickler) {
        checkPrivilege(loggedInInfo, PRIVILEGE_UPDATE);

        if (!validateTicklerIsValid(tickler)) {
            return false;
        }
        for (TicklerUpdate tu : tickler.getUpdates()) {
            if (tu.getId() == null || tu.getId().intValue() == 0) {
                ticklerUpdateDao.persist(tu);
            }
        }
        for (TicklerComment tc : tickler.getComments()) {
            if (tc.getId() == null || tc.getId().intValue() == 0) {
                ticklerCommentDao.persist(tc);
            }
        }
        ticklerDao.merge(tickler);

        // --- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "TicklerManager.updatetickler", "ticklerId=" + tickler.getId());

        return true;
    }

    @Override
    public List<Tickler> getTicklers(LoggedInInfo loggedInInfo, CustomFilter filter, String providerNo,
                                     String programId) {
        checkPrivilege(loggedInInfo, PRIVILEGE_READ);

        List<Tickler> results = ticklerDao.getTicklers(filter);

        if (OscarProperties.getInstance().getBooleanProperty("FILTER_ON_FACILITY", "true")) {
            // filter based on facility
            results = ticklerFacilityFiltering(loggedInInfo, results);

            // filter based on caisi role access
            results = filterTicklersByAccess(results, providerNo, programId);
        }
        return (results);
    }

    @Override
    public List<Tickler> getTicklers(LoggedInInfo loggedInInfo, CustomFilter filter) {
        checkPrivilege(loggedInInfo, PRIVILEGE_READ);

        return ticklerDao.getTicklers(filter);
    }

    @Override
    public List<Tickler> getTicklers(LoggedInInfo loggedInInfo, CustomFilter filter, int offset, int limit) {
        checkPrivilege(loggedInInfo, PRIVILEGE_READ);

        return ticklerDao.getTicklers(filter, offset, limit);
    }

    @Override
    public List<Tickler> getTicklerByLabId(LoggedInInfo loggedInInfo, int labId, Integer demoNo) {
        checkPrivilege(loggedInInfo, PRIVILEGE_READ);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        List<TicklerLink> links = ticklerLinkDao.getLinkByTableId("HL7", Long.valueOf(labId));

        ArrayList<Tickler> results = new ArrayList<Tickler>();

        for (TicklerLink link : links) {
            List<Tickler> ticklers = ticklerDao.findByTicklerNoAssignedTo(link.getTicklerNo(), providerNo, demoNo);
            for (Tickler tickler : ticklers) {
                results.add(tickler);
            }
        }

        Collections.sort(results, Tickler.StatusAscComparator);
        return results;
    }

    @Override
    public List<Tickler> getTicklerByLabIdAnyProvider(LoggedInInfo loggedInInfo, int labId, Integer demoNo) {
        checkPrivilege(loggedInInfo, PRIVILEGE_READ);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        List<TicklerLink> links = ticklerLinkDao.getLinkByTableId("HL7", Long.valueOf(labId));

        ArrayList<Tickler> results = new ArrayList<Tickler>();

        for (TicklerLink link : links) {
            List<Tickler> ticklers = ticklerDao.findByTicklerNoDemo(link.getTicklerNo(), demoNo);
            for (Tickler tickler : ticklers) {
                results.add(tickler);
            }
        }

        Collections.sort(results, Tickler.StatusAscComparator);
        return results;
    }

    @Override
    public List<Tickler> ticklerFacilityFiltering(LoggedInInfo loggedInInfo, List<Tickler> ticklers) {
        ArrayList<Tickler> results = new ArrayList<Tickler>();

        for (Tickler tickler : ticklers) {
            Integer programId = tickler.getProgramId();

            if (programManager.hasAccessBasedOnCurrentFacility(loggedInInfo, programId)) {
                results.add(tickler);
            }
        }

        return results;
    }

    @Override
    public List<Tickler> filterTicklersByAccess(List<Tickler> ticklers, String providerNo, String programNo) {
        List<Tickler> filteredTicklers = new ArrayList<Tickler>();

        if (ticklers.isEmpty()) {
            return ticklers;
        }

        String programId = "";
        // iterate through the tickler list
        for (Iterator<Tickler> iter = ticklers.iterator(); iter.hasNext(); ) {
            Tickler t = iter.next();
            boolean add = false;
            List<ProgramProvider> ppList = new ArrayList<ProgramProvider>();

            programId = String.valueOf(t.getProgramId());

            // If the ticklers are not in any program (old ticklers), show them.
            // They will not be filtered by the role access.
            if (programId == null || "".equals(programId) || "null".equals(programId)) {
                filteredTicklers.add(t);
                continue;
            }

            // load up the program_provider entry to get the role for this provider in the
            // tickler's program
            ppList = programProviderDAO.getProgramProviderByProviderProgramId(providerNo, Long.valueOf(programId));
            if (ppList == null || ppList.isEmpty()) {
                continue;
            }
            ProgramProvider pp = ppList.get(0);
            Secrole role = pp.getRole();

            // Get the tickler assigned to providers's role in the tickler's program
            String ticklerRole = null;
            List<ProgramProvider> ppList2 = new ArrayList<ProgramProvider>();
            ppList2 = this.programProviderDAO.getProgramProviderByProviderProgramId(t.getTaskAssignedTo(),
                    Long.valueOf(t.getProgramId()));
            if (ppList2 != null && !ppList2.isEmpty()) {
                ticklerRole = ppList2.get(0).getRole().getRoleName().toLowerCase();
            }

            ProgramAccess pa = null;

            // Load up access list from program
            List<ProgramAccess> programAccessList = programAccessDAO.getAccessListByProgramId(Long.valueOf(programId));
            Map<String, ProgramAccess> programAccessMap = caseManagementManager
                    .convertProgramAccessListToMap(programAccessList);

            // read

            // if the providers's role is allowed to read tickler's assigned to
            // "ticklerRole". add.
            // if no entry exists, but the the providers has same role as assigned to role,
            // then we add
            pa = programAccessMap.get("read ticklers assigned to a " + ticklerRole);
            if (pa != null) {
                if (pa.isAllRoles() || caseManagementManager.isRoleIncludedInAccess(pa, role)) {
                    add = true;
                }
            } else {
                if (ticklerRole.equals(role.getRoleName())) {
                    add = true;
                }
            }
            pa = null;

            // if this providers wrote the tickler, they should see it..doesn't matter
            // about the role based access
            if (!add) {
                if (t.getProvider().getProviderNo().equals(providerNo)) {
                    add = true;
                }

            }

            // apply defaults - i think this is already added above
            if (!add) {
                if (ticklerRole.equals(role.getRoleName())) {
                    add = true;
                }
            }

            // did it pass the test?
            if (add) {
                filteredTicklers.add(t);
            }
        }
        return filteredTicklers;
    }

    @Override
    public int getActiveTicklerCount(LoggedInInfo loggedInInfo, String providerNo) {
        return ticklerDao.getActiveTicklerCount(providerNo);
    }

    @Override
    public int getActiveTicklerByDemoCount(LoggedInInfo loggedInInfo, Integer demographicNo) {
        return ticklerDao.getActiveTicklerByDemoCount(demographicNo);
    }

    @Override
    public int getNumTicklers(LoggedInInfo loggedInInfo, CustomFilter filter) {
        return ticklerDao.getNumTicklers(filter);
    }

    @Override
    public Tickler getTickler(LoggedInInfo loggedInInfo, String tickler_no) {
        checkPrivilege(loggedInInfo, PRIVILEGE_READ);

        Integer id = Integer.valueOf(tickler_no);
        Tickler tickler = ticklerDao.find(id);

        // --- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "TicklerManager.getTickler",
                (tickler != null) ? "id=" + tickler.getId() : "");

        return tickler;
    }

    @Override
    public Tickler getTickler(LoggedInInfo loggedInInfo, Integer id) {
        checkPrivilege(loggedInInfo, PRIVILEGE_READ);

        Tickler tickler = ticklerDao.find(id);

        // --- log action ---
        LogAction.addLogSynchronous(loggedInInfo, "TicklerManager.getTickler",
                (tickler != null) ? "id=" + tickler.getId() : "");

        return tickler;
    }

    @Override
    public void addComment(LoggedInInfo loggedInInfo, Integer tickler_id, String provider, String message) {
        checkPrivilege(loggedInInfo, PRIVILEGE_UPDATE);

        Tickler tickler = ticklerDao.find(tickler_id);
        if (tickler != null && message != null && !"".equals(message)) {
            TicklerComment comment = new TicklerComment();
            comment.setTicklerNo(tickler_id.intValue());
            comment.setProviderNo(provider);
            comment.setMessage(message);
            ticklerCommentDao.persist(comment);

            // --- log action ---
            LogAction.addLogSynchronous(loggedInInfo, "TicklerManager.addComment", "ticklerId=" + tickler_id
                    + ",providers=" + provider + ",message=" + message + ",id=" + comment.getId());
        }
    }

    @Override
    public void reassign(LoggedInInfo loggedInInfo, Integer tickler_id, String provider, String task_assigned_to) {
        checkPrivilege(loggedInInfo, PRIVILEGE_UPDATE);

        Tickler tickler = ticklerDao.find(tickler_id);
        if (tickler != null && !task_assigned_to.equals(tickler.getTaskAssignedTo())) {
            String message;
            String former_assignee = tickler.getAssignee().getFormattedName();
            String current_assignee;
            tickler.setTaskAssignedTo(task_assigned_to);

            TicklerComment comment = new TicklerComment();
            comment.setTicklerNo(tickler_id.intValue());
            comment.setProviderNo(provider);

            current_assignee = providerDao.getProvider(task_assigned_to).getFormattedName();

            message = "RE-ASSIGNMENT RECORD: [Tickler \"" + tickler.getDemographic().getFormattedName()
                    + "\" was reassigned from \"" + former_assignee + "\"  to \"" + current_assignee + "\"]";
            comment.setMessage(message);

            ticklerCommentDao.persist(comment);
            ticklerDao.merge(tickler);

            // --- log action ---
            LogAction.addLogSynchronous(loggedInInfo, "TicklerManager.reassign",
                    "ticklerId=" + tickler_id + ",providers=" + provider + ",task_assigned_to=" + task_assigned_to);
        }
    }

    @Override
    public void updateStatus(LoggedInInfo loggedInInfo, Integer tickler_id, String provider, Tickler.STATUS status) {
        checkPrivilege(loggedInInfo, PRIVILEGE_UPDATE);

        Tickler tickler = ticklerDao.find(tickler_id);
        if (tickler != null && tickler.getStatus() != null && tickler.getStatus() != null
                && !status.equals(tickler.getStatus())) {
            tickler.setStatus(status);
            TicklerUpdate update = new TicklerUpdate();
            update.setProviderNo(provider);
            update.setStatus(status);
            update.setTicklerNo(tickler_id.intValue());
            update.setUpdateDate(new Date());

            ticklerUpdateDao.persist(update);
            ticklerDao.merge(tickler);

            // --- log action ---
            LogAction.addLogSynchronous(loggedInInfo, "TicklerManager.updateStatus",
                    "ticklerId=" + tickler_id + ",providers=" + provider + ",status=" + status);
        }
    }

    @Deprecated
    @Override
    public void sendNotification(LoggedInInfo loggedInInfo, Tickler t) throws IOException {
        throw new UnsupportedOperationException("This method is no longer supported.");
//        checkPrivilege(loggedInInfo, PRIVILEGE_READ);

//        if (t == null) {
//            throw new IllegalArgumentException("Tickler object required to send tickler email");
//        }
//
//        boolean ticklerEditEnabled = Boolean
//                .parseBoolean(OscarProperties.getInstance().getProperty("tickler_edit_enabled"));
//        boolean ticklerEmailEnabled = Boolean
//                .parseBoolean(OscarProperties.getInstance().getProperty("tickler_email_enabled"));
//
//        if (ticklerEditEnabled & ticklerEmailEnabled) {
//            String emailTo = t.getDemographic().getEmail();
//            if (EmailUtilsOld.isValidEmailAddress(emailTo)) {
//
//                InputStream is = TicklerManager.class.getResourceAsStream(TICKLER_EMAIL_TEMPLATE_FILE);
//                String emailTemplate = IOUtils.toString(is);
//                String emailSubject = OscarProperties.getInstance().getProperty("tickler_email_subject");
//                String emailFrom = OscarProperties.getInstance().getProperty("tickler_email_from_address");
//
//                ClinicDAO clinicDao = (ClinicDAO) SpringUtils.getBean(ClinicDAO.class);
//                Clinic c = clinicDao.getClinic();
//
//                VelocityContext velocityContext = VelocityUtils.createVelocityContextWithTools();
//                velocityContext.put("tickler", t);
//                velocityContext.put("clinic", c);
//
//                String mergedSubject = VelocityUtils.velocityEvaluate(velocityContext, emailSubject);
//                String mergedBody = VelocityUtils.velocityEvaluate(velocityContext, emailTemplate);
//
//                EmailUtilsOld.sendEmail(emailTo, null, emailFrom, null, mergedSubject, mergedBody, null);
//
//                // --- log action ---
//                LogAction.addLogSynchronous(loggedInInfo, "TicklerManager.sendNotification", "ticklerId=" + t.getId());
//            } else {
//                throw new EmailException("Email Address is invalid");
//            }
//        }
    }

    @Override
    public void completeTickler(LoggedInInfo loggedInInfo, Integer tickler_id, String provider) {
        checkPrivilege(loggedInInfo, PRIVILEGE_UPDATE);

        updateStatus(loggedInInfo, tickler_id, provider, Tickler.STATUS.C);
    }

    @Override
    public void deleteTickler(LoggedInInfo loggedInInfo, Integer tickler_id, String provider) {
        checkPrivilege(loggedInInfo, PRIVILEGE_UPDATE);

        updateStatus(loggedInInfo, tickler_id, provider, Tickler.STATUS.D);
    }

    @Override
    public void activateTickler(LoggedInInfo loggedInInfo, Integer tickler_id, String provider) {
        checkPrivilege(loggedInInfo, PRIVILEGE_UPDATE);

        updateStatus(loggedInInfo, tickler_id, provider, Tickler.STATUS.A);
    }

    @Override
    public void resolveTicklersBySubstring(LoggedInInfo loggedInInfo, String providerNo, List<String> demographicIds,
                                           String remString) {
        checkPrivilege(loggedInInfo, PRIVILEGE_UPDATE);

        List<Integer> tmp = new ArrayList<Integer>();
        for (String str : demographicIds) {
            tmp.add(Integer.parseInt(str));
        }
        List<Tickler> ticklers = ticklerDao.findActiveByMessageForPatients(tmp, remString);
        for (Tickler t : ticklers) {
            deleteTickler(loggedInInfo, t.getId(), providerNo);
        }
    }

    @Override
    public List<CustomFilter> getCustomFilters() {
        return customFilterDao.getCustomFilters();
    }

    @Override
    public List<CustomFilter> getCustomFilters(String provider_no) {
        return customFilterDao.findByProviderNo(provider_no);
    }

    @Override
    public List<CustomFilter> getCustomFilterWithShortCut(String providerNo) {
        return customFilterDao.getCustomFilterWithShortCut(providerNo);
    }

    @Override
    public CustomFilter getCustomFilter(String name) {
        return customFilterDao.findByName(name);
    }

    @Override
    public CustomFilter getCustomFilter(String name, String providerNo) {
        return customFilterDao.findByNameAndProviderNo(name, providerNo);
    }

    @Override
    public CustomFilter getCustomFilterById(Integer id) {
        return customFilterDao.find(id);
    }

    @Override
    public void saveCustomFilter(CustomFilter filter) {
        if (filter.getId() == null || filter.getId().intValue() == 0) {
            customFilterDao.persist(filter);
        } else {
            customFilterDao.merge(filter);
        }
    }

    @Override
    public void deleteCustomFilter(String name) {
        customFilterDao.deleteCustomFilter(name);
    }

    @Override
    public void deleteCustomFilterById(Integer id) {
        customFilterDao.remove(id);
    }

    @Override
    public void addTickler(String demographic_no, String message, Tickler.STATUS status, String service_date,
                           String creator, Tickler.PRIORITY priority, String task_assigned_to) {

        String date = service_date;
        if (date != null && !date.equals("now()")) { // Just a hack for now.
            date = "'" + service_date + "'";
        }

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Tickler t = new Tickler();
        t.setDemographicNo(Integer.parseInt(demographic_no));
        t.setMessage(message);
        t.setStatus(status);
        t.setUpdateDate(new Date());
        try {
            t.setServiceDate(formatter.parse(service_date));
        } catch (ParseException e) {
            MiscUtils.getLogger().error("Error", e);
            t.setServiceDate(new Date());
        }
        t.setCreator(creator);
        t.setPriority(priority);
        t.setTaskAssignedTo(task_assigned_to);

        ticklerDao.persist(t);
    }

    @Override
    public boolean hasTickler(String demographic, String task_assigned_to, String message) {
        List<Tickler> ticklers = ticklerDao.findByDemographicIdTaskAssignedToAndMessage(Integer.parseInt(demographic),
                task_assigned_to, message);
        return !ticklers.isEmpty();
    }

    @Override
    public void createTickler(String demoNo, String provNo, String message, String assignedTo) {
        Tickler t = new Tickler();
        t.setDemographicNo(Integer.parseInt(demoNo));
        t.setMessage(message);
        t.setCreator(provNo);
        t.setTaskAssignedTo(assignedTo);
        ticklerDao.persist(t);
    }

    @Override
    public void resolveTicklers(LoggedInInfo loggedInInfo, String providerNo, List<String> cdmPatientNos,
                                String remString) {
        resolveTicklersBySubstring(loggedInInfo, providerNo, cdmPatientNos, remString);
    }

    @Override
    public List<Tickler> listTicklers(LoggedInInfo loggedInInfo, Integer demographicNo, Date beginDate, Date endDate) {
        checkPrivilege(loggedInInfo, PRIVILEGE_READ);

        List<Tickler> result = ticklerDao.listTicklers(demographicNo, beginDate, endDate);

        for (Tickler tmp : result) {
            // --- log action ---
            LogAction.addLogSynchronous(loggedInInfo, "TicklerManager.listTicklers", "ticklerId=" + tmp.getId());
        }

        return result;
    }

    @Override
    public List<Tickler> findActiveByDemographicNo(LoggedInInfo loggedInInfo, Integer demographicNo) {
        checkPrivilege(loggedInInfo, PRIVILEGE_READ);

        List<Tickler> result = ticklerDao.findActiveByDemographicNo(demographicNo);

        for (Tickler tmp : result) {
            // --- log action ---
            LogAction.addLogSynchronous(loggedInInfo, "TicklerManager.listTicklers", "ticklerId=" + tmp.getId());
        }

        return result;
    }

    @Override
    public List<Tickler> search_tickler_bydemo(LoggedInInfo loggedInInfo, Integer demographicNo, String status,
                                               Date beginDate, Date endDate) {
        checkPrivilege(loggedInInfo, PRIVILEGE_READ);

        List<Tickler> result = ticklerDao.search_tickler_bydemo(demographicNo, status, beginDate, endDate);

        for (Tickler tmp : result) {
            // --- log action ---
            LogAction.addLogSynchronous(loggedInInfo, "TicklerManager.search_tickler_bydemo",
                    "ticklerId=" + tmp.getId());
        }

        return result;
    }

    @Override
    public List<Tickler> search_tickler(LoggedInInfo loggedInInfo, Integer demographicNo, Date endDate) {
        checkPrivilege(loggedInInfo, PRIVILEGE_READ);

        return ticklerDao.search_tickler(demographicNo, endDate);

        // for(Tickler tmp:result) {
        // //--- log action ---
        // LogAction.addLogSynchronous(loggedInInfo, "TicklerManager.search_tickler",
        // "ticklerId="+tmp.getId());
        // }
        //
        // return result;
    }

    @Override
    public List<TicklerTextSuggest> getActiveTextSuggestions(LoggedInInfo loggedInInfo) {
        return this.ticklerTextSuggestDao.getActiveTicklerTextSuggests();
    }

    @Override
    public List<TicklerTextSuggest> getAllTextSuggestions(LoggedInInfo loggedInInfo, int offset, int itemsToReturn) {
        return this.ticklerTextSuggestDao.findAll(offset, itemsToReturn);
    }

    @Override
    public List<Tickler> sortTicklerList(Boolean isSortAscending, String sortColumn, List<Tickler> ticklers) {

        if (isSortAscending) {
            if (sortColumn.equals(DEMOGRAPHIC_NAME)) {
                Collections.sort(ticklers, Tickler.DemographicNameAscComparator);
            } else if (sortColumn.equals(CREATOR)) {
                Collections.sort(ticklers, Tickler.CreatorAscComparator);
            } else if (sortColumn.equals(SERVICE_DATE)) {
                Collections.sort(ticklers, Tickler.ServiceDateAscComparator);
            } else if (sortColumn.equals(CREATION_DATE)) {
                Collections.sort(ticklers, Tickler.CreationDateAscComparator);
            } else if (sortColumn.equals(PRIORITY)) {
                Collections.sort(ticklers, Tickler.PriorityAscComparator);
            } else if (sortColumn.equals(TASK_ASSIGNED_TO)) {
                Collections.sort(ticklers, Tickler.TaskAssignedToAscComparator);
            }
        } else {
            if (sortColumn.equals(DEMOGRAPHIC_NAME)) {
                Collections.sort(ticklers, Tickler.DemographicNameDescComparator);
            } else if (sortColumn.equals(CREATOR)) {
                Collections.sort(ticklers, Tickler.CreatorDescComparator);
            } else if (sortColumn.equals(SERVICE_DATE)) {
                Collections.sort(ticklers, Tickler.ServiceDateDescComparator);
            } else if (sortColumn.equals(CREATION_DATE)) {
                Collections.sort(ticklers, Tickler.CreationDateDescComparator);
            } else if (sortColumn.equals(PRIORITY)) {
                Collections.sort(ticklers, Tickler.PriorityDescComparator);
            } else if (sortColumn.equals(TASK_ASSIGNED_TO)) {
                Collections.sort(ticklers, Tickler.TaskAssignedToDescComparator);
            }
        }

        return ticklers;
    }

    private void checkPrivilege(LoggedInInfo loggedInInfo, String privilege) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", privilege, null)) {
            throw new RuntimeException("missing required sec object (_tickler)");
        }
    }
}
