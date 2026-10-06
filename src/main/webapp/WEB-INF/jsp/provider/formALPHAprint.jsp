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

<%
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logoutPage");
    String user_no = (String) session.getAttribute("user");
%>
<%@ page import="java.util.*, java.sql.*, io.github.carlos_emr.*"
         errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<HTML>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <TITLE><fmt:message key="provider.formALPHAprint.title"/></TITLE>
</HEAD>
<BODY BGCOLOR="#FFFFFF">

<TABLE BORDER CELLSPACING=1 BORDERCOLOR="#000000" CELLPADDING=4>
    <TR>
        <TD VALIGN="TOP" HEIGHT=16 colspan="2">
            <table border="0" width="100%">
                <tr>
                    <td ALIGN="CENTER"><FONT FACE="Arial, Helvetica"><B><fmt:message key="provider.formALPHAprint.assessmentTitle"/></B></FONT></TD>
                    <td align="right"><a href="<%= request.getContextPath() %>/provider/ViewFormALPHAprint1"> <fmt:message key="provider.formALPHAprint.nextPage"/>
                    </a> | <a href=# onClick="window.print();"><fmt:message key="provider.formALPHAprint.print"/></a></td>
                </tr>
            </table>
        </td>
    </TR>
    <TR>
        <TD WIDTH="92%" VALIGN="TOP" ALIGN="JUSTIFY"><font
                face="Arial, Helvetica" size="-2"><fmt:message key="provider.formALPHAprint.introduction"/><BR>
            <fmt:message key="provider.formALPHAprint.concernGuidance"/><BR>
            <I><fmt:message key="provider.formALPHAprint.sensitivityWarning"/></I></font></TD>
        <TD WIDTH="8%" VALIGN="TOP"><FONT FACE="Arial, Helvetica"
                                          Size="-1"><fmt:message key="provider.formALPHAprint.addressograph"/></FONT></TD>
    </TR>
</table>

