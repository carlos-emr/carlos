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

--%>
<%@page import="java.nio.charset.StandardCharsets" %>
<%@page import="java.text.SimpleDateFormat" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page
        import="java.util.*, java.net.URLEncoder, ca.openosp.openo.db.*, ca.openosp.MyDateFormat, ca.openosp.openo.waitinglist.WaitingList, ca.openosp.openo.commn.OtherIdManager" %>
<%@ page import="ca.openosp.openo.log.*" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>

<%@page import="ca.openosp.openo.PMmodule.service.ProgramManager" %>
<%@page import="ca.openosp.openo.PMmodule.service.AdmissionManager" %>

<%@page import="ca.openosp.openo.managers.PatientConsentManager" %>
<%@page import="ca.openosp.OscarProperties" %>
<%@page import="ca.openosp.openo.utility.MiscUtils" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.log.LogAction" %>
<%@ page import="ca.openosp.openo.db.DBPreparedHandlerParam" %>
<%@ page import="ca.openosp.openo.commn.model.*" %>
<%@ page import="ca.openosp.openo.commn.dao.*" %>
<%@ page import="ca.openosp.openo.PMmodule.model.Program" %>
<%@ page import="ca.openosp.openo.PMmodule.dao.ProgramDao" %>
<%!
    java.util.Properties oscarVariables = OscarProperties.getInstance();

    //	AdmissionDao admissionDao = (AdmissionDao)SpringUtils.getBean(AdmissionDao.class);
    ProgramManager pm = SpringUtils.getBean(ProgramManager.class);
    AdmissionManager am = SpringUtils.getBean(AdmissionManager.class);
    WaitingListDao waitingListDao = (WaitingListDao) SpringUtils.getBean(WaitingListDao.class);
    DemographicExtDao demographicExtDao = SpringUtils.getBean(DemographicExtDao.class);
    DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    DemographicCustDao demographicCustDao = (DemographicCustDao) SpringUtils.getBean(DemographicCustDao.class);

//	ProgramDao programDao = (ProgramDao)SpringUtils.getBean(ProgramDao.class);

    DemographicExtArchiveDao demographicExtArchiveDao = SpringUtils.getBean(DemographicExtArchiveDao.class);
    DemographicArchiveDao demographicArchiveDao = (DemographicArchiveDao) SpringUtils.getBean(DemographicArchiveDao.class);

%>

