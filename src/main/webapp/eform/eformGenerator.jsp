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
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin.eform" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin.eform");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%@ page import="java.lang.*" %>
<%@ page import="java.io.*" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.*" %>
<%@ page import="ca.openosp.openo.eform.*" %>
<%@ page import="ca.openosp.openo.eform.data.*" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%@ page import="org.apache.logging.log4j.Logger" %>
<!--
eForm Generator version 7.4 (C) Peter Hutten-Czapski 2014-2023
based on the origional stand alone eform generator by Shelter Lee
version notes
7.5 Updated jQuery to refer to internal 3.6.4 failover to 1.12.3
7.4 Removed inline CSS if its default can be specified for the page
7.3 upgraded jQuery support to current
7.2 reverted default width to 750
7.1 bug fixes
7.0 Tickler support, refactored signatures to simplify, bug fixes
6.5 further adjusted defaults for width to 825
6.4 adjusted defaults for width to 825
6.3 selection for snappiness
added stamps.js as central location for stamps
6.2 file selector for images
revert changed relative to absolute positions for page ids
6.1 added some snap to form elements
This generator has had numerous contributions from the OSCAR community
There have been too many ideas and suggestions to be able to list them all but I would like to note
Darius
Charlie Livingston
Adrian Starzynski

released under
AGPL v2+
and other liscences (MIT, LGPL etc) as indicated
-->

<%@page import="ca.openosp.openo.utility.MiscUtils" %>
<%@ page import="ca.openosp.openo.eform.EFormLoader" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%
    boolean eformGeneratorIndivicaPrintEnabled = OscarProperties.getInstance().isPropertyActive("eform_generator_indivica_print_enabled");
    boolean eformGeneratorIndivicaFaxEnabled = OscarProperties.getInstance().isPropertyActive("eform_generator_indivica_fax_enabled");
    boolean eformGeneratorIndivicaSignatureEnabled = OscarProperties.getInstance().isPropertyActive("eform_generator_indivica_signature_enabled");
