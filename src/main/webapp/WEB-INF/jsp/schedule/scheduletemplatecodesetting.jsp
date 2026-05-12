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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<!DOCTYPE html>
<%
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logoutPage");
%>
<%@ page
        import="java.util.*, java.sql.*, io.github.carlos_emr.*, java.text.*, java.lang.*"
        errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>


<jsp:useBean id="dataBean" class="java.util.Properties" scope="page"/>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateCodeDao" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%
    ScheduleTemplateCodeDao scheduleTemplateCodeDao = SpringUtils.getBean(ScheduleTemplateCodeDao.class);
    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());
    String opEdit = bundle.getString("schedule.scheduletemplatecodesetting.btnEdit");
    String opSave = bundle.getString("schedule.scheduletemplatecodesetting.btnSave");
    String opDelete = bundle.getString("schedule.scheduletemplatecodesetting.btnDelete");
%>
<%
    int rowsAffected = 0;
    if (request.getParameter("dboperation") != null) {
        if (request.getParameter("dboperation").equals(opSave)) {
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
        if (request.getParameter("dboperation").equals(opDelete)) {
            ScheduleTemplateCode code = scheduleTemplateCodeDao.getByCode(request.getParameter("code").toCharArray()[0]);
            if (code != null) {
                scheduleTemplateCodeDao.remove(code.getId());
            }
        }
    }
%>
<html>
    <head>
        <title><fmt:message key="schedule.scheduletemplatecodesetting.title"/></title>

        <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
        <script type="text/javascript" src="${pageContext.request.contextPath}/share/javascript/picker.js"></script>
        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <!-- Bootstrap -->

        <script language="JavaScript">
            <!--

            function validateNum() {
                var node = document.getElementById("bookinglimit");

                if (isNaN(node.value)) {
                    alert("<fmt:message key="schedule.scheduletemplatecodesetting.msgCheckInput"/>");
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
                    alert("<fmt:message key="schedule.scheduletemplatecodesetting.msgCheckInput"/>");
                    return false;
                } else {
                    return true;
                }
            }

            //-->
        </script>

    </head>
    <body onLoad="setfocus()">

    <h4><fmt:message key="schedule.scheduletemplatecodesetting.msgApptTemplateCode"/></h4>
    <div class="alert">
        <fmt:message key="schedule.scheduletemplatecodesetting.msgCode"/><br>
        <fmt:message key="schedule.scheduletemplatecodesetting.msgDescription"/><br>
        <fmt:message key="schedule.scheduletemplatecodesetting.msgDuration"/><br>
        <fmt:message key="schedule.scheduletemplatecodesetting.msgColor"/><br>
        <fmt:message key="schedule.scheduletemplatecodesetting.msgBookingLimit"/><br>
    </div>
    <div style="text-align: center; background-color: #CCFFCC;">
        <form name="deletetemplatecode" method="post" action="${pageContext.request.contextPath}/schedule/TemplateCodeSetting">
            <fmt:message key="schedule.scheduletemplatecodesetting.formTemplateCode"/>:
            <select name="code">
                <%
                    List<ScheduleTemplateCode> stcs = new java.util.ArrayList<>(scheduleTemplateCodeDao.findAll());
                    Collections.sort(stcs, ScheduleTemplateCode.CodeComparator);

                    for (ScheduleTemplateCode stc : stcs) {
                %>
                <option value="<%=stc.getCode()%>"><%=stc.getCode() + " |" + SafeEncode.forHtmlContent(stc.getDescription())%>
                </option>
                <%
                    }
                %>
            </select>
            <input type="hidden" name="dboperation" value="<%= SafeEncode.forHtmlAttribute(opEdit) %>">
            <input type="submit" class="btn btn-secondary" value='<fmt:message key="schedule.scheduletemplatecodesetting.btnEdit"/>'>
        </form>
    </div>

    <div class="card card-body bg-body-tertiary">
        <form name="addtemplatecode" method="post" action="${pageContext.request.contextPath}/schedule/TemplateCodeSetting" class="">
            <%
                boolean bEdit = request.getParameter("dboperation") != null && request.getParameter("dboperation").equals(opEdit);
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
            <div class="mb-3">
                <label class="form-label" for="code"><fmt:message key="schedule.scheduletemplatecodesetting.formCode"/>:</label>
                <div>
                    <input type="text" name="code" id="code" maxlength="1"
                            <%=bEdit?("value='"+dataBean.getProperty("code")+"'"):"value=''"%>>
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="description"><fmt:message key="schedule.scheduletemplatecodesetting.formDescription"/>:</label>
                <div>
                    <input type="text" name="description" id="description" maxlength="40"
                            <%=bEdit?("value='"+SafeEncode.forHtmlContent(dataBean.getProperty("description"))+"'"):"value=''"%>>
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="duration"><fmt:message key="schedule.scheduletemplatecodesetting.formDuration"/>:</label>
                <div>
                    <input type="text" name="duration" id="duration" maxlength="3"
                           placeholder="<fmt:message key="schedule.scheduletemplatecodesetting.msgDuration"/>"
                            <%=bEdit?("value='"+dataBean.getProperty("duration")+"'"):"value=''"%>>
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="color"><fmt:message key="schedule.scheduletemplatecodesetting.formColor"/>:</label>
                <div>
                    <div class="input-group">
                        <input type="text" name="color" id="color" maxlength="10"
                               style="width: 178px; background-color:<%=bEdit?(dataBean.getProperty("color")):"white"%>;"
                               placeholder="<fmt:message key="schedule.scheduletemplatecodesetting.msgColorExample"/>"
                                <%=bEdit?("value='"+dataBean.getProperty("color")+"'"):"value=''"%>>
                        <span class="input-group-text"><a
                                href="javascript:TCP.popup(document.forms['addtemplatecode'].elements['color']);"><img
                                width="15" height="13" border="0"
                                src="${pageContext.request.contextPath}/images/sel.gif"
                                onclick="getElementById('color').style.backgroundColor='white'"></a></span>
                    </div>
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="bookingLimit"><fmt:message key="schedule.scheduletemplatecodesetting.formBookingLimit"/>:</label>
                <div>
                    <input type="text" id="bookinglimit" name="bookinglimit"
                            <%=bEdit?("value='"+dataBean.getProperty("bookinglimit")+"'"):"value='1'"%>>
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="limitType"><fmt:message key="schedule.scheduletemplatecodesetting.formLimitType"/>:</label>
                <div>
                    <input type="radio" name="confirm" value="No"
                            <%=(bEdit? (dataBean.getProperty("confirm").startsWith("N")? "checked" : "") : "checked")%>><fmt:message key="schedule.scheduletemplatecodesetting.formLimitOff"/>
                    <input type="radio" name="confirm" value="Yes"
                            <%=((bEdit && dataBean.getProperty("confirm").equals("Yes"))? "checked" : "")%>><fmt:message key="schedule.scheduletemplatecodesetting.formLimitWarning"/>
                    <!-- <input type="radio" name="confirm" value="Str"
					<%=(bEdit? (dataBean.getProperty("confirm").startsWith("Str")? "checked" : "") : "checked")%>>Strict
				not implimented --> <br>
                    <input type="radio" name="confirm" value="Day"
                            <%=(bEdit? (dataBean.getProperty("confirm").equals("Day")? "checked" : "") : "checked")%>><fmt:message key="schedule.scheduletemplatecodesetting.formLimitSameDay"/>
                    <input type="radio" name="confirm" value="Wk"
                            <%=(bEdit? (dataBean.getProperty("confirm").equals("Wk")? "checked" : "") : "checked")%>><fmt:message key="schedule.scheduletemplatecodesetting.formLimitSameWeek"/>
                    <input type="radio" name="confirm" value="Onc"
                            <%=(bEdit? (dataBean.getProperty("confirm").equals("Onc")? "checked" : "") : "checked")%>><fmt:message key="schedule.scheduletemplatecodesetting.formLimitOnCallUrgent"/>

                </div>
                <div style="text-align:right">
                    <br>
                    <input type="button" class="btn btn-secondary"
                           onclick="document.forms['addtemplatecode'].dboperation.value='<%= SafeEncode.forJavaScript(opDelete) %>'; document.forms['addtemplatecode'].submit();"
                           value='<fmt:message key="schedule.scheduletemplatecodesetting.btnDelete"/>'>
                    <input type="button" class="btn btn-primary"
                           onclick="if( validateNum() ) { document.forms['addtemplatecode'].dboperation.value='<%= SafeEncode.forJavaScript(opSave) %>'; document.forms['addtemplatecode'].submit();}"
                           value='<fmt:message key="schedule.scheduletemplatecodesetting.btnSave"/>'>
                    <input type="button" name="Button" class="btn btn-link"
                           value='<fmt:message key="global.btnExit"/>'
                           onClick="window.close()">
                    <input type="hidden" name="dboperation" value=""/>
                </div>
            </div>
        </form>
    </div>
    </body>
</html>
