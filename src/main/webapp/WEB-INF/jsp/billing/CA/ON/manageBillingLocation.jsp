<%--
  Page role: Renders `manageBillingLocation.jsp` for the Ontario billing workflow.
  Expected request model data includes: manageLocationModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<!DOCTYPE html>
<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
    manageBillingLocation.jsp (view) - Ontario billing clinic-location admin.
    Rendered by ManageBillingLocation2Action which:
      - enforces _admin.billing w
      - on `submit=Delete` (POST), removes the named clinic location
      - resolves the location list + the three echoed parameters into
        ${manageLocationModel}.
    Pure presentation here.
    @since 2006
--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<html>
    <head>
        <title><fmt:message key="admin.admin.btnAddBillingLocation"/></title>
        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
        <script language="JavaScript">
            <!--

            function selectprovider(s) {
                if (self.location.href.lastIndexOf("&providerview=") > 0) a = self.location.href.substring(0, self.location.href.lastIndexOf("&providerview="));
                else a = self.location.href;
                self.location.href = a + "&providerview=" + s.options[s.selectedIndex].value;
            }

            function openBrWindow(theURL, winName, features) {
                window.open(theURL, winName, features);
            }

            function setfocus() {
                this.focus();
                document.ADDAPPT.keyword.focus();
                document.ADDAPPT.keyword.select();
            }

            function valid(form) {
                if (validateServiceType(form)) {
                    form.action = "${pageContext.request.contextPath}/billing/CA/ON/DbManageBillingformAdd"
                    form.submit()
                } else {
                }
            }

            function validateServiceType() {
                if (document.servicetypeform.typeid.value == "MFP") {
                    alert("<fmt:message key="billing.manageBillingLocation.msgServiceTypeExists"/>");
                    return false;
                } else {
                    return true;
                }

            }

            function refresh() {
                var u = self.location.href;
                var idx = u.lastIndexOf("view=1");
                if (idx > 0) {
                    self.location.href = u.substring(0, idx) + "view=0" + u.substring(idx + 6);
                } else {
                    history.go(0);
                }
            }

            function confirmthis(lno) {
                if (confirm("Are you sure that you want to delete the location " + lno + "?")) {
                    return true;
                } else {
                    return false;
                }
            }

            //-->
        </script>
    </head>

    <body>
    <h3><fmt:message key="admin.admin.btnAddBillingLocation"/></h3>
    <div class="container-fluid card card-body bg-body-tertiary">
        <table>
            <tr>
                <td width="3%"></td>
                <td width="30%" align="left" valign="top">
                    <form name="serviceform" method="post"
                          action="DbManageBillingLocation"><B><fmt:message key="billing.manageBillingLocation.msgCodeDescription"/></B> <br>
                        <input style="width:40px" type="text" name="location1" size="4"> <input type="text"
                                                                                                name="location1desc"
                                                                                                size="30"> <br>
                        <input style="width:40px" type="text" name="location2" size="4"> <input type="text"
                                                                                                name="location2desc"
                                                                                                size="30"> <br>
                        <input style="width:40px" type="text" name="location3" size="4"> <input type="text"
                                                                                                name="location3desc"
                                                                                                size="30"> <br>
                        <input style="width:40px" type="text" name="location4" size="4"> <input type="text"
                                                                                                name="location4desc"
                                                                                                size="30"> <br>
                        <input style="width:40px" type="text" name="location5" size="4"> <input type="text"
                                                                                                name="location5desc"
                                                                                                size="30"> <br>
                        <br>
                        <input class="btn btn-primary" type="submit" name="action"
                               value="<fmt:message key="billing.manageBillingLocation.btnAdd"/>">
                        <br>
                        </p>
                    </form>
                </td>

                <td width="37%" valign="top">

                    <table class="table table-striped  table-sm">
                        <tr>
                            <th width="6%"><fmt:message key="billing.manageBillingLocation.msgClinicLocation"/></th>
                            <th><fmt:message key="billing.manageBillingLocation.msgDescription"/></th>
                            <th>Action</th>
                        </tr>

                        <c:choose>
                            <c:when test="${empty manageLocationModel.locations}">
                                <c:out value="failed!!!"/>
                            </c:when>
                            <c:otherwise>
                                <c:forEach var="clinicLocation" items="${manageLocationModel.locations}">
                                    <tr>
                                        <form name="serviceform" method="post"
                                              action="${pageContext.request.contextPath}/billing/CA/ON/ManageBillingLocation"
                                              onsubmit="return confirmthis(<carlos:encode value='${clinicLocation.clinicLocationNo}' context='javaScript'/>);">
                                            <td align="center"><carlos:encode value="${clinicLocation.clinicLocationNo}" context="html"/>
                                            </td>
                                            <td><carlos:encode value="${clinicLocation.clinicLocationName}" context="html"/>
                                            </td>
                                            <td align="center"><input class="btn btn-secondary" type="submit" name="submit"
                                                                      value="Delete"/> <input type="hidden" name="location_no"
                                                                                              value="<carlos:encode value='${clinicLocation.clinicLocationNo}' context='htmlAttribute'/>"/>
                                            </td>
                                        </form>
                                    </tr>
                                </c:forEach>
                            </c:otherwise>
                        </c:choose>

                    </table>

                </td>
            </tr>

        </table>
    </div>
    </body>
</html>
