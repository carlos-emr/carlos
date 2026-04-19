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
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_lab" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_lab");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="java.io.Serializable" %>
<%@page import="java.util.*,io.github.carlos_emr.carlos.lab.ca.on.*,io.github.carlos_emr.carlos.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.on.CommonLabTestValues" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.on.LabResultData" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%


    String ran = "" + Math.random();

    String demoNo = request.getParameter("demographicNo");
    String labType = request.getParameter("labType");
    String testName = request.getParameter("testName");
    String identCode = request.getParameter("identCode");

    ArrayList<Map<String, Serializable>> list = CommonLabTestValues.findValuesForTest(labType, Integer.valueOf(demoNo), testName, identCode);

%>
<div class="preventionSection" id="preventionSection<%=ran%>">
    <div class="headPrevention" id="headPrevention<%=ran%>">
        <p><a id="ahead<%=ran%>"
              title="fade=[on] header=[<carlos:encode value='<%= testName %>' context="htmlAttribute"/>] body=[]"
              href="javascript: function myFunction() {return false; }"> <span
                title="<%=""%>" style="font-weight: bold;"> <carlos:encode value='<%= StringUtils.maxLenString(testName, 10, 8, "...") %>' context="html"/>
<%=""/*testName*/%> </span> </a> <!--&nbsp;
               <a href="">#</a--> <br/>
        </p>
    </div>
    <%
        for (int k = 0; k < list.size(); k++) {
            HashMap hMap = (HashMap) list.get(k);
            String labNo = String.valueOf(hMap.get("lab_no"));
            String providerNo = String.valueOf(session.getAttribute("user"));
            String labDisplayLink = "";
            if (labType.equals(LabResultData.MDS)) {
                labDisplayLink = request.getContextPath() + "/oscarMDS/ViewSegmentDisplay?segmentID=" + java.net.URLEncoder.encode(labNo, java.nio.charset.StandardCharsets.UTF_8) + "&providerNo=" + java.net.URLEncoder.encode(providerNo, java.nio.charset.StandardCharsets.UTF_8);
            } else if (labType.equals(LabResultData.CML)) {
                labDisplayLink = request.getContextPath() + "/lab/CA/ON/ViewCMLDisplay?segmentID=" + java.net.URLEncoder.encode(labNo, java.nio.charset.StandardCharsets.UTF_8) + "&providerNo=" + java.net.URLEncoder.encode(providerNo, java.nio.charset.StandardCharsets.UTF_8);
            } else if (labType.equals(LabResultData.HL7TEXT)) {
                labDisplayLink = request.getContextPath() + "/lab/CA/ALL/ViewLabDisplay?segmentID=" + java.net.URLEncoder.encode(labNo, java.nio.charset.StandardCharsets.UTF_8) + "&providerNo=" + java.net.URLEncoder.encode(providerNo, java.nio.charset.StandardCharsets.UTF_8);
            } else if (labType.equals(LabResultData.EXCELLERIS)) {
                labDisplayLink = request.getContextPath() + "/lab/CA/BC/ViewLabDisplay?segmentID=" + java.net.URLEncoder.encode(labNo, java.nio.charset.StandardCharsets.UTF_8) + "&providerNo=" + java.net.URLEncoder.encode(providerNo, java.nio.charset.StandardCharsets.UTF_8);
            }

    %>
    <div style="text-align: justify;"
         title="fade=[on] header=[<carlos:encode value='<%= String.valueOf(hMap.get("result")) %>' context="htmlAttribute"/>] body=[<carlos:encode value='<%= String.valueOf(hMap.get("units")) %>' context="htmlAttribute"/> <carlos:encode value='<%= String.valueOf(hMap.get("range")) %>' context="htmlAttribute"/>]"
         class="preventionProcedure" id="preventionProcedure<%=""+k+""+ran%>"
         onclick="javascript:popup(660,960,'<carlos:encode value='<%= labDisplayLink %>' context="javaScriptAttribute"/>','labReport')">
        <p <%=r(hMap.get("abn"))%>><carlos:encode value='<%= String.valueOf(hMap.get("result")) %>' context="html"/>
            &nbsp;&nbsp;&nbsp; <carlos:encode value='<%= String.valueOf(hMap.get("collDate")) %>' context="html"/>
        </p>
    </div>
    <%}%>
</div>


<script type="text/javascript">
    ///alert("HI");
    //var ele = document.getElementById("preventionSection<%=ran%>");
    //alert(ele);
    <%for (int k =0; k < list.size(); k++){ %>
    Rounded("div#preventionProcedure<%=""+k+""+ran%>", "all", "#CCF", "#efeadc", "small border blue");
    scanDOM(document.getElementById("preventionProcedure<%=""+k+""+ran%>"));
    <%}%>
    Rounded("div#headPrevention<%=ran%>", "all", "transparent", "#F0F0E7", "small border #999");

    scanDOM(document.getElementById("ahead<%=ran%>"));
</script>


<%!
    String r(Object re) {
        String ret = "";
        if (re instanceof java.lang.String) {
            if (re != null && re.equals("A")) {
                ret = "style=\"background: #FFDDDD;\"";
            } else if (re != null && re.equals("2")) {
                ret = "style=\"background: #FFCC24;\"";
            }
        }
        return ret;
    }
%>
