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

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>




<html>

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConfirmConsultationRequest.title"/>
        </title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

    </head>
    <script language="javascript">
        function BackToOscar() {
            window.history.back();
        }

        function finishPage(secs) {
            setTimeout("BackToOscar()", secs * 500);
        }

    </script>


    <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/encounterStyles.css">
    <body topmargin="0" leftmargin="0" vlink="#0000FF"
          onload="finishPage(15);">
    <!--  -->
    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn">Consultation</td>
            <td class="MainTableTopRowRightColumn"></td>
        </tr>
        <tr style="vertical-align: top">
            <td class="MainTableLeftColumn" width="10%">&nbsp;</td>
            <td class="MainTableRightColumn">
                <table width="100%" height="100%">
                    <tr>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.msgNothingPrinted"/>
                        </td>
                    </tr>
                    <tr>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConfirmConsultationRequest.msgClose5Sec"/>
                        </td>
                    </tr>
                    <tr>
                        <td><a href="javascript: BackToOscar();"> <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/> </a></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
    </table>
    </body>
</html>
