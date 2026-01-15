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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_form" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_form");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<html>

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Vascular Tracker (Draft)</title>
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>

        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

    </head>

    <script type="text/javascript">

        var waist, hip, ratio;
        var smkSDate, smkHDate, smkCDate;
        var smkSCmt, smkHCmt, smkCCmt;
        var DpScId, StScId, LcCtId;
        var MedGId, MedNId, MedRId, MedAId;
        var FTExId, ftNeId, ftIsId, ftInId, ftUlId, ftOtId, ftReId;
        var FTExDate, ftNeDate, ftIsDate, ftInDate, ftUlDate, ftOtDate, ftReDate;
        var FTExCmt, ftNeCmt, ftIsCmt, ftInCmt, ftUlCmt, ftOtCmt, ftReCmt;
        var eyeExId, eyeHypId, eyeDiaId, eyeOthId, eyeRefId;
        var eyeExCmt, eyeHypCmt, eyeDiaCmt, eyeOthCmt, eyeRefCmt;
        var eyeExDate, eyeHypDate, eyeDiaDate, eyeOthDate, eyeRefDate;
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
            var ret = confirm("Are you sure you wish to save and close this window?");

            return ret;
        }

        var pageWindow = null;

        function popupDecisionSupport() {
            var varpage = "<%=request.getAttribute("decisionSupportURL")%>";
            var vheight = 760;
            var vwidth = 400;
            var posX = 400;
            var posY = 0;
            if (varpage != "null") {
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=680";
                var popup = window.open(varpage, "DecisionSupport", windowprops);
                pageWindow = popup;
                popup.focus();
            }
            /*else{
                alert("Miles server is not avaiable!");
            }*/

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
            showHideItem('detailCounselling');

            showHideItem('medications');
            showHideItem('medicationAllergies');

            showHideItem('medAllergiesLabel');
            showHideItem('medLabel');


        }

        function updateWaistHip() {
            var r = document.forms[0].elements[ratio].value;
            var w = document.forms[0].elements[waist].value;
            var h = document.forms[0].elements[hip].value;

            if (document.forms[0].elements[waist].value != "" && document.forms[0].elements[ratio].value != "" && document.forms[0].elements[waist].value != 0 && document.forms[0].elements[ratio].value != 0) {
                document.forms[0].elements[ratio].readOnly = true;
                document.forms[0].elements[hip].value = parseInt(parseInt(w) / parseFloat(r));
            } else if (document.forms[0].elements[hip].value != "" && document.forms[0].elements[ratio].value != "" && document.forms[0].elements[hip].value != 0 && document.forms[0].elements[ratio].value != 0) {
                document.forms[0].elements[ratio].readOnly = true;
                document.forms[0].elements[waist].value = parseInt(parseFloat(r) * parseInt(h));
            } else {
                document.forms[0].elements[ratio].readOnly = false;
            }
        }

        function updateWaistAndRatio() {
            var r = document.forms[0].elements[ratio].value;
            var w = document.forms[0].elements[waist].value;
            var h = document.forms[0].elements[hip].value;

            if (document.forms[0].elements[waist].value != "" && document.forms[0].elements[hip].value != "" && document.forms[0].elements[waist].value != 0 && document.forms[0].elements[hip].value != 0) {
                document.forms[0].elements[ratio].readOnly = true;
                document.forms[0].elements[ratio].value = parseInt(w) / parseInt(h);
            } else if (document.forms[0].elements[hip].value != "" && document.forms[0].elements[ratio].value != "" && document.forms[0].elements[hip].value != 0 && document.forms[0].elements[ratio].value != 0) {
                document.forms[0].elements[ratio].readOnly = false;
                document.forms[0].elements[waist].value = parseInt(parseFloat(r) * parseInt(h));
            } else {
                document.forms[0].elements[ratio].readOnly = false;
            }
        }

        function updateHipAndRatio() {
            var r = document.forms[0].elements[ratio].value;
            var w = document.forms[0].elements[waist].value;
            var h = document.forms[0].elements[hip].value;

            if (document.forms[0].elements[waist].value != "" && document.forms[0].elements[hip].value != "" && document.forms[0].elements[waist].value != 0 && document.forms[0].elements[hip].value != 0) {
                document.forms[0].elements[ratio].readOnly = true;
                document.forms[0].elements[ratio].value = parseInt(w) / parseInt(h);
            } else if (document.forms[0].elements[waist].value != "" && document.forms[0].elements[ratio].value != "" && document.forms[0].elements[waist].value != 0 && document.forms[0].elements[ratio].value != 0) {
                document.forms[0].elements[ratio].readOnly = false;
                document.forms[0].elements[hip].value = parseInt(parseInt(w) / parseFloat(r));
            } else {
                document.forms[0].elements[ratio].readOnly = false;
            }
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

            document.forms[0].elements[eyeExDate].value = document.forms[0].iDate.value;
            document.forms[0].elements[eyeHypDate].value = document.forms[0].iDate.value;
            document.forms[0].elements[eyeDiaDate].value = document.forms[0].iDate.value;
            document.forms[0].elements[eyeOthDate].value = document.forms[0].iDate.value;
            document.forms[0].elements[eyeRefDate].value = document.forms[0].iDate.value;

            document.forms[0].elements[eyeExCmt].value = document.forms[0].iComments.value;
            document.forms[0].elements[eyeHypCmt].value = document.forms[0].iComments.value;
            document.forms[0].elements[eyeDiaCmt].value = document.forms[0].iComments.value;
            document.forms[0].elements[eyeOthCmt].value = document.forms[0].iComments.value;
            document.forms[0].elements[eyeRefCmt].value = document.forms[0].iComments.value;
        }


        function controlEyeExam() {

            if (document.forms[0].elements[eyeExId].checked == false) {
                //enable all foot exam checkbox
                //alert("enable all foot exam checkboxes");
                document.forms[0].elements[eyeHypId - 1].disabled = false;
                document.forms[0].elements[eyeDiaId - 1].disabled = false;
                document.forms[0].elements[eyeOthId - 1].disabled = false;
                document.forms[0].elements[eyeRefId - 1].disabled = false;
                //document.forms[0].elements[eyeHypId].disabled= false;
                //document.forms[0].elements[eyeDiaId].disabled= false;
                //document.forms[0].elements[eyeOthId].disabled= false;
                //document.forms[0].elements[eyeRefId].disabled= false;
            } else if (document.forms[0].elements[eyeExId].checked == true) {
                //uncheck and disable all foot exam checkbox
                //alert("uncheck all foot exam checkboxes");
                document.forms[0].elements[eyeHypId - 1].checked = false;
                document.forms[0].elements[eyeDiaId - 1].checked = false;
                document.forms[0].elements[eyeOthId - 1].checked = false;
                document.forms[0].elements[eyeRefId - 1].checked = false;
                document.forms[0].elements[eyeHypId].checked = true;
                document.forms[0].elements[eyeDiaId].checked = true;
                document.forms[0].elements[eyeOthId].checked = true;
                document.forms[0].elements[eyeRefId].checked = true;

                //alert("disable all foot exam checkboxes");
                document.forms[0].elements[eyeHypId - 1].disabled = true;
                document.forms[0].elements[eyeDiaId - 1].disabled = true;
                document.forms[0].elements[eyeOthId - 1].disabled = true;
                document.forms[0].elements[eyeRefId - 1].disabled = true;
            }
        }

        function initialize() {
            var e = document.forms[0].elements;

            for (i = 0; i < e.length; i++) {
                switch (e[i].name) {
                    case 'value(WHRValue)':
                        ratio = i;
                        break;
                    case 'value(WCValue)':
                        waist = i;
                        break;
                    case 'value(HCValue)':
                        hip = i;
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

                    case 'value(iExDate)':
                        eyeExDate = i;
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

                    case 'value(iExComments)':
                        eyeExCmt = i;
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

                    case 'value(iExValue)':
                        eyeExId = i;
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
            document.forms[0].elements[eyeExId - 1].disabled = control;
            document.forms[0].elements[eyeHypId - 1].disabled = control;
            document.forms[0].elements[eyeDiaId - 1].disabled = control;
            document.forms[0].elements[eyeOthId - 1].disabled = control;
            document.forms[0].elements[eyeRefId - 1].disabled = control;

            document.forms[0].elements[eyeExId].disabled = control;
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
          onload="window.focus();window.resizeTo(680,760); popupDecisionSupport(); initialize();"
          onunload="closeDSWindow();">
    <!--  -->

    <form action="${pageContext.request.contextPath}/form/SubmitForm.do" method="post">
        <link rel="stylesheet" type="text/css"
              href="<%= request.getContextPath() %>/oscarEncounter/oscarMeasurements/styles/measurementStyle.css">
        <link rel="stylesheet" type="text/css" media="print" href="form/print.css"/>
        <input type="hidden" name="value(formName)" value="VTForm"/>
        <input type="hidden" name="value(formId)" id="value(formId)"/>
        <table class="Head" class="hidePrint" width="640px" cellpadding="0"
               cellspacing="0">
            <tr>
                <td align="left"><input type="submit" value="Update VT"
                                        onclick="javascript:onSave();"/> <input type="submit"
                                                                                value="Save and Exit"
                                                                                onclick="javascript:return onSaveExit();"/>
                    <input
                            type="submit" value="Exit" onclick="javascript:return onExit();"/>
                    <input type="button" value="Print"
                           onclick="javascript:return onPrint();"/></td>
            </tr>
        </table>
        <table width="640px" border=0 cellpadding="0" cellspacing="0">
            <tr>
                <td class="subject">Vascular Data Entry Template
                    (draft) <%=request.getAttribute("decisionSupportURL") == null ? "(Miles server is not available)" : ""%>
                </td>
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
                <li><%= error %></li>
            <% } %>
        </ul>
    </div>
<% } %>
                                    <tr>
                                        <td colspan="2">

                                            <table width="100%" border="0" cellspacing="0" cellpadding="1">
                                                <tr>
                                                    <td valign="top" width="22%">
                                                        <c:if test="${not empty EctSessionBean}">
                                                            ${EctSessionBean.patientLastName}
                                                            ${EctSessionBean.patientFirstName}
                                                            ${EctSessionBean.patientSex}
                                                            ${EctSessionBean.patientAge}
                                                        </c:if>

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
                                                                <td><%=request.getAttribute("ongoingConcerns")%>
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
                                                                <td class="subTitle" width="15%">Ob. Date<br>
                                                                    (yyyy-MM-dd)
                                                                </td>
                                                                <td class="subTitle" width="28%"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.Measurements.headingComments"/>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="dataEntryTable">Last Data</td>
                                                                <td class="dataEntryTable"><font style="font-size: 80%">
                                                                    <%=request.getAttribute("SmkSLastData")%>
                                                                </font></td>
                                                                <td class="dataEntryTable"><font style="font-size: 80%">
                                                                    <%=request.getAttribute("SmkHLastData")%>
                                                                </font></td>
                                                                <td class="dataEntryTable"><%=request.getAttribute("SmkCLastData")%>
                                                                </td>
                                                                <td class="dataEntryTable"><font style="font-size: 80%">
                                                                    <%=request.getAttribute("SmkSLDDate")%>
                                                                </font></td>
                                                                <td class="dataEntryTable">&nbsp;</td>
                                                            </tr>
                                                            <tr>
                                                                <td class="dataEntryTable">New Data</td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(SmkSValue)" size="8%" /></td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(SmkHValue)" size="8%" /></td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(SmkCValue)" size="8%" /></td>
                                                                <td class="dataEntryTable" align="center"><input
                                                                        type="text" id="SmkDate" name="SmkDate"
                                                                        value="<%=request.getAttribute("SmkSDate")%>"
                                                                        size="8%"/></td>
                                                                <td class="dataEntryTable" align="center"><input
                                                                        type="text" id="SmkComments" name="SmkComments"
                                                                        value="<%=request.getAttribute("SmkSComments")%>"
                                                                        size="25%"
                                                                        tabindex="9999"/></td>
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
                                                    <td class="subTitle" width="16%">Last Data</td>
                                                    <td class="subTitle" width="18%">New Data</td>
                                                    <td class="subTitle" width="30%"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.Measurements.headingComments"/>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><c:out value="${ExerDesc}"/>
                                                        <font class="eightyPercent"><%=request.getAttribute("ExerMeasuringInstrc")%>
                                                        </font></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("ExerLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("ExerLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(ExerValue)" size="4%" />
                                                    </td>
                                                    <input type="hidden" name="value(ExerDate)" id="value(ExerDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(ExerComments)" size="30%" tabindex="9999" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><c:out value="${DietDesc}"/>
                                                        <font class="eightyPercent"><%=request.getAttribute("DietMeasuringInstrc")%>
                                                        </font></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("DietLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("DietLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(DietValue)" size="4%" /></td>
                                                    <input type="hidden" name="value(DietDate)" id="value(DietDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(DietComments)" size="30%" tabindex="9999" /></td>
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
                                                                                           style="display: none;">Last Data</span>
                                                    </td>
                                                    <td class="subTitle" width="18%"><span id="psychHead3"
                                                                                           style="display: none;">New Data</span>
                                                    </td>
                                                    <td class="subTitle" width="30%"><span id="psychHead4"
                                                                                           style="display: none;"> <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.Measurements.headingComments"/>
									</span></td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("DpScDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("DpScLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("DpScLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="radio" name="value(DpScValue)" value="yes"/>Yes
                                                        <input type="radio" name="="value(DpScValue)" value="no"/>No
                                                        <a
                                                            title="clear all"
                                                            href="javascript:clearAll(DpScId, DpScId-1)">&nbsp
                                                        <font style="text-decoration: underline; color: blue;">clr</font>
                                                    </a></td>
                                                    <input type="hidden" name="value(DpScDate)" id="value(DpScDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(DpScComments)" size="30%" tabindex="9999" /></td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("StScDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("StScLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("StScLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="radio" name="value(StScValue)" value="yes"/>Yes
                                                        <input type="radio" name="value(StScValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(StScId, StScId-1)">&nbsp
                                                        <font style="text-decoration: underline; color: blue;">clr</font>
                                                    </a></td>
                                                    <input type="hidden" name="value(StScDate)" id="value(StScDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(StScComments)" size="30%" tabindex="9999" /></td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("LcCtDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("LcCtLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("LcCtLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(LcCtValue)" value="yes"/>Yes
                                                        <input type="radio" name="value(LcCtValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(LcCtId, LcCtId-1)">&nbsp
                                                        <font style="text-decoration: underline; color: blue;">clr</font>
                                                    </a></td>
                                                    <input type="hidden" name="value(LcCtDate)" id="value(LcCtDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(LcCtComments)" size="30%" tabindex="9999" /></td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>


                                    <tr>
                                        <td colspan="2">
                                            <table cellpadding='1' cellspacing='0' width="100%" border="1">
                                                <tr>
                                                    <td class="subTitle" width="36%" style="text-align: left">
									<span id="medHead1" style="display: none;">Medication
									Adherence screen</span></td>
                                                    <td class="subTitle" width="16%"><span id="medHead2"
                                                                                           style="display: none;">Last Data</span>
                                                    </td>
                                                    <td class="subTitle" width="18%"><span id="medHead3"
                                                                                           style="display: none;">New Data</span>
                                                    </td>
                                                    <td class="subTitle" width="30%"><span id="medHead4"
                                                                                           style="display: none;"> <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.Measurements.headingComments"/>
									</span></td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("MedGDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("MedGLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("MedGLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(MedGValue)" value="yes"/>Yes
                                                        <input type="radio" name="value(MedGValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(MedGId, MedGId-1)">&nbsp
                                                        <font style="text-decoration: underline; color: blue;">clr</font>
                                                    </a></td>
                                                    <input type="hidden" name="value(MedGDate)" id="value(MedGDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(MedGComments)" size="30%" tabindex="9999" /></td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("MedNDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("MedNLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("MedNLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(MedNValue)" value="yes"/>Yes
                                                        <input type="radio" name="value(MedNValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(MedNId, MedNId-1)">&nbsp
                                                        <font style="text-decoration: underline; color: blue;">clr</font>
                                                    </a></td>
                                                    <input type="hidden" name="value(MedNDate)" id="value(MedNDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(MedNComments)" size="30%" tabindex="9999" /></td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("MedRDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("MedRLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("MedRLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(MedRValue)" value="yes"/>Yes
                                                        <input type="radio" name="value(MedRValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(MedRId, MedRId-1)">&nbsp
                                                        <font style="text-decoration: underline; color: blue;">clr</font>
                                                    </a></td>
                                                    <input type="hidden" name="value(MedRDate)" id="value(MedRDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(MedRComments)" size="30%" tabindex="9999" /></td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("MedADesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("MedALDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("MedALastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="radio" name="value(MedAValue)" value="yes"/>Yes
                                                        <input type="radio" name="value(MedAValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(MedAId, MedAId-1)">&nbsp
                                                        <font style="text-decoration: underline; color: blue;">clr</font>
                                                    </a></td>
                                                    <input type="hidden" name="value(MedADate)" id="value(MedADate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(MedAComments)" size="30%" abindex="9999"/></td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>

                                    <tr>
                                        <th class="soap" width="2%"><span id="bigO"
                                                                          style="display: none;">O</span></th>
                                        <th>
                                            <table id="objective" style="display: none">
                                                <tr>
                                                    <th><textarea name="value(objective)" cols="78"
                                                                       style="height:40"></textarea></th>
                                                </tr>
                                            </table>
                                        </th>
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
                                                    <td class="subTitle" width="36%" style="text-align: left">
                                                    </td>
                                                    <td class="subTitle" width="16%"><span id="vitalHead1"
                                                                                           style="display: none;">Last Data<br>
									Entered on</span></td>
                                                    <td class="subTitle" width="18%"><span id="vitalHead2"
                                                                                           style="display: none;">New Data</span>
                                                    </td>
                                                    <td class="subTitle" width="30%"><span id="vitalHead3"
                                                                                           style="display: none;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.Measurements.headingComments"/></span>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("BPDisplay")%>
                                                        <br>
                                                        <font class="eightyPercent"><%=request.getAttribute("BPMeasuringInstrc")%>
                                                        </font></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("BPLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("BPLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(BPValue)" size="5%" /></td>
                                                    <input type="hidden" name="value(BPDate)" id="value(BPDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(BPComments)" size="30%" tabindex="9999" /></td>
                                                </tr>


                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("WHRBDisplay")%>
                                                        <br>
                                                        <font class="eightyPercent"><%=request.getAttribute("WHRBMeasuringInstrc")%>
                                                        </font></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <c:if test="${not empty WHRBLastData}">
                                                            <table cellpadding="0" cellspacing="0">
                                                                <tr>
                                                                    <td class="eightyPercent" align="left">${WHRBLDDate}</td>
                                                                </tr>
                                                                <tr>
                                                                    <td class="eightyPercent" align="right">${WHRBLastData}</td>
                                                                </tr>
                                                            </table>
                                                        </c:if>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="radio" name="value(WHRBValue)" value="yes"/>Yes
                                                        <input type="radio" name="value(WHRBValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(MedAId, MedAId-1)">&nbsp
                                                        <font style="text-decoration: underline; color: blue;">clr</font>
                                                    </a></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(WHRBComments)" size="30%" tabindex="9999" /></td>
                                                    </tr-->


                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("WCDisplay")%>
                                                        <br>
                                                        <font class="eightyPercent"><%=request.getAttribute("WCMeasuringInstrc")%>
                                                        </font></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <c:if test="${not empty WCLastData}">
                                                            <table cellpadding="0" cellspacing="0">
                                                                <tr>
                                                                    <td class="eightyPercent" align="left">${WCLDDate}</td>
                                                                </tr>
                                                                <tr>
                                                                    <td class="eightyPercent" align="right">${WCLastData}</td>
                                                                </tr>
                                                            </table>
                                                        </c:if>

                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(WCValue)" size="5%" onchange="javascript: updateHipAndRatio();"/>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(WCComments)" size="30%" tabindex="9999" /></td>
                                                </tr>
                                                <!--new-->
                                                <tr>
                                                    <td width="36%"
                                                        class="dataEntryTable"><%=request.getAttribute("HCDisplay")%>
                                                        <br>
                                                        <font class="eightyPercent"><%=request.getAttribute("HCMeasuringInstrc")%>
                                                        </font></td>
                                                    <td width="16%" class="dataEntryTable" align="center">
                                                        <c:if test="${not empty HCLastData}">
                                                            <table cellpadding="0" cellspacing="0">
                                                                <tr>
                                                                    <td class="eightyPercent" align="left">${HCLDDate}</td>
                                                                </tr>
                                                                <tr>
                                                                    <td class="eightyPercent" align="right">${HCLastData}</td>
                                                                </tr>
                                                            </table>
                                                        </c:if>
                                                    </td>
                                                    <td width="18%" class="dataEntryTable" align="center">
                                                        <input type="text" name="value(HCValue)" size="5%" onchange="javascript: updateWaistAndRatio();"/>
                                                    </td>
                                                    <td width="33%" class="dataEntryTable" align="center">
                                                        <input type="text" name="value(HCComments)" size="30%" tabindex="9999"/>
                                                    </td>
                                                </tr>

                                                <tr>
                                                    <td colspan="5">
                                                        <table cellpadding="1" cellspacing="0" width="100%"
                                                               style="display: none" id="detailVital">

                                                            <tr>
                                                                <td class="dataEntryTable"><%=request.getAttribute("HRDisplay")%>
                                                                    <br>
                                                                    <font class="eightyPercent"><%=request.getAttribute("HRMeasuringInstrc")%>
                                                                    </font></td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <table cellpadding='0' cellspacing='0'>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="left"><%=request.getAttribute("HRLDDate")%>
                                                                            </td>
                                                                        </tr>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="right"><%=request.getAttribute("HRLastData")%>
                                                                            </td>
                                                                        </tr>
                                                                    </table>
                                                                </td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(HRValue)" size="5%" />
                                                                </td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(HRComments)" size="30%" tabindex="9999" />
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="dataEntryTable"><%=request.getAttribute("HTDisplay")%>
                                                                    <br>
                                                                    <font class="eightyPercent"><%=request.getAttribute("HTMeasuringInstrc")%>
                                                                    </font></td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <table cellpadding='0' cellspacing='0'>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="left"><%=request.getAttribute("HTLDDate")%>
                                                                            </td>
                                                                        </tr>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="right"><%=request.getAttribute("HTLastData")%>
                                                                            </td>
                                                                        </tr>
                                                                    </table>
                                                                </td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(HTValue)" size="5%" />
                                                                </td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(HTComments)" size="30%" tabindex="9999" />
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("WTDisplay")%>
                                                        <br>
                                                        <font class="eightyPercent"><%=request.getAttribute("WTMeasuringInstrc")%>
                                                        </font></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <c:if test="${not empty WTLastData}">
                                                            <table cellpadding="0" cellspacing="0">
                                                                <tr>
                                                                    <td class="eightyPercent" align="left">${WTLDDate}</td>
                                                                </tr>
                                                                <tr>
                                                                    <td class="eightyPercent" align="right">${WTLastData}</td>
                                                                </tr>
                                                            </table>
                                                        </c:if>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(WTValue)" size="5%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(WTComments)" size="30%" tabindex="9999" />
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
                                                    <td class="subTitle" width="17%">Last Data<br>
                                                        Entered on
                                                    </td>
                                                    <td class="subTitle" width="22%">New Data</td>
                                                    <td class="subTitle" width="10%">Ob. Date<br>
                                                        (yyyy-MM-dd)
                                                    </td>
                                                    <td class="subTitle" width="23%"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.Measurements.headingComments"/></td>
                                                </tr>
                                                <tr>
                                                    <th class="dataEntryTable" colspan="3">Foot Exam</th>
                                                    <td class="dataEntryTable" valign="top" align="center"><input
                                                            type="text" id="FTDate" name="FTDate"
                                                            value="<%=request.getAttribute("FTNeDate")%>" size="10%"
                                                            tabindex="9999"/></td>
                                                    <td class="dataEntryTable" rowspan="8" valign="top"
                                                        align="center"><textarea name="FTComments" wrap="hard"
                                                                                 cols="24" style="height: 80"
                                                                                 tabindex="9999">
                                                        <c:if test="${not empty FTExComments}">
                                                            ${FTExComments}
                                                        </c:if>
                                                    </textarea>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("FTExDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("FTExLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("FTExLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(FTExValue)"
                                                            onclick="javascript:controlFTExam();" value="yes"/>Yes
                                                        <input type="radio" name="="value(FTExValue)"
                                                                onclick="javascript:controlFTExam();" value="no"/>No <a
                                                                title="clear all"
                                                                href="javascript:clearAll(FTExId, FTExId-1)">&nbsp
                                                            <font style="text-decoration: underline; color: blue;">clr</font>
                                                        </a></td>
                                                    <td class="dataEntryTable" rowspan="7" valign="top"
                                                        align="center">&nbsp;
                                                    </td>
                                                    <input type="hidden" name="value(FTExDate)" id="value(FTExDate)"/>
                                                    <input type="hidden" name="value(FTExComments)" id="value(FTExComments)"/>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("FTNeDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("FTNeLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("FTNeLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(FTNeValue)" value="yes"/>Yes <input type="radio" name="value(FTNeValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(ftNeId, ftNeId-1)">&nbsp
                                                        <font style="text-decoration: underline; color: blue;">clr</font>
                                                    </a></td>
                                                    <input type="hidden" name="value(FTNeDate)" id="value(FTNeDate)"/>
                                                    <input type="hidden" name="value(FTNeComments)" id="value(FTNeComments)"/>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("FTIsDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("FTIsLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("FTIsLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(FTIsValue)" value="yes"/>Yes <input type="radio" name="value(FTIsValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(ftIsId, ftIsId-1)">&nbsp
                                                        <font style="text-decoration: underline; color: blue;">clr</font>
                                                    </a></td>
                                                    <input type="hidden" name="value(FTIsDate)" id="value(FTIsDate)"/>
                                                    <input type="hidden" name="value(FTIsComments)" id="value(FTIsComments)"/>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("FTUlDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("FTUlLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("FTUlLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(FTUlValue)" value="yes"/>Yes <input type="radio" name="value(FTUlValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(ftUlId, ftUlId-1)">&nbsp
                                                        <font style="text-decoration: underline; color: blue;">clr</font>
                                                    </a></td>
                                                    <input type="hidden" name="value(FTUlDate)" id="value(FTUlDate)"/>
                                                    <input type="hidden" name="value(FTUlComments)" id="value(FTUlComments)"/>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("FTInDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("FTInLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("FTInLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(FTInValue)" value="yes"/>Yes <input type="radio" name="value(FTInValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(ftInId, ftInId-1)">&nbsp
                                                        <font style="text-decoration: underline; color: blue;">clr</font>
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
                                                                    class="dataEntryTable"><%=request.getAttribute("FTOtDesc")%>
                                                                </td>
                                                                <td width="16%" class="dataEntryTable" align="center">
                                                                    <table cellpadding='0' cellspacing='0'>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="left"><%=request.getAttribute("FTOtLDDate")%>
                                                                            </td>
                                                                        </tr>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="right"><%=request.getAttribute("FTOtLastData")%>
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
                                                                        <font
                                                                                style="text-decoration: underline; color: blue;">clr</font>
                                                                    </a></td>
                                                                <input type="hidden" name="value(FTOtDate)" id="value(FTOtDate)"/>
                                                                <input type="hidden" name="value(FTOtComments)" id="value(FTOtComments)"/>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("FTReDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("FTReLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("FTReLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(FTReValue)" value="yes"/>Yes <input type="radio" name="value(FTReValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(ftReId, ftReId-1)">&nbsp
                                                        <font style="text-decoration: underline; color: blue;">clr</font>
                                                    </a></td>
                                                    <input type="hidden" name="value(FTReDate)" id="value(FTReDate)"/>
                                                    <input type="hidden" name="value(FTReComments)" id="value(FTReComments)"/>
                                                </tr>
                                            </table>
                                            <table cellpadding='0' cellspacing='0' border="1" width="100%"
                                                   id="examinationEye">
                                                <tr>
                                                    <td class="subTitle" width="28%">&nbsp;</td>
                                                    <td class="subTitle" width="17%">Last Data<br>
                                                        Entered on
                                                    </td>
                                                    <td class="subTitle" width="22%">New Data</td>
                                                    <td class="subTitle" width="10%">Ob. Date<br>
                                                        (yyyy-MM-dd)
                                                    </td>
                                                    <td class="subTitle" width="23%"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.Measurements.headingComments"/></td>
                                                </tr>
                                                <tr>
                                                    <th class="dataEntryTable" colspan="3">Eye Exam</th>
                                                    <td class="dataEntryTable" valign="top" align="center"><input
                                                            type="text" name="iDate"
                                                            value="<%=request.getAttribute("iDiaDate")%>" size="10%"/>
                                                    </td>
                                                    <td class="dataEntryTable" rowspan="8" valign="top"
                                                        align="center"><textarea name="iComments" wrap="hard"
                                                                                 cols="24"
                                                                                 style="height: 80">
                                                        <c:if test="${not empty iExComments}">
                                                        ${iExComments}
                                                        </c:if>
                                                        </textarea>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("iDiaDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("iDiaLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("iDiaLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(iDiaValue)" value="yes"/>Yes <input type="radio" name="value(iDiaValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(eyeDiaId, eyeDiaId-1)">&nbsp <font
                                                            style="text-decoration: underline; color: blue;">clr</font>
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
                                                                    class="dataEntryTable"><%=request.getAttribute("iHypDesc")%>
                                                                </td>
                                                                <td width="16%" class="dataEntryTable" align="center">
                                                                    <table cellpadding='0' cellspacing='0'>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="left"><%=request.getAttribute("iHypLDDate")%>
                                                                            </td>
                                                                        </tr>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="right"><%=request.getAttribute("iHypLastData")%>
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
                                                                        <font
                                                                                style="text-decoration: underline; color: blue;">clr</font>
                                                                    </a></td>
                                                                <input type="hidden" name="value(iHypDate)" id="value(iHypDate)"/>
                                                                <input type="hidden" name="value(iHypComments)" id="value(iHypComments)"/>
                                                            </tr>
                                                            <tr>
                                                                <td class="dataEntryTable"><%=request.getAttribute("iOthDesc")%>
                                                                </td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <table cellpadding='0' cellspacing='0'>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="left"><%=request.getAttribute("iOthLDDate")%>
                                                                            </td>
                                                                        </tr>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="right"><%=request.getAttribute("iOthLastData")%>
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
                                                                        <font
                                                                                style="text-decoration: underline; color: blue;">clr</font>
                                                                    </a></td>
                                                                <input type="hidden" name="value(iOthDate)" id="value(iOthDate)"/>
                                                                <input type="hidden" name="value(iOthComments)" id="value(iOthComments)"/>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><c:out value="${iRefDesc}"/></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("iRefLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("iRefLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center"><input type="radio" name="value(iRefValue)" value="yes"/>Yes <input type="radio" name="value(iRefValue)" value="no"/>No <a
                                                            title="clear all"
                                                            href="javascript:clearAll(eyeRefId, eyeRefId-1)">&nbsp <font
                                                            style="text-decoration: underline; color: blue;">clr</font>
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
                                                    <td class="subTitle" width="16%">Last Data<br>
                                                        Entered on
                                                    </td>
                                                    <td class="subTitle" width="5%">New Data</td>
                                                    <td class="subTitle" width="10%">Ob. Date<br>
                                                        (yyyy-MM-dd)
                                                    </td>
                                                    <td class="subTitle" width="35%"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.Measurements.headingComments"/>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("HbA1Desc")%><br>
                                                        <font class="eightyPercent"><%=request.getAttribute("HbA1MeasuringInstrc")%>
                                                        </font></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("HbA1LDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("HbA1LastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(HbA1Value)" size="4%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(HbA1Date)" size="10%" tabindex="9999" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(HbA1Comments)" size="30%" tabindex="9999" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("LDLDesc")%><br>
                                                        <font class="eightyPercent"><%=request.getAttribute("LDLMeasuringInstrc")%>
                                                        </font></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("LDLLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("LDLLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(LDLValue)" size="4%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(LDLDate)" size="10%" tabindex="9999" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(LDLComments)" size="30%" tabindex="9999" /></td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("HDLDesc")%><br>
                                                        <font class="eightyPercent"><%=request.getAttribute("HDLMeasuringInstrc")%>
                                                        </font></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("HDLLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("HDLLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(HDLValue)" size="4%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(HDLDate)" size="10%" tabindex="9999" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(HDLComments)" size="30%" tabindex="9999" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("TCHLDesc")%><br>
                                                        <font class="eightyPercent"><%=request.getAttribute("TCHLMeasuringInstrc")%>
                                                        </font></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("TCHLLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("TCHLLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(TCHLValue)" size="4%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(TCHLDate)" size="10%" tabindex="9999" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(TCHLComments)" size="30%" tabindex="9999" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td colspan='5'>
                                                        <table cellpadding="0" cellspacing="0" width="100%"
                                                               style="display: none" id="detailLab">
                                                            <tr>
                                                                <td width="34%"
                                                                    class="dataEntryTable"><%=request.getAttribute("TRIGDesc")%>
                                                                    <br>
                                                                    <font class="eightyPercent"><%=request.getAttribute("TRIGMeasuringInstrc")%>
                                                                    </font></td>
                                                                <td width="16%" class="dataEntryTable" align="center">
                                                                    <table cellpadding='0' cellspacing='0'>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="left"><%=request.getAttribute("TRIGLDDate")%>
                                                                            </td>
                                                                        </tr>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="right"><%=request.getAttribute("TRIGLastData")%>
                                                                            </td>
                                                                        </tr>
                                                                    </table>
                                                                </td>
                                                                <td width="5%" class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(TRIGValue)" size="4%"/></td>
                                                                <td width="10%" class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(TRIGDate)" size="10%"
                                                                            tabindex="9999"/></td>
                                                                <td width="35%" class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(TRIGComments)" size="30%"
                                                                            tabindex="9999"/></td>
                                                            </tr>
                                                            <tr>
                                                                <td class="dataEntryTable"><%=request.getAttribute("BGDesc")%>
                                                                    <br>
                                                                    <font class="eightyPercent"><%=request.getAttribute("BGMeasuringInstrc")%>
                                                                    </font></td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <table cellpadding='0' cellspacing='0'>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="left"><%=request.getAttribute("BGLDDate")%>
                                                                            </td>
                                                                        </tr>
                                                                        <tr>
                                                                            <td class="eightyPercent"
                                                                                align="right"><%=request.getAttribute("BGLastData")%>
                                                                            </td>
                                                                        </tr>
                                                                    </table>
                                                                </td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(BGValue)" size="4%" />
                                                                </td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(BGDate)" size="10%" tabindex="9999" />
                                                                </td>
                                                                <td class="dataEntryTable" align="center">
                                                                    <input type="text" name="value(BGComments)" size="30%" tabindex="9999" />
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("UALBDesc")%><br>
                                                        <font class="eightyPercent"><%=request.getAttribute("UALBMeasuringInstrc")%>
                                                        </font></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("UALBLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("UALBLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(UALBValue)" size="4%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(UALBDate)" size="10%" tabindex="9999" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(UALBComments)" size="30%" tabindex="9999" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("24UADesc")%><br>
                                                        <font class="eightyPercent"><%=request.getAttribute("24UAMeasuringInstrc")%>
                                                        </font></td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("24UALDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("24UALastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(24UAValue)" size="4%" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(24UADate)" size="10%" tabindex="9999" />
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(24UAComments)" size="30%" tabindex="9999" />
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
                                                href="javascript: showHideItem('diagnosis');">Diagnosis >></a></span>
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
                                                                ${drg}<br>
                                                            </c:forEach>
                                                        </c:if>
                                                        <c:if test="${empty drugs}">
                                                            No Drug in the current drug profile
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
                                                                ${alg}<br>
                                                            </c:forEach>
                                                        </c:if>
                                                        <c:if test="${empty allergies}">
                                                            No Allergies in the current drug profile
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
                                                    <td class="subTitle" width="16%">Last Data<br>
                                                        Entered on
                                                    </td>
                                                    <td class="subTitle" width="10%">&nbsp;</td>
                                                    <td class="subTitle" width="45%"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarMeasurements.Measurements.headingComments"/>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("NtrCDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("NtrCLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("NtrCLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="checkbox" name="value(NtrCValue)"/>
                                                    </td>
                                                    <input type="hidden" name="value(NtrCDate)" id="value(NtrCDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(NtrCComments)" size="45" tabindex="9999" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("ExeCDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("ExeCLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("ExeCLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="checkbox" name="value(ExeCValue)"/>
                                                    </td>
                                                    <input type="hidden" name="value(ExeCDate)" id="value(ExeCDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(ExeCComments)" size="45" tabindex="9999" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("SmCCDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("SmCCLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("SmCCLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="checkbox" name="value(SmCCValue)"/>
                                                    </td>
                                                    <input type="hidden" name="value(SmCCDate)" id="value(SmCCDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(SmCCComments)" size="45" tabindex="9999" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("DiaCDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("DiaCLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("DiaCLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="checkbox" name="value(DiaCValue)"/>
                                                    </td>
                                                    <input type="hidden" name="value(DiaCDate)" id="value(DiaCDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(DiaCComments)" size="45" tabindex="9999" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("PsyCDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("PsyCLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("PsyCLastData")%>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="checkbox" name="value(PsyCValue)"/>
                                                    </td>
                                                    <input type="hidden" name="value(PsyCDate)" id="value(PsyCDate)"/>
                                                    <td class="dataEntryTable" align="center">
                                                        <input type="text" name="value(PsyCComments)" size="45" tabindex="9999" />
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="dataEntryTable"><%=request.getAttribute("OthCDesc")%>
                                                    </td>
                                                    <td class="dataEntryTable" align="center">
                                                        <table cellpadding='0' cellspacing='0'>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="left"><%=request.getAttribute("OthCLDDate")%>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td class="eightyPercent"
                                                                    align="right"><%=request.getAttribute("OthCLastData")%>
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
                                                        <input type="text" name="value(OthCComments)" size="45" tabindex="9999"/></td>
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
                    <input type="submit" value="Update VT" onclick="javascript:onSave();"/>
                    <input type="submit" value="Save and Exit"
                           onclick="javascript:return onSaveExit();"/> <input type="submit"
                                                                              value="Exit"
                                                                              onclick="javascript:return onExit();"/>
                    <input
                            type="button" value="Print" onclick="javascript:return onPrint();"/>
                </td>
            </tr>
        </table>
    </form>
    </body>
</html>
