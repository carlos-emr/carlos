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
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.dao.DrugDao" %>
<%@page import="ca.openosp.openo.commn.dao.PartialDateDao" %>
<%@page import="ca.openosp.openo.commn.model.PartialDate" %>
<%@page import="java.util.List" %>
<%@page import="ca.openosp.openo.commn.model.Drug" %>
<%@page import="ca.openosp.openo.prescript.data.RxPrescriptionData" %>
<%@page import="ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager" %>
<%@page import="ca.openosp.openo.caisi_integrator.ws.DemographicWs" %>
<%@page import="ca.openosp.openo.utility.SessionConstants" %>
<%@page import="ca.openosp.openo.rx.StaticScriptBean" %>
<%@page import="ca.openosp.openo.prescript.util.RxUtil" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@page import="java.util.ArrayList" %>
<%@ page import="ca.openosp.openo.services.security.SecurityManager" %>
<%@ page import="ca.openosp.openo.prescript.pageUtil.RxSessionBean" %>
<%@ page import="ca.openosp.openo.casemgmt.model.CaseManagementNoteLink" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_rx" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_rx");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<html>
    <head>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="StaticScript.title"/></title>

        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

        <%
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
            RxSessionBean rxBean = null;
        %>
        <%
            if (request.getParameter("demographicNo") != null) {
                rxBean = new RxSessionBean();

                rxBean.setProviderNo((String) session.getAttribute("user"));
                rxBean.setDemographicNo(Integer.parseInt(request.getParameter("demographicNo")));

                request.getSession().setAttribute("RxSessionBean", rxBean);
            }
        %>

        <c:if test="${sessionScope.RxSessionBean == null}">
            <c:redirect url="error.html"/>
        </c:if>

        <c:if test="${not empty sessionScope.RxSessionBean}">
            <c:set var="bean" value="${sessionScope.RxSessionBean}" scope="page"/>
            <c:if test="${bean.valid == false}">
                <c:redirect url="error.html"/>
            </c:if>
        </c:if>
        <c:set var="ctx" value="${pageContext.request.contextPath}"/>
        <%
            if (rxBean == null) {
                rxBean = (RxSessionBean) pageContext.findAttribute("bean");
            }
            SecurityManager securityManager = new SecurityManager();
        %>


        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/oscarRx/styles.css">

        <script type="text/javascript">
            function ShowDrugInfo(gn) {
                window.open("<%= request.getContextPath() %>/oscarRx/drugInfo.do?GN=" + encodeURIComponent(gn), "_blank",
                    "location=no, menubar=no, toolbar=no, scrollbars=yes, status=yes, resizable=yes");
            }
        </script>

        <%
            if (session.getAttribute("user") == null)
                response.sendRedirect(request.getContextPath() + "/logout.jsp");
            String curUser_no = (String) session.getAttribute("user");
            String regionalIdentifier = request.getParameter("regionalIdentifier");
            String cn = request.getParameter("cn");
            String bn = request.getParameter("bn");
            Integer currentDemographicNo = rxBean.getDemographicNo();
            String atc = request.getParameter("atc");

            ArrayList<StaticScriptBean.DrugDisplayData> drugs = StaticScriptBean.getDrugList(loggedInInfo, currentDemographicNo, regionalIdentifier, cn, bn, atc);

            RxPatientData.Patient patient = RxPatientData.getPatient(loggedInInfo, currentDemographicNo);
            String annotation_display = CaseManagementNoteLink.DISP_PRESCRIP;
        %>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/prototype.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/scriptaculous.js"></script>
        <script type="text/javascript" src="<c:out value="${ctx}/share/javascript/Oscar.js"/>"></script>

        <script language="javascript">

            function addFavorite2(drugId, brandName) {
                var favoriteName = window.prompt('Please enter a name for the Favorite:', brandName);

                if (favoriteName !== null && favoriteName.length > 0) {
                    var url = '<%=request.getContextPath()%>' + "/oscarRx/addFavorite2.do?parameterValue=addFav2";
                    oscarLog(url);
                    favoriteName = encodeURIComponent(favoriteName);
                    var data = "drugId=" + encodeURIComponent(drugId) + "&favoriteName=" + favoriteName;
                    new Ajax.Request(url, {
                        method: 'get', parameters: data, onSuccess: function (transport) {
                            window.location.href = "<c:out value="${ctx}"/>" + "/oscarRx/StaticScript2.jsp?regionalIdentifier=" + '<%=regionalIdentifier%>' + "&cn=" + '<%=cn%>';
                        }
                    });
                }
            }

            //represcribe a drug
            function reRxDrugSearch3(reRxDrugId) {
                var dataUpdateId = "reRxDrugId=" + encodeURIComponent(reRxDrugId) + "&action=addToReRxDrugIdList&rand=" + Math.floor(Math.random() * 10001);
                var urlUpdateId = "<c:out value="${ctx}"/>" + "/oscarRx/WriteScript.do?parameterValue=updateReRxDrug";
                new Ajax.Request(urlUpdateId, {method: 'get', parameters: dataUpdateId});

                var data = "drugId=" + encodeURIComponent(reRxDrugId);
                var url = "<c:out value="${ctx}"/>" + "/oscarRx/rePrescribe2.do?method=saveReRxDrugIdToStash";
                new Ajax.Request(url, {
                    method: 'post', parameters: data, asynchronous: false, onSuccess: function (transport) {
                        location.href = "<c:out value="${ctx}"/>" + "/oscarRx/SearchDrug3.jsp?";
                    }
                });
            }

        </script>


    </head>

    <body topmargin="0" leftmargin="0" vlink="#0000FF">
    <table border="0" cellpadding="0" cellspacing="0" style="border-collapse: collapse" bordercolor="#111111"
           width="100%" id="AutoNumber1" height="100%">
        <%@ include file="TopLinks.jsp"%><!-- Row One included here-->
        <tr>
            <%@ include file="SideLinksNoEditFavorites2.jsp"%><!-- <td></td>Side Bar File --->
            <td width="100%" style="border-left: 2px solid #A9A9A9;" height="100%" valign="top">
                <table cellpadding="0" cellspacing="2" style="border-collapse: collapse" bordercolor="#111111"
                       width="100%" height="100%">
                    <tr>
                        <td width="0%" valign="top">
                            <div class="DivCCBreadCrumbs"><a href="<%= request.getContextPath() %>/oscarRx/SearchDrug3.jsp"> <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.title"/></a> &gt; <b><fmt:setBundle basename="oscarResources"/><fmt:message key="StaticScript.title"/></b>
                            </div>
                        </td>
                    </tr>
                    <!----Start new rows here-->

                    <tr>
                        <td style="font-size: small;"><br/>
                            <br/>
                            <b>Patient Name:</b> <%=patient.getFirstName()%> <%=patient.getSurname()%> <br/>
                            <br/>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <table cellspacing="10" cellpadding="0">
                                <tr style="height: 20px">
                                    <th align="left"><b>Provider</b></th>
                                    <th align="left"><b>Start Date</b></th>
                                    <th align="left"><b>End Date</b></th>
                                    <th align="left"><b>Written Date</b></th>
                                    <th align="left"><b>Medication Details</b></th>
                                    <th colspan="2"></th>
                                </tr>
                                        <%
						PartialDateDao partialDateDao = (PartialDateDao)SpringUtils.getBean(PartialDateDao.class);
						for (StaticScriptBean.DrugDisplayData drug : drugs)
							{
								String arch="";
								if (drug.isArchived)
								{
									arch="text-decoration: line-through;";
								}
					%>
                                <tr style="height:20px;<%=arch%>">
                                    <td><%=drug.providerName%>
                                    </td>
                                    <td><%
                                        if (!drug.startDate.equals("0001/01/01")) {
                                            out.print(partialDateDao.getDatePartial(drug.startDate, PartialDate.DRUGS, drug.localDrugId, PartialDate.DRUGS_STARTDATE));
							/*
							String startDate = drug.startDate;
		            		PartialDate pd = partialDateDao.getPartialDate(PartialDate.DRUGS , drug.localDrugId, PartialDate.DRUGS_STARTDATE);
		            		if(pd != null) {
		            			startDate = startDate.substring(0,pd.getFormat().length());
		            		}
		            		
							out.print(startDate);
							*/
                                        }
                                    %></td>
                                    <td><%
                                        if (!drug.startDate.equals("0001/01/01")) {
                                            out.print(drug.endDate);
                                        }
                                    %></td>
                                    <td><%
                                        if (!drug.writtenDate.equals("0001/01/01")) {
                                            out.print(partialDateDao.getDatePartial(drug.writtenDate, PartialDate.DRUGS, drug.localDrugId, PartialDate.DRUGS_WRITTENDATE));
                                        }
                                    %></td>
                                    <td>
                                        <%if (drug.localDrugId != null) { %>
                                        <a href="javascript:void(0);"
                                           onclick="popup(600, 425,'<%= request.getContextPath() %>/oscarRx/DisplayRxRecord.jsp?id=<%=drug.localDrugId%>','displayRxWindow')">
                                            <%}%>
                                            <%=drug.prescriptionDetails%>
                                            <%if (drug.localDrugId != null) { %>
                                        </a>
                                        <%}%>

                                        <% if (drug.nonAuthoritative) { %>
                                        &nbsp;<fmt:setBundle basename="oscarResources"/>
                                        <fmt:message key="WriteScript.msgNonAuthoritative"/>
                                        <% } %>

                                        <%
                                            if (drug.pickupDate != null && !drug.pickupDate.equals("") && !drug.pickupDate.equals("0000-00-00")) {
                                        %><br/><fmt:message
                                            key="WriteScript.msgPickUpDate"/>&nbsp;<%=drug.pickupDate%>&nbsp;
                                        <%
                                            if (!((drug.pickupTime).equals("")) && !((drug.pickupTime).equals("12:00 AM"))) {
                                        %> &nbsp;<%=drug.pickupTime%>&nbsp;
                                        <% }
                                        } %>
                                        <%if (drug.eTreatmentType != null && !drug.eTreatmentType.equals("null")) { %>
                                        &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgETreatmentType"/>:

                                        <%if (drug.eTreatmentType.equals("CHRON")) {%>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgETreatment.Continuous"/>
                                        <%} else if (drug.eTreatmentType.equals("ACU")) {%>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgETreatment.Acute"/>
                                        <%} else if (drug.eTreatmentType.equals("ONET")) {%>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgETreatment.OneTime"/>
                                        <%} else if (drug.eTreatmentType.equals("PRNL")) {%>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgETreatment.LongTermPRN"/>
                                        <%} else if (drug.eTreatmentType.equals("PRNS")) {%>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgETreatment.ShortTermPRN"/>
                                        <%
                                                }
                                            }
                                        %>
                                        <%if (drug.rxStatus != null && !drug.rxStatus.equals("null")) { %>
                                        &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgRxStatus"/>: <%=drug.rxStatus%>
                                        <%}%>

                                    </td>
                                            <%
							if (drug.customName==null)
									{
						%> <a href="javascript:ShowDrugInfo('<%=drug.genericName%>');">Info</a> <%
							}
						%>
                        </td>
                        <%if (securityManager.hasWriteAccess("_rx", roleName2$, true)) {%>
                        <td>
                            <%
                                if (drug.isLocal) {
                            %>
                            <input type="button" value="Annotation" title="Annotation" class="ControlPushButton"
                                   onclick="window.open('<%= request.getContextPath() %>/annotation/annotation.jsp?display=<%=annotation_display%>&table_id=<%=drug.localDrugId%>&demo=<%=currentDemographicNo%>','anwin','width=400,height=500');">
                            <%
                                }
                            %>
                        </td>
                        <td>
                            <%
                                if (drug.isLocal) {
                            %>
                                <%--  <form action="">
      <input type="hidden" name="drugList" value="<%=drug.localDrugId.toString()%>" />
      <input type="hidden" name="method" value="represcribe">
                                                  <input type="submit" name="submit" style="width:100px" class="ControlPushButton"  onclick="javascript:reRxDrugSearch3('<%=drug.localDrugId%>');" value="Re-prescribe" />
  </form> --%>
                            <input type="button" align="top" value="Represcribe" style="width: 100px"
                                   class="ControlPushButton"
                                   onclick="javascript:reRxDrugSearch3('<%=drug.localDrugId%>');"/>
                            <input type="button" align="top" value="Add to Favorites" style="width: 100px"
                                   class="ControlPushButton"
                                   onclick="javascript:addFavorite2(<%=drug.localDrugId%>, '<%=Encode.forJavaScript((drug.customName!=null&&(!drug.customName.equalsIgnoreCase("null")))?drug.customName:drug.brandName)%>');"/>


                            <%
                            } else {
                            %>
                            <form action="<%=request.getContextPath()%>/oscarRx/searchDrug.do" method="post">
                                <input type="hidden" name="demographicNo" value="<%=currentDemographicNo%>"/>
                                <%
                                    String searchString = drug.brandName;
                                    if (searchString == null) searchString = drug.customName;
                                    if (searchString == null) searchString = drug.genericName;
                                    if (searchString == null) searchString = drug.prescriptionDetails;
                                %>
                                <input type="hidden" name="searchString" value="<%=searchString%>"/>
                                <input type="submit" class="ControlPushButton" value="Search to Re-prescribe"/>
                            </form>
                            <%
                                }
                            %>
                        </td>
                        <% } %>
                    </tr>
                    <%
                        }
                    %>
                </table>
            </td>
        </tr>
        <tr>
            <td><br/>
                <br/>
                <input type="button" value="Back To Search Drug" class="ControlPushButton"
                       onclick="javascript:window.location.href='<%= request.getContextPath() %>/oscarRx/SearchDrug3.jsp';"/></td>
        </tr>
        <!----End new rows here-->
        <tr height="100%">
            <td></td>
        </tr>
    </table>
    </td>
    </tr>
    <tr>
        <td height="0%" style="border-bottom: 2px solid #A9A9A9; border-top: 2px solid #A9A9A9;"></td>
        <td height="0%" style="border-bottom: 2px solid #A9A9A9; border-top: 2px solid #A9A9A9;"></td>
    </tr>
    <tr>
        <td width="100%" height="0%" colspan="2">&nbsp;</td>
    </tr>
    <tr>
        <td width="100%" height="0%" style="padding: 5" bgcolor="#DCDCDC" colspan="2"></td>
    </tr>
    </table>

    </body>
</html>
