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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.webserv.rest;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.dao.QuickListDao;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.commn.model.QuickList;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DiagnosisTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DxQuickList;
import io.github.carlos_emr.carlos.webserv.rest.to.model.IssueTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import io.github.carlos_emr.carlos.log.LogAction;

@Path("/dxRegisty")
@Consumes(MediaType.APPLICATION_JSON)
public class DiseaseRegistryService extends AbstractServiceImpl {

    @Autowired
    QuickListDao quickListDao;

    @Autowired
    @Qualifier("DxresearchDAO")
    protected DxresearchDAO dxresearchDao;

    @Autowired
    @Qualifier("IssueDAO")
    private IssueDAO issueDao;

    @Autowired
    private SecurityInfoManager securityInfoManager;

    @GET
    @Path("/quickLists")
    @Produces("application/json")
    public List<DxQuickList> getQuickLists() {
        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_newCasemgmt.DxRegistry", "r", null)) {
            throw new SecurityException("missing required sec object (_newCasemgmt.DxRegistry)");
        }

        Map<String, DxQuickList> quickListMap = new HashMap<String, DxQuickList>();

        List<QuickList> quicklists = quickListDao.findAll();

        for (QuickList item : quicklists) {
            DxQuickList dxList = quickListMap.get(item.getQuickListName());
            if (dxList == null) {
                dxList = new DxQuickList();
                dxList.setLabel(item.getQuickListName());
                quickListMap.put(item.getQuickListName(), dxList);
            }

            String desc = dxresearchDao.getDescription(item.getCodingSystem(), item.getDxResearchCode());
            if (desc != null) {
                DiagnosisTo1 dx = new DiagnosisTo1();
                dx.setCode(item.getDxResearchCode());
                dx.setCodingSystem(item.getCodingSystem());
                dx.setDescription(desc);
                dxList.getDxList().add(dx);
            }
        }


        List<DxQuickList> returnQuickLists = new ArrayList<DxQuickList>(quickListMap.values());
        return returnQuickLists;
    }

    @POST
    @Path("/findLikeIssue")
    @Produces("application/json")
    @Consumes("application/json")
    public Response findLikeIssues(DiagnosisTo1 dx) {
        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_newCasemgmt.DxRegistry", "r", null)) {
            throw new SecurityException("missing required sec object (_newCasemgmt.DxRegistry)");
        }
        Issue issue = issueDao.findIssueByTypeAndCode(dx.getCodingSystem(), dx.getCode());
        IssueTo1 returnIssue = new IssueTo1();
        returnIssue.setCode(issue.getCode());
        returnIssue.setDescription(issue.getDescription());
        returnIssue.setId(issue.getId());
        returnIssue.setType(issue.getType());
        returnIssue.setPriority(issue.getPriority());
        returnIssue.setRole(issue.getRole());
        returnIssue.setUpdate_date(issue.getUpdate_date());
        returnIssue.setSortOrderId(issue.getSortOrderId());
        return Response.ok(returnIssue).build();
    }

    /**
     * Adds a diagnosis code to a patient's disease registry.
     *
     * <p>The privilege check is patient-scoped: {@code demographicNo} is passed to
     * {@link SecurityInfoManager#hasPrivilege(io.github.carlos_emr.carlos.utility.LoggedInInfo, String, String, int)}
     * so a patient-specific {@code _newCasemgmt.DxRegistry} restriction takes priority over the caller's
     * general privilege. Without it any authenticated user could write diagnosis codes to an arbitrary
     * patient by supplying their {@code demographicNo} (issue #2280).</p>
     *
     * @param demographicNo patient whose registry is written; rejected with 400 when absent, since the
     *                      privilege check below unboxes it
     * @param issue         diagnosis code and coding system to record
     * @return empty 200 response; already-active entries are a no-op rather than a duplicate
     * @throws jakarta.ws.rs.BadRequestException if {@code demographicNo} is missing
     * @throws SecurityException                 if the caller lacks {@code _newCasemgmt.DxRegistry} write
     *                                           access to this patient
     */
    @POST
    @Path("/{demographicNo}/add")
    @Produces("application/json")
    @Consumes("application/json")
    public Response addToDiseaseRegistry(@PathParam("demographicNo") Integer demographicNo, IssueTo1 issue) {
        if (demographicNo == null) {
            throw new BadRequestException("demographicNo is required");
        }
        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_newCasemgmt.DxRegistry", "w", demographicNo)) {
            throw new SecurityException("missing required sec object (_newCasemgmt.DxRegistry)");
        }
        boolean activeEntryExists = dxresearchDao.activeEntryExists(demographicNo, issue.getType(), issue.getCode());

        if (!activeEntryExists) {
            Dxresearch dx = new Dxresearch();
            dx.setStartDate(new Date());
            dx.setCodingSystem(issue.getType());
            dx.setDemographicNo(demographicNo);
            dx.setDxresearchCode(issue.getCode());
            dx.setStatus('A');
            dx.setProviderNo(getCurrentProvider().getProviderNo());
            dxresearchDao.persist(dx);
            LogAction.addLog(getLoggedInInfo(), "Dxresearch.add", "dxresearch", "" + dx.getId(), "" + demographicNo, dx.toString());
        }

        return Response.ok().build();
    }

    /**
     * Returns the diagnosis codes recorded in a patient's disease registry.
     *
     * <p>Patient-scoped privilege check, for the same reason as
     * {@link #addToDiseaseRegistry(Integer, IssueTo1)}: the registry is PHI, so the caller must hold
     * {@code _newCasemgmt.DxRegistry} read access <em>for this patient</em>, not merely for the module.</p>
     *
     * @param demographicNo patient whose registry is read; rejected with 400 when absent, since the
     *                      privilege check below unboxes it
     * @return 200 with the patient's {@code Dxresearch} rows
     * @throws jakarta.ws.rs.BadRequestException if {@code demographicNo} is missing
     * @throws SecurityException                 if the caller lacks {@code _newCasemgmt.DxRegistry} read
     *                                           access to this patient
     */
    @GET
    @Path("/getDiseaseRegistry")
    @Produces("application/json")
    @Consumes("application/json")
    public Response getDiseaseRegistry(@QueryParam("demographicNo") Integer demographicNo) {
        if (demographicNo == null) {
            throw new BadRequestException("demographicNo is required");
        }
        if (!securityInfoManager.hasPrivilege(getLoggedInInfo(), "_newCasemgmt.DxRegistry", "r", demographicNo)) {
            throw new SecurityException("missing required sec object (_newCasemgmt.DxRegistry)");
        }
        List<Dxresearch> dxresearchList = dxresearchDao.getByDemographicNo(demographicNo);
        return Response.ok(dxresearchList).build();
    }

}
