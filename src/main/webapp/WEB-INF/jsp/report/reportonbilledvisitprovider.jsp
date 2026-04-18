<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%

    String curUser_no = (String) session.getAttribute("user");
    String[] ROLE = new String[]{"doctor", "resident", "nurse", "social worker", "other"};
%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="java.sql.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.model.security.Secuserrole" %>
<%@ page import="io.github.carlos_emr.carlos.daos.security.SecuserroleDao" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<fmt:setBundle basename="oscarResources"/>
<%
    SecuserroleDao secuserroleDao = (SecuserroleDao) SpringUtils.getBean(SecuserroleDao.class);
    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());
%>
<%
    DBPreparedHandler dbObj = new DBPreparedHandler();
    String submitAddRoles = bundle.getString("report.reportonbilledvisitprovider.btnAddRoles");
    String submitUpdateRole = bundle.getString("report.reportonbilledvisitprovider.btnUpdate");

    // update the role list
    if (request.getParameter("buttonUpdate") != null && request.getParameter("buttonUpdate").length() > 0) {
        String number = request.getParameter("providerId");
        String name = request.getParameter("name" + number);

        List<Secuserrole> surs = secuserroleDao.findByProviderNo(number);
        for (Secuserrole sur : surs) {
            secuserroleDao.updateRoleName(sur.getId(), name);
        }
    }

    // save the role list
    if (request.getParameter("submit") != null && request.getParameter("submit").equals(submitAddRoles)) {
        Properties prop = new Properties();

        List<Secuserrole> surs = secuserroleDao.findAll();
        for (Secuserrole sur : surs) {
            prop.setProperty(sur.getProviderNo(), "");
        }

        for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
            String temp = e.nextElement().toString();

            if (!temp.startsWith("type") || prop.containsKey(temp.substring(4, temp.length()))) {
                continue;
            }

            Secuserrole sur = new Secuserrole();
            sur.setProviderNo(temp.substring(4, temp.length()));
            sur.setRoleName(request.getParameter(temp));
            secuserroleDao.save(sur);

        }
    }
%>
<%@page import="io.github.carlos_emr.carlos.db.DBPreparedHandler" %>
<%@page import="io.github.carlos_emr.carlos.db.DBPreparedHandlerParam" %>

<%@page import="io.github.carlos_emr.Misc" %>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title><fmt:message key="report.reportonbilledvisitprovider.title"/></title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/receptionistapptstyle.css">
    <script language="JavaScript">

        <!--
        function setfocus() {
            this.focus();
            //  document.titlesearch.keyword.select();
        }

        function submit(form) {
            form.submit();
        }

        //-->

    </script>
</head>
<body bgproperties="fixed" onLoad="setfocus()" topmargin="0"
      leftmargin="0" rightmargin="0">
<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr bgcolor="#486ebd">
        <th align="CENTER" width="90%"><font face="Helvetica"
                                             color="#FFFFFF"><fmt:message key="report.reportonbilledvisitprovider.header.providerList"/></font></th>
        <td nowrap><font size="-1" color="#FFFFFF"><fmt:message key="report.reportonbilledvisitprovider.rolesHint"/></font></td>
    </tr>
</table>
<%
    String color = "#ccCCFF";
    Properties prop = new Properties();
    Properties oldRoleProp = new Properties();
    Vector vec = new Vector();
    Vector oldRoleList = new Vector();
    String query = "select u.*, p.first_name, p.last_name from secUserRole u, provider p ";

    query += "where u.provider_no=p.provider_no  order by p.first_name, p.last_name";

    ResultSet rs = dbObj.queryResults(query, new DBPreparedHandlerParam[0]);

    while (rs.next()) {
        oldRoleProp.setProperty(Misc.getString(rs, "provider_no"), Misc.getString(rs, "role_name"));
        oldRoleList.add(Misc.getString(rs, "first_name"));
        oldRoleList.add(Misc.getString(rs, "last_name"));
        oldRoleList.add(Misc.getString(rs, "role_name"));
        oldRoleList.add(Misc.getString(rs, "provider_no"));
    }

    query = "select * from provider order by first_name, last_name";
    rs = dbObj.queryResults(query, new DBPreparedHandlerParam[0]);

    while (rs.next()) {
        if (Misc.getString(rs, "last_name").length() < 1 || oldRoleProp.containsKey((Misc.getString(rs, "provider_no")))) {
            continue;
        }

        prop = new Properties();

        prop.setProperty("provider_no", Misc.getString(rs, "provider_no"));
        prop.setProperty("first_name", Misc.getString(rs, "first_name"));
        prop.setProperty("last_name", Misc.getString(rs, "last_name"));
        prop.setProperty("provider_type", Misc.getString(rs, "provider_type"));
        prop.setProperty("specialty", Misc.getString(rs, "specialty"));
        prop.setProperty("ohip_no", Misc.getString(rs, "ohip_no"));
        vec.add(prop);
    }
