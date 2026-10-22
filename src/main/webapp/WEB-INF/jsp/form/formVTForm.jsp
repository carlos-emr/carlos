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
<%@ taglib uri="http://displaytag.sf.net" prefix="display" %>

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

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>

<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext" %>
<%
    // This view is populated by SetupForm for one explicitly authorized patient.
    try {
        String formPatientNo = EncounterPatientContext.requireDemographicNo(request);
        if (!formPatientNo.equals(request.getAttribute("formDemographicNo"))
                || request.getAttribute("formPatient") == null) {
            response.sendError(400, "Invalid form patient context");
            return;
        }
    } catch (IllegalArgumentException invalidPatient) {
        response.sendError(400, "Invalid form patient context");
        return;
    }
%>
<html>

    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="form.vt.title"/></title>
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>

        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

        <style>
            .vt-screen-reader-only {
                position: absolute;
                width: 1px;
                height: 1px;
                padding: 0;
                margin: -1px;
                overflow: hidden;
                clip: rect(0, 0, 0, 0);
                white-space: nowrap;
                border: 0;
            }
            th.vt-column-heading {
                font-size: inherit;
                font-weight: normal;
                text-align: left;
            }
        </style>
    </head>

    <script type="text/javascript">

        var WHRBId;
        var smkSDate, smkHDate, smkCDate;
        var smkSCmt, smkHCmt, smkCCmt;
        var DpScId, StScId, LcCtId;
        var MedGId, MedNId, MedRId, MedAId;
        var FTExId, ftNeId, ftIsId, ftInId, ftUlId, ftOtId, ftReId;
        var FTExDate, ftNeDate, ftIsDate, ftInDate, ftUlDate, ftOtDate, ftReDate;
        var FTExCmt, ftNeCmt, ftIsCmt, ftInCmt, ftUlCmt, ftOtCmt, ftReCmt;
        var eyeHypId, eyeDiaId, eyeOthId, eyeRefId;
        var eyeHypCmt, eyeDiaCmt, eyeOthCmt, eyeRefCmt;
        var eyeHypDate, eyeDiaDate, eyeOthDate, eyeRefDate;
        var MIValue, AngValue, ACSValue, RVTNValue, CADValue, DMValue, PVDValue;
        var HTNValue, HchlValue, CVDValue;
        var HbA1Value, HbA1Date, HbA1Cmt;
        var DMCValue, DMCDate, DMCCmt;


        function onPrint() {
//        document.forms[0].submit.value="print";
//        var ret = checkAllDates();
//        if(ret==true)
//        {
//            ret = confirm("Do you wish to save this form and view the print preview?");
//        }
//        return ret;
            window.print();
        }

        function onSave() {
            document.forms[0].submit.value = "save";
            storeSMKSupportData();
            storeFTExamSupportData();
            storeEyeExamSupportData();
        }

        function onSaveExit() {
            document.forms[0].submit.value = "exit";
            storeSMKSupportData();
            storeFTExamSupportData();
            storeEyeExamSupportData();
            var ret = confirm("<fmt:message key='global.msgSaveExit'/>");

            return ret;
        }

        var pageWindow = null;

        function popupDecisionSupport() {
            // JavaScript-string context: the URL is encoded for the script, never as HTML. An unset
            // attribute encodes to "", which must not open a popup (it used to print "null").
            var varpage = "${carlos:forJavaScript(requestScope.decisionSupportURL)}";
            var vheight = 760;
            var vwidth = 400;
            var posX = 400;
            var posY = 0;
            if (varpage !== "" && varpage !== "null") {
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=680";
                var popup = window.open(varpage, "DecisionSupport", windowprops);
                pageWindow = popup;
                popup.focus();
            }

        }

        function closeDSWindow() {
            if (pageWindow != null) {
                if (!pageWindow.closed) {
                    pageWindow.close();
                }
            }

        }


        function showHideItem(id) {
            if (document.getElementById(id).style.display == 'none')
                document.getElementById(id).style.display = 'block';
            else
                document.getElementById(id).style.display = 'none';
        }

        function showItem(id) {
            document.getElementById(id).style.display = 'block';
        }

        function hideItem(id) {
            document.getElementById(id).style.display = 'none';
        }

        function showHideDetail() {
            showHideItem('subjective');
            showHideItem('showDetailTitle');
            showHideItem('hideDetailTitle');
            showHideItem('bigS');
            showHideItem('historyLabel');
            showHideItem('detailHistory');
            showHideItem('psychHead1');
            showHideItem('psychHead2');
            showHideItem('psychHead3');
            showHideItem('psychHead4');

            showHideItem('medHead1');
            showHideItem('medHead2');
            showHideItem('medHead3');
            showHideItem('medHead4');

            showHideItem('bigO');
            showHideItem('objective');
            showHideItem('vitalsLabel');
            showHideItem('vitalHead1');
            showHideItem('vitalHead2');
            showHideItem('vitalHead3');
            showHideItem('detailVital');
            showHideItem('examLabel');
            showHideItem('labsLabel');

            showHideItem('detailFT');
            showHideItem('detailEye');

            showHideItem('bigA');
            showHideItem('assessment');


            showHideItem('bigP');
            showHideItem('plan');
            showHideItem('diagnosis');
            //

            showHideItem('detailDiag');


            showHideItem('CounsellingLabel');
            showHideItem('counselling');


            showHideItem('detailLab');

            showHideItem('medications');
            showHideItem('medicationAllergies');

            showHideItem('medAllergiesLabel');
            showHideItem('medLabel');


        }

        function storeSMKSupportData() {
            //getElementbyId function doesn't work on these elements
            //here is the work around

            document.forms[0].elements[smkSDate].value = document.getElementById('SmkDate').value;
            document.forms[0].elements[smkHDate].value = document.getElementById('SmkDate').value;
            document.forms[0].elements[smkCDate].value = document.getElementById('SmkDate').value;

            document.forms[0].elements[smkSCmt].value = document.getElementById('SmkComments').value;
            document.forms[0].elements[smkHCmt].value = document.getElementById('SmkComments').value;
            document.forms[0].elements[smkCCmt].value = document.getElementById('SmkComments').value;
        }


        function storeFTExamSupportData() {

            document.forms[0].elements[FTExDate].value = document.getElementById('FTDate').value;
            document.forms[0].elements[ftNeDate].value = document.getElementById('FTDate').value;
            document.forms[0].elements[ftIsDate].value = document.getElementById('FTDate').value;
            document.forms[0].elements[ftUlDate].value = document.getElementById('FTDate').value;
            document.forms[0].elements[ftInDate].value = document.getElementById('FTDate').value;
            document.forms[0].elements[ftOtDate].value = document.getElementById('FTDate').value;
            document.forms[0].elements[ftReDate].value = document.getElementById('FTDate').value;

            document.forms[0].elements[FTExCmt].value = document.forms[0].FTComments.value;
            document.forms[0].elements[ftNeCmt].value = document.forms[0].FTComments.value;
            document.forms[0].elements[ftIsCmt].value = document.forms[0].FTComments.value;
            document.forms[0].elements[ftUlCmt].value = document.forms[0].FTComments.value;
            document.forms[0].elements[ftInCmt].value = document.forms[0].FTComments.value;
            document.forms[0].elements[ftOtCmt].value = document.forms[0].FTComments.value;
            document.forms[0].elements[ftReCmt].value = document.forms[0].FTComments.value;
        }

        function controlFTExam() {

            if (document.forms[0].elements[FTExId].checked == false) {
                //enable all foot exam checkbox
                //alert("enable all foot exam checkboxes");
                document.forms[0].elements[ftNeId - 1].disabled = false;
                document.forms[0].elements[ftIsId - 1].disabled = false;
                document.forms[0].elements[ftUlId - 1].disabled = false;
                document.forms[0].elements[ftInId - 1].disabled = false;
                document.forms[0].elements[ftOtId - 1].disabled = false;
                document.forms[0].elements[ftReId - 1].disabled = false;
            } else {
                //uncheck and disable all foot exam checkbox
                //alert("uncheck all foot exam checkboxes");
                document.forms[0].elements[ftNeId - 1].checked = false;
                document.forms[0].elements[ftIsId - 1].checked = false;
                document.forms[0].elements[ftUlId - 1].checked = false;
                document.forms[0].elements[ftInId - 1].checked = false;
                document.forms[0].elements[ftOtId - 1].checked = false;
                document.forms[0].elements[ftReId - 1].checked = false;

                document.forms[0].elements[ftNeId].checked = true;
                document.forms[0].elements[ftIsId].checked = true;
                document.forms[0].elements[ftUlId].checked = true;
                document.forms[0].elements[ftInId].checked = true;
                document.forms[0].elements[ftOtId].checked = true;
                //document.forms[0].elements[ftReId].checked= true;

                //alert("disable all foot exam checkboxes");
                document.forms[0].elements[ftNeId - 1].disabled = true;
                document.forms[0].elements[ftIsId - 1].disabled = true;
                document.forms[0].elements[ftUlId - 1].disabled = true;
                document.forms[0].elements[ftInId - 1].disabled = true;
                document.forms[0].elements[ftOtId - 1].disabled = true;
                document.forms[0].elements[ftReId - 1].disabled = true;
            }
        }

        function storeEyeExamSupportData() {

            document.forms[0].elements[eyeHypDate].value = document.forms[0].iDate.value;
            document.forms[0].elements[eyeDiaDate].value = document.forms[0].iDate.value;
            document.forms[0].elements[eyeOthDate].value = document.forms[0].iDate.value;
            document.forms[0].elements[eyeRefDate].value = document.forms[0].iDate.value;

            document.forms[0].elements[eyeHypCmt].value = document.forms[0].iComments.value;
            document.forms[0].elements[eyeDiaCmt].value = document.forms[0].iComments.value;
            document.forms[0].elements[eyeOthCmt].value = document.forms[0].iComments.value;
            document.forms[0].elements[eyeRefCmt].value = document.forms[0].iComments.value;
        }


        function initialize() {
            var e = document.forms[0].elements;

            // Restore this request's values through DOM properties, never executable markup.
            document.querySelectorAll('#vt-form-values [data-field]').forEach(function (stored) {
                var name = 'value(' + stored.dataset.field + ')';
                for (var field of e) {
                    if (field.name !== name) continue;
                    if (field.type === 'checkbox') {
                        field.checked = ['yes', 'on', 'true', '1'].includes(stored.dataset.value);
                    } else if (field.type === 'radio') {
                        field.checked = field.value === stored.dataset.value;
                    } else {
                        field.value = stored.dataset.value;
                    }
                }
            });

            for (i = 0; i < e.length; i++) {
                switch (e[i].name) {
                    case 'value(WHRBValue)':
                        WHRBId = i;
                        break;
                    case 'value(SmkSDate)':
                        smkSDate = i;
                        break;
                    case 'value(SmkHDate)':
                        smkHDate = i;
                        break;
                    case 'value(SmkCDate)':
                        smkCDate = i;
                        break;

                    case 'value(SmkSComments)':
                        smkSCmt = i;
                        break;
                    case 'value(SmkHComments)':
                        smkHCmt = i;
                        break;
                    case 'value(SmkCComments)':
                        smkCCmt = i;
                        break;

                    case 'value(DpScValue)':
                        DpScId = i;
                        break;
                    case 'value(StScValue)':
                        StScId = i;
                        break;
                    case 'value(LcCtValue)':
                        LcCtId = i;
                        break;

                    case 'value(MedGValue)':
                        MedGId = i;
                        break;
                    case 'value(MedNValue)':
                        MedNId = i;
                        break;
                    case 'value(MedRValue)':
                        MedRId = i;
                        break;
                    case 'value(MedAValue)':
                        MedAId = i;
                        break;

                    case 'value(FTExValue)':
                        FTExId = i;
                        break;
                    case 'value(FTNeValue)':
                        ftNeId = i;
                        break;
                    case 'value(FTIsValue)':
                        ftIsId = i;
                        break;
                    case 'value(FTUlValue)':
                        ftUlId = i;
                        break;
                    case 'value(FTInValue)':
                        ftInId = i;
                        break;
                    case 'value(FTOtValue)':
                        ftOtId = i;
                        break;
                    case 'value(FTReValue)':
                        ftReId = i;
                        break;

                    case 'value(FTExDate)':
                        FTExDate = i;
                        break;
                    case 'value(FTNeDate)':
                        ftNeDate = i;
                        break;
                    case 'value(FTIsDate)':
                        ftIsDate = i;
                        break;
                    case 'value(FTUlDate)':
                        ftUlDate = i;
                        break;
                    case 'value(FTInDate)':
                        ftInDate = i;
                        break;
                    case 'value(FTOtDate)':
                        ftOtDate = i;
                        break;
                    case 'value(FTReDate)':
                        ftReDate = i;
                        break;

                    case 'value(FTExComments)':
                        FTExCmt = i;
                        break;
                    case 'value(FTNeComments)':
                        ftNeCmt = i;
                        break;
                    case 'value(FTIsComments)':
                        ftIsCmt = i;
                        break;
                    case 'value(FTUlComments)':
                        ftUlCmt = i;
                        break;
                    case 'value(FTInComments)':
                        ftInCmt = i;
                        break;
                    case 'value(FTOtComments)':
                        ftOtCmt = i;
                        break;
                    case 'value(FTReComments)':
                        ftReCmt = i;
                        break;

                    case 'value(iHypDate)':
                        eyeHypDate = i;
                        break;
                    case 'value(iDiaDate)':
                        eyeDiaDate = i;
                        break;
                    case 'value(iOthDate)':
                        eyeOthDate = i;
                        break;
                    case 'value(iRefDate)':
                        eyeRefDate = i;
                        break;

                    case 'value(iHypComments)':
                        eyeHypCmt = i;
                        break;
                    case 'value(iDiaComments)':
                        eyeDiaCmt = i;
                        break;
                    case 'value(iOthComments)':
                        eyeOthCmt = i;
                        break;
                    case 'value(iRefComments)':
                        eyeRefCmt = i;
                        break;

                    case 'value(iHypValue)':
                        eyeHypId = i;
                        break;
                    case 'value(iDiaValue)':
                        eyeDiaId = i;
                        break;
                    case 'value(iOthValue)':
                        eyeOthId = i;
                        break;
                    case 'value(iRefValue)':
                        eyeRefId = i;
                        break;

                    case 'value(MIValue)':
                        MIValue = i;
                        break;
                    case 'value(AngValue)':
                        AngValue = i;
                        break;
                    case 'value(ACSValue)':
                        ACSValue = i;
                        break;
                    case 'value(RVTNValue)':
                        RVTNValue = i;
                        break;
                    case 'value(CADValue)':
                        CADValue = i;
                        break;

                    case 'value(DMValue)':
                        DMValue = i;
                        break;
                    case 'value(PVDValue)':
                        PVDValue = i;
                        break;

                    case 'value(HTNValue)':
                        HTNValue = i;
                        break;
                    case 'value(HchlValue)':
                        HchlValue = i;
                        break;
                    case 'value(CVDValue)':
                        CVDValue = i;
                        break;

                    case 'value(HbA1Value)':
                        HbA1Value = i;
                        break;
                    case 'value(HbA1Date)':
                        HbA1Date = i;
                        break;
                    case 'value(HbA1Comments)':
                        HbA1Cmt = i;
                        break;

                    case 'value(DiaCValue)':
                        DMCValue = i;
                        break;
                    case 'value(DiaCDate)':
                        DMCDate = i;
                        break;
                    case 'value(DiaCComments)':
                        DMCCmt = i;
                        break;

                }
            }

            /*if(document.forms[0].elements[MIValue].value=="yes"||document.forms[0].elements[AngValue].value=="yes"||document.forms[0].elements[ACSValue].value=="yes"){
                document.forms[0].elements[CADValue].value="yes";
            }*/

            DMCheck();
            PVDCheck();
        }

        function disableFTExam(control) {
            document.forms[0].elements[FTExId - 1].disabled = control;
            document.forms[0].elements[ftNeId - 1].disabled = control;
            document.forms[0].elements[ftIsId - 1].disabled = control;
            document.forms[0].elements[ftUlId - 1].disabled = control;
            document.forms[0].elements[ftInId - 1].disabled = control;
            document.forms[0].elements[ftOtId - 1].disabled = control;
            document.forms[0].elements[ftReId - 1].disabled = control;

            document.forms[0].elements[FTExId].disabled = control;
            document.forms[0].elements[ftNeId].disabled = control;
            document.forms[0].elements[ftIsId].disabled = control;
            document.forms[0].elements[ftUlId].disabled = control;
            document.forms[0].elements[ftInId].disabled = control;
            document.forms[0].elements[ftOtId].disabled = control;
            document.forms[0].elements[ftReId].disabled = control;
        }

        function disableEyeExam(control) {
            document.forms[0].elements[eyeHypId - 1].disabled = control;
            document.forms[0].elements[eyeDiaId - 1].disabled = control;
            document.forms[0].elements[eyeOthId - 1].disabled = control;
            document.forms[0].elements[eyeRefId - 1].disabled = control;

            document.forms[0].elements[eyeHypId].disabled = control;
            document.forms[0].elements[eyeDiaId].disabled = control;
            document.forms[0].elements[eyeOthId].disabled = control;
            document.forms[0].elements[eyeRefId].disabled = control;
        }

        function DMCheck() {
            if (document.forms[0].elements[DMValue].checked == false && document.forms[0].elements[PVDValue].checked == false) {
                disableFTExam(true);
                hideItem('examinationFoot');
            } else {
                disableFTExam(false);
                showItem('examinationFoot');
            }
            if (document.forms[0].elements[DMValue].checked == false) {
                hideItem('examinationEye');
                disableEyeExam(true);
                document.forms[0].elements[HbA1Value].disabled = true;
                document.forms[0].elements[HbA1Date].disabled = true;
                document.forms[0].elements[HbA1Cmt].disabled = true;
                document.forms[0].elements[DMCValue].disabled = true;
                document.forms[0].elements[DMCDate].disabled = true;
                document.forms[0].elements[DMCCmt].disabled = true;
            } else {
                showItem('examinationEye');
                disableEyeExam(false);
                document.forms[0].elements[HbA1Value].disabled = false;
                document.forms[0].elements[HbA1Date].disabled = false;
                document.forms[0].elements[HbA1Cmt].disabled = false;
                document.forms[0].elements[DMCValue].disabled = false;
                document.forms[0].elements[DMCDate].disabled = false;
                document.forms[0].elements[DMCCmt].disabled = false;
            }
        }

        function PVDCheck() {
            DMCheck();
            /*if(document.forms[0].elements[DMValue].checked==false && document.forms[0].elements[PVDValue].checked==false){
                disableFTExam(true);
                hideItem('examinationFoot');
            }
            else{
                disableFTExam(false);
                showItem('examinationFoot');
            }*/
        }

        function setCheckboxValue(id) {
            if (document.forms[0].elements[id].checked == false) {
                document.forms[0].elements[id].value = "no";
            }
        }

        function clearAll(yRadio, nRadio) {
            document.forms[0].elements[yRadio].checked = false;
            document.forms[0].elements[nRadio].checked = false;
        }

    </script>
    <body class="BodyStyle" vlink="#0000FF"
          onload="window.focus();window.resizeTo(680,760); initialize();">
    <div id="vt-form-values" hidden>
        <c:forEach var="field" items="${requestScope.formValues}">
            <span data-field="${carlos:forHtmlAttribute(field.key)}"
                  data-value="${carlos:forHtmlAttribute(field.value)}"></span>
        </c:forEach>
    </div>
    <!--  -->

    <form action="${pageContext.request.contextPath}/form/SubmitForm" method="post">
        <span id="vt-comments-label" class="vt-screen-reader-only"><fmt:message key="encounter.oscarMeasurements.Measurements.headingComments"/></span>
        <span id="vt-new-data-label" class="vt-screen-reader-only"><fmt:message key="form.vt.newData"/></span>
        <span id="vt-ob-date-label" class="vt-screen-reader-only"><fmt:message key="form.vt.obDate"/></span>
        <link rel="stylesheet" type="text/css"
              href="<%= request.getContextPath() %>/encounter/oscarMeasurements/styles/measurementStyle.css">
        <link rel="stylesheet" type="text/css" media="print" href="form/print.css"/>
        <input type="hidden" name="value(formName)" value="VTForm"/>
        <input type="hidden" name="demographic_no" value="${carlos:forHtmlAttribute(requestScope.formDemographicNo)}"/>
        <input type="hidden" name="value(formId)" id="value(formId)" value="${carlos:forHtmlAttribute(requestScope.formValues['formId'])}"/>
        <%-- The XML/record still contains iEx, but this view has no editor for it. Preserve its
             stored value and metadata through the encoded formValues restoration without
             treating it as a visible radio pair or replacing its date/comments on save. --%>
        <input type="hidden" name="value(iExValue)"/>
        <input type="hidden" name="value(iExDate)"/>
        <input type="hidden" name="value(iExComments)"/>
        <%-- WHR likewise has stored fields but no ratio editor on this page. Retain its
             history without deriving a new result from the standalone WC/HC inputs. --%>
        <input type="hidden" name="value(WHRValue)"/>
        <input type="hidden" name="value(WHRDate)"/>
        <input type="hidden" name="value(WHRComments)"/>
        <table class="Head" class="hidePrint" width="640px" cellpadding="0"
               cellspacing="0">
            <tr>
                <td align="left"><input type="submit" value="<fmt:message key='form.vt.update'/>"
                                        onclick="javascript:onSave();"/> <input type="submit"
                                                                                value="<fmt:message key='global.saveExit'/>"
                                                                                onclick="javascript:return onSaveExit();"/>
                    <input
                            type="submit" value="<fmt:message key="global.btnExit"/>" onclick="javascript:return onExit();"/>
                    <input type="button" value="<fmt:message key='global.btnPrint'/>"
                           onclick="javascript:return onPrint();"/></td>
            </tr>
        </table>
        <table width="640px" border=0 cellpadding="0" cellspacing="0">
            <tr>
                <td class="subject">Vascular Data Entry Template</td>
            </tr>
            <tr>
                <td>
                    <table width="640px" border="0" cellspacing="0" cellpadding="0">
                        <tr>
                            <td>
                                <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                    <% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><carlos:encode value="<%= error %>"/></li>
            <% } %>
        </ul>
    </div>
