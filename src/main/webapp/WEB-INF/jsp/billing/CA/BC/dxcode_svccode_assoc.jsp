<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="java.util.List" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.ServiceCodeAssociation" %>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="oscar.billing.CA.BC.billingBC.dxcode_svccode_assoc.title"/></title>
        <style>
            .assocField { font-family: Verdana, Arial, Helvetica, sans-serif; font-size: x-small; }
        </style>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
        <script language="JavaScript">
            <!--
            var remote = null;

            function rs(n, u, w, h, x) {
                args = "width=" + w + ",height=" + h + ",resizable=yes,scrollbars=yes,status=0,top=60,left=30";
                remote = window.open(u, n, args);
                if (remote != null) {
                    if (remote.opener == null) {
                        remote.opener = self;

                    }
                }
                if (x == 1) {
                    return remote;
                }
            }


            var awnd = null;

            function ScriptAttach() {


                t0 = escape(document.forms[0].xml_diagnostic_detail1.value);
                t1 = escape(document.forms[0].xml_diagnostic_detail2.value);
                t2 = escape(document.forms[0].xml_diagnostic_detail3.value);
                awnd = rs('att', '<%= request.getContextPath() %>/billing/CA/BC/ViewBillingDigNewSearch?name=' + t0 + '&name1=' + t1 + '&name2=' + t2 + '&search=', 600, 600, 1);
                awnd.focus();


            }


            function OtherScriptAttach() {
                var t0 = encodeURIComponent(document.forms[0].xml_other1.value);
                awnd = rs('att', '<rewrite:reWrite jspPage="/billing/CA/BC/ViewBillingCodeNewSearch" context="javaScriptBlock"/>?name=' + t0 + '&name1=&name2=&search=', 820, 660, 1);
                awnd.focus();
            }

            //-->
        </SCRIPT>
        <script language="JavaScript">
        </script>
        <link rel="stylesheet"
              href="<%= request.getContextPath() %>/billing/billing.css" type="text/css">
    </head>
    <%
        String mode = request.getAttribute("mode") != null ? (String) request.getAttribute("mode") : "";
        // The association the editor was opened for (or the one a refused Save posted), so the boxes show its codes.
        ServiceCodeAssociation association = (ServiceCodeAssociation) request.getAttribute("serviceCodeAssociation");
        String serviceCode = association == null || association.getServiceCode() == null ? "" : association.getServiceCode();
        List<?> dxCodes = association == null ? List.of() : association.getDxCodes();
        String[] dx = new String[3];
        for (int i = 0; i < dx.length; i++) {
            dx[i] = i < dxCodes.size() && dxCodes.get(i) != null ? String.valueOf(dxCodes.get(i)) : "";
        }
    %>
    <body bgcolor="#FFFFFF" text="#000000" rightmargin="0" leftmargin="0"
          topmargin="10" marginwidth="0" marginheight="0">
    <h2><% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><%= error %></li>
            <% } %>
        </ul>
    </div>
<% } %></h2>
    <form action="${pageContext.request.contextPath}/billing/CA/BC/saveAssocAction" method="post">
        <input type="hidden" name="mode" id="mode" value="<carlos:encode value='<%= mode %>' context="htmlAttribute"/>"/>
        <table width="75%" border="1" align="center" cellpadding="3"
               cellspacing="3" bgcolor="EEEEFF">
            <tr bgcolor="#000000">
                <td width="90%" height="40" align="left">
                    <p><font face="Verdana, Arial, Helvetica, sans-serif"
                             color="#FFFFFF"> <b> <font
                            face="Arial, Helvetica, sans-serif" size="2"><fmt:message key="oscar.billing.CA.BC.billingBC.dxcode_svccode_assoc.title"/></font> </b> </font>
                    </p>
                </td>
            </tr>
            <tr bgcolor="CCCCFF">
                <td><strong><label for="xml_other1"><fmt:message key="oscar.billing.CA.BC.billingBC.dxcode_svccode_assoc.step1"/></label></strong></td>
            </tr>
            <tr>
                <td width="78%"><span class="assocField"><%
                    boolean state = mode.equals("edit") ? true : false;
                %> <%-- Read-only only while editing: the save replaces that service code's diagnoses. --%>
                <input type="text" name="xml_other1" id="xml_other1" size="40" value="<carlos:encode value='<%= serviceCode %>' context="htmlAttribute"/>" <%= state ? "readonly" : "" %>/> <a href="javascript:OtherScriptAttach()">
                    <img src="<%= request.getContextPath() %>/images/search_code.jpg" border="0" alt="<fmt:message key='global.btnSearch'/>"> </a> </span></td>
            </tr>
            <tr bgcolor="CCCCFF">
                <td><strong><fmt:message key="oscar.billing.CA.BC.billingBC.dxcode_svccode_assoc.step2"/></strong></td>
            </tr>
            <tr>
                <td><span class="assocField">
                    <label for="xml_diagnostic_detail1"><fmt:message key="oscar.billing.CA.BC.billingBC.dxcode_svccode_assoc.dxcode"/></label>
                    <input type="text" name="xml_diagnostic_detail1" id="xml_diagnostic_detail1" size="25" value="<carlos:encode value='<%= dx[0] %>' context="htmlAttribute"/>"/> </span> <span
                        class="assocField"> <a
                        href="javascript:ScriptAttach()"> <img
                        src="<%= request.getContextPath() %>/images/search_dx_code.jpg" border="0" alt="<fmt:message key='global.btnSearch'/>"> </a> </span>
                </td>
            </tr>
            <!--This is really quite redundant but its code copied from bcBilling.jsp
          using the same search screens, therefore the hidden fields need to be here so that the javascript doesn't break -->
            <input type="hidden" name="xml_diagnostic_detail2" id="xml_diagnostic_detail2" value="<carlos:encode value='<%= dx[1] %>' context="htmlAttribute"/>"/>
            <input type="hidden" name="xml_diagnostic_detail3" id="xml_diagnostic_detail3" value="<carlos:encode value='<%= dx[2] %>' context="htmlAttribute"/>"/>
            <!--Technically don't need three but just in case
      <tr>
        <td>
          <font face="Verdana, Arial, Helvetica, sans-serif" size="1">            DX Code #2:
            <input type="checkbox" name="xml_diagnostic_detail2" size="25" />
          </font>
        </td>
      </tr>
      <tr>
        <td>
          <font face="Verdana, Arial, Helvetica, sans-serif" size="1">            DX Code #3:
            <input type="checkbox" name="xml_diagnostic_detail3" size="25" />
          </font>
        </td>
      </tr>
      <tr>
        -->
            <td></td>
            </tr>
            <tr bgcolor="CCCCFF">
                <td align="center">
                    <input type="submit" name="submit" value="Save" />
                    <button type="button" onclick="window.close();">Cancel</button>
                </td>
            </tr>
        </table>
    </form>
    </body>
</html>