%>
<form name="myform" action="<%= request.getContextPath() %>/report/ViewReportonbilledvisitprovider"
      method="POST">
    <table width="100%" border="0" bgcolor="ivory" cellspacing="1"
           cellpadding="1">
        <tr bgcolor="mediumaquamarine">
            <th colspan="7" align="left"><fmt:message key="report.reportonbilledvisitprovider.header.newProviderRoleList"/></th>
            <td align="right"><input type="submit" name="submit"
                                     value="<fmt:message key='report.reportonbilledvisitprovider.btnAddRoles'/>"></td>
        </tr>
        <tr bgcolor="silver">
            <th width="30%" nowrap><fmt:message key="report.reportonbilledvisitprovider.header.id"/></th>
            <th width="30%" nowrap><b><fmt:message key="report.reportonbilledvisitprovider.header.firstName"/></b></th>
            <th width="30%" nowrap><b><fmt:message key="report.reportonbilledvisitprovider.header.lastName"/></b></th>
            <!--th width="10%" nowrap>
                <b>providers type</b>
              </th>
              <th width="10%" nowrap>
                <b>specialty</b>
              </th>
              <th width="10%" nowrap>
                <b>ohip_no</b>
              </th-->
            <th width="5%" nowrap><fmt:message key="report.reportonbilledvisitprovider.role.fp"/> <br>
                <fmt:message key="report.reportonbilledvisitprovider.role.doctor"/>
            </th>
            <th width="5%" nowrap><fmt:message key="report.reportonbilledvisitprovider.role.rfp"/> <br>
                <fmt:message key="report.reportonbilledvisitprovider.role.resident"/>
            </th>
            <th width="5%" nowrap><fmt:message key="report.reportonbilledvisitprovider.role.np"/> <br>
                <fmt:message key="report.reportonbilledvisitprovider.role.nurse"/>
            </th>
            <th width="5%" nowrap><fmt:message key="report.reportonbilledvisitprovider.role.sw"/> <br>
                <fmt:message key="report.reportonbilledvisitprovider.role.socialWorker"/>
            </th>
            <th width="5%" nowrap><fmt:message key="report.reportonbilledvisitprovider.role.ot"/></th>
        </tr>
        <%
            for (int i = 0; i < vec.size(); i++) {
                boolean bDoc = false;
                boolean bRes = false;
                boolean bNp = false;
                boolean bSw = false;
                boolean bOt = false;
                String providerType = ((Properties) vec.get(i)).getProperty("provider_type", "");

                if (((Properties) vec.get(i)).getProperty("ohip_no", "").length() > 3) {
                    bDoc = true;
                } else if (providerType.matches(".*[rR][eE][sS][iI][dD][eE][nN][tT].*")) {
                    bRes = true;
                } else if (providerType.matches(".*[nN][pP].*|.*[nN][uU][rR][sS][eE].*")) {
                    bNp = true;
                } else {
                    bOt = true;
                }
        %>
        <tr bgcolor="<%=i%2==0?"white":color%>">
            <td><e:forHtmlContent value='<%= ((Properties) vec.get(i)).getProperty("provider_no", "") %>' />
            </td>
            <td><e:forHtmlContent value='<%= ((Properties) vec.get(i)).getProperty("first_name", "") %>' />
            </td>
            <td><e:forHtmlContent value='<%= ((Properties) vec.get(i)).getProperty("last_name", "") %>' />
            </td>
            <!--td>
              <%= ((Properties)vec.get(i)).getProperty("provider_type", "") %>
            </td>
            <td>
              <%= ((Properties)vec.get(i)).getProperty("specialty", "") %>
            </td>
            <td>
              <%= ((Properties)vec.get(i)).getProperty("ohip_no", "") %>
            </td-->
            <td align="center" <%=bDoc ? "bgcolor=\"silver\"" : ""%> title="<fmt:message key='report.reportonbilledvisitprovider.role.doctor'/>">
                <input type="radio"
                       name="type<e:forHtmlAttribute value='<%= ((Properties)vec.get(i)).getProperty("provider_no", "") %>' />"
                       value="<%=ROLE[0]%>" <%=bDoc?"checked":""%>></td>
            <td align="center" <%=bRes ? "bgcolor=\"silver\"" : ""%> title="<fmt:message key='report.reportonbilledvisitprovider.role.resident'/>">
                <input type="radio"
                       name="type<e:forHtmlAttribute value='<%= ((Properties)vec.get(i)).getProperty("provider_no", "") %>' />"
                       value="<%=ROLE[1]%>" <%=bRes?"checked":""%>></td>
            <td align="center" <%=bNp ? "bgcolor=\"silver\"" : ""%> title="<fmt:message key='report.reportonbilledvisitprovider.role.nurse'/>">
                <input type="radio"
                       name="type<e:forHtmlAttribute value='<%= ((Properties)vec.get(i)).getProperty("provider_no", "") %>' />"
                       value="<%=ROLE[2]%>" <%=bNp?"checked":""%>></td>
            <td align="center" <%=bSw ? "bgcolor=\"silver\"" : ""%>
                title="<fmt:message key='report.reportonbilledvisitprovider.role.socialWorker'/>"><input type="radio"
                                             name="type<e:forHtmlAttribute value='<%= ((Properties)vec.get(i)).getProperty("provider_no", "") %>' />"
                                             value="<%=ROLE[3]%>" <%=bSw?"checked":""%>></td>
            <td align="center" <%=bOt ? "bgcolor=\"silver\"" : ""%> title="<fmt:message key='report.reportonbilledvisitprovider.role.other'/>">
                <input type="radio"
                       name="type<e:forHtmlAttribute value='<%= ((Properties)vec.get(i)).getProperty("provider_no", "") %>' />"
                       value="<%=ROLE[4]%>" <%=bOt?"checked":""%>></td>
        </tr>
        <%
            }
            if (vec.size() > 0) {
        %>
        <tr bgcolor="A9A9A9">
            <td colspan="8" align="right"><input type="submit" name="submit"
                                                 value="<fmt:message key='report.reportonbilledvisitprovider.btnAddRoles'/>"></td>
        </tr>
        <%
            }
        %>
    </table>
