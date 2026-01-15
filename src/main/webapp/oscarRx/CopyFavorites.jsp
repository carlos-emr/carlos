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
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ page import="java.util.*" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.dao.FavoritesDao" %>
<%@ page import="ca.openosp.openo.commn.model.Favorites" %>
<%@ page import="ca.openosp.openo.commn.dao.FavoritesPrivilegeDao" %>
<%@ page import="ca.openosp.openo.commn.model.FavoritesPrivilege" %>
<%@ page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@ page import="ca.openosp.openo.commn.model.Provider" %>
<%@ page import="ca.openosp.openo.prescript.pageUtil.RxSessionBean" %>
<%@ page import="ca.openosp.openo.prescript.data.RxCodesData" %>
<%
    FavoritesDao favoritesDao = SpringUtils.getBean(FavoritesDao.class);
    FavoritesPrivilegeDao favoritesPrivilegeDao = SpringUtils.getBean(FavoritesPrivilegeDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    RxSessionBean bean = null;
%>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath()%>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.title.CopyFavorites"/></title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

        <c:if test="${empty RxSessionBean}">
            <% response.sendRedirect("error.html"); %>
        </c:if>
        <c:if test="${not empty RxSessionBean}">
            <%
                // Directly access the RxSessionBean from the session
                bean = (RxSessionBean) session.getAttribute("RxSessionBean");
                if (bean != null && !bean.isValid()) {
                    response.sendRedirect("error.html");
                    return; // Ensure no further JSP processing
                }
            %>
        </c:if>
        <link rel="stylesheet" type="text/css" href="oscarRx/styles.css">
    </head>


    <%

        RxCodesData.FrequencyCode[] freq = new RxCodesData().getFrequencyCodes();

        int i, j;

        String providerNo = bean.getProviderNo();
        boolean share = false;
        FavoritesPrivilege fp = favoritesPrivilegeDao.findByProviderNo(providerNo);
        if (fp != null) {
            share = fp.isOpenToPublic();
        }

        List<String> allProviders = favoritesPrivilegeDao.getProviders();
        String copyProviderNo = "";
        if (request.getAttribute("copyProviderNo") != null)
            copyProviderNo = request.getAttribute("copyProviderNo").toString();
    %>


    <script language=javascript>

        function update() {
            document.getElementsByName("dispatch")[0].value = 'update';
        }

        function copy() {
            document.getElementsByName("dispatch")[0].value = 'copy';
        }
    </script>


    <body topmargin="0" leftmargin="0" vlink="#0000FF">
    <form action="<%= request.getContextPath() %>/oscarRx/copyFavorite.do">
        <input type="hidden" name="dispatch" value="refresh"/>
        <input type="hidden" name="userProviderNo" value=<%=providerNo%>/>
        <input type="hidden" name="copyProviderNo" value=""/>
        <table border="0" cellpadding="0" cellspacing="0"
               style="border-collapse: collapse" bordercolor="#111111" width="100%"
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
                                <div class="DivCCBreadCrumbs"><a href="oscarRx/CopyFavorites.jsp"> Copy Favorites</a> > <b>Select
                                    Provider Copy From</b></div>
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

                        <!----Start new rows here-->
                        <tr>
                            <td>
                                <div class="DivContentPadding">
                                    <div class="DivContentTitle">Choose provider who share the favorites</div>
                                </div>

                            </td>
                        </tr>


                        <!----End new rows here-->


                        <tr>
                            <td>
                                <div class="DivContentPadding">
                                    <table cellspacing=0 cellpadding=2>
                                        <tr>
                                            <td>
                                                <select name="ddl_provider" onchange="form.submit();">
                                                    <option value=""> Select Provider</option>
                                                    <%
                                                        for (int p = 0; p < allProviders.size(); p++) {
                                                            if (((String) allProviders.get(p)).equalsIgnoreCase(providerNo)) {
                                                                continue;
                                                            }
                                                    %>

                                                    <option
                                                            value=<%=((String) allProviders.get(p))%>  <%=((String) allProviders.get(p)).equalsIgnoreCase(copyProviderNo) ? "SELECTED" : ""%>><%=(providerDao.getProvider((String) allProviders.get(p)).getFormattedName())%>
                                                    </option>
                                                    <% }%>

                                                </select>


                                                <input type="button" onclick="copy();form.submit();"
                                                       value="Copy to my Favorites" name="b_copy"/>

                                            </td>
                                        </tr>
                                        <%
                                            String style;
                                            int count = (favoritesDao.findByProviderNo((String) request.getAttribute("copyProviderNo"))).size();
                                            for (i = 0; i < count; i++) {
                                                Favorites fav = favoritesDao.findByProviderNo(copyProviderNo).get(i);
                                                boolean isCustom = fav.getGcnSeqNo() == 0;
                                                style = "style='background-color:#F5F5F5'";
                                        %>
                                        <tr>
                                            <td>
                                                <input type="hidden" name="countFavorites" value="<%=count%>"/>
                                                <input type="checkbox" name="selected<%=i%>" value="ON"/> Check off this
                                                favorite to copy
                                            </td>
                                        </tr>
                                        <tr class=tblRow <%= style%> name="record<%= i%>Line1">
                                            <td colspan=2><b>Favorite Name:</b><input type=hidden
                                                                                      name="fldFavoriteId<%= i%>"
                                                                                      value="<%= fav.getId()%>"/>
                                                <input type=text size="50" name="fldFavoriteName<%= i%>"
                                                       class=tblRow size=80 value="<%= fav.getFavoriteName()%>"/>&nbsp;&nbsp;&nbsp;
                                            </td>
                                        </tr>
                                        <% if (!isCustom) {%>
                                        <tr class=tblRow <%= style%> name="record<%= i%>Line2">
                                            <td><b>Brand Name:</b><%= fav.getBn()%>
                                            </td>
                                            <td colspan=5><b>Generic Name:</b><%= fav.getGn()%>
                                            </td>
                                            <td colspan=1>&nbsp; <input type="hidden"
                                                                        name="fldCustomName<%= i%>" value=""/></td>
                                        </tr>
                                        <% } else {%>
                                        <tr class=tblRow <%= style%> name="record<%= i%>Line2">
                                            <td colspan=7><b>Custom Drug Name:</b> <input type=text
                                                                                          size="50"
                                                                                          name="fldCustomName<%= i%>"
                                                                                          class=tblRow size=80
                                                                                          value="<%= fav.getCustomName()%>"/>
                                            </td>
                                        </tr>
                                        <% }%>
                                        <tr class=tblRow <%= style%> name="record<%= i%>Line3">
                                            <td nowrap><b>Take:</b> <input type=text
                                                                           name="fldTakeMin<%= i%>" class=tblRow size=3
                                                                           value="<%= fav.getTakeMin()%>"/>
                                                <span>to</span> <input
                                                        type=text name="fldTakeMax<%= i%>" class=tblRow size=3
                                                        value="<%= fav.getTakeMax()%>"/> <select
                                                        name="fldFrequencyCode<%= i%>" class=tblRow>
                                                    <%
                                                        for (j = 0; j < freq.length; j++) {
                                                    %>
                                                    <option
                                                            value="<%= freq[j].getFreqCode()%>"
                                                            <%
                                                                if (freq[j].getFreqCode().equals(fav.getFreqCode())) {
                                                            %>
                                                            selected="selected"
                                                            <% }
                                                            %>><%=freq[j].getFreqCode()%>
                                                    </option>
                                                    <%
                                                        }
                                                    %>
                                                </select> <b>For:</b> <input type=text name="fldDuration<%= i%>"
                                                                             class=tblRow size=3
                                                                             value="<%= fav.getDuration()%>"/> <select
                                                        name="fldDurationUnit<%= i%>" class=tblRow>
                                                    <option
                                                            <%
                                                                if (fav.getDurationUnit().equals("D")) {%>
                                                            selected="selected"
                                                            <% }
                                                            %>
                                                            value="D">Day(s)
                                                    </option>
                                                    <option
                                                            <%
                                                                if (fav.getDurationUnit().equals("W")) {%>
                                                            selected="selected"
                                                            <% }
                                                            %>
                                                            value="W">Week(s)
                                                    </option>
                                                    <option
                                                            <%
                                                                if (fav.getDurationUnit().equals("M")) {%>
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
                                                                               value="<%= fav.getQuantity()%>"/></td>
                                            <td></td>
                                            <td><b>Repeats:</b><input type=text name="fldRepeat<%= i%>"
                                                                      class=tblRow size=3
                                                                      value="<%= fav.getRepeat()%>"/></td>

                                            <td><b>No Subs:</b><input type=checkbox
                                                                      name="fldNosubs<%= i%>" <% if (fav.isNoSubs() == true) {%>
                                                                      checked
                                                    <%}%> class=tblRow size=1 value="on"/></td>
                                            <td><b>PRN:</b><input type=checkbox name="fldPrn<%= i%>"
                                                    <% if (fav.isPrn() == true) {%> checked <%}%> class=tblRow size=1
                                                                  value="on"/></td>
                                        </tr>
                                        <tr <%= style%>>
                                            <td colspan=7>
                                                <table>
                                                    <tr>
                                                        <td><b>Special Instructions:</b><br/>
                                                            Custom Instructions:&nbsp;<input type="checkbox"
                                                                                             name="customInstr<%=i%>" <% if (fav.isCustomInstructions()) {%>
                                                                                             checked
                                                                    <%}%>></td>
                                                        <td width="100%"><textarea name="fldSpecial<%= i%>"
                                                                                   style="width: 100%" rows=5>
                                <%
                                    String s = fav.getSpecial();
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
                            </td>
                            </div>
                        </tr>

                        <tr height="100%">
                            <td></td>
                        </tr>
                    </table>
                </td>
            </tr>

            <tr>
                <td></td>


                <td width="100%" style="border-left: 2px solid #A9A9A9;" height="100%"
                    valign="top">
                    <table style="border-collapse: collapse" bordercolor="#111111"
                           width="100%" height="100%">
                        <tr>
                            <td width="0%" valign="top">
                                <div class="DivCCBreadCrumbs">
                                    <div class="DivCCBreadCrumbs"><a href="SearchDrug3.jsp"> <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.title"/></a> > <b>
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="SearchDrug.title.CopyFavorites"/> > <b>Setting</b></div>
                            </td>
                        </tr>


                        <!----Start new rows here-->
                        <tr>
                            <td>
                                <div class="DivContentPadding">
                                    <div class="DivContentTitle">Your Favorite Share Setting</div>
                                </div>
                            </td>
                        </tr>

                        <tr>
                            <td>
                                Share My Favorites<input type="radio" name="rb_share"
                                                         value="1" <%=share ? "checked='checked'" : ""%> />
                                Won't Share<input type="radio" name="rb_share"
                                                  value="0" <%=share ? "" : "checked='checked'"%>/>
                                <input type="button" onclick="update();form.submit();" value="Update" name="Update"/>

                            </td>
                        </tr>

                        <tr>
                            <td>
                                <div class=DivContentPadding><input type=button
                                                                    value="Back to Search For Drug"
                                                                    class="ControlPushButton"
                                                                    onClick="javascript:window.location.href='SearchDrug.jsp';"/>
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
    </form>
    </body>
</html>
