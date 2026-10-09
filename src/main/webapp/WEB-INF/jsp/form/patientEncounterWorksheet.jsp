<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>

<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_form" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_form");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="io.github.carlos_emr.carlos.util.*, io.github.carlos_emr.carlos.form.*, io.github.carlos_emr.carlos.form.data.*,java.util.List" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Clinic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ClinicDAO" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Allergy" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.AllergyDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Appointment" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%
    String formClass = "CostQuestionnaire";
    String formLink = "formcostquestionnaire.jsp";

    int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
    int formId = Integer.parseInt(request.getParameter("formId"));
    int provNo = Integer.parseInt((String) session.getAttribute("user"));
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    ClinicDAO clinicDao = SpringUtils.getBean(ClinicDAO.class);
    DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    AllergyDao allergyDao = SpringUtils.getBean(AllergyDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);

    Clinic clinic = clinicDao.getClinic();
    Demographic demographic = demographicDao.getDemographicById(demoNo);
    StringBuilder allergyString = new StringBuilder();
    List<Allergy> allergies = allergyDao.findActiveAllergies(demoNo);
    for (int x = 0; x < allergies.size(); x++) {
        Allergy allergy = allergies.get(x);
        if (x > 0)
            allergyString.append(",");
        allergyString.append(allergy.getDescription());
    }

    String providerName = providerDao.getProvider(demographic.getProviderNo()).getFormattedName();

    Appointment appt = (Appointment)request.getAttribute("appt");
    
    if (appt == null && request.getParameter("appointmentNo") != null) {
        try {
            appt = appointmentDao.find(Integer.parseInt(request.getParameter("appointmentNo")));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid appointment number: " + request.getParameter("appointmentNo"));
        }
    }
    
    boolean hasAppointment = (appt != null);
    
    if (false) { 
        out.println("<!-- DEBUG: appt=" + appt + " -->");
        out.println("<!-- DEBUG: hasAppointment=" + hasAppointment + " -->");
    }

    SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm");

    //get a few things we need.
    //family doc
    //referring doc
    //allergies
    //clinic info


    // FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
    //java.util.Properties props = rec.getFormRecord(demoNo, formId);

    //FrmData fd = new FrmData();    String resource = fd.getResource(); resource = resource + "ob/riskinfo/";

    //get project_home
    String project_home = request.getContextPath().substring(1);
%>
<%
    boolean bView = false;
    if (request.getParameter("view") != null && request.getParameter("view").equals("1")) bView = true;