<html>
    <script src="${pageContext.request.contextPath}/csrfguard"></script>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css"/>
        <script LANGUAGE="JavaScript">
            function start() {
                this.focus();
                this.resizeTo(1000, 700);
            }

            function closeit() {
                //parent.refresh();
                close();
            }
        </script>
    </head>

    <body onload="start()" bgproperties="fixed" topmargin="0" leftmargin="0" rightmargin="0">
    <div class="container">
        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr bgcolor="#486ebd">
                <th align="CENTER"><font face="Helvetica" color="#FFFFFF">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddarecord.title"/></font></th>
            </tr>
        </table>
        <form method="post" name="addappt">
            <%
                LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

                //If this is from adding appointment screen, then back to there
                String fromAppt = request.getParameter("fromAppt");
                String originalPage2 = request.getParameter("originalPage");
                String provider_no2 = request.getParameter("provider_no");
                String bFirstDisp2 = request.getParameter("bFirstDisp");
                String year2 = request.getParameter("year");
                String month2 = request.getParameter("month");
                String day2 = request.getParameter("day");
                String start_time2 = request.getParameter("start_time");
                String end_time2 = request.getParameter("end_time");
                String duration2 = request.getParameter("duration");

                String dem = null;
                String year, month, day;
                String curUser_no = (String) session.getAttribute("user");

                DBPreparedHandlerParam[] param = new DBPreparedHandlerParam[34];

                Demographic demographic = new Demographic();
                demographic.setLastName(request.getParameter("last_name").trim());
                demographic.setFirstName(request.getParameter("first_name").trim());
                demographic.setMiddleNames(request.getParameter("middleNames").trim());
                demographic.setAlias(request.getParameter("nameUsed"));
                demographic.setPrefName(request.getParameter("nameUsed"));
                demographic.setAddress(request.getParameter("address"));
                demographic.setCity(request.getParameter("city"));
                if (request.getParameter("province") != null) {
                    demographic.setProvince(request.getParameter("province"));
                } else {
                    demographic.setProvince("");
                }
                demographic.setPostal(request.getParameter("postal"));
                demographic.setResidentialAddress(request.getParameter("residentialAddress"));
                demographic.setResidentialCity(request.getParameter("residentialCity"));
                if (request.getParameter("residentialProvince") != null) {
                    demographic.setResidentialProvince(request.getParameter("residentialProvince"));
                } else {
                    demographic.setResidentialProvince("");
                }
                demographic.setResidentialPostal(request.getParameter("residentialPostal"));
                demographic.setPhone(request.getParameter("phone"));
                demographic.setPhone2(request.getParameter("phone2"));
                demographic.setEmail(request.getParameter("email"));
                demographic.setYearOfBirth(request.getParameter("year_of_birth"));
                demographic.setMonthOfBirth(request.getParameter("month_of_birth") != null && request.getParameter("month_of_birth").length() == 1 ? "0" + request.getParameter("month_of_birth") : request.getParameter("month_of_birth"));
                demographic.setDateOfBirth(request.getParameter("date_of_birth") != null && request.getParameter("date_of_birth").length() == 1 ? "0" + request.getParameter("date_of_birth") : request.getParameter("date_of_birth"));
                demographic.setHin(request.getParameter("hin"));
                demographic.setVer(request.getParameter("ver"));
                demographic.setRosterStatus(request.getParameter("roster_status"));
                demographic.setRosterEnrolledTo(request.getParameter("roster_enrolled_to"));
                demographic.setPatientStatus(request.getParameter("patient_status"));
                demographic.setDateJoined(MyDateFormat.getSysDate(request.getParameter("date_joined_year") + "-" + request.getParameter("date_joined_month") + "-" + request.getParameter("date_joined_date")));
                demographic.setChartNo(request.getParameter("chart_no"));
                demographic.setProviderNo(request.getParameter("staff"));
                demographic.setSex(request.getParameter("sex"));
                demographic.setPronoun(request.getParameter("pronouns"));
                demographic.setGender(request.getParameter("gender"));

                year = StringUtils.trimToNull(request.getParameter("end_date_year"));
                month = StringUtils.trimToNull(request.getParameter("end_date_month"));
                day = StringUtils.trimToNull(request.getParameter("end_date_date"));
                if (year != null && month != null && day != null) {
                    demographic.setEndDate(MyDateFormat.getSysDate(year + "-" + month + "-" + day));
                } else {
                    demographic.setEndDate(null);
                }

                year = StringUtils.trimToNull(request.getParameter("eff_date_year"));
                month = StringUtils.trimToNull(request.getParameter("eff_date_month"));
                day = StringUtils.trimToNull(request.getParameter("eff_date_date"));
                if (year != null && month != null && day != null) {
                    demographic.setEffDate(MyDateFormat.getSysDate(year + "-" + month + "-" + day));
                } else {
                    demographic.setEffDate(null);
                }

                demographic.setPcnIndicator(request.getParameter("pcn_indicator"));
                demographic.setHcType(request.getParameter("hc_type"));

                year = StringUtils.trimToNull(request.getParameter("roster_date_year"));
                month = StringUtils.trimToNull(request.getParameter("roster_date_month"));
                day = StringUtils.trimToNull(request.getParameter("roster_date_date"));
                if (year != null && month != null && day != null) {
                    demographic.setRosterDate(MyDateFormat.getSysDate(year + "-" + month + "-" + day));
                } else {
                    demographic.setRosterDate(null);
                }

                year = StringUtils.trimToNull(request.getParameter("hc_renew_date_year"));
                month = StringUtils.trimToNull(request.getParameter("hc_renew_date_month"));
                day = StringUtils.trimToNull(request.getParameter("hc_renew_date_date"));
                if (year != null && month != null && day != null) {
                    demographic.setHcRenewDate(MyDateFormat.getSysDate(year + "-" + month + "-" + day));
                } else {
                    demographic.setHcRenewDate(null);
                }

                demographic.setFamilyDoctor("<rdohip>" + request.getParameter("r_doctor_ohip") + "</rdohip>" + "<rd>" + request.getParameter("r_doctor") + "</rd>" + (request.getParameter("family_doc") != null ? ("<family_doc>" + request.getParameter("family_doc") + "</family_doc>") : ""));
                demographic.setCountryOfOrigin(request.getParameter("countryOfOrigin"));
                demographic.setNewsletter(request.getParameter("newsletter"));
                demographic.setSin(request.getParameter("sin"));
                demographic.setTitle(request.getParameter("title"));
                demographic.setOfficialLanguage(request.getParameter("official_lang"));
                demographic.setSpokenLanguage(request.getParameter("spoken_lang"));
                demographic.setLastUpdateUser(curUser_no);
                demographic.setLastUpdateDate(new java.util.Date());

                if (request.getParameter("patient_status_date") != null) {
                    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
                    try {
                        demographic.setPatientStatusDate(fmt.parse(request.getParameter("patient_status_date")));
                    } catch (Exception e) {
                        demographic.setPatientStatusDate(new java.util.Date());
                    }
                } else {
                    demographic.setPatientStatusDate(new java.util.Date());
                }


                StringBuilder bufChart = null, bufName = null, bufNo = null, bufDoctorNo = null;
                // add checking hin duplicated record, if there is a HIN number
                // added check to see if patient has a bc health card and has a version code of 66, in this case you are aloud to have dup hin
                boolean hinDupCheckException = false;
                String hcType = request.getParameter("hc_type");
                String ver = request.getParameter("ver");
                if (hcType != null && ver != null && hcType.equals("BC") && ver.equals("66")) {
                    hinDupCheckException = true;
                }

                if (request.getParameter("hin") != null && request.getParameter("hin").length() > 5 && !hinDupCheckException) {
                    String paramNameHin = new String();
                    paramNameHin = request.getParameter("hin").trim();
                    List<Demographic> demographics = demographicDao.searchByHealthCard(paramNameHin);
                    if (demographics.size() > 0) {
            %>
            ***<font color='red'><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddarecord.msgDuplicatedHIN"/></font>***<br><br>
            <a href=# onClick="history.go(-1);return false;"><b>&lt;-<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnBack"/></b></a>
            <%
                        return;
                    }
                }


                bufName = new StringBuilder(request.getParameter("last_name") + "," + request.getParameter("first_name"));
                bufNo = new StringBuilder((StringUtils.trimToEmpty("demographic_no")));
                bufChart = new StringBuilder(StringUtils.trimToEmpty("chart_no"));
                bufDoctorNo = new StringBuilder(StringUtils.trimToEmpty("provider_no"));

                demographicDao.save(demographic);




                //add democust record for alert
                String[] param2 = new String[6];
                param2[0] = demographic.getDemographicNo().toString();

                DemographicCust demographicCust = new DemographicCust();
                demographicCust.setResident(request.getParameter("cust2"));
                demographicCust.setNurse(request.getParameter("cust1"));
                demographicCust.setAlert(request.getParameter("cust3"));
                demographicCust.setMidwife(request.getParameter("cust4"));
                demographicCust.setNotes("<unotes>" + request.getParameter("content") + "</unotes>");
                demographicCust.setId(demographic.getDemographicNo());
                demographicCustDao.persist(demographicCust);
                int rowsAffected = 1;

                dem = demographic.getDemographicNo().toString();

                if (OscarProperties.getInstance().getBooleanProperty("USE_NEW_PATIENT_CONSENT_MODULE", "true")) {
                    // Retrieve and set patient consents.
                    PatientConsentManager patientConsentManager = SpringUtils.getBean(PatientConsentManager.class);
                    List<ConsentType> consentTypes = patientConsentManager.getActiveConsentTypes();
                    boolean explicitConsent = Boolean.TRUE;

                    for (ConsentType consentType : consentTypes) {
                        String type = consentType.getType();
                        String consentRecord = request.getParameter(type);

                        if (consentRecord != null) {
                            //either opt-in or opt-out is selected
                            boolean optOut = Integer.parseInt(consentRecord) == 1;
                            patientConsentManager.addEditConsentRecord(loggedInInfo, demographic.getDemographicNo(), consentType.getId(), explicitConsent, optOut);
                        }

                    }
                }

                String proNo = (String) session.getValue("user");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "hPhoneExt", request.getParameter("hPhoneExt"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "wPhoneExt", request.getParameter("wPhoneExt"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "demo_cell", request.getParameter("demo_cell"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "aboriginal", request.getParameter("aboriginal"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "cytolNum", request.getParameter("cytolNum"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "ethnicity", request.getParameter("ethnicity"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "area", request.getParameter("area"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "statusNum", request.getParameter("statusNum"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "fNationCom", request.getParameter("fNationCom"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "given_consent", request.getParameter("given_consent"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "rxInteractionWarningLevel", request.getParameter("rxInteractionWarningLevel"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "primaryEMR", request.getParameter("primaryEMR"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "phoneComment", request.getParameter("phoneComment"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "usSigned", request.getParameter("usSigned"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "privacyConsent", request.getParameter("privacyConsent"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "informedConsent", request.getParameter("informedConsent"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "HasPrimaryCarePhysician", request.getParameter("HasPrimaryCarePhysician"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "EmploymentStatus", request.getParameter("EmploymentStatus"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "PHU", request.getParameter("PHU"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "fNationFamilyNumber", request.getParameter("fNationFamilyNumber"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "fNationFamilyPosition", request.getParameter("fNationFamilyPosition"), "");
                demographicExtDao.addKey(proNo, demographic.getDemographicNo(), "labelfNationCom", request.getParameter("labelfNationCom"), "");
                //for the IBD clinic
                OtherIdManager.saveIdDemographic(dem, "meditech_id", request.getParameter("meditech_id"));

                // customized key
                if (oscarVariables.getProperty("demographicExt") != null) {
                    String[] propDemoExt = oscarVariables.getProperty("demographicExt", "").split("\\|");
                    for (int k = 0; k < propDemoExt.length; k++) {
                        demographicExtDao.addKey(proNo, demographic.getDemographicNo(), propDemoExt[k].replace(' ', '_'), request.getParameter(propDemoExt[k].replace(' ', '_')), "");
                    }
                }
                // customized key

                // add log
                String ip = request.getRemoteAddr();
                LogAction.addLog(curUser_no, "add", "demographic", param2[0], ip, param2[0]);

                //archive the original too
                Long archiveId = demographicArchiveDao.archiveRecord(demographicDao.getDemographic(dem));
                List<DemographicExt> extensions = demographicExtDao.getDemographicExtByDemographicNo(Integer.parseInt(dem));
                for (DemographicExt extension : extensions) {
                    DemographicExtArchive archive = new DemographicExtArchive(extension);
                    archive.setArchiveId(archiveId);
                    archive.setValue(request.getParameter(archive.getKey()));
                    demographicExtArchiveDao.saveEntity(archive);
                }

                //add to waiting list if the waiting_list parameter in the property file is set to true

                WaitingList wL = WaitingList.getInstance();
                if (wL.getFound() && OscarProperties.getInstance().getBooleanProperty("DEMOGRAPHIC_WAITING_LIST", "true")) {

                    String[] paramWLPosition = new String[1];
                    paramWLPosition[0] = request.getParameter("list_id");
                    if (paramWLPosition[0].compareTo("") != 0) {

                        List<Long> positionList = new ArrayList<Long>();
                        List<ca.openosp.openo.commn.model.WaitingList> waitingListList = waitingListDao.findByWaitingListId(new Integer(1));

                        if (waitingListList != null) {

                            for (ca.openosp.openo.commn.model.WaitingList waitingList : waitingListList) {
                                positionList.add(waitingList.getPosition());
                            }
                            Long maxPosition = 0L;
                            if (positionList.size() > 0) {
                                maxPosition = Collections.max(positionList);
                            }

                            String listId = request.getParameter("list_id");
                            if (listId != null && !listId.equals("") && !listId.equals("0")) {
                                ca.openosp.openo.commn.model.WaitingList waitingList = new ca.openosp.openo.commn.model.WaitingList();
                                waitingList.setListId(Integer.parseInt(request.getParameter("list_id")));
                                waitingList.setDemographicNo(demographic.getDemographicNo());
                                waitingList.setNote(request.getParameter("waiting_list_note"));
                                waitingList.setPosition(maxPosition.longValue() + 1);
                                waitingList.setOnListSince(MyDateFormat.getSysDate(request.getParameter("waiting_list_referral_date")));
                                waitingList.setIsHistory("N");
                                waitingList.setOnListSince(new java.util.Date());
                                waitingListDao.persist(waitingList);
                            }
                        }
                    }


                } //end of waitingl list

                // Ensure program admission - default to OSCAR if no program selected
                String rps = request.getParameter("rps");
                if (rps == null || rps.trim().isEmpty()) {
                    // No program selected, try to use OSCAR program
                    ProgramDao programDao = (ProgramDao) SpringUtils.getBean(ProgramDao.class);
                    Program oscarProgram = programDao.getProgramByName("OSCAR");
                    if (oscarProgram != null) {
                        rps = String.valueOf(oscarProgram.getId());
                    }
                }
                
                // Create admission if we have a valid program
                if (rps != null && !rps.trim().isEmpty()) {
                    try {
                        Integer programId = Integer.parseInt(rps);
                        Admission admission = new Admission();
                        admission.setClientId(demographic.getDemographicNo());
                        admission.setProgramId(programId);
                        admission.setProviderNo(curUser_no);
                        admission.setAdmissionDate(new java.util.Date());
                        admission.setAdmissionStatus("current");
                        admission.setAdmissionFromTransfer(false);
                        
                        AdmissionDao admissionDao = (AdmissionDao) SpringUtils.getBean(AdmissionDao.class);
                        admissionDao.saveAdmission(admission);
                    } catch (Exception e) {
                        // Log but don't fail demographic creation
                        MiscUtils.getLogger().warn("Failed to create program admission for demographic " + dem, e);
                    }
                }

                //if(request.getParameter("fromAppt")!=null && request.getParameter("provider_no").equals("1")) {
                if (start_time2 != null && !start_time2.equals("null")) {
            %>
            <script language="JavaScript">
                document.addappt.action = "<%= request.getContextPath() %>/appointment/addappointment.jsp?user_id=<%=Encode.forJavaScript(request.getParameter("creator"))%>&provider_no=<%=provider_no2%>&bFirstDisp=<%=bFirstDisp2%>&appointment_date=<%=request.getParameter("appointment_date")%>&year=<%=year2%>&month=<%=month2%>&day=<%=day2%>&start_time=<%=start_time2%>&end_time=<%=end_time2%>&duration=<%=duration2%>&name=<%=URLEncoder.encode(bufName.toString(), StandardCharsets.UTF_8)%>&chart_no=<%=URLEncoder.encode(bufChart.toString(), StandardCharsets.UTF_8)%>&bFirstDisp=false&demographic_no=<%=dem.toString()%>&messageID=<%=request.getParameter("messageId")%>&doctor_no=<%=Encode.forJavaScript(bufDoctorNo.toString())%>&notes=<%=Encode.forJavaScript(request.getParameter("notes"))%>&reason=<%=Encode.forJavaScript(request.getParameter("reason"))%>&location=<%=Encode.forJavaScript(request.getParameter("location"))%>&resources=<%=request.getParameter("resources")%>&type=<%=request.getParameter("type")%>&style=<%=request.getParameter("style")%>&billing=<%=request.getParameter("billing")%>&status=<%=Encode.forJavaScript(request.getParameter("status"))%>&createdatetime=<%=request.getParameter("createdatetime")%>&creator=<%=Encode.forJavaScript(request.getParameter("creator"))%>&remarks=<%=Encode.forJavaScript(request.getParameter("remarks"))%>";
                document.addappt.submit();
            </SCRIPT>
            <% } %>
        </form>


        <p>
        <h2><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddarecord.msgSuccessful"/></h2>

        <a href="demographiccontrol.jsp?demographic_no=<%=dem%>&displaymode=edit&dboperation=search_detail"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddarecord.goToRecord"/></a>

        <caisi:isModuleLoad moduleName="caisi">
            <br/>
            <a href="<%= request.getContextPath() %>/PMmodule/ClientManager.do?id=<%=dem%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddarecord.goToCaisiRecord"/> (<a href="#"
                                                                                  onclick="popup(700,1027,'demographiccontrol.jsp?demographic_no=<%=dem%>&displaymode=edit&dboperation=search_detail')">New
                Window</a>)</a>
        </caisi:isModuleLoad>


        <caisi:isModuleLoad moduleName="caisi">
            <br/>
            <a href="<%= request.getContextPath() %>/PMmodule/ClientManager.do?id=<%=dem%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddarecord.goToCaisiRecord"/></a>
        </caisi:isModuleLoad>


        </p>
        <%@ include file="footer.jsp" %>
    </div>
    </body>
</html>
