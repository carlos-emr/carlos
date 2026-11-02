<%@ page import="io.github.carlos_emr.carlos.commn.dao.BilledProviderReportDao" %>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" buffer="64kb" %>
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
    String tdTitleColor = "#CCCC99";
    String tdSubtitleColor = "#CCFF99";
    String tdInterlColor = "white";
    String[] ROLE = new String[]{"doctor", "resident", "nurse", "social worker", "other"};
    Vector[] VEC_PROVIDER = new Vector[]{new Vector(), new Vector(), new Vector(), new Vector(), new Vector()};
    String codeType = request.getParameter("codeType");
    String startDate = request.getParameter("startDate");
    String endDate = request.getParameter("endDate");
    String providerNo = null;
    String providerName = "";
    int[] total = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    String[] providerNoName = {"providerNoDoctor", "providerNoResident", "providerNoNP", "providerNoSW"};
    for (int i = 0; i < providerNoName.length; i++) {
        if (request.getParameter(providerNoName[i]) != null && !"".equals(request.getParameter(providerNoName[i]))) {
            providerNo = request.getParameter(providerNoName[i]);
            break;
        }
    }
%>
<%@ page import="java.util.*" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<%
    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());
    // select provider list
    Properties prop = new Properties();
    BilledProviderReportDao reportDao = io.github.carlos_emr.carlos.utility.SpringUtils.getBean(io.github.carlos_emr.carlos.commn.dao.BilledProviderReportDao.class);
    for (BilledProviderReportDao.ProviderRole provider : reportDao.providerRoles()) {
        prop = new Properties();
        prop.setProperty("providerNo", provider.providerNo());
        prop.setProperty("firstName", provider.firstName());
        prop.setProperty("lastName", provider.lastName());
        for (int i = 0; i < ROLE.length; i++) {
            if (ROLE[i].equals(provider.role())) VEC_PROVIDER[i].add(prop);
        }
        if (provider.providerNo().equals(providerNo)) providerName = provider.firstName() + " " + provider.lastName();
    }
%>

