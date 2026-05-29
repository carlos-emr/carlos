<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_form" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_form");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="io.github.carlos_emr.carlos.util.*, io.github.carlos_emr.carlos.form.*, io.github.carlos_emr.carlos.form.data.*" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>


<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.data.FrmData" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%
    String formClass = "BCNewBorn";
    String formLink = "formbcnewbornpg3.jsp";

    int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
    int formId = Integer.parseInt(request.getParameter("formId"));
    int provNo = Integer.parseInt((String) session.getAttribute("user"));
    FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
    java.util.Properties props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId);

    FrmData fd = new FrmData();
    String resource = fd.getResource();
    //resource = resource + "ob/riskinfo/";  props.setProperty("c_lastVisited", "pg3");

    //get project_home
    String project_home = request.getContextPath().substring(1);
    boolean bSync = false;
    if (!props.getProperty("c_surname_cur", "").equals("") && !(props.getProperty("c_surname_cur", "").equals(props.getProperty("c_surname", ""))
            && props.getProperty("c_givenName_cur", "").equals(props.getProperty("c_givenName", ""))
            && props.getProperty("c_address_cur", "").equals(props.getProperty("c_address", ""))
            && props.getProperty("c_city_cur", "").equals(props.getProperty("c_city", ""))
            && props.getProperty("c_province_cur", "").equals(props.getProperty("c_province", ""))
            && props.getProperty("c_postal_cur", "").equals(props.getProperty("c_postal", ""))
            //&& props.getProperty("c_phn_cur", "").equals(props.getProperty("c_phn", ""))
            && props.getProperty("c_phone_cur", "").trim().equals(props.getProperty("c_phone", "").trim())
    )) {
        bSync = true;
    }
%>
<%
    boolean bView = false;
    if (request.getParameter("view") != null && request.getParameter("view").equals("1")) bView = true;
%>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key='form.bcnewborn.title'/></title>
        <link rel="stylesheet" type="text/css"
              href="<%=bView?"bcArStyleView.css" : "bcArStyle.css"%>">
        <!-- calendar stylesheet -->
        <link rel="stylesheet" type="text/css" media="all"
              href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>

        <!-- main calendar program -->
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>

        <!-- language for the calendar -->
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:message key='global.javascript.calendar'/>"></script>

        <!-- the following script defines the Calendar.setup helper function, which makes
               adding a calendar a matter of 1 or 2 lines of code. -->
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
    </head>

    <script type="text/javascript" language="Javascript">

        function reset() {
            document.forms[0].target = "";
            document.forms[0].action = "/<%=project_home%>/form/formname";
        }

        function onPrint() {
            document.forms[0].submit.value = "print"; //printAR1
            var ret = checkAllDates();
            if (ret == true) {
                document.forms[0].action = "<%= request.getContextPath() %>/form/createpdf?__title=British+Columbia+Newborn+Record+Part+2&__cfgfile=bcnb2PrintCfgPg2&__template=bcnewborn2";
                document.forms[0].target = "_blank";
            }
            return ret;
        }

        function onSave() {
            document.forms[0].submit.value = "save";
            var ret = checkAllDates();
            if (ret == true) {
                ret = checkAllTimes();
            }
            if (ret == true) {
                reset();
                ret = confirm("<fmt:message key='global.msgWannaSave'/>");
            }
            return ret;
        }

        function onSaveExit() {
            document.forms[0].submit.value = "exit";
            var ret = checkAllDates();
            if (ret == true) {
                ret = checkAllTimes();
            }
            if (ret == true) {
                reset();
                ret = confirm("<fmt:message key='global.msgSaveExit'/>");
            }
            return ret;
        }

        function popupPage(varpage) {
            windowprops = "height=700,width=960" +
                ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=no,screenX=50,screenY=50,top=20,left=20";
            var popup = window.open(varpage, "ar2", windowprops);
            if (popup.opener == null) {
                popup.opener = self;
            }
        }

        function popPage(varpage, pageName) {
            windowprops = "height=700,width=960" +
                ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=no,screenX=50,screenY=50,top=20,left=20";
            var popup = window.open(varpage, pageName, windowprops);
            //if (popup.opener == null) {
            //    popup.opener = self;
            //}
            popup.focus();
        }

        function popupFixedPage(vheight, vwidth, varpage) {
            var page = "" + varpage;
            windowprop = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=10,screenY=0,top=0,left=0";
            var popup = window.open(page, "planner", windowprop);
        }

        function showDef(str, field) {
            if (document.getElementById) {
                field.value = str;
            }
        }

        function syncDemo() {
            document.forms[0].c_surname.value = "<%=props.getProperty("c_surname_cur", "")%>";
            document.forms[0].c_givenName.value = "<%=props.getProperty("c_givenName_cur", "")%>";
            document.forms[0].c_address.value = "<%=props.getProperty("c_address_cur", "")%>";
            document.forms[0].c_city.value = "<%=props.getProperty("c_city_cur", "")%>";
            document.forms[0].c_province.value = "<%=props.getProperty("c_province_cur", "")%>";
            document.forms[0].c_postal.value = "<%=props.getProperty("c_postal_cur", "")%>";
            //document.forms[0].c_phn.value = "<%=props.getProperty("c_phn_cur", "")%>";
            document.forms[0].c_phone.value = "<%=props.getProperty("c_phone_cur", "")%>";
        }

        /**
         * DHTML date validation script. Courtesy of SmartWebby.com (http://www.smartwebby.com/dhtml/)
         */
