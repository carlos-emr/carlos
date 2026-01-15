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


import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.casemgmt.service.CaseManagementPrint;
import ca.openosp.openo.commn.dao.EncounterTemplateDao;
import ca.openosp.openo.commn.model.EncounterTemplate;
import ca.openosp.openo.managers.ConsultationManager;
import ca.openosp.openo.managers.PreferenceManager;
import ca.openosp.openo.managers.PreferenceManagerImpl;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.webserv.rest.conversion.EncounterTemplateConverter;
import ca.openosp.openo.webserv.rest.conversion.summary.Summary;
import ca.openosp.openo.webserv.rest.to.EncounterTemplateResponse;
import ca.openosp.openo.webserv.rest.to.model.EncounterTemplateTo1;
import ca.openosp.openo.webserv.rest.to.model.MenuItemTo1;
import ca.openosp.openo.webserv.rest.to.model.SummaryTo1;
import ca.openosp.openo.webserv.rest.util.ClinicalConnectUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


@Path("/recordUX/")
@Component("recordUxService")
@Consumes(MediaType.APPLICATION_JSON)
public class RecordUxService extends AbstractServiceImpl {
    private static final Logger logger = MiscUtils.getLogger();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SecurityInfoManager securityInfoManager;

    @Autowired
    private ConsultationManager consultationManager;

    @Autowired
    private EncounterTemplateDao encounterTemplateDao;

    @Autowired
    private PreferenceManager preferenceManager;

    /**
     * $scope.recordtabs2 = [
     * {id : 0,name : 'Master',url : 'partials/master.html'},
     * {id : 1,name : 'Summary',url : 'partials/summary.html'},
     * {id : 2,name : 'Rx',url : 'partials/prescript.jsp'},
     * {id : 3,name : 'Msg',url : 'partials/summary.html'},
     * {id : 4,name : 'Trackers',url : 'partials/tracker.jsp'},
     * {id : 5,name : 'Consults',url : 'partials/summary.html'},
     * {id : 6,name : 'Forms',url : 'partials/formview.html'},
     * {id : 7,name : 'Prevs/Measurements',url : 'partials/summary.html'},
     * {id : 8,name : 'Ticklers',url : 'partials/summary.html'},
     * {id : 10,name : 'Allergies',url : 'partials/summary.html'},
     * {id : 11,name : 'CPP',url : 'partials/cpp.html'},
     * {id : 12,name : 'Labs/Docs',url : 'partials/labview.html'},
     * {id : 13,name : 'Billing',url : 'partials/billing.jsp'}
     * ];
     * ...
     **/

    @GET
    @Path("/{demographicNo}/recordMenu")
    @Produces("application/json")
    public List<MenuItemTo1> getRecordMenu(@PathParam("demographicNo") Integer demographicNo) {
        logger.error("getRecordMenu getting called for demo " + demographicNo);
        LoggedInInfo loggedInInfo = getLoggedInInfo();

        int idCounter = 0;

        List<MenuItemTo1> menulist = new ArrayList<MenuItemTo1>();
        if (securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            menulist.add(MenuItemTo1.generateStateMenuItem(idCounter, "Details", "record.details"));
        }

        if (securityInfoManager.hasPrivilege(loggedInInfo, "_eChart", "r", null)) {
            menulist.add(MenuItemTo1.generateStateMenuItem(idCounter++, "Summary", "record.summary"));
        }

        if (securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.forms", "r", null) || securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.eforms", "r", null)) {
            menulist.add(MenuItemTo1.generateStateMenuItem(idCounter++, "Forms", "record.forms"));
        }

        if (securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.viewTickler", "r", null)) {
            menulist.add(MenuItemTo1.generateStateMenuItem(idCounter++, "Tickler", "record.tickler"));
        }

        //Remove until available
        //if(securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.documents", "r", null) || securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.labResult", "r", null) ) {
        //	menulist.add(MenuItemTo1.generateStateMenuItem(idCounter++, "Labs/Docs", "record.labsdocs"));
        //}

