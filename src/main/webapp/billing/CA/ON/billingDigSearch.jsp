<%--

    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<!DOCTYPE html>
<%
    String user_no = (String) session.getAttribute("user");
%>
<%@ page import="java.util.*, java.sql.*, io.github.carlos_emr.*, java.net.*" errorPage="/errorpage.jsp" %>

<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DiagnosticCode" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    DiagnosticCodeDao diagnosticCodeDao = SpringUtils.getBean(DiagnosticCodeDao.class);
%>
<% String search = "", search2 = "";
    search = request.getParameter("search");
    if (search.compareTo("") == 0) {
        search = "search_diagnostic_code";
    }


    String codeName = request.getParameter("name");

    // Validate name2 as safe JS property path to prevent script injection
    String name2 = request.getParameter("name2");
    if (name2 != null && !name2.matches("[a-zA-Z0-9_.\\[\\]]+")) {
        name2 = null;
    }
%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingDigSearch.title"/></title>
        <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet"> <!-- Bootstrap -->
        <script>
            function CodeAttach(File2) {
                if (self.opener.callChangeCodeDesc) self.opener.callChangeCodeDesc();

                <%if(name2 != null) {%>
                self.opener.<%=name2%> = File2.substring(0, 3);
                <%} else {%>
                self.opener.document.forms[1].xml_diagnostic_detail.value = File2;
                <%}%>
                setTimeout("self.close();", 100);
            }

            function setfocus() {
                this.focus();
                document.forms[0].codedesc.focus();
                document.forms[0].codedesc.select();
            }
        </script>

    </head>

    <body onLoad="setfocus()">
    <table style="width:100%">
        <tr>
            <th style="text-align:center; background-color:silver;"><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingDigSearch.msgDiagnostic"/><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingDigSearch.msgMaxSelections"/></th>
        </tr>
    </table>
    <% String coderange = request.getParameter("coderange");
        String codedesc = request.getParameter("codedesc");
        if (codedesc != null) {
            if (codedesc.compareTo("") == 0) {

                codeName = coderange;
            } else {
                codeName = codedesc;
            }
        }
    %>

    <form name="codesearch" id="codesearch" method="post"
          action="billingDigSearch.jsp">
        <%if (name2 != null) {%>
        <input type="hidden" name="name2"
               value="<%=Encode.forHtmlAttribute(name2)%>"/>
        <%}%>
        <p><b><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingDigSearch.msgRefine"/></b><br>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingDigSearch.msgCodeRange"/>: <select
                    name="coderange">
                <option value="0" selected>000-099</option>
                <option value="1">100-199</option>
                <option value="2">200-299</option>
                <option value="3">300-399</option>
                <option value="4">400-499</option>
                <option value="5">500-599</option>
                <option value="6">600-699</option>
                <option value="7">700-799</option>
                <option value="8">800-899</option>
                <option value="9">900-999</option>
            </select> <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingDigSearch.msgOR"/> <br/>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingDigSearch.msgDescription"/>: <input
                    type="text" name="codedesc" value=""> <input type="submit" class="btn btn-secondary"
                                                                 name="search1"
                                                                 value="<fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingDigSearch.btnSearch"/>"/>
        </p>
        <input type="hidden" name="search"
               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingDigSearch.btnSearch"/>"/>
    </form>

    <form name="diagcode" id="diagcode" method="post"
          action="billingDigUpdate.jsp">
        <table style="width:800px; margin:auto" class="table-striped table-sm">
            <thead>
            <tr>
                <th style="width:12%"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingDigSearch.formCode"/></b></th>
                <th style="width:88%"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingDigSearch.formDescription"/></b></th>
            </tr>
            </thead>
            <tbody>
            <% ResultSet rslocal = null;
                ResultSet rslocal2 = null;
                String Dcode = "", DcodeDesc = "", Dcode2 = "", DcodeDesc2 = "";
                String codeName2 = "";

                int Count = 0;
                int intCount = 0;
                String numCode = "";
                String textCode = "";
                String searchType = "";
                for (int i = 0; i < codeName.length(); i++) {
                    String c = codeName.substring(i, i + 1);
                    if (c.hashCode() >= 48 && c.hashCode() <= 58)
                        numCode += c;
                }
                for (int j = 0; j < codeName.length(); j++) {
                    String d = codeName.substring(j, j + 1);
                    if (d.hashCode() < 48 || d.hashCode() > 58)
                        textCode += d;
                }
                if (textCode.compareTo("") == -1 && textCode != null) {
                    StringBuffer sBuffer = new StringBuffer(textCode);
                    int k = textCode.indexOf(' ');
                    sBuffer.deleteCharAt(k);
                    sBuffer.insert(k, "");
                    textCode = sBuffer.toString();
                }
                if (numCode.compareTo("") == 0) {
                    if (textCode.compareTo("") == 0) {
                        // search all case
                        codeName = numCode;
                        search = "search_diagnostic_code";
                        searchType = "N";
                    } else {
                        //search text only
                        codeName = "%" + textCode;
                        search = "search_diagnostic_text";
                        searchType = "N";
                    }
                } else {

                    if (textCode.compareTo("") == 0) {
                        // search number only
                        codeName = numCode;
                        search = "search_diagnostic_code";
                        searchType = "N";
                    } else {
                        //search both text and number only
                        codeName = "%" + textCode;
                        codeName2 = numCode;
                        search = "search_diagnostic_text";
                        search2 = "search_diagnostic_code";
                        searchType = "BOTH";
                    }
                }

                List<DiagnosticCode> results = null;

                if (searchType.length() == 1) {

// Retrieving Provider

                    if ("search_diagnostic_code".equals(search)) {
                        results = diagnosticCodeDao.searchCode(codeName + "%");
                    } else if ("search_diagnostic_text".equals(search)) {
                        results = diagnosticCodeDao.searchText(codeName + "%");
                    }
                    for (DiagnosticCode result : results) {
                        intCount++;
                        Dcode = result.getDiagnosticCode();
                        DcodeDesc = result.getDescription().trim();
                        if (Count == 0) {
                            Count = 1;
                        } else {
                            Count = 0;
                        }
            %>

            <tr>
                <td style="width:12%"><a
                        href="javascript:CodeAttach('<%=Dcode%>|<%=DcodeDesc%>')"><%=Dcode%>
                </a></td>
                <td style="width:88%"><input type="text" class="form-control" style="margin-bottom: 0px;"
                                             name="<%=Dcode%>"
                                             value="<%=DcodeDesc%>">&nbsp;<input type="submit" class="btn btn-secondary"
                                                                                 name="update"
                                                                                 value="<fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingDigSearch.btnUpdate"/> <%=Dcode%>">
                </td>
            </tr>
            <%
                } //end of while looop
            } else { //both

                results = diagnosticCodeDao.searchText(codeName + "%");
                for (DiagnosticCode result : results) {
                    intCount++;
                    Dcode = result.getDiagnosticCode();
                    DcodeDesc = result.getDescription().trim();
                    if (Count == 0) {
                        Count = 1;
                    } else {
                        Count = 0;
                    }

            %>

            <tr>
                <td style="width:12%"><a
                        href="javascript:CodeAttach('<%=Dcode%>|<%=DcodeDesc%>')"><%=Dcode%>
                </a></td>
                <td style="width:88%"><input type="text" class="form-control" style="margin-bottom: 0px;"
                                             name="<%=Dcode%>"
                                             value="<%=DcodeDesc%>">&nbsp;<input type="submit" class="btn btn-secondary"
                                                                                 name="update"
                                                                                 value="<fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingDigSearch.btnUpdate"/> <%=Dcode%>">
                </td>
            </tr>
            <%
                }


                results = diagnosticCodeDao.searchCode(codeName2 + "%");
                for (DiagnosticCode result : results) {
                    intCount++;
                    Dcode2 = result.getDiagnosticCode();
                    DcodeDesc2 = result.getDescription().trim();
                    if (Count == 0) {
                        Count = 1;
                    } else {
                        Count = 0;
                    }
            %>

            <tr>
                <td style="width:12%"><a
                        href="javascript:CodeAttach('<%=Dcode2%>|<%=DcodeDesc2%>')"><%=Dcode2%>
                </a></td>
                <td style="width:88%"><input type="text" class="form-control" style="margin-bottom: 0px;"
                                             name="<%=Dcode2%>"
                                             value="<%=DcodeDesc2%>">&nbsp;<input type="submit" class="btn btn-secondary"
                                                                                  name="update"
                                                                                  value="<fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingDigSearch.btnUpdate"/> <%=Dcode2%>">
                </td>
            </tr>
            <%
                    }
                }
            %>

            <% if (intCount == 0) { %>
            <tr>
                <td colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.billingDigSearch.msgNoMatch"/>. <%// =i%></td>

            </tr>
            <% }%>

            <% if (intCount == 1) { %>
            <script LANGUAGE="JavaScript">
                <!--
                CodeAttach('<%=Dcode%>|<%=DcodeDesc%>');
                -->

            </script>
            <% } %>
            </tbody>
        </table>
    </form>
    <p>&nbsp;</p>
    <p>&nbsp;</p>
    <h3>&nbsp;</h3>
    </body>
</html>