</form>
<hr>
<table width="100%" border="0" bgcolor="ivory" cellspacing="1"
       cellpadding="1">
    <tr bgcolor="mediumaquamarine">
        <th colspan="5" align="left"><fmt:message key="report.reportonbilledvisitprovider.header.confirmedProviderRoleList"/></th>
    </tr>
    <tr bgcolor="silver">
        <th width="10%" nowrap><fmt:message key="report.reportonbilledvisitprovider.header.id"/></th>
        <th width="30%" nowrap><b><fmt:message key="report.reportonbilledvisitprovider.header.firstName"/></b></th>
        <th width="30%" nowrap><b><fmt:message key="report.reportonbilledvisitprovider.header.lastName"/></b></th>
        <th nowrap><fmt:message key="report.reportonbilledvisitprovider.header.role"/></th>
        <th nowrap><fmt:message key="report.reportonbilledvisitprovider.header.action"/></th>
            <%
          int k = 0;

          for (int i = 0; i < oldRoleList.size(); i += 4) {
            k++;
%>

    <tr bgcolor="<%=k%2==0?"white":color%>">
        <form name="mySecform<%=i%>" action="<%= request.getContextPath() %>/report/ViewReportonbilledvisitprovider"
              method="POST">
            <td><e:forHtmlContent value='<%= oldRoleList.get(i + 3).toString() %>' />
            </td>
            <td><e:forHtmlContent value='<%= oldRoleList.get(i).toString() %>' />
            </td>
            <td><e:forHtmlContent value='<%= oldRoleList.get(i + 1).toString() %>' />
            </td>
            <td align="center"><select
                    name="<%="name" + Encode.forHtmlAttribute(oldRoleList.get(i + 3).toString())%>">
                <%
                    for (int j = 0; j < ROLE.length; j++) {
                        String roleOptionLabel = ROLE[j];
                        if ("doctor".equals(ROLE[j])) {
                            roleOptionLabel = bundle.getString("report.reportonbilledvisitprovider.role.doctor");
                        } else if ("resident".equals(ROLE[j])) {
                            roleOptionLabel = bundle.getString("report.reportonbilledvisitprovider.role.resident");
                        } else if ("nurse".equals(ROLE[j])) {
                            roleOptionLabel = bundle.getString("report.reportonbilledvisitprovider.role.nurse");
                        } else if ("social worker".equals(ROLE[j])) {
                            roleOptionLabel = bundle.getString("report.reportonbilledvisitprovider.role.socialWorker");
                        } else {
                            roleOptionLabel = bundle.getString("report.reportonbilledvisitprovider.role.other");
                        }
                %>
                <option value="<%=ROLE[j]%>"
                        <%= ROLE[j].equals(oldRoleList.get(i + 2)) ? "selected" : "" %>>
                    <%= roleOptionLabel %>
                </option>
                <%
                    }
                %>
            </select></td>
            <td align="center"><input type="hidden" name="providerId"
                                      value="<e:forHtmlAttribute value='<%= oldRoleList.get(i + 3).toString() %>' />"> <input type="submit"
                                                                                    name="buttonUpdate" value="<%=submitUpdateRole%>">
            </td>
        </form>
    </tr>
    <%
        }
    %>
</table>
</body>
</html>
