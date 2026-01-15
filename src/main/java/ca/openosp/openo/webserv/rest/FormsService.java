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
package ca.openosp.openo.webserv.rest;


import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.AppDefinitionDao;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.dao.EFormDao.EFormSortOrder;
import ca.openosp.openo.commn.model.EForm;
import ca.openosp.openo.commn.model.EFormData;
import ca.openosp.openo.commn.model.EncounterForm;
import ca.openosp.openo.managers.FormsManager;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.webserv.rest.conversion.EFormConverter;
import ca.openosp.openo.webserv.rest.conversion.EncounterFormConverter;
import ca.openosp.openo.webserv.rest.to.AbstractSearchResponse;
import ca.openosp.openo.webserv.rest.to.model.EFormTo1;
import ca.openosp.openo.webserv.rest.to.model.EncounterFormTo1;
import ca.openosp.openo.webserv.rest.to.model.FormListTo1;
import ca.openosp.openo.webserv.rest.to.model.FormTo1;
import ca.openosp.openo.webserv.rest.to.model.MenuTo1;
import ca.openosp.openo.webserv.rest.to.model.SummaryItemTo1;
import ca.openosp.openo.webserv.rest.to.model.SummaryTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.openosp.openo.encounter.data.EctFormData;


/**
 * Service for interacting with forms (eforms and sql table forms in oscar)
 */
@Path("/forms")
@Component("formsService")
@Consumes(MediaType.APPLICATION_JSON)
public class FormsService extends AbstractServiceImpl {
    Logger logger = MiscUtils.getLogger();

    @Autowired
    private FormsManager formsManager;

    @Autowired
    private AppDefinitionDao appDefinitionDao;


    @GET
    @Path("/{demographicNo}/all")
    @Produces("application/json")
    public FormListTo1 getFormsForHeading(@PathParam("demographicNo") Integer demographicNo, @QueryParam("heading") String heading) {
        FormListTo1 formListTo1 = new FormListTo1();
        if (heading.equals("Completed")) {
            List<EFormData> completedEforms = formsManager.findByDemographicId(getLoggedInInfo(), demographicNo);
            Collections.sort(completedEforms, Collections.reverseOrder(EFormData.FORM_DATE_COMPARATOR));

            for (EFormData eformData : completedEforms) {
                int id = eformData.getId();
                int formId = eformData.getFormId();
                String name = eformData.getFormName();
                String subject = eformData.getSubject();
                String status = eformData.getSubject();
                Date date = eformData.getFormDate();
                Boolean showLatestFormOnly = eformData.isShowLatestFormOnly();
                formListTo1.add(FormTo1.create(id, demographicNo, formId, FormsManager.EFORM, name, subject, status, date, showLatestFormOnly));
            }

        } else {  // Only two options right now.  Need to change this anyways
            List<EForm> eforms = formsManager.findByStatus(getLoggedInInfo(), true, null);  //This will have to change to accommodate forms too.
            Collections.sort(eforms, EForm.FORM_NAME_COMPARATOR);
            for (EForm eform : eforms) {
                int formId = eform.getId();
                String name = eform.getFormName();
                String subject = eform.getSubject();
                String status = null;
                Date date = null;
                Boolean showLatestFormOnly = eform.isShowLatestFormOnly();
                formListTo1.add(FormTo1.create(null, demographicNo, formId, FormsManager.EFORM, name, subject, status, date, showLatestFormOnly));
            }
        }
        return formListTo1;
    }

    @GET
    @Path("/allEForms")
    @Produces("application/json")
    public AbstractSearchResponse<EFormTo1> getAllEFormNames() {
        AbstractSearchResponse<EFormTo1> response = new AbstractSearchResponse<EFormTo1>();
        response.setContent(new EFormConverter(true).getAllAsTransferObjects(getLoggedInInfo(), formsManager.findByStatus(getLoggedInInfo(), true, EFormSortOrder.NAME)));
        response.setTotal(response.getContent().size());
        return response;

    }

    @GET
    @Path("/allEncounterForms")
    @Produces("application/json")
    public AbstractSearchResponse<EncounterFormTo1> getAllFormNames() {
        AbstractSearchResponse<EncounterFormTo1> response = new AbstractSearchResponse<EncounterFormTo1>();
        response.setContent(new EncounterFormConverter().getAllAsTransferObjects(getLoggedInInfo(), formsManager.getAllEncounterForms()));
        response.setTotal(response.getContent().size());
        return response;

    }

