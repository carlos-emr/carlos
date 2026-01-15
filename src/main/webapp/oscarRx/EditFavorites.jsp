<%@ page import="ca.openosp.openo.prescript.pageUtil.RxSessionBean" %>
<%@ page import="ca.openosp.openo.prescript.data.RxDrugData" %>
<%@ page import="ca.openosp.openo.prescript.data.RxCodesData" %>
<%@ page import="ca.openosp.openo.prescript.data.RxPrescriptionData" %><%--

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

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Edit Favorites</title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

        <c:if test="${empty RxSessionBean}">
            <% response.sendRedirect("error.html"); %>
        </c:if>
        <c:if test="${not empty RxSessionBean}">
            <c:set var="bean" value="${RxSessionBean}" scope="page"/>
            <c:if test="${bean.valid == false}">
                <% response.sendRedirect("error.html"); %>
            </c:if>
        </c:if>
        <%
            RxSessionBean bean = (RxSessionBean) pageContext.findAttribute("bean");
        %>
        <link rel="stylesheet" type="text/css" href="oscarRx/styles.css">
    </head>


    <%
        RxPrescriptionData rxData = new RxPrescriptionData();
        RxDrugData drugData = new RxDrugData();

        RxPrescriptionData.Favorite[] favorites = rxData.getFavorites(bean.getProviderNo());
        RxPrescriptionData.Favorite f;

        RxCodesData.FrequencyCode[] freq = new RxCodesData().getFrequencyCodes();

        int i, j;
    %>

    <form action="${pageContext.request.contextPath}/oscarRx/updateFavorite.do" method="post">
        <input type="hidden" name="favoriteId" id="favoriteId"/>
        <input type="hidden" name="favoriteName" id="favoriteName"/>
        <input type="hidden" name="customName" id="customName"/>
        <input type="hidden" name="takeMin" id="takeMin"/>
        <input type="hidden" name="takeMax" id="takeMax"/>
        <input type="hidden" name="frequencyCode" id="frequencyCode"/>
        <input type="hidden" name="duration" id="duration"/>
        <input type="hidden" name="durationUnit" id="durationUnit"/>
        <input type="hidden" name="quantity" id="quantity"/>
        <input type="hidden" name="repeat" id="repeat"/>
        <input type="hidden" name="nosubs" id="nosubs"/>
        <input type="hidden" name="prn" id="prn"/>
        <input type="hidden" name="special" id="special"/>
        <input type="hidden" name="customInstr" id="customInstr"/>
    </form>

    <form action="${pageContext.request.contextPath}/oscarRx/deleteFavorite.do" method="post">
        <input type="hidden" name="favoriteId" id="favoriteId"/>
    </form>

    <script language=javascript>
        function updateRow(rowId) {
            var put = document.forms.RxUpdateFavoriteForm;
            var get = document.forms.DispForm;

            var err = false;

            put.favoriteId.value = eval('get.fldFavoriteId' + rowId).value;
            put.favoriteName.value = eval('get.fldFavoriteName' + rowId).value;
            put.customName.value = eval('get.fldCustomName' + rowId).value;
            put.takeMin.value = eval('get.fldTakeMin' + rowId).value;
            put.takeMax.value = eval('get.fldTakeMax' + rowId).value;
            put.frequencyCode.value = eval('get.fldFrequencyCode' + rowId).value;
            put.duration.value = eval('get.fldDuration' + rowId).value;
            put.durationUnit.value = eval('get.fldDurationUnit' + rowId).value;
            put.quantity.value = eval('get.fldQuantity' + rowId).value;
            put.repeat.value = eval('get.fldRepeat' + rowId).value;

            put.nosubs.value = eval('get.fldNosubs' + rowId).checked;
            put.prn.value = eval('get.fldPrn' + rowId).checked;
            put.customInstr.value = eval('get.customInstr' + rowId).checked;

            put.special.value = eval('get.fldSpecial' + rowId).value;

            if (put.favoriteName.value == null || put.favoriteName.value.length < 1) {
                alert('Please enter a favorite name.');
                err = true;
            }

            if (put.takeMin.value.length < 1 || isNaN(put.takeMin.value)) {
                alert('Incorrect entry in field Take Min.');
                err = true;
            }
            if (put.takeMax.value.length < 1 || isNaN(put.takeMax.value)) {
                alert('Incorrect entry in field Take Max.');
                err = true;
            }
            if (put.duration.value.length < 1 || isNaN(put.duration.value)) {
                alert('Incorrect entry in field Duration.');
                err = true;
            }
            if (put.quantity.value.length < 1) {
                alert('Incorrect entry in field Quantity.');
                err = true;
            }
            if (put.repeat.value.length < 1 || isNaN(put.repeat.value)) {
                alert('Incorrect entry in field Repeat.');
                err = true;
            }

            if (err == false) {
                put.submit();
            }
        }

        function deleteRow(rowId) {
            var fId = eval('document.forms.DispForm.fldFavoriteId' + rowId).value;
            var fName = eval('document.forms.DispForm.fldFavoriteName' + rowId).value;

            if (confirm('Are you sure you want to delete favorite: \n' + fName + '?')) {
                document.forms.RxDeleteFavoriteForm.favoriteId.value = fId;
                document.forms.RxDeleteFavoriteForm.submit();
            }
        }

    </script>


    <body topmargin="0" leftmargin="0" vlink="#0000FF">
    <table border="0" cellpadding="0" cellspacing="0"
           style="border-collapse: collapse; position: absolute; left: 0; top:0;" bordercolor="#111111" width="100%"
           id="AutoNumber1" height="100%">
        <%@ include file="TopLinks.jsp"%><!-- Row One included here-->
        <tr>
            <td></td>


            <td width="100%" style="border-left: 2px solid #A9A9A9;" height="100%"
                valign="top">
                <table style="border-collapse: collapse" bordercolor="#111111"
                       width="100%" height="100%">
                    <tr>
                        <td width="0%" valign="top">
                            <div class="DivCCBreadCrumbs"><a href="oscarRx/SearchDrug.jsp"> <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.title"/></a> > <b><fmt:setBundle basename="oscarResources"/><fmt:message key="StaticScript.title.EditFavorites"/></b></div>
                        </td>
                    </tr>


                    <!----Start new rows here-->
                    <tr>
                        <td>
                            <div class="DivContentPadding">
                                <div class="DivContentSectionHead">Favorites</div>
                            </div>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <div class=DivContentPadding><input type=button
                                                                value="Back to Search For Drug"
                                                                class="ControlPushButton"
                                                                onClick="javascript:window.location.href='<%= request.getContextPath() %>/oscarRx/SearchDrug.jsp';"/>
                            </div>
                        </td>
                    </tr>

                    <tr>
                        <td>
                            <div class="DivContentPadding">
                                <form name="DispForm">
                                    <table cellspacing=0 cellpadding=2>
                                        <%
                                            String style;

                                            for (i = 0; i < favorites.length; i++) {
                                                f = favorites[i];
                                                boolean isCustom = f.getGCN_SEQNO() == 0;

                                                style = "style='background-color:#F5F5F5'";
                                        %>
                                        <tr class=tblRow <%= style %> name="record<%= i%>Line1">
                                            <td colspan=2><b>Favorite Name:</b><input type=hidden
                                                                                      name="fldFavoriteId<%= i%>"
                                                                                      value="<%= f.getFavoriteId() %>"/>
                                                <input type=text size="50" name="fldFavoriteName<%= i%>"
                                                       class=tblRow size=80 value="<%= f.getFavoriteName() %>"/>&nbsp;&nbsp;&nbsp;
                                            </td>
                                            <td colspan=5><a href="javascript:updateRow(<%= i%>);">Save
                                                Changes</a>&nbsp;&nbsp;&nbsp; <a href="javascript:deleteRow(<%= i%>);">Delete
                                                Favorite</a></td>
                                        </tr>
                                        <% if (!isCustom) { %>
                                        <tr class=tblRow <%= style %> name="record<%= i%>Line2">
                                            <td><b>Brand Name:</b><%= f.getBN() %>
                                            </td>
                                            <td colspan=5><b>Generic Name:</b><%= f.getGN() %>
                                            </td>
                                            <td colspan=1>&nbsp; <input type="hidden"
                                                                        name="fldCustomName<%= i%>" value=""/></td>
                                        </tr>
                                        <% } else { %>
                                        <tr class=tblRow <%= style %> name="record<%= i%>Line2">
                                            <td colspan=7><b>Custom Drug Name:</b> <input type=text
                                                                                          size="50"
                                                                                          name="fldCustomName<%= i%>"
                                                                                          class=tblRow size=80
                                                                                          value="<%= f.getCustomName() %>"/>
                                            </td>
                                        </tr>
                                        <% } %>
                                        <tr class=tblRow <%= style %> name="record<%= i%>Line3">
                                            <td nowrap><b>Take:</b> <input type=text
                                                                           name="fldTakeMin<%= i%>" class=tblRow size=3
                                                                           value="<%= f.getTakeMin() %>"/>
                                                <span>to</span> <input
                                                        type=text name="fldTakeMax<%= i%>" class=tblRow size=3
                                                        value="<%= f.getTakeMax() %>"/> <select
                                                        name="fldFrequencyCode<%= i%>" class=tblRow>
                                                    <%
                                                        for (j = 0; j < freq.length; j++) {
                                                    %>
                                                    <option
                                                            value="<%= freq[j].getFreqCode() %>"
                                                            <%
                                                                if (freq[j].getFreqCode().equals(f.getFrequencyCode())) {
                                                            %>
                                                            selected="selected"
                                                            <%
                                                                }
                                                            %>><%=freq[j].getFreqCode()%>
                                                    </option>
                                                    <%
                                                        }
                                                    %>
                                                </select> <b>For:</b> <input type=text name="fldDuration<%= i%>"
                                                                             class=tblRow size=3
                                                                             value="<%= f.getDuration() %>"/> <select
                                                        name="fldDurationUnit<%= i%>" class=tblRow>
                                                    <option
                                                            <%
                                                                if (f.getDurationUnit().equals("D")) { %>
                                                            selected="selected"
                                                            <% }
                                                            %>
                                                            value="D">Day(s)
                                                    </option>
                                                    <option
                                                            <%
                                                                if (f.getDurationUnit().equals("W")) { %>
                                                            selected="selected"
                                                            <% }
                                                            %>
                                                            value="W">Week(s)
                                                    </option>
                                                    <option
                                                            <%
                                                                if (f.getDurationUnit().equals("M")) { %>
                                                            selected="selected"
                                                            <% }
                                                            %>
                                                            value="M">Month(s)
                                                    </option>
                                                </select></td>
                                            <td></td>

                                            <td nowrap><b>Quantity:</b> <input type=text
                                                                               name="fldQuantity<%= i%>" class=tblRow
                                                                               size=5
                                                                               value="<%= f.getQuantity() %>"/></td>
                                            <td></td>
                                            <td><b>Repeats:</b><input type=text name="fldRepeat<%= i%>"
                                                                      class=tblRow size=3 value="<%= f.getRepeat() %>"/>
                                            </td>

                                            <td><b>No Subs:</b><input type=checkbox
                                                                      name="fldNosubs<%= i%>" <% if (f.getNosubs() == true) { %>
                                                                      checked
                                                    <%} %> class=tblRow size=1 value="on"/></td>
                                            <td><b>PRN:</b><input type=checkbox name="fldPrn<%= i%>"
                                                    <% if (f.getPrn() == true) { %> checked <%} %> class=tblRow size=1
                                                                  value="on"/></td>
                                        </tr>
                                        <tr <%= style %>>
                                            <td colspan=7>
                                                <table>
                                                    <tr>
                                                        <td><b>Special Instructions:</b><br/>
                                                            Custom Instructions:&nbsp;<input type="checkbox"
                                                                                             name="customInstr<%=i%>" <% if(f.getCustomInstr()) { %>
                                                                                             checked
                                                                    <%}%>></td>
                                                        <td width="100%"><textarea name="fldSpecial<%= i%>"
                                                                                   style="width: 100%" rows=5>
								<%
                                    String s = f.getSpecial();
                                    if (s != null) {
                                        if (!s.equals("null")) {
                                %><%= s.trim()%>
								<%
                                        }
                                    }
                                %>
								</textarea></td>
                                                    </tr>
                                                </table>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td colspan=7 valign=center>
                                                <hr width=100%>
                                            </td>
                                        </tr>
                                        <tr>
                                            <td colspan="7"></td>
                                        </tr>
                                        <tr>
                                            <td colspan="7"></td>
                                        </tr>

                                        <% } //for i %>

                                    </table>
                                </form>
                        </td>
                        </div>
                    </tr>

                    <tr>
                        <td>
                            <div class=DivContentPadding><input type=button
                                                                value="Back to Search For Drug"
                                                                class="ControlPushButton"
                                                                onClick="javascript:window.location.href='<%= request.getContextPath() %>/oscarRx/SearchDrug.jsp';"/>
                            </div>
                        </td>
                    </tr>

                    <!----End new rows here-->

                    <tr height="100%">
                        <td></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td height="0%"
                style="border-bottom: 2px solid #A9A9A9; border-top: 2px solid #A9A9A9;"></td>
            <td height="0%"
                style="border-bottom: 2px solid #A9A9A9; border-top: 2px solid #A9A9A9;"></td>
        </tr>
        <tr>
            <td width="100%" height="0%" colspan="2">&nbsp;</td>
        </tr>
        <tr>
            <td width="100%" height="0%" style="padding: 5" bgcolor="#DCDCDC"
                colspan="2"></td>
        </tr>
    </table>
    </body>
</html>
