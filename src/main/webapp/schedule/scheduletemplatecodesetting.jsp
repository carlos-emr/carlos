<%--

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
<!DOCTYPE html>
<%
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
%>
<%@ page
        import="java.util.*, java.sql.*, ca.openosp.*, java.text.*, java.lang.*"
        errorPage="/errorpage.jsp" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<jsp:useBean id="dataBean" class="java.util.Properties" scope="page"/>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.ScheduleTemplateCode" %>
<%@ page import="ca.openosp.openo.commn.dao.ScheduleTemplateCodeDao" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    ScheduleTemplateCodeDao scheduleTemplateCodeDao = SpringUtils.getBean(ScheduleTemplateCodeDao.class);
%>
<%
    int rowsAffected = 0;
    if (request.getParameter("dboperation") != null) {
        if (request.getParameter("dboperation").compareTo(" Save ") == 0) {
            ScheduleTemplateCode code = scheduleTemplateCodeDao.getByCode(request.getParameter("code").toCharArray()[0]);
            if (code != null) {
                scheduleTemplateCodeDao.remove(code.getId());
            }

            code = new ScheduleTemplateCode();
            code.setCode(request.getParameter("code").toCharArray()[0]);
            code.setDescription(request.getParameter("description"));
            code.setDuration(request.getParameter("duration"));
            code.setColor(request.getParameter("color"));
            code.setConfirm(request.getParameter("confirm"));
            code.setBookinglimit(Integer.parseInt(request.getParameter("bookinglimit")));
            scheduleTemplateCodeDao.persist(code);

        }
        if (request.getParameter("dboperation").equals("Delete")) {
            ScheduleTemplateCode code = scheduleTemplateCodeDao.getByCode(request.getParameter("code").toCharArray()[0]);
            if (code != null) {
                scheduleTemplateCodeDao.remove(code.getId());
            }
        }
    }