%>
<html>
    <head>
    <link rel="icon" href="<carlos:encode value="${pageContext.request.contextPath}/images/favicon.ico" context="htmlAttribute"/>"/>
        <script type="text/javascript" src="<carlos:encode value='<%= request.getContextPath() + "/js/global.js" %>' context="htmlAttribute"/>"></script>
        <title><fmt:message key='form.patientEncounterWorksheet.title'/></title>
        <base href="<carlos:encode value='<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>' context="htmlAttribute"/>">
        <link rel="stylesheet" type="text/css" media="all" href="<carlos:encode value='<%= request.getContextPath() + "/share/css/extractedFromPages.css" %>' context="htmlAttribute"/>"/>
    </head>


    <script type="text/javascript" src="formScripts.js">
    </script>


    <body bgproperties="fixed" topmargin="0" leftmargin="0" rightmargin="0">


    <h4 style="font-weight:bold;font-size:15px;text-align:center"><fmt:message key='form.patientEncounterWorksheet.title'/></h4>

    <div align="center">
        <form action="<carlos:encode value='<%= request.getContextPath() + "/form/createpdf" %>' context="htmlAttribute"/>" method="POST">
            <input type="hidden" name="demographic_no" value="<carlos:encode value='<%= StringUtils.noNull(request.getParameter("demographic_no")) %>' context="htmlAttribute"/>"/>
            <input type="hidden" name="form_id" value="<carlos:encode value='<%= StringUtils.noNull(request.getParameter("form_id")) %>' context="htmlAttribute"/>"/>
            <input type="hidden" name="__title" value="PatientEcounterWorksheet"/>
            <input type="hidden" name="__cfgfile" value="patientEncounterWorksheetCfg"/>
            <input type="hidden" name="__template" value="patientEncounterWorksheet"/>

            <table border="1" cellspacing="1" cellpadding="1" width="90%">

                <tr>
                    <td valign="top" width="50%">
                        <table border="0" cellspacing="2" cellpadding="2">
                            <input type="hidden" name="clinic_name" value="<carlos:encode value='<%= clinic.getClinicName() %>' context="htmlAttribute"/>"/>
                            <input type="hidden" name="clinic_address1" value="<carlos:encode value='<%= clinic.getClinicAddress() %>' context="htmlAttribute"/>"/>
                            <input type="hidden" name="clinic_address2"
                                   value="<carlos:encode value='<%= clinic.getClinicCity() + ", " + clinic.getClinicProvince() + ", " + clinic.getClinicPostal() %>' context="htmlAttribute"/>"/>
                            <input type="hidden" name="clinic_phone" value="<carlos:encode value='<%= clinic.getClinicPhone() %>' context="htmlAttribute"/>"/>
                            <input type="hidden" name="clinic_fax" value="<carlos:encode value='<%= clinic.getClinicFax() %>' context="htmlAttribute"/>"/>
                            <tr>
                                <td valign="top"><b><fmt:message key='form.patientEncounterWorksheet.office'/></b></td>
                                <td valign="top">
                                    <carlos:encode value='<%= clinic.getClinicName() %>' context="html"/>
                                    <br/>
                                    <carlos:encode value='<%= clinic.getClinicAddress() %>' context="html"/>
                                    <br/>
                                    <carlos:encode value='<%= clinic.getClinicCity() %>' context="html"/>, <carlos:encode value='<%= clinic.getClinicProvince() %>' context="html"/>
                                    , <carlos:encode value='<%= clinic.getClinicPostal() %>' context="html"/>
                                </td>
                            </tr>
                            <tr>
                                <td><fmt:message key='form.patientEncounterWorksheet.phone'/></td>
                                <td><carlos:encode value='<%= clinic.getClinicPhone() %>' context="html"/>
                                </td>
                            </tr>
                            <tr>
                                <td><fmt:message key='form.patientEncounterWorksheet.fax'/></td>
                                <td><carlos:encode value='<%= clinic.getClinicFax() %>' context="html"/>
                                </td>
                            </tr>
                        </table>
                    </td>
                    <td valign="top" width="50%">
                        <table border="0" cellspacing="2" cellpadding="2">
                            <input type="hidden" name="demo_name"
                                   value="<carlos:encode value='<%= demographic.getFormattedName() + " (" + demographic.getSex().toUpperCase()  + ")" %>' context="htmlAttribute"/>"/>
                            <input type="hidden" name="demo_address1" value="<carlos:encode value='<%= demographic.getAddress() %>' context="htmlAttribute"/>"/>
                            <input type="hidden" name="demo_address2"
                                   value="<carlos:encode value='<%= demographic.getCity() + ", " + demographic.getProvince() + ", " + demographic.getPostal() %>' context="htmlAttribute"/>"/>
                            <input type="hidden" name="demo_id" value="<carlos:encode value='<%= String.valueOf(demographic.getDemographicNo()) %>' context="htmlAttribute"/>"/>
                            <input type="hidden" name="demo_bday"
                                   value="<carlos:encode value='<%= demographic.getBirthDayAsString() + " (" + demographic.getAgeInYears() + ")" %>' context="htmlAttribute"/>"/>
                            <input type="hidden" name="demo_hin"
                                   value="<carlos:encode value='<%= demographic.getHin() + " (" + demographic.getHcType() + ")" %>' context="htmlAttribute"/>"/>
                            <tr>
                                <td valign="top"><b><fmt:message key='form.patientEncounterWorksheet.patient'/></b></td>
                                <td>
                                    <b><carlos:encode value='<%= demographic.getFormattedName() %>' context="html"/>
                                    </b> (<carlos:encode value='<%= demographic.getSex().toUpperCase() %>' context="html"/>)<br/>
                                    <carlos:encode value='<%= demographic.getAddress() %>' context="html"/><br/>
                                    <carlos:encode value='<%= demographic.getCity() %>' context="html"/>, <carlos:encode value='<%= demographic.getProvince() %>' context="html"/>
                                    , <carlos:encode value='<%= demographic.getPostal() %>' context="html"/>
                                </td>
                            </tr>
                            <tr>
                                <td><fmt:message key='form.patientEncounterWorksheet.patientId'/></td>
                                <td><carlos:encode value='<%= String.valueOf(demographic.getDemographicNo()) %>' context="html"/>
                                </td>
                            </tr>
                            <tr>
                                <td><fmt:message key='form.patientEncounterWorksheet.dob'/></td>
                                <td><carlos:encode value='<%= demographic.getBirthDayAsString() %>' context="html"/>(<carlos:encode value='<%= String.valueOf(demographic.getAgeInYears()) %>' context="html"/>)</td>
                            </tr>
                            <tr>
                                <td><fmt:message key='form.patientEncounterWorksheet.hcNumber'/></td>
                                <td><carlos:encode value='<%= demographic.getHin() %>' context="html"/> (<carlos:encode value='<%= demographic.getHcType() %>' context="html"/>)</td>
                            </tr>
                        </table>
                    </td>
                </tr>


                <tr>
                    <td valign="top" width="50%">
                        <table border="0" cellspacing="2" cellpadding="2">
                            <input type="hidden" name="mrp_provider" value="<carlos:encode value='<%= providerName %>' context="htmlAttribute"/>"/>
                            <input type="hidden" name="fam_provider" value="test,test"/>
                            <input type="hidden" name="ref_provider" value="test,test"/>

                            <tr>
                                <td><fmt:message key='form.patientEncounterWorksheet.provider'/></td>
                                <td><carlos:encode value='<%= providerName %>' context="html"/>
                                </td>
                            </tr>
                            <tr>
                                <td><fmt:message key='form.patientEncounterWorksheet.familyDoctor'/></td>
                                <td>Smith, John</td>
                            </tr>
                            <tr>
                                <td><fmt:message key='form.patientEncounterWorksheet.referringDoctor'/></td>
                                <td>Smith, John</td>
                            </tr>
                        </table>
                    </td>
                    <td valign="top" width="50%">
                        <table border="0" cellspacing="2" cellpadding="2">
                            <% if (hasAppointment) { %>
                                <input type="hidden" name="appt_date" 
                                       value="<carlos:encode value='<%= dateFormatter.format(appt.getAppointmentDate()) + " " + timeFormatter.format(appt.getStartTime()) %>' context="htmlAttribute"/>"/>
                                <input type="hidden" name="appt_type" value="<carlos:encode value='<%= appt.getType() %>' context="htmlAttribute"/>"/>
                                <input type="hidden" name="appt_reason" value="<carlos:encode value='<%= appt.getReason() %>' context="htmlAttribute"/>"/>
                            <% } %>
                        
                            <tr>
                                <td><fmt:message key='form.patientEncounterWorksheet.apptDate'/></td>
                                <td>
                                    <% if (hasAppointment) { %>
                                        <carlos:encode value='<%= dateFormatter.format(appt.getAppointmentDate()) %>' context="html"/>&nbsp;<carlos:encode value='<%= timeFormatter.format(appt.getStartTime()) %>' context="html"/>
                                    <% } else { %>
                                        N/A
                                    <% } %>
                                </td>
                            </tr>
                            <tr>
                                <td><fmt:message key='form.patientEncounterWorksheet.apptType'/></td>
                                <td>
                                    <% if (hasAppointment) { %>
                                        <carlos:encode value='<%= appt.getType() %>' context="html"/>
                                    <% } else { %>
                                        N/A
                                    <% } %>
                                </td>
                            </tr>
                            <tr>
                                <td><fmt:message key='form.patientEncounterWorksheet.reason'/></td>
                                <td>
                                    <% if (hasAppointment) { %>
                                        <carlos:encode value='<%= appt.getReason() %>' context="html"/>
                                    <% } else { %>
                                        N/A
                                    <% } %>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>

                <tr>
                    <input type="hidden" name="allergies" value="<carlos:encode value='<%= allergyString.toString() %>' context="htmlAttribute"/>"/>
                    <td colspan="2">
                        <fmt:message key='form.patientEncounterWorksheet.allergies'/><br/>
                        <carlos:encode value='<%= allergyString.toString() %>' context="html"/>
                    </td>
                </tr>

                <tr>

                    <td colspan="2">
                        <fmt:message key='form.patientEncounterWorksheet.encounterNotes'/><br/>
                        <textarea cols="138" rows="50" name="encounter_notes"></textarea>
                    </td>
                </tr>

                <tr>
                    <td colspan="2">
                        <table border="0" cellspacing="2" cellpadding="2">

                            <tr>
                                <td><fmt:message key='form.patientEncounterWorksheet.diagnosis'/></td>
                                <td><input name="diagnosis" type="text" value=""/>
                                <td>
                            </tr>
                            <tr>
                                <td><fmt:message key='form.patientEncounterWorksheet.signature'/></td>
                                <td><input name="signature" type="text" value=""/></td>
                            </tr>
                            <tr>
                                <td>&nbsp;</td>
                                <td><fmt:message key='form.patientEncounterWorksheet.doctorPrefix'/> <carlos:encode value='<%= loggedInInfo.getLoggedInProvider().getFormattedName() %>' context="html"/>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>


                <tr>
                    <td valign="top" colspan="2">
                        <table class="Head" class="hidePrint" height="5%" border="0">
                            <tr>
                                <td align="left">
                                    <input type="button" value="<fmt:message key="global.btnExit"/>" onclick="javascript:return onExit();"/>
                                    <input type="submit" value="<fmt:message key='global.btnPrint'/>"/></td>
                    </td>
                </tr>

            </table>
            </td>
            </tr>
            </table>
        </form>
    </div>

    </body>
</html>
