<%--

    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".

--%>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<%@page import="ca.openosp.openo.commn.model.ClinicNbr" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.dao.ClinicNbrDao" %>
<%@page import="ca.openosp.OscarProperties, ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page
        import="java.sql.*, java.util.*, ca.openosp.*, ca.openosp.SxmlMisc, ca.openosp.openo.providers.data.ProviderBillCenter"
        errorPage="/errorpage.jsp" %>

<html>
    <head>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/global.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/jquery.js"></script>
        <title>Clinic NBR Management Start Time : <%=OscarProperties.getInstance().getStartTime()%>
        </title>
        <script type="text/javascript">
            function toggleButtons(visible) {
                jQuery("input[type='submit']").attr("disabled", visible);
            }

            function removeCode() {
                toggleButtons(true);
                jQuery.getJSON("clinicNbrManage.json",
                    {
                        method: "remove",
                        nbr: jQuery("#xml_p_nbr").val()
                    },
                    handleJSON);
                return false;

            }

            function addCode() {
                toggleButtons(true);
                jQuery.getJSON("clinicNbrManage.json",
                    {
                        method: "add",
                        nbr: jQuery("#nbr_value").val(),
                        nbrDesc: jQuery("#nbr_string").val()
                    },
                    handleJSON);
                return false;
            }

            function handleJSON(xml) {
                if (xml.method == "remove") {
                    if (xml.success) {
                        jQuery("#xml_p_nbr option[value='" + xml.nbr + "']").remove();
                    } else {
                        alert(xml.error);
                    }
                } else if (xml.method == "add") {
                    if (xml.success) {
                        jQuery("#xml_p_nbr").append("<option value='" + xml.nbr + "'>" + xml.nbr + " | " + xml.nbrDesc + "</option>");
                        jQuery("#nbr_value").val("");
                        jQuery("#nbr_string").val("");
                        jQuery("#xml_p_nbr").val(xml.nbr);
                    } else {
                        alert(xml.error);
                    }
                } else {
                    alert("Error unknown method " + xml.method + " please contact an administrator");
                }
                toggleButtons(false);
            }
        </script>

    </head>

    <body>

    <form method="post" action="" name="manageNRB">
        <table>

            <tr bgcolor="#486ebd">
                <th align="LEFT" colspan="3"><font face="Helvetica" color="#FFFFFF">Remove NRB Code</font></th>
            </tr>

            <tr>
                <td align="right">Current NBR codes in database:</td>
                <td>
                    <select id="xml_p_nbr" name="xml_p_nbr">
                        <%
                            ClinicNbrDao clinicNbrDAO = (ClinicNbrDao) SpringUtils.getBean(ClinicNbrDao.class);
                            List<ClinicNbr> nbrList = clinicNbrDAO.findAll();
                            Iterator<ClinicNbr> nbrIter = nbrList.iterator();
                            while (nbrIter.hasNext()) {
                                ClinicNbr tempNbr = nbrIter.next();
                                String valueString = tempNbr.getNbrValue() + " | " + tempNbr.getNbrString();
                        %>
                        <option value="<%=tempNbr.getId()%>"><%=valueString%>
                        </option>
                        <%}%>

                    </select>
                </td>

                <td>
                    <input type="submit" name="subbutton" value="Remove Selected Code" onclick="return removeCode();">
                </td>
            </tr>
        </table>
    </form>
    <form method="post" action="" name="manageNRB">
        <table>
            <tr bgcolor="#486ebd">
                <th align="LEFT" colspan="3"><font face="Helvetica" color="#FFFFFF">Add NBR Code</font></th>
            </tr>
            <tr>
                <td>
                    Value: <input id="nbr_value" type="text" name="nbr_value"/>
                </td>
                <td>
                    Description: <input id="nbr_string" type="text" name="nbr_string"/>
                </td>
                <td>
                    <input type="submit" name="subbutton" value="Add New Code" onclick="return addCode();">
                </td>
            </tr>
        </table>
    </form>
    </body>

</html>
