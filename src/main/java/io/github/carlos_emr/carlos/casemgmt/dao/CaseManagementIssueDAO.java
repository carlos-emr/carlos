//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
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
 * Modifications made by Magenta Health in 2024.
 */

package io.github.carlos_emr.carlos.casemgmt.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.caisi_integrator.ws.FacilityIdDemographicIssueCompositePk;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;

public interface CaseManagementIssueDAO {

    public List<CaseManagementIssue> getIssuesByDemographic(String demographic_no);

    public List<CaseManagementIssue> getIssuesByDemographicOrderActive(Integer demographic_no, Boolean resolved);

    public List<CaseManagementIssue> getIssuesByNote(Integer noteId, Boolean resolved);

    public Issue getIssueByCmnId(Integer cmnIssueId);

    public CaseManagementIssue getIssuebyId(String demo, String id);

    public CaseManagementIssue getIssuebyIssueCode(String demo, String issueCode);

    public void deleteIssueById(CaseManagementIssue issue);

    public void saveAndUpdateCaseIssues(List<CaseManagementIssue> issuelist);

    public void saveIssue(CaseManagementIssue issue);

    public List<CaseManagementIssue> getAllCertainIssues();

    public List<Integer> getIssuesByProgramsSince(Date date, List<Program> programs);

    public List<CaseManagementIssue> getIssuesByDemographicSince(String demographic_no, Date date);

    public List<FacilityIdDemographicIssueCompositePk> getIssueIdsForIntegrator(Integer facilityId,
                                                                                Integer demographicNo);
}