        if (securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.prescriptions", "r", null)) {
            menulist.add(new MenuItemTo1(idCounter++, "Rx", "../oscarRx/choosePatient.do?demographicNo=" + demographicNo));
        }


        menulist.add(MenuItemTo1.generateStateMenuItem(idCounter++, "Rx", "record.prescript"));

        if (securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.consultations", "r", null)) {

            //add notification if patient has outstanding consultation requests (incomplete requests > 1 month)
            String outstanding = consultationManager.hasOutstandingConsultations(loggedInInfo, demographicNo) ? "outstanding" : null;

            if (!consultationManager.isConsultRequestEnabled() && consultationManager.isConsultResponseEnabled()) {
                menulist.add(new MenuItemTo1(idCounter++, "Consultation Responses", "record.consultResponses." + demographicNo, null));
            } else if (consultationManager.isConsultRequestEnabled() && consultationManager.isConsultResponseEnabled()) {
                MenuItemTo1 consultMenu = new MenuItemTo1(idCounter++, "Consultations", null, outstanding);
                consultMenu.setDropdown(true);

                List<MenuItemTo1> consultList = new ArrayList<MenuItemTo1>();
                consultList.add(new MenuItemTo1(idCounter++, "Consultation Requests", "record.consultRequests." + demographicNo, outstanding));
                consultList.add(new MenuItemTo1(idCounter++, "Consultation Responses", "record.consultResponses." + demographicNo, null));
                consultMenu.setDropdownItems(consultList);

                menulist.add(consultMenu);
            } else {
                menulist.add(new MenuItemTo1(idCounter++, "Consultations", "record.consultRequests." + demographicNo, outstanding));
            }
        }

        MenuItemTo1 moreMenu = new MenuItemTo1(idCounter++, "More", null);
        moreMenu.setDropdown(true);

        List<MenuItemTo1> morelist = new ArrayList<MenuItemTo1>();
		
		/*if(securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.preventions", "r", null)) {
			morelist.add(new MenuItemTo1(idCounter++, "Preventions", "../oscarPrevention/index.jsp?demographic_no="+demographicNo));
		}*/
		
		/*if(securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.viewTickler", "r", null)) {
			if( org.oscarehr.commons.IsPropertiesOn.isTicklerPlusEnable()) {
				morelist.add(new MenuItemTo1(idCounter++, "Tickler", "../Tickler.do?filter.demographicNo="+demographicNo));
			}else {
				morelist.add(new MenuItemTo1(idCounter++, "Tickler", "..//tickler/ticklerDemoMain.jsp?demoview="+demographicNo));
			}
		}*/

        if (securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.DxRegistry", "r", null)) {
            morelist.add(new MenuItemTo1(idCounter++, "Disease Registry", "../oscarResearch/dxresearch/setupDxResearch.do?quickList=&demographicNo=" + demographicNo));
        }

        if (securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.oscarMsg", "r", null)) {
            morelist.add(new MenuItemTo1(idCounter++, "Messenger", "../messenger/DisplayDemographicMessages.do?orderby=date&boxType=3&demographic_no=" + demographicNo));
        }

        if (securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.oscarMsg", "r", null)) {
            morelist.add(new MenuItemTo1(idCounter++, "Create Message", "../messenger/SendDemoMessage.do?demographic_no=" + demographicNo));
        }
        // Requires EctSession bean to open the window.  I think it's best to just redo measurements in a better interface in the record with angular
        //if(checkPermissions("_newCasemgmt.measurements", roleName)){
        //	morelist.add(new MenuItemTo1(2, "Measurements", "../oscarEncounter/oscarMeasurements/SetupHistoryIndex.do?demographic_no="+demographicNo));
        //}

        if (securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.documents", "r", null)) {
            morelist.add(new MenuItemTo1(idCounter++, "Documents", "../documentManager/documentReport.jsp?function=demographic&doctype=lab&functionid=" + demographicNo));
        }

        if (securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.decisionSupportAlerts", "r", null)) {
            morelist.add(new MenuItemTo1(idCounter++, "DS Guidelines", "../oscarEncounter/decisionSupport/guidelineAction.do?method=list&provider_no=" + loggedInInfo.getLoggedInProviderNo() + "&demographic_no=" + demographicNo));
        }