    @GET
    @Path("/selectedEncounterForms")
    @Produces("application/json")
    public AbstractSearchResponse<EncounterFormTo1> getSelectedFormNames() {
        AbstractSearchResponse<EncounterFormTo1> response = new AbstractSearchResponse<EncounterFormTo1>();
        response.setContent(new EncounterFormConverter().getAllAsTransferObjects(getLoggedInInfo(), formsManager.getSelectedEncounterForms()));
        response.setTotal(response.getContent().size());
        return response;

    }


    @GET
    @Path("/{demographicNo}/completedEncounterForms")
    @Produces("application/json")
    public FormListTo1 getCompletedFormNames(@PathParam("demographicNo") String demographicNo) {
        FormListTo1 formListTo1 = new FormListTo1();

        List<EncounterForm> encounterForms = formsManager.getAllEncounterForms();
        Collections.sort(encounterForms, EncounterForm.BC_FIRST_COMPARATOR);

        for (EncounterForm encounterForm : encounterForms) {
            String table = StringUtils.trimToNull(encounterForm.getFormTable());
            if (table != null) {

                EctFormData.PatientForm[] pforms = EctFormData.getPatientFormsFromLocalAndRemote(getLoggedInInfo(), demographicNo, table);
                int formId = 0;
                String name = encounterForm.getFormName();

                if (pforms.length > 0) {

                    EctFormData.PatientForm pfrm = pforms[0];
                    formId = Integer.parseInt(pfrm.getFormId());
                    Date date;

                    //d-MMM-y
                    DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
                    String dateStr = pfrm.getCreated();
                    try {
                        date = formatter.parse(dateStr);
                    } catch (ParseException ex) {
                        date = null;
                    }

                    formListTo1.add(FormTo1.create(null, Integer.parseInt(demographicNo), formId, FormsManager.FORM, name, null, null, date, false));

                }

            }
        }

        return formListTo1;
    }

    @GET
    @Path("/groupNames")
    @Produces("application/json")
    public AbstractSearchResponse<String> getGroupNames() {
        AbstractSearchResponse<String> response = new AbstractSearchResponse<String>();

        response.setContent(formsManager.getGroupNames());
        response.setTotal(response.getContent().size());
        return response;

    }

    @GET
    @Path("/getFavouriteFormGroup")
    @Produces("application/json")
    public SummaryTo1 getFavouriteFormGroups() {
        UserPropertyDAO userPropertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
        String groupName = userPropertyDao.getStringValue(getLoggedInInfo().getLoggedInProviderNo(), "favourite_eform_group");
        logger.debug("favourite eform group name " + groupName);
        if (groupName == null) return null;
        List<EForm> eforms = formsManager.getEfromInGroupByGroupName(getLoggedInInfo(), groupName);
        SummaryTo1 formSummary = new SummaryTo1(groupName, 0, null);
        List<SummaryItemTo1> summaryItems = formSummary.getSummaryItem();
        for (EForm eform : eforms) {
            SummaryItemTo1 summaryItem = new SummaryItemTo1(eform.getId(), eform.getFormName(), "record.forms.new", "eform");
            summaryItems.add(summaryItem);
        }
        return formSummary;
    }


    @GET
    @Path("/getFormGroups")
    @Produces("application/json")
    public List<SummaryTo1> getGroupsWithForms() {
        int count = 0;
        List<SummaryTo1> summaryList = new ArrayList<SummaryTo1>();
        List<String> groupNames = formsManager.getGroupNames();

        if (groupNames != null) {
            Collections.sort(groupNames);
            for (String groupName : groupNames) {
                SummaryTo1 formSummary = new SummaryTo1(groupName, count++, null);
                summaryList.add(formSummary);
                List<EForm> eforms = formsManager.getEfromInGroupByGroupName(getLoggedInInfo(), groupName);
                List<SummaryItemTo1> summaryItems = formSummary.getSummaryItem();
                for (EForm eform : eforms) {
                    SummaryItemTo1 summaryItem = new SummaryItemTo1(eform.getId(), eform.getFormName(), "record.forms.new", "eform");
                    summaryItems.add(summaryItem);
                }
            }
        }
        return summaryList;
    }

    @GET
    @Path("/{demographicNo}/formOptions")
    @Produces("application/json")
    public MenuTo1 getFormOptions(@PathParam("demographicNo") String demographicNo) {
        ResourceBundle bundle = getResourceBundle();
        MenuTo1 formMenu = new MenuTo1();
        int idCounter = 0;

        //formMenu.add(idCounter++, bundle.getString("global.saveAsPDF"), "PDF", "URL");
        return formMenu;
    }
}
