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

<%--
  Purpose: Display general clinical calculation and conversion tools.
  Features: Browser-side calculation controls and application-context styling
  that loads correctly when the page opens from its nested calculator route.
  Parameters: No request parameters are consumed; users enter values in the page.
  @since 2026-09-17
--%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>


<html>


    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="encounter.calculators.GeneralCalculators.title"/></title>
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/encounter/encounterStyles.css">

        <SCRIPT LANGUAGE="JavaScript">

            // Generic Unit Conversion Program

            // Author    : Jonathan Weesner (jweesner@cyberstation.net)  21 Nov 95

            // Copyright : You want it? Take it! ... but leave the Author line intact please!

            function convertform(form) {
                var source;
                for (var i = 1; i <= form.count; i++) {
                    if (form.elements[i].value.trim() !== "") {
                        source = form.elements[i];
                        break;
                    }
                }
                if (!source) return false;
                var value = Number(source.value);
                var firstvalue = value / source.factor;
                source.setCustomValidity(Number.isFinite(value) && Number.isFinite(firstvalue)
                    ? "" : "Enter a finite number.");
                if (!source.reportValidity()) return false;
                for (var i = 1; i <= form.count; i++) {
                    var converted = firstvalue * form.elements[i].factor;
                    if (!Number.isFinite(converted)) {
                        source.setCustomValidity("The value is too large to convert.");
                        source.reportValidity();
                        return false;
                    }
                }
                for (var i = 1; i <= form.count; i++) {
                    form.elements[i].value = formatvalue(firstvalue * form.elements[i].factor, form.rsize);
                }
                return true;
            }

            function formatvalue(input, rsize) {
                // Significant figures preserve very small values and scientific notation.
                return String(Number(input.toPrecision(rsize)));
            }

            function convertTemperature(input, outputName, toCelsius) {
                var output = input.form.elements[outputName];
                output.setCustomValidity("");
                if (input.value.trim() === "") {
                    input.setCustomValidity("");
                    output.value = "";
                    return;
                }
                var value = Number(input.value);
                var converted = toCelsius ? (value - 32) * 5 / 9 : value * 9 / 5 + 32;
                var valid = Number.isFinite(value) && Number.isFinite(converted);
                input.setCustomValidity(valid ? "" : "Enter a finite number.");
                output.value = valid ? formatvalue(converted, 10) : "";
                input.reportValidity();
            }

            function resetform(form) {

                clearform(form);

                form.elements[1].value = 1;

                convertform(form);

                return true;

            }

            function clearform(form) {

                for (var i = 1; i <= form.count; i++) {
                    form.elements[i].value = "";
                    form.elements[i].setCustomValidity("");
                }

                return true;

            }

            function conversionInputChanged(input) {
                // Editing selects the source unit. Merely focusing a field
                // (including reportValidity focusing an error) must preserve it.
                for (var i = 1; i <= input.form.count; i++) {
                    var field = input.form.elements[i];
                    if (field !== input) field.value = "";
                    field.setCustomValidity("");
                }
            }

            // End conversion helpers.

        </SCRIPT>


        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    </head>

    <body class="BodyStyle" vlink="#0000FF">
    <!--  -->
    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:message key="encounter.calculators.GeneralCalculators.msgCalculators"/>
            </td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td><fmt:message key="encounter.calculators.GeneralCalculators.msgTitle"/></td>
                        <td>&nbsp;</td>
                        <td style="text-align: right"><a
                                href="javascript:popupPage(300,400,'<%=request.getContextPath()%>/encounter/ViewAbout')"><fmt:message key="global.about"/></a> | <a
                                href="javascript:popupPage(300,400,'<%=request.getContextPath()%>/encounter/ViewLicense')"><fmt:message key="global.license"/></a></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn" valign="top"><a href="#distance"><fmt:message key="encounter.calculators.GeneralCalculators.msgDistance"/></A> <a
                    href="#weight"><fmt:message key="encounter.calculators.GeneralCalculators.msgWeight"/></A> <a
                    href="#volume"><fmt:message key="encounter.calculators.GeneralCalculators.msgVolume"/></A> <a
                    href="#temps"><fmt:message key="encounter.calculators.GeneralCalculators.msgTemperatures"/></A>


            </td>
            <td class="MainTableRightColumn">
                <table>
                    <tr>
                        <td style="text-align: center">
                            <FORM method="post">
                                <TABLE BORDER=2 cellpadding=3 cellspacing=0>
                                    <TR class="Header">
                                        <TD COLSPAN=7 ALIGN=CENTER VALIGN=MIDDLE><A NAME="distance"><b><fmt:message key="encounter.calculators.GeneralCalculators.msgDistanceConversion"/></b></A>
                                        </TD>
                                    </TR>
                                    <TR>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgMeters"/></TD>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgInches"/></TD>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgFeet"/></TD>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgYards"/></TD>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgMiles"/></TD>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgNauticalMiles"/></TD>
                                        <TD><INPUT TYPE="button"
                                                   VALUE="<fmt:message key="encounter.calculators.GeneralCalculators.btnCalibrate"/>"
                                                   onclick="resetform(this.form)"></TD>
                                    </TR>
                                    <TR>
                                        <TD><INPUT TYPE=TEXT NAME=val1 SIZE=7
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE=TEXT NAME=val2 SIZE=7
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE=TEXT NAME=val3 SIZE=7
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE=TEXT NAME=val4 SIZE=7
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE=TEXT NAME=val5 SIZE=7
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE=TEXT NAME=val6 SIZE=7
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE="button"
                                                   VALUE="<fmt:message key="encounter.calculators.GeneralCalculators.btnCalculate"/>"
                                                   onclick="convertform(this.form)"></TD>
                                    </TR>
                                </TABLE>
                            </FORM>
                            <FORM method="post">
                                <TABLE BORDER=2 cellpadding=3 cellspacing=0>
                                    <TR class="Header">
                                        <TD COLSPAN=8 ALIGN=CENTER VALIGN=MIDDLE style="font-weight: bold">
                                            <A NAME="weight"><b><fmt:message key="encounter.calculators.GeneralCalculators.msgWightConversion"/></b></A>
                                        </TD>
                                    </TR>
                                    <TR>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgKilograms"/></TD>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgOunces"/></TD>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgPounds"/></TD>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgTroyPounds"/></TD>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgStones"/></TD>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgShortTons"/></TD>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgLongTons"/></TD>
                                        <TD><INPUT TYPE="button"
                                                   VALUE="<fmt:message key="encounter.calculators.GeneralCalculators.btnCalibrate"/>"
                                                   onClick="resetform(this.form)"></TD>
                                    </TR>
                                    <TR>
                                        <TD><INPUT TYPE=TEXT NAME=val1 SIZE=6
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE=TEXT NAME=val2 SIZE=6
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE=TEXT NAME=val3 SIZE=6
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE=TEXT NAME=val4 SIZE=6
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE=TEXT NAME=val5 SIZE=6
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE=TEXT NAME=val6 SIZE=6
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE=TEXT NAME=val7 SIZE=6
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE="button"
                                                   VALUE="<fmt:message key="encounter.calculators.GeneralCalculators.btnCalculate"/>"
                                                   onclick="convertform(this.form)"></TD>
                                    </TR>
                                </TABLE>
                            </FORM>
                            <FORM method="post">
                                <TABLE border=2 cellpadding=3 cellspacing=0>
                                    <TR class="Header">
                                        <TD COLSPAN=7 ALIGN=CENTER VALIGN=MIDDLE><A NAME="volume"><b><fmt:message key="encounter.calculators.GeneralCalculators.msgVolumeConversion"/></b></A>
                                        </TD>
                                    </TR>
                                    <TR>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgLitres"/></TD>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgFluid"/></TD>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgQuarts"/></TD>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgGallons"/></TD>
                                        <TD ALIGN=CENTER><fmt:message key="encounter.calculators.GeneralCalculators.msgImperialGallons"/></TD>
                                        <TD><INPUT TYPE="button"
                                                   VALUE="<fmt:message key="encounter.calculators.GeneralCalculators.btnCalibrate"/>"
                                                   onclick="resetform(this.form)"></TD>
                                    </TR>
                                    <TR>
                                        <TD><INPUT TYPE=TEXT NAME=val1 SIZE=6
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE=TEXT NAME=val2 SIZE=6
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE=TEXT NAME=val3 SIZE=6
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE=TEXT NAME=val4 SIZE=6
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE=TEXT NAME=val5 SIZE=6
                                                   oninput="conversionInputChanged(this)"></TD>
                                        <TD><INPUT TYPE="button"
                                                   VALUE="<fmt:message key="encounter.calculators.GeneralCalculators.btnCalculate"/>"
                                                   onclick="convertform(this.form)"></TD>
                                    </TR>
                                </TABLE>
                            </FORM>
                            <TABLE border=2 cellpadding=3 cellspacing=0>
                                <TR class="Header">
                                    <TD COLSPAN=3 ALIGN=CENTER VALIGN=MIDDLE><A NAME="temps"><b><fmt:message key="encounter.calculators.GeneralCalculators.msgTemperaturesConversion"/></b></A>
                                    </TD>
                                </TR>
                                <TR>
                                    <TD><fmt:message key="encounter.calculators.GeneralCalculators.msgInstructions1"/></TD>
                                    <TD><fmt:message key="encounter.calculators.GeneralCalculators.msgInstructions2"/></TD>
                                    <TD><fmt:message key="encounter.calculators.GeneralCalculators.msgInstructions3"/></TD>
                                </TR>
                            </table>

                            <form method="post">
                                <table border=2 cellpadding=3 cellspacing=0 width="100%"
                                       height="100%">
                                    <tr>
                                        <td width="50%" style="text-align: center;" nowrap><fmt:message key="encounter.calculators.GeneralCalculators.msgFahrenheit"/>
                                            <input type="text" name="F" value="32"
                                                   onChange="convertTemperature(this, 'C', true)"></td>
                                        <td width="50%" style="text-align: center;" nowrap><fmt:message key="encounter.calculators.GeneralCalculators.msgCelsius"/>
                                            <input type="text" name="C" value="0"
                                                   onChange="convertTemperature(this, 'F', false)"></td>
                                    </tr>
                                </table>
                            </form>

                        </td>
                    </tr>
                    <tr>
                        <td style="text-align: center">&nbsp;</td>
                    </tr>
                    <tr>
                        <td style="text-align: center">&nbsp;</td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
    </table>
    </body>
    <SCRIPT LANGUAGE="JavaScript">

        // Set conversion factors for each distance unit.

        // Factors convert the base SI unit to the labelled unit, using exact definitions.
        // Volume columns use U.S. liquid units except the explicitly Imperial gallon.
        // Source: NIST SP 811, Appendix B.8 (conversion factors by unit name).
        // https://www.nist.gov/pml/special-publication-811/nist-guide-si-appendix-b-conversion-factors/nist-guide-si-appendix-b8

        // Be sure to use the correct form index. The first form is

        // always index "0" and remaining forms are numbered in the

        // order they appear in the document.

        document.forms[0].count = 6;  // number of unit types

        document.forms[0].rsize = 7;  // Rounding size, use same as SIZE

        document.forms[0].val1.factor = 1;            // m to m.

        document.forms[0].val2.factor = 1 / 0.0254;  // m to in.

        document.forms[0].val3.factor = 1 / 0.3048;  // m to ft.

        document.forms[0].val4.factor = 1 / 0.9144;  // m to yards.

        document.forms[0].val5.factor = 1 / 1609.344; // m to mi.

        document.forms[0].val6.factor = 1 / 1852; // m to international nautical miles (NIST SP 811).

        // End conversion helpers.

    </SCRIPT>

    <SCRIPT LANGUAGE="JavaScript">

        // Set conversion factors for each weight unit.
        document.forms[1].count = 7;

        document.forms[1].rsize = 6;

        document.forms[1].val1.factor = 1;

        document.forms[1].val2.factor = 16 / 0.45359237;

        document.forms[1].val3.factor = 1 / 0.45359237;

        document.forms[1].val4.factor = 1 / 0.3732417216;

        document.forms[1].val5.factor = 1 / (14 * 0.45359237);

        document.forms[1].val6.factor = 1 / (2000 * 0.45359237);

        document.forms[1].val7.factor = 1 / (2240 * 0.45359237); // kg to long tons, not metric tonnes.

        // End conversion helpers.

    </SCRIPT>


    <SCRIPT LANGUAGE="JavaScript">

        // Set conversion factors for each item in form.

        document.forms[2].count = 5;

        document.forms[2].rsize = 6;

        document.forms[2].val1.factor = 1;

        document.forms[2].val2.factor = 1 / 0.0295735295625;

        document.forms[2].val3.factor = 1 / 0.946352946;

        document.forms[2].val4.factor = 1 / 3.785411784;

        document.forms[2].val5.factor = 1 / 4.54609;

    </SCRIPT>


</html>
