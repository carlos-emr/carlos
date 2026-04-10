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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_billing" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_billing");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%
    //
    if (session.getAttribute("user") == null) {
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
    }
    String strLimit1 = "0";
    String strLimit2 = "20";
    if (request.getParameter("limit1") != null)
        strLimit1 = request.getParameter("limit1");
    if (request.getParameter("limit2") != null)
        strLimit2 = request.getParameter("limit2");

    int nItems = 0;
    Vector vec = new Vector();
    Properties prop = null;
    String param = request.getParameter("param") == null ? "" : request.getParameter("param");
    String param2 = request.getParameter("param2") == null ? "" : request.getParameter("param2");
    String keyword = request.getParameter("keyword");

    if (request.getParameter("submit") != null
            && (request.getParameter("submit").equals("Search")
            || request.getParameter("submit").equals("Next Page") || request.getParameter("submit")
            .equals("Last Page"))) {
        DBPreparedHandler dbObj = new DBPreparedHandler();
        String search_mode = request.getParameter("search_mode") == null ? "search_name" : request
                .getParameter("search_mode");
        String orderBy = request.getParameter("orderby") == null ? "company_name" : request
                .getParameter("orderby");

        // Validate orderBy against allowlist to prevent SQL injection
        java.util.Set<String> VALID_COLUMNS = java.util.Set.of("company_name", "attention", "address", "city", "province", "postcode", "telephone", "fax", "id");
        if (!VALID_COLUMNS.contains(orderBy)) {
            orderBy = "company_name";
        }

        String where = "";
        java.util.List<String> params = new java.util.ArrayList<>();
        if ("search_name".equals(search_mode)) {
            String[] temp = keyword.split("\\,\\p{Space}*");
            if (temp.length > 1) {
                where = "company_name like ? and company_name like ?";
                params.add(temp[0] + "%");
                params.add(temp[1] + "%");
            } else {
                where = "company_name like ?";
                params.add(temp[0] + "%");
            }
        } else {
            // Validate search_mode column name
            if (!VALID_COLUMNS.contains(search_mode)) {
                search_mode = "company_name";
            }
            where = search_mode + " like ?";
            params.add(keyword + "%");
        }
        String sql = "select * from billing_on_3rdPartyAddress where " + where + " order by " + orderBy;
        ResultSet rs = dbObj.queryResults_paged(sql, params.toArray(new String[0]), Integer.parseInt(strLimit1));
        int idx = 0;
        while (rs.next() && idx < Integer.parseInt(strLimit2)) {
            prop = new Properties();
            prop.setProperty("id", Misc.getString(rs, "id"));
            prop.setProperty("attention", Misc.getString(rs, "attention"));
            prop.setProperty("company_name", Misc.getString(rs, "company_name"));
            prop.setProperty("address", Misc.getString(rs, "address"));
            prop.setProperty("city", Misc.getString(rs, "city"));
            prop.setProperty("province", Misc.getString(rs, "province"));
            prop.setProperty("postcode", Misc.getString(rs, "postcode"));
            prop.setProperty("telephone", Misc.getString(rs, "telephone"));
            prop.setProperty("fax", Misc.getString(rs, "fax"));
            vec.add(prop);
            idx++;
        }
    }
%>
<%@ page errorPage="/errorpage.jsp"
         import="java.util.*,java.sql.*,java.net.*" %>

<%@ page import="org.apache.commons.text.WordUtils" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>