%>
<html>
<head>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.title"/></title>
    <script src="<%=request.getContextPath()%>/csrfguard" type="text/javascript"></script>

    <style type="text/css" media="print">
        /*CSS Script that removes the whole division when printing*/
        .DoNotPrint {
            display: none;
        }

        /*CSS Script that removes textarea and textbox borders when printing*/
        .noborder {
            border: 0;
            background: transparent;
            overflow: hidden;
        }
    </style>
    <style>
        span.h1 {
            font-family: sans-serif;
            font-size: 14px;
            font-weight: bolder;
        }

        span.h2 {
            font-family: sans-serif;
            font-size: 12px;
            font-weight: bold;
            text-decoration: underline;
        }

        span.h3 {
            font-family: sans-serif;
            font-size: 12px;
            font-weight: bold;
        }

        span.h4 {
            font-family: sans-serif;
            font-size: 12px;
            font-weight: normal;
            text-decoration: underline;
        }

        p, li, span {
            font-family: sans-serif;
            font-size: 12px;
            font-weight: normal;
        }

        a {
            font-family: sans-serif;
            font-size: 12px;
            font-weight: normal;
            color: blue;
            cursor: pointer;
        }
    </style>
    <script type='text/javascript'>
        /**
         * Enable nudging with arrow keys
         * - Binds to arrow keys to nudge and resize fields
         * - Shift as a modifier switches from move to resize mode
         * - Alt as a modifier changes the step size from 1px to 10px
         *
         *
         * Written By Charlie Livingston <charlie@litsolutions.ca>
         */
        document.onkeydown = function (evt) {
            evt = (evt) ? evt : ((window.event) ? event : null);
            var myReturn;
            if (evt) {
                if (evt.altKey) {
                    step = 10;
                } else {
                    step = 1;
                }
                switch (evt.keyCode) {
                    case 37:
                        if (evt.shiftKey) {
                            changeInput('width', -step);
                        } else {
                            changeInput('left', step);
                        }
                        myReturn = false;
                        break;
                    case 38:
                        if (evt.shiftKey) {
                            changeInput('height', -step);
                        } else {
                            changeInput('up', step);
                        }
                        myReturn = false;
                        break;
                    case 39:
                        if (evt.shiftKey) {
                            changeInput('width', step);
                        } else {
                            changeInput('right', step);
                        }
                        myReturn = false;
                        break;
                    case 40:
                        if (evt.shiftKey) {
                            changeInput('height', step);
                        } else {
                            changeInput('down', step);
                        }
                        myReturn = false;
                        break;
                    default:
                        myReturn = true;
                        break;
                }
            }
            return myReturn;
        }
    </script>
    <script type="text/javascript">

        var BGWidth = 0;
        var BGHeight = 0;
        var PageNum = 0;
        var PageIterate = 1;
        var sigint = 0;
        var pageoffset = 0;
        var snap = 5;
        var boxleft = 0;
        var boxup = 0;
        var boxbottom = 0;
        var boxwidth = 0;
        var boxheight = 0;
        var file_selected = false;

        var SignatureHolderX = 0;
        var SignatureHolderY = 0;
        var sigOffset = 0;
        var SignatureHolderH = 0;
        var SignatureHolderW = 0;
        var SignatureHolderP = 0;
        var SignatureColor = "Black";
        var SignatureLineColor = "rgba(255, 255, 255, 0.0)";
        var SignatureBorder = "2px dotted blue";
        var parentcounter = 0;

        var calendars = false;

        function isValid(str) {
            return /^\w+$/.test(str);
        }

        function getCheckedValue(radioObj) {
            if (!radioObj)
                return "";
            var radioLength = radioObj.length;
            if (radioLength == undefined)
                if (radioObj.checked)
                    return String(radioObj.value);
                else
                    return String(value);
            for (var k = 0; k < radioLength; k++) {
                if (radioObj[k].checked) {
                    return String(radioObj[k].value);
                }
            }
            return "";
        }

        function loadImage() {
            var img = document.getElementById('imageName');
            var bg = document.getElementById('BGImage');
            if (img.value == "") {
                return;
            }
            if (bg.src.indexOf(img.value) > 0) {
                var r = confirm('<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.loadFileAgain"/> ' + img.value + ' <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.Again"/>');
                if (r != true) {
                    return;
                }
            }
            //Boilerplate mod to set the path for image function
            bg.src = ("<%=request.getContextPath()%>" + "/eform/displayImage.do?imagefile=" + img.value);
            PageNum = PageNum + 1;

            DrawPage(jg, PageNum, img.value, bg.width)
            document.getElementById('page').value = PageNum;
            document.getElementById('AutoNamePrefix').value = "page" + PageNum + "_";

            loadInputList();
        }

        function finishLoadingImage() {

            var img = document.getElementById('imageName');
            var myCnv = document.getElementById('myCanvas');
            var bg = document.getElementById('BGImage');

            document.getElementById('OrientCustom').value = document.getElementById('OrientCustomValue').value;
            BGWidth = parseInt(getCheckedValue(document.getElementsByName('Orientation')));
            bg.width = BGWidth;
            if (PageNum > 0) {
                pageoffset += bg.height;  //the current one
            }
            //alert("Page"+PageNum+"\nPageoffset"+pageoffset+"\nBGHeight"+BGHeight+"\nbg.height"+bg.height+"\nPageNum"+PageNum);
            BGHeight = bg.height;

            document.getElementById('Wizard').style.left = BGWidth;

            myCnv.style.top = bg.style.top;
            myCnv.style.left = bg.style.left;
            myCnv.width = bg.width;
            myCnv.height = bg.height;

            jg.clear();
            drawPageOutline();

        }

        function drawPageOutline() {
            if (BGWidth <= 850) {
                drawPortraitOutline();
            } else if (BGWidth > 850) {
                drawLandscapeOutline();
            }
        }

        function show(x) {
            //expands all if x=all
            if (x == 'all') {
                show('Section1');
                show('Section2');
                show('Section3');
                show('Section4');
                show('Section5');
                show('Section6');
                show('Section7');
                show('Section8');
            } else {
                //expands section
                document.getElementById(x).style.display = 'block';
            }
        }

        function hide(x) {
            //collapse all if x=all
            if (x == 'all') {
                hide('Section1');
                hide('Section2');
                hide('Section3');
                hide('Section4');
                hide('Section5');
                hide('Section6');
                hide('Section7');
                hide('Section8');
            } else {
                //collapses section
                document.getElementById(x).style.display = 'none';
            }
        }

        function toggleView(checked, x) {
            if (checked) {
                document.getElementById(x).style.display = 'block'
            } else if (!checked) {
                document.getElementById(x).style.display = 'none';
            }
        }

        function loadInputList() {
            //load checklist of all eform input fields

            var InputList = document.getElementById('InputList');
            //empty InputList
            while (InputList.childNodes.length > 0) {
                InputList.removeChild(InputList.lastChild);
            }
            //assign input name into variable 'InputName'
            TempData = DrawData;
            for (var j = 0; (j < (TempData.length)); j++) {
                var RedrawParameter = TempData[j].split("|");
                var InputType = RedrawParameter[0]
                var InputName = "";

                if (InputType == 'Text') {
                    InputName = new String(RedrawParameter[5]);
                } else if (InputType == 'Textbox') {
                    InputName = new String(RedrawParameter[5]);
                } else if (InputType == 'Checkbox') {
                    InputName = new String(RedrawParameter[3]);
                } else if (InputType == 'Xbox') {
                    InputName = new String(RedrawParameter[5]);
                } else if (InputType == 'Page') {
                    InputName = "--Page--";
                } else if (InputType == 'Signature') {
                    InputName = new String(RedrawParameter[5]);
                } else if (InputType == 'Stamp') {
                    InputName = new String(RedrawParameter[5]);
                }
                //adds InputName as list item in InputList
                var ListItem = document.createElement("li");
                // Fix XSS vulnerability: Create checkbox element properly instead of using innerHTML
                var checkbox = document.createElement("input");
                checkbox.type = "checkbox";
                checkbox.name = "InputChecklist";
                checkbox.id = InputName;
                checkbox.value = InputName;
                ListItem.appendChild(checkbox);
                // Add the label text safely using createTextNode
                ListItem.appendChild(document.createTextNode(InputName));
                InputList.appendChild(ListItem);
            }
            //if (document.getElementById('AddSignatureClassic').checked){
            //	ListItem = document.createElement("li");
            //	ListItem.innerHTML = "<input name='InputChecklist' type='checkbox' id='SignatureBox' value ='SignatureBox'>SignatureBox";
            //	InputList.appendChild(ListItem);
            //}
        }

        function addToUserSignatureList() {
            var UserSignatureList = document.getElementById('UserSignatureList');	//adds User Name and Signature Image Filename to UserSignature List, separated by '|'
            var UserName = document.getElementById('UserList').value;
            var FileName = document.getElementById('SignatureList').value;

            var ListItem = document.createElement("li");
            ListItem.setAttribute('name', 'UserSignatureListItem');
            var UserSignature = UserName + '|' + FileName;
            // Fix XSS vulnerability: Use textContent instead of innerHTML to prevent HTML injection
            ListItem.textContent = UserSignature;
            UserSignatureList.appendChild(ListItem);
        }

        function emptyUserSignaturelist() {
            var UserSignatureList = document.getElementById('UserSignatureList');	//Empty UserSignature List
            //empty UserSignatureList
            while (UserSignatureList.childNodes.length > 0) {
                UserSignatureList.removeChild(UserSignatureList.lastChild);
            }
        }

        function uncheckList(x) {
            var List = document.getElementsByName(x);
            for (i = 0; i < List.length; i++) {
                List[i].checked = false;
            }
        }

        function checkList(x) {
            var List = document.getElementsByName(x);
            for (i = 0; i < List.length; i++) {
                List[i].checked = true;
            }
        }

        function changeInput(d, p) {
            var InputChecklist = document.getElementsByName('InputChecklist');
            for (i = 0; i < InputChecklist.length; i++) {
                if (InputChecklist[i].checked) {
                    var n = InputChecklist[i].value;
                    TransformInput(n, d, p);
                }
            }
        }

        function TransformInput(n, d, p) {
//parses DrawData and find InputName = n,
//then shift the inputbox p pixels in direction d (up, down, left, right)
// if d = 'width' or 'height', the width and height will change by p pixels

            TempData = DrawData;
            var InputName = ""	//hold InputName
            var DataNumber = parseInt(0)	//holds the number that correspond to the order in which the Input is entered into the array
            p = parseInt(p);

            //shift Text, Textbox, Checkboxes
            for (var j = 0; (j < (TempData.length)); j++) {
                var RedrawParameter = TempData[j].split("|");
                var InputType = RedrawParameter[0]
                if (InputType == 'Text') {
                    InputName = new String(RedrawParameter[5]);
                    DataNumber = j;
                } else if (InputType == 'Textbox') {
                    InputName = new String(RedrawParameter[5]);
                    DataNumber = j;
                } else if (InputType == 'Checkbox') {
                    InputName = new String(RedrawParameter[3]);
                    DataNumber = j;
                } else if (InputType == 'Xbox') {
                    InputName = new String(RedrawParameter[5]);
                    DataNumber = j;
                } else if (InputType == 'Signature') {
                    InputName = new String(RedrawParameter[5]);
                    DataNumber = j;
                } else if (InputType == 'Stamp') {
                    InputName = new String(RedrawParameter[5]);
                    DataNumber = j;
                }
                if (InputName == n) {		//if InputName matches n
                    var TargetParameter = TempData[DataNumber].split("|");
                    var Xcoord = parseInt(TargetParameter[1]);
                    var Ycoord = parseInt(TargetParameter[2]);
                    var W = parseInt(TargetParameter[3]);
                    var H = parseInt(TargetParameter[4]);
                    if (d == 'up') {
                        Ycoord = Ycoord - p;
                        TargetParameter[2] = Ycoord;
                    } else if (d == 'down') {
                        Ycoord = Ycoord + p;
                        TargetParameter[2] = Ycoord;
                    } else if (d == 'left') {
                        Xcoord = Xcoord - p;
                        TargetParameter[1] = Xcoord;
                    } else if (d == 'right') {
                        Xcoord = Xcoord + p;
                        TargetParameter[1] = Xcoord;
                    } else if (d == 'width') {
                        W = W + p;
                        TargetParameter[3] = W;
                    } else if (d == 'height') {
                        H = H + p;
                        TargetParameter[4] = H;
                    }
                    DrawData[j] = TargetParameter.join("|");
                }
            }

            //Redraw boxes after updating coordinates
            RedrawAll();
        }


        var TopEdge = parseInt(0);
        var BottomEdge = parseInt(0);
        var LeftEdge = parseInt(0);
        var RightEdge = parseInt(0);


        function alignInput(edge) {
//finds checked InputName, then aligns checked input boxes to top/bottom/left/right edge
            TempData = DrawData;
            var InputChecklist = document.getElementsByName('InputChecklist');
            var InputName = "";		//hold InputName
            var DataNumber = parseInt(0);	//holds the number that correspond to the order in which the Input is entered into the array
            var Initialized = false;

            //find the inputs with the most top/bottom/left/right coordinates
            for (i = 0; i < InputChecklist.length; i++) {

                if (InputChecklist[i].checked) {
                    var n = InputChecklist[i].value;	//finds name of checked input, assigns it to n

                    for (var j = 0; (j < (TempData.length)); j++) {
                        var RedrawParameter = TempData[j].split("|");
                        var InputType = RedrawParameter[0]

                        if (InputType == 'Text') {
                            InputName = new String(RedrawParameter[5]);
                            DataNumber = j;
                        } else if (InputType == 'Textbox') {
                            InputName = new String(RedrawParameter[5]);
                            DataNumber = j;
                        } else if (InputType == 'Checkbox') {
                            InputName = new String(RedrawParameter[3]);
                            DataNumber = j;
                        } else if (InputType == 'Xbox') {
                            InputName = new String(RedrawParameter[5]);
                            DataNumber = j;
                        } else if (InputType == 'Signature') {
                            InputName = new String(RedrawParameter[5]);
                            DataNumber = j;
                        } else if (InputType == 'Stamp') {
                            InputName = new String(RedrawParameter[5]);
                            DataNumber = j;
                        }
                        if (InputName == n) {
                            var TargetParameter = TempData[DataNumber].split("|");
                            var Xcoord = parseInt(TargetParameter[1]);
                            var Ycoord = parseInt(TargetParameter[2]);
                            if (!Initialized) {
                                TopEdge = Ycoord;
                                BottomEdge = Ycoord;
                                LeftEdge = Xcoord;
                                RightEdge = Xcoord;
                                Initialized = true;
                            }

                            if (Xcoord < LeftEdge) {
                                LeftEdge = Xcoord;
                            } else if (Xcoord > RightEdge) {
                                RightEdge = Xcoord;
                            }
                            if (Ycoord < TopEdge) {
                                TopEdge = Ycoord;
                            } else if (Ycoord > BottomEdge) {
                                BottomEdge = Ycoord;
                            }
                        }
                    }
                }
            }

            //change selected inputs' coordinates to top/bottom/left/right edges
            for (i = 0; i < InputChecklist.length; i++) {
                if (InputChecklist[i].checked) {
                    var n = InputChecklist[i].value;	//finds name of checked input, assigns it to n
                    for (var j = 0; (j < (TempData.length)); j++) {
                        var RedrawParameter = TempData[j].split("|");
                        var InputType = RedrawParameter[0]
                        if (InputType == 'Text') {
                            InputName = new String(RedrawParameter[5]);
                            DataNumber = j;
                        } else if (InputType == 'Textbox') {
                            InputName = new String(RedrawParameter[5]);
                            DataNumber = j;
                        } else if (InputType == 'Checkbox') {
                            InputName = new String(RedrawParameter[3]);
                            DataNumber = j;
                        } else if (InputType == 'Xbox') {
                            InputName = new String(RedrawParameter[5]);
                            DataNumber = j;
                        } else if (InputType == 'Signature') {
                            InputName = new String(RedrawParameter[5]);
                            DataNumber = j;
                        } else if (InputType == 'Stamp') {
                            InputName = new String(RedrawParameter[5]);
                            DataNumber = j;
                        }
                        if (InputName == n) {		//if InputName matches n
                            var TargetParameter = TempData[DataNumber].split("|");
                            var Xcoord = parseInt(TargetParameter[1]);
                            var Ycoord = parseInt(TargetParameter[2]);
                            if (edge == 'top') {
                                TargetParameter[2] = TopEdge;
                            } else if (edge == 'bottom') {
                                TargetParameter[2] = BottomEdge;
                            } else if (edge == 'left') {
                                TargetParameter[1] = LeftEdge;
                            } else if (edge == 'right') {
                                TargetParameter[1] = RightEdge;
                            }
                            DrawData[DataNumber] = TargetParameter.join("|");
                        }
                    }

                }

            }

            //Redraw boxes after updating coordinates
            RedrawAll();
        }

        function deleteInput() {
            TempData = DrawData;
            var InputChecklist = document.getElementsByName('InputChecklist');
            var InputName = ""	//hold InputName
            var DataNumber = parseInt(0)	//holds the number that correspond to the order in which the Input is entered into the array

            //delete checked inputs in the input checklist
            for (i = 0; i < InputChecklist.length; i++) {

                if (InputChecklist[i].checked) {
                    var n = InputChecklist[i].value;	//finds name of checked input, assigns it to n

                    for (var j = 0; (j < (TempData.length)); j++) {
                        var RedrawParameter = TempData[j].split("|");
                        var InputType = RedrawParameter[0]

                        if (InputType == 'Text') {
                            InputName = new String(RedrawParameter[5]);
                            DataNumber = j;
                        } else if (InputType == 'Textbox') {
                            InputName = new String(RedrawParameter[5]);
                            DataNumber = j;
                        } else if (InputType == 'Checkbox') {
                            InputName = new String(RedrawParameter[3]);
                            DataNumber = j;
                        } else if (InputType == 'Xbox') {
                            InputName = new String(RedrawParameter[5]);
                            DataNumber = j;
                        } else if (InputType == 'Signature') {
                            InputName = new String(RedrawParameter[5]);
                            DataNumber = j;
                        } else if (InputType == 'Stamp') {
                            InputName = new String(RedrawParameter[5]);
                            document.getElementById('AddStamp').checked = false;
                            document.getElementById('AddStamp').disabled = false;
                            document.getElementById('AddSignatureBox2').disabled = false;
                            DataNumber = j;
                        }
                        if (InputName == n) {
                            TempData.splice(j, 1);
                        }
                    }
                }
            }
            DrawData = TempData;
            //Redraw boxes after updating coordinates
            RedrawAll();
            loadInputList();
        }
    </script>

    <script type="text/javascript">
        //output html code for eform

        var text = "";
        var textTop = "";
        var textMiddle = "";
        var textBottom = "";

        var CheckboxOffset = 4;
        var XboxOffset = 4;

        var MTopLeftX = 0;
        var MTopLeftY = 0;
        var FTopLeftX = 0;
        var FTopLeftY = 0;
        var SignatureHolderX = 0;
        var SignatureHolderY = 0;
        var SignatureHolderH = 0;
        var SignatureHolderW = 0;

        var parentPresent = false;
        var xPresent = false;
        var radioPresent = false;

        function updateOptions() {

            for (j = 0; (j < (DrawData.length)); j++) {
                var P = DrawData[j].split("|");
                if ((P[3] == "parent1") || (P[5] == "parent1")) {
                    parentPresent = true;
                }
            }
            for (j = 0; (j < (DrawData.length)); j++) {
                var P = DrawData[j].split("|");
                if (P[0] == "Xbox") {
                    xPresent = true;
                }
            }
            //PHC add in test to detect Radio
            for (j = 0; (j < (DrawData.length)); j++) {
                var P = DrawData[j].split("|");
                if (P.length > 14) {
                    if (P[14].indexOf("Radio") > -1) {
                        radioPresent = true;
                    }
                }
            }

        }


        function resetAll() {
            text = "";
            textTop = "";
            textBottom = "";
            textMiddle = "";
            inputName = "";
            inputCounter = 1;
            DrawData = new Array();
            TempData = new Array();

            SetSwitchOn('Text');
            document.getElementById('Text').click();

            document.getElementById('inputValue').value = "";
            document.getElementById('inputName').value = "";
            document.getElementById('page').value = "";
            document.getElementById('preCheck').checked = false;
            document.getElementById('preCheckGender').checked = false;
            document.getElementById('XboxType').checked = false;
            document.getElementById('maximizeWindow').checked = false;
            document.getElementById('removeHeadersFooters').checked = false;
            var l = document.getElementById('oscarDB');
            l[0].selected = true;
            document.getElementById('AddSignature').checked = false;
            document.getElementById('AddStamp').checked = false;
            document.getElementById('AddSignatureClassic').checked = false;
            document.getElementById('AddSignature').disabled = false;
            document.getElementById('AddSignatureClassic').disabled = false;
            document.getElementById('AddSignatureBox1').disabled = false;
            document.getElementById('AddStamp').disabled = false;
            document.getElementById('AddSignatureBox2').disabled = false;

            document.getElementById('includePdfPrintControl').checked = false;
            document.getElementById('includeFaxControl').checked = false;

            document.getElementById('BlackBox').checked = false;
            clearGraphics(jg);
            PageNum = 0;
            finishLoadingImage();
            loadImage();
        }


        function GetTextTop() {

            updateOptions();


            textTop = "&lt;!DOCTYPE html&gt;\n&lt;html&gt;\n&lt;head&gt;\n"
            textTop += "&lt;META http-equiv=&quot;Content-Type&quot; content=&quot;text/html; charset=UTF-8&quot;&gt;\n";
            textTop += "&lt;title&gt;"
            textTop += document.getElementById('eFormName').value;
            textTop += "&lt;/title&gt;\n";
            // first style that is there for all media
            textTop += "&lt;style&gt;\n";
            textTop += " body, textarea {\n\tfont-size:12px;\n\tfont-family:sans-serif;\n\tfont-style:normal;\n\tfont-weight:normal;\n\ttext-align:left;\n\tbackground-color:transparent;\n }\n";
            textTop += " input {\n\t-moz-box-sizing: content-box;\n\t-webkit-print-color-adjust: exact;\n\t-webkit-box-sizing: content-box;\n\tbox-sizing: content-box;\n }\n";
            if (xPresent) {
                textTop += " .Xbox {\n\twidth:14px;\n\theight:14px;\n\tfont-weight:bold;\n\ttext-align:center;\n\tbackground-color:white;\n }\n";
            }
            if (radioPresent) {
                textTop += " .Radio {\n\twidth:14px;\n\theight:14px;\n\tfont-weight:bold;\n\ttext-align:center;\n\tbackground-color:white;\n\tborder-radius:14px;\n }\n";
            }
            if (document.getElementById('BlackBox').checked) {
                textTop += " .on {\n\tbox-shadow:inset 0px 0px 0px 30px black;\n }\n"
            }
            if (document.getElementById('AddSignature').checked) {
                textTop += " .sig {\n\tborder:" + SignatureBorder + ";\n\tcolor:" + SignatureColor + ";\n\tbackground-color:white;\n }\n"
            }
            textTop += "&lt;/style&gt;\n\n";

            // for screen media noborder should have a border and supply a gripper
            textTop += "&lt;style type=&quot;text/css&quot; media=&quot;screen&quot; &gt;\n";
            textTop += " .noborder {\n\tborder:1px solid; !important\n }\n"
            textTop += " html.touch #content {\n\tfloat:left;\n\twidth:92%;\n}\n html.touch #scrollgrabber {\n\tfloat:right;\n\twidth:4%;\n\tmargin-right:2%;\n\tbackground-image:url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAAFCAAAAACh79lDAAAAAXNSR0IArs4c6QAAABJJREFUCB1jmMmQxjCT4T/DfwAPLgOXlrt3IwAAAABJRU5ErkJggg==)\n }\n html.borderradius #scrollgrabber {\n\tborder-radius: 1em;\n }\n";

            textTop += "&lt;/style&gt;\n\n";
            // for print media do not display DoNotPrint nor noborders
            textTop += "&lt;style type=&quot;text/css&quot; media=&quot;print&quot;&gt;\n"
            textTop += " .DoNotPrint {\n\tdisplay:none;\n }\n .noborder {\n\tborder:0px;\n\tbackground: transparent;\n";
            textTop += "&lt;/style&gt;\n\n";

            if (<% if (OscarProperties.getInstance().isPropertyActive("eform_generator_indivica_print_enabled")) { %>(document.getElementById('includePdfPrintControl').checked) || <%}%> <% if (OscarProperties.getInstance().isPropertyActive("eform_generator_indivica_fax_enabled")) { %>(document.getElementById("includeFaxControl").checked) || <% } %> (document.getElementById('AddSignature').checked)) {
                textTop += "&lt;!-- jQuery for greater functionality --&gt;\n"
                // dependency on jquery up to version 2.2.1 for pdf and faxing for OSCAR Pro
                // ensure that we check the integrety of the CDN's version

                textTop += "&lt;script type=&quot;text/javascript&quot; src=&quot;<%=request.getContextPath()%>/library/jquery/jquery-3.6.4.min.js&quot; &gt;&lt;/script&gt;\n";
                // if unavailable reference the one in OSCAR
                textTop += "&lt;script&gt; window.jQuery || document.write('&lt;script src=&quot;<%=request.getContextPath()%>/js/jquery-1.12.3.js&quot;&gt;&lt; &#92;/script&gt;') &lt;/script&gt;\n";
            }


//   if (parentPresent  || document.getElementById('radioX').checked || document.getElementById('radio').checked || document.getElementById('preCheckGender').checked) {
            // Adding jquery code for checkbox parent-child-fields (Bell Eapen, nuchange.ca)
//        textTop += "\n&lt;!-- jQuery for parent-child and radio fields --&gt;\n"
//        textTop += "&lt;script&gt;\n";
//        textTop += "$(document).ready(function() {\n\t$('[class^=\"child-\"]').hide();";
//        textTop += "\n\t$('.parent-field').on( "click", function() {\n\t\t$('[class^=\"child-\"]').hide();\n\t\t$('.parent-field').each(function() {"
//        textTop += "\n\t\t\tif ( $(this).is('input:checkbox') ){\n\t\t\t\tif(this.checked){\n\t\t\t\t\t$('.child-' +  $(this).prop('id')).show();\n\t\t\t\t}else{\n\t\t\t\t$('.child-' + $(this).val()).show();\n\t\t\t\t}\n\t\t\t}"
//        textTop += "\n\t\t\tif ( $(this).is('input:text') ){\n\t\t\t\tif($(this).val()=='X'){\n\t\t\t\t\t$('.child-' +  $(this).prop('id')).show();\n\t\t\t\t}else{\n\t\t\t\t$('.child-' + $(this).val()).show();\n\t\t\t\t}\n\t\t\t}"
//        textTop += "\n\t\t});\n\t});";
//        textTop += "\n\t$('[class^=\"only-one-\"]').on( "click", function() {\n\t\tif ( $(this).is('input:checkbox') ){";
//        textTop += "\n\t\t\t$('.'+$(this).attr('class')).prop('checked', false);\n\t\t\t$(this).prop('checked', true);\n\t\t}\n";
//        textTop += "\n\t\tif ( $(this).is('input:text') ){";
//        textTop += "\n\t\t\t$('.'+$(this).attr('class')).val('');";
//        textTop += "\n\t\t\t$( this ).val('X');\n\t\t}\n\t});\n});\n";
//        textTop += "&lt;/script&gt;\n";
//    }

            //reference built in functions as desired

            if (document.getElementById('includePdfPrintControl').checked) {
                textTop += "&lt;script type=&quot;text/javascript&quot; src=&quot;$%7Boscar_javascript_path%7Deforms/printControl.js&quot;&gt;&lt;/script&gt;\n";
            }

            //reference built in faxControl
            if (document.getElementById("includeFaxControl").checked) {
                textTop += "&lt;script type=&quot;text/javascript&quot; src=&quot;$%7Boscar_javascript_path%7Deforms/faxControl.js&quot;&gt;&lt;/script&gt;\n";
            }

            // Support for consult_sig_xxx.png signatures
            if (document.getElementById('AddStamp2').checked) {
                textTop += "\n&lt;!-- Classic Signatures --&gt;\n\n"
                textTop += "&lt;script type=&quot;text/javascript&quot;&gt;\n";
                textTop += "function SignForm2() {\n";
                if (document.getElementById('Delegation').checked) {
                    textTop += "\t//stamp by delegation model \n";
                    textTop += "\tvar provNum = '';\n";
                    textTop += "\tvar userBillingNo = document.getElementById('user_ohip_no').value;\n";
                    textTop += "\tif (parseInt(userBillingNo) > 100) {\n";
                    textTop += "\t\t// then a valid billing number so use the current user id \n";
                    textTop += "\t\tprovNum = document.getElementById('user_id').value; \n";
                    textTop += "\t\tif (provNum != document.getElementById('doctor_no').value && !!document.getElementById('doctor')) {\n"
                    textTop += "\t\t\tdocument.getElementById('doctor').value=document.getElementById('CurrentUserName').value + ' CC: ' + document.getElementById('doctor').value;\n"
                    textTop += "\t\t}\n"
                    textTop += "\t} else { \n";
                    textTop += "\t\tprovNum = document.getElementById('doctor_no').value; \n";
                    textTop += "\t}\n";
                } else {
                    textTop += "\t//stamp by user model \n";
                    textTop += "\tvar provNum = document.getElementById('doctor_no').value; \n";
                }
                textTop += "\tdocument.getElementById('Stamp').src = '<%=request.getContextPath()%>/eform/displayImage.do?imagefile=consult_sig_'+provNum+'.png';\n";
                textTop += "}\n";
                textTop += "function toggleMe(){\n"
                textTop += "\tif (document.getElementById(&quot;Stamp&quot;).src.indexOf(&quot;BNK.png&quot;)>0){\n"
                textTop += "\t\tSignForm2()\n"
                textTop += "\t} else {\n"
                textTop += "\t\tdocument.getElementById(&quot;Stamp&quot;).src = &quot;<%=request.getContextPath()%>/eform/displayImage.do?imagefile=BNK.png&quot;;\n"
                textTop += "\t}\n"
                textTop += "}\n"
                textTop += "&lt;/script&gt;\n\n";
            }

            //reference built in signatureControl
            if (document.getElementById('AddSignatureClassic').checked) {
                textTop += "\n&lt;!-- Classic Signatures --&gt;\n"
                textTop += "&lt;script type=&quot;text/javascript&quot; src=&quot;$%7Boscar_javascript_path%7Deforms/signatureControl.jsp&quot;&gt;&lt;/script&gt;\n";
                textTop += "&lt;script type=&quot;text/javascript&quot;&gt;\n";
                textTop += "if (typeof jQuery != &quot;undefined&quot; &amp;&amp; typeof signatureControl != &quot;undefined&quot;) {";
                textTop += "jQuery(document).ready(function() {";
                var totalpx = SignatureHolderY + sigOffset;
                textTop += "signatureControl.initialize({eform:true, height:" + SignatureHolderH + ", width:" + SignatureHolderW + ", top:" + totalpx + ", left:" + SignatureHolderX + "});";
                textTop += "});}\n";
                textTop += "\t \n";
                textTop += "&lt;/script&gt;\n\n";
            }

            //reference Signature library
            if (document.getElementById('AddSignature').checked) {
                var sigArray = new Array();
                for (j = 0; (j < (DrawData.length)); j++) {
                    var P = DrawData[j].split("|");
                    if ((P[0] == "Signature") && (P[5] != "ClassicSignature")) {
                        sigArray.push(P[5]);
                    }
                }
                textTop += "\n&lt;!-- Freeform Signatures --&gt;\n\n"

                //For external testing jSignature should be placed in the location as the images and the resultant html file
                //textTop += "\n&lt;!-- jSignature file for local testing outside of OSCAR --&gt;\n"
                //textTop += "&lt;script type=&quot;text/javascript&quot; src=&quot;jSignature.min.js&quot;&gt;&lt;/script&gt;\n\n";

                // Sign Here is not included in OSCAR 15 source
                //if (document.getElementById('SignHere').checked){
                //textTop += "&lt;script type=&quot;text/javascript&quot; src=&quot;SignHere.js&quot;&gt;&lt;/script&gt;\n\n";
                //textTop += "&lt;script type=&quot;text/javascript&quot; src=&quot;${oscar_image_path}SignHere.js&quot;&gt;&lt;/script&gt;\n\n";
                //}

                //In OSCAR 12 jSignature and SignHere should be placed in the images folder
                //textTop += "&lt;script type=&quot;text/javascript&quot; src=&quot;${oscar_image_path}jSignature.min.js&quot;&gt;&lt;/script&gt;\n\n";
                //In OSCAR 15 jSignature is available within the source

                textTop += "&lt;script type=&quot;text/javascript&quot; src=&quot;$%7Boscar_javascript_path%7Djquery/jSignature.min.js&quot;&gt;&lt;/script&gt;\n\n";
                //flash and IE support deprecated
                //textTop += "&lt;!--[if lt IE 9]&gt;\n"
                //textTop += "&lt;script type=&quot;text/javascript&quot; src=&quot;$%7Boscar_javascript_path%7Dflashcanvas.js&quot;&gt;&lt;/script&gt;\n";
                //textTop += "&lt;![endif]--&gt;\n\n"

                textTop += "&lt;script type=&quot;text/javascript&quot;&gt;\n";
                textTop += "jQuery(document).ready(function() {\n";
                for (j = 0; (j < (sigArray.length)); j++) {
                    textTop += "\t$(&quot;#Canvas" + sigArray[j] + "&quot;).jSignature({'decor-color':'" + SignatureLineColor + "'})\n"
                }
                textTop += "\tvar pdf = jQuery(&quot;input[name='pdfButton']&quot;);\n"
                textTop += "\tif (pdf.length != 0) {\n"
                textTop += "\t\tpdf.attr('onclick', '').off('click');\n"
                textTop += "\t\tpdf.attr('value', 'PDF');\n"
                textTop += "\t\tpdf.click(function(){saveSig();submitPrintButton(false);});\n"
                textTop += "\t\t}\n"
                textTop += "\tvar pdfSave = jQuery(&quot;input[name='pdfSaveButton']&quot;);\n"
                textTop += "\tif (pdfSave.length != 0) {\n"
                textTop += "\t\tpdfSave.attr('onclick', '').off('click');\n"
                textTop += "\t\tpdfSave.attr('value', 'Submit & PDF');\n"
                textTop += "\t\tpdfSave.click(function(){saveSig();submitPrintButton(true);});\n"
                textTop += "\t\t}\n"

                textTop += "})\n";
                textTop += "&lt;/script&gt;\n\n";
                textTop += "&lt;script type=&quot;text/javascript&quot;&gt;\n";
                textTop += "function saveSig(){\n"
                for (j = 0; (j < (sigArray.length)); j++) {
                    textTop += "\tvar $sig=$(&quot;#Canvas" + sigArray[j] + "&quot;);\n"
                    textTop += "\tvar datapair=new Array();\n"
                    textTop += "\tdatapair=$sig.jSignature(&quot;getData&quot;,&quot;base30&quot;);\n"
                    textTop += "\tdocument.getElementById(&quot;Store" + sigArray[j] + "&quot;).value=datapair.join(&quot;,&quot;);\n"
                }
                textTop += "}\n";
                textTop += "function clearSig(){\n"
                for (j = 0; (j < (sigArray.length)); j++) {
                    textTop += "\t$(&quot;#Canvas" + sigArray[j] + "&quot;).jSignature(&quot;reset&quot;);\n"
                }
                textTop += "}\n";

                textTop += "function loadSig(){\n"
                if (document.getElementById('Delegation').checked) {
                    textTop += "\tvar provNum = '';\n";
                    textTop += "\tvar userBillingNo = $('#user_ohip_no').val();\n";
                    textTop += "\tif (parseInt(userBillingNo) > 100) {\n";
                    textTop += "\t\t// then a valid billing number so use the current user id \n";
                    textTop += "\t\tprovNum = $('#user_id').val(); \n";
                    textTop += "\t} else { \n";
                    textTop += "\t\tprovNum = $('#doctor_no').val(); \n";
                    textTop += "\t}\n";
                }
                for (j = 0; (j < (sigArray.length)); j++) {
                    textTop += "\tvar $sig=$(&quot;#Canvas" + sigArray[j] + "&quot;);\n"
                    textTop += "\tvar data\n"
                    textTop += "\tdata=document.getElementById(&quot;Store" + sigArray[j] + "&quot;).value;\n"
                    textTop += "\t$sig.jSignature(&quot;setData&quot;,&quot;data:&quot;+ data) ;\n"
                }
                textTop += "}\n";
                textTop += "&lt;/script&gt;\n\n"
            }

            //auto ticking gender Xboxes OR checkboxes
            if ((document.getElementById('preCheckGender').checked) || (document.getElementById('XboxType').checked)) {
                textTop += "&lt;!-- auto ticking gender Xboxes OR checkboxes --&gt;\n"
                textTop += "&lt;script type=&quot;text/javascript&quot; language=&quot;javascript&quot;&gt;\n"
                textTop += "function checkGender(){\n" +
                    "\tlet patientGenderVal = document.getElementById(\"PatientGender\").value;\n" +
                    "\tif (patientGenderVal == \"M\" || patientGenderVal == \"F\") {\n" +
                    "\t\tlet inputCheckEle = document.getElementById(patientGenderVal == \"M\" ? \"Male\" : \"Female\");\n" +
                    "\t\tif (inputCheckEle.classList.contains(\"Xbox\")) {\n" +
                    "\t\t\t// xbox\n" +
                    "\t\t\tinputCheckEle.value = \"X\";\n";
                if (document.getElementById('BlackBox').checked) {
                    textTop += "\t\t\t\tinputCheckEle.classList.add('on');\n";
                }
                textTop += "\t\t} else {\n" +
                    "\t\t\t// checkbox\n" +
                    "\t\t\tinputCheckEle.checked = true;\n" +
                    "\t\t}\n" +
                    "\t}\n }\n";
                textTop += "&lt;/script&gt;\n\n"
            }

            //printing script
            textTop += "&lt;script language=&quot;javascript&quot;&gt;\n"
            textTop += "function formPrint(){\n"
            textTop += "\twindow.print();\n"
            textTop += "} \n"
            textTop += "&lt;/script&gt;\n\n"

            //reference built in faxControl
            <% if (eformGeneratorIndivicaFaxEnabled) { %>
            //fax number script
            if ((document.getElementById('faxno').value.length > 0)) {
                textTop += "&lt;script language=&quot;javascript&quot;&gt;\n"
                textTop += "function setFaxNo(){\n"
                textTop += "\tsetTimeout('document.getElementById(&quot;otherFaxInput&quot;).value=&quot;"
                textTop += document.getElementById('faxno').value
                textTop += "&quot;',1000);\n"
                textTop += "} \n"
                textTop += "&lt;/script&gt;\n\n"
            }

            <% } %>

            // Tickler Support
            if (document.getElementById('includeTicklerControl').checked) {
                textTop += "\n&lt;!-- Tickler Support --&gt;\n\n"
                textTop += "&lt;script language=&quot;javascript&quot;&gt;\n"
                textTop += "function setDate(weeks){\n"
                textTop += "\tvar now = new Date();\n"
                textTop += "\tnow.setDate(now.getDate() + weeks * 7);\n"
                textTop += "\treturn (now.toISOString().substring(0,10));\n"
                textTop += "\t}\n\n"
                textTop += "function setAtickler(){\n"
                textTop += "\tvar today = new Date().toISOString().slice(0, 10);\n"
                textTop += "\tvar subject=( $('#subject').val() ? $('#subject').val() : 'test');\n"
                textTop += "\tvar demographicNo = ($('#tickler_patient_id').val() ? $('#tickler_patient_id').val() : '-1'); // patient_id\n"
                textTop += "\tvar taskAssignedTo = ($('#tickler_send_to').val() ? $('#tickler_send_to').val() : '-1'); // -1 is reserved for the system user\n"
                textTop += "\tvar weeks = ($('#tickler_weeks').val() ? $('#tickler_weeks').val() : '6');\n"
                textTop += "\tvar message = ($('#tickler_message').val() ? $('#tickler_message').val() : 'Check for results of '+subject+' ordered ' + today);\n"
                textTop += "\tvar ticklerDate = setDate(weeks);\n"
                textTop += "\tvar urgency = ($('#tickler_priority').val() ? $('#tickler_priority').val() : 'Normal'); // case sensitive: Low Normal High\n"
                textTop += "\tvar ticklerToSend = {};\n"
                textTop += "\tticklerToSend.demographicNo = demographicNo;\n"
                textTop += "\tticklerToSend.message = message;\n"
                textTop += "\tticklerToSend.taskAssignedTo = taskAssignedTo;\n"
                textTop += "\tticklerToSend.serviceDate = ticklerDate;\n"
                textTop += "\tticklerToSend.priority = urgency;\n"
                textTop += "\treturn $.ajax({\n"
                textTop += "\t\ttype: 'POST',\n"
                textTop += "\t\turl: '<%= request.getContextPath() %>/ws/rs/tickler/add',\n"
                textTop += "\t\tdataType:'json',\n"
                textTop += "\t\tcontentType:'application/json',\n"
                textTop += "\t\tdata: JSON.stringify(ticklerToSend)\n"
                textTop += "\t\t});\n"
                textTop += "\t}\n"
                textTop += "&lt;/script&gt;\n\n"
            }

            //Peter Hutten-Czapski's script to confirm closing of window if eform changed
            textTop += "&lt;!-- scripts to confirm closing of window if it hadnt been saved yet --&gt;\n"
            textTop += "&lt;script language=&quot;javascript&quot;&gt;\n"
            textTop += "//keypress events trigger dirty flag\n"
            textTop += "var needToConfirm = false;\n"
            textTop += "document.onkeyup=setDirtyFlag;\n"

            textTop += "function setDirtyFlag(){\n"
            textTop += "\tneedToConfirm = true;\n"
            textTop += "}\n"

            textTop += "function releaseDirtyFlag(){\n"
            textTop += "\tneedToConfirm = false; //Call this function to prevent an alert.\n"
            if (document.getElementById('includeTicklerControl').checked) {
                textTop += "\t$.when(setAtickler()).then(function( data, textStatus, jqXHR ) {\n"
                textTop += "\t\tif ( jqXHR.status != 200 ){ alert('ERROR ('+jqXHR.status+') automatic tickler FAILED to be set');}\n"
                textTop += "\t\tdocument.getElementById('FormName').submit()\n"
                textTop += "\t});\n"
            } else {
                textTop += "\tdocument.getElementById('FormName').submit()\n"
            }
            textTop += "}\n"
            textTop += "window.onbeforeunload = confirmExit;\n"

            textTop += "function confirmExit(){\n"
            textTop += "\tif (needToConfirm){\n"
            textTop += "\t\t return &quot;You have attempted to leave this page. If you have made any changes to the fields without clicking the Save button, your changes will be lost. Are you sure you want to exit this page?&quot;;\n"
            textTop += "\t }\n"
            textTop += "}\n"
            textTop += "&lt;/script&gt;\n\n"

            //maximize window script
            if (document.getElementById('maximizeWindow').checked) {
                textTop += "&lt;!-- scripts to maximise window on load --&gt;\n"
                textTop += "&lt;script language=&quot;JavaScript&quot;&gt;\n"
                textTop += "\t top.window.moveTo(0,0);\n"
                textTop += "\t top.window.resizeTo(screen.availWidth,screen.availHeight);\n"
                textTop += "\t\n\n &lt;/script&gt;\n\n"
            }

            //By Adrian Starzynski: Option to remove headers and footers on print
            if (document.getElementById('maximizeWindow').checked) {
                textTop += "&lt;!-- style to remove headers and footers on print --&gt;\n"
                textTop += "&lt;style type=&quot;text/css&quot; media=&quot;print&quot;&gt;\n"
                textTop += "\t &commat;media print {\n"
                textTop += "\t &commat;page &#123;\n"
                textTop += "\t margin-top: 0;\n"
                textTop += "\t margin-bottom: 0;\n"
                textTop += "\t &#125;\n"
                textTop += "\t body &#123;\n"
                textTop += "\t padding-top: 36px;\n"
                textTop += "\t padding-bottom: 36px;\n"
                textTop += "\t &#125;\n"
                textTop += "\t &#125;\n"
                textTop += "\t\n\n &lt;/style&gt;\n\n"
            }

            //scripts for scaling up checkboxes
            if (document.getElementById('ScaleCheckmark').checked) {
                textTop += "&lt;!-- scripts for scaling up checkboxes --&gt;\n"
                textTop += "&lt;style type=&quot;Text/css&quot;&gt;\n"
                textTop += "input.largerCheckbox {\n"
                textTop += "\t-moz-transform:scale(1.3);         /*scale up image 1.3x - Firefox specific */ \n"
                textTop += "\t-webkit-transform:scale(1.3);      /*Webkit based browser eg Chrome, Safari */ \n"
                textTop += "\t-o-transform:scale(1.3);           /*Opera browser */ \n"
                textTop += "}\n"
                textTop += "&lt;/style&gt;\n"
                textTop += "&lt;style type=&quot;text/css&quot; media=&quot;print&quot;&gt;\n"
                textTop += "input.largerCheckbox { \n"
                textTop += "\t-moz-transform:scale(1.8);         /*scale up image 1.8x - Firefox specific */ \n"
                textTop += "\t-webkit-transform:scale(1.3);      /*Webkit based browser eg Chrome, Safari */ \n"
                textTop += "\t-o-transform:scale(1.3);           /*Opera browser */ \n"
                textTop += "} \n"
                textTop += "&lt;/style&gt;\n"
                textTop += "&lt;!--[if IE]&gt;\n"
                textTop += "&lt;style type=&quot;text/css&quot;&gt;\n"
                textTop += "input.largerCheckbox { \n"
                textTop += "\theight: 30px;                     /*30px checkboxes for IE 5 to IE 7 */ \n"
                textTop += "\twidth: 30px; \n"
                textTop += "} \n"
                textTop += "&lt;/style&gt; \n"
                textTop += "&lt;![endif]--&gt; \n\n"
            }

            if (document.getElementById('AddStamp').checked) {

                var List = document.getElementsByName('UserSignatureListItem');

                textTop += "&lt;!-- Stamped Signatures --&gt;\n"
                textTop += "&lt;script type=&quot;text/javascript&quot;&gt;\n"
                textTop += "var ImgArray = [];\n"
                textTop += "&lt;/script&gt;\n\n"

                textTop += "&lt;script src=\"<%= request.getContextPath() %>/eform/displayImage.do?imagefile=stamps.js\"&gt;&lt;/script&gt;\n"
                textTop += "&lt;script type=&quot;text/javascript&quot;&gt;\n"
                textTop += "//autoloading signature images\n"
                textTop += "ImgArray.push(\n\t&quot;anonymous|BNK.png&quot;"
                for (i = 0; i < List.length; i++) {
                    // Use textContent instead of innerHTML to prevent potential XSS
                    textTop += ",\n\t&quot;" + List[i].textContent.trim() + "&quot;"
                }
                textTop += "\n\t);\n\n"
                textTop += "function SignForm(){\n"
                textTop += "\t//first look for the current users stamp\n"
                textTop += "\tfor (i=0; i&lt;ImgArray.length;i++){\n"
                textTop += "\t\tvar ListItemArr =  ImgArray[i].split(&quot;|&quot;);\n"
                textTop += "\t\tvar UserName = ListItemArr[0];\n"
                textTop += "\t\tvar FileName = ListItemArr[1];\n"
                textTop += "\t\tif (document.getElementById(&quot;CurrentUserName&quot;).value.indexOf(UserName)>=0){\n"
                textTop += "\t\t\tdocument.getElementById(&quot;Stamp&quot;).src = &quot;<%=request.getContextPath()%>/eform/displayImage.do?imagefile=&quot;+FileName;\n"
                textTop += "\t\t}\n"
                textTop += "\t}\n"
                textTop += "\t//hmm not found so lets try the MRPs stamp\n"
                textTop += "\tif (document.getElementById(&quot;Stamp&quot;).src.indexOf(&quot;BNK.png&quot;)>0){\n"
                textTop += "\t\tfor (i=0; i&lt;ImgArray.length;i++){\n"
                textTop += "\t\t\tvar ListItemArr =  ImgArray[i].split(&quot;|&quot;);\n"
                textTop += "\t\t\tvar UserName = ListItemArr[0];\n"
                textTop += "\t\t\tvar FileName = ListItemArr[1];\n"
                textTop += "\t\t\tif (document.getElementById(&quot;DoctorName&quot;).value.indexOf(UserName)>=0){\n"
                textTop += "\t\t\t\tdocument.getElementById(&quot;Stamp&quot;).src = &quot;<%=request.getContextPath()%>/eform/displayImage.do?imagefile=&quot;+FileName;\n"
                textTop += "\t\t\t}\n"
                textTop += "\t\t}\n"
                textTop += "\t}\n"
                textTop += "}\n"

                textTop += "function toggleMe(){\n"
                textTop += "\tif (document.getElementById(&quot;Stamp&quot;).src.indexOf(&quot;BNK.png&quot;)>0){\n"
                textTop += "\t\tSignForm()\n"
                textTop += "\t} else {\n"
                textTop += "\t\tdocument.getElementById(&quot;Stamp&quot;).src = &quot;<%=request.getContextPath()%>/eform/displayImage.do?imagefile=BNK.png&quot;;\n"
                textTop += "\t}\n"
                textTop += "}\n"
                textTop += "&lt;/script&gt;\n\n"

            }

            if (document.getElementById('AddDate').checked) {
                for (j = 0; (j < (DrawData.length)); j++) {
                    var P = DrawData[j].split("|");
                    if ((P[0] == "Text") && ((P[5].indexOf("day") > -1) || (P[5].indexOf("date") > -1)) && (P[5] !== "dob_day") && (P[5] !== "today_rx")) {
                        calendars = true
                    }
                }
            }

            if (calendars) {
                textTop += "\n&lt;!-- main calendar program --&gt;\n"
                textTop += "&lt;script type=&quot;text/javascript&quot; src=&quot;<%=request.getContextPath()%>/share/calendar/calendar.js&quot;&gt;&lt;/script&gt;\n"
                textTop += "&lt;script type=&quot;text/javascript&quot; src=&quot;<%=request.getContextPath()%>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>&quot;&gt;&lt;/script&gt;\n"
                textTop += "&lt;script type=&quot;text/javascript&quot; src=&quot;<%=request.getContextPath()%>/share/calendar/calendar-setup.js&quot;&gt;&lt;/script&gt;\n"
                textTop += "&lt;link rel=&quot;stylesheet&quot; type=&quot;text/css&quot; media=&quot;all&quot; href=&quot;<%=request.getContextPath()%>/share/calendar/calendar.css&quot; title=&quot;win2k-cold-1&quot; /&gt;\n\n"

            }

            //</head>
            textTop += "&lt;/head&gt;\n\n"
            //<body>
            textTop += "&lt;body"
            textTop += " onload=&quot;"
            //textTop += "reImg();"
            //auto load signature stamp image, default to 'current_user'
            if (document.getElementById('AddStamp').checked) {
                textTop += "SignForm();"
            }
            if (document.getElementById('AddStamp2').checked) {
                textTop += "SignForm2();"
            }
            if (document.getElementById('AddSignature').checked) {
                textTop += "loadSig();"
            }
            //auto check gender boxes
            if ((document.getElementById('preCheckGender').checked) || (document.getElementById('XboxType').checked)) {
                textTop += "checkGender();"
            }
            <% if (eformGeneratorIndivicaFaxEnabled) { %>
            if ((document.getElementById('faxno').value.length > 0)) {
                textTop += "setFaxNo();"
            }
            <% } %>

            textTop += "&quot;&gt;\n"

            //<form>
            textTop += "&lt;form method=&quot;post&quot; name=&quot;FormName&quot; id=&quot;FormName&quot; &gt;\n";

        }

        function GetTextMiddle(P) {

            var InputType = P[0];
            console.log(P);
            if (InputType == "Page") {

                var pg = parseInt(P[1]);
                var im = P[2];
                var width = parseInt(P[3]);
                m = "";
                if (pg > 1) {
                    m = "&lt;/div&gt;\n\n";
                }
                m += "&lt;div id=&quot;page"
                m += pg
                m += "&quot; style=&quot;"
                if ((PageNum > 1) && (pg < PageNum)) {
                    m += "page-break-after:always;"
                }
                m += "position:relative;&quot; &gt;\n"
                m += "&lt;img id=&quot;BGImage"
                m += pg
                m += "&quot; src=&quot;$%7Boscar_image_path%7D";
                m += im
                m += "&quot; style=&quot;position: relative; left: 0px; top: 0px; width:"
                m += width
                m += "px&quot;&gt;\n"

            }

            if (InputType == "Text") {
                var x0 = parseInt(P[1]);
                var y0 = parseInt(P[2]);
                var width = parseInt(P[3]);
                var height = parseInt(P[4]);
                var inputName = P[5];
                var fontFamily = P[6];
                var fontStyle = P[7];
                var fontWeight = P[8];
                var fontSize = P[9];
                var textAlign = P[10];
                var bgColor = P[11];
                var oscarDB = P[12];
                var inputValue = P[13];
                var inputClassValue = P[14] + P[15];
                m = "&lt;input name=&quot;"
                m += inputName
                m += "&quot; id=&quot;"
                m += inputName
                m += "&quot; type=&quot;text&quot; class=&quot;"
                m += inputClassValue
                m += "noborder&quot; style=&quot;position:absolute; left:"
                m += x0
                m += "px; top:"
                m += y0
                m += "px; width:"
                m += width
                m += "px; height:"
                m += height;
                m += "px; "
                if (fontFamily != 'sans-serif') {
                    m += "font-family:";
                    m += fontFamily + "; ";
                }
                if (fontStyle != 'normal') {
                    m += "font-style:";
                    m += fontStyle + "; ";
                }
                if (fontWeight != 'normal') {
                    m += "font-weight:";
                    m += fontWeight + "; ";
                }
                if (fontSize != '12') {
                    m += "font-size:";
                    m += fontSize + "; ";
                }
                if (textAlign != 'left') {
                    m += "text-align:";
                    m += textAlign + "; ";
                }
                if (bgColor != 'transparent') {
                    m += "background-color:";
                    m += bgColor + "; ";
                }
                m += "&quot; "
                if (oscarDB) {
                    m += " oscarDB="
                    m += oscarDB
                } else if ((!oscarDB) && (inputValue)) {
                    m += "value=&quot;"
                    m += inputValue
                    m += "&quot;"
                }
                m += "&gt;"

            } else if (InputType == "Textbox") {
                var x0 = parseInt(P[1]);
                var y0 = parseInt(P[2]);
                var width = parseInt(P[3]);
                var height = parseInt(P[4]);
                var inputName = P[5];
                var fontFamily = P[6];
                var fontStyle = P[7];
                var fontWeight = P[8];
                var fontSize = P[9];
                var textAlign = P[10];
                var bgColor = P[11];
                var oscarDB = P[12];
                var inputValue = P[13];
                var inputClassValue = P[14] + P[15];
                m = "&lt;textarea name=&quot;"
                m += inputName
                m += "&quot; id=&quot;"
                m += inputName
                m += "&quot; type=&quot;text&quot; class=&quot;"
                m += inputClassValue
                m += " noborder&quot; style=&quot;position:absolute; left:"
                m += x0
                m += "px; top:"
                m += y0
                m += "px; width:"
                m += width
                m += "px; height:"
                m += height
                m += "px; ";
                if (fontFamily != 'sans-serif') {
                    m += "font-family:";
                    m += fontFamily + "; ";
                }
                if (fontStyle != 'normal') {
                    m += "font-style:";
                    m += fontStyle + "; ";
                }
                if (fontWeight != 'normal') {
                    m += "font-weight:";
                    m += fontWeight + "; ";
                }
                if (fontSize != '12') {
                    m += "font-size:";
                    m += fontSize + "; ";
                }
                if (textAlign != 'left') {
                    m += "text-align:";
                    m += textAlign + "; ";
                }
                if (bgColor != 'transparent') {
                    m += "background-color:";
                    m += bgColor + "; ";
                }
                m += "&quot; "
                if (oscarDB) {
                    m += " oscarDB="
                    m += oscarDB
                }
                m += "&gt;"
                if (!oscarDB) {
                    m += inputValue
                }
                m += "&lt;/textarea&gt;"

            } else if (InputType == "Checkbox") {
                var x = parseInt(P[1]);
                var y = parseInt(P[2]);
                var inputName = P[3];
                var preCheck = P[4];
                var inputClassValue = P[5] + P[6];
                m = "&lt;input name=&quot;"
                m += inputName
                m += "&quot; id=&quot;"
                m += inputName
                m += "&quot; class=&quot;"
                m += inputClassValue
                m += "&quot; type=&quot;checkbox&quot;"
                if (document.getElementById('ScaleCheckmark').checked) {
                    m += " class=&quot;largerCheckbox&quot;"
                }
                m += " style=&quot;position:absolute; left:"
                var a = parseInt(x - XboxOffset);
                m += a
                m += "px; top:"
                var b = parseInt(y - XboxOffset);
                m += b
                m += "px; &quot;"
                m += "&gt;"

            } else if (InputType == "Xbox") {
                var x0 = parseInt(P[1]);
                var y0 = parseInt(P[2]);
                var width = parseInt(P[9]) + 2;
                var height = width;
                var inputName = P[5];
                var fontFamily = P[6];
                var fontStyle = P[7];
                var fontWeight = P[8];
                var fontSize = P[9];
                var textAlign = P[10];
                var bgColor = P[11];
                var oscarDB = P[12];
                var inputValue = P[13];
                var inputClassValue = P[14] + P[15];
                if (inputClassValue == "") {
                    inputClassValue = "Xbox";
                } //else {inputClassValue += " Xbox";}
                if (inputClassValue == "parent-field") {
                    inputClassValue += " Xbox";
                }
                m = "&lt;input name=&quot;"
                m += inputName
                m += "&quot; id=&quot;"
                m += inputName
                m += "&quot; type=&quot;text&quot; class=&quot;"
                m += inputClassValue
                m += "&quot; style=&quot;position:absolute; left:"
                m += x0;
                m += "px; top:";
                m += y0;
                m += "px;";
                if (width != '14') {
                    m += "width:";
                    m += width + "px; ";
                }
                if (height != '14') {
                    m += "height:";
                    m += height + "px; ";
                }
                if (fontFamily != 'sans-serif') {
                    m += "font-family:";
                    m += fontFamily + "; ";
                }
                if (fontStyle != 'normal') {
                    m += "font-style:";
                    m += fontStyle + "; ";
                }
                // font weight bold and text align center are set in the default CSS style declaration with no override
                if (fontSize != '12') {
                    m += "font-size:";
                    m += fontSize + "; ";
                }
                if ((document.getElementById('BlackBox').checked) && (inputValue == 'X')) {
                    m += "background-color:";
                    m += "rgb(0,0,0)";
                    m += "; ";
                } else if (bgColor != 'white') {
                    m += "background-color:";
                    m += bgColor;
                    m += "; ";
                }
                m += "&quot; "
                if (inputValue) {
                    m += "value=&quot;"
                    m += inputValue
                    m += "&quot;"
                }
                m += "&gt;"

            } else if (InputType == "Signature") {
                var x0 = parseInt(P[1]);
                var y0 = parseInt(P[2]);
                var width = parseInt(P[3]);
                var height = parseInt(P[4]);
                m = "";
                var mstyle = " style=&quot;position:absolute; left:";
                mstyle += x0;
                mstyle += "px; top:"
                mstyle += y0
                mstyle += "px; width:"
                mstyle += width
                mstyle += "px; height:"
                mstyle += height
                mstyle += "px;";

                if (P[5] == "ClassicSignature") {
                    m = "&lt;img id=&quot;signature&quot; src=&quot;${oscar_image_path}BNK.png&quot;"
                    m += mstyle;
                    m += "&quot; &gt;";
                } else {
                    m += "&lt;div id=&quot;Canvas" + P[5] + "&quot; class=&quot;sig&quot;"
                    m += mstyle;
                    m += "; z-index:10;&quot;&gt;\n";
                    m += "&lt;/div&gt;\n";
                    m += "&lt;input type=&quot;hidden&quot; name=&quot;Store" + P[5] + "&quot; id=&quot;Store" + P[5] + "&quot; value=&quot;&quot;&gt;\n";
                    m += "&lt;img id=&quot;" + P[5] + "&quot; src=&quot;${oscar_image_path}BNK.png&quot;"
                    m += mstyle;
                    m += "; z-index:12;&quot;&gt;\n";
                }

            } else if (InputType == "Stamp") {
                var x0 = parseInt(P[1]);
                var y0 = parseInt(P[2]);
                var width = parseInt(P[3]);
                var height = parseInt(P[4]);
                var inputName = P[5];
                var signo = parseInt(P[6]);
                m = "&lt;div style=&quot;position:absolute; left:"
                m += x0
                m += "px; top:"
                m += y0
                m += "px;&quot;&gt;\n"
                m += "&lt;img id=&quot;Stamp&quot; src=&quot;<%=request.getContextPath()%>/eform/displayImage.do?imagefile=BNK.png&quot; width=&quot;"
                m += width
                m += "&quot; height=&quot;"
                m += height
                m += "&quot; onclick=&quot;toggleMe();&quot;&gt;\n&lt;/div&gt;\n"
            }
            textMiddle += m;
            textMiddle += "\n"
        }

        function GetTextBottom() {
            //gender checkboxes
            if ((document.getElementById('preCheckGender').checked) || (document.getElementById('XboxType').checked)) {
                textBottom += "&lt;input name=&quot;PatientGender&quot; id=&quot;PatientGender&quot; type=&quot;hidden&quot; oscarDB=sex&gt;\n"
            }

            //auto load signature images
            if ((document.getElementById('AddStamp').checked) || (document.getElementById('AddStamp2').checked) || (document.getElementById('AddSignatureClassic').checked) || (document.getElementById('AddSignature').checked)) {
                textBottom += "&lt;input type=&quot;hidden&quot; name=&quot;DoctorName&quot; id=&quot;DoctorName&quot; oscarDB=doctor&gt;\n"
                textBottom += "&lt;input type=&quot;hidden&quot; name=&quot;CurrentUserName&quot; id=&quot;CurrentUserName&quot; oscarDB=current_user&gt;\n"
                textBottom += "&lt;input type=&quot;hidden&quot; name=&quot;SubmittedBy&quot; id=&quot;SubmittedBy&quot;&gt;\n"
                textBottom += "&lt;input type=&quot;hidden&quot; name=&quot;user_id&quot; id=&quot;user_id&quot; oscarDB=current_user_id&gt;\n"
                textBottom += "&lt;input type=&quot;hidden&quot; name=&quot;user_ohip_no&quot; id=&quot;user_ohip_no&quot; oscarDB=current_user_ohip_no&gt;\n"
                textBottom += "&lt;input type=&quot;hidden&quot; name=&quot;doctor_no&quot; id=&quot;doctor_no&quot; oscarDB=doctor_provider_no&gt;\n"
                textBottom += "&lt;div id=&quot;signatureDisplay&quot;&gt;&lt;&#47;div&gt;\n"
                textBottom += "&lt;input type=&quot;hidden&quot; name=&quot;signatureValue&quot; id=&quot;signatureValue&quot;&gt;\n"
            }
            //tickler settings
            if (document.getElementById('includeTicklerControl').checked) {
                textBottom += "&lt;input id=&quot;tickler_patient_id&quot; type=&quot;hidden&quot; oscarDB=patient_id&gt;\n"
                textBottom += "&lt;input id=&quot;tickler_send_to&quot; type=&quot;hidden&quot; oscarDB=" + document.getElementById('tickler_send_to').value + "&gt;\n"
                if (!document.getElementById('endUserTicklerWeekAdj').checked) {
                    textBottom += "&lt;input id=&quot;tickler_weeks&quot; type=&quot;hidden&quot; value=&quot;" + document.getElementById('tickler_weeks').value + "&quot;&gt;\n"
                }
                textBottom += "&lt;input id=&quot;tickler_priority&quot; type=&quot;hidden&quot; value=&quot;" + document.getElementById('tickler_priority').value + "&quot;&gt;\n"
                if (document.getElementById('tickler_message').value.length > 0) {
                    textBottom += "&lt;input id=&quot;tickler_message&quot; type=&quot;hidden&quot; value=&quot;" + document.getElementById('tickler_message').value + "&quot;&gt;\n"
                }
            }

            //classic signature
            //if (document.getElementById('AddSignatureClassic').checked){
            //	textBottom +="&lt;div id=&quot;signatureDisplay&quot;&gt;&lt;/div&gt;&lt;input type=&quot;hidden&quot; name=&quot;signatureValue&quot; id=&quot;signatureValue&quot; value=&quot;&quot; &gt;&lt;/input&gt;\n"
            //}

            //bottom submit boxes
            textBottom += "\n\n&lt;div class=&quot;DoNotPrint&quot; id=&quot;BottomButtons&quot; style=&quot;position: absolute; top:"
            textBottom += parseInt(BGHeight);
            textBottom += "px; left:0px;&quot;&gt;\n";
            textBottom += "\t&lt;table&gt;&lt;tr&gt;&lt;td&gt;\n";
            textBottom += "\t\tSubject: &lt;input name=&quot;subject&quot; id=&quot;subject&quot; size=&quot;40&quot; type=&quot;text&quot;&gt; &lt;br&gt; \n";
            //tickler settings
            if (document.getElementById('includeTicklerControl').checked) {
                textBottom += "On Save this form will create a tickler for the ";
                if (document.getElementById('tickler_send_to').value == "current_user_id") {
                    textBottom += "current user";
                } else {
                    textBottom += "MRP";
                }
                textBottom += " of " + document.getElementById('tickler_priority').value + " urgency for ";
                if (!document.getElementById('endUserTicklerWeekAdj').checked) {
                    textBottom += document.getElementById('tickler_weeks').value;
                } else {
                    textBottom += "&lt;input id=&quot;tickler_weeks&quot; size=&quot;3&quot; type=&quot;number&quot; value=&quot;" + document.getElementById('tickler_weeks').value + "&quot;&gt;";
                }
                textBottom += " weeks time. &lt;br&gt;\n";
            }
            //buttons
            textBottom += "\t\t&lt;input value=&quot;Submit&quot; name=&quot;SubmitButton&quot; id=&quot;SubmitButton&quot; type=&quot;button&quot; onclick=&quot;"
            if (document.getElementById('AddSignature').checked) {
                textBottom += " saveSig(); releaseDirtyFlag();&quot;&gt; \n"
                textBottom += "\t\t&lt;input value=&quot;Save Sig&quot; name=&quot;SaveSigButton&quot; id=&quot;SaveSigButton&quot; type=&quot;button&quot; onclick=&quot;saveSig();&quot;&gt; \n"
                textBottom += "\t\t&lt;input value=&quot;Clear Sig&quot; name=&quot;ClearButton&quot; id=&quot;ClearButton&quot; type=&quot;button&quot; onclick=&quot;clearSig();&quot;&gt; \n"
            } else {
                textBottom += " releaseDirtyFlag();&quot;&gt; \n"
            }
            textBottom += "\t\t &lt;input value=&quot;Reset&quot; name=&quot;ResetButton&quot; id=&quot;ResetButton&quot; type=&quot;reset&quot;&gt; \n"
            textBottom += "\t\t	&lt;input value=&quot;Print&quot; name=&quot;PrintButton&quot; id=&quot;PrintButton&quot; type=&quot;button&quot; onclick=&quot;formPrint();&quot;&gt; \n"
            //textBottom += "\t\t	&lt;input value=&quot;Print &amp; Submit&quot; name=&quot;PrintSubmitButton&quot; id=&quot;PrintSubmitButton&quot; type=&quot;button&quot; onclick=&quot;formPrint();releaseDirtyFlag();setTimeout('SubmitButton.click()',1800);&quot;&gt; \n"

            textBottom += "\t&lt;/td&gt;&lt;/tr&gt;&lt;/table&gt;\n"
            textBottom += "\n&lt;!-- Copy Left --&gt;\n"
            textBottom += "&lt;a rel=&quot;liscence&quot; href=&quot;https://www.gnu.org/licenses/gpl.html&quot; target=&quot;blank&quot;&gt;&lt;img alt=&quot;GPLv3&quot; src=&quot;https://www.gnu.org/graphics/gplv3-88x31.png&quot;/&gt;&lt;/a&gt;&lt;br&gt;\n"
            textBottom += "&lt;/div&gt;\n"
            //close pagation
            textBottom += "&lt;/div&gt;\n"
            textBottom += " &lt;/form&gt;\n"

            if (calendars) {
                textBottom += "\n&lt;!-- Define Date Calendars --&gt;\n"
                textBottom += "&lt;script type=&quot;text/javascript&quot;&gt;\n"
                for (j = 0; (j < (DrawData.length)); j++) {
                    var P = DrawData[j].split("|");
                    if ((P[0] == "Text") && ((P[5].indexOf("day") > -1) || (P[5].indexOf("date") > -1)) && (P[5] !== "dob_day") && (P[5] !== "today_rx")) {
                        textBottom += "\tCalendar.setup( { inputField : &quot;" + P[5] + "&quot;, ifFormat : &quot;%Y-%m-%d&quot;,  button : &quot;" + P[5] + "&quot; } );\n"
                    }
                }
                textBottom += "&lt;/script&gt;\n\n";
            }


            //Peter Hutten-Czapski's Xbox scripts
            if (xPresent) {
                textBottom += "\n&lt;!-- Xbox Script --&gt;\n";
                textBottom += "&lt;script type=&quot;text/javascript&quot;&gt;\n";
                textBottom += "\tvar b = document.getElementsByClassName('Xbox');\n";
                textBottom += "\tfor (var i = 0; i &lt; b.length; i++) {\n";
                textBottom += "\t\tb[i].onclick = function(){\n";
                textBottom += "\t\t\tlet x = this.id;\n";
                textBottom += "\t\t\tif (document.getElementById(x).value.length &lt; 1) {\n";
                textBottom += "\t\t\t\tdocument.getElementById(x).value ='X';\n"
                if (document.getElementById('BlackBox').checked) {
                    textBottom += "\t\t\t\tdocument.getElementById(x).classList.add('on');\n";
                }
                textBottom += "\t\t\t} else {\n";
                textBottom += "\t\t\t\tdocument.getElementById(x).value ='';\n"
                if (document.getElementById('BlackBox').checked) {
                    textBottom += "\t\t\t\tdocument.getElementById(x).classList.remove('on');\n";
                }
                textBottom += "\t\t\t}\n";
                textBottom += "\t\t\treturn false;\n";
                textBottom += "\t\t}\n";
                textBottom += "\t}\n";
                textBottom += "&lt;/script&gt;\n\n";
            }
            if (radioPresent) {
                textBottom += "\n&lt;!-- Radio Script --&gt;\n";
                textBottom += "&lt;script type=&quot;text/javascript&quot;&gt;\n";
                textBottom += "\tvar r = document.getElementsByClassName('Radio');\n";
                textBottom += "\tfor ( var i = 0; i &lt; r.length; i++) {\n";
                textBottom += "\t\tr[i].onclick = function(){\n";
                textBottom += "\t\t\tlet x = this.id;\n";
                textBottom += "\t\t\tlet series = '';\n";
                textBottom += "\t\t\tlet classes = document.getElementById(x).getAttribute('class').split(' ');\n";
                textBottom += "\t\t\tfor( j = 0; j < classes.length; j++ ) {\n";
                textBottom += "\t\t\t\tif (classes[j].indexOf('ly-one-') > 0) {let x = this.id;\n";
                textBottom += "\t\t\t\t\tseries='.'+classes[j];;\n";
                textBottom += "\t\t\t\t}\n";
                textBottom += "\t\t\t}\n";
                textBottom += "\t\t\tif (series==''){ console.log('missing only-one-x class'); return;}\n";
                textBottom += "\t\t\tvar partA=x.substring(0, x.indexOf('_'));\n";
                textBottom += "\t\t\tlet items=document.querySelectorAll(series);\n";
                textBottom += "\t\t\tfor(let item of items){\n";
                textBottom += "\t\t\t\tif(x!=item.id) {\n";
                textBottom += "\t\t\t\t\titem.value='';\n";
                textBottom += "\t\t\t\t\titem.removeAttribute('required');\n";
                if (document.getElementById('BlackBox').checked) {
                    textBottom += "\t\t\t\t\titem.classList.remove('on');\n";
                }
                textBottom += "\t\t\t\t} else {\n";
                textBottom += "\t\t\t\t\tif (document.getElementById(x).value !='X') {\n";
                textBottom += "\t\t\t\t\t\tdocument.getElementById(x).value = 'X';\n";
                if (document.getElementById('BlackBox').checked) {
                    textBottom += "\t\t\t\t\t\tdocument.getElementById(x).classList.add('on');\n";
                }
                textBottom += "\t\t\t\t\t} else {\n";
                textBottom += "\t\t\t\t\t\tdocument.getElementById(x).value ='';\n";
                if (document.getElementById('BlackBox').checked) {
                    textBottom += "\t\t\t\t\t\tdocument.getElementById(x).classList.remove('on');\n";
                }
                textBottom += "\t\t\t\t\t}\n";
                textBottom += "\t\t\t\t}\n";
                textBottom += "\t\t\t}\n";
                textBottom += "\t\t\}\n";
                textBottom += "\t} \n";

                textBottom += "&lt;/script&gt;\n\n";
            }


            //</body></html>
            textBottom += "&lt;/body&gt;\n&lt;/html&gt;\n";
        }

        //load generated eform code in new window
        function popUp() {

            textTop = "";
            GetTextTop();

            textMiddle = "";
            var m = ""
            for (j = 0; (j < (DrawData.length)); j++) {
                var GetTextMiddleParameter = DrawData[j].split("|");
                GetTextMiddle(GetTextMiddleParameter);
            }


            textBottom = "";
            GetTextBottom();

            text = textTop + textMiddle + textBottom;

//popup modified at this point PHC
            return unescape(text);
        }

        //this function used for injecting html in to Edit E-Form in efmformmanageredit.jsp w/ variable formHtml
        function injectHtml() {
            document.getElementById('formHtmlG').value = popUp();
            document.getElementById('formHtmlName').value = document.getElementById('eFormName').value;
            document.getElementById('toSave').submit();
        }

    </script>
    <!-- back to boilerplate -->

    <!-- mousefunction.js -->
    <script type="text/javascript">
        var mousex = 0;
        var mousey = 0;
        var grabx = 0;
        var graby = 0;
        var orix = 0;
        var oriy = 0;
        var elex = 0;
        var eley = 0;
        var algor = 0;

        var dragobj = null;

        function falsefunc() {
            return false;
        } // used to block cascading events

        function init() {
            document.onmousemove = update; // update(event) implied on NS, update(null) implied on IE
            update();
        }

        function getMouseXY(e) // works on IE6,FF,Moz,Opera7
        {
            if (!e) e = window.event; // works on IE, but not NS (we rely on NS passing us the event)

            if (e) {
                if (e.pageX || e.pageY) { // this doesn't work on IE6!! (works on FF,Moz,Opera7)
                    mousex = e.pageX;
                    mousey = e.pageY;
                    algor = '[e.pageX]';
                    if (e.clientX || e.clientY) algor += ' [e.clientX] '
                } else if (e.clientX || e.clientY) { // works on IE6,FF,Moz,Opera7
                    // Note: I am adding together both the "body" and "documentElement" scroll positions
                    //       this lets me cover for the quirks that happen based on the "doctype" of the html page.
                    //         (example: IE6 in compatibility mode or strict)
                    //       Based on the different ways that IE,FF,Moz,Opera use these ScrollValues for body and documentElement
                    //       it looks like they will fill EITHER ONE SCROLL VALUE OR THE OTHER, NOT BOTH
                    //         (from info at http://www.quirksmode.org/js/doctypes.html)
                    mousex = e.clientX + document.body.scrollLeft + document.documentElement.scrollLeft;
                    mousey = e.clientY + document.body.scrollTop + document.documentElement.scrollTop;
                    algor = '[e.clientX]';
                    if (e.pageX || e.pageY) algor += ' [e.pageX] '
                }
                if (e.preventDefault) {
                    e.preventDefault();
                } else {
                    window.event.returnValue = false;
                    window.event.cancelBubble = true
                }
            }
        }

        function grab(context) {
            document.onmousedown = falsefunc; // in NS this prevents cascading of events, thus disabling text selection
            dragobj = context;
            dragobj.style.zIndex = 10; // move it to the top
            document.onmousemove = drag;
            document.onmouseup = drop;
            grabx = mousex;
            graby = mousey;
            elex = orix = dragobj.offsetLeft;
            eley = oriy = dragobj.offsetTop;
            update();
        }

        function drag(e) // parameter passing is important for NS family
        {
            if (dragobj) {
                elex = orix + (mousex - grabx);
                eley = oriy + (mousey - graby);
                dragobj.style.position = "absolute";
                dragobj.style.left = (elex).toString(10) + 'px';
                dragobj.style.top = (eley).toString(10) + 'px';
            }
            update(e);
            return false; // in IE this prevents cascading of events, thus text selection is disabled
        }

        function drop() {
            if (dragobj) {
                dragobj.style.zIndex = 0;
                dragobj = null;
            }
            update();
            document.onmousemove = update;
            document.onmouseup = null;
            document.onmousedown = null;   // re-enables text selection on NS

        }

        function update(e) {
            getMouseXY(e); // NS is passing (event), while IE is passing (null)
        }

    </script>

    <!-- js graphics scripts -->
    <script type="text/javascript" src="<%=request.getContextPath()%>/library/eforms/jsgraphics.js"></script>

