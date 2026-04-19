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
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DrugDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.PartialDateDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.PartialDate" %>
<%@page import="java.util.List" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Drug" %>
<%@page import="io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData" %>
<%@page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%@page import="io.github.carlos_emr.carlos.rx.StaticScriptBean" %>
<%@page import="io.github.carlos_emr.carlos.prescript.util.RxUtil" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@page import="java.util.ArrayList" %>
<%@ page import="io.github.carlos_emr.carlos.services.security.SecurityManager" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_rx" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_rx");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<html>
    <head>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/global.js"></script>
        <title><fmt:message key="StaticScript.title"/></title>

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


        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/rx/styles.css">

        <script type="text/javascript">
            function ShowDrugInfo(gn) {
                window.open("<%= request.getContextPath() %>/rx/drugInfo?GN=" + encodeURIComponent(gn), "_blank",
                    "location=no, menubar=no, toolbar=no, scrollbars=yes, status=yes, resizable=yes");
            }
        </script>

        <%
            if (session.getAttribute("user") == null)
                response.sendRedirect(request.getContextPath() + "/logoutPage");
            String curUser_no = (String) session.getAttribute("user");
            String regionalIdentifier = request.getParameter("regionalIdentifier");
            String cn = request.getParameter("cn");
            String bn = request.getParameter("bn");
            Integer currentDemographicNo = rxBean.getDemographicNo();
            String atc = request.getParameter("atc");

            ArrayList<StaticScriptBean.DrugDisplayData> drugs = StaticScriptBean.getDrugList(loggedInInfo, currentDemographicNo, regionalIdentifier, cn, bn, atc);

            RxPatientData.Patient patient = RxPatientData.getPatient(loggedInInfo, currentDemographicNo);
        %>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/carlos-ajax.js"></script>
        <script type="text/javascript" src="${carlos:forHtmlAttribute(ctx)}/share/javascript/Oscar.js"></script>

        <script language="javascript">
            var csrfEl = document.querySelector('input[name="CSRF-TOKEN"]');
            var csrfToken = csrfEl ? csrfEl.value : '';

            function addFavorite2(drugId, brandName) {
                var favoriteName = window.prompt('Please enter a name for the Favorite:', brandName);

                if (favoriteName !== null && favoriteName.length > 0) {
                    var url = '<%=request.getContextPath()%>' + "/rx/addFavorite2?parameterValue=addFav2";
                    oscarLog(url);
                    favoriteName = encodeURIComponent(favoriteName);
                    var data = "drugId=" + encodeURIComponent(drugId) + "&favoriteName=" + favoriteName;
                    fetch(url, {
                        method: 'POST',
                        headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': csrfToken},
                        credentials: 'same-origin',
                        body: data
                    }).then(function() {
                        <c:set var="__enc_1"><carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(regionalIdentifier) %>' context="uriComponent"/></c:set>
                        <c:set var="__enc_2"><carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(cn) %>' context="uriComponent"/></c:set>
                        window.location.href = "${carlos:forJavaScript(ctx)}" + "/rx/ViewStaticScript2?regionalIdentifier=" + '<carlos:encode value='${__enc_1}' context="javaScriptBlock"/>' + "&cn=" + '<carlos:encode value='${__enc_2}' context="javaScriptBlock"/>';
                    });
                }
            }

            //represcribe a drug
            async function reRxDrugSearch3(reRxDrugId) {
                var dataUpdateId = "reRxDrugId=" + encodeURIComponent(reRxDrugId) + "&action=addToReRxDrugIdList&rand=" + Math.floor(Math.random() * 10001);
                var urlUpdateId = "${carlos:forJavaScript(ctx)}" + "/rx/WriteScript";
                fetch(urlUpdateId, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': csrfToken},
                    credentials: 'same-origin',
                    body: dataUpdateId + "&parameterValue=updateReRxDrug"
                });

                var data = "drugId=" + encodeURIComponent(reRxDrugId);
                var url = "${carlos:forJavaScript(ctx)}" + "/rx/rePrescribe2?method=saveReRxDrugIdToStash";
                await fetch(url, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': csrfToken},
                    credentials: 'same-origin',
                    body: data
                });
                location.href = "${carlos:forJavaScript(ctx)}" + "/rx/searchDrug?";
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
                            <div class="DivCCBreadCrumbs"><a href="<%= request.getContextPath() %>/rx/searchDrug"> <fmt:message key="SearchDrug.title"/></a> &gt; <b><fmt:message key="StaticScript.title"/></b>
                            </div>
                        </td>
                    </tr>
                    <!----Start new rows here-->

                    <tr>
                        <td style="font-size: small;"><br/>
                            <br/>
                            <b>Patient Name:</b> <carlos:encode value='<%= patient.getFirstName() %>' context="html"/> <carlos:encode value='<%= patient.getSurname() %>' context="html"/> <br/>
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
                                    <td><carlos:encode value='<%= drug.providerName %>' context="html"/>
                                    </td>
                                    <td><%
                                        if (!drug.startDate.equals("0001/01/01")) {
                                            out.print(SafeEncode.forHtml(partialDateDao.getDatePartial(drug.startDate, PartialDate.DRUGS, drug.localDrugId, PartialDate.DRUGS_STARTDATE)));
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
                                            out.print(SafeEncode.forHtml(drug.endDate));
                                        }
                                    %></td>
                                    <td><%
                                        if (!drug.writtenDate.equals("0001/01/01")) {
                                            out.print(SafeEncode.forHtml(partialDateDao.getDatePartial(drug.writtenDate, PartialDate.DRUGS, drug.localDrugId, PartialDate.DRUGS_WRITTENDATE)));
                                        }
                                    %></td>
                                    <td>
                                        <%if (drug.localDrugId != null) { %>
                                        <a href="javascript:void(0);"
                                           onclick="popup(600, 425,'<%= request.getContextPath() %>/rx/ViewDisplayRxRecord?id=<carlos:encode value='<%= String.valueOf(drug.localDrugId) %>' context="uriComponent"/>','displayRxWindow')">
                                            <%}%>
                                            <carlos:encode value='<%= drug.prescriptionDetails %>' context="html"/>
                                            <%if (drug.localDrugId != null) { %>
                                        </a>
                                        <%}%>

                                        <% if (drug.nonAuthoritative) { %>
                                        &nbsp;
                                        <fmt:message key="WriteScript.msgNonAuthoritative"/>
                                        <% } %>

                                        <%
                                            if (drug.pickupDate != null && !drug.pickupDate.equals("") && !drug.pickupDate.equals("0000-00-00")) {
                                        %><br/><fmt:message
                                            key="WriteScript.msgPickUpDate"/>&nbsp;<carlos:encode value='<%= drug.pickupDate %>' context="html"/>&nbsp;
                                        <%
                                            if (!((drug.pickupTime).equals("")) && !((drug.pickupTime).equals("12:00 AM"))) {
                                        %> &nbsp;<carlos:encode value='<%= drug.pickupTime %>' context="html"/>&nbsp;
                                        <% }
                                        } %>
                                        <%if (drug.eTreatmentType != null && !drug.eTreatmentType.equals("null")) { %>
                                        &nbsp;<fmt:message key="WriteScript.msgETreatmentType"/>:

                                        <%if (drug.eTreatmentType.equals("CHRON")) {%>
                                        <fmt:message key="WriteScript.msgETreatment.Continuous"/>
                                        <%} else if (drug.eTreatmentType.equals("ACU")) {%>
                                        <fmt:message key="WriteScript.msgETreatment.Acute"/>
                                        <%} else if (drug.eTreatmentType.equals("ONET")) {%>
                                        <fmt:message key="WriteScript.msgETreatment.OneTime"/>
                                        <%} else if (drug.eTreatmentType.equals("PRNL")) {%>
                                        <fmt:message key="WriteScript.msgETreatment.LongTermPRN"/>
                                        <%} else if (drug.eTreatmentType.equals("PRNS")) {%>
                                        <fmt:message key="WriteScript.msgETreatment.ShortTermPRN"/>
                                        <%
                                                }
                                            }
                                        %>
                                        <%if (drug.rxStatus != null && !drug.rxStatus.equals("null")) { %>
                                        &nbsp;<fmt:message key="WriteScript.msgRxStatus"/>: <carlos:encode value='<%= drug.rxStatus %>' context="html"/>
                                        <%}%>

                                    </td>
                                            <%
							if (drug.customName==null)
									{
						%> <a href="javascript:ShowDrugInfo('<carlos:encode value='<%= drug.genericName %>' context="javaScriptAttribute"/>');">Info</a> <%
							}
						%>
                        </td>
                        <%if (securityManager.hasWriteAccess("_rx", roleName2$, true)) {%>
                        <td>
                            <%
                                if (drug.isLocal) {
                            %>
                            <input type="button" align="top" value="Represcribe" style="width: 100px"
                                   class="ControlPushButton"
                                   onclick="javascript:reRxDrugSearch3('<carlos:encode value='<%= String.valueOf(drug.localDrugId) %>' context="javaScriptAttribute"/>');"/>
                            <input type="button" align="top" value="Add to Favorites" style="width: 100px"
                                   class="ControlPushButton"
                                   onclick="javascript:addFavorite2(<carlos:encode value='<%= String.valueOf(drug.localDrugId) %>' context="javaScriptAttribute"/>, '<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull((drug.customName!=null&&(!drug.customName.equalsIgnoreCase("null")))?drug.customName:drug.brandName) %>' context="javaScriptAttribute"/>');"/>


                            <%
                            } else {
                            %>
                            <form action="<%=request.getContextPath()%>/rx/searchDrug" method="post">
                                <input type="hidden" name="demographicNo" value="<carlos:encode value='<%= String.valueOf(currentDemographicNo) %>' context="htmlAttribute"/>"/>
                                <%
                                    String searchString = drug.brandName;
                                    if (searchString == null) searchString = drug.customName;
                                    if (searchString == null) searchString = drug.genericName;
                                    if (searchString == null) searchString = drug.prescriptionDetails;
                                %>
                                <input type="hidden" name="searchString" value="<carlos:encode value='<%= searchString %>' context="htmlAttribute"/>"/>
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
                       onclick="javascript:window.location.href='<%= request.getContextPath() %>/rx/searchDrug';"/></td>
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
