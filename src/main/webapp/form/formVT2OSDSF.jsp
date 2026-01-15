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
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Vascular Tracker to OSDEF</title>
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>

    </head>

    <script type="text/javascript">

        function autosubmit() {
            document.forms[0].submit();
            //self.window.close();
        }

    </script>
    <body class="BodyStyle" vlink="#0000FF">
    <!--  -->
    <form method="POST"
          action="https://oscartest.oscarmcmaster.org/osdsf/form/dmtracker.do?id=12345678"
          enctype="multipart/form-data">Uploading data to OSDEF... HbA1c <input
            type="hidden" name="dbl_HbA1c" value="<c:out value="${A1C}"/>"/> LDL
        <input type="hidden" name="dbl_LDL_mM" value="<c:out value="${LDL}"/>"/>
        HDL <input type="hidden" name="dbl_HDL_mM"
                   value="<c:out value="${HDL}"/>"/> Total Cholesterol <input
                type="hidden" name="dbl_TotalCholesterol_mM"
                value="<c:out value="${TCHL}"/>"/> Triglycerides <input type="hidden"
                                                                         name="dbl_Triglycerides"
                                                                         value="<c:out value="${TRIG}"/>"/> Urinary
        Albumin Creatinine Ratio <input type="hidden"
                                        name="dbl_UrinaryAlbuminCreatinineRatio_mgPermmol"
                                        value="<c:out value="${ALCR}"/>"/> 24 hrs albumin Foot check: Ulcer <input
                type="hidden" name="b_FootCheck_NoUlcerSeen"
                value="<c:out value="${FTUL}"/>"/> Foot check: Ischemia <input
                type="hidden" name="b_FootCheck_NoIschemiaSeen"
                value="<c:out value="${FTIS}"/>"/> Foot check: Neuropathy <input
                type="hidden" name="b_FootCheck_NoNeuropathySeen"
                value="<c:out value="${FTNE}"/>"/> Eye Exam: Diabetic Retinopathy <input
                type="hidden" name="b_EyeCheck_NoDiabeticRetinopathySeen"
                value="<c:out value="${EYED}"/>"/> Eye Exam: Hypertensive Retinopathy
        <input type="hidden" name="b_EyeCheck_NoHypertensiveRetinopathySeen"
               value="<c:out value="${EYEH}"/>"/> Vascular Specialist Blood pressure
        SBP: <c:out value="${SBP}"/> DBP: <c:out value="${DBP}"/> DateBP: 
            <c:out value="${DateBP}"/> <input type="hidden" name="int_SBP_mmHg"
                                       value="<c:out value="${SBP}"/>"/> <input type="hidden"
                                                                                 name="int_DBP_mmHg"
                                                                                 value="<c:out value="${DBP}"/>"/>
        <input
                type="hidden" name="int_BP_mmHg" value="<c:out value="${DateBP}"/>"/>
        Weight <input type="hidden" name="int_Weight_kg"
                      value="<c:out value="${WT}"/>"/> Height <input type="hidden"
                                                                      name="int_Height_kg"
                                                                      value="<c:out value="${HT}"/>"/> smoking number
        of pack of cigarette per day <input type="hidden"
                                            name="int_CumulativeSmoking_PackYears"
                                            value="<c:out value="${SMKP}"/>"/> number of cigarette per day <input
                type="hidden" name="int_AvgSmokingcurrently_CigsPerDay"
                value="<c:out value="${SMKC}"/>"/> exercise
        <script>
            autosubmit();
        </script>
    </form>
    </body>
</html>