</head>

<!-- resetAll() -->
<body style="font-family: sans-serif;" onload="init(); resetAll(); hide('all');
    <% if (eformGeneratorIndivicaSignatureEnabled) { %>
        show('classic');
    <% } %> ">

<img id="BGImage" src="<%= request.getContextPath() %>/images/transparent_icon.gif" alt="BGImage" style="position: absolute; left: 0px; top: 0px; "
     onmouseover="SetDrawOn();"
     onmouseout="SetDrawOff();"
     onmousedown="if (event.preventDefault) event.preventDefault(); SetMouseDown();SetStart();"
     onmousemove=""
     onmouseup="SetMouseUp(); DrawMarker();loadInputList();"
     onload="finishLoadingImage()">


<!-- this form  used for injecting html in to Edit E-Form  efmformmanageredit.jsp -->
<form method="post" action="efmformmanageredit.jsp" id="toSave">
    <input type="hidden" name="formHtmlName" id="formHtmlName"/>
    <input type="hidden" name="formHtmlG" id="formHtmlG"/>
</form>

<form method="post" name="generator" id="generator">

    <div id="Wizard" class="DoNotPrint"
         style="position: absolute; left:750px; top: 0px; width: 500px; padding:5px; height: 1010px; overflow-y: scroll;">
        <h2><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.title"/> 7.4</h2>
        <span class="h1"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.title"/></span>
        <a onclick="show('all');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.expandAll"/></a>/
        <a onclick="hide('all');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.collapseAll"/></a>
        <p><label for="snappiness">Snap to grid (0 is off)</label>
            <select name="snappiness" id="snappiness"
                    onchange='snap = document.getElementById("snappiness").value; console.log(snap)'>
                <option>0</option>
                <option>4</option>
                <option>5</option>
                <option selected>6</option>
                <option>7</option>
                <option>8</option>
                <option>9</option>
                <option>10</option>
            </select>

        <hr>
        <span class="h2">1. <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.loadImage"/>:</span> <a
            onclick="show('Section1');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.expand"/></a>/<a
            onclick="hide('Section1');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.collapse"/></a>
        <div id="Section1">
            <p>
                <select name="imageName" id="imageName">
                    <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.imageChooseSelect"/>...</option>
                    <%
                        /**
                         This function/scriplet looks in the images directory and populates the selection
                         so that the user can select which image they want to use for generating an eform
                         */
                        String imagePath = OscarProperties.getInstance().getEformImageDirectory();
                        if (imagePath == null) {
                            MiscUtils.getLogger().debug("Please provide a valid image path for EFORM_IMAGES_DIR in properties");
                        }

                        // Fetch file names from the specified directory
                        String[] fileINames = new File(imagePath).list();
                        if (fileINames == null) {
                            MiscUtils.getLogger().debug("Strange, no files found in the supplied EFORM_IMAGES_DIR directory");
                            fileINames = new String[0]; // Initialize to an empty array to avoid NullPointerException
                        } else {
                            Arrays.sort(fileINames); // Sort only if fileINames is not null
                        }

                        // Populate the dropdown options
                        for (int i = 0; i < fileINames.length; i++) {
                    %>
                    <option value="<%= fileINames[i] %>"><%= fileINames[i] %>
                    </option>
                    <%
                        }
                    %>
                </select>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.page"/>
                <input type="text" name="page" id="page" style="width:30px" value="" readonly>
            </p>


            <!-- <p><b>Image Name:</b><input type="text" name="imageName" id="imageName"></p> -->
            <p> - <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.imageUploadPrompt"/> <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.imageUploadLink"/></p>
            <p><b>Orientation of form:</b><br>
                <input type="radio" name="Orientation" id="OrientPortrait" value="750" checked><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.imagePortrait"/><br>
                <input type="radio" name="Orientation" id="OrientLandscape" value="1000"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.imageLandscape"/><br>
                <input type="radio" name="Orientation" id="OrientCustom" value="CustomWidth"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.imageCustom"/> <input type="text" name="OrientCustomValue"
                                                                  id="OrientCustomValue" style="width:100px;">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.imageEnterInteger"/><br>
                <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.imageLoadButton"/>"
                       onClick="loadImage();">
            </p>
            <p><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.image.RedOutlinehint"/></p>

        </div>

        <hr>

        <span class='h2'>2. <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.eFormName"/></span> <a
            onclick="show('Section2');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.expand"/></a>/<a
            onclick="hide('Section2');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.collapse"/></a>
        <div id="Section2">
            <p><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.nameInstruction"/><input type="text" name="eFormName" id="eFormName">
            </p>
        </div>

        <hr>

        <span class='h2'>3. <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.gender"/> Radio and Parent Child boxes</span> <a
            onclick="show('Section3');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.expand"/></a>/<a
            onclick="hide('Section3');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.collapse"/></a>
        <div id="Section3">
            <p><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.genderCheckbox"/><br> <input name="preCheckGender" id="preCheckGender"
                                                                              type="checkbox"
                                                                              onclick="toggleView(this.checked,'Section3a');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.GenderCheckbox"/><br>
                <input name="XboxType" id="XboxType" type="checkbox"
                       onclick="toggleView(this.checked,'Section3a');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.GenderXbox"/>
            </p>
            <div id="Section3a" style="display:none">
                <table>
                    <tr>
                        <td><span><b><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.genderMale"/></b>: </span></td>
                        <td><input name="Male" id="Male" type="button"
                                   value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.genderMaleButton"/>'
                                   onclick="SetSwitchOn(this.id);"></td>
                    </tr>
                    <tr>
                        <td><span><b><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.genderFemale"/></b>:</span></td>
                        <td><input name="Female" id="Female" type="button"
                                   value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.genderFemaleButton"/>'
                                   onclick="SetSwitchOn(this.id);"></td>
                    </tr>
                </table>
            </div>

            <p><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.radio"/><br>
                <!-- <input name="radio" id="radio" type="checkbox" onclick="toggleView(this.checked,'Section3b');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.radioCheckbox"/><br> -->
                <input name="radioX" id="radioX" type="checkbox"
                       onclick="toggleView(this.checked,'Section3b');document.getElementById('bgColor').value='white';">Add
                Radio Xboxes</p>

            <div id="Section3b" style="display:none">
                <span><b><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.radioLabel"/></b>: </span>
                <input name="RadioButton" id="RadioButton" type="button"
                       value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.radioButton"/>' onclick="SetSwitchOn(this.id);">
                <br>
                <span><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.radioHint"/></span><input type="text" name="RadioName"
                                                                                  id="RadioName" style="width:200px;"
                                                                                  value="radio">
            </div>
            <p><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.parent"/><br> <input name="parentchild" id="parentchild"
                                                                      type="checkbox"
                                                                      onclick="toggleView(this.checked,'Section3c');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.parentCheckbox"/></p>
            <div id="Section3c" style="display:none">
                <table>
                    <tr>
                        <td><span><b><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.parentLabel"/></b>: </span></td>
                        <td><input name="Parent" id="Parent" type="button"
                                   value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.parentButton"/>'
                                   onclick="parentcounter += 1; document.getElementById('Checkbox').click(); document.getElementById('inputClass').value = 'parent-field'; document.generator.InputNameType[1].checked=true; document.getElementById('inputName').value ='parent' + parentcounter; document.getElementById('inputParentclass').value ='';">
                        </td>

                    </tr>
                    <tr>
                        <td><span><b><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.childLabel"/></b>: </span></td>
                        <td><input name="Child" id="Child" type="button"
                                   value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.childButton"/>'
                                   onclick=" document.getElementById('inputClass').value = 'child-'; document.getElementById('inputParentclass').value ='parent' + parentcounter; document.getElementById('InputNameAuto').click();">
                        </td>

                    </tr>
                </table>
            </div>
        </div>
        <hr>


        <span class='h2'>4. <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.signature"/></span><a
            onclick="show('Section4');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.expand"/></a>/<a
            onclick="hide('Section4');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.collapse"/></a>
        <div id="Section4">
            <input type="checkbox" name="AddStamp2" id="AddStamp2"
                   onclick="toggleView(this.checked,'Section4e');toggleView(this.checked,'Section4f');"><span><b>Add Signature Stamps to this form</b></span><br>
            <div id="Section4e" style="display:none">
                <input type="radio" name="D" id="Delegation" checked><span><b>MRP Signature by Delegation</b> If no sig file Sig of MRP used</span><br>
                <input type="radio" name="D" id="Strict"><span><i>Strict User Signatures</i> Only signed in users can stamp</span>
            </div>
            <div id="Section4f" style="display:none">
                <input type="button" name="AddSignatureBox3" id="AddSignatureBox3" style="width:400px; color:red"
                       value="Click here, then drag a box around the signature area"
                       onclick="SetSwitchOn('Stamp');document.getElementById('AddStamp2').disabled=true; document.getElementById('AddSignatureBox3').disabled=true;"><br>
                <span>Signatures image files <code>consult_sig_xxx.png</code> where xxx is the OSCAR provider no., are uploaded to eform images</span><br>
            </div>
            <span id="classic" style="display:none">
		<br>
		<input type="checkbox" name="AddSignatureClassic" id="AddSignatureClassic"
               onclick="	toggleView(this.checked,'Section4d');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.classic"/>
		<br>
	</span>
            <div id="Section4d" style="display:none">
                <input type="button" name="AddClassicSignatureBox" id="AddClassicSignatureBox" style="width:400px"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.signatureLocationButton"/>"
                       onclick="SetSwitchOn('ClassicSignature');document.getElementById('AddSignatureClassic').disabled=true; document.getElementById('AddClassicSignatureBox').disabled=true;">
                <br>
            </div>
            <br>
            <span>
	<input type="checkbox" name="AddSignature" id="AddSignature"
           onclick="	toggleView(this.checked,'Section4a');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.freehand"/>
                <!-- Add A Freehand Signature area to this form--> <br>
    </span>
            <div id="Section4a" style="display:none">
                <input type="button" name="AddSignatureBox1" id="AddSignatureBox1" style="width:400px"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.signatureLocationButton"/>"
                       onclick="SetSwitchOn('SignatureBox');document.getElementById('AddSignature').disabled=true; ">
                <br>Signature Color
                <select id="sigColor" onchange="SignatureColor=document.getElementById('sigColor').value;">
                    <option value="black" selected>black</option>
                    <option value="blue">blue</option>
                    <option value="green">green</option>
                    <option value="red">red</option>
                    <option value="orange">orange</option>
                    <option value="purple">purple</option>
                    <option value="brown">brown</option>
                </select>
                Signature Line Color
                <select id="sigLineColor" onchange="SignatureLineColor=document.getElementById('sigLineColor').value;">
                    <option value="rgba(255, 255, 255, 0.0)" selected>none</option>
                    <option value="#000000">black</option>
                    <option value="#0000FF">blue</option>
                    <option value="#00FF00">green</option>
                    <option value="#FF0000">red</option>
                </select><br>
                Boundary Type
                <select id="sigBorderType"
                        onchange="SignatureBorder='2px '+document.getElementById('sigBorderType').value+' '+document.getElementById('sigBorderColor').value;">
                    <option value="none">none</option>
                    <option value="dotted" selected>dotted</option>
                    <option value="dashed">dashed</option>
                    <option value="solid">solid</option>
                    <option value="double">double</option>
                    <option value="groove">groove</option>
                </select>
                Boundary Color
                <select id="sigBorderColor"
                        onchange="SignatureBorder='2px '+document.getElementById('sigBorderType').value+' '+document.getElementById('sigBorderColor').value;">
                    <option value="black">black</option>
                    <option value="blue" selected>blue</option>
                    <option value="green">green</option>
                    <option value="red">red</option>
                    <option value="orange">orange</option>
                    <option value="yellow">yellow</option>
                    <option value="purple">purple</option>
                    <option value="brown">brown</option>
                </select><br>
            </div>


            <input type="checkbox" name="AddStamp" id="AddStamp"
                   onclick="	toggleView(this.checked,'Section4b');toggleView(this.checked,'Section4c');"><span><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.stamp"/></span><br>
            <div id="Section4b" style="display:none">
                <input type="button" name="AddSignatureBox2" id="AddSignatureBox2" style="width:400px"
                       value="Click here, then drag a box around the signature area"
                       onclick="SetSwitchOn('Stamp');document.getElementById('AddStamp').disabled=true; document.getElementById('AddSignatureBox2').disabled=true;">
            </div>
            <div id="Section4c" style="display:none">
                <br><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.signatureFragment"/>
                <input type="text" name="UserList" id="UserList" style="width:200px;">
                <br><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.signatureImage"/>
                <input type="text" name="SignatureList" id="SignatureList" style="width:200px;">
                <br>
                <input type="button" name="AddToUserSignatureList" id="AddToUserSignatureList"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.signatureAddButton"/>"
                       onclick="addToUserSignatureList();">
                <input type="button" name="EmptyUserSignatureList" id="EmptyUserSignatureList"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.signatureEmptyButton"/>"
                       onclick="emptyUserSignaturelist()">
                <br>
                <ul id="UserSignatureList"
                    style="list-style-type:none; list-style: none; margin-left: 50px; padding-left: 1em; text-indent: -1em">
                    <li>
                        zapski|PHC.png
                    </li>
                    <li>
                        acasse|MLL.png
                    </li>
                    <li>
                        mith|PJS.png
                    </li>
                    <li>
                        urrie|LNC.png
                    </li>
                    <li>
                        awson|HAL.png
                    </li>
                    <li>
                        ears|STS.png
                    </li>
                    <li>
                        eller|MKK.png
                    </li>
                </ul>
            </div>

        </div>

        <hr>


        <span class='h2'>5. <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.input"/></span> <a
            onclick="show('Section5');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.expand"/></a>/<a
            onclick="hide('Section5');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.collapse"/></a>
        <div id="Section5">
            <span class='h3'><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputType"/></span>
            <p>
                <input type="radio" name="inputType" id="Text" value="text"
                       onclick="hide('SectionPrecheck');show('SectionCustomText');show('SectionDatabase');show('SectionImportMeasurements');show('wtalign');show('SectionCustomText');show('iiimeasures');show('c_formating');SetSwitchOn(this.id);document.getElementById('bgColor').value='transparent';"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeText"/>
                <input type="radio" name="inputType" id="Textbox" value="textarea"
                       onclick="hide('SectionPrecheck');show('SectionCustomText');show('SectionDatabase');show('wtalign');show('SectionCustomText');show('c_formating');SetSwitchOn(this.id);"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeTextArea"/>
                <input type="radio" name="inputType" id="Checkbox" value="checkbox"
                       onclick="show('SectionPrecheck');hide('SectionCustomText');hide('SectionDatabase');hide('SectionImportMeasurements');hide('iiimeasures');hide('c_formating');SetSwitchOn(this.id);"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeCheckbox"/>
                <input type="radio" name="inputType" id="Xbox" value="Xbox"
                       onclick="show('SectionPrecheck');show('SectionCustomText');hide('SectionDatabase');hide('SectionImportMeasurements');hide('wtalign');show('c_formating');hide('iiimeasures');SetSwitchOn(this.id);document.getElementById('bgColor').value='white';"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeXbox"/>
            </p>

            <span class='h3'><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputAuto"/></span>
            <ul style="list-style-type:none">
                <li id='SectionCustomText' style="display:block">
                    <input type="radio" name="AutoPopType" id="AutoPopCustom" value="custom"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeCustom"/><input type="text" name="inputValue" id="inputValue"
                                                                     value=""></li>
                <li id='SectionDatabase' style="display:block">
                    <input type="radio" name="AutoPopType" id="AutoPopDatabase" value="database"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeData"/>
                    <select name="oscarDB" id="oscarDB">
                        <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeDataButton"/></option>
                        <%
                            EFormLoader names = EFormLoader.getInstance();
                            //return the array with a list of names from database
                            List<String> kout = names.getNames();
                            for (String str : kout) { %>
                        <option value="<%= str %>"><%= str %>
                        </option>
                        <%
                            }
                        %>
                    </select></li>
                <li id="SectionImportMeasurements" style="display:block;">
                    <input type="radio" name="AutoPopType" id="AutoPopMeasurements" value="measurements"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeMeasurements"/><br>
                    <table>
                        <tr>
                            <td>
                                <p><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeMeasurementsType"/><br>
                                    <select name="MeasurementList" id="MeasurementList">
                                        <option value="" selected="selected"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeMeasurementsButton"/></option>
                                        <option value="HT">HT</option>
                                        <option value="WT">WT</option>
                                        <option value="BP">BP</option>
                                        <option value="BMI">BMI</option>
                                        <option value="WAIS">WAIS (waist)</option>
                                        <option value="WC">WC (waist circ.)</option>
                                        <option value="G">Gravida</option>
                                        <option value="P">Para</option>
                                        <option value="LMP">LMP</option>
                                        <option value="SMK">Smoking</option>
                                        <option value="HbAi">HbAi</option>
                                        <option value="A1C">A1C</option>
                                        <option value="FBS">FBS</option>
                                        <option value="TG">TG</option>
                                        <option value="LDL">LDL</option>
                                        <option value="HDL">HDL</option>
                                        <option value="TCHD">TCHD</option>
                                        <option value="TCHL">TCHL</option>
                                        <option value="EGFR">EGFR</option>
                                        <option value="SCR">SCR (Cr)</option>
                                        <option value="ACR">ACR</option>
                                    </select>
                                </p>
                            </td>
                            <td><p><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeMeasurementsCustom"/><br>
                                <input type="text" name="MeasurementCustom" id="MeasurementCustom" style="width:50px;">
                            </p>
                            </td>
                            <td>
                                <p><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeMeasurementsField"/><br>
                                    <select name="MeasurementField" id="MeasurementField">
                                        <option value="value"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeMeasurementsFieldButtonValue"/></option>
                                        <option value="dateObserved"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeMeasurementsFieldButtonDateObserved"/></option>
                                        <option value="comments"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeMeasurementsFieldButtonComment"/></option>
                                    </select>
                                </p>
                            </td>
                        </tr>
                    </table>
                </li>
                <li id='SectionPrecheck' style="display:none"><input name="preCheck" id="preCheck"
                                                                     type="checkbox"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.precheck"/></li>

            </ul>


            <span id="c_formating"><span class='h3'><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormat"/></span>
			<br>
			<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatFont"/>
				<select id="fontFamily">
					 <option value="sans-serif"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatSelectSans"/></option>
					 <option value="serif"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatSelectSerif"/></option>
					 <option value="monospace"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatSelectMono"/></option>
				</select>
			<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatStyle"/>
				<select id="fontStyle">
					 <option value="normal"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatStyleNormal"/></option>
					 <option value="italic"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatStyleItalic"/></option>
				</select>
			<span id="wtalign"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatWeight"/>
				<select id="fontWeight">
					 <option value="normal"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatStyleNormal"/></option>
					 <option value="bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatWeightBold"/></option>
					 <option value="bolder"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatWeightBolder"/></option>
					 <option value="lighter"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatWeightLighter"/></option>
				</select>
			<br></span>
			<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatSize"/><input type="text" name="fontSize" id="fontSize"
                                                                       style="width:50px" value="12"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatSizehint"/>
			<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatAlign"/>
				<select id="textAlign">
					<option value="left"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatAlignLeft"/></option>
					<option value="center"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatAlignCenter"/></option>
					<option value="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatAlignRight"/></option>
					<option value="justify"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatAlignJustify"/></option>
				</select>
			<br>
			<br><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatBackground"/>
				<select id="bgColor">
					<option value="transparent"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatBackgroundTransparent"/></option>
					<option value="white"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatBackgroundWhite"/></option>
				</select><br>
				- <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputFormatBackgroundhint"/>




	<span class='h3'><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputName"/></span>
	    <br>
	    			<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputClass"/>
        				<select id="inputClass">
        					 <option value="" selected><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputClassNone"/></option>
        					 <option value="parent-field"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputClassParent"/></option>
        					 <option value="child-"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputClassChild"/></option>
        				</select>
        		    <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputParentclass"/><input type="text" name="inputParentclass"
                                                                                id="inputParentclass"
                                                                                style="width:100px" value="">

        <br>
		<br><input type="radio" name="InputNameType" id="InputNameAuto" value="Auto" checked><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputNameSeq"/><br>
				- <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputNameSeqPrefix"/><input type="text" name="AutoNamePrefix"
                                                                                id="AutoNamePrefix" style="width:100px"
                                                                                value="AutoName"><br>
			<input type="radio" name="InputNameType" id="InputNameCustom" value="Custom"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputNameSeqCustom"/>
				<input type="text" name="inputName" id="inputName">
				<br>
				- <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputNameSeqCustomhint1"/><br>
				- <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputNameSeqCustomhint2"/>
			<br></span>
            <span id="iiimeasures"><input type="radio" name="InputNameType" id="InputNameMeasurement"
                                          value="Measurement"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputNameMeasurement"/></span><br>
            <table>
                <tr>
                    <td>
                        <p><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputNameMeasurementType"/><br>
                            <select name="ExportMeasurementList" id="ExportMeasurementList">
                                <option value="" selected="selected"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputNameMeasurementButton"/></option>
                                <option value="HT">HT</option>
                                <option value="WT">WT</option>
                                <option value="BP">BP</option>
                                <option value="BMI">BMI</option>
                                <option value="WAIS">WAIS (waist)</option>
                                <option value="WC">WC (waist circ.)</option>
                                <option value="G">Gravida</option>
                                <option value="P">Para</option>
                                <option value="LMP">LMP</option>
                                <option value="SMK">Smoking</option>
                                <option value="HbAi">HbAi</option>
                                <option value="A1C">A1C</option>
                                <option value="FBS">FBS</option>
                                <option value="TG">TG</option>
                                <option value="LDL">LDL</option>
                                <option value="HDL">HDL</option>
                                <option value="TCHD">TCHD</option>
                                <option value="TCHL">TCHL</option>
                                <option value="EGFR">EGFR</option>
                                <option value="SCR">SCR (Cr)</option>
                                <option value="ACR">ACR</option>
                            </select>
                            <br>
                    </td>
                    <td><br><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputNameMeasurementsCustom"/><br>
                        <input type="text" name="ExportMeasurementCustom" id="ExportMeasurementCustom"
                               style="width:50px;">
                        <br>
                    </td>
                    <td>
                        <br><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputNameMeasurementsField"/><br>
                        <select name="ExportMeasurementField" id="ExportMeasurementField">
                            <option value="value"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeMeasurementsFieldButtonValue"/></option>
                            <option value="dateObserved"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeMeasurementsFieldButtonDateObserved"/></option>
                            <option value="comments"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputTypeMeasurementsFieldButtonComment"/></option>
                        </select>
                        <br>
                    </td>
                </tr>
            </table>
            <br>
            <span class='h3'><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputDraw"/></span>
            <br>
            <span class='h4'><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputDrawText"/></span>
            <br>
            - <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputDrawTexthint"/><br>
            <br>
            <span class='h4'><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputDrawCheckbox"/></span>
            <br>
            - <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputDrawCheckboxhint"/><br>
            <br>
            <br><input type="button" onclick="Undo();" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputDrawUndoButton"/>'><br>
            <br><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.inputDrawhint"/><br>

        </div>

        <hr>
        <span class='h2'>6. <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuning"/></span><a
            onclick="show('Section6');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.expand"/></a>/<a
            onclick="hide('Section6');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.collapse"/></a>
        <div id="Section6">

            <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningShowButton"/>'
                   onclick="ToggleInputName();"><br>
            <table style="text-align:center; border: 1px solid black;">
                <tr>
                    <td style="background-color:#dddddd;">
                        <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningNoneButton"/>'
                               onclick="uncheckList('InputChecklist');"><br>
                        <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningAllButton"/>'
                               onclick="checkList('InputChecklist');">
                    </td>
                    <td>
                        <span><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningUpButton"/></span><br>
                        <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningAlignButton"/>'
                               style="width:100px;" onclick="alignInput('top');"><br>
                        <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningShiftButton"/> [alt]&uarr;'
                               style="width:100px;" onclick="changeInput('up',10);"><br>
                        <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningNudgeButton"/> &uarr;'
                               style="width:100px;" onclick="changeInput('up',1);">
                    </td>
                    <td style="background-color:#dddddd;">
                        <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningDeleteButton"/>'
                               Style="width:100px;" onclick="deleteInput();">
                    </td>
                </tr>
                <tr>
                    <td>
                        <span><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningLeft"/></span><br>
                        <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningAlignButton"/>'
                               style="width:50px;" onclick="alignInput('left');">
                        <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningShiftButton"/>'
                               style="width:50px;" onclick="changeInput('left',10);">
                        <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningNudgeButton"/>'
                               style="width:50px;" onclick="changeInput('left',1);">
                    </td>
                    <td style="text-align:left;">
                        <ul id="InputList"
                            style="list-style-type:none; list-style: none; margin-left: 0; padding-left: 1em; text-indent: -1em"></ul>
                    </td>
                    <td>
                        <span><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningRight"/></span><br>
                        <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningNudgeButton"/>'
                               style="width:50px;" onclick="changeInput('right',1);">
                        <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningShiftButton"/>'
                               style="width:50px;" onclick="changeInput('right',10);">
                        <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningAlignButton"/>'
                               style="width:50px;" onclick="alignInput('right');">
                    </td>
                </tr>
                <tr>
                    <td style="background-color:#dddddd;">
                        <span><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningWidth"/></span><br>
                        <input type="button"
                               value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningIncreaseButton"/>  &uArr;+&rarr;'
                               style="width:120px;" onclick="changeInput('width',1);"><br>
                        <input type="button"
                               value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningDecreaseButton"/>  &uArr;+&larr;'
                               style="width:120px;" onclick="changeInput('width',-1);">
                    </td>
                    <td>
                        <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningNudgeButton"/> &darr;'
                               style="width:100px;" onclick="changeInput('down',1);"><br>
                        <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningShiftButton"/> [alt]&darr;'
                               style="width:100px;" onclick="changeInput('down',10);"><br>
                        <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningAlignButton"/>'
                               style="width:100px;" onclick="alignInput('bottom');"><br>
                        <span><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningDown"/></span>
                    </td>
                    <td style="background-color:#dddddd;">
                        <span><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningHeight"/></span><br>
                        <input type="button"
                               value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningIncreaseButton"/> &uArr;+&darr;'
                               style="width:120px;" onclick="changeInput('height',1);"><br>
                        <input type="button"
                               value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.tuningDecreaseButton"/> &uArr;+&uarr;'
                               style="width:120px;" onclick="changeInput('height',-1);">
                    </td>
                </tr>
            </table>

        </div>
        <hr>
        <span class='h2'>7. <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.misc"/></span><a onclick="show('Section7');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.expand"/></a>/<a onclick="hide('Section7');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.collapse"/></a>
        <div id="Section7">
            <p><span class="h2"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.miscMax"/></span><br>
                <input name="maximizeWindow" id="maximizeWindow" type="checkbox"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.miscMaxhint"/>
            </p>
            <p><span class="h2">Hide Headers/Footers on Print</span><br>
                <input name="removeHeadersFooters" id="removeHeadersFooters" type="checkbox">Removes headers/footers on
                print (so your OSCAR link isn't displayed on the printed eForm...)
            </p>
            <p><span class='h2'><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.date"/></span><br>
                <input name="AddDate" id="AddDate" type="checkBox" checked><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.dateDescription"/>
            </p>
            <p><span class='h2'><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.miscCheckmarks"/></span><br>
                <input name="BlackBox" id="BlackBox" type="checkbox">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.BlackBox"/>
                <br>

                <input name="ScaleCheckmark" id="ScaleCheckmark" type="checkbox"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.miscCheckmarksScale"/><br>
                <input name="DefaultCheckmark" id="DefaultCheckmark" type="checkbox" style="display:none"><span
                        style="display:none"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.miscCheckmarksDraw"/></span>
            </p>
            <p <%=eformGeneratorIndivicaFaxEnabled ? "" : "style=\"display: none;\"" %>>
                <span class='h2'><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.fax"/></span><br>
                <input name="includeFaxControl" id="includeFaxControl" type="checkBox"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.faxDescription"/><br>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.faxnumber"/>: <input type="text" name="faxno" id="faxno"
                                                                       style="width:200px;">
            </p>

            <div id='pdfOption' <%=eformGeneratorIndivicaPrintEnabled ? "" : "style=\"display: none;\"" %>>
                <p><span class='h2'><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.PDFprint"/></span><br>
                    <input name="includePdfPrintControl" id="includePdfPrintControl" type="checkBox" checked>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.includePDFprint"/>
                </p>
            </div>
            <div id='ticklerOptions'>
                <p><span class='h2'>Tickler Options</span><br>
                    <input name="includeTicklerControl" id="includeTicklerControl" type="checkBox">
                    Include automatic ticklers (requires PDF print)<br>
                    Priority
                    <select name="tickler_priority" id="tickler_priority">
                        <option value="Normal">Normal</option>
                        <option value="High">High</option>
                        <option value="Low">Low</option>
                    </select><br>
                    Assign to
                    <select name="tickler_send_to" id="tickler_send_to">
                        <option value="doctor_provider_no">Patients MRP</option>
                        <option value="current_user_id">Current User</option>
                    </select><br>
                    Due in
                    <select name="tickler_weeks" id="tickler_weeks">
                        <option value="6">6 weeks</option>
                        <option value="1">1 week</option>
                        <option value="2">2 weeks</option>
                        <option value="3">3 weeks</option>
                        <option value="4">4 weeks</option>
                        <option value="8">2 months</option>
                        <option value="12">3 months</option>
                        <option value="16">4 months</option>
                        <option value="24">6 months</option>
                        <option value="36">9 months</option>
                        <option value="52">1 year</option>
                    </select>&nbsp;&nbsp;allow end user to adjust<input name="endUserTicklerWeekAdj"
                                                                        id="endUserTicklerWeekAdj" type="checkBox">
                    <br>
                    Tickler message <input type="text" name="tickler_message" id="tickler_message" style="width:200px;">
                    <br>
                    (leave blank to have the message read "Check for results of [subject] ordered [today]") <br>
                </p>
            </div>

        </div>
        <hr>
        <span class='h2'>8. <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.generate"/></span><a
            onclick="show('Section8');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.expand"/></a>/<a
            onclick="hide('Section8');"><fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.collapse"/></a>
        <div id='Section8'>
            <!-- Inject the html to the eForm window -->
            <input name="loadHTMLButton" id="loadHTMLButton" type="button"
                   value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.generateLoadButton"/>' onClick="injectHtml();">
            <input name="reset" id="reset" type="button"
                   value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.generateResetButton"/>' onclick="resetAll();">
            <!--  Cookie Monster says hello! -->
            <input name="save" id="save" type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.generateSaveButton"/>'
                   onclick="save_to_cookie();">
            <input name="restore" id="restore" type="button"
                   value='<fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.generateRestoreSaveButton"/>' onclick="restoreSaved();">
            <!--  Cookie Monster says bye! -->
            <p>- <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.generatehint1"/>
                <br>- <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.generatehint2"/>
                <br>- <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.generatehint3"/>
                <br>- <fmt:setBundle basename="oscarResources"/><fmt:message key="eFormGenerator.generatehint4"/>
            </p>

        </div>

    </div>
</form>

<!--  Drawing code: start -->
<div id="preview" style="position: absolute; left: 0px; top: 0px;"></div>
<div id="myCanvas" style="position: absolute; left: 0px; top: 0px;"></div>

<script type="text/javascript">
    var DrawData = new Array();
    var TempData = new Array();

    var cnv = document.getElementById("myCanvas");
    var jg = new jsGraphics(cnv);

    var pvcnv = document.getElementById("preview");
    var pv = new jsGraphics(pvcnv);

    jg.setPrintable(true);
    var StrokeColor = "red";
    var StrokeThickness = 2;
    var x0 = 0;
    var y0 = 0;
    var ShowInputName = false;

    function clearGraphics(canvas) {
        canvas.clear();
    }

    function SetStrokeColor(c) {
        StrokeColor = c;
    }

    var MouseDown = false;

    function SetMouseDown() {
        MouseDown = true;
    }

    function SetMouseUp() {
        MouseDown = false;
    }

    var DrawSwitch = false;

    function SetDrawOn() {
        DrawSwitch = true;
    }

    function SetDrawOff() {
        DrawSwitch = false;
    }

    var TextSwitch = true;
    var TextboxSwitch = false;
    var CheckboxSwitch = false;
    var XboxSwitch = false;
    var MaleSwitch = false;
    var FemaleSwitch = false;
    var SignatureBoxSwitch = false;
    var StampSwitch = false;
    var ClassicSignatureSwitch = false;
    var RadioButtonSwitch = false;

    function SetSwitchesOff() {
        TextSwitch = false;
        TextboxSwitch = false;
        CheckboxSwitch = false;
        XboxSwitch = false;
        MaleSwitch = false;
        FemaleSwitch = false;
        SignatureBoxSwitch = false;
        StampSwitch = false;
        ClassicSignatureSwitch = false;
        RadioButtonSwitch = false;
    }

    var DrawTool = "Text";

    function SetSwitchOn(n) {
        SetSwitchesOff();
        DrawTool = n;

        if (n == "Text") {
            TextSwitch = true;
        } else if (n == "Textbox") {
            TextboxSwitch = true;
        } else if (n == "Checkbox") {
            CheckboxSwitch = true;
        } else if (n == "Xbox") {
            XboxSwitch = true;
        } else if (n == "Male") {
            MaleSwitch = true;
        } else if (n == "Female") {
            FemaleSwitch = true;
        } else if (n == "SignatureBox") {
            SignatureBoxSwitch = true;
        } else if (n == "Stamp") {
            StampSwitch = true;
        } else if (n == "ClassicSignature") {
            ClassicSignatureSwitch = true;
        } else if (n == "RadioButton") {
            RadioButtonSwitch = true;
        }
    }

    function SetStart() {
        x0 = parseInt(mousex);	//assign x coordinate at mousedown to x0
        y0 = parseInt(mousey);	//assign y coordinate at mousedown to y0
    }

    function DrawText(canvas, x0, y0, width, height, inputName, fontFamily, fontStyle, fontWeight, fontSize, textAlign, bgColor, oscarDB, inputValue, inputClass, inputParentclass) {
        // draw Rectangle
//alert(PageIterate+"|"+PageNum+"|"+( PageIterate == PageNum )+(canvas == jg));
        if (PageIterate == PageNum) {
            var x0 = parseInt(x0);
            var y0 = parseInt(y0);
            var width = parseInt(width);
            var height = parseInt(height);
            canvas.setColor(StrokeColor);
            canvas.setStroke(StrokeThickness);
            canvas.drawRect(x0, y0, width, height);
            canvas.paint();
            if (ShowInputName) {
                canvas.setColor('blue');
                canvas.setFont("sans-serif", "10px", Font.BOLD);
                var xt = x0 + StrokeThickness
                var yt = y0 + StrokeThickness
                canvas.drawString(inputName, xt, y0);
                canvas.paint();
                canvas.setColor(StrokeColor);
            }
        }
        //store parameters in an array (using separator "|")
        if (canvas == jg) {
            var Parameter = "Text" + "|" + x0 + "|" + y0 + "|" + width + "|" + height + "|" + inputName + "|" + fontFamily + "|" + fontStyle + "|" + fontWeight + "|" + fontSize + "|" + textAlign + "|" + bgColor + "|" + oscarDB + "|" + inputValue + "|" + inputClass + "|" + inputParentclass;
            DrawData.push(Parameter);
        }
    }

    function DrawTextbox(canvas, x0, y0, width, height, inputName, fontFamily, fontStyle, fontWeight, fontSize, textAlign, bgColor, oscarDB, inputValue, inputClass, inputParentclass) {
        // draws Rectangle
        if (PageIterate == PageNum) {
            var x0 = parseInt(x0);
            var y0 = parseInt(y0);
            var width = parseInt(width);
            var height = parseInt(height);
            canvas.setColor(StrokeColor);
            canvas.setStroke(StrokeThickness);
            canvas.drawRect(x0, y0, width, height);
            canvas.paint()
            if (ShowInputName) {
                canvas.setColor('blue');
                canvas.setFont("sans-serif", "10px", Font.BOLD);
                var xt = x0 + StrokeThickness
                var yt = y0 + StrokeThickness
                canvas.drawString(inputName, xt, y0);
                canvas.paint();
                canvas.setColor(StrokeColor);
            }
        }
        //store parameters in an array (using separator "|")
        if (canvas == jg) {
            var Parameter = "Textbox" + "|" + x0 + "|" + y0 + "|" + width + "|" + height + "|" + inputName + "|" + fontFamily + "|" + fontStyle + "|" + fontWeight + "|" + fontSize + "|" + textAlign + "|" + bgColor + "|" + oscarDB + "|" + inputValue + "|" + inputClass + "|" + inputParentclass;
            DrawData.push(Parameter);
        }
    }

    function DrawCheckbox(canvas, x0, y0, inputName, preCheck, inputClass, inputParentclass) {
        // draws Checkbox
        if (PageIterate == PageNum) {
            var x = parseInt(x);
            var y = parseInt(y);
            canvas.setColor(StrokeColor);
            canvas.setStroke(StrokeThickness);
            var s = 10; 	//square with side of 10
            canvas.drawRect(x0, y0, s, s);
            canvas.paint();
            if (ShowInputName) {
                canvas.setColor('blue');
                canvas.setFont("sans-serif", "10px", Font.BOLD);
                var xt = x0 + StrokeThickness
                var yt = y0 + StrokeThickness
                canvas.drawString(inputName, xt, y0);
                canvas.paint();
                canvas.setColor(StrokeColor);
            }
            canvas.paint();
        }
        //store parameters in an array (using separator "|")
        if (canvas == jg) {
            var Parameter = "Checkbox" + "|" + x0 + "|" + y0 + "|" + inputName + "|" + preCheck + "|" + inputClass + "|" + inputParentclass;
            DrawData.push(Parameter);
        }
        if ((inputName == "Male") || (inputName == "Female")) {
            SetSwitchOn('Text');
            document.getElementById('Text').click();
        }
    }

    function DrawXbox(canvas, x0, y0, width, height, inputName, fontFamily, fontStyle, fontWeight, fontSize, textAlign, bgColor, oscarDB, inputValue, inputClass, inputParentclass) {
        // draw Rectangle
        if (PageIterate == PageNum) {
            var x0 = parseInt(x0);
            var y0 = parseInt(y0);
            var s = 10; 	//square with side of 10
            var s = parseInt(fontSize) + 2;
            canvas.setColor(StrokeColor);
            canvas.setStroke(StrokeThickness);
            canvas.drawRect(x0, y0, s, s);
            canvas.paint();
            if (ShowInputName) {
                canvas.setColor('blue');
                canvas.setFont("sans-serif", "10px", Font.BOLD);
                var xt = x0 + StrokeThickness
                var yt = y0 + StrokeThickness
                canvas.drawString(inputName, xt, y0);
                canvas.paint();
                canvas.setColor(StrokeColor);
            }
        }
        //store parameters in an array (using separator "|")
        if (canvas == jg) {
            var Parameter = "Xbox" + "|" + x0 + "|" + y0 + "|" + width + "|" + height + "|" + inputName + "|" + fontFamily + "|" + fontStyle + "|" + fontWeight + "|" + fontSize + "|" + textAlign + "|" + bgColor + "|" + oscarDB + "|" + inputValue + "|" + inputClass + "|" + inputParentclass;
            DrawData.push(Parameter);
        }
        if ((inputName == "Male") || (inputName == "Female")) {
            SetSwitchOn('Text');
            document.getElementById('Text').click();
        }

    }

    function DrawPage(canvas, pnum, pimage, bwidth) {
//alert("page draw"+pnum)
        PageIterate = pnum;
        //store parameters in an array (using separator "|")
        if (canvas == jg) {
            var Parameter = "Page" + "|" + pnum + "|" + pimage + "|" + bwidth;
            DrawData.push(Parameter);
        }
//var tempa="";
//for (j=0; (j < (DrawData.length) ); j++){
//		tempa += DrawData[j]+"\n";
//	}
//alert(tempa);
    }

    function DrawSignatureBox(canvas, x0, y0, width, height, inputName) {

        if (inputName == "ClassicSignature") {
            //assigns coordinates of top left corner of box
            SignatureHolderX = x0;
            SignatureHolderY = y0;
            SignatureHolderW = width;
            SignatureHolderH = height;
            sigOffset = pageoffset - document.getElementById('BGImage').height;

        } else {
            //constrains width and height
            if (height < 30) {
                height = 30;
            }
            width = 4 * height;
        }

        //draws box
        canvas.setColor(StrokeColor);
        canvas.setStroke(StrokeThickness);
        canvas.drawRect(x0, y0, width, height);
        canvas.paint();

        if (ShowInputName) {
            canvas.setColor('blue');
            canvas.setFont("sans-serif", "10px", Font.BOLD);
            var xt = x0 + StrokeThickness
            var yt = y0 + StrokeThickness
            canvas.drawString(inputName, xt, y0);
            canvas.paint();
            canvas.setColor(StrokeColor);
        }

        //store parameters in an array (using separator "|")
        if (canvas == jg) {
            var Parameter = "Signature" + "|" + x0 + "|" + y0 + "|" + width + "|" + height + "|" + inputName;
            DrawData.push(Parameter);
        }

        //reset to default input of text input
        SetSwitchOn('Text');
        document.getElementById('Text').click();
    }

    function DrawStamp(canvas, x0, y0, width, height, inputName) {
        //draws box
        canvas.setColor(StrokeColor);
        canvas.setStroke(StrokeThickness);
        canvas.drawRect(x0, y0, width, height);
        canvas.paint();

        if (ShowInputName) {
            canvas.setColor('blue');
            canvas.setFont("sans-serif", "10px", Font.BOLD);
            var xt = x0 + StrokeThickness
            var yt = y0 + StrokeThickness
            canvas.drawString(inputName, xt, y0);
            canvas.paint();
            canvas.setColor(StrokeColor);
        }

        //store parameters in an array (using separator "|")
        if (canvas == jg) {
            var Parameter = "Stamp" + "|" + x0 + "|" + y0 + "|" + width + "|" + height + "|" + inputName;
            DrawData.push(Parameter);
        }

        //reset to default input of text input
        SetSwitchOn('Text');
        document.getElementById('Text').click();
    }

    var inputName = "";
    var inputCounter = 1;


    function DrawMarker() {
        var x = parseInt(mousex);	//assign x coordinate at mouseup to x
        var y = parseInt(mousey);	//assign y coordinate at mouseup to y


        var width = x - x0;
        var height = y - y0;
        var fontFamily = document.getElementById('fontFamily').value;
        var fontStyle = document.getElementById('fontStyle').value;
        var fontWeight = document.getElementById('fontWeight').value;
        var fontSize = document.getElementById('fontSize').value;
        var inputClass = document.getElementById('inputClass').value;
        var inputParentclass = document.getElementById('inputParentclass').value;
        var textAlign = document.getElementById('textAlign').value;
        var bgColor = document.getElementById('bgColor').value;

        //get value of autopopulating data
        var preCheck = document.getElementById('preCheck').checked
        var inputValue = "";
        var oscarDB = "";
        var AutoPopType = getCheckedValue(document.getElementsByName('AutoPopType'));
        if (AutoPopType == 'custom') {
            inputValue = document.getElementById('inputValue').value;
        } else if (AutoPopType == 'database') {
            oscarDB = document.getElementById('oscarDB').value;
        } else if (AutoPopType == 'measurements') {
            if (document.getElementById('MeasurementList').value) {	// Common Standard MeasurementTypes
                oscarDB = "m$" + document.getElementById('MeasurementList').value + "#" + document.getElementById('MeasurementField').value;
            } else if (document.getElementById('MeasurementCustom').value) {	//Custom Measurement Types
                oscarDB = "m$" + document.getElementById('MeasurementCustom').value + "#" + document.getElementById('MeasurementField').value;
            }
        }

        //get name of input field
        var inputNameType = getCheckedValue(document.getElementsByName('InputNameType'));  // inputNameType = Auto/Custom
        if (inputNameType == "Custom") {
            e = document.getElementById('inputName').value
            if (e) {
                if (isValid(e)) {
                    inputName = e
                } else {
                    alert("The custom input name field can only contain\n letters A-Z a-z numbers 0-9 and underline _");
                    return false;
                }
            } else if (!e) {
                alert("Please enter in a value for the custom input name field");	//reminds user to put in mandatory name for input field
                return false;
            }
        } else if (inputNameType == "Measurement") {
            if (document.getElementById('ExportMeasurementList').value) {
                inputName = "m$" + document.getElementById('ExportMeasurementList').value + "#" + document.getElementById('ExportMeasurementField').value;
            } else if (document.getElementById('ExportMeasurementCustom').value) {
                inputName = "m$" + document.getElementById('ExportMeasurementCustom').value + "#" + document.getElementById('ExportMeasurementField').value;
            }
        } else if (inputNameType == "Auto") {
            if (oscarDB) {
                inputName = oscarDB;	//if auto-naming input fields, use oscarDB tag if available
                var InputList = document.getElementsByName('InputChecklist');
                var j = 0;
                for (i = 0; i < InputList.length; i++) {
                    var InputItem = InputList[i].value.substring(0, inputName.length);	//add increment to oscarDB name if repeated
                    if (InputItem == inputName) {
                        j++;
                    }
                }
                if (j > 0) {
                    inputName = inputName + j;
                }
            } else {
                inputName = document.getElementById('AutoNamePrefix').value + inputCounter;
                ++inputCounter;
            }
        }
        //compare inputName to list of existing inputNames to ensure unique names
        for (i = 0; i < document.getElementsByName('InputChecklist').length; i++) {
            var InputItem = document.getElementsByName('InputChecklist')[i].value;
            if (inputName == InputItem) {
                alert("Name already in use, please enter in another UNIQUE input name");
            }
        }


        if (DrawSwitch) {
            console.log("boxleft=" + boxleft + " x0=" + x0 + " boxup=" + boxup + " y0=" + y0 + "  boxwidth=" + boxwidth + " width=" + width + " boxheight=" + boxheight + " height=" + height);
            if ((x0 < boxleft + (snap * 2)) && (x0 > boxleft - (snap * 2))) {
                x0 = boxleft;
            } else {
                boxleft = x0;
            }
            if ((y0 < boxup + (snap * 2)) && (y0 > boxup - (snap * 2))) {
                y0 = boxup;
            } else {
                boxup = y0;
            }
            if ((width < boxwidth + snap) && (width > boxwidth - snap)) {
                width = boxwidth;
            } else {
                boxwidth = width;
            }
            if ((height < boxheight + snap) && (height > boxheight - snap)) {
                height = boxheight;
            } else {
                boxheight = height;
            }
            console.log("boxleft=" + boxleft + " x0=" + x0 + " boxup=" + boxup + " y0=" + y0 + "  boxwidth=" + boxwidth + " width=" + width + " boxheight=" + boxheight + " height=" + height);
            if (TextSwitch) {
                DrawText(jg, x0, y0, width, height, inputName, fontFamily, fontStyle, fontWeight, fontSize, textAlign, bgColor, oscarDB, inputValue, inputClass, inputParentclass);
            } else if (TextboxSwitch) {
                DrawTextbox(jg, x0, y0, width, height, inputName, fontFamily, fontStyle, fontWeight, fontSize, textAlign, bgColor, oscarDB, inputValue, inputClass, inputParentclass);
            } else if (CheckboxSwitch) {
                DrawCheckbox(jg, x0, y0, inputName, preCheck, inputClass, inputParentclass);
            } else if (XboxSwitch) {
                if (preCheck) {
                    inputValue = 'X';
                } else {
                    inputValue = '';
                }
                DrawXbox(jg, x0, y0, width, height, inputName, fontFamily, fontStyle, fontWeight, fontSize, textAlign, bgColor, oscarDB, inputValue, inputClass, inputParentclass);
            } else if (MaleSwitch) {
                if (document.getElementById('XboxType').checked) {
                    DrawXbox(jg, x0, y0, width, height, "Male", fontFamily, fontStyle, fontWeight, fontSize, textAlign, bgColor, oscarDB, "", "", "");
                } else {
                    DrawCheckbox(jg, x0, y0, "Male", false, "only-one-", "gender");
                }
            } else if (FemaleSwitch) {
                if (document.getElementById('XboxType').checked) {
                    DrawXbox(jg, x0, y0, width, height, "Female", fontFamily, fontStyle, fontWeight, fontSize, textAlign, bgColor, oscarDB, "", "", "");
                } else {
                    DrawCheckbox(jg, x0, y0, "Female", false, "only-one-", "gender");
                }
            } else if (SignatureBoxSwitch) {
                var sigtext = "Signature";
                sigint += 1;
                sigtext = sigtext + sigint
                DrawSignatureBox(jg, x0, y0, width, height, sigtext);
            } else if (StampSwitch) {
                var sigtext = "Stamp";
                DrawStamp(jg, x0, y0, width, height, sigtext);
            } else if (ClassicSignatureSwitch) {
                var sigtext = "ClassicSignature";
                DrawSignatureBox(jg, x0, y0, width, height, sigtext);
            } else if (RadioButtonSwitch) {
                if (document.getElementById('radioX').checked) {
                    DrawXbox(jg, x0, y0, width, height, inputName, fontFamily, fontStyle, fontWeight, fontSize, textAlign, bgColor, oscarDB, inputValue, "Radio only-one-", document.getElementById('RadioName').value);
                } else {
                    DrawCheckbox(jg, x0, y0, inputName, false, "only-one-", document.getElementById('RadioName').value);
                }
            } else
                alert("nothing selected!");

        }

        //reset input data
        document.getElementById('inputValue').value = "";
        document.getElementById('inputName').value = "";
        //document.getElementById('bgColor')[0].selected = true;
        document.getElementById('preCheck').checked = false;
        document.getElementById('oscarDB')[0].selected = true;
        document.getElementById('MeasurementList')[0].selected = true;
        document.getElementById('ExportMeasurementList')[0].selected = true;
        document.getElementById('MeasurementCustom').value = "";
        document.getElementById('ExportMeasurementCustom').value = "";
        document.getElementById('inputClass')[0].selected = true;
        document.getElementById('inputParentclass').value = "";

    }

    function ToggleInputName() {
        PageIterate = 0;
        jg.clear();
        if (ShowInputName) {
            ShowInputName = false;
        } else if (!ShowInputName) {
            ShowInputName = true;
        }
        drawPageOutline();
        TempData = DrawData;
        DrawData = new Array();
        for (j = 0; (j < (TempData.length)); j++) {
            var RedrawParameter = TempData[j].split("|");
            RedrawImage(RedrawParameter);
        }
    }

    function RedrawAll() {
        PageIterate = 0;
        jg.clear();

        drawPageOutline();
        TempData = DrawData;
        DrawData = new Array();
        for (j = 0; (j < (TempData.length)); j++) {
            var RedrawParameter = TempData[j].split("|");
            RedrawImage(RedrawParameter);
        }
    }

    function Undo() {
        jg.clear();
        TempData = DrawData;
        DrawData = new Array();

        drawPageOutline();
        for (j = 0; (j < (TempData.length - 1)); j++) {
            var RedrawParameter = TempData[j].split("|");
            RedrawImage(RedrawParameter);
        }
        var inputNameType = getCheckedValue(document.getElementsByName('InputNameType'));  // inputNameType = Auto/Custom
        if (inputNameType == "Auto") {
            --inputCounter;
        }
        loadInputList();
    }

    function RedrawImage(RedrawParameter) {
        var InputType = RedrawParameter[0];
        if (InputType == "Text") {
            var x0 = parseInt(RedrawParameter[1]);
            var y0 = parseInt(RedrawParameter[2]);
            var width = parseInt(RedrawParameter[3]);
            var height = parseInt(RedrawParameter[4]);
            var inputName = RedrawParameter[5];
            var fontFamily = RedrawParameter[6];
            var fontStyle = RedrawParameter[7];
            var fontWeight = RedrawParameter[8];
            var fontSize = RedrawParameter[9];
            var textAlign = RedrawParameter[10];
            var bgColor = RedrawParameter[11];
            var oscarDB = RedrawParameter[12];
            var inputValue = RedrawParameter[13];
            var inputClass = RedrawParameter[14];
            var inputParentclass = RedrawParameter[15];
            DrawText(jg, x0, y0, width, height, inputName, fontFamily, fontStyle, fontWeight, fontSize, textAlign, bgColor, oscarDB, inputValue, inputClass, inputParentclass);
        } else if (InputType == "Textbox") {
            var x0 = parseInt(RedrawParameter[1]);
            var y0 = parseInt(RedrawParameter[2]);
            var width = parseInt(RedrawParameter[3]);
            var height = parseInt(RedrawParameter[4]);
            var inputName = RedrawParameter[5];
            var fontFamily = RedrawParameter[6];
            var fontStyle = RedrawParameter[7];
            var fontWeight = RedrawParameter[8];
            var fontSize = RedrawParameter[9];
            var textAlign = RedrawParameter[10];
            var bgColor = RedrawParameter[11];
            var oscarDB = RedrawParameter[12];
            var inputValue = RedrawParameter[13];
            var inputClass = RedrawParameter[14];
            var inputParentclass = RedrawParameter[15];
            DrawTextbox(jg, x0, y0, width, height, inputName, fontFamily, fontStyle, fontWeight, fontSize, textAlign, bgColor, oscarDB, inputValue, inputClass, inputParentclass);
        } else if (InputType == "Checkbox") {
            var x0 = parseInt(RedrawParameter[1]);
            var y0 = parseInt(RedrawParameter[2]);
            var inputName = RedrawParameter[3];
            var preCheck = RedrawParameter[4];
            var inputClass = RedrawParameter[5];
            var inputParentclass = RedrawParameter[6];
            DrawCheckbox(jg, x0, y0, inputName, preCheck, inputClass, inputParentclass);
        } else if (InputType == "Xbox") {
            var x0 = parseInt(RedrawParameter[1]);
            var y0 = parseInt(RedrawParameter[2]);
            var width = parseInt(RedrawParameter[3]);
            var height = parseInt(RedrawParameter[4]);
            var inputName = RedrawParameter[5];
            var fontFamily = RedrawParameter[6];
            var fontStyle = RedrawParameter[7];
            var fontWeight = RedrawParameter[8];
            var fontSize = RedrawParameter[9];
            var textAlign = RedrawParameter[10];
            var bgColor = RedrawParameter[11];
            var oscarDB = RedrawParameter[12];
            var inputValue = RedrawParameter[13];
            var inputClass = RedrawParameter[14];
            var inputParentclass = RedrawParameter[15];
            DrawXbox(jg, x0, y0, width, height, inputName, fontFamily, fontStyle, fontWeight, fontSize, textAlign, bgColor, oscarDB, inputValue, inputClass, inputParentclass);
        } else if (InputType == "Page") {
            var pnum = parseInt(RedrawParameter[1]);
            PageIterate = pnum;
            var pimage = RedrawParameter[2];
            var bwidth = parseInt(RedrawParameter[3]);
            DrawPage(jg, pnum, pimage, bwidth);
        } else if (InputType == "Signature") {
            var x0 = parseInt(RedrawParameter[1]);
            var y0 = parseInt(RedrawParameter[2]);
            var width = parseInt(RedrawParameter[3]);
            var height = parseInt(RedrawParameter[4]);
            var inputName = RedrawParameter[5];
            DrawSignatureBox(jg, x0, y0, width, height, inputName);
        } else if (InputType == "Stamp") {
            var x0 = parseInt(RedrawParameter[1]);
            var y0 = parseInt(RedrawParameter[2]);
            var width = parseInt(RedrawParameter[3]);
            var height = parseInt(RedrawParameter[4]);
            var inputName = RedrawParameter[5];
            DrawStamp(jg, x0, y0, width, height, inputName);
        }
    }

    function drawPortraitOutline() {
        jg.setColor('red');
        jg.setStroke(StrokeThickness);
        jg.drawRect(0, 0, 750, 1000);
        jg.paint();
    }

    function drawLandscapeOutline() {
        jg.setColor('red');
        jg.setStroke(StrokeThickness);
        jg.drawRect(0, 0, 1000, 750);
        jg.paint();
    }

    <!-- Drawing code ends -->
</script>
<!--  Cookie Monster says hello! -->
<script type="text/javascript">
    function getCookie(name) {
        var dc = document.cookie;
        var cname = name + "=";

        if (dc.length > 0) {
            begin = dc.indexOf(cname);
            if (begin != -1) {
                begin += cname.length;
                end = dc.indexOf(";", begin);
                if (end == -1) end = dc.length;
                return dc.substring(begin, end);
            }
        }
        return null;
    }

    function save_to_cookie() {
        var exp = new Date();
        exp.setTime(exp.getTime() + (1000 * 60 * 60 * 24 * 30));
        document.cookie = "drawdata" + "=" + DrawData + "; expires=" + exp.toGMTString() + "; path=/";
        document.cookie = "inputcounter" + "=" + inputCounter + "; expires=" + exp.toGMTString() + "; path=/";
    }

    function restoreSaved() {
        DrawData = getCookie("drawdata").split(",");
        inputCounter = getCookie("inputcounter");
        RedrawAll();
    }
</script>
<!--  Cookie Monster says bye! -->
</body>
</html>