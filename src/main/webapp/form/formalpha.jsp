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
<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
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

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ page import="io.github.carlos_emr.carlos.form.*" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%@ page import="org.owasp.encoder.Encode" %>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.title"/></title>
        <link rel="stylesheet" type="text/css" href="alphaStyle.css">
        <link rel="stylesheet" type="text/css" media="print" href="print.css">
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

        <%
            String formClass = "Alpha";
            String formLink = "formalpha.jsp";

            int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
            int formId = Integer.parseInt(request.getParameter("formId"));
            int provNo = Integer.parseInt((String) session.getAttribute("user"));
            FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
            java.util.Properties props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId);
        %>

        <script type="text/javascript" language="Javascript">
            function onPrint() {
                window.print();
            }

            function onSave() {
                document.forms[0].submit.value = "save";
                var ret = confirm("<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgWannaSave"/>");
                return ret;
            }

            function onSaveExit() {
                document.forms[0].submit.value = "exit";
                var ret = confirm("<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgWannaSaveClose"/>");
                return ret;
            }
        </script>
    </head>


    <body topmargin="0" leftmargin="0" rightmargin="0">
    <form action="${pageContext.request.contextPath}/form/formname.do" method="post">

        <input type="hidden" name="demographic_no"
               value="<%= props.getProperty("demographic_no", "0") %>"/>
        <input type="hidden" name="ID"
               value="<%= props.getProperty("ID", "0") %>"/>
        <input type="hidden" name="provider_no"
               value="<%= Encode.forHtmlAttribute(request.getParameter("provNo")) %>"/>
        <input type="hidden" name="formCreated"
               value="<%= props.getProperty("formCreated", "") %>"/>
        <input type="hidden" name="form_class" value="<%=formClass%>"/>
        <input type="hidden" name="form_link" value="<%=formLink%>"/>
        <input type="hidden" name="pName"
               value="<%= props.getProperty("pName", "") %>"/>
        <input type="hidden" name="provNo"
               value="<%= Encode.forHtmlAttribute(request.getParameter("provNo")) %>"/>
        <input type="hidden" name="submit" value="exit"/>

        <table class="Head" class="hidePrint">
            <tr>
                <td align="left"><input type="submit"
                                        value="<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.btnSave"/>"
                                        onclick="javascript:return onSave();"/> <input type="submit"
                                                                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.btnSaveExit"/>"
                                                                                       onclick="javascript:return onSaveExit();"/>
                    <input type="submit"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.btnExit"/>"
                           onclick="javascript:return onExit();"/> <input type="button"
                                                                          value="<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.btnPrint"/>"
                                                                          onclick="javascript:return onPrint();"/></td>
            </tr>
        </table>

        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr bgcolor="#486ebd">
                <th align='CENTER'><font size="-1"
                                         face="Arial, Helvetica, sans-serif" color="#FFFFFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgAlpha"/></font></th>
            </tr>
        </table>
        <table width="100%" border="0" bgcolor="ivory">
            <tr bgcolor="#99FF99">
                <td><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgFamilyFactors"/></b></td>
                <td align="right"></td>
            </tr>
            <tr>
                <td width="50%"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formSocialSupport"/></b>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgFamilyFeel"/></font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgFormWhoHelp"/></font></li>
                </td>
                <td><textarea name="socialSupport" style="width: 100%" cols="40"
                              rows="3"><%= props.getProperty("socialSupport", "") %></textarea></td>
            </tr>
            <tr>
                <td><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formRecentStressfulEvents"/></b>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgLifeChanges"/></font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgPlanningChanges"/></font></li>
                </td>
                <td><textarea name="lifeEvents" style="width: 100%" cols="40"
                              rows="3"><%= props.getProperty("lifeEvents", "") %></textarea></td>
            </tr>
            <tr>
                <td><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formCoupleRelationship"/></b>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgRelationship"/></font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgRelationshipAfterBirth"/></font><br>
                    </li>
                </td>
                <td><textarea name="coupleRelationship" style="width: 100%"
                              cols="40" rows="3"><%= props.getProperty("coupleRelationship", "") %></textarea>
                </td>
            </tr>
            <tr bgcolor="#99FF99">
                <td colspan="2"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgMaternalFactors"/></b></td>
            </tr>
            <tr>
                <td valign="top"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formPrenatal"/></b>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgPrenatalVisit"/></font></li>
                </td>
                <td><textarea name="prenatalCare" style="width: 100%" cols="40"
                              rows="2"><%= props.getProperty("prenatalCare", "") %></textarea></td>
            </tr>
            <tr>
                <td valign="top"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formPrenatalEducation"/></b>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgPrenatalPlans"/> </font></li>
                </td>
                <td><textarea name="prenatalEducation" style="width: 100%"
                              cols="40" rows="2"><%= props.getProperty("prenatalEducation", "") %></textarea>
                </td>
            </tr>
            <tr>
                <td valign="top"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formFeelingsTowardpregnancy"/> </b>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgFellPregnant"/></font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgFellAboutpregnant"/></font></li>
                </td>
                <td><textarea name="feelingsRePregnancy" style="width: 100%"
                              cols="40" rows="2"><%= props.getProperty("feelingsRePregnancy", "") %></textarea>
                </td>
            </tr>
            <tr>
                <td valign="top"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formRelationshipWithParents"/></b>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgRelationshipWithParents"/></font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgLovedByParents"/></font></li>
                </td>
                <td><textarea name="relationshipParents" style="width: 100%"
                              cols="40" rows="2"><%= props.getProperty("relationshipParents", "") %></textarea>
                </td>
            </tr>
            <tr>
                <td valign="top"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formSelfEsteem"/></b>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgSelfEsteemConcerns"/></font></li>
                </td>
                <td><textarea name="selfEsteem" style="width: 100%" cols="40"
                              rows="2"><%= props.getProperty("selfEsteem", "") %></textarea></td>
            </tr>
            <tr>
                <td valign="top"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formEmotionalProblems"/></b>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgEmotioanlProblems"/></font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgPsychiatrist"/></font></li>
                </td>
                <td><textarea name="psychHistory" style="width: 100%" cols="40"
                              rows="2"><%= props.getProperty("psychHistory", "") %></textarea></td>
            </tr>
            <tr>
                <td valign="top"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formDepression"/></b>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgMood"/> </font></li>
                </td>
                <td><textarea name="depression" style="width: 100%" cols="40"
                              rows="2"><%= props.getProperty("depression", "") %></textarea></td>
            </tr>
            <tr bgcolor="#99FF99">
                <td valign="top" colspan="2"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgSubstanceUse"/></b></td>
            </tr>
            <tr>
                <td valign="top"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formAlcohol"/></b>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgDrinksPerWeek"/></font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgTimesDrinkMore"/></font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgDrugsUse"/></font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgPartnerAlcoholAndDrugs"/></font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgCAGE"/></font></li>
                </td>
                <td><textarea name="alcoholDrugAbuse" style="width: 100%"
                              cols="40" rows="5"><%= props.getProperty("alcoholDrugAbuse", "") %></textarea>
                </td>
            </tr>
            <tr bgcolor="#99FF99">
                <td valign="top" colspan="2"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgFamilyViolence"/></b></td>
            </tr>
            <tr>
                <td valign="top"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgAbuse"/></b>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgRelationship"/></font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgFatherViolence"/></font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgParentsViolence"/></font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgAbusedAsAChild"/></font></li>
                </td>
                <td><textarea name="abuse" style="width: 100%" cols="40"
                              rows="5"><%= props.getProperty("abuse", "") %></textarea></td>
            </tr>
            <tr>
                <td valign="top"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formWomanAbuse"/></b><br>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgSolveArguments"/> </font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgFeelFrightened"/> </font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgBeenHit"/></font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgHumiliated"/> </font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgForcedSex"/> </font></li>
                </td>
                <td><textarea name="womanAbuse" style="width: 100%" cols="40"
                              rows="5"><%= props.getProperty("womanAbuse", "") %></textarea></td>
            </tr>
            <tr style="page-break-before: always;">
                <td valign="top"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formPreviousChildAbuse"/></b>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgDistantChild"/> </font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgChildProtectionAgency"/> </font></li>
                </td>
                <td><textarea name="childAbuse" style="width: 100%" cols="40"
                              rows="2"><%= props.getProperty("childAbuse", "") %></textarea></td>
            </tr>
            <tr>
                <td valign="top"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formChildDiscipline"/> </b>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgDiscilinedMother"/></font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgHowWillDiscipline"/></font></li>
                    <li><font size="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgMisbehave"/></font></li>
                </td>
                <td><textarea name="childDiscipline" style="width: 100%"
                              cols="40" rows="3"><%= props.getProperty("childDiscipline", "") %></textarea>
                </td>
            </tr>
        </table>
        <table width="100%" border="0" bgcolor="ivory">
            <tr bgcolor="#99ff99">
                <td align="center" colspan="6"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.msgFollowUp"/></b></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="provCounselling"
                        <%= props.getProperty("provCounselling", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formCounselling"/></td>
                <td><input type="checkbox" name="homecare"
                        <%= props.getProperty("homecare", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formHomecare"/></td>
                <td><input type="checkbox" name="assaultedWomen"
                        <%= props.getProperty("assaultedWomen", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formAssaultedWomen"/></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="addAppts"
                        <%= props.getProperty("addAppts", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.form"/></td>
                <td><input type="checkbox" name="parentingClasses"
                        <%= props.getProperty("parentingClasses", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formParentingClasses"/></td>
                <td><input type="checkbox" name="legalAdvice"
                        <%= props.getProperty("legalAdvice", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formLegalAdvise"/></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="postpartumAppts"
                        <%= props.getProperty("postpartumAppts", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formPospartumAppointments"/></td>
                <td><input type="checkbox" name="addictPrograms"
                        <%= props.getProperty("addictPrograms", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formAddictionTreatment"/></td>
                <td><input type="checkbox" name="cas"
                        <%= props.getProperty("cas", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formChildrenAid"/></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="babyVisits"
                        <%= props.getProperty("babyVisits", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formBabyVisits"/>
                </td>
                <td><input type="checkbox" name="quitSmoking"
                        <%= props.getProperty("quitSmoking", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formSmokingCessation"/></td>
                <td><input type="checkbox" name="other1"
                        <%= props.getProperty("other1", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formOther"/>:<input
                        type="text" name="other1Name"
                        value="<%= props.getProperty("other1Name", "")%>"></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="publicHealth"
                        <%= props.getProperty("publicHealth", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formPublicHealth"/></td>
                <td><input type="checkbox" name="socialWorker"
                        <%= props.getProperty("socialWorker", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formSocialWorker"/></td>
                <td><input type="checkbox" name="other2"
                        <%= props.getProperty("other2", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formOther"/>:<input
                        type="text" name="other2Name"
                        value="<%= props.getProperty("other2Name", "") %>"></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="prenatalEdu"
                        <%= props.getProperty("prenatalEdu", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formPrenatalEducation"/></td>
                <td><input type="checkbox" name="psych"
                        <%= props.getProperty("psych", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formPsychologist"/></td>
                <td><input type="checkbox" name="other3"
                        <%= props.getProperty("other3", "") %>></td>
                <td>Other:<input type="text" name="other3Name"
                                 value="<%= props.getProperty("other3Name", "") %>"></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="nutritionist"
                        <%= props.getProperty("nutritionist", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formNutrucionist"/></td>
                <td><input type="checkbox" name="therapist"
                        <%= props.getProperty("therapist", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formFamilyTherapist"/></td>
                <td><input type="checkbox" name="other4"
                        <%= props.getProperty("other4", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formOther"/>:<input
                        type="text" name="other4Name"
                        value="<%= props.getProperty("other4Name", "") %>"></td>
            </tr>
            <tr>
                <td><input type="checkbox" name="resources"
                        <%= props.getProperty("resources", "") %>></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formMothersGroup"/></td>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
                <td>&nbsp;</td>
            </tr>
        </table>
        <table width="100%" border="0" bgcolor="ivory">
            <tr>
                <td><b><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.formComments"/></b>:<br>
                    <textarea name="comments" style="width: 100%"
                              cols="80"><%= props.getProperty("comments", "") %></textarea>
                </td>

            </tr>
        </table>

        <table class="Head" class="hidePrint">
            <tr>
                <td align="left"><input type="submit"
                                        value="<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.btnSave"/>"
                                        onclick="javascript:return onSave();"/> <input type="submit"
                                                                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.btnSaveExit"/>"
                                                                                       onclick="javascript:return onSaveExit();"/>
                    <input type="submit"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.btnExit"/>"
                           onclick="javascript:return onExit();"/> <input type="button"
                                                                          value="<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.formAlpha.btnPrint"/>"
                                                                          onclick="javascript:return onPrint();"/></td>
            </tr>
        </table>

    </form>
    </body>
</html>
