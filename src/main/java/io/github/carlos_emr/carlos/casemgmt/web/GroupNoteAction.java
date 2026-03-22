/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.casemgmt.web;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.web.CreateAnonymousClientAction;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.GroupNoteDao;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.GroupNoteLink;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;


/**
 * Action class for managing group clinical notes. Provides functionality to
 * create and manage notes that are shared across multiple patient demographics
 * within a program, typically used in group therapy or community sessions.
 *
 * @since 2026-03-17
 */
public class GroupNoteAction {

    private static Logger logger = MiscUtils.getLogger();

    private static AdmissionManager admissionManager = (AdmissionManager) SpringUtils.getBean(AdmissionManager.class);
    private static GroupNoteDao groupNoteDao = (GroupNoteDao) SpringUtils.getBean(GroupNoteDao.class);
    private static DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);

    public static int saveGroupNote(LoggedInInfo loggedInInfo, CaseManagementEntryFormBean cform, String programId) {
        logger.info("saving group note");

        String ids[] = cform.getGroupNoteClientIds();
        int totalAnonymous = cform.getGroupNoteTotalAnonymous();

        //logger.info("group note will have " + ids.length + " clients, and " + totalAnonymous + " anonymous clients");


        List<GroupNoteLink> currentLinks = groupNoteDao.findLinksByNoteId(Integer.parseInt(cform.getNoteId()));
        if (currentLinks.size() > 0) {
            for (GroupNoteLink link : currentLinks) {
                if (link.isAnonymous()) {
                    Demographic d = demographicDao.getDemographic(String.valueOf(link.getDemographicNo()));
                    if (d != null) {
                        d.setPatientStatus("DL");
                    }
                    List<Admission> admissions = admissionManager.getAdmissions(link.getDemographicNo());
                    for (Admission admission : admissions) {
                        admission.setDischargeDate(new Date());
                        admission.setDischargeNotes("Auto-Discharge");
                        admission.setAdmissionStatus(Admission.STATUS_DISCHARGED);
                        admissionManager.saveAdmission(admission);
                    }
                }
                link.setActive(false);
                groupNoteDao.merge(link);
            }
        }


        List<Demographic> anonymousClients = new ArrayList<Demographic>();

        //create anonymous clients
        for (int x = 0; x < totalAnonymous; x++) {
            Demographic d = CreateAnonymousClientAction.generateAnonymousClient(loggedInInfo.getLoggedInProviderNo(), Integer.valueOf(programId));
            anonymousClients.add(d);
        }

        logger.info("created anonymous clients");

        //save links
        if (ids != null) {
            for (String id : ids) {
                GroupNoteLink link = new GroupNoteLink();
                link.setNoteId(Integer.valueOf(cform.getNoteId()));
                link.setDemographicNo(Integer.valueOf(id));
                link.setActive(true);
                groupNoteDao.persist(link);
            }
        }

        for (Demographic d : anonymousClients) {
            GroupNoteLink link = new GroupNoteLink();
            link.setNoteId(Integer.valueOf(cform.getNoteId()));
            link.setDemographicNo(d.getDemographicNo());
            link.setAnonymous(true);
            link.setActive(true);
            groupNoteDao.persist(link);
        }

        logger.info("links saved");
        return 0;
    }
}