%>
<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.title"/></title>

        <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
        <script type="text/javascript" src="${pageContext.request.contextPath}/share/javascript/picker.js"></script>
        <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet" type="text/css">
        <!-- Bootstrap 2.3.1 -->

        <script language="JavaScript">
            <!--

            function validateNum() {
                var node = document.getElementById("bookinglimit");

                if (isNaN(node.value)) {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.msgCheckInput"/>");
                    node.focus();
                    return false;
                }

                return true;
            }

            function setfocus() {
                this.focus();
                document.addtemplatecode.code.focus();
                document.addtemplatecode.code.select();
            }

            function upCaseCtrl(ctrl) {
                ctrl.value = ctrl.value.toUpperCase();
            }

            function checkInput() {
                if (document.schedule.holiday_name.value == "") {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.msgCheckInput"/>");
                    return false;
                } else {
                    return true;
                }
            }

            //-->
        </script>

    </head>
    <body onLoad="setfocus()">

    <h4><fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.msgApptTemplateCode"/></h4>
    <div class="alert">
        <fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.msgCode"/><br>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.msgDescription"/><br>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.msgDuration"/><br>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.msgColor"/><br>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.msgBookingLimit"/><br>
    </div>
    <div style="text-align: center; background-color: #CCFFCC;">
        <form name="deletetemplatecode" method="post" action="scheduletemplatecodesetting.jsp">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.formTemplateCode"/>:
            <select name="code">
                <%
                    List<ScheduleTemplateCode> stcs = scheduleTemplateCodeDao.findAll();
                    Collections.sort(stcs, ScheduleTemplateCode.CodeComparator);

                    for (ScheduleTemplateCode stc : stcs) {
                %>
                <option value="<%=stc.getCode()%>"><%=stc.getCode() + " |" + Encode.forHtmlContent(stc.getDescription())%>
                </option>
                <%
                    }
                %>
            </select>
            <input type="hidden" name="dboperation" value=" Edit ">
            <input type="submit" class="btn" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.btnEdit"/>'>
        </form>
    </div>

    <div class="well">
        <form name="addtemplatecode" method="post" action="scheduletemplatecodesetting.jsp" class="form-horizontal">
            <%
                boolean bEdit = request.getParameter("dboperation") != null && request.getParameter("dboperation").equals(" Edit ");
                if (bEdit) {
                    ScheduleTemplateCode stc = scheduleTemplateCodeDao.findByCode(request.getParameter("code"));
                    if (stc != null) {

                        dataBean.setProperty("code", String.valueOf(stc.getCode()));
                        dataBean.setProperty("description", stc.getDescription());
                        dataBean.setProperty("duration", stc.getDuration() == null ? "" : stc.getDuration());
                        dataBean.setProperty("color", stc.getColor() == null ? "" : stc.getColor());
                        dataBean.setProperty("confirm", stc.getConfirm() == null ? "No" : stc.getConfirm());
                        dataBean.setProperty("bookinglimit", String.valueOf(stc.getBookinglimit()));
                    }
                }
            %>
            <div class="control-group">
                <label class="control-label" for="code"><fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.formCode"/>:</label>
                <div class="controls">
                    <input type="text" name="code" id="code" maxlength="1"
                            <%=bEdit?("value='"+dataBean.getProperty("code")+"'"):"value=''"%>>
                </div>
            </div>
            <div class="control-group">
                <label class="control-label" for="description"><fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.formDescription"/>:</label>
                <div class="controls">
                    <input type="text" name="description" id="description" maxlength="40"
                            <%=bEdit?("value='"+Encode.forHtmlContent(dataBean.getProperty("description"))+"'"):"value=''"%>>
                </div>
            </div>
            <div class="control-group">
                <label class="control-label" for="duration"><fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.formDuration"/>:</label>
                <div class="controls">
                    <input type="text" name="duration" id="duration" maxlength="3"
                           placeholder="<fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.msgDuration"/>"
                            <%=bEdit?("value='"+dataBean.getProperty("duration")+"'"):"value=''"%>>
                </div>
            </div>
            <div class="control-group">
                <label class="control-label" for="color"><fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.formColor"/>:</label>
                <div class="controls">
                    <div class="input-append">
                        <input type="text" name="color" id="color" maxlength="10"
                               style="width: 178px; background-color:<%=bEdit?(dataBean.getProperty("color")):"white"%>;"
                               placeholder="<fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.msgColorExample"/>"
                                <%=bEdit?("value='"+dataBean.getProperty("color")+"'"):"value=''"%>>
                        <span class="add-on"><a
                                href="javascript:TCP.popup(document.forms['addtemplatecode'].elements['color']);"><img
                                width="15" height="13" border="0"
                                src="${pageContext.request.contextPath}/images/sel.gif"
                                onclick="getElementById('color').style.backgroundColor='white'"></a></span>
                    </div>
                </div>
            </div>
            <div class="control-group">
                <label class="control-label" for="bookingLimit"><fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.formBookingLimit"/>:</label>
                <div class="controls">
                    <input type="text" id="bookinglimit" name="bookinglimit"
                            <%=bEdit?("value='"+dataBean.getProperty("bookinglimit")+"'"):"value='1'"%>>
                </div>
            </div>
            <div class="control-group">
                <label class="control-label" for="limitType">Limit Type:</label>
                <div class="controls">
                    <input type="radio" name="confirm" value="No"
                            <%=(bEdit? (dataBean.getProperty("confirm").startsWith("N")? "checked" : "") : "checked")%>>Off
                    <input type="radio" name="confirm" value="Yes"
                            <%=((bEdit && dataBean.getProperty("confirm").equals("Yes"))? "checked" : "")%>>Warning
                    <!-- <input type="radio" name="confirm" value="Str"
					<%=(bEdit? (dataBean.getProperty("confirm").startsWith("Str")? "checked" : "") : "checked")%>>Strict
				not implimented --> <br>
                    <input type="radio" name="confirm" value="Day"
                            <%=(bEdit? (dataBean.getProperty("confirm").equals("Day")? "checked" : "") : "checked")%>>Same
                    Day
                    <input type="radio" name="confirm" value="Wk"
                            <%=(bEdit? (dataBean.getProperty("confirm").equals("Wk")? "checked" : "") : "checked")%>>Same
                    Week
                    <input type="radio" name="confirm" value="Onc"
                            <%=(bEdit? (dataBean.getProperty("confirm").equals("Onc")? "checked" : "") : "checked")%>>On-Call
                    Urgent

                </div>
                <div style="text-align:right">
                    <br>
                    <input type="button" class="btn"
                           onclick="document.forms['addtemplatecode'].dboperation.value='Delete'; document.forms['addtemplatecode'].submit();"
                           value='<fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.btnDelete"/>'>
                    <input type="button" class="btn btn-primary"
                           onclick="if( validateNum() ) { document.forms['addtemplatecode'].dboperation.value=' Save '; document.forms['addtemplatecode'].submit();}"
                           value='<fmt:setBundle basename="oscarResources"/><fmt:message key="schedule.scheduletemplatecodesetting.btnSave"/>'>
                    <input type="button" name="Button" class="btn btn-link"
                           value='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnExit"/>'
                           onClick="window.close()">
                    <input type="hidden" name="dboperation" value=""/>
                </div>
            </div>
        </form>
    </div>
    </body>
</html>