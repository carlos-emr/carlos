<%--

    Copyright (c) 2015-2019. The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia. All Rights Reserved.
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
    The Pharmacists Clinic
    Faculty of Pharmaceutical Sciences
    University of British Columbia
    Vancouver, British Columbia, Canada

--%>

<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<%-- 
	Author: Dennis Warren 
	Company: Colcamex Resources
	Date: November 2014 
	Comment: The Pharmacists Clinic, Faculty of Pharmaceutical Sciences, University of British Columbia.
--%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName2$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_form" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_form");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<html>

    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.title"/></title>
        <link rel="stylesheet" type="text/css" media="screen"
              href="${ pageContext.request.contextPath }/form/pharmaForms/index.css"/>
        <link rel="stylesheet" type="text/css" media="screen"
              href="${ pageContext.request.contextPath }/css/bootstrap.min.css"/>
        <link rel="stylesheet" type="text/css" href="${ pageContext.request.contextPath }/css/healthCareTeam.css"/>
        <script type="text/javascript" src="${ pageContext.request.contextPath }/js/jquery.js"></script>
        <script type="text/javascript">

            function popUpData(data) {
                if (data) {
                    location.reload();
                }
            }

            jQuery(document).ready(function ($) {
                $("#editFamilyDr").click(function () {
                    var familyDrContactId = $("#familyDrContactId").val();
                    var demographicNo = '${ bpmh.demographic.demographicNo }';
                    var source = "${ pageContext.request.contextPath }/demographic/manageHealthCareTeam.jsp?" +
                        "demographicNo=" + demographicNo +
                        "&view=detached" +
                        "&element=providerId";
                    var windowspecs = "width=700,height=400,left=-1000,top=-1000, scrollbars=yes, resizable=yes";
                    window.open(source, "manageHealthCareTeam", windowspecs);
                })
            })
        </script>

    </head>

    <body id="formBPMH">

    <!--  HEADING  -->
    <!-- FORM BEGIN -->
    <form action="${pageContext.request.contextPath}/formBPMH.do" method="post">
        <input type="hidden" name="demographicNo" id="demographicNo"/>
        <input type="hidden" name="formId" id="formId"/>
        <header>

            <h1><fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.title"/></h1>

            <c:set var="controls" value="on" scope="page"/>

            <div id="bpmhId">

                <c:if test="${empty bpmh.provider}">
                    <span class="red">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.preparedby"/>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.error.unknown"/>
                    </span>
                </c:if>


                <c:if test="${not empty bpmh.provider}">
		
				<span>	
						<fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.preparedby"/>
						<c:out value="${bpmh.provider.formattedName}"/>
						<c:choose>
                            <c:when test="${not empty bpmh.provider.ohipNo}">
                                &#40;<c:out value="${bpmh.provider.ohipNo}"/>&#41;
                            </c:when>
                            <c:otherwise>
                                &#40;<fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.error.unknown"/>&#41;
                            </c:otherwise>
                        </c:choose>

								
					</c:if>
				</span>


                <c:if test="${empty bpmh.formDateFormatted}">
                    <span class="red">
                </c:if>
                <c:if test="${not empty bpmh.formDateFormatted}">
                    <span>
                </c:if>

					<fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.preparedon"/>
					<c:out value="${bpmh.formDateFormatted}"/>
				</span>
									
				<span>				
					<fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.printedon"/>
					<c:out value="${bpmh.editDateFormatted}"/>
				</span>

            </div>
        </header>

        <!-- SUB HEADING -->
        <section id="subHeader">

            <!--  PATIENT  -->
            <table id="patientId">
                <tr>
                    <th colspan="6"><fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.patient"/></th>
                </tr>
                <tr>
                    <td rowspan="2" class="columnTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.patient.name"/></td>
                    <td rowspan="2">
                        <c:out value="${bpmh.demographic.fullName}"/>
                    </td>
                    <td class="columnTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.patient.insurance"/></td>
                    <td>
                        <c:out value="${bpmh.demographic.hin}"/>
                    </td>
                    <td class="columnTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.patient.gender"/></td>
                    <td>
                        <c:out value="${bpmh.demographic.sex}"/>
                    </td>
                </tr>

                <tr>
                    <td class="columnTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.patient.dob"/></td>
                    <td>
                        <c:out value="${bpmh.demographic.formattedDob}"/>
                    </td>
                    <td class="columnTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.patient.phone"/></td>
                    <td>
                        <c:out value="${bpmh.demographic.phone}"/>
                    </td>
                </tr>
            </table>
            <table id="allergies">
                <tr>
                    <td class="columnTitle">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.patient.allergies"/>
                    </td>
                    <td>
                        <c:if test="${not empty bpmh.allergiesString}">
                            <c:out value="${bpmh.allergiesString}" />
                        </c:if>
                    </td>
                </tr>
            </table>
        </section>

        <!-- FAMILY PHYSICIAN -->
        <section id="familyPhysician">
            <input type="hidden" name="bpmh" property="familyDrContactId" id="familyDrContactId"/>
            <table id="providerId">
                <tr>
                    <th colspan="7"><fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.familyDr"/></th>
                </tr>
                <tr>
                    <td class="columnTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.familyDr.name"/></td>
                    <td>
                        <c:choose>
                            <c:when test="${empty bpmh.familyDrName}">
                                <span class="red">Unknown</span>
                            </c:when>
                            <c:otherwise>
                                <c:out value="${bpmh.familyDrName}" />
                            </c:otherwise>
                        </c:choose>

                    </td>

                    <td class="columnTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.familyDr.phone"/></td>
                    <td>
                        <c:choose>
                            <c:when test="${empty bpmh.familyDrPhone}">
                                <span class="red">Unknown</span>
                            </c:when>
                            <c:otherwise>
                                <c:out value="${bpmh.familyDrPhone}" />
                            </c:otherwise>
                        </c:choose>
                    </td>

                    <td class="columnTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.familyDr.fax"/></td>
                    <td>
                        <c:choose>
                            <c:when test="${empty bpmh.familyDrFax}">
                                <span class="red">Unknown</span>
                            </c:when>
                            <c:otherwise>
                                <c:out value="${bpmh.familyDrFax}" />
                            </c:otherwise>
                        </c:choose>
                    </td>
                    <c:if test="${bpmh.formId == 0}">
                        <c:if test="${not empty bpmh.familyDrContactId}">
                            <td class="columnTitle" style="text-align:center;background-color:#CCC;border-top:#ccc thin solid;">
                                <input type="button" id="editFamilyDr" value="edit"/>
                            </td>
                        </c:if>
                    </c:if>

                </tr>

            </table>
        </section>

        <!-- DRUG DATA -->
        <section id="drugData">
            <table id="drugtable">

                <tr>
                    <th colspan="4"><fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.drugs"/></th>
                </tr>
                <tr id="drugtableSubheading">

                    <td class="columnTitle">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.drugs.what"/><br/>
                        <span class="smallText">
							<fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.drugs.what.sub"/>
						</span>
                    </td>
                    <td class="columnTitle">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.drugs.how"/><br/>
                        <span class="smallText">
							<fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.drugs.how.sub"/>
						</span>
                    </td>
                    <td class="columnTitle">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.drugs.why"/><br/>
                        <span class="smallText">
							<fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.drugs.why.sub"/>
						</span>
                    </td>
                    <td class="columnTitle">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.drugs.instructions"/><br/>
                        <span class="smallText">
							<fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.drugs.instructions.sub"/>
						</span>
                    </td>
                </tr>
                <c:set value="missingDrugData" var="false"/>
                <c:if test="${not empty bpmh.drugs}">
                    <c:forEach var="drugs" items="${bpmh.drugs}" varStatus="status">
                        <tr>
                            <form:hidden property="id" indexed="true" value="${drugs.id}"/>

                            <td>
                                <!-- WHAT -->
                                <c:out value="${drugs.what}"/>
                            </td>

                            <c:choose>
                                <c:when test="${empty drugs.how}">
                                    <c:set var="missingDrugData" value="true"/>
                                    <td style="border:red medium solid;">
                                        <!-- HOW ERROR -->
                                        &nbsp;
                                    </td>
                                </c:when>
                                <c:otherwise>
                                    <td>
                                        <!-- HOW -->
                                        <c:out value="${drugs.how}"/>
                                    </td>
                                </c:otherwise>
                            </c:choose>

                            <c:choose>
                                <c:when test="${empty drugs.why}">
                                    <c:set var="missingDrugData" value="true"/>
                                    <td style="border:red medium solid;">
                                        <!-- WHY ERROR -->
                                        &nbsp;
                                    </td>
                                </c:when>
                                <c:otherwise>
                                    <td>
                                        <!-- WHY -->
                                        <c:out value="${drugs.why}"/>
                                    </td>
                                </c:otherwise>
                            </c:choose>

                            <td>
                                <!-- INSTRUCTION -->
                                <c:choose>
                                    <c:when test="${not empty drugs.instruction}">
                                        <c:out value="${drugs.instruction}"/>
                                    </c:when>
                                    <c:otherwise>
                                        <form:textarea property="instruction" indexed="true">
                                            &nbsp;
                                        </form:textarea>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                        </tr>
                    </c:forEach>
                </c:if>


            </table>
        </section>
        <section id="declaration">
            <table>
                <tr>
                    <td>
                        <input type="checkbox" name="confirm" id="confirm" value="checked"/>
                        <label for="confirm"><fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.confirm"/></label>
                    </td>
                </tr>
            </table>
        </section>
        <section id="note">
            <table>
                <tr>
                    <td>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.notes"/>
                    </td>
                </tr>
                <tr>
                    <td>
                        <c:choose>
                            <c:when test="${not empty bpmh.note}">
                                <c:out value="${bpmh.note}"/>&nbsp;
                            </c:when>
                            <c:otherwise>
                                <form:textarea property="note">&nbsp;</form:textarea>
                            </c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </table>
        </section>

        <section id="controls">
            <table>
                <tr>
                    <td>
                        <c:if test="${ missingDrugData }">
                            <c:set var="controls" value="off" scope="page"/>
                            <span style="color:red;">Missing Medication Data</span>
                        </c:if>
                        <c:choose>
                            <c:when test="${empty bpmh.allergiesString}">
                                <c:set var="controls" value="off" scope="page"/>
                                <span style="color:red;">Allergy Notation is Required (ie: NKDA)</span>
                            </c:when>
                        </c:choose>
                        <c:choose>
                            <c:when test="${empty bpmh.provider}">
                                <c:set var="controls" value="off" scope="page"/>
                                <span class="red">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.error.missing.provider"/>
                                </span>
                            </c:when>
                        </c:choose>


                        <c:if test="${ controls eq 'on' }">

                            <input type="submit" name="submit" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.print"/>" />

                            <c:if test="${bpmh.formId == 0}">
                            <input type="submit" name="submit" value=“<fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.save"/>" />
                            </c:if>

                        </c:if>

                        <c:if test="${not empty savedMessage}">
                            <div class="messages">
                                    ${savedMessage}
                            </div>
                        </c:if>
                    </td>
                </tr>
            </table>
        </section>
    </form>

    <!-- FOOTER -->
    <footer>
        <span><fmt:setBundle basename="oscarResources"/><fmt:message key="colcamex.formBPMH.formowner"/></span>
    </footer>

    </body>
</html>