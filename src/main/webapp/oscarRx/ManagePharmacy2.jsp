<%@ page import="ca.openosp.openo.prescript.pageUtil.RxSessionBean" %><%--

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

--%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    RxSessionBean bean = null;
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_rx" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_rx");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }

    String type = request.getParameter("type");
%>
<!DOCTYPE HTML>
<html>
    <head>
        <link rel="stylesheet" type="text/css" href="<%=request.getContextPath()%>/oscarRx/styles.css">
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/library/jquery/jquery-ui-1.12.1.min.js"></script>

	<script type="text/javascript" src="${pageContext.request.contextPath}/js/validateTextInputs.js"></script>

<script type="text/javascript">
<%
 if (request.getParameter("ID") != null && type != null && type.equals("Edit")){ %>
	$(function() {
		var data = "pharmacyId=<%=request.getParameter("ID")%>";
		$.post("<%=request.getContextPath()%>/oscarRx/managePharmacy.do?method=getPharmacyInfo",
				  data, function( data ) {
			if(data.name) {
			  $('#pharmacyId').val(<%=request.getParameter("ID")%>);
			  $('#pharmacyName').val(data.name);
			  $('#pharmacyAddress').val(data.address);
			  $('#pharmacyCity').val(data.city);
			  $('#pharmacyProvince').val(data.province);
			  $('#pharmacyPostalCode').val(data.postalCode);
			  $('#pharmacyPhone1').val(data.phone1);
			  $('#pharmacyPhone2').val(data.phone2);
			  $('#pharmacyFax').val(data.fax);
			  $('#pharmacyEmail').val(data.email);
			  $('#pharmacyServiceLocationId').val(data.serviceLocationIdentifier);
			  $('#pharmacyNotes').val(data.notes);
			}
			else {
				alert("Unable to retrieve pharmacy information");
			}
		},"json");
	});
<% } %>
  function savePharmacy() {
		sanitizeAllElements()
	var typeName = (new URLSearchParams(window.location.search)).get('type')
	if (typeName != null && typeName.toLowerCase() == "edit") {
		const saveWarningStr = "WARNING - you are about to edit a pharmacy's entry in the clinic's database. Any changes will automatically apply to all patients who already have this pharmacy as a preferred pharmacy.\n\nOnly proceed if you are absolutely sure. Type \"yes\" in the box below to proceed.";
		const userInput = prompt(saveWarningStr);
		if (userInput == null || userInput.toLowerCase() != "yes") {
			alert("This pharmacy's entry has not been edited because you did not type \"yes\" in the previous box.");
			return false;
		}
	}
	  if( !isFaxNumberCorrect() ) {   return false;  }

                if ($("#pharmacyId").val() != null && $("#pharmacyId").val() != "") {

                    var data = $("#pharmacyForm").serialize();
                    $.post("<%=request.getContextPath() + "/oscarRx/managePharmacy.do?method=save"%>",
                        data, function (data) {
                            if (data.id) {
                                parent.location.reload();
                            } else {
                                alert("There was a problem saving your record");
                            }
                        }, "json");
                } else {
                    addPharmacy();
                }

                return false;
            }

            function addPharmacy() {

                if ($("#pharmacyName").val() == "") {
                    alert("Please fill in the name of a pharmacy");
                    return false;
                }

                var data = $("#pharmacyForm").serialize();
                $.post("<%=request.getContextPath() + "/oscarRx/managePharmacy.do?method=add"%>",
                    data, function (data) {
                        if (data.success) {
                            parent.location.reload();
                        } else {
                            alert("There was an error saving your Pharmacy");
                        }
                    },
                    "json"
                );
            }

            function isFaxNumberCorrect() {

                var faxNumber = $("#pharmacyFax").val().trim();
                var isCorrect = false;

                if (faxNumber.split("-").join("").length == 12) {
                    isCorrect = faxNumber.match(/^1?\s?\(?[9]{1}\)?[\-\s]?[1|9]{1}\)?[\-\s]?[0-9]{3}\)?[\-\s]?[0-9]{3}[\-\s]?[0-9]{4}$/);
                } else if (faxNumber.split("-").join("").length == 11) {
                    isCorrect = faxNumber.match(/^1?\s?\(?[1|9]{1}\)?[\-\s]?[0-9]{3}\)?[\-\s]?[0-9]{3}[\-\s]?[0-9]{4}$/);
                } else if (faxNumber.split("-").join("").length == 10) {
                    isCorrect = faxNumber.match(/^1?\s?\(?[0-9]{3}\)?[\-\s]?[0-9]{3}[\-\s]?[0-9]{4}$/)
                }

                if (!isCorrect) {

                    alert("Fax number REQUIRED, in the following formats:" +
                        "\n###-###-#### " +
                        "\n1-###-###-###" +
                        "\n9-###-###-####" +
                        "\n9-1-###-###-####" +
                        "\n9-9-###-###-####");
                    setTimeout(function () {
                        $("#pharmacyFax").focus();
                    }, 1);

                }

                return isCorrect;
            }

        </script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.title"/></title>
        <script src="<%=request.getContextPath()%>/csrfguard" type="text/javascript"></script>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

        <c:if test="${empty RxSessionBean}">
            <c:redirect url="error.html"/>
        </c:if>
        <c:if test="${not empty sessionScope.RxSessionBean}">
    <%
        // Directly access the RxSessionBean from the session
        bean = (RxSessionBean) session.getAttribute("RxSessionBean");
        if (bean != null && !bean.isValid()) {
            response.sendRedirect("error.html");
            return; // Ensure no further JSP processing
        }
    %>
        </c:if>
    </head>
    <body topmargin="0" leftmargin="0" vlink="#0000FF">

    <table border="0" cellpadding="0" cellspacing="0"
           style="border-collapse: collapse" bordercolor="#111111" width="100%"
           id="AutoNumber1" height="100%">
        <tr>
            <td width="100%" height="100%"
                valign="top" colspan="2">
                <table cellpadding="0" cellspacing="2"
                       style="border-collapse: collapse" bordercolor="#111111" width="100%"
                       height="100%">
                    <!----Start new rows here-->
                    <tr>
                        <td>
                            <div class="DivContentSectionHead" style="height:8px; text-indent: 10px">
                                <% if (request.getParameter("ID") == null) { %> <fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.subTitle.add"/> <%} else {%> <fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.subTitle.update"/> <%}%>
                            </div>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <form id="pharmacyForm">
                                <table>
                                    <tr>
                                        <td>
                                            <input type="hidden" id="pharmacyId" name="pharmacyId"/>
                                            <input type="hidden" id="demographicNo" name="demographicNo"
                                                   value="<%=bean.getDemographicNo()%>"/>
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.pharmacyName"/> :
                                        </td>
                                        <td><input type="text" id="pharmacyName" name="pharmacyName"/></td>
                                    </tr>
                                    <tr>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.address"/>
                                            :
                                        </td>
                                        <td><input type="text" id="pharmacyAddress" name="pharmacyAddress"/></td>
                                    </tr>
                                    <tr>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.city"/>
                                            :
                                        </td>
                                        <td><input type="text" id="pharmacyCity" name="pharmacyCity"/></td>
                                    </tr>
                                    <tr>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.province"/>
                                            :
                                        </td>
                                        <td><input type="text" id="pharmacyProvince" name="pharmacyProvince"/></td>
                                    </tr>
                                    <tr>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.postalCode"/> :
                                        </td>
                                        <td><input type="text" id="pharmacyPostalCode" name="pharmacyPostalCode"/></td>
                                    </tr>
                                    <tr>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.phone1"/>
                                            :
                                        </td>
                                        <td><input type="text" id="pharmacyPhone1" name="pharmacyPhone1"/></td>
                                    </tr>
                                    <tr>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.phone2"/>
                                            :
                                        </td>
                                        <td><input type="text" id="pharmacyPhone2" name="pharmacyPhone2"/></td>
                                    </tr>
                                    <tr>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.fax"/> :
                                        </td>
                                        <td><input type="text" id="pharmacyFax" name="pharmacyFax"/></td>
                                    </tr>
                                    <tr>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.email"/>
                                            :
                                        </td>
                                        <td><input type="text" id="pharmacyEmail" name="pharmacyEmail"/></td>
                                    </tr>
                                    <tr>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.serviceLocationIdentifier"/>
                                            :
                                        </td>
                                        <td><input type="text" id="pharmacyServiceLocationId"
                                                   name="pharmacyServiceLocationId"/></td>
                                    </tr>

                                    <tr>
                                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.txtfld.label.notes"/> :
                                        </td>
                                        <td><textarea id="pharmacyNotes" name="pharmacyNotes" rows="4"></textarea></td>
                                    </tr>

                                    <tr>
                                        <td><input type="button" onclick="savePharmacy();"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="ManagePharmacy.submitBtn.label.submit"/>"/>
                                        </td>
                                    </tr>
                                </table>
                            </form>
                        </td>
                    </tr>
                    <!----End new rows here-->
                    <tr height="100%">
                        <td></td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>

    </body>

</html>
