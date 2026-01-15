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

<%
    String demographic_no = request.getParameter("demographic_no") != null
            ? request.getParameter("demographic_no")
            : ("null");
    String form_no = request.getParameter("formId") != null
            ? request.getParameter("formId")
            : ("0");
    String curUser_no = (String) session.getAttribute("user");
%>
<%@ page errorPage="/errorpage.jsp"
         import="java.util.*,
                 java.sql.*,
                 ca.openosp.*,
                 ca.openosp.openo.util.*" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.model.Demographic" %>
<%@page import="ca.openosp.openo.commn.dao.DemographicDao" %>
<%
    DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    String folderPath = pageContext.getServletContext().getRealPath("/decision/annualreview/");
    folderPath = folderPath.endsWith("/") ? folderPath : folderPath + "/";

    if (folderPath == null) {
        throw new NullPointerException("Cannot resolve real path for /decision/annualreview/ - check deployment configuration");
    }
%>

<jsp:useBean id="riskDataBean" class="java.util.Properties" scope="page"/>
<!--jsp:useBean id="risks" class="ca.openosp.decision.DesAnnualReviewPlannerRisk" scope="page" /-->
<jsp:useBean id="risks"
             class="ca.openosp.openo.decision.DesAntenatalPlannerRisks_99_12" scope="page"/>
<jsp:useBean id="checklist"
             class="ca.openosp.openo.decision.DesAnnualReviewPlannerChecklist" scope="page"/>
<%@ include file="/admin/dbconnection.jsp" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.model.DesAnnualReviewPlan" %>
<%@page import="ca.openosp.openo.commn.dao.DesAnnualReviewPlanDao" %>
<%@ page import="ca.openosp.openo.util.UtilDateUtilities" %>
<%@ page import="ca.openosp.SxmlMisc" %>
<%
    DesAnnualReviewPlanDao desAnnualReviewPlanDao = SpringUtils.getBean(DesAnnualReviewPlanDao.class);
%>

<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/decision/annualreview/annualreviewplanner.js"></script>
    <title>ANNUAL HEALTH REVIEW PLANNER</title>
    <script type="text/javascript" language="Javascript">
        function onExit() {
            if (confirm("Are you sure you wish to exit without saving your changes?") == true) {
                window.close();
            }

            return (false);
        }

        function popupPage(vheight, vwidth, varpage) {
            //open a new popup window
            var page = "" + varpage;

            windowprops = "height=" + vheight + ",width=" + vwidth +
                ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,top=5,left=5";

            //360,680
            var popup = window.open(page, "editxml", windowprops);
        }
    </script>
