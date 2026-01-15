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

--%>
<!DOCTYPE html>
<%
    if (session.getAttribute("user") == null) {
        response.sendRedirect("${pageContext.request.contextPath}/logout.jsp");
    }
    String user_no = (String) session.getAttribute("user");

%>
<%@ page errorPage="/errorpage.jsp"
         import="java.util.*,java.sql.*,ca.openosp.*,java.text.*,java.net.*" %>
<%@ page import="ca.openosp.openo.billings.ca.on.data.JdbcBillingPageUtil" %>
<%@ page import="ca.openosp.openo.billing.ca.on.data.*" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="ca.openosp.openo.billings.ca.on.data.BillingDataHlp" %>
<% //
    int serviceCodeLen = 5;
    String msg = "Type in a name and search first to see if it is available.";
    String action = "search"; // add/edit
    //BillingServiceCode serviceCodeObj = new BillingServiceCode();
    Properties prop = new Properties();
    JdbcBillingPageUtil dbObj = new JdbcBillingPageUtil();
    if (request.getParameter("submit") != null && request.getParameter("submit").equals("Save")) {
        if (request.getParameter("action").startsWith("edit")) {
            // update the service code
            String name = request.getParameter("name");
            if (name.equals(request.getParameter("action").substring("edit".length()))) {
                String list = "";
                for (int i = 0; i < BillingDataHlp.FIELD_SERVICE_NUM; i++) {
                    if (request.getParameter("serviceCode" + i).length() == 5) {
                        String unit = request.getParameter("serviceUnit" + i).length() > 0 ? request
                                .getParameter("serviceUnit" + i) : "1";
                        String at = request.getParameter("serviceAt" + i).length() > 0 ? request
                                .getParameter("serviceAt" + i) : "1";
                        list += request.getParameter("serviceCode" + i) + "|" + unit + "|" + at + "|";
                    }
                }

                if (request.getParameter("dx").length() == 3) {
                    list += request.getParameter("dx") + "|";
                    if (request.getParameter("dx1").length() == 3) {
                        list += request.getParameter("dx1") + "|";
                        if (request.getParameter("dx2").length() == 3) {
                            list += request.getParameter("dx2") + "|";
                        }
                    }
                }

                boolean ni = dbObj.updateBillingFavouriteList(name, list, user_no);
                if (ni) {
                    msg = name + " is updated.<br>"
                            + "Type in a name and search first to see if it is available.";
                    action = "search";
                    prop.setProperty("name", name);
                } else {
                    msg = name + " is <font color='red'>NOT</font> updated. Action failed! Try edit it again.";
                    action = "edit" + name;
                    prop.setProperty("name", name);
                    for (int i = 0; i < BillingDataHlp.FIELD_SERVICE_NUM; i++) {
                        prop.setProperty("serviceCode" + i, request.getParameter("serviceCode" + i));
                        prop.setProperty("serviceUnit" + i, request.getParameter("serviceUnit" + i));
                        prop.setProperty("serviceAt" + i, request.getParameter("serviceAt" + i));
                    }
                    prop.setProperty("dx", request.getParameter("dx"));
                    prop.setProperty("dx1", request.getParameter("dx1"));
                    prop.setProperty("dx2", request.getParameter("dx2"));
                }
            } else {
                msg = "You can <font color='red'>NOT</font> save the name - " + name
                        + ". Please search the name first.";
                action = "search";
                prop.setProperty("name", name);
            }

        } else if (request.getParameter("action").startsWith("add")) {
            String name = request.getParameter("name");
            if (name.equals(request.getParameter("action").substring("add".length()))) {
                String list = "";
                for (int i = 0; i < BillingDataHlp.FIELD_SERVICE_NUM; i++) {
                    if (request.getParameter("serviceCode" + i).length() == 5) {
                        String unit = request.getParameter("serviceUnit" + i).length() > 0 ? request
                                .getParameter("serviceUnit" + i) : "1";
                        String at = request.getParameter("serviceAt" + i).length() > 0 ? request
                                .getParameter("serviceAt" + i) : "1";
                        if (at.length() == 3) {
                            if (at.startsWith(".")) {
                                at = "0" + at;
                            } else {
                                at = at + "0";
                            }
                        }
                        list += request.getParameter("serviceCode" + i) + "|" + unit + "|" + at + "|";
                    }
                }

                if (request.getParameter("dx").length() == 3) {
                    list += request.getParameter("dx") + "|";
                    if (request.getParameter("dx1").length() == 3) {
                        list += request.getParameter("dx1") + "|";
                        if (request.getParameter("dx2").length() == 3) {
                            list += request.getParameter("dx2") + "|";
                        }
                    }
                }

                int ni = dbObj.addBillingFavouriteList(name, list, user_no);
                if (ni > 0) {
                    msg = name + " is added.<br>"
                            + "Type in a name and search first to see if it is available.";
                    action = "search";
                    prop.setProperty("name", name);
                } else {
                    msg = name + " is <font color='red'>NOT</font> added. Action failed! Try edit it again.";
                    action = "add" + name;
                    prop.setProperty("name", name);
                    for (int i = 0; i < BillingDataHlp.FIELD_SERVICE_NUM; i++) {
                        prop.setProperty("serviceCode" + i, request.getParameter("serviceCode" + i));
                        prop.setProperty("serviceUnit" + i, request.getParameter("serviceUnit" + i));
                        prop.setProperty("serviceAt" + i, request.getParameter("serviceAt" + i));
                    }
                    prop.setProperty("dx", request.getParameter("dx"));
                    prop.setProperty("dx1", request.getParameter("dx1"));
                    prop.setProperty("dx2", request.getParameter("dx2"));
                }
            } else {
                msg = "You can <font color='red'>NOT</font> save the name - " + name
                        + ". Please search the name first.";
                action = "search";
                prop.setProperty("name", name);
            }
        } else {
            msg = "You can <font color='red'>NOT</font> save the name. Please search the name first.";
        }
    } else if (request.getParameter("submit") != null && request.getParameter("submit").equals("Search")) {
//				 @ OSCARSERVICE
        if (request.getParameter("action").equals("Delete")) {
            // delete the service code
            String name = request.getParameter("name");
            if (name == null || name.equals("")) {
                msg = "nothing to delete, please choose a name.";
                action = "search";
            } else {
                boolean ni = dbObj.delBillingFavouriteList(name, user_no);
                if (ni) {
                    msg = name + " is deleted.<br>"
                            + "Type in a name and search first to see if it is available.";
                    action = "search";
                    prop.setProperty("name", name);
                } else {
                    msg = name + " is <font color='red'>NOT</font> deleted. Action failed! Try edit it again.";
                    action = "edit" + name;
                    prop.setProperty("name", name);
                }
            }
        }
        // @ OSCARSERVICE
        else {
            // check the input data
            if (request.getParameter("name") == null) {
                msg = "Please type in a right name.";
            } else {
                String name = request.getParameter("name");
                List ni = dbObj.getBillingFavouriteOne(name);
                if (ni != null && ni.size() > 0) {
                    prop.setProperty("name", (String) ni.get(0));
                    String list1 = (String) ni.get(1);
                    String[] temp = list1.split("\\|");
                    int n = 0;
                    for (int i = 0; i < temp.length; i++) {
                        if (temp[i].length() == 5) {
                            prop.setProperty("serviceCode" + n, temp[i]);
                            prop.setProperty("serviceUnit" + n, temp[i + 1]);
                            prop.setProperty("serviceAt" + n, temp[i + 2]);
                            i = i + 2;
                            n++;
                        } else if (temp[i].length() == 3) {
                            if (prop.getProperty("dx", "").equals(""))
                                prop.setProperty("dx", temp[i]);
                            else if (prop.getProperty("dx1", "").equals(""))
                                prop.setProperty("dx1", temp[i]);
                            else if (prop.getProperty("dx2", "").equals(""))
                                prop.setProperty("dx2", temp[i]);
                        }
                    }
                    msg = "You can edit the name.";
                    action = "edit" + name;

                } else {
                    prop.setProperty("name", name);
                    msg = "It is a NEW name. You can add it.";
                    action = "add" + name;
                }
            }
        }
    }
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<html>
    <head>
        <title>Add/Edit Service Code</title>

        <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
        <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet"> <!-- Bootstrap 2.3.1 -->

        <script language="JavaScript">

            <!--
            function setfocus() {
                this.focus();
                document.forms[0].name.focus();
                document.forms[0].name.select();
            }

            function onSearch() {
                //document.forms[0].submit.value="Search";
                var ret = checkServiceCode();
                return ret;
            }

            // @ OSCARSERVICE
            function onDelete() {
                var ret = false;
                ret = confirm("Are you sure you want to Delete?");
                return ret;
            }

            // @ OSCARSERVICE

            function onSave() {
                //document.forms[0].submit.value="Save";
                var ret = checkServiceCode();
                if (ret == true) {
                    ret = checkAllFields();
                }
                if (ret == true) {
                    ret = confirm("Are you sure you want to save?");
                }
                return ret;
            }

            function checkServiceCode() {
                var b = true;
                if (document.forms[0].service_code.value.length != 5 || !isServiceCode(document.forms[0].service_code.value)) {
                    b = false;
                    alert("You must type in a service code with 5 (upper case) letters/digits. The service code ends with \'A\' or \'B\'...");
                }
                return b;
            }

            function isServiceCode(s) {
                // temp for 0.
                if (s.length == 0) return true;
                if (s.length != 5) return false;
                if ((s.charAt(0) < "A") || (s.charAt(0) > "Z")) return false;
                if ((s.charAt(4) < "A") || (s.charAt(4) > "Z")) return false;

                var i;
                for (i = 1; i < s.length - 1; i++) {
                    // Check that current character is number.
                    var c = s.charAt(i);
                    if (((c < "0") || (c > "9"))) return false;
                }
                return true;
            }

            function checkAllFields() {
                var b = true;
                for (var i = 0; i < 10; i++) {
                    var fieldItem = eval("document.forms[1].serviceCode" + i);
                    if (fieldItem.value.length > 0) {
                        if (!isServiceCode(fieldItem.value)) {
                            b = false;
                            alert("You must type in a Service Code in the field!");
                        }
                    }
                    var fieldItem1 = eval("document.forms[1].serviceUnit" + i);
                    var fieldItem2 = eval("document.forms[1].serviceAt" + i);
                    if (fieldItem1.value.length > 0) {
                        if (!isNumber(fieldItem1.value)) {
                            b = false;
                            alert("You must type in a number in the field!");
                        }
                    }
                    if (fieldItem2.value.length > 0) {
                        if (!isNumber(fieldItem2.value)) {
                            b = false;
                            alert("You must type in a number in the field!");
                        }
                    }
                }
                var fieldItemDx = eval("document.forms[1].dx");
                if (fieldItemDx.value.length > 0) {
                    if (!isNumber(fieldItemDx.value) || fieldItemDx.value.length != 3) {
                        b = false;
                        alert("You must type in a number in the right Dx field!");
                    }
                }
                return b;
            }

            function isNumber(s) {
                var i;
                for (i = 0; i < s.length; i++) {
                    // Check that current character is number.
                    var c = s.charAt(i);
                    if (c == ".") continue;
                    if (((c < "0") || (c > "9"))) return false;
                }
                // All characters are numbers.
                return true;
            }

            function upCaseCtrl(ctrl) {
                ctrl.value = ctrl.value.toUpperCase();
            }

            //-->

        </script>
    </head>
    <body onLoad="setfocus()">
    <h4>Add/Edit Service Code</h4>
    <table style="width:100%;">
        <tr class="myDarkGreen">
            <th class="alert alert-info"><%=msg%>
            </th>
        </tr>
    </table>

    <form method="post" name="baseur0" action="billingONfavourite.jsp">
        <table style="width:100%;">
            <tr>
                <td style="width:50%; text-align: center;"><select name="name" id="name">
                    <option selected="selected" value="">- choose one -</option>
                    <%
                        //
                        List sL = dbObj.getBillingFavouriteList();
                        for (int i = 0; i < sL.size(); i = i + 2) {
                    %>
                    <option value="<%=(String) sL.get(i)%>"><%=(String) sL.get(i)%>
                    </option>
                    <%
                        }
                    %>
                </select></td>
                <td><input class="input-mini" type="hidden" name="submit" value="Search"> <input class="btn"
                                                                                                 type="submit"
                                                                                                 name="action"
                                                                                                 value=" Edit "> <input
                        class="btn"
                        type="submit" name="action" value="Delete"
                        onClick="javascript:return onDelete();"></td>
            </tr>

        </table>
    </form>
    <form method="post" name="baseurl" action="billingONfavourite.jsp">
        <table style="width:100%;" class="table table-striped table-condensed">

            <tr class="myGreen">
                <td style="text-align:right"><b>Name</b></td>
                <td><input class="input" type="text" name="name"
                           value="<%=prop.getProperty("name", "")%>" maxlength='50'/>
                    (e.g. Flu shot) <input class="btn" type="submit" name="submit" value="Search"
                                           onclick="javascript:return onSearch();"></td>
            </tr>

            <%
                for (int i = 0; i < BillingDataHlp.FIELD_SERVICE_NUM; i++) {

            %>
            <tr>
                <td style="text-align:right"><b>Service Code <%=i + 1%>
                </b></td>
                <td><input class="input-mini" type="text" name="serviceCode<%=i%>"
                           value="<%=prop.getProperty("serviceCode"+i, "")%>"
                           maxlength='50' onblur="upCaseCtrl(this)"/> (e.g. A001A) <b>Unit</b><input class="input-mini"
                                                                                                     type="text"
                                                                                                     name="serviceUnit<%=i%>"
                                                                                                     value="<%=prop.getProperty("serviceUnit"+i, "")%>"
                                                                                                     maxlength='2'/>
                    (e.g. 1, 12) <b>@</b><input class="input-mini" type="text"
                                                name="serviceAt<%=i%>"
                                                value="<%=prop.getProperty("serviceAt"+i, "")%>"
                                                maxlength='4'/> (e.g. 0.85)
                </td>
            </tr>
            <%
                }

            %>

            <tr>
                <td style="text-align:right"><b>Dx</b></td>
                <td><input class="input-mini" type="text" name="dx"
                           value="<%=prop.getProperty("dx", "")%>" maxlength='4'/>
                    (e.g. 012) <b>Dx1</b> <input class="input-mini" type="text" name="dx1"
                                                 value="<%=prop.getProperty("dx1", "")%>" maxlength='4'/> <b>Dx2</b>
                    <input class="input-mini" type="text" name="dx2" value="<%=prop.getProperty("dx2", "")%>"
                           maxlength='4'/></td>
            </tr>
            <tr>
                <td style="text-align:center" class="myGreen" colspan="2"><input
                        type="hidden" name="action" value='<%=action%>'> <input
                        type="submit" name="submit" class="btn btn-primary"
                        value="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.resourcebaseurl.btnSave"/>"
                        onclick="javascript:return onSave();"> <input class="btn" type="button"
                                                                      name="Cancel"
                                                                      value="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.resourcebaseurl.btnExit"/>"
                                                                      onClick="window.close()"></td>
            </tr>
        </table>
    </form>
    </body>
</html>