<TABLE BORDER BORDERCOLOR="#000000" CELLSPACING=1 CELLPADDING=4>
    <TR bgcolor="#eeeeee">
        <TD WIDTH="50%" VALIGN="TOP" ALIGN="CENTER" bgcolor="#eeeeee"><font
                face="Arial, Helvetica" size="-1"><B><fmt:message key="provider.formALPHAprint.antenatalFactors"/></B></font></TD>
        <TD WIDTH="50%" VALIGN="TOP" ALIGN="CENTER"><font
                face="Arial, Helvetica" size="-1"><B><fmt:message key="provider.formALPHAprint.commentsPlan"/></B></font></TD>
    </TR>
    <TR bgcolor="#f9f9f9">
        <TD colspan="2" VALIGN="TOP"><FONT FACE="Arial, Helvetica"
                                           Size="-1"><fmt:message key="provider.formALPHAprint.familyFactors"/></FONT></TD>
    </TR>
    <TR>
        <TD WIDTH="50%" VALIGN="TOP"><FONT FACE="Arial, Helvetica"
                                           Size="-1"><B><fmt:message key="provider.formALPHAprint.socialSupport"/></B></FONT> (<B><I>CA, WA,</I></B> <FONT
                SIZE="-1">PD</FONT>)<BR>
            <FONT SIZE="-1" FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.socialSupport.q1"/></FONT><BR>
            <FONT SIZE="-1" FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.socialSupport.q2"/></FONT></TD>
        <TD VALIGN="TOP"
            WIDTH="50%"><%= request.getParameter("xml_ff_socialsupport") == null || request.getParameter("xml_ff_socialsupport").isEmpty() ? "&nbsp;" : SafeEncode.forHtml(request.getParameter("xml_ff_socialsupport")) %> <%-- NOSONAR java:S5131 — encoded via Encode.forHtml() --%><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
        </TD>
    </TR>
    <TR>
        <TD WIDTH="50%" VALIGN="TOP"><B><FONT Size="-1"
                                              FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.stressfulLifeEvents"/></FONT></B> (<B><I>CA,
            WA, PD,</I></B> <FONT SIZE="-1">PI</FONT>)<BR>
            <FONT SIZE="-1" FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.stressfulLifeEvents.q1"/></FONT><BR>
            <FONT SIZE="-1" FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.stressfulLifeEvents.q2"/></FONT></TD>
        <TD VALIGN="TOP"><%= request.getParameter("xml_ff_recentstressfullifeevents") == null || request.getParameter("xml_ff_recentstressfullifeevents").isEmpty() ? "&nbsp;" : SafeEncode.forHtml(request.getParameter("xml_ff_recentstressfullifeevents")) %> <%-- NOSONAR java:S5131 — encoded via Encode.forHtml() --%><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
        </TD>
    </TR>
    <TR>
        <TD WIDTH="50%" VALIGN="TOP"><B><FONT SIZE="-1"
                                              FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.coupleRelationship"/></FONT></B> (<B><I>CD,
            PD,</I></B> <FONT SIZE="-1">WA, CA</FONT>)<BR>
            <FONT SIZE="-1" FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.coupleRelationship.q1"/></FONT><BR>
            <FONT SIZE="-1" FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.coupleRelationship.q2"/></FONT></TD>
        <TD VALIGN="TOP"><%= request.getParameter("xml_ff_couplerelationship") == null || request.getParameter("xml_ff_couplerelationship").isEmpty() ? "&nbsp;" : SafeEncode.forHtml(request.getParameter("xml_ff_couplerelationship")) %> <%-- NOSONAR java:S5131 — encoded via Encode.forHtml() --%><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
        </TD>
    </TR>
    <TR bgcolor="#f9f9f9">
        <TD colspan="2" VALIGN="TOP"><FONT FACE="Arial, Helvetica"
                                           Size="-1"><fmt:message key="provider.formALPHAprint.maternalFactors"/></FONT></TD>
    </TR>
    <TR>
        <TD WIDTH="50%" VALIGN="TOP"><FONT FACE="Arial, Helvetica"
                                           Size="-1"><B><fmt:message key="provider.formALPHAprint.prenatalCareLateOnset"/></B></FONT> (<B><I>WA</I></B>)<BR>
            <FONT SIZE="-1" FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.prenatalCareLateOnset.q1"/></FONT></TD>
        <TD VALIGN="TOP"><%= request.getParameter("xml_mf_prenatalcare") == null || request.getParameter("xml_mf_prenatalcare").isEmpty() ? "&nbsp;" : SafeEncode.forHtml(request.getParameter("xml_mf_prenatalcare")) %> <%-- NOSONAR java:S5131 — encoded via Encode.forHtml() --%><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
        </TD>
    </TR>
    <TR>
        <TD WIDTH="50%" VALIGN="TOP"><B><FONT FACE="Arial, Helvetica"
                                              Size="-1"><fmt:message key="provider.formALPHAprint.prenatalEducation"/></FONT></B>
            (<B><I>CA</I></B>)<BR>
            <FONT SIZE="-1" FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.prenatalEducation.q1"/></FONT></TD>
        <TD VALIGN="TOP"><%= request.getParameter("xml_mf_prenataleducation") == null || request.getParameter("xml_mf_prenataleducation").isEmpty() ? "&nbsp;" : SafeEncode.forHtml(request.getParameter("xml_mf_prenataleducation")) %> <%-- NOSONAR java:S5131 — encoded via Encode.forHtml() --%><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
        </TD>
    </TR>
    <TR>
        <TD WIDTH="50%" VALIGN="TOP"><B><FONT FACE="Arial, Helvetica"
                                              Size="-1"><fmt:message key="provider.formALPHAprint.feelingsTowardPregnancy20Weeks"/></FONT></B> (<B><I>CA,
            WA</I></B>)<BR>
            <FONT SIZE="-1" FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.feelingsTowardPregnancy20Weeks.q1"/></FONT><BR>
            <FONT SIZE="-1" FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.feelingsTowardPregnancy20Weeks.q2"/></FONT></TD>
        <TD VALIGN="TOP"><%= request.getParameter("xml_mf_feelingstopregnancy20") == null || request.getParameter("xml_mf_feelingstopregnancy20").isEmpty() ? "&nbsp;" : SafeEncode.forHtml(request.getParameter("xml_mf_feelingstopregnancy20")) %> <%-- NOSONAR java:S5131 — encoded via Encode.forHtml() --%><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
        </TD>
    </TR>
    <TR>
        <TD WIDTH="50%" VALIGN="TOP"><B><FONT FACE="Arial, Helvetica"
                                              Size="-1"><fmt:message key="provider.formALPHAprint.relationshipWithParentsChildhood"/></FONT></B> (<B><I>CA</I></B>)<BR>
            <FONT SIZE="-1" FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.relationshipWithParentsChildhood.q1"/></FONT><BR>
            <FONT SIZE="-1" FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.relationshipWithParentsChildhood.q2"/></FONT></TD>
        <TD VALIGN="TOP"><%= request.getParameter("xml_mf_relationshipwithparents") == null || request.getParameter("xml_mf_relationshipwithparents").isEmpty() ? "&nbsp;" : SafeEncode.forHtml(request.getParameter("xml_mf_relationshipwithparents")) %> <%-- NOSONAR java:S5131 — encoded via Encode.forHtml() --%><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
        </TD>
    </TR>
    <TR>
        <TD WIDTH="50%" VALIGN="TOP"><B><FONT FACE="Arial, Helvetica"
                                              Size="-1"><fmt:message key="provider.formALPHAprint.selfEsteem"/></FONT></B> (<B><I>CA,</I></B> <FONT
                SIZE="-1">WA</FONT>)<BR>
            <FONT SIZE="-1" FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.selfEsteem.q1"/></FONT></TD>
        <TD VALIGN="TOP"><%= request.getParameter("xml_mf_selfesteem") == null || request.getParameter("xml_mf_selfesteem").isEmpty() ? "&nbsp;" : SafeEncode.forHtml(request.getParameter("xml_mf_selfesteem")) %> <%-- NOSONAR java:S5131 — encoded via Encode.forHtml() --%><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
        </TD>
    </TR>
    <TR>
        <TD WIDTH="50%" VALIGN="TOP"><B><FONT FACE="Arial, Helvetica"
                                              Size="-1"><fmt:message key="provider.formALPHAprint.historyPsychiatricEmotionalProblems"/></FONT></B> (<B><I>CA,
            WA,</I></B> <FONT SIZE="-1">PD</FONT>)<BR>
            <FONT SIZE="-1" FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.historyPsychiatricEmotionalProblems.q1"/></FONT><BR>
            <FONT SIZE="-1" FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.historyPsychiatricEmotionalProblems.q2"/></FONT></TD>
        <TD VALIGN="TOP"><%= request.getParameter("xml_mf_historypsychiatricemaotional") == null || request.getParameter("xml_mf_historypsychiatricemaotional").isEmpty() ? "&nbsp;" : SafeEncode.forHtml(request.getParameter("xml_mf_historypsychiatricemaotional")) %> <%-- NOSONAR java:S5131 — encoded via Encode.forHtml() --%><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
        </TD>
    </TR>
    <TR>
        <TD WIDTH="50%" VALIGN="TOP"><B><FONT FACE="Arial, Helvetica"
                                              Size="-1"><fmt:message key="provider.formALPHAprint.depressionThisPregnancy"/></FONT></B> (<B><I>PD</I></B>)<BR>
            <FONT SIZE="-1" FACE="Arial, Helvetica"><fmt:message key="provider.formALPHAprint.depressionThisPregnancy.q1"/></FONT></TD>
        <TD VALIGN="TOP"><%= request.getParameter("xml_mf_depression") == null || request.getParameter("xml_mf_depression").isEmpty() ? "&nbsp;" : SafeEncode.forHtml(request.getParameter("xml_mf_depression")) %> <%-- NOSONAR java:S5131 — encoded via Encode.forHtml() --%><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
        </TD>
    </TR>
    <TR>
        <TD VALIGN="TOP" ALIGN="CENTER" COLSPAN=2><font
                face="Arial, Helvetica" size="-1"><b><fmt:message key="provider.formALPHAprint.associatedPostpartumOutcomes"/></b><br>
            <fmt:message key="provider.formALPHAprint.associatedPostpartumOutcomes.detail"/> <b><i><fmt:message key="provider.formALPHAprint.associatedPostpartumOutcomes.boldItalic"/></i></b> <fmt:message key="provider.formALPHAprint.associatedPostpartumOutcomes.goodEvidence"/> <b>CA</b>&nbsp;&#150;&nbsp;<fmt:message key="provider.formALPHAprint.associatedPostpartumOutcomes.childAbuse"/> <b>CD</b>&nbsp;&#150;&nbsp;<fmt:message key="provider.formALPHAprint.associatedPostpartumOutcomes.coupleDysfunction"/> <b>PI</b>&nbsp;&#150;&nbsp;<fmt:message key="provider.formALPHAprint.associatedPostpartumOutcomes.physicalIllness"/> <b>PD</b>&nbsp;&#150;&nbsp;<fmt:message key="provider.formALPHAprint.associatedPostpartumOutcomes.postpartumDepression"/> <b>WA</b>&nbsp;&#150;&nbsp;<fmt:message key="provider.formALPHAprint.associatedPostpartumOutcomes.womanAbuse"/></font></TD>
    </TR>
</TABLE>

</BODY>
</HTML>