<%@page import="io.github.carlos_emr.Misc" %>
<%@ page import="io.github.carlos_emr.carlos.db.DBPreparedHandler" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Add/Edit 3rd Bill Address</title>
        <link rel="stylesheet" type="text/css" href="billingON.css"/>
        <script language="JavaScript">

            <!--
            function setfocus() {
                this.focus();
                document.forms[0].keyword.focus();
                document.forms[0].keyword.select();
            }

            function check() {
                document.forms[0].submit.value = "Search";
                return true;
            }

            function setOpenerProperty(path, value) {
                var tokens = path.match(/[^.\[\]'"]+/g);
                if (!tokens || tokens.length === 0) return;
                var obj = opener;
                for (var i = 0; i < tokens.length - 1; i++) {
                    if (obj == null) return;
                    obj = obj[tokens[i]];
                }
                if (obj != null) obj[tokens[tokens.length - 1]] = value;
            }

            <%if(param.length()>0) {%>

            function typeInData1(data) {
                self.close();
                setOpenerProperty('<%= Encode.forJavaScript(param) %>', data);
            }

            <%if(param2.length()>0) {%>

            function typeInData2(data1, data2) {
                setOpenerProperty('<%= Encode.forJavaScript(param) %>', data1);
                setOpenerProperty('<%= Encode.forJavaScript(param2) %>', data2);
                self.close();
            }

            <%}}%>
            -->
            function fillForm(attention, company_name, address, city, province, telephone, fax, post) {
                var atten = '';
                if (attention.length > 0) {
                    atten = " Attn: " + attention
                }
                opener.document.forms[0].recipientName.value = company_name + atten;
                opener.document.forms[0].recipientAddress.value = address;
                opener.document.forms[0].recipientCity.value = city;
                opener.document.forms[0].recipientProvince.value = province;
                opener.document.forms[0].recipientPostal.value = post;
                opener.focus();
                self.close();
            }
        </script>
    </head>
    <body bgcolor="white" bgproperties="fixed" onload="setfocus()"
          topmargin="0" leftmargin="0" rightmargin="0">
    <table border="0" cellpadding="1" cellspacing="0" width="100%"
           class="myDarkGreen">
        <form method="post" name="titlesearch" action="onSearch3rdBillAddr.jsp"
              onSubmit="return check();">
            <tr>
                <td class="searchTitle" colspan="4"><font color="white">Search
                    Address</font></td>
            </tr>
            <tr class="myYellow">
                <td class="blueText" width="10%" nowrap><input type="radio"
                                                               name="search_mode" value="search_name" checked> Name
                </td>
                <td class="blueText" nowrap><input type="radio"
                                                   name="search_mode" value="postcode"> Postcode
                </td>
                <td class="blueText" nowrap><input type="radio"
                                                   name="search_mode" value="telephone"> Tel.
                </td>
                <td valign="middle" rowspan="2" align="left"><input type="text"
                                                                    name="keyword" value="" size="17" maxlength="100">
                    <input
                            type="hidden" name="orderby" value="company_name"> <input
                            type="hidden" name="limit1" value="0"> <input type="hidden"
                                                                          name="limit2" value="20"> <input type="hidden"
                                                                                                           name="submit"
                                                                                                           value='Search'>
                    <input type="submit" value='Search'>
                </td>
            </tr>
    </table>
    <input type='hidden' name='param'
           value="<%=Encode.forHtmlAttribute(param)%>">
    <input type='hidden' name='param2'
           value="<%=Encode.forHtmlAttribute(param2)%>">
    <table width="95%" border="0">
        <tr>
            <td align="left">Results based on keyword(s): <%= Encode.forHtml(keyword == null ? "" : keyword) %>
            </td>
        </tr>
        </form>
    </table>
    <center>
        <table width="100%" border="0" cellpadding="0" cellspacing="2"
               class="myYellow">
            <tr class="title">
                <th width="20%">Attention</b></th>
                <th width="20%">Company name</b></th>
                <th width="25%">Address</b></th>
                <th width="10%">City</b></th>
                <th width="10%">Postcode</b></th>
                <th>Phone</b></th>
                <!--  >th width="20%">Fax</b></th-->
            </tr>
            <%
                for (int i = 0; i < vec.size(); i++) {
                    prop = (Properties) vec.get(i);
                    String bgColor = i % 2 == 0 ? "#EEEEFF" : "ivory";
                    String strOnClick = param.length() > 0 ? "typeInData1('"
                            + Encode.forJavaScript((prop.getProperty("attention", "").equals("") ? "" : (prop.getProperty("attention") + "\n")))
                            + Encode.forJavaScript(prop.getProperty("company_name", "").equals("") ? "" : (prop.getProperty("company_name") + "\n"))
                            + Encode.forJavaScript(prop.getProperty("address", "").equals("") ? "" : (prop.getProperty("address") + "\n"))
                            + Encode.forJavaScript(prop.getProperty("city", "").equals("") ? "" : (prop.getProperty("city") + " "))
                            + Encode.forJavaScript(prop.getProperty("province", "").equals("") ? "" : (prop.getProperty("province") + "\n"))
                            + Encode.forJavaScript(prop.getProperty("telephone", "").equals("") ? "" : (prop.getProperty("telephone") + "\n"))
                            + Encode.forJavaScript(prop.getProperty("fax", "").equals("") ? "" : (prop.getProperty("fax") + "\n"))
                            + "')" : "typeInData1('"
                            + Encode.forJavaScript(prop.getProperty("city", "")) + "')";

            %>
            <tr align="center" bgcolor="<%=bgColor%>" align="center"
                onMouseOver="this.style.cursor='hand';this.style.backgroundColor='pink';"
                onMouseout="this.style.backgroundColor='<%=bgColor%>';"
                onClick="fillForm('<%= str(prop.getProperty("attention", ""))%>','<%= str(prop.getProperty("company_name", ""))%>','<%= str(prop.getProperty("address", ""))%>','<%=  str(prop.getProperty("city", ""))%>','<%=  str(prop.getProperty("province", ""))%>','<%=  str(prop.getProperty("telephone", ""))%>','<%=  str(prop.getProperty("fax", ""))%>','<%=  str(prop.getProperty("postcode", ""))%>');">
                <td><%=Encode.forHtml(prop.getProperty("attention", ""))%>
                </td>
                <td><%=Encode.forHtml(WordUtils.capitalize(prop.getProperty("company_name", "").toLowerCase()))%>
                </td>
                <td><%=Encode.forHtml(WordUtils.capitalize(prop.getProperty("address", "").toLowerCase()))%>
                </td>
                <td><%=Encode.forHtml(prop.getProperty("city", ""))%>
                </td>
                <td><%=Encode.forHtml(prop.getProperty("postcode", ""))%>
                </td>
                <td><%=Encode.forHtml(prop.getProperty("telephone", ""))%>
                </td>
                <%-- <td><%=Encode.forHtml(prop.getProperty("fax", ""))%></td> --%>
            </tr>
            <%
                }

            %>
        </table>

        <%
            nItems = vec.size();
            int nLastPage = 0, nNextPage = 0;
            nNextPage = Integer.parseInt(strLimit2) + Integer.parseInt(strLimit1);
            nLastPage = Integer.parseInt(strLimit1) - Integer.parseInt(strLimit2);

        %> <%
        if (nItems == 0 && nLastPage <= 0) {

    %> <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.noResultsWereFound"/> <%
        }
    %>
        <script language="JavaScript">
            <!--
            function last() {
                document.nextform.action = "<%= request.getContextPath() %>/billing/CA/BC/onSearch3rdBillAddr.jsp?param=<%=Encode.forJavaScript(URLEncoder.encode(param,"UTF-8"))%>&param2=<%=Encode.forJavaScript(URLEncoder.encode(param2,"UTF-8"))%>&keyword=<%=Encode.forJavaScript(URLEncoder.encode(StringUtils.noNull(request.getParameter("keyword")), "UTF-8"))%>&search_mode=<%=Encode.forJavaScript(URLEncoder.encode(StringUtils.noNull(request.getParameter("search_mode")), "UTF-8"))%>&orderby=<%=Encode.forJavaScript(URLEncoder.encode(StringUtils.noNull(request.getParameter("orderby")), "UTF-8"))%>&limit1=<%=nLastPage%>&limit2=<%=Encode.forJavaScript(strLimit2)%>";
                document.nextform.submit();
            }

            function next() {
                document.nextform.action = "<%= request.getContextPath() %>/billing/CA/BC/onSearch3rdBillAddr.jsp?param=<%=Encode.forJavaScript(URLEncoder.encode(param,"UTF-8"))%>&param2=<%=Encode.forJavaScript(URLEncoder.encode(param2,"UTF-8"))%>&keyword=<%=Encode.forJavaScript(URLEncoder.encode(StringUtils.noNull(request.getParameter("keyword")), "UTF-8"))%>&search_mode=<%=Encode.forJavaScript(URLEncoder.encode(StringUtils.noNull(request.getParameter("search_mode")), "UTF-8"))%>&orderby=<%=Encode.forJavaScript(URLEncoder.encode(StringUtils.noNull(request.getParameter("orderby")), "UTF-8"))%>&limit1=<%=nNextPage%>&limit2=<%=Encode.forJavaScript(strLimit2)%>";
                document.nextform.submit();
            }

            //-->
        </SCRIPT>

        <form method="post" name="nextform" action="onSearch3rdBillAddr.jsp">
            <%
                if (nLastPage >= 0) {

            %> <input type="submit" class="mbttn" name="submit"
                      value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.btnPrevPage"/>"
                      onClick="last()"> <%
            }
            if (nItems == Integer.parseInt(strLimit2)) {

        %> <input type="submit" class="mbttn" name="submit"
                  value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearch2apptresults.btnNextPage"/>"
                  onClick="next()"> <%
            }
        %>
        </form>
        <br>
        <a href="onAddEdit3rdAddr.jsp">Add/Edit Address</a></center>
    </body>
</html>
<%!
    String str(String d) {
        if (d == null || d.trim().equals("")) {
            return "";
        }
        return org.owasp.encoder.Encode.forJavaScript(d);
    }
%>