<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="report.reportonbilledphcp.title"/></title>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/css/receptionistapptstyle.css">
        <link rel="stylesheet" href="<%= request.getContextPath() %>/css/report-print.css">
        <!-- calendar stylesheet -->
        <link rel="stylesheet" type="text/css" media="all"
              href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>
        <!-- main calendar program -->
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
        <!-- language for the calendar -->
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:message key="global.javascript.calendar"/>"></script>
        <!-- the following script defines the Calendar.setup helper function, which makes
               adding a calendar a matter of 1 or 2 lines of code. -->
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
        <script language="JavaScript">

            <!--
            function setfocus() {
                this.focus();
                //  document.titlesearch.keyword.select();
            }

            function onSub() {
                if (document.myform.codeType.value == "" || document.myform.startDate.value == "" || document.myform.endDate.value == ""
                    || (document.myform.providerNoDoctor.value == "" && document.myform.providerNoResident.value == ""
                        && document.myform.providerNoNP.value == "" && document.myform.providerNoSW.value == "")) {
                    alert("<fmt:message key='report.reportonbilledphcp.alertSelectItems'/>");
                    return false;
                } else {
                    return true;
                }
            }

            //-->

        </script>
    </head>
    <body bgproperties="fixed" onLoad="setfocus()" topmargin="0"
          leftmargin="0" rightmargin="0">
    <table border="0" cellspacing="0" cellpadding="0" width="100%">
        <tr bgcolor="#486ebd">
            <th align="CENTER" width="90%"><font face="Helvetica"
                                                 color="#FFFFFF"><fmt:message key="report.reportonbilledphcp.header.report"/></font></th>
        </tr>
    </table>
    <form name="myform" class="report-print-hide" action="<%= request.getContextPath() %>/report/ViewReportonbilledphcp" method="POST"
          onsubmit="return onSub();">
        <table width="100%" border="0" bgcolor="ivory" cellspacing="1"
               cellpadding="1">
            <tr bgcolor="lightsteelblue">
                <td><fmt:message key="report.reportonbilledphcp.label.code"/> <br>
                    <select name="codeType">
                        <option value=""><fmt:message key="report.reportonbilledphcp.option.code"/></option>
                        <option value="DxCode"><fmt:message key="report.reportonbilledphcp.option.dxCode"/></option>
                        <option value="ServiceCode"><fmt:message key="report.reportonbilledphcp.option.serviceCode"/></option>
                    </select></td>
                <td nowrap><fmt:message key="report.reportonbilledphcp.label.start"/> <input type="text" name="startDate"
                                        id="startDate" value="<carlos:encode value='<%= startDate!=null?startDate:"" %>' context="htmlAttribute"/>" size="10"
                                        readonly> <img src="<%= request.getContextPath() %>/images/cal.gif" id="startDate_cal">
                    <fmt:message key="report.reportonbilledphcp.label.end"/> <input type="text" name="endDate" id="endDate"
                               value="<carlos:encode value='<%= endDate!=null?endDate:"" %>' context="htmlAttribute"/>" size="10" readonly> <img
                            src="<%= request.getContextPath() %>/images/cal.gif" id="endDate_cal"></td>
                <td><fmt:message key="report.reportonbilledphcp.label.provider"/> <select name="providerNoDoctor">
                    <option value=""><fmt:message key="report.reportonbilledphcp.option.doctor"/></option>
                    <%
                        for (int i = 0; i < VEC_PROVIDER[0].size(); i++) {
                    %>
                    <option
                            value="<carlos:encode value='<%= ((Properties) VEC_PROVIDER[0].get(i)).getProperty("providerNo", "") %>' context="htmlAttribute"/>">
                        <carlos:encode value='<%= ((Properties) VEC_PROVIDER[0].get(i)).getProperty("firstName", "") + " " +
                                ((Properties) VEC_PROVIDER[0].get(i)).getProperty("lastName", "") %>' context="html"/>
                    </option>
                    <%
                        }
                    %>
                </select> <select name="providerNoResident">
                    <option value=""><fmt:message key="report.reportonbilledphcp.option.resident"/></option>
                    <%
                        for (int i = 0; i < VEC_PROVIDER[1].size(); i++) {
                    %>
                    <option
                            value="<carlos:encode value='<%= ((Properties) VEC_PROVIDER[1].get(i)).getProperty("providerNo", "") %>' context="htmlAttribute"/>">
                        <carlos:encode value='<%= ((Properties) VEC_PROVIDER[1].get(i)).getProperty("firstName", "") + " " +
                                ((Properties) VEC_PROVIDER[1].get(i)).getProperty("lastName", "") %>' context="html"/>
                    </option>
                    <%
                        }
                    %>
                </select> <select name="providerNoNP">
                    <option value=""><fmt:message key="report.reportonbilledphcp.option.nurse"/></option>
                    <%
                        for (int i = 0; i < VEC_PROVIDER[2].size(); i++) {
                    %>
                    <option
                            value="<carlos:encode value='<%= ((Properties) VEC_PROVIDER[2].get(i)).getProperty("providerNo", "") %>' context="htmlAttribute"/>">
                        <carlos:encode value='<%= ((Properties) VEC_PROVIDER[2].get(i)).getProperty("firstName", "") + " " +
                                ((Properties) VEC_PROVIDER[2].get(i)).getProperty("lastName", "") %>' context="html"/>
                    </option>
                    <%
                        }
                    %>
                </select> <select name="providerNoSW">
                    <option value=""><fmt:message key="report.reportonbilledphcp.option.socialWorker"/></option>
                    <%
                        for (int i = 0; i < VEC_PROVIDER[3].size(); i++) {
                    %>
                    <option
                            value="<carlos:encode value='<%= ((Properties) VEC_PROVIDER[3].get(i)).getProperty("providerNo", "") %>' context="htmlAttribute"/>">
                        <carlos:encode value='<%= ((Properties) VEC_PROVIDER[3].get(i)).getProperty("firstName", "") + " " +
                                ((Properties) VEC_PROVIDER[3].get(i)).getProperty("lastName", "") %>' context="html"/>
                    </option>
                    <%
                        }
                    %>
                </select></td>
                <td><input type="submit" name="submit" value="<fmt:message key='report.reportonbilledphcp.button.go'/>"></td>
            </tr>
        </table>
    </form>
    <%
            if (request.getParameter("submit") != null && providerNo != null) {
                // set dx/serviceCode mode
                boolean bDx = true;
                if ("DxCode".equals(request.getParameter("codeType"))) {
                    bDx = true;
                } else {
                    bDx = false;
                }

                // get dx code list
                Vector vServiceCode = new Vector();
                Vector vServiceDesc = new Vector();
                Properties props = new Properties();

                // dx category
                Properties propCatCode = new Properties();
                int indexNum = 0;
                Vector vec = new Vector();
                if (bDx) {
                    for (BilledProviderReportDao.Category category : reportDao.categories()) {
                        prop = new Properties();
                        prop.setProperty("dxcode", category.code());
                        prop.setProperty("level1", category.level1());
                        prop.setProperty("level2", category.level2());
                        vec.add(prop);
                        propCatCode.setProperty(category.code(), String.valueOf(indexNum++));
                    }
                }
                for (BilledProviderReportDao.Code code : reportDao.codes(bDx, startDate, endDate)) {
                    vServiceCode.add(code.code());
                    vServiceDesc.add(code.description());
                }

                for (int i = 0; i < vServiceCode.size(); i++) {
                    for (BilledProviderReportDao.MetricCount count : reportDao.counts(bDx, providerNo, startDate, endDate,
                            (String) vServiceCode.get(i), (String) vServiceDesc.get(i))) {
                        props.setProperty(vServiceCode.get(i) + count.key() + vServiceDesc.get(i), String.valueOf(count.value()));
                    }
                }
    %>
    <table border="0" cellspacing="0" cellpadding="0" width="100%">
        <tr bgcolor="<%="#669999"%>">
            <th align="left"><font face="Helvetica" color="white"><carlos:encode value='<%= providerName %>' context="html"/>
                - <%= bundle.getString("report.reportonbilledphcp.header.patientVisitList") %> </font></th>
            <th width="10%" nowrap class="report-print-hide"><input type="button" name="Button"
                                          value="<%= bundle.getString("global.btnPrint") %>" onClick="window.print()"> <input type="button"
                                                                                         name="Button" value="<%= bundle.getString("global.btnExit") %>"
                                                                                         onClick="window.close()"></th>
        </tr>
    </table>
    <table border="0" cellspacing="0" cellpadding="0" width="100%">
        <tr>
            <td><%= bundle.getString("report.reportonbilledphcp.label.period") %> ( <carlos:encode value='<%= startDate %>' context="html"/> ~ <carlos:encode value='<%= endDate %>' context="html"/> )</td>
        </tr>
    </table>
    <table class="report-print-table report-print-wide" width="100%" border="1" bgcolor="#ffffff" cellspacing="0"
           cellpadding="0">
        <thead>
        <tr bgcolor="<%=tdTitleColor%>">
            <TH colspan="2" width="10%"><%=bDx ? bundle.getString("report.reportonbilledphcp.header.dxCode") : bundle.getString("report.reportonbilledphcp.header.serviceCode")%>
            </TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.total") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.sexF") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.sexM") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.age0_1") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.age2_11") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.age12_20") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.age21_34") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.age35_50") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.age51_64") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.age65_70") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.age71Plus") %></TH>
        </tr>
        <tr align="center" bgcolor="<%=tdTitleColor%>">
            <td><%= bundle.getString("report.reportonbilledphcp.header.code") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.description") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
        </tr>
        </thead>
        <tbody>
        <%
            String catName = "";
            String color = "";
            int vecNum = 0;
            for (int i = 0; i < vServiceCode.size(); i++) {
                if (bDx) {
                    color = i % 2 == 0 ? tdInterlColor : "white";

                    // sync vServiceCode and vec
                    String serviceCode = (String) vServiceCode.get(i);
                    if (serviceCode != null && propCatCode.containsKey(serviceCode)) {
                        vecNum = Integer.parseInt(propCatCode.getProperty(serviceCode));

                        // display the category name if necessary
                        String curCatName = ((Properties) vec.get(vecNum)).getProperty("level1", "").toUpperCase() + " - " +
                                ((Properties) vec.get(vecNum)).getProperty("level2", "");

                        if (!curCatName.equals(catName)) {
                            // new level1
                            if (catName.indexOf('-') > 0 && !curCatName.startsWith(catName.substring(0, catName.indexOf('-')))) {
                                tdSubtitleColor = "#00FF99";
                            } else {
                                tdSubtitleColor = "mediumaquamarine";
                            }

                            // new level2
                            catName = curCatName;
        %>
        <tr bgcolor="<%=tdSubtitleColor%>">
            <td colspan="24"><carlos:encode value='<%= curCatName %>' context="html"/>
            </td>
        </tr>
        <%
                    }
                } else {
                    color = "gold";
                }
            } else {
                // Preserve the legacy ServiceCode rendering: only unmapped diagnoses are gold.
                color = "";
            }
        %>
        <tr bgcolor="<%=color %>" align="center">
            <td><carlos:encode value='<%= String.valueOf(vServiceCode.get(i)) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= String.valueOf(vServiceDesc.get(i)) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "pat" + vServiceDesc.get(i)), 0) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "vis" + vServiceDesc.get(i)), 1) %>' context="html"/>
            </td>

            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "patSexF" + vServiceDesc.get(i)), 2) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "visSexF" + vServiceDesc.get(i)), 3) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "patSexM" + vServiceDesc.get(i)), 4) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "visSexM" + vServiceDesc.get(i)), 5) %>' context="html"/>
            </td>

            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "pat0_1" + vServiceDesc.get(i)), 6) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "vis0_1" + vServiceDesc.get(i)), 7) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "pat2_11" + vServiceDesc.get(i)), 8) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "vis2_11" + vServiceDesc.get(i)), 9) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "pat12_20" + vServiceDesc.get(i)), 10) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "vis12_20" + vServiceDesc.get(i)), 11) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "pat21_34" + vServiceDesc.get(i)), 12) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "vis21_34" + vServiceDesc.get(i)), 13) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "pat35_50" + vServiceDesc.get(i)), 14) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "vis35_50" + vServiceDesc.get(i)), 15) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "pat51_64" + vServiceDesc.get(i)), 16) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "vis51_64" + vServiceDesc.get(i)), 17) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "pat65_70" + vServiceDesc.get(i)), 18) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "vis65_70" + vServiceDesc.get(i)), 19) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "pat71_" + vServiceDesc.get(i)), 20) %>' context="html"/>
            </td>
            <td><carlos:encode value='<%= getNumAndCalTotal(props.getProperty(vServiceCode.get(i) + "vis71_" + vServiceDesc.get(i)), 21) %>' context="html"/>
            </td>
        </tr>
        <%
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "pat" + vServiceDesc.get(i)), 0);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "vis" + vServiceDesc.get(i)), 1);

                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "patSexF" + vServiceDesc.get(i)), 2);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "visSexF" + vServiceDesc.get(i)), 3);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "patSexM" + vServiceDesc.get(i)), 4);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "visSexM" + vServiceDesc.get(i)), 5);

                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "pat0_1" + vServiceDesc.get(i)), 6);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "vis0_1" + vServiceDesc.get(i)), 7);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "pat2_11" + vServiceDesc.get(i)), 8);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "vis2_11" + vServiceDesc.get(i)), 9);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "pat12_20" + vServiceDesc.get(i)), 10);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "vis12_20" + vServiceDesc.get(i)), 11);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "pat21_34" + vServiceDesc.get(i)), 12);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "vis21_34" + vServiceDesc.get(i)), 13);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "pat35_50" + vServiceDesc.get(i)), 14);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "vis35_50" + vServiceDesc.get(i)), 15);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "pat51_64" + vServiceDesc.get(i)), 16);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "vis51_64" + vServiceDesc.get(i)), 17);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "pat65_70" + vServiceDesc.get(i)), 18);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "vis65_70" + vServiceDesc.get(i)), 19);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "pat71_" + vServiceDesc.get(i)), 20);
                total = getNumAndCalTotal(total, props.getProperty(vServiceCode.get(i) + "vis71_" + vServiceDesc.get(i)), 21);
            }
        %>
        <tr bgcolor="<%=tdTitleColor%>">
            <TH colspan="2" width="10%"><%=bDx ? bundle.getString("report.reportonbilledphcp.header.dxCode") : bundle.getString("report.reportonbilledphcp.header.serviceCode")%>
            </TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.total") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.sexF") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.sexM") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.age0_1") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.age2_11") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.age12_20") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.age21_34") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.age35_50") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.age51_64") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.age65_70") %></TH>
            <TH colspan="2" width="6%"><%= bundle.getString("report.reportonbilledphcp.header.age71Plus") %></TH>
        </tr>
        <tr align="center" bgcolor="<%=tdTitleColor%>">
            <td><%= bundle.getString("report.reportonbilledphcp.header.code") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.description") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.pt") %></td>
            <td><%= bundle.getString("report.reportonbilledphcp.header.visit") %></td>
        </tr>
        <tr align="center">
            <td colspan="2" align="right"><%= bundle.getString("report.reportonbilledphcp.label.subTotal") %></td>
            <% for (int i = 0; i < total.length; i++) { %>
            <td><%=total[i]%>
            </td>
            <% } %>
        </tr>
        <!--tr align="center">
                <td colspan="2" align="right"><%= bundle.getString("report.reportonbilledphcp.label.total") %></td>
                < %for(int i=0; i<total1.length; i++) { %>
                <td>< %=total1[i]%></td>
                < % } %>
              </tr-->
        </tbody>
    </table>
    <%
        }
    %>
    <script type="text/javascript">
        Calendar.setup({
            inputField: "startDate",
            ifFormat: "%Y/%m/%d",
            showsTime: false,
            button: "startDate_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "endDate",
            ifFormat: "%Y/%m/%d",
            showsTime: false,
            button: "endDate_cal",
            singleClick: true,
            step: 1
        });
    </script>
    </body>
    <%! int[] total1 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    %>
    <%! String getNumAndCalTotal(String str, int i) {
        String ret = str;
        int j = Integer.parseInt(str);
        total1[i] += j;
        return ret;
    }

        int[] getNumAndCalTotal(int[] sTotal, String str, int i) {
            //String ret = str;
            int j = Integer.parseInt(str);
            sTotal[i] += j;
            return sTotal;
        }
    %>
</html>