</head>
<body bgproperties="fixed" topmargin="0" leftmargin="1" rightmargin="1">
<form name="planner" method="post"
      action="annualreviewplanner.jsp?demographic_no=<%=demographic_no%>&formId=<%=form_no%>">
    <%
        //save risk&checklist data if required
        if (request.getParameter("submit") != null &&
                (request.getParameter("submit").equals(" Save ") || request.getParameter("submit").equals("Save and Exit"))) {

            String risk_content = "", checklist_content = "";

            risk_content = SxmlMisc.createXmlDataString(request, "risk_");
            checklist_content = SxmlMisc.createXmlDataString(request, "checklist_");

            DesAnnualReviewPlan darp = new DesAnnualReviewPlan();
            darp.setDesDate(new java.util.Date());
            darp.setDesTime(new java.util.Date());
            darp.setProviderNo(curUser_no);
            darp.setRiskContent(risk_content);
            darp.setChecklistContent(checklist_content);
            darp.setDemographicNo(Integer.parseInt(demographic_no));
            darp.setFormNo(Integer.parseInt(form_no));
            desAnnualReviewPlanDao.persist(darp);
        }

        //save & exit if required
        if (request.getParameter("submit") != null && request.getParameter("submit").equals("Save and Exit")) {
            out.print("<script type='text/javascript' language='Javascript'>window.close();</script>");

            return;
        }

        //initial prop bean with "0"
        //  for(int i=0;i<riskdataname.length;i++) {
        //	  riskDataBean.setProperty(riskdataname[i][0],"0");
        //  }
        //get the risk data from table desaprisk for other risk factors

        DesAnnualReviewPlan darp = desAnnualReviewPlanDao.search(Integer.parseInt(form_no), Integer.parseInt(demographic_no));

        String risk_content = "";
        String checklist_content = "";

        // Load all possible risk names from XML file
        Properties savedar1risk1 = risks.getRiskName(folderPath + "desannualreviewplannerrisk.xml");

        if (darp != null) {
            // If we have saved data, populate riskDataBean from saved risks only
            risk_content = darp.getRiskContent();
            checklist_content = darp.getChecklistContent();

            StringBuffer tt;
            for (Enumeration e = savedar1risk1.propertyNames(); e.hasMoreElements(); ) {
                tt = new StringBuffer().append(e.nextElement());

                if (SxmlMisc.getXmlContent(risk_content, "risk_" + tt.toString()) != null) {
                    riskDataBean.setProperty(tt.toString(), savedar1risk1.getProperty(tt.toString()));
                }
            }
        } else {
            // On first load (no saved data), show all possible checklist items
            // by adding all risks to riskDataBean
            for (Enumeration e = savedar1risk1.propertyNames(); e.hasMoreElements(); ) {
                String riskKey = (String) e.nextElement();
                riskDataBean.setProperty(riskKey, savedar1risk1.getProperty(riskKey));
            }
        }

        //find the age and sex of the patient from demographic table
        Demographic d = demographicDao.getDemographic(demographic_no);
        int age = 0;
        String sex = null;

        if (d != null) {
            age = UtilDateUtilities.calcAge(d.getYearOfBirth(), d.getMonthOfBirth(), d.getDateOfBirth());
            sex = d.getSex();
        }

        if (age >= 65 && age <= 85) {
            riskDataBean.setProperty("999", "checked");

            if (sex.equals("f") || sex.equals("F")) {
                riskDataBean.setProperty("998", "checked");
            }

            if (sex.equals("m") || sex.equals("M")) {
                riskDataBean.setProperty("997", "checked");
            }

            if (age >= 65 && age <= 69) {
                riskDataBean.setProperty("996", "checked");
            }
        }
    %>
    <script type="text/xml" id="xml_list">
        <planner><%= risk_content %> <%= checklist_content %></planner>
    </script>
    <table bgcolor='silver' width='100%'>
        <tr>
            <td align="left"><input type="submit" name="submit"
                                    value=" Save "/> <input type="submit" name="submit"
                                                            value="Save and Exit"/> <input type="button"
                                                                                           value="  Exit  "
                                                                                           onclick="javascript:return onExit();"/>
                <input type="button"
                       name="submit" value="Print"
                       onclick="popupPage(700,800,'annualreviewplannerprint.jsp?demographic_no=<%=demographic_no%>&formId=<%=form_no%>');return false;"/>
            </td>
            <td align="right"><a href=#
                                 onClick="popupPage(600,930,'riskedit.jsp');return false;"> Edit
                Risk </a> | <a href=#
                               onClick="popupPage(600,930,'checklistedit.jsp');return false;">
                Edit CheckList </a></td>
        </tr>
    </table>
    <table bgcolor='silver' width='100%'>
        <tr>
            <td width="20%" valign='top'>
                <%
                    out.println(risks.doStuff(folderPath + "desannualreviewplannerrisk.xml"));
                %>
            </td>
            <td>
            <%
                out.println(checklist.doStuff(folderPath + "desannualreviewplannerriskchecklist.xml", riskDataBean));
            %>

        </tr>
    </table>
    <table bgcolor='silver' width='100%'>
        <tr>
            <td align="left"><input type="submit" name="submit"
                                    value=" Save "/> <input type="submit" name="submit"
                                                            value="Save and Exit"/> <input type="button"
                                                                                           value="  Exit  "
                                                                                           onclick="javascript:return onExit();"/>
                <input type="button"
                       name="submit" value="Print"
                       onclick="popupPage(700,800,'annualreviewplannerprint.jsp?demographic_no=<%=demographic_no%>&formId=<%=form_no%>');return false;"/>
            </td>
            <td align="right"><a href=#
                                 onClick="popupPage(600,930,'riskedit.jsp');return false;"> Edit
                Risks </a> | <a href=#
                                onClick="popupPage(600,930,'checklistedit.jsp');return false;">
                Edit CheckList </a></td>
        </tr>
    </table>
</form>
</body>
</html>