        if (securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            if (ClinicalConnectUtil.isReady(loggedInInfo.getLoggedInProviderNo())) {
                //Launch ClinicalConnect and open patient record
                String url = ClinicalConnectUtil.getLaunchURL(loggedInInfo, demographicNo.toString());
                morelist.add(new MenuItemTo1(idCounter++, "Launch ClinicalConnect", url));
            }
        }
		
		/*measurements,<a onclick="popupPage(600,1000,'measurements69','/oscar/oscarEncounter/oscarMeasurements/SetupHistoryIndex.do'); return false;" href="#">Measurements</a>
		 <a onclick="popupPage(500,900,'episode69','/oscar/Episode.do?method=list&amp;demographicNo=69'); return false;" href="#">Episodes</a>
		 <a onclick="popupPage(500,900,'pregnancy69','/oscar/Pregnancy.do?method=list&amp;demographicNo=69'); return false;" href="#">Pregnancies</a>
		 */
        if (!morelist.isEmpty()) {  // If the more list is empty no sense in displaying it.
            moreMenu.setDropdownItems(morelist);
            menulist.add(moreMenu);
        }

        return menulist;
		/*
		 * ADD A WAY TO CHECK IF THE USER HAS AUTHORIZATION to specific modules.
		 * 		  
	    | _newCasemgmt.allergies                      
		| _newCasemgmt.riskFactors                       
		| _newCasemgmt.calculators           
		| _newCasemgmt.templates             
		| _newCasemgmt.cpp                   
		 */
    }

    //This will be more dynamic in the future but will
    @GET
    @Path("/{demographicNo}/summary/{summaryName}") //@Path("/leftsideSummary")
    @Produces("application/json")
    public List<SummaryTo1> getSummary(@PathParam("demographicNo") Integer demographicNo, @PathParam("summaryName") String summaryName) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();// LoggedInInfo.loggedInInfo.get();
        logger.debug("getting summary:" + summaryName + " for demo " + demographicNo + "  loggedInInfo " + loggedInInfo);
        List<SummaryTo1> summaryList = null;
        int count = 0;

        if ("right".equals(summaryName)) {
            summaryList = new ArrayList<SummaryTo1>();
            if ((securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.documents", "r", null) || securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.labResult", "r", null)) && preferenceManager.displaySummaryItem(loggedInInfo, PreferenceManagerImpl.INCOMING_POS)) {
                summaryList.add(new SummaryTo1("Incoming", count++, SummaryTo1.INCOMING_CODE));
            }

            if (securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.decisionSupportAlerts", "r", null) && preferenceManager.displaySummaryItem(loggedInInfo, PreferenceManagerImpl.DS_SUPPORT_POS)) {
                summaryList.add(new SummaryTo1("Decision Support", count++, SummaryTo1.DECISIONSUPPORT_CODE));
            }
        } else if ("left".equals(summaryName)) {
            summaryList = new ArrayList<SummaryTo1>();
            if (securityInfoManager.hasPrivilege(loggedInInfo, "_prevention", "r", null) && preferenceManager.displaySummaryItem(loggedInInfo, PreferenceManagerImpl.PREVENTION_POS)) {
                summaryList.add(new SummaryTo1("Preventions", count++, SummaryTo1.PREVENTIONS));
            }

            if (securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.prescriptions", "r", null) && preferenceManager.displaySummaryItem(loggedInInfo, PreferenceManagerImpl.MEDS_POS)) {
                summaryList.add(new SummaryTo1("Medications", count++, SummaryTo1.MEDICATIONS_CODE));
            }

            if (securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.otherMeds", "r", null) && preferenceManager.displaySummaryItem(loggedInInfo, PreferenceManagerImpl.OTHER_MEDS_POS)) {
                summaryList.add(new SummaryTo1("Other Meds", count++, SummaryTo1.OTHERMEDS_CODE));
            }

            if (preferenceManager.displaySummaryItem(loggedInInfo, PreferenceManagerImpl.ONGOING_POS)) {
                summaryList.add(new SummaryTo1("Ongoing Concerns", count++, SummaryTo1.ONGOINGCONCERNS_CODE));
            }

            if (securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.medicalHistory", "r", null) && preferenceManager.displaySummaryItem(loggedInInfo, PreferenceManagerImpl.MED_HX_POS)) {
                summaryList.add(new SummaryTo1("Medical History", count++, SummaryTo1.MEDICALHISTORY_CODE));
            }

            if (preferenceManager.displaySummaryItem(loggedInInfo, PreferenceManagerImpl.SOC_HX_POS)) {
                //summaryList[2] = new SummaryTo1("Social/Family History",2,"socfamhx");
                summaryList.add(new SummaryTo1("Social History", count++, SummaryTo1.SOCIALHISTORY_CODE));
            }

            if (securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.familyHistory", "r", null) && preferenceManager.displaySummaryItem(loggedInInfo, PreferenceManagerImpl.FAM_HX_POS)) {
                summaryList.add(new SummaryTo1("Family History", count++, SummaryTo1.FAMILYHISTORY_CODE));
            }

            if (preferenceManager.displaySummaryItem(loggedInInfo, PreferenceManagerImpl.REMINDERS_POS)) {
                summaryList.add(new SummaryTo1("Reminders", count++, SummaryTo1.REMINDERS_CODE));
            }

            if (preferenceManager.displaySummaryItem(loggedInInfo, PreferenceManagerImpl.RISK_FACTORS_POS)) {
                summaryList.add(new SummaryTo1("Risk Factors", count++, SummaryTo1.RISK_FACTORS));
            }

            if (preferenceManager.displaySummaryItem(loggedInInfo, PreferenceManagerImpl.ALLERGIES_POS)) {
                summaryList.add(new SummaryTo1("Allergies", count++, SummaryTo1.ALLERGIES));
            }

            if ((securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.forms", "r", null) || securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.eforms", "r", null)) && preferenceManager.displaySummaryItem(loggedInInfo, PreferenceManagerImpl.ASSESSMENTS_POS)) {
                summaryList.add(new SummaryTo1("Assessments", count++, SummaryTo1.ASSESSMENTS_CODE));
            }
            //summaryList[9] = new SummaryTo1("Outgoing",7,"outgoing");
        } else if ("rxLeft".equals(summaryName)) {
            summaryList = new ArrayList<SummaryTo1>();

            if (preferenceManager.displaySummaryItem(loggedInInfo, PreferenceManagerImpl.ALLERGIES_POS)) {
                summaryList.add(new SummaryTo1("Allergies", count++, SummaryTo1.ALLERGIES));
            }

        } else if ("rxRight".equals(summaryName)) {
            summaryList = new ArrayList<SummaryTo1>();

            if (securityInfoManager.hasPrivilege(loggedInInfo, "_newCasemgmt.decisionSupportAlerts", "r", null) && preferenceManager.displaySummaryItem(loggedInInfo, PreferenceManagerImpl.DS_SUPPORT_POS)) {
                summaryList.add(new SummaryTo1("Decision Support", count++, SummaryTo1.DECISIONSUPPORT_CODE));
            }
        }
        return summaryList;
    }


    private static final Map<String, String> MY_MAP = createMap();

    private static Map<String, String> createMap() {
        Map<String, String> result = new HashMap<String, String>();

        result.put("preventions", "preventionsSummary");
        result.put("meds", "rxSummary");
        result.put("othermeds", "issueNoteSummary");
        result.put("ongoingconcerns", "ongoingConcernDxRegSummary");
        result.put("medhx", "issueNoteSummary");
        result.put("socfamhx", "issueNoteSummary");
        result.put("reminders", "issueNoteSummary");
        result.put("assessments", "formsSummary");
        result.put("outgoing", "formsSummary");
        result.put("sochx", "issueNoteSummary");
        result.put("famhx", "issueNoteSummary");
        result.put("incoming", "labsDocsSummary");
        result.put("dssupport", "decisionSupportSummary");
        result.put("allergies", "allergiesSummary");
        result.put("riskfactors", "issueNoteSummary");

        return Collections.unmodifiableMap(result);
    }


    @GET
    @Path("/{demographicNo}/fullSummary/{summaryCode}")
    @Produces("application/json")
    public SummaryTo1 getFullSummmary(@PathParam("demographicNo") Integer demographicNo, @PathParam(value = "summaryCode") String summaryCode) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        SummaryTo1 summary = null;

        Summary summaryInterface = (Summary) SpringUtils.getBean(MY_MAP.get(summaryCode));
        summary = summaryInterface.getSummary(loggedInInfo, demographicNo, summaryCode);

        logger.debug("outgoing summary object:" + summary);
        return summary;
    }


    @GET
    @Path("/{demographicNo}/getFamilyHistory")
    @Produces(MediaType.APPLICATION_JSON)
    public SummaryTo1 getFamilyHistory(@PathParam("demographicNo") Integer demographicNo) {
        return getFullSummmary(demographicNo, SummaryTo1.FAMILYHISTORY_CODE);
    }

    @GET
    @Path("/{demographicNo}/getMedicalHistory")
    @Produces(MediaType.APPLICATION_JSON)
    public SummaryTo1 getMedicalHistory(@PathParam("demographicNo") Integer demographicNo) {
        return getFullSummmary(demographicNo, SummaryTo1.MEDICALHISTORY_CODE);
    }

    @GET
    @Path("/{demographicNo}/getOngoingConcerns")
    @Produces(MediaType.APPLICATION_JSON)
    public SummaryTo1 getOngoingConcerns(@PathParam("demographicNo") Integer demographicNo) {
        return getFullSummmary(demographicNo, SummaryTo1.ONGOINGCONCERNS_CODE);
    }

    @GET
    @Path("/{demographicNo}/getOtherMeds")
    @Produces(MediaType.APPLICATION_JSON)
    public SummaryTo1 getOtherMeds(@PathParam("demographicNo") Integer demographicNo) {
        return getFullSummmary(demographicNo, SummaryTo1.OTHERMEDS_CODE);
    }

    @GET
    @Path("/{demographicNo}/getReminders")
    @Produces(MediaType.APPLICATION_JSON)
    public SummaryTo1 getReminders(@PathParam("demographicNo") Integer demographicNo) {
        return getFullSummmary(demographicNo, SummaryTo1.REMINDERS_CODE);
    }

    @GET
    @Path("/{demographicNo}/getRiskFactors")
    @Produces(MediaType.APPLICATION_JSON)
    public SummaryTo1 getRiskFactors(@PathParam("demographicNo") Integer demographicNo) {
        return getFullSummmary(demographicNo, SummaryTo1.RISK_FACTORS);
    }

    @GET
    @Path("/{demographicNo}/getAllergies")
    @Produces(MediaType.APPLICATION_JSON)
    public SummaryTo1 getAllergies(@PathParam("demographicNo") Integer demographicNo) {
        return getFullSummmary(demographicNo, SummaryTo1.ALLERGIES);
    }

    @GET
    @Path("/{demographicNo}/getPreventions")
    @Produces(MediaType.APPLICATION_JSON)
    public SummaryTo1 getPreventions(@PathParam("demographicNo") Integer demographicNo) {
        return getFullSummmary(demographicNo, SummaryTo1.PREVENTIONS);
    }

    boolean getBoolean(ObjectNode jsonobject, String key) {
        if (jsonobject.has(key)) {
            return jsonobject.get(key).asBoolean();
        }
        return false;
    }

    String getString(ObjectNode jsonobject, String key) {
        if (jsonobject.has(key)) {
            return jsonobject.get(key).asText();
        }
        return null;
    }


    @GET
    @Path("/{demographicNo}/print")
    @Produces("application/pdf")
    public StreamingOutput print(@PathParam("demographicNo") Integer demographicNo, @QueryParam("printOps") String jsonString, @Context HttpServletRequest request) {


        logger.debug("jsonobject " + jsonString);
        ObjectNode jsonobject = null;
        try {
            jsonobject = (ObjectNode) objectMapper.readTree(jsonString);
        } catch (Exception e) {
            logger.error("Error parsing JSON", e);
        }
        //{"printType":"all","cpp":true,"prescript":true,"selectedList":[]}

        final Integer demographicNof = demographicNo;
        final HttpServletRequest requestf = request;
        boolean printAllNotesType = "all".equalsIgnoreCase(getString(jsonobject, "printType"));
        final boolean printDateRangeNotes = "dates".equalsIgnoreCase(getString(jsonobject, "printType"));

        Calendar startCal = null;
        Calendar endCal = null;
        if (printDateRangeNotes) {
            if (jsonobject.has("dates")) {
                ObjectNode datesJson = (ObjectNode) jsonobject.get("dates");
                if (datesJson.has("start")) {
                    startCal = javax.xml.bind.DatatypeConverter.parseDateTime(datesJson.get("start") != null ? datesJson.get("start").asText() : null);
                }
                if (datesJson.has("end")) {
                    endCal = javax.xml.bind.DatatypeConverter.parseDateTime(datesJson.get("end") != null ? datesJson.get("end").asText() : null);
                }
            }
            if (startCal != null && endCal != null) {
                printAllNotesType = true;
            }

        }
        final Calendar startCalf = startCal;
        final Calendar endCalf = endCal;

        final boolean printAllNotes = printAllNotesType;

        final LoggedInInfo loggedInInfo = getLoggedInInfo();
        final boolean printCPP = getBoolean(jsonobject, "cpp");
        final boolean printRx = getBoolean(jsonobject, "rx");
        final boolean printLabs = getBoolean(jsonobject, "labs");
        final boolean printPreventions = getBoolean(jsonobject, "preventions");
        final boolean printAllergies = getBoolean(jsonobject, "allergies");
        final boolean useDates = jsonobject.has("dates");

        final ArrayNode keyArray = (ArrayNode) jsonobject.get("selectedList");
        final String[] noteIds = new String[keyArray.size()];
        for (int i = 0; i < keyArray.size(); i++) {
            noteIds[i] = keyArray.get(i).asText();
        }


        return new StreamingOutput() {
            @Override
            public void write(java.io.OutputStream os)
                    throws IOException, WebApplicationException {
                try {
                    CaseManagementPrint cmp = new CaseManagementPrint();
                    cmp.doPrint(loggedInInfo, demographicNof, printAllNotes, noteIds, printCPP, printRx, printLabs, printPreventions, printAllergies, useDates, startCalf, endCalf, requestf, os);
                } catch (Exception e) {
                    logger.error("error streaming", e);
                } finally {
                    IOUtils.closeQuietly(os);
                }

            }
        };
    }

    @POST
    @Path("/searchTemplates")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public EncounterTemplateResponse getEncounterTemplates(ObjectNode obj, @QueryParam("startIndex") Integer startIndex, @QueryParam("itemsToReturn") Integer itemsToReturn) {

        String name = obj.get("name") != null ? obj.get("name").asText() : null;

        List<EncounterTemplate> et = encounterTemplateDao.findByName(name + "%", startIndex, itemsToReturn);

        List<EncounterTemplateTo1> transfers = new EncounterTemplateConverter().getAllAsTransferObjects(getLoggedInInfo(), et);

        EncounterTemplateResponse response = new EncounterTemplateResponse();
        response.setTemplates(transfers);

        return response;
    }

    @POST
    @Path("/template")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public EncounterTemplateResponse getEncounterTemplate(ObjectNode obj) {

        String name = obj.get("name") != null ? obj.get("name").asText() : null;

        List<EncounterTemplate> et = new ArrayList<EncounterTemplate>();

        et.add(encounterTemplateDao.find(name));

        List<EncounterTemplateTo1> transfers = new EncounterTemplateConverter().getAllAsTransferObjects(getLoggedInInfo(), et);

        EncounterTemplateResponse response = new EncounterTemplateResponse();
        response.setTemplates(transfers);

        return response;
    }

}