<% } %>
                                    <tr>
                                        <td colspan="2">

                                            <table width="100%" border="0" cellspacing="0" cellpadding="1">
                                                <tr>
                                                    <td valign="top" width="22%">
                                                        <span id="vt-patient-header">
                                                            ${carlos:forHtml(requestScope.formPatient.lastName)}
                                                            ${carlos:forHtml(requestScope.formPatient.firstName)}
                                                            ${carlos:forHtml(requestScope.formPatient.sex)}
                                                            ${carlos:forHtml(requestScope.formPatient.age)}
                                                        </span>

                                                        <table width="100%" id="showDetailTitle">
                                                            <tr>
                                                                <th><a href="javascript: showHideDetail();">Show
                                                                    Details >></a></th>
                                                            </tr>
                                                        </table>
                                                        <table style="display: none" width="100%" id="hideDetailTitle">
                                                            <tr>
                                                                <th><a href="javascript: showHideDetail();">Hide
                                                                    Details << </a></th>
                                                            </tr>
                                                        </table>

                                                    </td>
                                                    <td width="53%">
                                                        <table width="100%">
                                                            <tr>
                                                                <td align="left" class="sixtyPercent"><input type="checkbox" name="value(DMValue)" onclick="javascript: DMCheck();"/></td>
                                                                <td align="left" class="sixtyPercent"><input type="checkbox" name="value(HTNValue)"/></td>
                                                                <td align="left" class="sixtyPercent"><input type="checkbox" name="value(HchlValue)"/></td>
                                                                <td align="left" class="sixtyPercent"><input type="checkbox" name="value(MIValue)"/></td>
                                                                <td align="left" class="sixtyPercent"><input type="checkbox" name="value(AngValue)"/></td>
                                                                <td align="left" class="sixtyPercent"><input type="checkbox" name="value(ACSValue)"/></td>
                                                                <td align="left" class="sixtyPercent"><input type="checkbox" name="value(RVTNValue)"/></td>
                                                                <td align="left" class="sixtyPercent"><input type="checkbox" name="value(CVDValue)"/></td>
                                                                <td align="left" class="sixtyPercent"><input type="checkbox" name="value(PVDValue)" onclick="javascript: PVDCheck();"/></td>
                                                            </tr>
                                                            <tr>
                                                                <td valign="top" align="center" class="sixtyPercent"
                                                                    title="DM">DM
                                                                </td>
                                                                <td valign="top" align="center" class="sixtyPercent"
                                                                    title="HTN">HTN
                                                                </td>
                                                                <td valign="top" align="center" class="sixtyPercent"
                                                                    title="Hyperlipidemia">Hyperlipidemia
                                                                </td>
                                                                <td valign="top" align="center" class="sixtyPercent"
                                                                    title="MI">MI
                                                                </td>
                                                                <td valign="top" align="center" class="sixtyPercent"
                                                                    title="Angina">Angina
                                                                </td>
                                                                <td valign="top" align="center" class="sixtyPercent"
                                                                    title="Acute Coronary Syndrome">ACS
                                                                </td>
                                                                <td valign="top" align="center" class="sixtyPercent"
                                                                    title="Revascularization">Revascularization
                                                                </td>
                                                                <td valign="top" align="center" class="sixtyPercent"
                                                                    title="Stroke/TIA">Stroke/TIA
                                                                </td>
                                                                <td valign="top" align="center" class="sixtyPercent"
                                                                    title="PVD">PVD
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td valign="top" width="25%" align="right">
                                                        <table>
                                                            <tr>
                                                                <td>FP Visit Date</td>
                                                                <td rowspan="2"><input type="text" size="8" name="value(visitCod)"/></td>
                                                            </tr>
                                                            <tr>
                                                                <td>(YYYY-MM-DD)</td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                </tr>
                                            </table>

                                        </td>
                                    </tr>

                                    <tr>
                                        <th class="soap" width="2%"><span id="bigS"
                                                                          style="display: none;">S</span></th>
                                        <th>
                                            <table id="subjective" style="display: none">
                                                <tr>
                                                    <th><textarea name="value(subjective)" cols="78"
                                                                       style="height:40"></textarea></th>
                                                </tr>
                                            </table>
                                        </th>
                                    </tr>

                                    <tr>
                                        <th class="title" colspan="2"><span id="historyLabel"
                                                                            style="display: none;"><a
                                                href="javascript: showHideItem('history');">History >> </a></span></th>
                                    </tr>

                                    <tr>
                                        <td colspan="2">
                                            <table cellpadding='1' cellspacing='0' id="history" width="100%">
                                                <tr>
                                                    <td>
                                                        <table style="display: none" id="detailHistory" cellpadding="1"
                                                               cellspacing="0" width="100%">
                                                            <tr>
                                                                <td class="subTitle" style="text-align: left"
                                                                    width="100%">
                                                                    <span>On Going Concern</span></td>
                                                            </tr>
                                                            <tr>
                                                                <td><carlos:encode value="${requestScope.ongoingConcerns}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                </tr>

                                                <tr>
                                                    <td>
                                                        <table cellpadding='1' cellspacing='0' width="100%">
                                                            <tr>
                                                                <td class="subTitle" width="12%">Cigarette<br>
                                                                    Smoking
                                                                </td>
                                                                <td class="subTitle" width="15%">Status<br>
                                                                    [cig/day 0-80]
                                                                </td>
                                                                <td class="subTitle" width="15%">History<br>
                                                                    [pk yrs 0-150]
                                                                </td>
                                                                <td class="subTitle" width="15%">Last Quit<br>
                                                                    (yyyy-MM-dd)
                                                                </td>
                                                                <td class="subTitle" width="15%"><fmt:message key="form.vt.obDate"/><br>
                                                                    (yyyy-MM-dd)
                                                                </td>
                                                                <td class="subTitle" width="28%"><fmt:message key='encounter.oscarMeasurements.Measurements.headingComments'/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="dataEntryTable"><fmt:message key="form.vt.lastData"/></td>
                                                                <td class="dataEntryTable"><span style="font-size: 80%">
                                                                    <carlos:encode value="${requestScope.SmkSLastData}"/>
                                                                </span></td>
                                                                <td class="dataEntryTable"><span style="font-size: 80%">
                                                                    <carlos:encode value="${requestScope.SmkHLastData}"/>
                                                                </span></td>
                                                                <td class="dataEntryTable"><carlos:encode value="${requestScope.SmkCLastData}"/>
                                                                </td>
                                                                <td class="dataEntryTable"><span style="font-size: 80%">
                                                                    <carlos:encode value="${requestScope.SmkSLDDate}"/>
                                                                </span></td>
                                                                <td class="dataEntryTable">&nbsp;</td>
                                                            </tr>
                                                            <tr>
                                                                <td class="dataEntryTable"><fmt:message key="form.vt.newData"/></td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(SmkSValue)" size="8%" /></td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(SmkHValue)" size="8%" /></td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(SmkCValue)" size="8%" /></td>
                                                                <td class="dataEntryTable" align="center"><input
                                                                        type="text" id="SmkDate" name="SmkDate"
                                                                        value="${carlos:forHtmlAttribute(requestScope.SmkSDate)}"
                                                                        size="8%"/></td>
                                                                <td class="dataEntryTable" align="center"><input
                                                                        type="text" id="SmkComments" name="SmkComments"
                                                                        value="${carlos:forHtmlAttribute(requestScope.SmkSComments)}"
                                                                        size="25%"/></td>
                                                                <input type="hidden" name="value(SmkSComments)" id="value(SmkSComments)"/>
                                                                <input type="hidden" name="value(SmkSDate)" id="value(SmkSDate)"/>
                                                                <input type="hidden" name="value(SmkHComments)" id="value(SmkHComments)"/>
                                                                <input type="hidden" name="value(SmkHDate)" id="value(SmkHDate)"/>
                                                                <input type="hidden" name="value(SmkCComments)" id="value(SmkCComments)"/>
                                                                <input type="hidden" name="value(SmkCDate)" id="value(SmkCDate)"/>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>


                                    <tr>
                                        <td colspan="2">
                                            <table cellpadding='1' cellspacing='0' width="100%">
                                                <tr>
                                                    <td class="subTitle" width="36%" style="text-align: left">
                                                        Lifestyle
                                                    </td>
                                                    <td class="subTitle" width="16%"><fmt:message key="form.vt.lastData"/></td>
                                                    <td class="subTitle" width="18%"><fmt:message key="form.vt.newData"/></td>
                                                    <td class="subTitle" width="30%"><fmt:message key='encounter.oscarMeasurements.Measurements.headingComments'/>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-Exer-label">${carlos:forHtml(ExerDesc)}</span>
                                                        <span class="eightyPercent"><carlos:encode value="${requestScope.ExerMeasuringInstrc}"/>
                                                        </span></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.ExerLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.ExerLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(ExerValue)" size="4%" />
                                                    </td>
                                                    <input type="hidden" name="value(ExerDate)" id="value(ExerDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-Exer-comments" aria-labelledby="vt-Exer-label vt-comments-label" type="text" name="value(ExerComments)" size="30%" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-Diet-label">${carlos:forHtml(DietDesc)}</span>
                                                        <span class="eightyPercent"><carlos:encode value="${requestScope.DietMeasuringInstrc}"/>
                                                        </span></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.DietLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.DietLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(DietValue)" size="4%" /></td>
                                                    <input type="hidden" name="value(DietDate)" id="value(DietDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-Diet-comments" aria-labelledby="vt-Diet-label vt-comments-label" type="text" name="value(DietComments)" size="30%" /></td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>


                                    <tr>
                                        <td colspan="2">
                                            <table cellpadding='1' cellspacing='0' width="100%">
                                                <tr>
                                                    <td class="subTitle" width="36%" style="text-align: left">
									<span id="psychHead1" style="display: none;">Psychosocial
									Screen</span></td>
                                                    <td class="subTitle" width="16%"><span id="psychHead2"
                                                                                           style="display: none;"><fmt:message key="form.vt.lastData"/></span>
                                                    </td>
                                                    <td class="subTitle" width="18%"><span id="psychHead3"
                                                                                           style="display: none;"><fmt:message key="form.vt.newData"/></span>
                                                    </td>
                                                    <td class="subTitle" width="30%"><span id="psychHead4"
                                                                                           style="display: none;"> <fmt:message key='encounter.oscarMeasurements.Measurements.headingComments'/>
									</span></td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-DpSc-label"><carlos:encode value="${requestScope.DpScDesc}"/></span>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.DpScLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.DpScLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-DpSc-yes" aria-labelledby="vt-DpSc-label vt-DpSc-yes-label" type="radio" name="value(DpScValue)" value="yes"/><label id="vt-DpSc-yes-label" for="vt-DpSc-yes">Yes</label>
                                                        <input id="vt-DpSc-no" aria-labelledby="vt-DpSc-label vt-DpSc-no-label" type="radio" name="value(DpScValue)" value="no"/><label id="vt-DpSc-no-label" for="vt-DpSc-no">No</label>
                                                        <a
                                                            title="clear all"
                                                            href="javascript:clearAll(DpScId, DpScId-1)">&nbsp
                                                        <span style="text-decoration: underline; color: blue;">clr</span>
                                                    </a></td>
                                                    <input type="hidden" name="value(DpScDate)" id="value(DpScDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-DpSc-comments" aria-labelledby="vt-DpSc-label vt-comments-label" type="text" name="value(DpScComments)" size="30%" /></td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-StSc-label"><carlos:encode value="${requestScope.StScDesc}"/></span>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table role="presentation" cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.StScLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.StScLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-StSc-yes" aria-labelledby="vt-StSc-label vt-StSc-yes-label" type="radio" name="value(StScValue)" value="yes"/><label id="vt-StSc-yes-label" for="vt-StSc-yes">Yes</label>
                                                        <input id="vt-StSc-no" aria-labelledby="vt-StSc-label vt-StSc-no-label" type="radio" name="value(StScValue)" value="no"/><label id="vt-StSc-no-label" for="vt-StSc-no">No</label> <a
                                                            title="clear all"
                                                            href="javascript:clearAll(StScId, StScId-1)">&nbsp
                                                        <span style="text-decoration: underline; color: blue;">clr</span>
                                                    </a></td>
                                                    <input type="hidden" name="value(StScDate)" id="value(StScDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-StSc-comments" aria-labelledby="vt-StSc-label vt-comments-label" type="text" name="value(StScComments)" size="30%" /></td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-LcCt-label"><carlos:encode value="${requestScope.LcCtDesc}"/></span>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table role="presentation" cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.LcCtLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.LcCtLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input id="vt-LcCt-yes" aria-labelledby="vt-LcCt-label vt-LcCt-yes-label" type="radio" name="value(LcCtValue)" value="yes"/><label id="vt-LcCt-yes-label" for="vt-LcCt-yes">Yes</label>
                                                        <input id="vt-LcCt-no" aria-labelledby="vt-LcCt-label vt-LcCt-no-label" type="radio" name="value(LcCtValue)" value="no"/><label id="vt-LcCt-no-label" for="vt-LcCt-no">No</label> <a
                                                            title="clear all"
                                                            href="javascript:clearAll(LcCtId, LcCtId-1)">&nbsp
                                                        <span style="text-decoration: underline; color: blue;">clr</span>
                                                    </a></td>
                                                    <input type="hidden" name="value(LcCtDate)" id="value(LcCtDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-LcCt-comments" aria-labelledby="vt-LcCt-label vt-comments-label" type="text" name="value(LcCtComments)" size="30%" /></td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>


                                    <tr>
                                        <td colspan="2">
                                            <table id="vt-medication-adherence" cellpadding='1' cellspacing='0' width="100%" border="1">
                                                <tr>
                                                    <th aria-labelledby="medHead1" scope="col" class="subTitle vt-column-heading" width="36%" style="text-align: left">
									<span id="medHead1" style="display: none;">Medication
									Adherence screen</span></th>
                                                    <th aria-labelledby="medHead2" scope="col" class="subTitle vt-column-heading" width="16%"><span id="medHead2"
                                                                                           style="display: none;"><fmt:message key="form.vt.lastData"/></span>
                                                    </th>
                                                    <th aria-labelledby="medHead3" scope="col" class="subTitle vt-column-heading" width="18%"><span id="medHead3"
                                                                                           style="display: none;"><fmt:message key="form.vt.newData"/></span>
                                                    </th>
                                                    <th aria-labelledby="medHead4" scope="col" class="subTitle vt-column-heading" width="30%"><span id="medHead4"
                                                                                           style="display: none;"> <fmt:message key='encounter.oscarMeasurements.Measurements.headingComments'/>
									</span></th>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-MedG-label"><carlos:encode value="${requestScope.MedGDesc}"/></span>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table role="presentation" cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.MedGLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.MedGLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input id="vt-MedG-yes" aria-labelledby="vt-MedG-label vt-MedG-yes-label" type="radio" name="value(MedGValue)" value="yes"/><label id="vt-MedG-yes-label" for="vt-MedG-yes">Yes</label>
                                                        <input id="vt-MedG-no" aria-labelledby="vt-MedG-label vt-MedG-no-label" type="radio" name="value(MedGValue)" value="no"/><label id="vt-MedG-no-label" for="vt-MedG-no">No</label> <a
                                                            title="clear all"
                                                            href="javascript:clearAll(MedGId, MedGId-1)">&nbsp
                                                        <span style="text-decoration: underline; color: blue;">clr</span>
                                                    </a></td>
                                                    <input type="hidden" name="value(MedGDate)" id="value(MedGDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-MedG-comments" aria-labelledby="vt-MedG-label vt-comments-label" type="text" name="value(MedGComments)" size="30%" /></td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-MedN-label"><carlos:encode value="${requestScope.MedNDesc}"/></span>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table role="presentation" cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.MedNLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.MedNLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input id="vt-MedN-yes" aria-labelledby="vt-MedN-label vt-MedN-yes-label" type="radio" name="value(MedNValue)" value="yes"/><label id="vt-MedN-yes-label" for="vt-MedN-yes">Yes</label>
                                                        <input id="vt-MedN-no" aria-labelledby="vt-MedN-label vt-MedN-no-label" type="radio" name="value(MedNValue)" value="no"/><label id="vt-MedN-no-label" for="vt-MedN-no">No</label> <a
                                                            title="clear all"
                                                            href="javascript:clearAll(MedNId, MedNId-1)">&nbsp
                                                        <span style="text-decoration: underline; color: blue;">clr</span>
                                                    </a></td>
                                                    <input type="hidden" name="value(MedNDate)" id="value(MedNDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-MedN-comments" aria-labelledby="vt-MedN-label vt-comments-label" type="text" name="value(MedNComments)" size="30%" /></td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-MedR-label"><carlos:encode value="${requestScope.MedRDesc}"/></span>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table role="presentation" cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.MedRLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.MedRLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input id="vt-MedR-yes" aria-labelledby="vt-MedR-label vt-MedR-yes-label" type="radio" name="value(MedRValue)" value="yes"/><label id="vt-MedR-yes-label" for="vt-MedR-yes">Yes</label>
                                                        <input id="vt-MedR-no" aria-labelledby="vt-MedR-label vt-MedR-no-label" type="radio" name="value(MedRValue)" value="no"/><label id="vt-MedR-no-label" for="vt-MedR-no">No</label> <a
                                                            title="clear all"
                                                            href="javascript:clearAll(MedRId, MedRId-1)">&nbsp
                                                        <span style="text-decoration: underline; color: blue;">clr</span>
                                                    </a></td>
                                                    <input type="hidden" name="value(MedRDate)" id="value(MedRDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-MedR-comments" aria-labelledby="vt-MedR-label vt-comments-label" type="text" name="value(MedRComments)" size="30%" /></td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-MedA-label"><carlos:encode value="${requestScope.MedADesc}"/></span>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table role="presentation" cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.MedALDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.MedALastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-MedA-yes" aria-labelledby="vt-MedA-label vt-MedA-yes-label" type="radio" name="value(MedAValue)" value="yes"/><label id="vt-MedA-yes-label" for="vt-MedA-yes">Yes</label>
                                                        <input id="vt-MedA-no" aria-labelledby="vt-MedA-label vt-MedA-no-label" type="radio" name="value(MedAValue)" value="no"/><label id="vt-MedA-no-label" for="vt-MedA-no">No</label> <a
                                                            title="clear all"
                                                            href="javascript:clearAll(MedAId, MedAId-1)">&nbsp
                                                        <span style="text-decoration: underline; color: blue;">clr</span>
                                                    </a></td>
                                                    <input type="hidden" name="value(MedADate)" id="value(MedADate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-MedA-comments" aria-labelledby="vt-MedA-label vt-comments-label" type="text" name="value(MedAComments)" size="30%"/></td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>

                                    <tr>
                                        <th class="soap" width="2%"><span id="bigO"
                                                                          style="display: none;">O</span></th>
                                        <td style="text-align: center; font-size: small; font-weight: bold;">
                                            <div id="objective" style="display: none">
                                                <label class="vt-screen-reader-only" for="vt-objective">Objective</label><textarea id="vt-objective" name="value(objective)" cols="78"
                                                                   style="height:40"></textarea>
                                            </div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <th class="title" colspan="2"><span id="vitalsLabel"
                                                                            style="display: none;"><a
                                                href="javascript: showHideItem('vital');">Vitals >> </a></span></th>
                                    </tr>


                                    <tr>
                                        <td colspan="2">
                                            <table cellpadding='1' cellspacing='0' id="vital">
                                                <tr>
                                                    <th scope="col" class="subTitle vt-column-heading" width="36%" style="text-align: left"><span class="vt-screen-reader-only">Measurement</span>
                                                    </th>
                                                    <th aria-labelledby="vitalHead1" scope="col" class="subTitle vt-column-heading" width="16%"><span id="vitalHead1"
                                                                                           style="display: none;"><fmt:message key="form.vt.lastData"/><br>
									Entered on</span></th>
                                                    <th aria-labelledby="vitalHead2" scope="col" class="subTitle vt-column-heading" width="18%"><span id="vitalHead2"
                                                                                           style="display: none;"><fmt:message key="form.vt.newData"/></span>
                                                    </th>
                                                    <th aria-labelledby="vitalHead3" scope="col" class="subTitle vt-column-heading" width="30%"><span id="vitalHead3"
                                                                                           style="display: none;"><fmt:message key='encounter.oscarMeasurements.Measurements.headingComments'/></span>
                                                    </th>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><label id="vt-BP-label" for="vt-BP-value">${carlos:forHtml(empty requestScope.BPDisplay ? 'BP' : requestScope.BPDisplay)}</label>
                                                        <br>
                                                        <span class="eightyPercent"><carlos:encode value="${requestScope.BPMeasuringInstrc}"/>
                                                        </span></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table role="presentation" cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.BPLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.BPLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-BP-value" aria-labelledby="vt-BP-label vt-new-data-label" type="text" name="value(BPValue)" size="5%" /></td>
                                                    <input type="hidden" name="value(BPDate)" id="value(BPDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-BP-comments" aria-labelledby="vt-BP-label vt-comments-label" type="text" name="value(BPComments)" size="30%" /></td>
                                                </tr>


                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-WHRB-label"><carlos:encode value="${requestScope.WHRBDisplay}"/></span>
                                                        <br>
                                                        <span class="eightyPercent"><carlos:encode value="${requestScope.WHRBMeasuringInstrc}"/>
                                                        </span></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <c:if test="${not empty WHRBLastData}">
                                                            <table role="presentation" cellpadding="0" cellspacing="0">
                                                                <tr>
                                                                    <td class="eightyPercent" align="left"><carlos:encode value="${requestScope.WHRBLDDate}"/></td>
                                                                </tr>
                                                                <tr>
                                                                    <td class="eightyPercent" align="right"><carlos:encode value="${requestScope.WHRBLastData}"/></td>
                                                                </tr>
                                                            </table>
                                                        </c:if>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-WHRB-yes" aria-labelledby="vt-WHRB-label vt-WHRB-yes-label" type="radio" name="value(WHRBValue)" value="yes"/><label id="vt-WHRB-yes-label" for="vt-WHRB-yes">Yes</label>
                                                        <input id="vt-WHRB-no" aria-labelledby="vt-WHRB-label vt-WHRB-no-label" type="radio" name="value(WHRBValue)" value="no"/><label id="vt-WHRB-no-label" for="vt-WHRB-no">No</label> <a
                                                            title="clear all"
                                                            href="javascript:clearAll(WHRBId, WHRBId-1)">&nbsp
                                                        <span style="text-decoration: underline; color: blue;">clr</span>
                                                    </a></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-WHRB-comments" aria-labelledby="vt-WHRB-label vt-comments-label" type="text" name="value(WHRBComments)" size="30%" /></td>
                                                    </tr>


                                                <tr>
                                                    <td class="dataEntryTable"><label id="vt-WC-label" for="vt-WC-value">${carlos:forHtml(empty requestScope.WCDisplay ? 'WC' : requestScope.WCDisplay)}</label>
                                                        <br>
                                                        <span class="eightyPercent"><carlos:encode value="${requestScope.WCMeasuringInstrc}"/>
                                                        </span></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <c:if test="${not empty WCLastData}">
                                                            <table role="presentation" cellpadding="0" cellspacing="0">
                                                                <tr>
                                                                    <td class="eightyPercent" align="left"><carlos:encode value="${requestScope.WCLDDate}"/></td>
                                                                </tr>
                                                                <tr>
                                                                    <td class="eightyPercent" align="right"><carlos:encode value="${requestScope.WCLastData}"/></td>
                                                                </tr>
                                                            </table>
                                                        </c:if>

                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-WC-value" aria-labelledby="vt-WC-label vt-new-data-label" type="text" name="value(WCValue)" size="5%"/>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-WC-comments" aria-labelledby="vt-WC-label vt-comments-label" type="text" name="value(WCComments)" size="30%" /></td>
                                                </tr>
                                                <!--new-->
                                                <tr>
                                                    <td width="36%"
                                                        class="dataEntryTable"><label id="vt-HC-label" for="vt-HC-value">${carlos:forHtml(empty requestScope.HCDisplay ? 'HC' : requestScope.HCDisplay)}</label>
                                                        <br>
                                                        <span class="eightyPercent"><carlos:encode value="${requestScope.HCMeasuringInstrc}"/>
                                                        </span></td>
                                                    <td width="16%" class="dataEntryTable" align="center">
                                                        <c:if test="${not empty HCLastData}">
                                                            <table role="presentation" cellpadding="0" cellspacing="0">
                                                                <tr>
                                                                    <td class="eightyPercent" align="left"><carlos:encode value="${requestScope.HCLDDate}"/></td>
                                                                </tr>
                                                                <tr>
                                                                    <td class="eightyPercent" align="right"><carlos:encode value="${requestScope.HCLastData}"/></td>
                                                                </tr>
                                                            </table>
                                                        </c:if>
                                                    </td>
                                                    <td width="18%" class="dataEntryTable" align="center">
                                                        <input id="vt-HC-value" aria-labelledby="vt-HC-label vt-new-data-label" type="text" name="value(HCValue)" size="5%"/>
                                                    </td>
                                                    <td width="33%" class="dataEntryTable" align="center">
                                                        <input id="vt-HC-comments" aria-labelledby="vt-HC-label vt-comments-label" type="text" name="value(HCComments)" size="30%"/>
                                                    </td>
                                                </tr>

                                                <tr>
                                                    <td colspan="5">
                                                        <table cellpadding="1" cellspacing="0" width="100%"
                                                               style="display: none" id="detailVital">
                                                            <tr class="vt-screen-reader-only">
                                                                <th scope="col">Measurement</th>
                                                                <th scope="col"><fmt:message key="form.vt.lastData"/></th>
                                                                <th scope="col"><fmt:message key="form.vt.newData"/></th>
                                                                <th scope="col"><fmt:message key="encounter.oscarMeasurements.Measurements.headingComments"/></th>
                                                            </tr>

                                                            <tr>
                                                                <td class="dataEntryTable"><label id="vt-HR-label" for="vt-HR-value">${carlos:forHtml(empty requestScope.HRDisplay ? 'HR' : requestScope.HRDisplay)}</label>
                                                                    <br>
                                                                    <span class="eightyPercent"><carlos:encode value="${requestScope.HRMeasuringInstrc}"/>
                                                                    </span></td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <table role="presentation" cellpadding='0' cellspacing='0'>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="left"><carlos:encode value="${requestScope.HRLDDate}"/>
                                                                            </td>
                                                                        </tr>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="right"><carlos:encode value="${requestScope.HRLastData}"/>
                                                                            </td>
                                                                        </tr>
                                                                    </table>
                                                                </td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input id="vt-HR-value" aria-labelledby="vt-HR-label vt-new-data-label" type="text" name="value(HRValue)" size="5%" />
                                                                </td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input id="vt-HR-comments" aria-labelledby="vt-HR-label vt-comments-label" type="text" name="value(HRComments)" size="30%" />
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="dataEntryTable"><label id="vt-HT-label" for="vt-HT-value">${carlos:forHtml(empty requestScope.HTDisplay ? 'HT' : requestScope.HTDisplay)}</label>
                                                                    <br>
                                                                    <span class="eightyPercent"><carlos:encode value="${requestScope.HTMeasuringInstrc}"/>
                                                                    </span></td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <table role="presentation" cellpadding='0' cellspacing='0'>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="left"><carlos:encode value="${requestScope.HTLDDate}"/>
                                                                            </td>
                                                                        </tr>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="right"><carlos:encode value="${requestScope.HTLastData}"/>
                                                                            </td>
                                                                        </tr>
                                                                    </table>
                                                                </td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input id="vt-HT-value" aria-labelledby="vt-HT-label vt-new-data-label" type="text" name="value(HTValue)" size="5%" />
                                                                </td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input id="vt-HT-comments" aria-labelledby="vt-HT-label vt-comments-label" type="text" name="value(HTComments)" size="30%" />
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><label id="vt-WT-label" for="vt-WT-value">${carlos:forHtml(empty requestScope.WTDisplay ? 'WT' : requestScope.WTDisplay)}</label>
                                                        <br>
                                                        <span class="eightyPercent"><carlos:encode value="${requestScope.WTMeasuringInstrc}"/>
                                                        </span></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <c:if test="${not empty WTLastData}">
                                                            <table role="presentation" cellpadding="0" cellspacing="0">
                                                                <tr>
                                                                    <td class="eightyPercent" align="left"><carlos:encode value="${requestScope.WTLDDate}"/></td>
                                                                </tr>
                                                                <tr>
                                                                    <td class="eightyPercent" align="right"><carlos:encode value="${requestScope.WTLastData}"/></td>
                                                                </tr>
                                                            </table>
                                                        </c:if>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-WT-value" aria-labelledby="vt-WT-label vt-new-data-label" type="text" name="value(WTValue)" size="5%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-WT-comments" aria-labelledby="vt-WT-label vt-comments-label" type="text" name="value(WTComments)" size="30%" />
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>

                                    <tr>
                                        <th class="title" colspan="2"><span id="examLabel"
                                                                            style="display: none;"><a
                                                href="javascript: showHideItem('examinationFoot'); showHideItem('examinationEye');">Examination
							>> </a></span></th>
                                    </tr>


                                    <tr>
                                        <td colspan="2">
                                            <table cellpadding='0' cellspacing='0' border="0" width="100%"
                                                   id="examinationFoot">
                                                <tr>
                                                    <td class="subTitle" width="28%">&nbsp;</td>
                                                    <td class="subTitle" width="17%"><fmt:message key="form.vt.lastDataEnteredOn"/><br></td>
                                                    <td class="subTitle" width="22%"><fmt:message key="form.vt.newData"/></td>
                                                    <td class="subTitle" width="10%"><fmt:message key="form.vt.obDate"/><br>
                                                        (yyyy-MM-dd)
                                                    </td>
                                                    <td class="subTitle" width="23%"><fmt:message key='encounter.oscarMeasurements.Measurements.headingComments'/></td>
                                                </tr>
                                                <tr>
                                                    <th class="dataEntryTable" colspan="3">Foot Exam</th>
                                                    <td class="dataEntryTable" valign="top" align="center"><label class="vt-screen-reader-only" for="FTDate">Foot exam <fmt:message key="form.vt.obDate"/></label><input
                                                            type="text" id="FTDate" name="FTDate"
                                                            value="${carlos:forHtmlAttribute(requestScope.FTNeDate)}" size="10%"/></td>
                                                    <td class="dataEntryTable" rowspan="8" valign="top"
                                                        align="center"><label class="vt-screen-reader-only" for="vt-FTComments">Foot exam <fmt:message key="encounter.oscarMeasurements.Measurements.headingComments"/></label><textarea id="vt-FTComments" name="FTComments" wrap="hard"
                                                                                 cols="24" style="height: 80"><carlos:encode value="${requestScope.FTExComments}"/></textarea>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-FTEx-label"><carlos:encode value="${requestScope.FTExDesc}"/></span>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table role="presentation" cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.FTExLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.FTExLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input id="vt-FTEx-yes" aria-labelledby="vt-FTEx-label vt-FTEx-yes-label" type="radio" name="value(FTExValue)"
                                                            onclick="javascript:controlFTExam();" value="yes"/><label id="vt-FTEx-yes-label" for="vt-FTEx-yes">Yes</label>
                                                        <input id="vt-FTEx-no" aria-labelledby="vt-FTEx-label vt-FTEx-no-label" type="radio" name="value(FTExValue)"
                                                                onclick="javascript:controlFTExam();" value="no"/><label id="vt-FTEx-no-label" for="vt-FTEx-no">No</label> <a
                                                                title="clear all"
                                                                href="javascript:clearAll(FTExId, FTExId-1)">&nbsp
                                                            <span style="text-decoration: underline; color: blue;">clr</span>
                                                        </a></td>
                                                    <td class="dataEntryTable" rowspan="7" valign="top"
                                                        align="center">&nbsp;
                                                    </td>
                                                    <input type="hidden" name="value(FTExDate)" id="value(FTExDate)"/>
                                                    <input type="hidden" name="value(FTExComments)" id="value(FTExComments)"/>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><carlos:encode value="${requestScope.FTNeDesc}"/>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.FTNeLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.FTNeLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(FTNeValue)" value="yes"/>Yes <input type="radio" name="value(FTNeValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(ftNeId, ftNeId-1)">&nbsp
                                                        <span style="text-decoration: underline; color: blue;">clr</span>
                                                    </a></td>
                                                    <input type="hidden" name="value(FTNeDate)" id="value(FTNeDate)"/>
                                                    <input type="hidden" name="value(FTNeComments)" id="value(FTNeComments)"/>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><carlos:encode value="${requestScope.FTIsDesc}"/>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.FTIsLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.FTIsLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(FTIsValue)" value="yes"/>Yes <input type="radio" name="value(FTIsValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(ftIsId, ftIsId-1)">&nbsp
                                                        <span style="text-decoration: underline; color: blue;">clr</span>
                                                    </a></td>
                                                    <input type="hidden" name="value(FTIsDate)" id="value(FTIsDate)"/>
                                                    <input type="hidden" name="value(FTIsComments)" id="value(FTIsComments)"/>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><carlos:encode value="${requestScope.FTUlDesc}"/>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.FTUlLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.FTUlLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(FTUlValue)" value="yes"/>Yes <input type="radio" name="value(FTUlValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(ftUlId, ftUlId-1)">&nbsp
                                                        <span style="text-decoration: underline; color: blue;">clr</span>
                                                    </a></td>
                                                    <input type="hidden" name="value(FTUlDate)" id="value(FTUlDate)"/>
                                                    <input type="hidden" name="value(FTUlComments)" id="value(FTUlComments)"/>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><carlos:encode value="${requestScope.FTInDesc}"/>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.FTInLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.FTInLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(FTInValue)" value="yes"/>Yes <input type="radio" name="value(FTInValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(ftInId, ftInId-1)">&nbsp
                                                        <span style="text-decoration: underline; color: blue;">clr</span>
                                                    </a></td>
                                                    <input type="hidden" name="value(FTInDate)" id="value(FTInDate)"/>
                                                    <input type="hidden" name="value(FTInComments)" id="value(FTInComments)"/>
                                                </tr>
                                                <tr>
                                                    <td colspan='3'>
                                                        <table cellpadding="0" cellspacing="0" width="100%"
                                                               style="display: none" id="detailFT">
                                                            <tr>
                                                                <td width="29%"
                                                                    class="dataEntryTable"><carlos:encode value="${requestScope.FTOtDesc}"/>
                                                                </td>
                                                                <td width="16%" class="dataEntryTable" align="center">
                                                                    <table cellpadding='0' cellspacing='0'>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="left"><carlos:encode value="${requestScope.FTOtLDDate}"/>
                                                                            </td>
                                                                        </tr>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="right"><carlos:encode value="${requestScope.FTOtLastData}"/>
                                                                            </td>
                                                                        </tr>
                                                                    </table>
                                                                </td>
                                                                <td width="22%" class="dataEntryTable" align="center">
                                                                    <input type="radio"
                                                                            name="value(FTOtValue)" value="yes"/>Yes
                                                                    <input type="radio"
                                                                            name="value(FTOtValue)" value="no"/>No
                                                                    <a
                                                                            title="clear all"
                                                                            href="javascript:clearAll(ftOtId, ftOtId-1)">&nbsp
                                                                        <span
                                                                                style="text-decoration: underline; color: blue;">clr</span>
                                                                    </a></td>
                                                                <input type="hidden" name="value(FTOtDate)" id="value(FTOtDate)"/>
                                                                <input type="hidden" name="value(FTOtComments)" id="value(FTOtComments)"/>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><carlos:encode value="${requestScope.FTReDesc}"/>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.FTReLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.FTReLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(FTReValue)" value="yes"/>Yes <input type="radio" name="value(FTReValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(ftReId, ftReId-1)">&nbsp
                                                        <span style="text-decoration: underline; color: blue;">clr</span>
                                                    </a></td>
                                                    <input type="hidden" name="value(FTReDate)" id="value(FTReDate)"/>
                                                    <input type="hidden" name="value(FTReComments)" id="value(FTReComments)"/>
                                                </tr>
                                            </table>
                                            <table cellpadding='0' cellspacing='0' border="1" width="100%"
                                                   id="examinationEye">
                                                <tr>
                                                    <td class="subTitle" width="28%">&nbsp;</td>
                                                    <td class="subTitle" width="17%"><fmt:message key="form.vt.lastDataEnteredOn"/><br></td>
                                                    <td class="subTitle" width="22%"><fmt:message key="form.vt.newData"/></td>
                                                    <td class="subTitle" width="10%"><fmt:message key="form.vt.obDate"/><br>
                                                        (yyyy-MM-dd)
                                                    </td>
                                                    <td class="subTitle" width="23%"><fmt:message key='encounter.oscarMeasurements.Measurements.headingComments'/></td>
                                                </tr>
                                                <tr>
                                                    <th class="dataEntryTable" colspan="3">Eye Exam</th>
                                                    <td class="dataEntryTable" valign="top" align="center"><input
                                                            type="text" name="iDate"
                                                            value="${carlos:forHtmlAttribute(requestScope.iDiaDate)}" size="10%"/>
                                                    </td>
                                                    <td class="dataEntryTable" rowspan="8" valign="top"
                                                        align="center"><%-- Read back from a row storeEyeExamSupportData() writes (the date
                                                             likewise reads iDiaDate); iEx is retained history only, so it is
                                                             shown only for a legacy record whose eye rows hold no comment. --%><label class="vt-screen-reader-only" for="vt-iComments">Eye exam <fmt:message key="encounter.oscarMeasurements.Measurements.headingComments"/></label><textarea id="vt-iComments" name="iComments" wrap="hard"
                                                                                 cols="24"
                                                                                 style="height: 80"><carlos:encode value="${not empty iDiaComments ? iDiaComments : iExComments}"/></textarea>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><carlos:encode value="${requestScope.iDiaDesc}"/>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.iDiaLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.iDiaLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(iDiaValue)" value="yes"/>Yes <input type="radio" name="value(iDiaValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(eyeDiaId, eyeDiaId-1)">&nbsp <span
                                                            style="text-decoration: underline; color: blue;">clr</span>
                                                    </a></td>
                                                    <input type="hidden" name="value(iDiaDate)" id="value(iDiaDate)"/>
                                                    <input type="hidden" name="value(iDiaComments)" id="value(iDiaComments)"/>
                                                </tr>
                                                <tr>
                                                    <td colspan='3'>
                                                        <table cellpadding="0" cellspacing="0" width="100%"
                                                               style="display: none" id="detailEye">
                                                            <tr>
                                                                <td width="29%"
                                                                    class="dataEntryTable"><carlos:encode value="${requestScope.iHypDesc}"/>
                                                                </td>
                                                                <td width="16%" class="dataEntryTable" align="center">
                                                                    <table cellpadding='0' cellspacing='0'>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="left"><carlos:encode value="${requestScope.iHypLDDate}"/>
                                                                            </td>
                                                                        </tr>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="right"><carlos:encode value="${requestScope.iHypLastData}"/>
                                                                            </td>
                                                                        </tr>
                                                                    </table>
                                                                </td>
                                                                <td width="22%" class="dataEntryTable" align="center">
                                                                    <input type="radio"
                                                                            name="value(iHypValue)" value="yes"/>Yes
                                                                    <input type="radio"
                                                                            name="value(iHypValue)" value="no"/>No
                                                                    <a
                                                                            title="clear all"
                                                                            href="javascript:clearAll(eyeHypId, eyeHypId-1)">&nbsp
                                                                        <span
                                                                                style="text-decoration: underline; color: blue;">clr</span>
                                                                    </a></td>
                                                                <input type="hidden" name="value(iHypDate)" id="value(iHypDate)"/>
                                                                <input type="hidden" name="value(iHypComments)" id="value(iHypComments)"/>
                                                            </tr>
                                                            <tr>
                                                                <td class="dataEntryTable"><carlos:encode value="${requestScope.iOthDesc}"/>
                                                                </td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <table cellpadding='0' cellspacing='0'>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="left"><carlos:encode value="${requestScope.iOthLDDate}"/>
                                                                            </td>
                                                                        </tr>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="right"><carlos:encode value="${requestScope.iOthLastData}"/>
                                                                            </td>
                                                                        </tr>
                                                                    </table>
                                                                </td>
                                                                <td class="dataEntryTable" align="center"><input type="radio"
                                                                        name="value(iOthValue)" value="yes"/>Yes
                                                                    <input type="radio"
                                                                            name="value(iOthValue)" value="no"/>No
                                                                    <a
                                                                            title="clear all"
                                                                            href="javascript:clearAll(eyeOthId, eyeOthId-1)">&nbsp
                                                                        <span
                                                                                style="text-decoration: underline; color: blue;">clr</span>
                                                                    </a></td>
                                                                <input type="hidden" name="value(iOthDate)" id="value(iOthDate)"/>
                                                                <input type="hidden" name="value(iOthComments)" id="value(iOthComments)"/>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable">${carlos:forHtml(iRefDesc)}</td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.iRefLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.iRefLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(iRefValue)" value="yes"/>Yes <input type="radio" name="value(iRefValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(eyeRefId, eyeRefId-1)">&nbsp <span
                                                            style="text-decoration: underline; color: blue;">clr</span>
                                                    </a></td>
                                                    <input type="hidden" name="value(iRefDate)" id="value(iRefDate)"/>
                                                    <input type="hidden" name="value(iRefComments)" id="value(iRefComments)"/>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>


                                    <tr>
                                        <th class="title" colspan="2"><span id="labsLabel"
                                                                            style="display: none;"><a
                                                href="javascript: showHideItem('labs');">Labs >> </a></span></th>
                                    </tr>


                                    <tr>
                                        <td colspan="2">
                                            <table cellpadding='0' cellspacing='0' id="labs">
                                                <tr>
                                                    <td class="subTitle" width="34%"></td>
                                                    <td class="subTitle" width="16%"><fmt:message key="form.vt.lastDataEnteredOn"/><br></td>
                                                    <td class="subTitle" width="5%"><fmt:message key="form.vt.newData"/></td>
                                                    <td class="subTitle" width="10%"><fmt:message key="form.vt.obDate"/><br>
                                                        (yyyy-MM-dd)
                                                    </td>
                                                    <td class="subTitle" width="35%"><fmt:message key='encounter.oscarMeasurements.Measurements.headingComments'/>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-HbA1-label"><carlos:encode value="${requestScope.HbA1Desc}"/></span><br>
                                                        <span class="eightyPercent"><carlos:encode value="${requestScope.HbA1MeasuringInstrc}"/>
                                                        </span></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.HbA1LDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.HbA1LastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(HbA1Value)" size="4%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-HbA1-date" aria-labelledby="vt-HbA1-label vt-ob-date-label" type="text" name="value(HbA1Date)" size="10%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-HbA1-comments" aria-labelledby="vt-HbA1-label vt-comments-label" type="text" name="value(HbA1Comments)" size="30%" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-LDL-label"><carlos:encode value="${requestScope.LDLDesc}"/></span><br>
                                                        <span class="eightyPercent"><carlos:encode value="${requestScope.LDLMeasuringInstrc}"/>
                                                        </span></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.LDLLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.LDLLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(LDLValue)" size="4%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-LDL-date" aria-labelledby="vt-LDL-label vt-ob-date-label" type="text" name="value(LDLDate)" size="10%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-LDL-comments" aria-labelledby="vt-LDL-label vt-comments-label" type="text" name="value(LDLComments)" size="30%" /></td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-HDL-label"><carlos:encode value="${requestScope.HDLDesc}"/></span><br>
                                                        <span class="eightyPercent"><carlos:encode value="${requestScope.HDLMeasuringInstrc}"/>
                                                        </span></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.HDLLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.HDLLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(HDLValue)" size="4%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-HDL-date" aria-labelledby="vt-HDL-label vt-ob-date-label" type="text" name="value(HDLDate)" size="10%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-HDL-comments" aria-labelledby="vt-HDL-label vt-comments-label" type="text" name="value(HDLComments)" size="30%" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-TCHL-label"><carlos:encode value="${requestScope.TCHLDesc}"/></span><br>
                                                        <span class="eightyPercent"><carlos:encode value="${requestScope.TCHLMeasuringInstrc}"/>
                                                        </span></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.TCHLLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.TCHLLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(TCHLValue)" size="4%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-TCHL-date" aria-labelledby="vt-TCHL-label vt-ob-date-label" type="text" name="value(TCHLDate)" size="10%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-TCHL-comments" aria-labelledby="vt-TCHL-label vt-comments-label" type="text" name="value(TCHLComments)" size="30%" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td colspan='5'>
                                                        <table cellpadding="0" cellspacing="0" width="100%"
                                                               style="display: none" id="detailLab">
                                                            <tr>
                                                                <td width="34%"
                                                                    class="dataEntryTable"><span id="vt-TRIG-label"><carlos:encode value="${requestScope.TRIGDesc}"/></span>
                                                                    <br>
                                                                    <span class="eightyPercent"><carlos:encode value="${requestScope.TRIGMeasuringInstrc}"/>
                                                                    </span></td>
                                                                <td width="16%" class="dataEntryTable" align="center">
                                                                    <table cellpadding='0' cellspacing='0'>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="left"><carlos:encode value="${requestScope.TRIGLDDate}"/>
                                                                            </td>
                                                                        </tr>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="right"><carlos:encode value="${requestScope.TRIGLastData}"/>
                                                                            </td>
                                                                        </tr>
                                                                    </table>
                                                                </td>
                                                                <td width="5%" class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(TRIGValue)" size="4%"/></td>
                                                                <td width="10%" class="dataEntryTable" align="center">
                                                                    <input id="vt-TRIG-date" aria-labelledby="vt-TRIG-label vt-ob-date-label" type="text" name="value(TRIGDate)" size="10%"/></td>
                                                                <td width="35%" class="dataEntryTable" align="center">
                                                                    <input id="vt-TRIG-comments" aria-labelledby="vt-TRIG-label vt-comments-label" type="text" name="value(TRIGComments)" size="30%"/></td>
                                                            </tr>
                                                            <tr>
                                                                <td class="dataEntryTable"><span id="vt-BG-label"><carlos:encode value="${requestScope.BGDesc}"/></span>
                                                                    <br>
                                                                    <span class="eightyPercent"><carlos:encode value="${requestScope.BGMeasuringInstrc}"/>
                                                                    </span></td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <table cellpadding='0' cellspacing='0'>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="left"><carlos:encode value="${requestScope.BGLDDate}"/>
                                                                            </td>
                                                                        </tr>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="right"><carlos:encode value="${requestScope.BGLastData}"/>
                                                                            </td>
                                                                        </tr>
                                                                    </table>
                                                                </td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(BGValue)" size="4%" />
                                                                </td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input id="vt-BG-date" aria-labelledby="vt-BG-label vt-ob-date-label" type="text" name="value(BGDate)" size="10%" />
                                                                </td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input id="vt-BG-comments" aria-labelledby="vt-BG-label vt-comments-label" type="text" name="value(BGComments)" size="30%" />
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-UALB-label"><carlos:encode value="${requestScope.UALBDesc}"/></span><br>
                                                        <span class="eightyPercent"><carlos:encode value="${requestScope.UALBMeasuringInstrc}"/>
                                                        </span></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.UALBLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.UALBLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(UALBValue)" size="4%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-UALB-date" aria-labelledby="vt-UALB-label vt-ob-date-label" type="text" name="value(UALBDate)" size="10%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-UALB-comments" aria-labelledby="vt-UALB-label vt-comments-label" type="text" name="value(UALBComments)" size="30%" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-24UA-label"><carlos:encode value="${requestScope['24UADesc']}"/></span><br>
                                                        <span class="eightyPercent"><carlos:encode value="${requestScope['24UAMeasuringInstrc']}"/>
                                                        </span></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope['24UALDDate']}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope['24UALastData']}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(24UAValue)" size="4%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-24UA-date" aria-labelledby="vt-24UA-label vt-ob-date-label" type="text" name="value(24UADate)" size="10%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-24UA-comments" aria-labelledby="vt-24UA-label vt-comments-label" type="text" name="value(24UAComments)" size="30%" />
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>


                                    <tr>
                                        <th class="soap" width="2%"><span id="bigA"
                                                                          style="display: none;">A</span></th>
                                        <th><textarea name="value(assessment)" cols="78"
                                                           style="height:40;display:none;"
                                                           id="assessment"></textarea>
                                        </th>
                                    </tr>


                                    <!--diag-->

                                    <tr>
                                        <th class="title" colspan="2"><span id="detailDiag"
                                                                            style="display: none;"><a
                                                href="javascript: showHideItem('diagnosis');"><fmt:message key="form.vt.diagnosis"/></a></span>
                                        </th>
                                    </tr>

                                    <tr>
                                        <td colspan="2">
                                            <table cellpadding="0" cellspacing="0" id="diagnosis"
                                                   width="100%" style="display: none;">
                                                <tr>
                                                    <td><textarea name="value(diagnosisVT)" cols="78"
                                                                       style="height:40;margin-left:10px;"></textarea></td>
                                                </tr>

                                            </table>
                                        </td>
                                    </tr>


                                    <tr>
                                        <th class="soap" width="2%"><span id="bigP"
                                                                          style="display: none;">P</span></th>
                                        <th>
                                            <table id="plan" style="display: none" cellpadding="0"
                                                   cellspacing="0">
                                                <tr>
                                                    <th><textarea name="value(plan)" cols="78"
                                                                       style="height:40;"></textarea></th>
                                                </tr>
                                            </table>
                                        </th>
                                    </tr>

                                    <tr>
                                        <th class="title" colspan="2"><span id="medLabel"
                                                                            style="display: none;"><a
                                                href="javascript: showHideItem('medications');">Medications
							>> </a></span></th>
                                    </tr>


                                    <tr>
                                        <td colspan="2">
                                            <table cellpadding='1' cellspacing='0' id="medications"
                                                   style="display: none;">
                                                <tr>
                                                    <td>
                                                        <c:if test="${not empty drugs}">
                                                            <c:forEach var="drg" items="${drugs}">
                                                                <carlos:encode value="${drg}"/><br>
                                                            </c:forEach>
                                                        </c:if>
                                                        <c:if test="${empty drugs}">
                                                            <fmt:message key="form.vt.noDrugs"/>
                                                        </c:if>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>

                                    <tr>
                                        <th class="title" colspan="2"><span id="medAllergiesLabel"
                                                                            style="display: none;"><a
                                                href="javascript: showHideItem('medicationAllergies');">Medication
							Allergies >> </a></span></th>
                                    </tr>

                                    <tr>
                                        <td colspan="2">
                                            <table cellpadding='1' cellspacing='0' id="medicationAllergies"
                                                   style="display: none;">
                                                <tr>
                                                    <td>
                                                        <c:if test="${not empty allergies}">
                                                            <c:forEach var="alg" items="${allergies}">
                                                                <carlos:encode value="${alg}"/><br>
                                                            </c:forEach>
                                                        </c:if>
                                                        <c:if test="${empty allergies}">
                                                            <fmt:message key="form.vt.noAllergies"/>
                                                        </c:if>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>


                                    <tr>
                                        <th class="title" colspan="2"><span id="CounsellingLabel"
                                                                            style="display: none;"><a
                                                href="javascript: showHideItem('counselling');">Counselling
							>></a></span></th>
                                    </tr>

                                    <tr>
                                        <td colspan="2">
                                            <table width="100%" cellpadding='1' cellspacing='0'
                                                   id="counselling" style="display: none">
                                                <tr>
                                                    <td class="subTitle" width="29%"></td>
                                                    <td class="subTitle" width="16%"><fmt:message key="form.vt.lastDataEnteredOn"/><br></td>
                                                    <td class="subTitle" width="10%">&nbsp;</td>
                                                    <td class="subTitle" width="45%"><fmt:message key='encounter.oscarMeasurements.Measurements.headingComments'/>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-NtrC-label"><carlos:encode value="${requestScope.NtrCDesc}"/></span>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.NtrCLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.NtrCLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="checkbox" name="value(NtrCValue)"/>
                                                    </td>
                                                    <input type="hidden" name="value(NtrCDate)" id="value(NtrCDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-NtrC-comments" aria-labelledby="vt-NtrC-label vt-comments-label" type="text" name="value(NtrCComments)" size="45" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-ExeC-label"><carlos:encode value="${requestScope.ExeCDesc}"/></span>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.ExeCLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.ExeCLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="checkbox" name="value(ExeCValue)"/>
                                                    </td>
                                                    <input type="hidden" name="value(ExeCDate)" id="value(ExeCDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-ExeC-comments" aria-labelledby="vt-ExeC-label vt-comments-label" type="text" name="value(ExeCComments)" size="45" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-SmCC-label"><carlos:encode value="${requestScope.SmCCDesc}"/></span>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.SmCCLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.SmCCLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="checkbox" name="value(SmCCValue)"/>
                                                    </td>
                                                    <input type="hidden" name="value(SmCCDate)" id="value(SmCCDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-SmCC-comments" aria-labelledby="vt-SmCC-label vt-comments-label" type="text" name="value(SmCCComments)" size="45" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-DiaC-label"><carlos:encode value="${requestScope.DiaCDesc}"/></span>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.DiaCLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.DiaCLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="checkbox" name="value(DiaCValue)"/>
                                                    </td>
                                                    <input type="hidden" name="value(DiaCDate)" id="value(DiaCDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-DiaC-comments" aria-labelledby="vt-DiaC-label vt-comments-label" type="text" name="value(DiaCComments)" size="45" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-PsyC-label"><carlos:encode value="${requestScope.PsyCDesc}"/></span>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.PsyCLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.PsyCLastData}"/>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="checkbox" name="value(PsyCValue)"/>
                                                    </td>
                                                    <input type="hidden" name="value(PsyCDate)" id="value(PsyCDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-PsyC-comments" aria-labelledby="vt-PsyC-label vt-comments-label" type="text" name="value(PsyCComments)" size="45" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><span id="vt-OthC-label"><carlos:encode value="${requestScope.OthCDesc}"/></span>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><carlos:encode value="${requestScope.OthCLDDate}"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><carlos:encode value="${requestScope.OthCLastData}"/>
                                                                </td>
                                                            </tr>
                                                            <input type="hidden" name="value(OthCLDDate)" id="value(OthCLDDate)"/>
                                                            <input type="hidden" name="value(OthCLastData)" id="value(OthCLastData)"/>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="checkbox" name="value(OthCValue)"/></td>
                                                    <input type="hidden" name="value(OthCDate)" id="value(OthCDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input id="vt-OthC-comments" aria-labelledby="vt-OthC-label vt-comments-label" type="text" name="value(OthCComments)" size="45"/></td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>


                                </table>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
        <table class="Head" class="hidePrint" width="640">
            <tr>
                <td align="left"><input type="hidden" name="submit" value=""/>
                    <input type="submit" value="<fmt:message key='form.vt.update'/>" onclick="javascript:onSave();"/>
                    <input type="submit" value="<fmt:message key='global.saveExit'/>"
                           onclick="javascript:return onSaveExit();"/> <input type="submit"
                                                                              value="<fmt:message key='global.btnExit'/>"
                                                                              onclick="javascript:return onExit();"/>
                    <input
                            type="button" value="<fmt:message key="global.btnPrint"/>" onclick="javascript:return onPrint();"/>
                </td>
            </tr>
        </table>
    </form>
    </body>
</html>
