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
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_form");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<!DOCTYPE html>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LocaleUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>

<%
    String formClass = "SelfAssessment";
    String formLink = "formSelfAssessment.jsp";
    String projectHome = request.getContextPath().substring(1);

    int formId = Integer.parseInt(request.getParameter("formId"));
    int demoNo = Integer.parseInt(request.getParameter("demographic_no"));

    FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
    java.util.Properties props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId);

    DemographicDao demoDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    Demographic demo = demoDao.getDemographic(request.getParameter("demographic_no"));
    String demoName = demo.getFormattedName();

    ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
    String providerNo = props.getProperty("provider_no", "");
    String providerName = "";
    if (providerNo != null && !providerNo.isEmpty() && !providerNo.equals("999998")) {
        providerName = providerDao.getProviderName(providerNo);
    } else {
        providerName = LocaleUtils.getMessage(request.getLocale(), "encounter.formCounseling.notValidated");
    }
    props.setProperty("doc_name", providerName);

%>

<html>
    <% response.setHeader("Cache-Control", "no-cache");%>

    <HEAD>
        <META HTTP-EQUIV="CONTENT-TYPE" CONTENT="text/html; charset=iso-8859-1">

        <TITLE><fmt:message key="form.selfAssessment.title"/></TITLE>
    </HEAD>

    <body bgproperties="fixed" topmargin="0" leftmargin="0" rightmargin="0" onload="window.resizeTo(768,768)" bgcolor="#eeeeee">

    <form action="${pageContext.request.contextPath}/form/formname" method="post">
        <input type="hidden" name="demographic_no" value="<%= props.getProperty("demographic_no", "0") %>"/>
        <input type="hidden" name="formCreated" value="<%= props.getProperty("formCreated", "") %>"/>
        <input type="hidden" name="form_class" value="<%=formClass%>"/>
        <input type="hidden" name="form_link" value="<%=formLink%>"/>
        <input type="hidden" name="formId" value="<%=formId%>"/>
        <input type="hidden" name="demographic_no" value="<%= props.getProperty("demographic_no", "0") %>"/>
        <input type="hidden" name="doc_name" value="<%=props.getProperty("doc_name", "")%>"/>
        <input type="hidden" name="submit" value="exit"/>

        <table cellpadding="0" cellspacing="0" border="1" align="center" width="803" bordercolor="#000001">
            <COL WIDTH=433>
            <TR>
                <TD COLSPAN=2 WIDTH=785 VALIGN=TOP BGCOLOR="#000000">
                    <OL>
                        <LI><P><font color="#ffffff" STYLE="font-size: 14pt" FACE="Arial, serif"><B><fmt:message key="form.selfAssessment.id"/></B></FONT></P>
                        </LI>
                    </OL>
                </TD>
            </TR>
            <TR VALIGN=TOP>
                <TD WIDTH=335>
                    <p><fmt:message key="form.selfAssessment.name"/> <input type="text" name="name" value="<%= props.getProperty("name", "")%>" readonly></FONT>
                    </P>
                    <p><fmt:message key="form.selfAssessment.sex"/> <input type="text" name="sex" value="<%= props.getProperty("sex", "")%>" readonly></FONT>
                    </P>
                    <p><fmt:message key="form.selfAssessment.dob"/> <input type="text" name="p_birthdate" value="<%= props.getProperty("p_birthdate", "")%>"
                                   readonly></FONT></P>
                </TD>
                <TD WIDTH=433>
                    <p><fmt:message key="form.selfAssessment.faculty"/> <input type="text" name="faculty" value="<%= props.getProperty("faculty", "")%>"></FONT>
                    </P>
                    <p><fmt:message key="form.selfAssessment.academicYear"/> <input type="text" name="AcademicYear"
                                             value="<%= props.getProperty("AcademicYear", "")%>"></FONT></P>
                    <p><fmt:message key="form.selfAssessment.ptft"/> <input type="text" name="PTFT"
                                                      value="<%= props.getProperty("PTFT", "")%>"></FONT></P>
                    <p><fmt:message key="form.selfAssessment.job"/> <input type="text" name="Job" size="25" maxlength="200"
                                                 value="<%= props.getProperty("Job", "")%>"></FONT></P>
                    <p><fmt:message key="form.selfAssessment.hoursPerWeek"/> <input type="text" name="Hours" size="25" maxlength="200"
                                                          value="<%= props.getProperty("Hours", "")%>"></FONT></P>
                </TD>
            </TR>
            <TR>
                <TD COLSPAN=2 WIDTH=785 VALIGN=TOP BGCOLOR="#000000"></td>
                </TD>
            </TR>
            <TR>
                <TD COLSPAN=2 WIDTH=785 VALIGN=TOP>
                    <fmt:message key="form.selfAssessment.livingSituation"/><br>
                    <fmt:message key="form.selfAssessment.residence"/> <input type="text" name="Residence" value="<%= props.getProperty("Residence", "")%>">
                    <br>
                    <fmt:message key="form.selfAssessment.offCampus"/> <input type="text" name="Campus" value="<%= props.getProperty("Campus", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.atHome"/> <input type="text" name="Home" value="<%= props.getProperty("Home", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.roommates"/> <input type="text" name="Roommates" size="5" maxlength="50"
                                                value="<%= props.getProperty("Roommates", "")%>"><br>
                    <fmt:message key="form.selfAssessment.other"/> <input type="text" name="LivingSituationOther" size="100" maxlength="250"
                                  value="<%= props.getProperty("LivingSituationOther", "")%>">
                </TD>
            </TR>
            <TR>
                <TD COLSPAN=2 WIDTH=785 VALIGN=TOP BGCOLOR="#000000">
                    <OL START=3>
                        <LI><P><font color="#ffffff" STYLE="font-size: 14pt" FACE="Arial, serif"><fmt:message key="form.selfAssessment.reasonsCounselling"/></FONT></P></LI>
                        <LI><P><font color="#ffffff" STYLE="font-size: 14pt" FACE="Arial, serif"><fmt:message key="form.selfAssessment.comfortLevel"/></FONT></P></LI>
                    </OL>
                </TD>
            </TR>
            <TR>
                <TD COLSPAN=2 WIDTH=785 VALIGN=TOP>
                    <fmt:message key="form.selfAssessment.depressionSelfEsteem"/> <input type="text" name="Depression"
                                                   value="<%= props.getProperty("Depression", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.helplessnessHopelessness"/> <input type="text" name="helplessness"
                                                                  value="<%= props.getProperty("helplessness", "")%>">
                    <br>
                    <fmt:message key="form.selfAssessment.adhd"/> <input type="text" name="ADHD" value="<%= props.getProperty("ADHD", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.obsessionsCompulsions"/> <input type="text" name="Obsessions"
                                                   value="<%= props.getProperty("Obsessions", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.bipolarMoodDisorder"/> <input type="text" name="Bipolar"
                                                                           value="<%= props.getProperty("Bipolar", "")%>">
                    <br>
                    <fmt:message key="form.selfAssessment.anxiety"/> <input type="text" name="Anxiety" value="<%= props.getProperty("Anxiety", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.selfEsteem"/> <input type="text" name="Esteem" value="<%= props.getProperty("Esteem", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.relationshipDifficulties"/> <input type="text" name="Relationship"
                                                      value="<%= props.getProperty("Relationship", "")%>"> <br>

                    <fmt:message key="form.selfAssessment.eatingProblems"/> <input type="text" name="Eating" value="<%= props.getProperty("Eating", "")%>">
                    <br>
                    <fmt:message key="form.selfAssessment.sexualIssues"/> <input type="text" name="Sexual" value="<%= props.getProperty("Sexual", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.suicidalThoughts"/> <input type="text" name="Suicidal"
                                              value="<%= props.getProperty("Suicidal", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.psychosis"/> <input type="text" name="Psychosis"
                                                                      value="<%= props.getProperty("Psychosis", "")%>">
                    <br>
                    <fmt:message key="form.selfAssessment.mania"/> <input type="text" name="Mania" value="<%= props.getProperty("Mania", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.grief"/> <input type="text" name="Grief" value="<%= props.getProperty("Grief", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.substanceAbuse"/> <input type="text" name="Substance"
                                            value="<%= props.getProperty("Substance", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.trauma"/><br>
                    <UL>
                        <LI><fmt:message key="form.selfAssessment.emotional"/> <input type="text" name="TraumaEmotional"
                                              value="<%= props.getProperty("TraumaEmotional", "")%>"></LI>
                        <LI><fmt:message key="form.selfAssessment.physical"/> <input type="text" name="TraumaPhysical"
                                             value="<%= props.getProperty("TraumaPhysical", "")%>"></LI>
                        <LI><fmt:message key="form.selfAssessment.sexual"/> <input type="text" name="TraumaSexual"
                                           value="<%= props.getProperty("TraumaSexual", "")%>"></LI>

                    </UL>
                    <fmt:message key="form.selfAssessment.academicDifficulties"/> <input type="text" name="Academic"
                                                  value="<%= props.getProperty("Academic", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.otherPleaseExplain"/> <br>
                    <input type="text" name="ReasonsOther" size="100" maxlength="200"
                           value="<%= props.getProperty("ReasonsOther", "")%>">
                </TD>
            </TR>
            <TR>
                <TD COLSPAN=2 WIDTH=785 VALIGN=TOP BGCOLOR="#000000">
                    <OL START=4>
                        <LI><P><FONT COLOR="#ffffff"><B><fmt:message key="form.selfAssessment.pastMedicalHistory"/></B></FONT></P></LI>
                    </OL>
                </TD>
            </TR>
            <TR>
                <TD COLSPAN=2 WIDTH=785 VALIGN=TOP>
                    <fmt:message key="form.selfAssessment.hospitalizations"/> <br>
                    <fmt:message key="form.selfAssessment.whenDateWhy"/> <br>
                    <textarea name="Hospitalizations" id="Hospitalizations" rows="0"
                              cols="111"><%= props.getProperty("Hospitalizations", "")%></textarea><br>

                    <fmt:message key="form.selfAssessment.surgery"/> <br>
                    <textarea name="Surgery" id="Surgery" rows="0"
                              cols="111"><%= props.getProperty("Surgery", "")%></textarea><br>

                    <fmt:message key="form.selfAssessment.medicalIllnesses"/> <br><textarea name="Medicalillnesses" id="Medicalillnesses" rows="0"
                                                     cols="111"><%= props.getProperty("Medicalillnesses", "")%></textarea><br>

                    <fmt:message key="form.selfAssessment.currentlyTakingMedication"/> <input type="text" name="CurrentMedications"
                                                                value="<%= props.getProperty("CurrentMedications", "")%>">
                    <br>
                    <fmt:message key="form.selfAssessment.pleaseList"/><br><textarea name="CurrentMedicationsList" id="CurrentMedicationsList" rows="0"
                                              cols="111"><%= props.getProperty("CurrentMedicationsList", "")%></textarea><br>

                    <fmt:message key="form.selfAssessment.previousPsychiatricMedication"/> <input type="text" name="psychiatricMedications"
                                                                             value="<%= props.getProperty("psychiatricMedications", "")%>">
                    <br>
                    <fmt:message key="form.selfAssessment.pleaseList"/><br><textarea name="psychiatricMedicationsList" id="psychiatricMedicationsList" rows="0"
                                              cols="111"><%= props.getProperty("psychiatricMedicationsList", "")%></textarea><br>

                    <fmt:message key="form.selfAssessment.additionalInformation"/><br><textarea name="HospitalizationsOther"
                                                                      id="HospitalizationsOther" rows="0"
                                                                      cols="111"><%= props.getProperty("HospitalizationsOther", "")%></textarea><br>
                </TD>
            </TR>
            <TR>
                <TD COLSPAN=2 WIDTH=785 VALIGN=TOP BGCOLOR="#000000">
                    <OL START=5>
                        <LI><P><FONT COLOR="#ffffff"><B><fmt:message key="form.selfAssessment.pastPsychiatricHistory"/></B></FONT></P></LI>
                    </OL>
                </TD>
            </TR>
            <TR>
                <TD COLSPAN=2 WIDTH=785 VALIGN=TOP>
                    <fmt:message key="form.selfAssessment.substanceAbuse"/> <input type="text" name="PastSubstance"
                                            value="<%= props.getProperty("PastSubstance", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.alcohol"/> <input type="text" name="PastAlcohol" value="<%= props.getProperty("PastAlcohol", "")%>">
                    <br>
                    <fmt:message key="form.selfAssessment.prescribedDrugs"/> <input type="text" name="PastPrescribedDrugs"
                                             value="<%= props.getProperty("PastPrescribedDrugs", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.overTheCounterMedications"/> <input type="text" name="PastCounterMedications"
                                                         value="<%= props.getProperty("PastCounterMedications", "")%>">
                    <br>
                    <fmt:message key="form.selfAssessment.streetDrugs"/> <input type="text" name="PastStreetDrugs"
                                         value="<%= props.getProperty("PastStreetDrugs", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.tobacco"/> <input type="text" name="PastTobacco" value="<%= props.getProperty("PastTobacco", "")%>">
                    <br>
                    <fmt:message key="form.selfAssessment.trauma"/>
                    <UL>
                        <LI><fmt:message key="form.selfAssessment.emotional"/> <input type="text" name="PastPSYCHIATRICTraumaEmotional"
                                              value="<%= props.getProperty("PastPSYCHIATRICTraumaEmotional", "")%>">
                        </LI>
                        <LI><fmt:message key="form.selfAssessment.physical"/> <input type="text" name="PastPSYCHIATRICTraumaPhysical"
                                             value="<%= props.getProperty("PastPSYCHIATRICTraumaPhysical", "")%>"></LI>
                        <LI><fmt:message key="form.selfAssessment.sexual"/> <input type="text" name="PastPSYCHIATRICTraumaSexual"
                                           value="<%= props.getProperty("PastPSYCHIATRICTraumaSexual", "")%>"></LI>
                    </UL>
                    <fmt:message key="form.selfAssessment.legalProblems"/> <input type="text" name="PastLegal"
                                           value="<%= props.getProperty("PastLegal", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.gamblingAddiction"/> <input type="text" name="PastGambling"
                                               value="<%= props.getProperty("PastGambling", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.allergiesReactionsToPsychiatricMedications"/> <input type="text" name="PastReactionsMedication"
                                                                           value="<%= props.getProperty("PastReactionsMedication", "")%>">
                    <br>

                    <fmt:message key="form.selfAssessment.medicationName"/> <br><textarea name="PastReactionsMedicationList" id="PastReactionsMedicationList"
                                                   rows="0"
                                                   cols="111"><%= props.getProperty("PastReactionsMedicationList", "")%></textarea><br>

                    <fmt:message key="form.selfAssessment.suicideAttempts"/> <input type="text" name="PastSuicideAttempts"
                                             value="<%= props.getProperty("PastSuicideAttempts", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.howMany"/><br><textarea name="PastSuicideMany" id="PastSuicideMany" rows="0"
                                           cols="111"><%= props.getProperty("PastSuicideMany", "")%></textarea><br>
                    <fmt:message key="form.selfAssessment.when"/><br><textarea name="PastSuicideWhen" id="PastSuicideWhen" rows="0"
                                       cols="111"><%= props.getProperty("PastSuicideWhen", "")%></textarea><br>

                    <fmt:message key="form.selfAssessment.selfHarmCutting"/> <input type="text" name="PastCutting"
                                              value="<%= props.getProperty("PastCutting", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.ptsd"/> <input type="text" name="ptsd"
                                                            value="<%= props.getProperty("ptsd", "")%>"> <br>

                    <fmt:message key="form.selfAssessment.additionalInformation"/> <br><textarea name="PastPASTPSYCHIATRICOther"
                                                                       id="PastPASTPSYCHIATRICOther" rows="0"
                                                                       cols="111"><%= props.getProperty("PastPASTPSYCHIATRICOther", "")%></textarea><br>
                </TD>
            </TR>
            <TR>
                <TD COLSPAN=2 WIDTH=785 VALIGN=TOP BGCOLOR="#000000">
                    <OL START=6>
                        <LI><P><FONT FACE="Arial, serif" COLOR="#ffffff" SIZE=2 STYLE="font-size: 9pt"><FONT><B>Immediate
                            Family Members</B></FONT></P></LI>
                    </OL>
                </TD>
            </TR>
            <TR>
                <TD COLSPAN=2 WIDTH=785 VALIGN=TOP>
                    <fmt:message key="form.selfAssessment.ages"/>
                    <fmt:message key="form.selfAssessment.mother"/> <input type="text" name="AgesMother" size="5" maxlength="50"
                                   value="<%= props.getProperty("AgesMother", "")%>"><br>
                    <fmt:message key="form.selfAssessment.father"/> <input type="text" name="AgesFather" size="5" maxlength="50"
                                   value="<%= props.getProperty("AgesFather", "")%>"><br>
                    <fmt:message key="form.selfAssessment.siblings"/> <input type="text" name="AgesSiblings" size="30" maxlength="100"
                                     value="<%= props.getProperty("AgesSiblings", "")%>"><br>
                    <fmt:message key="form.selfAssessment.others"/> <input type="text" name="AgesOthers" size="100" maxlength="100"
                                   value="<%= props.getProperty("AgesOthers", "")%>"><br>
                    <fmt:message key="form.selfAssessment.adopted"/> <input type="text" name="Adopted" value="<%= props.getProperty("Adopted", "")%>">
                    <br>
                </TD>
            </TR>
            <TR>
                <TD width="778" colspan="2" valign="TOP" bgcolor="#000000">
                    <OL START=7>
                        <LI>
                            <P><FONT COLOR="#ffffff"><B><fmt:message key="form.selfAssessment.familyPsychiatricHistory"/></B></FONT></P>
                        </LI>
                    </OL>
                </TD>
            </TR>
            <TR>
                <TD WIDTH=778 colspan="2" VALIGN=TOP>
                    <fmt:message key="form.selfAssessment.depression"/> <input type="text" name="FamilyDepression"
                                       value="<%= props.getProperty("FamilyDepression", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.anxiety"/> <input type="text" name="FamilyAnxiety"
                                    value="<%= props.getProperty("FamilyAnxiety", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.substanceAbuse"/> <input type="text" name="FamilySubstance"
                                            value="<%= props.getProperty("FamilySubstance", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.alcohol"/> <input type="text" name="FamilyAlcohol"
                                    value="<%= props.getProperty("FamilyAlcohol", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.drugsSpecify"/>
                    <br><textarea name="FamilyDrugs" id="FamilyDrugs" rows="0"
                                  cols="111"><%= props.getProperty("FamilyDrugs", "")%></textarea><br>
                    <fmt:message key="form.selfAssessment.trauma"/>
                    <UL>
                        <LI><fmt:message key="form.selfAssessment.emotional"/> <input type="text" name="FamilyEmotional"
                                              value="<%= props.getProperty("FamilyEmotional", "")%>"></LI>
                        <LI><fmt:message key="form.selfAssessment.physical"/> <input type="text" name="FamilyPhysical"
                                             value="<%= props.getProperty("FamilyPhysical", "")%>"></LI>
                        <LI><fmt:message key="form.selfAssessment.sexual"/> <input type="text" name="FamilySexual"
                                           value="<%= props.getProperty("FamilySexual", "")%>"></LI>
                    </UL>
                    <fmt:message key="form.selfAssessment.suicide"/> <input type="text" name="FamilySuicide"
                                    value="<%= props.getProperty("FamilySuicide", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.eatingDisorder"/> <input type="text" name="FamilyEating"
                                            value="<%= props.getProperty("FamilyEating", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.bipolarDisorder"/> <input type="text" name="FamilyBipolar"
                                             value="<%= props.getProperty("FamilyBipolar", "")%>"> <br>
                    Psychosis: <input type="text" name="FamilyPsychosis"
                                      value="<%= props.getProperty("FamilyPsychosis", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.schizophrenia"/> <input type="text" name="FamilySchizophrenia"
                                          value="<%= props.getProperty("FamilySchizophrenia", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.adhd"/> <input type="text" name="FamilyADHD" value="<%= props.getProperty("FamilyADHD", "")%>"> <br>

                    <fmt:message key="form.selfAssessment.additionalInformation"/> <br><textarea name="FamilyPsychiatricOther"
                                                                       id="FamilyPsychiatricOther" rows="0"
                                                                       cols="111"><%= props.getProperty("FamilyPsychiatricOther", "")%></textarea><br>
                </TD>
            </TR>
            <TR>
                <TD WIDTH=778 colspan="2" VALIGN=TOP BGCOLOR="#000000">
                </TD>
            </TR>
            <TR>
                <TD WIDTH=778 colspan="2" VALIGN=TOP>
                    <fmt:message key="form.selfAssessment.smokerAtPresent"/> <input type="text" name="Smoker"
                                                        value="<%= props.getProperty("Smoker", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.howMuchSmoke"/> <input type="text" name="SmokeQty" size="100" maxlength="100"
                                                  value="<%= props.getProperty("SmokeQty", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.streetDrugsAnyKind"/> <input type="text" name="StreetDrugs"
                                                                value="<%= props.getProperty("StreetDrugs", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.drinkAlcohol"/> <input type="text" name="DrinkAlcohol"
                                                 value="<%= props.getProperty("DrinkAlcohol", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.drinksPerOccasion"/> <input type="text" name="DrinkAlcoholMany"
                                                                                size="100" maxlength="100"
                                                                                value="<%= props.getProperty("DrinkAlcoholMany", "")%>"><br>
                    <fmt:message key="form.selfAssessment.drinksPerWeek"/> <input type="text" name="DrinkAlcoholWeekly"
                                                                            size="100" maxlength="150"
                                                                            value="<%= props.getProperty("DrinkAlcoholWeekly", "")%>"><br>
                    <fmt:message key="form.selfAssessment.exerciseWeekly"/> <input type="text" name="Exercise"
                                                   value="<%= props.getProperty("Exercise", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.eatThreeMeals"/> <input type="text" name="Meals"
                                                     value="<%= props.getProperty("Meals", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.inRelationship"/> <input type="text" name="InRelationship"
                                                      value="<%= props.getProperty("InRelationship", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.academicPerformanceOkay"/> <input type="text" name="AcademicPerformance"
                                                              value="<%= props.getProperty("AcademicPerformance", "")%>">
                    <br>
                    <fmt:message key="form.selfAssessment.sexualOrientation"/><input type="text" name="SexualOrientation"
                                              value="<%= props.getProperty("SexualOrientation", "")%>"> <br>
                    <br>
                    <fmt:message key="form.selfAssessment.religiousAffiliation"/> <input type="text" name="ReligiousAffiliation" size="100" maxlength="150"
                                                  value="<%= props.getProperty("ReligiousAffiliation", "")%>"> <br>
                    <fmt:message key="form.selfAssessment.additionalInformation"/> <br><textarea name="GeneralOther" id="GeneralOther" rows="0"
                                                                       cols="111"><%= props.getProperty("GeneralOther", "")%></textarea><br>

                </TD>
            </TR>

            <TR>
                <TD WIDTH=778 colspan="2" VALIGN=TOP>
                    <br>
                    <br>
                </TD>
            </TR>

        </TABLE>


        <div align="center" id="buttons">
            <input id="savebut" type="submit" value="<fmt:message key="global.save"/>" onclick="javascript: return onSave();"/>
            <input id="saveexitbut" type="submit" value="<fmt:message key="global.saveExit"/>" onclick="javascript: return onSaveExit();"/>
            <input id="exitbut" type="submit" value="<fmt:message key="global.btnExit"/>" onclick="javascript: return onExit();"/>

        </div>

        </body>

        <script type="text/javascript">

            function onSave() {
                document.forms[0].submit.value = "save";
                ret = confirm("<fmt:message key='global.msgWannaSave'/>");
                return ret;
            }

            function onSaveExit() {
                document.forms[0].submit.value = "exit";
                ret = confirm("<fmt:message key='global.msgSaveExit'/>");
                return ret;
            }

            function onExit() {
                if (confirm("<fmt:message key='global.msgNotSave'/>") == true) {
                    window.close();
                }
                return (false);
            }

        </script>
    </form>
</html>