// Declaring valid date character, minimum year and maximum year
        var dtCh = "/";
        var minYear = 1900;
        var maxYear = 9900;

        function isInteger(s) {
            var i;
            for (i = 0; i < s.length; i++) {
                // Check that current character is number.
                var c = s.charAt(i);
                if (((c < "0") || (c > "9"))) return false;
            }
            // All characters are numbers.
            return true;
        }

        function stripCharsInBag(s, bag) {
            var i;
            var returnString = "";
            // Search through string's characters one by one.
            // If character is not in bag, append to returnString.
            for (i = 0; i < s.length; i++) {
                var c = s.charAt(i);
                if (bag.indexOf(c) == -1) returnString += c;
            }
            return returnString;
        }

        function daysInFebruary(year) {
            // February has 29 days in any year evenly divisible by four,
            // EXCEPT for centurial years which are not also divisible by 400.
            return (((year % 4 == 0) && ((!(year % 100 == 0)) || (year % 400 == 0))) ? 29 : 28);
        }

        function DaysArray(n) {
            for (var i = 1; i <= n; i++) {
                this[i] = 31
                if (i == 4 || i == 6 || i == 9 || i == 11) {
                    this[i] = 30
                }
                if (i == 2) {
                    this[i] = 29
                }
            }
            return this
        }

        function isDate(dtStr) {
            var daysInMonth = DaysArray(12)
            var pos1 = dtStr.indexOf(dtCh)
            var pos2 = dtStr.indexOf(dtCh, pos1 + 1)
            var strMonth = dtStr.substring(0, pos1)
            var strDay = dtStr.substring(pos1 + 1, pos2)
            var strYear = dtStr.substring(pos2 + 1)
            strYr = strYear
            if (strDay.charAt(0) == "0" && strDay.length > 1) strDay = strDay.substring(1)
            if (strMonth.charAt(0) == "0" && strMonth.length > 1) strMonth = strMonth.substring(1)
            for (var i = 1; i <= 3; i++) {
                if (strYr.charAt(0) == "0" && strYr.length > 1) strYr = strYr.substring(1)
            }
            month = parseInt(strMonth)
            day = parseInt(strDay)
            year = parseInt(strYr)
            if (pos1 == -1 || pos2 == -1) {
                return "format"
            }
            if (month < 1 || month > 12) {
                return "month"
            }
            if (day < 1 || day > 31 || (month == 2 && day > daysInFebruary(year)) || day > daysInMonth[month]) {
                return "day"
            }
            if (strYear.length != 4 || year == 0 || year < minYear || year > maxYear) {
                return "year"
            }
            if (dtStr.indexOf(dtCh, pos2 + 1) != -1 || isInteger(stripCharsInBag(dtStr, dtCh)) == false) {
                return "date"
            }
            return true
        }


        function checkTypeIn(obj) {
            if (!checkTypeNum(obj.value)) {
                alert("<fmt:message key='global.msgTypeANumber'/>");
            }
        }

        function valDate(dateBox) {
            try {
                var dateString = dateBox.value;
                if (dateString == "") {
                    //alert('dateString'+dateString);
                    return true;
                }
                var dt = dateString.split('/');
                var y = dt[2];
                var m = dt[1];
                var d = dt[0];
                //var y = dt[0];  var m = dt[1];  var d = dt[2];
                var orderString = m + '/' + d + '/' + y;
                var pass = isDate(orderString);

                if (pass != true) {
                    alert('Invalid ' + pass + ' in field ' + dateBox.name);
                    dateBox.focus();
                    return false;
                }
            } catch (ex) {
                alert('<fmt:message key='global.msgInvalidDatePrefix'/>' + dateBox.name);
                dateBox.focus();
                return false;
            }
            return true;
        }

        function valTime(dateBox) {
            try {
                var dateString = dateBox.value;
                if (dateString == "") {
                    //alert('dateString'+dateString);
                    return true;
                }
                var dt = dateString.split(':');
                var m = dt[1];
                var h = dt[0];
                var pass = false;
                if (h >= 0 && h <= 23 && m >= 0 && m <= 59) {
                    pass = true;
                }

                if (pass != true) {
                    alert('Invalid data in field ' + dateBox.name);
                    dateBox.focus();
                    return false;
                }
            } catch (ex) {
                alert('<fmt:message key='global.msgInvalidDatePrefix'/>' + dateBox.name);
                dateBox.focus();
                return false;
            }
            return true;
        }

        function checkAllDates() {
            var b = true;
            if (valDate(document.forms[0].ar2_9Date1) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_9Date2) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_9Date3) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_9Date4) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_9Date5) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_9Date6) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg3_formDate) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_10Date1) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_10Date2) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_10Date3) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_10DateRes1) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_10DateRes2) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_10DateRes3) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg3_11Date1) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg3_11Date2) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg3_11Date3) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg3_11Date4) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg3_11Date5) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg3_11Date6) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg3_11Date7) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg3_11Date8) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg3_11Date9) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg3_11Date10) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg3_11Date11) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg3_11Date12) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg3_11Date13) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_12Date) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_13ExamDate) == false) {
                b = false;
            }

            return b;
        }

        function checkAllTimes() {
            var b = true;
            if (valTime(document.forms[0].ar2_9Test1Time) == false) {
                b = false;
            }

            return b;
        }
    </script>


    <script type="text/javascript" language="Javascript">

        function onCheck(a, groupName) {
            if (a.checked) {
                var s = groupName;
                unCheck(s);
                a.checked = true;
            }
        }

        function unCheck(s) {
            for (var i = 0; i < document.forms[0].elements.length; i++) {
                if (document.forms[0].elements[i].name.indexOf(s) != -1 && document.forms[0].elements[i].name.indexOf(s) < 1) {
                    document.forms[0].elements[i].checked = false;
                }
            }
        }

        function isChecked(s) {
            for (var i = 0; i < document.forms[0].elements.length; i++) {
                if (document.forms[0].elements[i].name == s) {
                    if (document.forms[0].elements[i].checked) {
                        return true;
                    } else {
                        return false;
                    }
                }
            }
        }

        function onCheckMaster(a, groupName) {
            if (!a.checked) {
                var s = groupName;
                unCheck(s);
                //a.checked = false;
            }
        }

        function onCheckSlave(a, masterName) {
            if (a.checked) {
                if (!isChecked(masterName)) {
                    a.checked = false;
                } else {
                    a.checked = true;
                }
            }
        }
    </script>


    <body bgproperties="fixed" topmargin="0" leftmargin="1" rightmargin="1"
          onLoad="setfocus()">

    <form action="${pageContext.request.contextPath}/form/formname" method="post">
    <input type="hidden" name="commonField" value="ar2_"/>
        <input type="hidden" name="c_lastVisited" value="pg3"/>
        <input type="hidden" name="demographic_no"
               value="<%= props.getProperty("demographic_no", "0") %>"/>
        <input type="hidden" name="formCreated"
               value="<%= props.getProperty("formCreated", "") %>"/>
        <input type="hidden" name="form_class" value="<%=formClass%>"/>
        <input type="hidden" name="form_link" value="<%=formLink%>"/>
        <input type="hidden" name="formId" value="<%=formId%>"/>
        <input type="hidden" name="provider_no" value=<%="" + provNo%>/>
        <!--input type="hidden" name="provNo" value="<carlos:encode value='<%= StringUtils.noNull(request.getParameter("provNo")) %>' context="htmlAttribute"/>" /-->
        <input type="hidden" name="submit" value="exit"/>

        <table class="Head" class="hidePrint">
            <tr>
                <td align="left">
                    <%
                        if (!bView) {
                    %> <input type="submit" value="<fmt:message key='global.save'/>"
                              onclick="javascript:return onSave();"/> <input type="submit"
                                                                             value="<fmt:message key='global.saveExit'/>"
                                                                             onclick="javascript:return onSaveExit();"/> <%
                    }
                %> <input type="submit" value="<fmt:message key='global.btnExit'/>"
                          onclick="javascript:return onExit();"/> <input type="submit"
                                                                         value="<fmt:message key='global.btnPrint'/>"
                                                                         onclick="javascript:return onPrint();"/></td>
                <%
                    if (!bView) {
                %>

                <td align="right"><!--  b>View:</b>
            <a href="javascript: popupPage('form/formbcnewbornpg1?demographic_no=<%=demoNo%>&formId=<%=formId%>&view=1');">Part1 </a>
            <a href="javascript: popupPage('form/formbcnewbornpg2?demographic_no=<%=demoNo%>&formId=<%=formId%>&view=1');">Part2 <font size=-2>(pg.1)</font></a> |
            &nbsp;--></td>
                <td align="right"><b>Edit:</b> <a
                        href="form/formbcnewbornpg1?demographic_no=<%=demoNo%>&formId=<%=formId%>">Part1</a>
                    | <a
                            href="form/formbcnewbornpg2?demographic_no=<%=demoNo%>&formId=<%=formId%>">Part2
                        <font size=-2>(pg.1)</font></a> | Part2 <font size=-2>(pg.2)</font> |
                </td>
                <%
                    }
                %>
            </tr>
        </table>


        <table width="100%" border="1" cellspacing="0" cellpadding="0">
            <tr>
                <td width="60%">

                    <table width="100%" border="0" cellspacing="0" cellpadding="0">
                        <tr>
                            <th><%=bView ? "<font color='yellow'><fmt:message key='form.bcnewborn.viewPage'/> </font>" : ""%>
                                <fmt:message key='form.bcnewborn.recordTitlePart2'/>
                            </th>
                        </tr>
                    </table>

                    <table width="100%" border="1" cellspacing="0" cellpadding="0">
                        <tr>
                            <td width="15%" nowrap><b>9.</b> <fmt:message key='form.bcnewborn.dateDone'/><br>
                                &nbsp;&nbsp;&nbsp;dd/mm/yyyy
                            </td>
                            <td rowspan="2" valign="bottom"><input type="checkbox"
                                                                   name="ar2_9Test1" <%= props.getProperty("ar2_9Test1", "") %> />
                                <fmt:message key='form.bcnewborn.dateDoneDetail'/> &nbsp; <input type="checkbox"
                                                                     name="ar2_9Test1a" <%= props.getProperty("ar2_9Test1a", "") %> />
                                <fmt:message key='form.bcnewborn.deferred'/> &nbsp;&nbsp; <fmt:message key='form.bcnewborn.time'/>: <input type="text"
                                                                   name="ar2_9Test1Time" size="5" maxlength="5"
                                                                   value="<%= props.getProperty("ar2_9Test1Time", "") %>"/>
                            </td>
                        </tr>
                        <tr>
                            <td nowrap><input type="text" name="ar2_9Date1"
                                              id="ar2_9Date1" size="8" maxlength="10"
                                              value="<%= props.getProperty("ar2_9Date1", "") %>"/> <img
                                    src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_9Date1_cal"></td>
                        </tr>
                        <tr>
                            <td nowrap><input type="text" name="ar2_9Date2"
                                              id="ar2_9Date2" size="8" maxlength="10"
                                              value="<%= props.getProperty("ar2_9Date2", "") %>"/> <img
                                    src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_9Date2_cal"></td>
                            <td><input type="checkbox" name="ar2_9Test2"
                                    <%= props.getProperty("ar2_9Test2", "") %> /> POSITIVE MATERNAL
                                HBsAg STATUS
                            </td>
                        </tr>
                        <tr>
                            <td nowrap><input type="text" name="ar2_9Date3"
                                              id="ar2_9Date3" size="8" maxlength="10"
                                              value="<%= props.getProperty("ar2_9Date3", "") %>"/> <img
                                    src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_9Date3_cal"></td>
                            <td>&nbsp;&nbsp;&nbsp;<input type="checkbox" name="ar2_9Test3"
                                    <%= props.getProperty("ar2_9Test3", "") %> /> <fmt:message key='form.bcnewborn.hbigGiven'/>
                            </td>
                        </tr>
                        <tr>
                            <td nowrap><input type="text" name="ar2_9Date4"
                                              id="ar2_9Date4" size="8" maxlength="10"
                                              value="<%= props.getProperty("ar2_9Date4", "") %>"/> <img
                                    src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_9Date4_cal"></td>
                            <td>&nbsp;&nbsp;&nbsp;<input type="checkbox" name="ar2_9Test4"
                                    <%= props.getProperty("ar2_9Test4", "") %> /> HEPATITIS B
                                VACCINE GIVEN
                            </td>
                        </tr>
                        <tr>
                            <td nowrap><input type="text" name="ar2_9Date5"
                                              id="ar2_9Date5" size="8" maxlength="10"
                                              value="<%= props.getProperty("ar2_9Date5", "") %>"/> <img
                                    src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_9Date5_cal"></td>
                            <td><input type="checkbox" name="ar2_9Test5"
                                    <%= props.getProperty("ar2_9Test5", "") %> /> POSITIVE MATERNAL
                                HIV STATUS
                            </td>
                        </tr>
                        <tr>
                            <td nowrap><input type="text" name="ar2_9Date6"
                                              id="ar2_9Date6" size="8" maxlength="10"
                                              value="<%= props.getProperty("ar2_9Date6", "") %>"/> <img
                                    src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_9Date6_cal"></td>
                            <td><input type="checkbox" name="ar2_9Test6"
                                    <%= props.getProperty("ar2_9Test6", "") %> /> <fmt:message key='form.bcnewborn.other'/>
                        </tr>
                    </table>


                </td>
                <td>

                    <table width="100%" border="0" cellspacing="0" cellpadding="0">
                        <tr>
                            <td width="55%"><fmt:message key='form.bcnewborn.hospitalName'/><br>
                                <input type="text" name="c_hospitalName" style="width: 100%"
                                       size="30" maxlength="80"
                                       value="<%= props.getProperty("c_hospitalName", "") %>"/></td>
                            <td><fmt:message key='form.bcnewborn.date'/> <img src="<%= request.getContextPath() %>/images/cal.gif" id="pg3_formDate_cal">
                                <%=bSync ? ("<b><a href=# onClick='syncDemo(); return false;'><font color='red'>Synchronize</font></a></b>") : "" %>
                                <br>
                                <input type="text" name="pg3_formDate" id="pg3_formDate"
                                       style="width: 100%" size="10" maxlength="10"
                                       value="<%= props.getProperty("pg1_formDate", "") %>" @oscar.formDB
                                       dbType="date"/></td>
                        </tr>
                        <tr>
                            <td width="55%"><fmt:message key='form.bcnewborn.surname'/><br>
                                <input type="text" name="c_surname" style="width: 100%" size="30"
                                       maxlength="30" value="<%= props.getProperty("c_surname", "") %>"/>
                            </td>
                            <td><fmt:message key='form.bcnewborn.givenName'/><br>
                                <input type="text" name="c_givenName" style="width: 100%" size="30"
                                       maxlength="30" value="<%= props.getProperty("c_givenName", "") %>"/>
                            </td>
                        </tr>
                        <tr>
                            <td><fmt:message key='form.bcnewborn.address'/><br>
                                <input type="text" name="c_address" style="width: 100%" size="50"
                                       maxlength="60" value="<%= props.getProperty("c_address", "") %>"/>
                                <input type="text" name="c_city" style="width: 100%" size="50"
                                       maxlength="60" value="<%= props.getProperty("c_city", "") %>"/>
                                <input type="text" name="c_province" size="18" maxlength="50"
                                       value="<%= props.getProperty("c_province", "") %>"/> <input
                                        type="text" name="c_postal" size="7" maxlength="8"
                                        value="<%= props.getProperty("c_postal", "") %>"/></td>
                            <td valign="top"><fmt:message key='form.bcnewborn.phoneNumber'/><br>
                                <input type="text" name="c_phone" style="width: 100%" size="60"
                                       maxlength="60" value="<%= props.getProperty("c_phone", "") %>"/>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="2"><span class="small9"><fmt:message key='form.bcnewborn.physicianMidwifeName'/></span><br>
                                <input type="text" name="c_phyMid" style="width: 100%" size="30"
                                       maxlength="60" value="<%= props.getProperty("c_phyMid", "") %>"/>
                            </td>
                        </tr>
                    </table>

                </td>
            </tr>
        </table>


        <table width="100%" border="1" cellspacing="0" cellpadding="0">
            <tr>
                <td width="10%"><B>10.</B> Date</td>
                <td width="80%"><B><fmt:message key='form.bcnewborn.problemList'/></B></td>
                <td><span class="small8"><fmt:message key='form.bcnewborn.date'/> RESOLVED</span></td>
            </tr>
            <tr>
                <td nowrap><input type="text" name="ar2_10Date1"
                                  id="ar2_10Date1" size="8" maxlength="10"
                                  value="<%= props.getProperty("ar2_10Date1", "") %>"/> <img
                        src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_10Date1_cal"></td>
                <td><input type="text" name="ar2_10List1" style="width: 100%"
                           size="90" maxlength="200"
                           value="<%= props.getProperty("ar2_10List1", "") %>"/></td>
                <td nowrap><input type="text" name="ar2_10DateRes1"
                                  id="ar2_10DateRes1" size="8" maxlength="10"
                                  value="<%= props.getProperty("ar2_10DateRes1", "") %>"/> <img
                        src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_10DateRes1_cal"></td>
            </tr>
            <tr>
                <td nowrap><input type="text" name="ar2_10Date2"
                                  id="ar2_10Date2" size="8" maxlength="10"
                                  value="<%= props.getProperty("ar2_10Date2", "") %>"/> <img
                        src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_10Date2_cal"></td>
                <td nowrap><input type="text" name="ar2_10List2"
                                  style="width: 100%" size="90" maxlength="200"
                                  value="<%= props.getProperty("ar2_10List2", "") %>"/></td>
                <td><input type="text" name="ar2_10DateRes2" id="ar2_10DateRes2"
                           size="8" maxlength="10"
                           value="<%= props.getProperty("ar2_10DateRes2", "") %>"/> <img
                        src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_10DateRes2_cal"></td>
            </tr>
            <tr>
                <td nowrap><input type="text" name="ar2_10Date3"
                                  id="ar2_10Date3" size="8" maxlength="10"
                                  value="<%= props.getProperty("ar2_10Date3", "") %>"/> <img
                        src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_10Date3_cal"></td>
                <td><input type="text" name="ar2_10List3" style="width: 100%"
                           size="90" maxlength="200"
                           value="<%= props.getProperty("ar2_10List3", "") %>"/></td>
                <td nowrap><input type="text" name="ar2_10DateRes3"
                                  id="ar2_10DateRes3" size="8" maxlength="10"
                                  value="<%= props.getProperty("ar2_10DateRes3", "") %>"/> <img
                        src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_10DateRes3_cal"></td>
            </tr>
        </table>

        <table width="100%" border="1" cellspacing="0" cellpadding="0">
            <tr>
                <td width="10%"><B>11.</B> Date</td>
                <td><B><fmt:message key='form.bcnewborn.progressNotes'/></B></td>
            </tr>
            <tr>
                <td nowrap><input type="text" name="pg3_11Date1"
                                  id="pg3_11Date1" size="8" maxlength="10"
                                  value="<%= props.getProperty("pg3_11Date1", "") %>" @oscar.formDB
                                  dbType="date"/> <img src="<%= request.getContextPath() %>/images/cal.gif" id="pg3_11Date1_cal">
                </td>
                <td><input type="text" name="pg3_11List1" style="width: 100%"
                           size="90" maxlength="200"
                           value="<%= props.getProperty("pg3_11List1", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td nowrap><input type="text" name="pg3_11Date2"
                                  id="pg3_11Date2" size="8" maxlength="10"
                                  value="<%= props.getProperty("pg3_11Date2", "") %>" @oscar.formDB
                                  dbType="date"/> <img src="<%= request.getContextPath() %>/images/cal.gif" id="pg3_11Date2_cal">
                </td>
                <td><input type="text" name="pg3_11List2" style="width: 100%"
                           size="90" maxlength="200"
                           value="<%= props.getProperty("pg3_11List2", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td nowrap><input type="text" name="pg3_11Date3"
                                  id="pg3_11Date3" size="8" maxlength="10"
                                  value="<%= props.getProperty("pg3_11Date3", "") %>" @oscar.formDB
                                  dbType="date"/> <img src="<%= request.getContextPath() %>/images/cal.gif" id="pg3_11Date3_cal">
                </td>
                <td><input type="text" name="pg3_11List3" style="width: 100%"
                           size="90" maxlength="200"
                           value="<%= props.getProperty("pg3_11List3", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td nowrap><input type="text" name="pg3_11Date4"
                                  id="pg3_11Date4" size="8" maxlength="10"
                                  value="<%= props.getProperty("pg3_11Date4", "") %>" @oscar.formDB
                                  dbType="date"/> <img src="<%= request.getContextPath() %>/images/cal.gif" id="pg3_11Date4_cal">
                </td>
                <td><input type="text" name="pg3_11List4" style="width: 100%"
                           size="90" maxlength="200"
                           value="<%= props.getProperty("pg3_11List4", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td nowrap><input type="text" name="pg3_11Date5"
                                  id="pg3_11Date5" size="8" maxlength="10"
                                  value="<%= props.getProperty("pg3_11Date5", "") %>" @oscar.formDB
                                  dbType="date"/> <img src="<%= request.getContextPath() %>/images/cal.gif" id="pg3_11Date5_cal">
                </td>
                <td><input type="text" name="pg3_11List5" style="width: 100%"
                           size="90" maxlength="200"
                           value="<%= props.getProperty("pg3_11List5", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td nowrap><input type="text" name="pg3_11Date6"
                                  id="pg3_11Date6" size="8" maxlength="10"
                                  value="<%= props.getProperty("pg3_11Date6", "") %>" @oscar.formDB
                                  dbType="date"/> <img src="<%= request.getContextPath() %>/images/cal.gif" id="pg3_11Date6_cal">
                </td>
                <td><input type="text" name="pg3_11List6" style="width: 100%"
                           size="90" maxlength="200"
                           value="<%= props.getProperty("pg3_11List6", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td nowrap><input type="text" name="pg3_11Date7"
                                  id="pg3_11Date7" size="8" maxlength="10"
                                  value="<%= props.getProperty("pg3_11Date7", "") %>" @oscar.formDB
                                  dbType="date"/> <img src="<%= request.getContextPath() %>/images/cal.gif" id="pg3_11Date7_cal">
                </td>
                <td><input type="text" name="pg3_11List7" style="width: 100%"
                           size="90" maxlength="200"
                           value="<%= props.getProperty("pg3_11List7", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td nowrap><input type="text" name="pg3_11Date8"
                                  id="pg3_11Date8" size="8" maxlength="10"
                                  value="<%= props.getProperty("pg3_11Date8", "") %>" @oscar.formDB
                                  dbType="date"/> <img src="<%= request.getContextPath() %>/images/cal.gif" id="pg3_11Date8_cal">
                </td>
                <td><input type="text" name="pg3_11List8" style="width: 100%"
                           size="90" maxlength="200"
                           value="<%= props.getProperty("pg3_11List8", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td nowrap><input type="text" name="pg3_11Date9"
                                  id="pg3_11Date9" size="8" maxlength="10"
                                  value="<%= props.getProperty("pg3_11Date9", "") %>" @oscar.formDB
                                  dbType="date"/> <img src="<%= request.getContextPath() %>/images/cal.gif" id="pg3_11Date9_cal">
                </td>
                <td><input type="text" name="pg3_11List9" style="width: 100%"
                           size="90" maxlength="200"
                           value="<%= props.getProperty("pg3_11List9", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td nowrap><input type="text" name="pg3_11Date10"
                                  id="pg3_11Date10" size="8" maxlength="10"
                                  value="<%= props.getProperty("pg3_11Date10", "") %>" @oscar.formDB
                                  dbType="date"/> <img src="<%= request.getContextPath() %>/images/cal.gif" id="pg3_11Date10_cal">
                </td>
                <td><input type="text" name="pg3_11List10" style="width: 100%"
                           size="90" maxlength="200"
                           value="<%= props.getProperty("pg3_11List10", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td nowrap><input type="text" name="pg3_11Date11"
                                  id="pg3_11Date11" size="8" maxlength="10"
                                  value="<%= props.getProperty("pg3_11Date11", "") %>" @oscar.formDB
                                  dbType="date"/> <img src="<%= request.getContextPath() %>/images/cal.gif" id="pg3_11Date11_cal">
                </td>
                <td><input type="text" name="pg3_11List11" style="width: 100%"
                           size="90" maxlength="200"
                           value="<%= props.getProperty("pg3_11List11", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td nowrap><input type="text" name="pg3_11Date12"
                                  id="pg3_11Date12" size="8" maxlength="10"
                                  value="<%= props.getProperty("pg3_11Date12", "") %>" @oscar.formDB
                                  dbType="date"/> <img src="<%= request.getContextPath() %>/images/cal.gif" id="pg3_11Date12_cal">
                </td>
                <td><input type="text" name="pg3_11List12" style="width: 100%"
                           size="90" maxlength="200"
                           value="<%= props.getProperty("pg3_11List12", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td nowrap><input type="text" name="pg3_11Date13"
                                  id="pg3_11Date13" size="8" maxlength="10"
                                  value="<%= props.getProperty("pg3_11Date13", "") %>" @oscar.formDB
                                  dbType="date"/> <img src="<%= request.getContextPath() %>/images/cal.gif" id="pg3_11Date13_cal">
                </td>
                <td><input type="text" name="pg3_11List13" style="width: 100%"
                           size="90" maxlength="200"
                           value="<%= props.getProperty("pg3_11List13", "") %>" @oscar.formDB/>
                </td>
            </tr>
        </table>

        <table width="100%" border="0" cellspacing="0" cellpadding="0">
            <tr>
                <td width="10%"><B>12.</B> Date</td>
                <td width="12%" nowrap><B><fmt:message key='form.bcnewborn.circumcision'/></B> <input
                        type="checkbox" name="ar2_12Done"
                        <%= props.getProperty("ar2_12Done", "") %> /></td>
                <td colspan="3%"><fmt:message key='form.bcnewborn.done'/></td>
            </tr>
            <tr>
                <td colspan="2%"><input type="text" name="ar2_12Date"
                                        id="ar2_12Date" size="8" maxlength="10"
                                        value="<%= props.getProperty("ar2_12Date", "") %>"/> <img
                        src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_12Date_cal"></td>
                <td width="22%" nowrap><span class="small8"><fmt:message key='form.bcnewborn.method'/></span> <input
                        type="text" name="ar2_12Method" size="20" maxlength="30"
                        value="<%= props.getProperty("ar2_12Method", "") %>"/></td>
                <td width="28%" nowrap><span class="small8">ANALGESIA
			USED</span> <input type="text" name="ar2_12Analg" size="20" maxlength="30"
                               value="<%= props.getProperty("ar2_12Analg", "") %>"/></td>
                <td nowrap><span class="small8"><fmt:message key='form.bcnewborn.signature'/> <input
                        type="text" name="ar2_12Signature" size="20" maxlength="60"
                        value="<%= props.getProperty("ar2_12Signature", "") %>"/> MD</span></td>
            </tr>
        </table>


        <table class="small9" width="100%" border="1" cellspacing="0"
               cellpadding="0">
            <tr>
                <td width="60%" rowspan="3" valign="top">

                    <table class="small8" width="100%" border="1" cellspacing="0"
                           cellpadding="0">
                        <tr>
                            <td colspan="4">

                                <table class="small9" width="100%" border="0" cellspacing="0"
                                       cellpadding="0">
                                    <tr>
                                        <td width="40%"><B>13. <fmt:message key='form.bcnewborn.dischargeExamination'/></B><br>
                                            Date <input type="text" name="ar2_13ExamDate" id="ar2_13ExamDate"
                                                        size="10" maxlength="10"
                                                        value="<%= props.getProperty("ar2_13ExamDate", "") %>"/> <img
                                                    src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_13ExamDate_cal"></td>
                                        <td width="18%" nowrap><fmt:message key='form.bcnewborn.weight'/><br>
                                            <input type="text" name="ar2_13Weight" size="6" maxlength="6"
                                                   value="<%= props.getProperty("ar2_13Weight", "") %>"/>g
                                        </td>
                                        <td><fmt:message key='form.bcnewborn.headCircumference'/><br>
                                            <input type="text" name="ar2_13HeadLen" size="6" maxlength="6"
                                                   value="<%= props.getProperty("ar2_13HeadLen", "") %>"/>cm
                                        </td>
                                    </tr>
                                </table>


                            </td>
                        </tr>
                        <tr>
                            <td width="20%">1. <fmt:message key='form.bcnewborn.generalAppearance'/></td>
                            <td width="8%"><fmt:message key='form.bcnewborn.normal'/><br>
                                <input type="checkbox" name="ar2_13Normal1"
                                        <%= props.getProperty("ar2_13Normal1", "") %> /></td>
                            <td width="10%"><fmt:message key='form.bcnewborn.abnormal'/><br>
                                <input type="checkbox" name="ar2_13Abnormal1"
                                        <%= props.getProperty("ar2_13Abnormal1", "") %> /></td>
                            <td><fmt:message key='form.bcnewborn.comments'/><br>
                                <input type="text" name="ar2_13Comment1" style="width: 100%"
                                       size="40" maxlength="60"
                                       value="<%= props.getProperty("ar2_13Comment1", "") %>"/></td>
                        </tr>
                        <tr>
                            <td>2. <fmt:message key='form.bcnewborn.skin'/></td>
                            <td><input type="checkbox" name="ar2_13Normal2"
                                    <%= props.getProperty("ar2_13Normal2", "") %> /></td>
                            <td><input type="checkbox" name="ar2_13Abnormal2"
                                    <%= props.getProperty("ar2_13Abnormal2", "") %> /></td>
                            <td><input type="text" name="ar2_13Comment2"
                                       style="width: 100%" size="40" maxlength="60"
                                       value="<%= props.getProperty("ar2_13Comment2", "") %>"/></td>
                        </tr>
                        <tr>
                            <td>3. <fmt:message key='form.bcnewborn.head'/></td>
                            <td><input type="checkbox" name="ar2_13Normal3"
                                    <%= props.getProperty("ar2_13Normal3", "") %> /></td>
                            <td><input type="checkbox" name="ar2_13Abnormal3"
                                    <%= props.getProperty("ar2_13Abnormal3", "") %> /></td>
                            <td><input type="text" name="ar2_13Comment3"
                                       style="width: 100%" size="40" maxlength="60"
                                       value="<%= props.getProperty("ar2_13Comment3", "") %>"/></td>
                        </tr>
                        <tr>
                            <td>4. <fmt:message key='form.bcnewborn.eent'/></td>
                            <td><input type="checkbox" name="ar2_13Normal4"
                                    <%= props.getProperty("ar2_13Normal4", "") %> /></td>
                            <td><input type="checkbox" name="ar2_13Abnormal4"
                                    <%= props.getProperty("ar2_13Abnormal4", "") %> /></td>
                            <td><input type="text" name="ar2_13Comment4"
                                       style="width: 100%" size="40" maxlength="60"
                                       value="<%= props.getProperty("ar2_13Comment4", "") %>"/></td>
                        </tr>
                        <tr>
                            <td>5. <fmt:message key='form.bcnewborn.respiratory'/></td>
                            <td><input type="checkbox" name="ar2_13Normal5"
                                    <%= props.getProperty("ar2_13Normal5", "") %> /></td>
                            <td><input type="checkbox" name="ar2_13Abnormal5"
                                    <%= props.getProperty("ar2_13Abnormal5", "") %> /></td>
                            <td><input type="text" name="ar2_13Comment5"
                                       style="width: 100%" size="40" maxlength="60"
                                       value="<%= props.getProperty("ar2_13Comment5", "") %>"/></td>
                        </tr>
                        <tr>
                            <td>6. CVS</td>
                            <td><input type="checkbox" name="ar2_13Normal6"
                                    <%= props.getProperty("ar2_13Normal6", "") %> /></td>
                            <td><input type="checkbox" name="ar2_13Abnormal6"
                                    <%= props.getProperty("ar2_13Abnormal6", "") %> /></td>
                            <td><input type="text" name="ar2_13Comment6"
                                       style="width: 100%" size="40" maxlength="60"
                                       value="<%= props.getProperty("ar2_13Comment6", "") %>"/></td>
                        </tr>
                        <tr>
                            <td>7. <fmt:message key='form.bcnewborn.abdomen'/></td>
                            <td><input type="checkbox" name="ar2_13Normal7"
                                    <%= props.getProperty("ar2_13Normal7", "") %> /></td>
                            <td><input type="checkbox" name="ar2_13Abnormal7"
                                    <%= props.getProperty("ar2_13Abnormal7", "") %> /></td>
                            <td><input type="text" name="ar2_13Comment7"
                                       style="width: 100%" size="40" maxlength="60"
                                       value="<%= props.getProperty("ar2_13Comment7", "") %>"/></td>
                        </tr>
                        <tr>
                            <td>8. <fmt:message key='form.bcnewborn.umbilicalCord'/></td>
                            <td><input type="checkbox" name="ar2_13Normal8"
                                    <%= props.getProperty("ar2_13Normal8", "") %> /></td>
                            <td><input type="checkbox" name="ar2_13Abnormal8"
                                    <%= props.getProperty("ar2_13Abnormal8", "") %> /></td>
                            <td><input type="text" name="ar2_13Comment8"
                                       style="width: 100%" size="40" maxlength="60"
                                       value="<%= props.getProperty("ar2_13Comment8", "") %>"/></td>
                        </tr>
                        <tr>
                            <td>9. <fmt:message key='form.bcnewborn.genitorectal'/></td>
                            <td><input type="checkbox" name="ar2_13Normal9"
                                    <%= props.getProperty("ar2_13Normal9", "") %> /></td>
                            <td><input type="checkbox" name="ar2_13Abnormal9"
                                    <%= props.getProperty("ar2_13Abnormal9", "") %> /></td>
                            <td><input type="text" name="ar2_13Comment9"
                                       style="width: 100%" size="40" maxlength="60"
                                       value="<%= props.getProperty("ar2_13Comment9", "") %>"/></td>
                        </tr>
                        <tr>
                            <td>10. <fmt:message key='form.bcnewborn.musculoskeletal'/></td>
                            <td><input type="checkbox" name="ar2_13Normal10"
                                    <%= props.getProperty("ar2_13Normal10", "") %> /></td>
                            <td><input type="checkbox" name="ar2_13Abnormal10"
                                    <%= props.getProperty("ar2_13Abnormal10", "") %> /></td>
                            <td><input type="text" name="ar2_13Comment10"
                                       style="width: 100%" size="40" maxlength="60"
                                       value="<%= props.getProperty("ar2_13Comment10", "") %>"/></td>
                        </tr>
                        <tr>
                            <td>11. <fmt:message key='form.bcnewborn.neurological'/></td>
                            <td><input type="checkbox" name="ar2_13Normal11"
                                    <%= props.getProperty("ar2_13Normal11", "") %> /></td>
                            <td><input type="checkbox" name="ar2_13Abnormal11"
                                    <%= props.getProperty("ar2_13Abnormal11", "") %> /></td>
                            <td><input type="text" name="ar2_13Comment11"
                                       style="width: 100%" size="40" maxlength="60"
                                       value="<%= props.getProperty("ar2_13Comment11", "") %>"/></td>
                        </tr>
                        <tr>
                            <td>12. <fmt:message key='form.bcnewborn.other'/></td>
                            <td><input type="checkbox" name="ar2_13Normal12"
                                    <%= props.getProperty("ar2_13Normal12", "") %> /></td>
                            <td><input type="checkbox" name="ar2_13Abnormal12"
                                    <%= props.getProperty("ar2_13Abnormal12", "") %> /></td>
                            <td><input type="text" name="ar2_13Comment12"
                                       style="width: 100%" size="40" maxlength="60"
                                       value="<%= props.getProperty("ar2_13Comment12", "") %>"/></td>
                        </tr>
                    </table>

                </td>
                <td colspan="2"><B>14. <fmt:message key='form.bcnewborn.statusAtDischarge'/></B> <textarea
                        name="ar2_14" style="width: 100%" cols="30"
                        rows="3"> <%= props.getProperty("ar2_14", "") %> </textarea>
                </td>
            </tr>
            <tr>
                <td colspan="2"><fmt:message key='form.bcnewborn.problemsRequiringFollowup'/><br>
                    <textarea name="ar2_14Problem" style="width: 100%" cols="30"
                              rows="3"> <%= props.getProperty("ar2_14Problem", "") %> </textarea>
                    <fmt:message key='form.bcnewborn.feeding'/>: <input type="checkbox" name="ar2_14Breast"
                            <%= props.getProperty("ar2_14Breast", "") %> />
                    <fmt:message key='form.bcnewborn.breast'/>&nbsp;&nbsp;&nbsp; <input type="checkbox" name="ar2_14VitD"
                            <%= props.getProperty("ar2_14VitD", "") %> /> <fmt:message key='form.bcnewborn.vitD'/><br>
                    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
                    <input type="checkbox" name="ar2_14Formula"
                            <%= props.getProperty("ar2_14Formula", "") %> /> <fmt:message key='form.bcnewborn.formula'/>
                </td>
            </tr>
            <tr>
                <td width="12%"><B>15. <fmt:message key='form.bcnewborn.discharged'/></B><br>
                    <input type="checkbox" name="ar2_15Home"
                            <%= props.getProperty("ar2_15Home", "") %> /> <fmt:message key='form.bcnewborn.home'/><br>
                    <input type="checkbox" name="ar2_15Adoption"
                            <%= props.getProperty("ar2_15Adoption", "") %> /> <fmt:message key='form.bcnewborn.adoption'/><br>
                    <input type="checkbox" name="ar2_15Foster"
                            <%= props.getProperty("ar2_15Foster", "") %> /> <fmt:message key='form.bcnewborn.fosterHome'/><br>
                    <input type="checkbox" name="ar2_15OtherHosp"
                            <%= props.getProperty("ar2_15OtherHosp", "") %> /> <span
                            class="small8"><fmt:message key='form.bcnewborn.other'/> HOSPITAL</span><br>
                    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<I>(specify)</I><br>
                    <textarea name="ar2_15OtherHospSpec" style="width: 100%" cols="15"
                              rows="2"> <%= props.getProperty("ar2_15OtherHospSpec", "") %> </textarea>
                </td>
                <td>

                    <table class="small8" width="100%" border="0" cellspacing="0"
                           cellpadding="0">
                        <tr>
                            <td colspan="2"><B>16. <fmt:message key='form.bcnewborn.followUpBy'/></B></td>
                            <td><I>(when?)</I></td>
                        </tr>
                        <tr>
                            <td width="2%"><input type="checkbox" name="ar2_16FamPhy"
                                    <%= props.getProperty("ar2_16FamPhy", "") %> /></td>
                            <td width="60%"><fmt:message key='form.bcnewborn.familyPhysician'/></td>
                            <td><input type="text" name="ar2_16FamPhyWhen" size="10"
                                       maxlength="20"
                                       value="<%= props.getProperty("ar2_16FamPhyWhen", "") %>"/></td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="ar2_16Midwife"
                                    <%= props.getProperty("ar2_16Midwife", "") %> /></td>
                            <td><fmt:message key='form.bcnewborn.midwife'/></td>
                            <td><input type="text" name="ar2_16MidwifeWhen" size="10"
                                       maxlength="20"
                                       value="<%= props.getProperty("ar2_16MidwifeWhen", "") %>"/></td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="ar2_16Pediat"
                                    <%= props.getProperty("ar2_16Pediat", "") %> /></td>
                            <td><fmt:message key='form.bcnewborn.pediatrician'/></td>
                            <td><input type="text" name="ar2_16PediatWhen" size="10"
                                       maxlength="20"
                                       value="<%= props.getProperty("ar2_16PediatWhen", "") %>"/></td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="ar2_16OtherConsul"
                                    <%= props.getProperty("ar2_16OtherConsul", "") %> /></td>
                            <td><fmt:message key='form.bcnewborn.otherConsultant'/></td>
                            <td><input type="text" name="ar2_16OtherConsulWhen" size="10"
                                       maxlength="20"
                                       value="<%= props.getProperty("ar2_16OtherConsulWhen", "") %>"/>
                            </td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="ar2_16ComNurse"
                                    <%= props.getProperty("ar2_16ComNurse", "") %> /></td>
                            <td><fmt:message key='form.bcnewborn.communityHealthNurse'/></td>
                            <td><input type="text" name="ar2_16ComNurseWhen" size="10"
                                       maxlength="20"
                                       value="<%= props.getProperty("ar2_16ComNurseWhen", "") %>"/></td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="ar2_16Ministry"
                                    <%= props.getProperty("ar2_16Ministry", "") %> /></td>
                            <td><fmt:message key='form.bcnewborn.ministryFor'/></td>
                            <td><input type="text" name="ar2_16MinistryWhen" size="10"
                                       maxlength="20"
                                       value="<%= props.getProperty("ar2_16MinistryWhen", "") %>"/></td>
                        </tr>
                        <tr>
                            <td></td>
                            <td colspan="2"><fmt:message key='form.bcnewborn.childrenAndFamilyDevelopment'/></td>
                        </tr>
                    </table>


                </td>
            </tr>
        </table>


        <table class="small9" width="100%" border="1" cellspacing="0"
               cellpadding="0">
            <tr>
                <td width="20%"><fmt:message key='form.bcnewborn.hlth'/></td>
                <td width="40%"><fmt:message key='form.bcnewborn.signature'/><br>
                    <input type="text" name="pg3_Signature" size="30" maxlength="80"
                           value="<%= props.getProperty("pg3_Signature", "") %>" @oscar.formDB/>
                    <fmt:message key='form.bcnewborn.mdRm'/>
                </td>
                <td><input type="checkbox" name="ar2_NeoDeath"
                        <%= props.getProperty("ar2_NeoDeath", "") %> /> <fmt:message key='form.bcnewborn.neonatalDeath'/>
                    &nbsp;&nbsp;&nbsp; <input type="checkbox" name="ar2_Autopsy"
                            <%= props.getProperty("ar2_Autopsy", "") %> /> <fmt:message key='form.bcnewborn.autopsyPerformed'/>
                </td>
            </tr>
        </table>


        <table class="Head" class="hidePrint">
            <tr>
                <td align="left">
                    <%
                        if (!bView) {
                    %> <input type="submit" value="<fmt:message key='global.save'/>"
                              onclick="javascript:return onSave();"/> <input type="submit"
                                                                             value="<fmt:message key='global.saveExit'/>"
                                                                             onclick="javascript:return onSaveExit();"/> <%
                    }
                %> <input type="submit" value="<fmt:message key='global.btnExit'/>"
                          onclick="javascript:return onExit();"/> <input type="submit"
                                                                         value="<fmt:message key='global.btnPrint'/>"
                                                                         onclick="javascript:return onPrint();"/></td>
                <%
                    if (!bView) {
                %>

                <td align="right"><!--  b>View:</b>
            <a href="javascript: popupPage('form/formbcnewbornpg1?demographic_no=<%=demoNo%>&formId=<%=formId%>&view=1');">Part1 </a>
            <a href="javascript: popupPage('form/formbcnewbornpg2?demographic_no=<%=demoNo%>&formId=<%=formId%>&view=1');">Part2 <font size=-2>(pg.1)</font></a> |
            &nbsp;--></td>
                <td align="right"><b>Edit:</b> <a
                        href="form/formbcnewbornpg1?demographic_no=<%=demoNo%>&formId=<%=formId%>">Part1</a>
                    | <a
                            href="form/formbcnewbornpg2?demographic_no=<%=demoNo%>&formId=<%=formId%>">Part2
                        <font size=-2>(pg.1)</font></a> | Part2 <font size=-2>(pg.2)</font> |
                </td>
                <%
                    }
                %>
            </tr>
        </table>


    </form>
    <script type="text/javascript">
        Calendar.setup({
            inputField: "pg3_formDate",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "pg3_formDate_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_9Date1",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_9Date1_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_9Date2",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_9Date2_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_9Date3",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_9Date3_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_9Date4",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_9Date4_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_9Date5",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_9Date5_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_9Date6",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_9Date6_cal",
            singleClick: true,
            step: 1
        });

        Calendar.setup({
            inputField: "ar2_10Date1",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_10Date1_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_10Date2",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_10Date2_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_10Date3",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_10Date3_cal",
            singleClick: true,
            step: 1
        });

        Calendar.setup({
            inputField: "ar2_10DateRes1",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_10DateRes1_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_10DateRes2",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_10DateRes2_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_10DateRes3",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_10DateRes3_cal",
            singleClick: true,
            step: 1
        });

        Calendar.setup({
            inputField: "pg3_11Date1",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "pg3_11Date1_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "pg3_11Date2",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "pg3_11Date2_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "pg3_11Date3",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "pg3_11Date3_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "pg3_11Date4",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "pg3_11Date4_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "pg3_11Date5",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "pg3_11Date5_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "pg3_11Date6",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "pg3_11Date6_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "pg3_11Date7",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "pg3_11Date7_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "pg3_11Date8",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "pg3_11Date8_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "pg3_11Date9",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "pg3_11Date9_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "pg3_11Date10",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "pg3_11Date10_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "pg3_11Date11",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "pg3_11Date11_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "pg3_11Date12",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "pg3_11Date12_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "pg3_11Date13",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "pg3_11Date13_cal",
            singleClick: true,
            step: 1
        });

        Calendar.setup({
            inputField: "ar2_12Date",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_12Date_cal",
            singleClick: true,
            step: 1
        });

        Calendar.setup({
            inputField: "ar2_13ExamDate",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_13ExamDate_cal",
            singleClick: true,
            step: 1
        });
    </script>
    </body>
</html>
