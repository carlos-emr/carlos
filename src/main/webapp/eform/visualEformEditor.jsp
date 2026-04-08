<%--

    Copyright (c) 2012-2018. CloudPractice Inc. All Rights Reserved.
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

    This software was written for
    CloudPractice Inc.
    Victoria, British Columbia
    Canada

--%>
<%--
    Visual eForm Editor — CARLOS EMR

    Purpose: Drag-and-drop visual editor for creating and editing eForm templates.
    Supports both standalone mode (for testing without a server) and integrated mode
    (with full CARLOS REST API connectivity for loading, saving, and updating eForms).

    Features:
    - Visual drag-and-drop form builder with text boxes, checkboxes, labels, shapes, images, and signatures
    - Background image support from the configured eForm image directory
    - Database tag insertion for dynamic patient/provider data binding
    - Multi-page eForm support with configurable page dimensions (portrait, landscape, custom)
    - Import/export of eForm HTML files
    - eForm function attachment (submit, print, fax, tickler, calendar)
    - Snap guides and ruler marks for precise element alignment
    - i18n support for 5 locales (EN, FR, ES, PL, pt_BR)

    Security: Requires _admin.eform write privilege.
    REST endpoints: /ws/rs/eform (single), /ws/rs/eforms (listings)

    @since 2026-03-13
--%>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%
    String roleName$ = (String)session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed=true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin.eform" rights="w" reverse="<%=true%>">
	<%authed=false; %>
	<%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin.eform");%>
</security:oscarSec>
<%
	if(!authed) {
		return;
	}
%>
<!DOCTYPE html>
<html>
<!-- Eform Generator 0.2.080 -->
<!--
The origional 2852 line generator was penned by Robert Martin for OSCAR Host
This generator incorperates numerous innovations from the OSCAR community
with particular mention of Stan Hurwitz
Peter Hutten-Czapski has reworked large parts of the code and ported it to OSCAR 19
... and now CARLOS
-->
<!--
version 0.2.059 changed CSS styling, added Tickler control, fixed radio when there is more than one grouping
version 0.2.071 reworking foreign eform import
version 0.2.072 added funtions to set Subject
                first test port to OSCAR 19
version 0.2.073 multiple bugfixes
version 0.2.074 rewritten UI for adding functions
version 0.2.075 improved speed of loading by making a function asynchronous
version 0.2.076 fixed BNK.png
version 0.2.077 now converts checkboxes to xBoxes
version 0.2.078 fixed datepicker, and wet signature print/pdf
version 0.2.079 support for converting checkboxes that are checked
version 0.2.080 fixed BNK.png again for export
-->
<!--
FOR STAND ALONE USE
-->
<head>
    <meta content="text/html; charset=UTF-8" http-equiv="Content-Type">
    <fmt:setBundle basename="oscarResources"/>
    <title><fmt:message key="eform.visual.editor.title"/></title>

    <!-- jQuery and UI -->
	<script src="<%= request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
	<script>
        $.uiBackCompat = true;
    </script>
	<script src="<%= request.getContextPath() %>/library/jquery/jquery-ui-1.14.2.min.js"></script>
    <link href="<%= request.getContextPath() %>/library/jquery/jquery-ui.theme-1.14.2.min.css" rel="stylesheet" type="text/css">
    <link href="<%= request.getContextPath() %>/library/jquery/jquery-ui.structure-1.14.2.min.css" rel="stylesheet" type="text/css">

    <!-- signature_pad.min.js (Szymon Nowak) for wet-signature canvas widgets. -->
    <script src="<%= request.getContextPath() %>/share/javascript/signature_pad.min.js"></script>

    <!-- main calendar program -->
    <script src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
    <script src="<%= request.getContextPath() %>/share/calendar/lang/calendar-en.js"></script>
    <script src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
    <link href="<%= request.getContextPath() %>/share/calendar/calendar.css" media="all" rel="stylesheet" title="win2k-cold-1" type="text/css">

    <script>
        var runStandaloneVersion = false;
        /* Load jquery requirements from jquery site when run outside of oscar */
        if (!window.jQuery){
			document.write("\x3cscript src='https://code.jquery.com/jquery-3.6.4.min.js' integrity='sha256-oP6HI9z1XaZNBrJURtCoUT5SUnxFr8s3BzRl+cbzUq8=' crossorigin='anonymous'\x3e\x3c\/script\x3e");
            document.write("\x3cscript src='https://code.jquery.com/ui/1.12.1/jquery-ui.min.js' integrity='sha256-VazP97ZCwtekAsvgPBSUwPFKdrwD3unUfSGVYrahUqU=' crossorigin='anonymous'\x3e\x3c\/script\x3e");
            document.write("\x3clink href='https://code.jquery.com/ui/1.12.0/themes/base/jquery-ui.min.css' rel='stylesheet' type='text/css' \x3e");
            /* local javascript file for the signature pads */
            document.write("\x3cscript src='signature_pad.min.js'\x3e\x3c\/script\x3e");
            runStandaloneVersion = true;
        }
        console.info("Run as standalone version: " + runStandaloneVersion);
    </script>

<fmt:setBundle basename="oscarResources"/>
<%-- Capture i18n messages into page-scoped variables for safe JavaScript encoding --%>
<c:set var="i18n_alertTitle"><fmt:message key="eform.visual.editor.dialog.alertTitle"/></c:set>
<c:set var="i18n_noMessage"><fmt:message key="eform.visual.editor.dialog.noMessage"/></c:set>
<c:set var="i18n_buttonOk"><fmt:message key="eform.visual.editor.button.ok"/></c:set>
<c:set var="i18n_buttonCancel"><fmt:message key="eform.visual.editor.button.cancel"/></c:set>
<c:set var="i18n_buttonClose"><fmt:message key="eform.visual.editor.button.close"/></c:set>
<c:set var="i18n_buttonDelete"><fmt:message key="eform.visual.editor.button.delete"/></c:set>
<c:set var="i18n_buttonClear"><fmt:message key="eform.visual.editor.button.clear"/></c:set>
<c:set var="i18n_buttonAddPage"><fmt:message key="eform.visual.editor.button.addPage"/></c:set>
<c:set var="i18n_buttonRemovePage"><fmt:message key="eform.visual.editor.button.removePage"/></c:set>
<c:set var="i18n_buttonSubmit"><fmt:message key="eform.visual.editor.button.submit"/></c:set>
<c:set var="i18n_buttonPrint"><fmt:message key="eform.visual.editor.button.print"/></c:set>
<c:set var="i18n_buttonPrintAndSubmit"><fmt:message key="eform.visual.editor.button.printAndSubmit"/></c:set>
<c:set var="i18n_buttonViewSource"><fmt:message key="eform.visual.editor.button.viewEformSource"/></c:set>
<c:set var="i18n_buttonDownload"><fmt:message key="eform.visual.editor.button.downloadAsFile"/></c:set>
<c:set var="i18n_buttonPrintPreview"><fmt:message key="eform.visual.editor.button.printPreview"/></c:set>
<c:set var="i18n_buttonLoadEForm"><fmt:message key="eform.visual.editor.button.loadSelectedEForm"/></c:set>
<c:set var="i18n_buttonAttachFunction"><fmt:message key="eform.visual.editor.button.attachFunction"/></c:set>
<c:set var="i18n_buttonAllNo"><fmt:message key="eform.visual.editor.button.allNo"/></c:set>
<c:set var="i18n_buttonGroupYellowRadios"><fmt:message key="eform.visual.editor.button.groupYellowRadios"/></c:set>
<c:set var="i18n_buttonResetYellowRadios"><fmt:message key="eform.visual.editor.button.resetYellowRadios"/></c:set>
<c:set var="i18n_buttonGroupNoRadios"><fmt:message key="eform.visual.editor.button.groupNoRadioButtons"/></c:set>
<c:set var="i18n_buttonGroupCompleted"><fmt:message key="eform.visual.editor.button.groupCompletedStartNew"/></c:set>
<c:set var="i18n_buttonLinkOrangeBoxes"><fmt:message key="eform.visual.editor.button.linkOrangeBoxes"/></c:set>
<c:set var="i18n_buttonResetOrangeBoxes"><fmt:message key="eform.visual.editor.button.resetOrangeBoxes"/></c:set>
<c:set var="i18n_labelSubject"><fmt:message key="eform.visual.editor.label.subject"/></c:set>
<c:set var="i18n_labelSelectEForm"><fmt:message key="eform.visual.editor.label.selectEForm"/></c:set>
<c:set var="i18n_labelSelectBgImage"><fmt:message key="eform.visual.editor.label.selectBackgroundImage"/></c:set>
<c:set var="i18n_labelWidth"><fmt:message key="eform.visual.editor.label.width"/></c:set>
<c:set var="i18n_labelHeight"><fmt:message key="eform.visual.editor.label.height"/></c:set>
<c:set var="i18n_labelPageDimensions"><fmt:message key="eform.visual.editor.label.pageDimensions"/></c:set>
<c:set var="i18n_labelCustomTitle"><fmt:message key="eform.visual.editor.label.customTitle"/></c:set>
<c:set var="i18n_labelLinkTo"><fmt:message key="eform.visual.editor.label.linkTo"/></c:set>
<c:set var="i18n_labelTemplateSize"><fmt:message key="eform.visual.editor.label.templateSize"/></c:set>
<c:set var="i18n_labelTemplateWidth"><fmt:message key="eform.visual.editor.label.templateWidth"/></c:set>
<c:set var="i18n_labelTemplateHeight"><fmt:message key="eform.visual.editor.label.templateHeight"/></c:set>
<c:set var="i18n_labelPreCheck"><fmt:message key="eform.visual.editor.label.preCheck"/></c:set>
<c:set var="i18n_labelAllNoFunction"><fmt:message key="eform.visual.editor.label.allNoFunction"/></c:set>
<c:set var="i18n_labelPreCheckByGender"><fmt:message key="eform.visual.editor.label.preCheckByGender"/></c:set>
<c:set var="i18n_labelCheckWhen"><fmt:message key="eform.visual.editor.label.checkWhen"/></c:set>
<c:set var="i18n_labelAddFunction"><fmt:message key="eform.visual.editor.label.addFunction"/></c:set>
<c:set var="i18n_labelAddSideBar"><fmt:message key="eform.visual.editor.label.addSideBar"/></c:set>
<c:set var="i18n_labelShowRulerMarks"><fmt:message key="eform.visual.editor.label.showRulerMarks"/></c:set>
<c:set var="i18n_labelEnableSnapGuides"><fmt:message key="eform.visual.editor.label.enableSnapGuides"/></c:set>
<c:set var="i18n_labelTextBorders"><fmt:message key="eform.visual.editor.label.textBorders"/></c:set>
<c:set var="i18n_labelXboxBorders"><fmt:message key="eform.visual.editor.label.xboxBorders"/></c:set>
<c:set var="i18n_labelEformName"><fmt:message key="eform.visual.editor.label.eformName"/></c:set>
<c:set var="i18n_labelIncludeFaxControls"><fmt:message key="eform.visual.editor.label.includeFaxControls"/></c:set>
<c:set var="i18n_labelDefaultFaxNo"><fmt:message key="eform.visual.editor.label.defaultFaxNo"/></c:set>
<c:set var="i18n_labelIncludeSetTickler"><fmt:message key="eform.visual.editor.label.includeSetTickler"/></c:set>
<c:set var="i18n_labelStandAloneTesting"><fmt:message key="eform.visual.editor.label.standAloneTesting"/></c:set>
<c:set var="i18n_labelTriggeringEvent"><fmt:message key="eform.visual.editor.label.triggeringEventName"/></c:set>
<c:set var="i18n_labelFunctionParams"><fmt:message key="eform.visual.editor.label.functionParameters"/></c:set>
<c:set var="i18n_headingLoadEForm"><fmt:message key="eform.visual.editor.heading.loadExistingEForm"/></c:set>
<c:set var="i18n_headingPageSetup"><fmt:message key="eform.visual.editor.heading.pageSetup"/></c:set>
<c:set var="i18n_headingFormBuilding"><fmt:message key="eform.visual.editor.heading.formBuilding"/></c:set>
<c:set var="i18n_headingFormStylization"><fmt:message key="eform.visual.editor.heading.formStylization"/></c:set>
<c:set var="i18n_headingFinalize"><fmt:message key="eform.visual.editor.heading.finalize"/></c:set>
<c:set var="i18n_tabCheckbox"><fmt:message key="eform.visual.editor.tab.checkbox"/></c:set>
<c:set var="i18n_tabTextBox"><fmt:message key="eform.visual.editor.tab.textBox"/></c:set>
<c:set var="i18n_tabLabel"><fmt:message key="eform.visual.editor.tab.label"/></c:set>
<c:set var="i18n_tabShapes"><fmt:message key="eform.visual.editor.tab.shapes"/></c:set>
<c:set var="i18n_tabImages"><fmt:message key="eform.visual.editor.tab.images"/></c:set>
<c:set var="i18n_tabSignature"><fmt:message key="eform.visual.editor.tab.signature"/></c:set>
<c:set var="i18n_optPortrait"><fmt:message key="eform.visual.editor.option.portrait"/></c:set>
<c:set var="i18n_optLandscape"><fmt:message key="eform.visual.editor.option.landscape"/></c:set>
<c:set var="i18n_optCustom"><fmt:message key="eform.visual.editor.option.custom"/></c:set>
<c:set var="i18n_optAlwaysVisible"><fmt:message key="eform.visual.editor.option.alwaysVisible"/></c:set>
<c:set var="i18n_optInvisibleOnPrint"><fmt:message key="eform.visual.editor.option.invisibleOnPrint"/></c:set>
<c:set var="i18n_optAlwaysInvisible"><fmt:message key="eform.visual.editor.option.alwaysInvisible"/></c:set>
<c:set var="i18n_tooltipDragActivate"><fmt:message key="eform.visual.editor.tooltip.dragActivate"/></c:set>
<c:set var="i18n_tooltipXBoxActivate"><fmt:message key="eform.visual.editor.tooltip.xBoxActivate"/></c:set>
<c:set var="i18n_tooltipRadioBoxActivate"><fmt:message key="eform.visual.editor.tooltip.radioBoxActivate"/></c:set>
<c:set var="i18n_tooltipButtonFrameActivate"><fmt:message key="eform.visual.editor.tooltip.buttonFrameActivate"/></c:set>
<c:set var="i18n_tooltipNotInUse"><fmt:message key="eform.visual.editor.tooltip.notInUse"/></c:set>
<c:set var="i18n_dialogAttachNewFunction"><fmt:message key="eform.visual.editor.dialog.attachNewFunction"/></c:set>
<c:set var="i18n_dialogFunctionParticularsPrefix"><fmt:message key="eform.visual.editor.dialog.functionParticularsPrefix"/></c:set>
<c:set var="i18n_dialogFunctionParticularsSuffix"><fmt:message key="eform.visual.editor.dialog.functionParticularsSuffix"/></c:set>
<c:set var="i18n_dialogConfirmPageRemoval"><fmt:message key="eform.visual.editor.dialog.confirmPageRemoval"/></c:set>
<c:set var="i18n_dialogConfirmPageRemovalMsg"><fmt:message key="eform.visual.editor.dialog.confirmPageRemovalMessage"/></c:set>
<c:set var="i18n_dialogSaveAsNewEform"><fmt:message key="eform.visual.editor.dialog.saveAsNewEform"/></c:set>
<c:set var="i18n_dialogUpdateEform"><fmt:message key="eform.visual.editor.dialog.updateEform"/></c:set>
<c:set var="i18n_alertSaveSuccessful"><fmt:message key="eform.visual.editor.alert.saveSuccessful"/></c:set>
<c:set var="i18n_alertSaveFailure"><fmt:message key="eform.visual.editor.alert.saveFailure"/></c:set>
<c:set var="i18n_alertOnlyOneMasterBox"><fmt:message key="eform.visual.editor.alert.onlyOneMasterBox"/></c:set>
<c:set var="i18n_alertSelectMasterCheckbox"><fmt:message key="eform.visual.editor.alert.selectMasterCheckbox"/></c:set>
<c:set var="i18n_alertWouldSubmitEform"><fmt:message key="eform.visual.editor.alert.wouldSubmitEform"/></c:set>
<c:set var="i18n_alertActLikeCheckbox"><fmt:message key="eform.visual.editor.alert.actLikeCheckbox"/></c:set>
<c:set var="i18n_alertBrowserNotSupported"><fmt:message key="eform.visual.editor.alert.browserNotSupported"/></c:set>
<c:set var="i18n_confirmRadioboxGrouping"><fmt:message key="eform.visual.editor.confirm.completedRadioboxGrouping"/></c:set>
<c:set var="i18n_confirmUnsavedChanges"><fmt:message key="eform.visual.editor.confirm.unsavedChanges"/></c:set>
<c:set var="i18n_helpAltResize"><fmt:message key="eform.visual.editor.help.altDraggableResize"/></c:set>
<c:set var="i18n_helpShiftAspectRatio"><fmt:message key="eform.visual.editor.help.shiftAspectRatio"/></c:set>
<c:set var="i18n_helpCtrlCVCopy"><fmt:message key="eform.visual.editor.help.ctrlCVCopy"/></c:set>
<c:set var="i18n_noteCustomScripts"><fmt:message key="eform.visual.editor.note.customScriptsNotPreserved"/></c:set>
<c:set var="i18n_textTextBox"><fmt:message key="eform.visual.editor.text.textBox"/></c:set>
<c:set var="i18n_textCheckbox"><fmt:message key="eform.visual.editor.text.checkbox"/></c:set>
<c:set var="i18n_textTrash"><fmt:message key="eform.visual.editor.text.trash"/></c:set>
<c:set var="i18n_textAddWetSignature"><fmt:message key="eform.visual.editor.text.addWetSignature"/></c:set>
<c:set var="i18n_textControls"><fmt:message key="eform.visual.editor.text.controls"/></c:set>
<c:set var="i18n_textGuideOptions"><fmt:message key="eform.visual.editor.text.guideOptions"/></c:set>
<script>
/* i18n strings for Visual eForm Editor - populated server-side, OWASP JS-encoded */
var EFORM_I18N = {
    alertTitle: '${e:forJavaScript(i18n_alertTitle)}',
    noMessage: '${e:forJavaScript(i18n_noMessage)}',
    buttonOk: '${e:forJavaScript(i18n_buttonOk)}',
    buttonCancel: '${e:forJavaScript(i18n_buttonCancel)}',
    buttonClose: '${e:forJavaScript(i18n_buttonClose)}',
    buttonDelete: '${e:forJavaScript(i18n_buttonDelete)}',
    buttonClear: '${e:forJavaScript(i18n_buttonClear)}',
    buttonAddPage: '${e:forJavaScript(i18n_buttonAddPage)}',
    buttonRemovePage: '${e:forJavaScript(i18n_buttonRemovePage)}',
    buttonSubmit: '${e:forJavaScript(i18n_buttonSubmit)}',
    buttonPrint: '${e:forJavaScript(i18n_buttonPrint)}',
    buttonPrintAndSubmit: '${e:forJavaScript(i18n_buttonPrintAndSubmit)}',
    buttonViewSource: '${e:forJavaScript(i18n_buttonViewSource)}',
    buttonDownload: '${e:forJavaScript(i18n_buttonDownload)}',
    buttonPrintPreview: '${e:forJavaScript(i18n_buttonPrintPreview)}',
    buttonLoadEForm: '${e:forJavaScript(i18n_buttonLoadEForm)}',
    buttonAttachFunction: '${e:forJavaScript(i18n_buttonAttachFunction)}',
    buttonAllNo: '${e:forJavaScript(i18n_buttonAllNo)}',
    buttonGroupYellowRadios: '${e:forJavaScript(i18n_buttonGroupYellowRadios)}',
    buttonResetYellowRadios: '${e:forJavaScript(i18n_buttonResetYellowRadios)}',
    buttonGroupNoRadios: '${e:forJavaScript(i18n_buttonGroupNoRadios)}',
    buttonGroupCompleted: '${e:forJavaScript(i18n_buttonGroupCompleted)}',
    buttonLinkOrangeBoxes: '${e:forJavaScript(i18n_buttonLinkOrangeBoxes)}',
    buttonResetOrangeBoxes: '${e:forJavaScript(i18n_buttonResetOrangeBoxes)}',
    labelSubject: '${e:forJavaScript(i18n_labelSubject)}',
    labelSelectEForm: '${e:forJavaScript(i18n_labelSelectEForm)}',
    labelSelectBgImage: '${e:forJavaScript(i18n_labelSelectBgImage)}',
    labelWidth: '${e:forJavaScript(i18n_labelWidth)}',
    labelHeight: '${e:forJavaScript(i18n_labelHeight)}',
    labelPageDimensions: '${e:forJavaScript(i18n_labelPageDimensions)}',
    labelCustomTitle: '${e:forJavaScript(i18n_labelCustomTitle)}',
    labelLinkTo: '${e:forJavaScript(i18n_labelLinkTo)}',
    labelTemplateSize: '${e:forJavaScript(i18n_labelTemplateSize)}',
    labelTemplateWidth: '${e:forJavaScript(i18n_labelTemplateWidth)}',
    labelTemplateHeight: '${e:forJavaScript(i18n_labelTemplateHeight)}',
    labelPreCheck: '${e:forJavaScript(i18n_labelPreCheck)}',
    labelAllNoFunction: '${e:forJavaScript(i18n_labelAllNoFunction)}',
    labelPreCheckByGender: '${e:forJavaScript(i18n_labelPreCheckByGender)}',
    labelCheckWhen: '${e:forJavaScript(i18n_labelCheckWhen)}',
    labelAddFunction: '${e:forJavaScript(i18n_labelAddFunction)}',
    labelAddSideBar: '${e:forJavaScript(i18n_labelAddSideBar)}',
    labelShowRulerMarks: '${e:forJavaScript(i18n_labelShowRulerMarks)}',
    labelEnableSnapGuides: '${e:forJavaScript(i18n_labelEnableSnapGuides)}',
    labelTextBorders: '${e:forJavaScript(i18n_labelTextBorders)}',
    labelXboxBorders: '${e:forJavaScript(i18n_labelXboxBorders)}',
    labelEformName: '${e:forJavaScript(i18n_labelEformName)}',
    labelIncludeFaxControls: '${e:forJavaScript(i18n_labelIncludeFaxControls)}',
    labelDefaultFaxNo: '${e:forJavaScript(i18n_labelDefaultFaxNo)}',
    labelIncludeSetTickler: '${e:forJavaScript(i18n_labelIncludeSetTickler)}',
    labelStandAloneTesting: '${e:forJavaScript(i18n_labelStandAloneTesting)}',
    labelTriggeringEvent: '${e:forJavaScript(i18n_labelTriggeringEvent)}',
    labelFunctionParams: '${e:forJavaScript(i18n_labelFunctionParams)}',
    headingLoadEForm: '${e:forJavaScript(i18n_headingLoadEForm)}',
    headingPageSetup: '${e:forJavaScript(i18n_headingPageSetup)}',
    headingFormBuilding: '${e:forJavaScript(i18n_headingFormBuilding)}',
    headingFormStylization: '${e:forJavaScript(i18n_headingFormStylization)}',
    headingFinalize: '${e:forJavaScript(i18n_headingFinalize)}',
    tabCheckbox: '${e:forJavaScript(i18n_tabCheckbox)}',
    tabTextBox: '${e:forJavaScript(i18n_tabTextBox)}',
    tabLabel: '${e:forJavaScript(i18n_tabLabel)}',
    tabShapes: '${e:forJavaScript(i18n_tabShapes)}',
    tabImages: '${e:forJavaScript(i18n_tabImages)}',
    tabSignature: '${e:forJavaScript(i18n_tabSignature)}',
    optPortrait: '${e:forJavaScript(i18n_optPortrait)}',
    optLandscape: '${e:forJavaScript(i18n_optLandscape)}',
    optCustom: '${e:forJavaScript(i18n_optCustom)}',
    optAlwaysVisible: '${e:forJavaScript(i18n_optAlwaysVisible)}',
    optInvisibleOnPrint: '${e:forJavaScript(i18n_optInvisibleOnPrint)}',
    optAlwaysInvisible: '${e:forJavaScript(i18n_optAlwaysInvisible)}',
    tooltipDragActivate: '${e:forJavaScript(i18n_tooltipDragActivate)}',
    tooltipXBoxActivate: '${e:forJavaScript(i18n_tooltipXBoxActivate)}',
    tooltipRadioBoxActivate: '${e:forJavaScript(i18n_tooltipRadioBoxActivate)}',
    tooltipButtonFrameActivate: '${e:forJavaScript(i18n_tooltipButtonFrameActivate)}',
    tooltipNotInUse: '${e:forJavaScript(i18n_tooltipNotInUse)}',
    dialogAttachNewFunction: '${e:forJavaScript(i18n_dialogAttachNewFunction)}',
    dialogFunctionParticularsPrefix: '${e:forJavaScript(i18n_dialogFunctionParticularsPrefix)}',
    dialogFunctionParticularsSuffix: '${e:forJavaScript(i18n_dialogFunctionParticularsSuffix)}',
    dialogConfirmPageRemoval: '${e:forJavaScript(i18n_dialogConfirmPageRemoval)}',
    dialogConfirmPageRemovalMsg: '${e:forJavaScript(i18n_dialogConfirmPageRemovalMsg)}',
    dialogSaveAsNewEform: '${e:forJavaScript(i18n_dialogSaveAsNewEform)}',
    dialogUpdateEform: '${e:forJavaScript(i18n_dialogUpdateEform)}',
    alertSaveSuccessful: '${e:forJavaScript(i18n_alertSaveSuccessful)}',
    alertSaveFailure: '${e:forJavaScript(i18n_alertSaveFailure)}',
    alertOnlyOneMasterBox: '${e:forJavaScript(i18n_alertOnlyOneMasterBox)}',
    alertSelectMasterCheckbox: '${e:forJavaScript(i18n_alertSelectMasterCheckbox)}',
    alertWouldSubmitEform: '${e:forJavaScript(i18n_alertWouldSubmitEform)}',
    alertActLikeCheckbox: '${e:forJavaScript(i18n_alertActLikeCheckbox)}',
    alertBrowserNotSupported: '${e:forJavaScript(i18n_alertBrowserNotSupported)}',
    confirmRadioboxGrouping: '${e:forJavaScript(i18n_confirmRadioboxGrouping)}',
    confirmUnsavedChanges: '${e:forJavaScript(i18n_confirmUnsavedChanges)}',
    helpAltResize: '${e:forJavaScript(i18n_helpAltResize)}',
    helpShiftAspectRatio: '${e:forJavaScript(i18n_helpShiftAspectRatio)}',
    helpCtrlCVCopy: '${e:forJavaScript(i18n_helpCtrlCVCopy)}',
    noteCustomScripts: '${e:forJavaScript(i18n_noteCustomScripts)}',
    textTextBox: '${e:forJavaScript(i18n_textTextBox)}',
    textCheckbox: '${e:forJavaScript(i18n_textCheckbox)}',
    textTrash: '${e:forJavaScript(i18n_textTrash)}',
    textAddWetSignature: '${e:forJavaScript(i18n_textAddWetSignature)}',
    textControls: '${e:forJavaScript(i18n_textControls)}',
    textGuideOptions: '${e:forJavaScript(i18n_textGuideOptions)}'
};
</script>

    <script>
       //for a unique identifier
	   //myunique = prompt("Enter Unique Identifier","")  //2021-Jan-10 commented out May-01-2024

        //enable submit button
        $(document).ready(function() {
            $('#SubmitButton').attr('disabled', false);
            $('#PrintSubmitButton').attr('disabled', false);
            $('#subject').attr('disabled', false);

        });

        function custom_alert( message, title ) {
            if ( !title )
                title = EFORM_I18N.alertTitle;

            if ( !message )
                message = EFORM_I18N.noMessage;

            var buttons = {};
            buttons[EFORM_I18N.buttonOk] = function() {
                $( this ).dialog( 'close' );
            };
            $('<div></div>').text( message ).dialog({
                title: title,
                resizable: true,
                modal: true,
                buttons: buttons
            });
        }


        // open to full screen
        window.moveTo(0, 0);
        if (document.all) {
            top.window.resizeTo(screen.availWidth, screen.availHeight);
        } else if (document.layers || document.getElementById) {
            if (top.window.outerHeight < screen.availHeight || top.window.outerWidth < screen.availWidth) {
                top.window.outerHeight = screen.availHeight;
                top.window.outerWidth = screen.availWidth;
            }
        }

        function dbWindow(qq) {
            if (runStandaloneVersion) {
                var myDbWindow = window.open('Eform_dbtags.html', 'mywindow', 'width=800, height=800,  top=0, left=0')
                myDbWindow.moveTo(0, 0);
            } else {
                var myDbWindow = window.open('${e:forJavaScript(oscar_image_path)}Eform_dbtags.html', 'mywindow', 'width=800, height=800,  top=0, left=0')
                myDbWindow.moveTo(0, 0);
            }
            // Get the modal
            var modal = myDbWindow;
            // When the user clicks anywhere outside of the modal, close it
            window.onclick = function(event) {
                if (event.target != modal) {
                    modal.close()
                }
            }
        }

        function openNavGen() {
            //window.resizeTo(1100,900)
            document.getElementById("mySidenavGen").style.width = "250px";
        }

        function closeNavGen() {
            document.getElementById('mySidenavGen').style.width = '0';
            //document.getElementById('mySidenavGen').style.visibility = "hidden";
        }

        function mydraginput(myelement) {
            $(myelement).parent().attr('title', 'Click to drag. DoubleClick to activate'); //2020-May-12

            var e = document.getElementById('MeasureSelect');
            if (e) {
                if ($('#toggleGOscarDbCheckbox5').prop('checked') && $('#toggleGOscarDbCheckbox10').prop('checked') && $('#toggleGOscarDbCheckbox7').prop('checked')) {
                    myelement.name = 'm$' + $(e).val() + '#value';
                }
                if ($('#toggleGOscarDbCheckbox5').prop('checked') && $('#toggleGOscarDbCheckbox10').prop('checked') && $('#toggleGOscarDbCheckbox8').prop('checked')) {
                    myelement.name = 'm$' + $(e).val() + '#dateObserved'
                }
            }
        }

        function measureTagfx(myelement) { //function for measurement tags
            var myinput = $('#textBoxTemplate').find('input')
            if ($('#toggleGOscarDbCheckbox5').prop('checked')) { //2020-May-02
                $('#someName').val(""); // 2020-May-03

                var e = document.getElementById('MeasureSelect');
                if ($('#toggleGOscarDbCheckbox7').prop('checked')) {
                    $('#textBoxTemplate').find(':input').attr('oscarDB', 'm$' + $(e).val() + '#value');
                    $('#textBoxTemplate').find(':input').attr('title', 'm$' + $(e).val() + '#value'); //2019-Feb-15
                    //$textAreaTemplate.find('textarea').removeAttr('title'); //2019-Feb-15
                }
                if ($('#toggleGOscarDbCheckbox8').prop('checked')) {
                    $('#textBoxTemplate').find(':input').attr('oscarDB', 'm$' + $(e).val() + '#dateObserved');
                    $('#textBoxTemplate').find(':input').attr('title', 'm$' + $(e).val() + '#dateObserved'); //2019-Feb-15
                }
            }
        }

        var measureArray = [];

        function getMeasures(measure) {
            //scrape to get the measurements on this system
            //converted to asynchronous to significantly speed ui load
            var elements = (window.location.pathname.split('/', 2));
            firstElement = (elements.slice(1));
            vPath = ("https://" + location.host + "/" + firstElement + "/");
            var newURL = vPath + "encounter/oscarMeasurements/SetupDisplayMeasurementTypes.do";
            $.ajax({
                type: "GET",
                url: newURL,
                async: true,
                success: function(data) {
                    var str = data //local variable
                    if (!str) {
                        return;
                    }
                    var myRe = /mType=([a-zA-Z0-9]+)/g
                    var myArray;
                    var i = 0;
                    while ((myArray = myRe.exec(str)) !== null) {
                        measureArray[i] = myArray[1];
                        i = i + 1;
                    }
                },
                error: function(jqXHR, textStatus, errorThrown) {
                    console.error("Failed to load measurements:", textStatus, errorThrown);
                }
            });

        }

    </script>
    <script>
        //Detect browser
        navigator.sayswho = (function() {
            var ua = navigator.userAgent,
                tem,
                M = ua.match(/(opera|chrome|safari|firefox|msie|trident(?=\/))\/?\s*(\d+)/i) || [];
            if (/trident/i.test(M[1])) {
                tem = /\brv[ :]+(\d+)/g.exec(ua) || [];
                return 'IE ' + (tem[1] || '');
            }
            if (M[1] === 'Chrome') {
                tem = ua.match(/\b(OPR|Edge)\/(\d+)/);
                if (tem != null) return tem.slice(1).join(' ').replace(/OPR/g, 'Opera');
            }
            M = M[2] ? [M[1], M[2]] : [navigator.appName, navigator.appVersion, '-?'];
            if ((tem = ua.match(/version\/(\d+)/i)) != null) M.splice(1, 1, tem[1]);
            return M.join(' ');
        })();
        var browser_version = navigator.sayswho
        console.log(browser_version);
        // declare variables for listfc to be reused when creating source
        var functionTitles = [];
        var functionCode =[];

        function listfc() { //create a select list of functions
            // var myDiv = document.getElementById('ui-id-1'); //Append option list to page //??????????
            openNavGen();
            var myDiv = document.getElementById('mySidenavGen');
            myDiv.style.padding = "50px 10px 20px 30px";
            $('#mySidenavGen').css('background-color', 'grey')

            //Create array of options to be added
            var array = [];
            var arraycontent = [];

            var num = 0
            var vset = 0
            for (var i in this) { //print a list of functions contained in this page
                if ((typeof this[i]).toString() == 'function' && this[i].toString().indexOf('native') == -1) {
                    if (this[i].name == 'SELECT_FUNCTION' || vset == 1) {
                        vset = 1
                        functionTitles[num] = this[i].name
                        functionCode[num] = this[i]
                        num = num + 1
                    }
                }
            }
            //Create and append select list
            var selectList = document.createElement('select');
            selectList.id = 'mySelect';
            myDiv.append(selectList);
            for (var i = 0; i < functionTitles.length; i++) {
                var option = document.createElement('option');
                option.value = functionCode[i];
                option.title = functionCode[i];
                option.text = functionTitles[i];
                selectList.appendChild(option);
            }

        }

        var input5 = document.createElement('input');
        input5.type = 'button';
        input5.id = "inputE"
        input5.value = 'Reset Yellow Radios';
        input5.onclick = showAlert5;
        input5.setAttribute('style', 'font-size:14px;position:fixed;top:30px;left:550px;');

        function showAlert5() {
            for (var q = 0; q < Rad.length; q++) {
                qq = document.getElementById(RadMain[q])
                qq.style.backgroundColor = "aquamarine";
                $(qq).removeClass("all-no")
                $(qq).removeClass("clear-yes")
            }
            $('#inputE').hide();
            $('#inputD').hide();
            Rad = []
            RadMain = []
        }

        var input4 = document.createElement('input');
        input4.type = 'button';
        input4.id = "inputD"
        input4.value = 'Group the No radio buttons';
        input4.onclick = showAlert4;
        input4.setAttribute('style', 'font-size:14px;position:fixed;top:30px;left:300px;');

        function showAlert4() {
            for (q = 0; q < Rad.length; q++) {
                qq = document.getElementById(RadMain[q])
                var mygroup = $(qq).attr("class").split(" ");
                for (i = 0; i < mygroup.length; i++) {
                    if (mygroup[i].indexOf("only-one-radio") > -1) {
                        var getmyclass = mygroup[i]
                    }
                }
                zz = document.getElementsByClassName(getmyclass)
                for (i = 0; i < zz.length; i++) {
                    tt = document.getElementById(zz[i].id)
                    $(tt).addClass("clear-yes")
                }
                qq.style.backgroundColor = "aquamarine";
                $(qq).addClass("all-no")
                $(qq).removeClass("clear-yes")
            }
            $('#inputD').hide();
            $('#inputE').hide();
        }

        var input = document.createElement('input');
        input.type = 'button';
        input.id = "inputA"
        input.value = 'Group Yellow Radios';
        input.onclick = showAlert;
        input.setAttribute('style', 'font-size:14px;position:fixed;top:30px;left:300px;');

        function showAlert() {
            groupTitle = "";
			var k;
            for (q = 0; q < Rad.length; q++) { // Rad is an array of ids
                qq = document.getElementById(Rad[q]);
				// only update Radios that are backgroundColor of yellow
				if ( qq.style.backgroundColor == "yellow" ) {
					if (typeof k === 'undefined') {
						k = q;
					};
					console.log("updating id "+ Rad[q] + " with class only-one-radio#"+ Rad[k]);
					qq.style.backgroundColor = "aquamarine";
					$(qq).removeClass("only-one-radio").addClass("only-one-radio" + "#" + Rad[k]);
					if (!groupTitle) {
						groupTitle = prompt("Enter a descriptive Title for the Group", Rad[k]);
					}
					document.getElementById(RadMain[q]).title = groupTitle;
				}
            }
            $('#inputA').hide();
            $('#inputC').show();
        }

        var input1 = document.createElement('input');
        input1.type = 'button';
        input1.id = "inputB"
        input1.value = 'Reset Yellow Radios';
        input1.onclick = showAlert1;
        input1.setAttribute('style', 'font-size:14px;position:fixed;top:30px;left:500px;');

        function showAlert1() {
            for (q = 0; q < Rad.length; q++) { // Rad is an array of ids
                qq = document.getElementById(Rad[q])
                qq.style.backgroundColor = "pink";
                qq.name = qq.id
                myreset = $(qq).attr("class").split(" ");
                for (i = 0; i < myreset.length; i++) {
                    if (myreset[i].indexOf("only-one-radio") > -1) {
                        $(qq).removeClass(myreset[i]);
                    }
                }
                document.getElementById(RadMain[q]).title = "nill"
            }
            $('#inputB').hide();
        }
        var input3 = document.createElement('input');
        input3.type = 'button';
        input3.id = "inputC"
        input3.value = 'Group completed. Start new group';
        input3.onclick = showAlert3;
        input3.setAttribute('style', 'font-size:14px;position:fixed;top:30px;left:200px;');

        function showAlert3() {
            Rad = []
            RadMain = []
            $('#inputC').hide();
            $('#inputB').hide();
        }

        function myextrastep(vthis) {
            x = vthis
            y = vthis.childNodes[0]
            x.title = "Click here to set all no"
            if (y.id.indexOf("gen_input") > -1) {
                y.id = "Gen_ButtonId"
                y.name = "Gen_ButtonId"
            }
            if (x.id.indexOf("gen_widgetId") > -1) {
                x.id = "Gen_Widget_ButtonId"
            }

            if (confirm("Have you completed grouping the radioboxes?")) {
                AllNoSetflag = "on"
                SetAllNo(vthis)
            } else {
                AllNoSetflag = "off"
            }
        }

        function SetAllNo(vthis) { //starts the process of all-no
            document.getElementById('control_menu_1-placement-tabs-0').append(input4);
            document.getElementById('control_menu_1-placement-tabs-0').append(input5);
            $('#inputD').show();
            $('#inputE').show();
            document.getElementById('inputD').style.backgroundColor = "yellow";
            document.getElementById('inputE').style.backgroundColor = "red";
            y = vthis.childNodes[0].id
            if (y.indexOf("gen_input") > -1) {
                x = document.getElementById(vthis.id).id
                document.getElementById(y).style.backgroundColor = "yellow";

                if (!Rad.includes(x)) { //setup array of dblclicked elements no duplicates
                    Rad[Rad.length] = x
                    RadMain[Rad.length - 1] = y
                }
            }
        }

        function mydclick2(vthis) {
            Rad = []
            if ($('#ui-id-11').css('color') == "rgb(255, 255, 255)") { // on finalization tab
                //alert("not on this page")
                return
            }
            if ($('#ui-id-10').css('color') == "rgb(255, 255, 255)") { // on finalization tab
                //alert("not on this page")
                return
            }

            if ($('#ui-id-1').css('color') == "rgb(255, 255, 255)") { //check which tab is open
                document.getElementById('control_menu_1-placement-tabs-0').append(input);
                document.getElementById('control_menu_1-placement-tabs-0').append(input1);
                document.getElementById('control_menu_1-placement-tabs-0').append(input3);
            }

            if ($('#ui-id-2').css('color') == "rgb(255, 255, 255)") {
                document.getElementById('control_menu_1-placement-tabs-1').append(input);
                document.getElementById('control_menu_1-placement-tabs-1').append(input1);
                document.getElementById('control_menu_1-placement-tabs-1').append(input3);
            }

            var y = (vthis.childNodes[0])

            if (y.className.indexOf("only-one-radio") > -1) { // 2020-May-25
                RadMain[RadMain.length] = y
                $(y).css("background-color", "orange"); // !important
                linkTo = "LinkedTo-" + y.id + "-" + y.title
            }

            if (y.className.indexOf("xBox") > -1) {
                if (RadMain.length > 0) {
                    alert("Only one master box can be selected. Click reset to restart if needed")
                    return
                }
                $(y).css("background-color", "orange"); // !important
                RadMain[RadMain.length] = y
            }

            if (y.className.indexOf("TextBox") > -1) {
                if (!Rad.includes(y)) {
                    $(y).css("border", "3px solid orange");
                    Rad[Rad.length] = y
                }

            }

            if (y.className.indexOf("TextArea") > -1) {
                if (!Rad.includes(y)) {
                    $(y).css("border", "3px solid orange");
                    Rad[Rad.length] = y
                }

            }

            $('#inputA').show();
            $('#inputA').val("Link Orange Boxes")
            $('#inputA').prop("onclick", null).off("click");; //remove previous onclick event

            $('#inputA').click(function() { //add new function

                if (RadMain.length < 1) {
                    alert("Select one Master checkbox")
                    return
                }

                for (i = 0; i < Rad.length; i++) {
                    Rad[i].title = "LinkedTo-" + RadMain[0].id + "-" + RadMain[0].title
                }

                $('#inputA').hide();
                $('#inputC').show();
                //reset
                RadMain[0].style.backgroundColor = "aquamarine";
                RadMain = []

                for (i = 0; i < Rad.length; i++) {
                    Rad[i].style.backgroundColor = "";
                    Rad[i].style.border = ""
                }
                Rad = []
                //end reset
            });

            $('#inputB').show();
            $('#inputB').val("Reset Orange Boxes")
            $('#inputB').prop("onclick", null).off("click"); //remove previous onclick event
            $('#inputB').click(function() {
                //Reset RadMain
                if (RadMain.length > 0) {
                    RadMain[0].style.backgroundColor = "aquamarine";
                    RadMain = []
                }

                //Reset Rad
                for (i = 0; i < Rad.length; i++) {
                    var y = Rad[i]
                    y.style.backgroundColor = "";
                    y.style.border = ""
                }
                Rad = []

            });

            $('#inputC').hide();
            document.getElementById('inputA').style.backgroundColor = "orange";
            document.getElementById('inputB').style.backgroundColor = "red";
            document.getElementById('inputC').style.backgroundColor = "lightgreen";
        }

        function mydclick(vthis) {
            if (AllNoSetflag == "off") {
                if ($('#ui-id-11').css('color') == "rgb(255, 255, 255)") { // on finalization tab
                    //alert("not on this page")
                    return
                }
                if ($('#ui-id-10').css('color') == "rgb(255, 255, 255)") { // on finalization tab
                    //alert("not on this page")
                    return
                }

                if ($('#ui-id-1').css('color') == "rgb(255, 255, 255)") { //check which tab is open
                    document.getElementById('control_menu_1-placement-tabs-0').append(input);
                    document.getElementById('control_menu_1-placement-tabs-0').append(input1);
                    document.getElementById('control_menu_1-placement-tabs-0').append(input3);
                }

                if ($('#ui-id-2').css('color') == "rgb(255, 255, 255)") {
                    document.getElementById('control_menu_1-placement-tabs-1').append(input);
                    document.getElementById('control_menu_1-placement-tabs-1').append(input1);
                    document.getElementById('control_menu_1-placement-tabs-1').append(input3);
                }

                $('#inputA').val("Group Yellow Radios")
                $('#inputB').val("Reset Yellow Radios")
                document.getElementById("inputA").onclick = function() {
                    showAlert();
                }; //2020-May-27

                $('#inputA').show();
                $('#inputB').show();
                $('#inputC').hide();
                document.getElementById('inputA').style.backgroundColor = "yellow";
                document.getElementById('inputB').style.backgroundColor = "red";
                document.getElementById('inputC').style.backgroundColor = "lightgreen";

                //*************combination radio box and linkbox*********  2020-May-25
                var z = (vthis.childNodes[0])
                //alert(Rad)
                if (z.getAttribute('onclick') == 'myupdate(this);LinkXBoxfunction(this)' && Rad.length == 0) {
                    //alert("combination box")
                    var linkTo = "LinkedTo-" + z.id + "-" + z.title
                    //alert(linkTo)
                    $(z).css("background-color", "orange"); // !important
                    $('#inputA').hide();
                    $('#inputB').hide();
                    $('#inputC').hide();

                    mydclick2(vthis)
                    return
                }
                //********************
                y = vthis.childNodes[0].id
                vthis.title = y
                x = document.getElementById(y)
                if (!Rad.includes(x.id)) { //setup array of dblclicked elements no duplicates
                    Rad[Rad.length] = x.id
                    RadMain[Rad.length - 1] = vthis.id
                }
                x.style.backgroundColor = "yellow";
            } else {
                SetAllNo(vthis)
            }
        }
    </script>

    <style>
        body {
            margin: 10px;
            background: #d7d7d7;
        }

        #main_container {
            display: flex;
            flex-direction: row;
            flex-wrap: nowrap;
            width: 100%;
        }

        #eform_container {
            height: 100%;
            min-height: 100%;
            max-height: calc(100vh - 20px);
            /*subtract body margin space*/
            ;
            overflow: auto;
            width: auto;
            padding: 0 25px 0 0;
        }

        #eform_view_wrapper {
            height: 100%;
        }

        /* pop-in grab bar for resizing eform viewport/controls */
        #eform_view_wrapper>.ui-resizable-e {
            background-color: transparent;
            transition: 2s;
        }

        #eform_view_wrapper>.ui-resizable-e:hover {
            background-color: #f19901;
            transition-delay: 1s;
            transition-duration: 2s;
        }

        @media screen {
            .page_container {
                margin: 0 0 10px 0 !important;
                /* shadows */
                -moz-box-shadow: 3px 3px 5px 0 #a0a0a0;
                -webkit-box-shadow: 3px 3px 5px 0 #a0a0a0;
                box-shadow: 3px 3px 5px 0 #a0a0a0;
            }
        }

        .flexV {
            display: flex;
            flex-direction: column;
        }

        .flexH {
            display: flex;
            flex-direction: row;
        }

        #control {
            padding: 0 10px 0 10px;
            width: auto;
            flex: 1;
            overflow: auto;
            max-height: calc(100vh - 20px);
            /*subtract body margin space*/
        }

        #control_menu_1-page_setup fieldset {
            flex: 1;
        }

        .gen-control-menu {
            box-sizing: border-box;
        }

        .gen-control-menu label {
            display: inline-block;
            width: 200px;
            text-align: right;
            padding: 2px 5px 2px 5px;
        }

        .gen-control-menu input[type=text] {
            display: inline-block;
            padding: 2px 5px 2px 5px;
            margin: 2px;
            width: calc(100% - 220px);
            /* 100% - label width + padding */
        }

        .gen-control-menu input[type=checkbox] {
            width: 16px;
            height: 16px;
        }

        /* these classes are added by jquery ui but we need to ensure that disabled resizables don't show */
        .ui-resizable-disabled .ui-resizable-handle,
        .ui-resizable-autohide .ui-resizable-handle {
            display: none !important;
        }

        .gen-trash_frame {
            border: dashed #d9534f;
            padding: 3px;
            box-sizing: border-box;
            text-align: center;
            float: left;
            width: 35%;
            min-width: 150px;
            height: 100%;
            max-height: 215px;
            min-height: 215px;
        }

        .gen-trashHover {
            background: rgba(217, 83, 79, 0.75);
        }

        .gen-stitch_frame {
            padding: 10px;
            margin: 10px;
            background: #8198c3;
            color: #fff;
            line-height: 1.3em;
            border: 2px dashed #fff;
            border-radius: 10px;
            box-shadow: 0 0 0 4px #8198c3, 2px 1px 6px 4px rgba(255, 255, 255, 0.5);
        }

        .gen-draggable {
            cursor: default;
        }

        .gen-selectOverflow {
            max-height: 200px;
        }

        .divHighlight {
            background-color: #bcd5eb !important;
            outline: 2px solid #5166bb !important;
        }

        .selectedHighlight {
            outline: 2px solid #0bbb00 !important;
        }

        .gen-snapGuide,
        .gen-snapLine {
            position: absolute;
            background: transparent;
            border: 0 solid;
            top: 0;
            left: 0;
        }

        .handle {
            position: absolute;
            width: 7px;
            height: 7px;
            top: 0;
            left: 0;
            padding: 0;
            z-index: 90;
        }

        .gen-snapLine.vertical {
            border-right: 1px solid red;
            width: 0;
            height: 100%;
            left: 100%;
            z-index: 90;
        }

        .gen-snapLine.vertical>.handle {
            margin: 0 0 0 -3px;
            height: 100%;
            cursor: e-resize;
        }

        .gen-snapLine.vertical>.ruler {
            background: linear-gradient(to bottom, rgba(255, 0, 0, 0.50) 1px, transparent 0px);
            background-size: 100% 10px;
        }

        .gen-snapLine.horizontal {
            border-bottom: 1px solid red;
            width: 100%;
            height: 0;
            top: 100%;
            z-index: 90;
        }

        .gen-snapLine.horizontal>.handle {
            margin: -3px 0 0 0;
            width: 100%;
            cursor: s-resize;
        }

        .gen-snapLine.horizontal>.ruler {
            background: linear-gradient(to left, rgba(255, 0, 0, 0.50) 1px, transparent 0px);
            background-size: 10px 100%;
        }

        /* only used by eform generator draggables */
        .gen-widget {
            position: relative;
            z-index: 1;
        }

        .gen-widget .inputOverride {
            position: absolute;
            width: 100%;
            height: 100%;
            z-index: 85;
        }

        .gen-widget .inputOverride[disabled] {
            z-index: -1;
        }

        /* highlight inputs that are tagged for various auto-fills on startup */
        input[type="checkbox"][class*="gender_precheck_"] {
            outline: 1px solid rgba(245, 190, 255, 1.0) !important;
        }

        [class*="gender_precheck_"] {
            background-color: rgba(245, 190, 255, 1.0) !important;
        }

        [oscarDB] {
            background-color: rgba(245, 190, 255, 0.50) !important;
        }

    </style>

    <style id="allno_style" class="toSource">

        .all-no-button1 {
            background: white;
            border: 7px solid #0000;
            padding-right: 45px;
            padding-bottom: 8px;
            padding-left: 5px;
            display: inline-block;
            border-radius: 100px;
			margin: 10px;
        }
    </style>
    <style id="sidenav_style" class="toSource">
        /* Float Box */
       body {
            font-family: "Lato", sans-serif;
        }

        .sidenav {
            height: 100%;
            width: 0;
            position: fixed;
            z-index: 1;
            top: 0;
            right: 0;
            background-color: #9ef3fa;
            overflow-x: hidden;
            transition: 0.5s;
            padding-top: 60px;
        }

        .sidenav a {
            padding: 8px 8px 8px 32px;
            text-decoration: none;
            font-size: 18px;
            color: #818181;
            display: block;
            transition: 0.3s;
        }

        .sidenav a:hover {
            color: #011;
        }

        .sidenav .closebtn {
            position: absolute;
            top: 0;
            right: 25px;
            font-size: 36px;
            margin-left: 50px;
        }

        .sidenav {
            padding-top: 15px;
        }

        .sidenav a {
            font-size: 18px;
        }
        /* END Float Box */
    </style>
    <style id="eform_style" class="toSource">

        /* base style for pages */
        .page_container {
            position: relative;
            background: #ffffff;
            float: left;
            border: solid 0;
            margin: 0;
        }

        /* div containing form input elements */
        .input_elements {
            position: absolute;
            top: 0;
            left: 0;
            font-size: 14px; /*2020-Sep-24 Input box text size*/
            width: 100%;
            height: 100%;
        }

        /* base styling for input wrapper divs */
        .gen-widget {
            display: inline-block;
            text-align: left;
            vertical-align: top;
            background: transparent;
            border: 0;
        }

        /* style wrapped input elements */
        .gen-widget input,
        textarea {
            position: absolute;
            display: inline-block;
            text-align: left;
            font-weight: normal;
            font-size: 14px; /*2020-Sep-24 TextArea Text size*/
            font-family: 'Helvetica', 'Arial', sans-serif;
            background: transparent;
            color: #000000;
            border: 1px solid #d2d2d2;
            padding: 0;
            width: 100%;
            height: 100%;
            z-index: 10;
            margin: 0;
        }

        /* remove firefox webkit so that checkboxes are resizable */
        .gen-widget input[type=checkbox] {
            -moz-appearance: none;
        }

        /* define other */
        .noborder {
            border-color: transparent !important;
        }

        /* print only styling */
        @media print {
            .DoNotPrint {
                display: none;
            }

            ::-webkit-input-placeholder { /* WebKit browsers */
                color: transparent;
            }

            ::-moz-placeholder { /* Mozilla Firefox 19+ */
                color: transparent;
            }

            .noborderPrint {
                border-color: transparent !important;
            }

            .page_container {
                page-break-after: always;
            }
        }

		label {
			font-family: 'Helvetica', 'Arial', sans-serif;
		}

        /* define label styling (only used by generated labels) */
        .label-style_1 {
            color: #000000;
            font-size: 12px;
            font-weight: normal;
            font-family: Verdana, Arial, sans-serif;
        }


	</style>
    <style id="radio_style" class="toSource">
	    [class^="only-one-radio"] {
            height: 12px;
            width: 12px;
            font-size: 12px;
            border: 1px solid #000000;
            cursor: pointer;
            font-weight: bold;
            text-align: center;
            background: pink;
        }

        .only-one-radio input {
            background: pink;
            text-align: center;
            font-weight: bold;
            font-size: 16px;
            border: 1px solid black;
            cursor: pointer;
            /*Rounds- delete comment if preferred */
            -webkit-border-radius: 12px; -moz-border-radius: 12px; border-radius: 10px;
        }

        .only-one-radio input:focus {
            outline: none;
            color: transparent;
            text-shadow: 0 0 0 #000;
        }

		/* print only styling */
        @media print {
            .only-one-radio input {
				background: white;
				border: 1px solid black;
            }
        }
    </style>
    <style id="xbox_style" class="toSource">
        /* define xbox styling */
        .gen-xBox input {
            background: aquamarine;
            text-align: center;
            font-weight: bold;
            font-size: 16px;
            border: 1px solid black;
            cursor: pointer;
        }

        .gen-xBox input:focus {
            outline: none;
            color: transparent;
            text-shadow: 0 0 0 #000;
        }
        /* print only styling */
        @media print {
            .gen-xBox input {
				background: white;
				border: 1px solid black;
            }
        }
    </style>
    <style id="eform_style_shapes" class="toSource">
        /* define shape styling
		 * can be safely removed if shapes are not used */
        .circle {
            border-radius: 50%;
            width: 100%;
            height: 100%;
            background: #FFFFFF;
            border: 1px solid black;
        }

        .square {
            border-radius: 0;
            background: #FFFFFF;
            border: 1px solid black;
        }

        .square-rounded {
            border-radius: 15%;
            background: #FFFFFF;
            border: 1px solid black;
        }
    </style>
    <style id="stamp_style" class="toSource">
	    .signatureStamp {
            background-color: #efefef;
            opacity: 0.6;
        }
        /* print only styling */
        @media print {
            .signatureStamp {
                background-color: transparent;
            }
        }
	</style>
    <style id="eform_style_signature" class="toSource">
        /* signature pad styling */
        .signaturePad {
            background-color: #efefef;
            opacity: 0.6;
        }

        .signaturePad canvas {
            position: absolute;
            width: 100%;
            height: 100%;
        }

        .signaturePad .canvas_frame {
            position: relative;
            width: 100%;
            height: 100%;
        }

        .signaturePad .clearBtn {
            position: relative;
            float: right;
            padding: 1px;
            line-height: 1em;
            font-size: 14px;
            font-family: monospace;
            text-align: center;
        }

        .signaturePad .clearBtn:active {
            color: red;
        }

        .signaturePad .signature_image {
            display: inline-block;
            position: absolute;
        }

        /* print only styling */
        @media print {
            .signaturePad {
                background-color: transparent;
                opacity: 1;
            }

            .signaturePad canvas {
                /* always hide canvas for printing. print image instead */
                display: none !important;
            }

            .signature_image {
                /* always show signature image when printing */
                display: inline-block !important;
            }
        }
    </style>
    <script>
        /** GLOBAL SCOPE CONSTANTS */
        var CONFIRM_PAGE_REMOVE_TITLE = "Confirm Page Removal";
        var CONFIRM_PAGE_REMOVE_MESSAGE = "You are about the remove a page from the eform. " +
            "This will delete any work on the page and cannot be undone. Are you sure you want to delete the page?";

        var OSCAR_SAVE_MESSAGE_NEW = "Save As New Eform";
        var OSCAR_SAVE_MESSAGE_UPDATE = "Update Eform";

        // make sure .page_container css matches this when modifying
        var eFormPageWidthPortrait = 800; //850 //800
        var eFormPageHeightPortrait = 1000; //1100
        var eFormPageWidthLandscape = 1100;
        var eFormPageHeightLandscape = 800;

        var defaultTextBoxWidth = 130; //256
        var defaultTextBoxHeight = 16; //16
        var defaultCheckboxSize = 14; //12
        var defaultbboxSize = 64;
        var defaultShapeSize = 64;

        var checkboxSizeRange = [1, 128];
        var textBoxSizeRange = [1, 1024];

        var inFirefox = (navigator.userAgent.search("Firefox") >= 0);
        var inChrome = (navigator.userAgent.search("Chrome") >= 0);
        var eFormViewMinWidth = 375; //px
        var eFormViewPadding = 25; //px
        // Fallback delay (ms) for revoking Blob URLs opened in a new window
        var BLOB_URL_REVOKE_TIMEOUT_MS = 60000;

        var defaultIncludeFaxControl = true;
        var defaultEnableSnapGuides = true;
        var defaultShowRuler = false;
        var defaultMenuOpenIndex = 1; // control menu accordion tab index

        /* define the base names for generated elements. */
        var baseWidgetName = "gen_widgetId";
        var baseButtonName = "gen_buttonId";
        var baseInputName = "gen_inputId";
        var baseBackImageName = "gen_backgroundImage";
        var baseImageWidgetName = "gen_dragImageTemplate";
        var baseSignatureDataName = "gen_signatureData";
        var basePageName = "page_";

        var XBOX_INPUT_SELECTOR = ".xBox";
        var TEXT_INPUT_SELECTOR = ":input[type=text]:not(.xBox), textarea";
        var CHEK_INPUT_SELECTOR = ":input[type=checkbox]";
        var GENDER_PRECHECK_CLASS_SELECTOR = "[class*=gender_precheck_]";

        var OSCAR_DISPLAY_IMG_SRC = "../eform/displayImage.do?imagefile=";
        var OSCAR_EFORM_ENTITY_URL = "../ws/rs/eform/";
        var OSCAR_EFORM_SEARCH_URL = "../ws/rs/eforms/";

        /**
         * Validates image src URLs using an allowlist to prevent XSS via dangerous URI schemes
         * (javascript:, data:text/html, etc.). Allows relative paths, http(s) URLs,
         * data:image/ URIs (used by signature pads), and simple filenames.
         */
        function isValidImageSrc(url) {
            if (!url || typeof url !== 'string') return false;
            if (/^(\/(?!\/)|\.\/|\.\.\/)/.test(url)) return true;
            if (/^https?:\/\//i.test(url)) return true;
            if (/^data:image\//i.test(url)) return true;
            if (/^[\w][\w.\- ]*$/.test(url)) return true; // simple filename
            console.error('isValidImageSrc: rejected URL:', url.substring(0, 50));
            return false;
        }

        /** GLOBAL SCOPE VARIABLES */
        var groupTitle
        var linkTo
        var setSideBar
        var eformName = "Untitled eForm";
		var defaultFaxNo = "";
        var textBordersVisibleState = 1;
        var xboxBordersVisibleState = 0;
        var orientationIndex = 0; //portrait 0 vs landscape 1 vs custom 2

        var Rad = []
        var RadMain = []
        var AllNoSetflag = "off";
        var newTitle = "";
		var myunique = "";

        var checkboxSize = defaultCheckboxSize;
        var textBoxWidth = defaultTextBoxWidth;
        var textBoxHeight = defaultTextBoxHeight;
        var eFormPageWidth = eFormPageWidthPortrait;
        var eFormPageHeight = eFormPageHeightPortrait;
        var enableElementHighlights = false;
        var dragAndDropEnabled = false;

        var etrigger;
        var parameter;

        // stores the current eform id. 0 if new eform.
        var eFormFid = 0;

        // store the list of images on the oscar server so only one load is needed.
        var eFormImageList = [];

        /** oscar db tags hardcoded for standalone. list update date: 2018-10-02 */

        var measureArray = [
        "02", "024UA", "24UR", "5DAA", "A1C", "AACP", "ACOS", "ACR", "ACS", "AEDR", "AELV", "AENC", "AHGM", "AIDU", "ALC", "ALPA", "ALT", "Ang", "ANR", "ANSY",
		"AORA", "ARAD", "ARDT", "ARMA", "ASAU", "ASPR", "AST", "ASTA", "ASWA", "ASYM", "BCTR", "BG", "BMED", "BMI", "BP", "BP", "BP", "CASA", "CD4", "CD4P",
		"CDMP", "CEDE", "CEDM", "CEDS", "CEDW", "CERV", "CGSD", "CIMF", "CMVI", "CODC", "COPE", "COPM", "COPS", "COUM", "CRCL", "CVD", "CXR", "DARB",
		"DEPR", "DESM", "DiaC", "DIER", "DIET", "DIFB", "DIGT", "DM", "DMED", "DMME", "DMSM", "DOLE", "DpSc", "DRCO", "DRPW", "DT1", "DT2", "DTYP",
		"ECG", "EDC", "EDDD", "EDF", "EDGI", "EDND", "EDNL", "EGFR", "EPR", "EXE", "ExeC", "Exer", "EYEE", "FAHS", "FBPC", "FBS", "FEET", "FEET",
		"FEET", "FEET", "FEET", "FEV1", "FGLC", "FICO", "FLUF", "FOBF", "FRAM", "FTE", "FTEx", "FTIn", "FTIs", "FTLS", "FTNe", "FTOt", "FTRe",
		"FTST", "FTUl", "G", "GAD7", "G6PD", "Hb", "HIP", "Hchl", "HDL", "HEAD", "HFCG", "HFCS", "HFMD", "HFMH", "HFMO", "HFMS", "HFMT", "HIVG",
		"HLA", "HpAI", "HpBA", "HPBC", "HPBP", "HpBS", "HpCA", "HPCG", "HPCP", "HR", "HRMS", "HSMC", "HSMG", "HT", "HTN", "HYPE", "HYPM", "IART",
		"iDia", "iEx", "iHyp", "INR", "INSL", "iOth", "iRef", "JVPE", "Kpl", "LcCt", "LDL", "LEFP", "LETH", "LHAD", "LMED", "LMP", "LUCR", "MACA",
		"MACC", "MAMF", "MCCE", "MCCN", "MCCO", "MCCS", "MedA", "MedG", "MedN", "MedR", "MI", "Napl", "NDIP", "NDIS", "NOSK", "NOVS", "NtrC", "NYHA",
		"OSWP", "OSWS", "OTCO", "OthC", "OUTR", "P", "PANE", "PAPF", "PEDE", "PEFR", "PHIN", "PIDU", "PPD", "PRRF", "PSPA", "PSSC", "PsyC", "PVD", "QDSH",
		"RABG", "REBG", "RESP", "RETI", "RPHR", "RPPT", "RVTN", "SCR", "SEXF", "SKST", "SMBG", "SmCC", "SMCD", "SMCP", "SMCS", "SMK", "SmkA", "SmkC",
		"SmkD", "SmkF", "SmkP", "SmkS", "SODI", "SOHF", "SPIR", "SSEX", "STRE", "StSc", "SUAB", "SUO2", "TCHD", "TCHL", "TEMP", "TG", "TOXP", "TRIG",
		"TSH", "TUG", "UAIP", "UALB", "UDUS", "UHTP", "URBH", "USSH", "VB12", "VDRL", "VLOA", "WAIS", "WHR", "WT", "ALB", "ALP", "BILC", "BILT", "BILU",
		"CK", "Clpl", "UA", "ALC", "ALPA", "BP", "DMSM", "POSK", "KEEL", "PSQS", "PTSD", "PHQS", "PHQ9", "DNFS", "NERF", "BPI", "BPII", "BPIS", "IBPL",
		"FAS", "COGA", "HPNP", "SBLT", "SDET", "SUNP", "BTFP", "FLOS", "DILY", "ABO", "AFP", "ALB", "ALP", "ANA", "APOB", "BILI", "BUN", "Clpl", "CK",
		"CRP", "CA", "C125", "C153", "C199", "CEA", "CHLM", "C3", "CMBS", "DIG", "DIL", "ENA", "ESR", "Fer", "FIT", "FOBT", "FT3", "FT4", "GBS", "GC",
		"GGT", "GCT", "GT1", "GT2", "HCO3", "HBEB", "HBEG", "HBVD", "HPYL", "LITH", "MG", "MCV", "PB19", "PHOS", "PLT", "PROT", "PSA", "iPTH", "RF",
		"Rh", "RUB", "TSAT", "URIC", "VZV", "WBC", "OPAE", "OPAB", "OPUS", "DMOE", "02SA", "SKD", "ORSK", "ECHK", "HCON", "BTFT", "HCVA", "HCGA",
		"CIRR", "FIBM", "FIBS", "APRI", "FIB4", "USND", "TXHX", "HCPC", "HAIM", "HBIM", "NEUT", "HIV", "HEPB", "AUDI", "OUDI", "SUDI", "STDI", "TUDI",
		"PSUP", "BARR", "DINC", "UMS", "FEV1BF", "FVCBF", "FEV1PCBF", "FEV1PRE", "FVCPRE", "FEV1PCPRE", "FEV1PCOFPREBF", "FVCRTBF", "FEV1FVCRTBF",
		"PEFRBF", "FEV1AFT", "FVCAFT", "FEV1PCAFT", "FEV1PCOFPREAFT", "FVCRTAFT", "FEV1FVCRTAFT", "PEFRAFT", "ANELV", "CNOLE", "WHE", "HFMR", "ASWAN",
		"HFSFT", "HFSDZ", "HFSSC", "HFSDE", "HFSDR", "HFSON", "HFSDP", "SPIRT", "COPDC", "RABG2", "EPR2", "ACOSY", "ACTSY", "ADYSY", "AWHSY",
		"CTDPW", "DYDPW", "WHDPW", "COPDC", "ACOSY", "ACTSY", "ADYSY", "AWHSY"
        ];

		var oscarDatabaseTags = [
		"time", "today", "appt_date", "current_form_id", "current_form_data_id", "current_user", "current_user_fname", "current_user_lname",
		"current_user_fname_lname", "current_user_ohip_no", "current_user_specialty_code", "current_user_specialty", "current_user_cpsid", "current_user_id",
		"current_user_address", "current_user_work_phone", "current_user_email", "current_user_fax", "current_user_team", "current_user_signature",
		"patient_name", "first_last_name", "patient_nameL", "patient_nameF", "patient_nameM", "patient_alias", "patient_title", "patient_id", "label",
		"residential_address", "address", "addressline", "address_street_number_and_name", "province", "city", "postal", "dob", "dobc", "dobc2", "dobc3",
		"dob_MONTH-dd-yyyy", "dob_year", "dob_month", "dob_day", "NameAddress", "hin", "hinc", "hinversion", "hc_type", "hc_renew_date", "chartno", "phone",
		"phone2", "cell", "phone_extension", "phone2_extension", "age", "age_in_months", "ageComplex", "ageComplex2", "sex", "She_He", "him_her", "his_her ",
		"sin", "date_joined", "partner_nameL", "partner_nameF", "partner_dob", "partner_dob2", "partner_hin ", "partner_phone ", "medical_history",
		"medical_history_ext", "other_medications_history", "other_medications_history_ext", "social_family_history", "social_family_history_ext",
		"ongoingconcerns", "ongoingconcerns_ext", "riskfactors", "riskfactors_ext", "reminders", "reminders_ext ", "risk_factors_json",
		"family_history_json", "dxregistry", "OHIPdxCode", "allergies_des", "allergies_des_no_archived", "recent_rx", "today_rx", "current_rx",
		"current_rx_lt", "druglist_generic", "druglist_trade", "druglist_line", "latest_echart_note", "todays_notes", "todays_notes_ext",
		"document_list", "onGTPAL", "onEDB ", "bcGTPAL ", "bcEDD", "doctor", "doctor_provider_no", "doctor_ohip_no", "doctor_specialty_code",
		"doctor_cpsid", "doctor_title", "provider_name", "provider_name_first_init", "doctor_work_phone", "doctor_signature", "appt_provider_name",
		"appt_provider_id", "appt_no", "appt_date", "appt_time", "appt_end_time", "appt_provider_ohip_no", "appt_provider_cpsid", "next_appt_provider_id",
		"next_appt_provider_name", "next_appt_date", "next_appt_time", "nextf_appt_date", "referral_name", "referral_Last_name", "referral_first_name",
		"referral_address", "dr_referral_name", "referral_no", "referral_phone", "referral_fax", "bc_referral_name ", "bc_referral_address", "bc_referral_phone",
		"bc_referral_fax ", "bc_referral_no", "clinic_name", "clinic_phone", "clinic_fax", "clinic_label", "clinic_addressLine", "clinic_addressLineFull",
		"clinic_address", "clinic_city", "clinic_province", "clinic_postal", "dtap_immunization_date", "flu_immunization_date"
		];
	/* Juno list
        var oscarDatabaseTags = [
            "today", "time", "appt_date", "appt_start_time", "appt_end_time", "appt_location",
            "next_appt_date", "next_appt_time", "nextf_appt_date", "next_appt_location", "current_form_id", "current_form_data_id",
            "current_user", "current_user_fname_lname", "current_user_ohip_no", "current_user_specialty", "current_user_specialty_code",
            "current_user_cpsid", "current_user_id", "current_user_signature", "current_logged_in_user", "current_logged_in_user_address",
            "current_logged_in_user_fax", "current_logged_in_user_work_phone", "current_logged_in_user_roles", "current_logged_in_user_type",
            "current_logged_in_user_id", "current_user_takno", "current_logged_in_user_takno", "patient_name", "first_last_name",
            "patient_nameL", "patient_nameF", "patient_alias", "patient_id", "label", "address", "addressline", "address_street_number_and_name",
            "province", "city", "postal", "dob", "dobc", "dobc2", "dobc3", "dob_year", "dob_month", "dob_day", "NameAddress",
            "hin", "hinc", "hinversion", "hc_type", "hc_renew_date", "chartno", "phone", "phone2", "cell", "phone_extension",
            "phone2_extension", "age", "age_in_months", "ageComplex", "ageComplex2", "sex", "sin", "licensed_producer_by_demographic",

            "licensed_producer2_by_demographic", "licensed_producer_address_name", "licensed_producer_address_name_list",
            "licensed_producer_full_address", "licensed_producer_full_address_list", "multisite_name_list", "multisite_fax_list",
            "multisite_phone_list", "multisite_full_address_list", "multisite_address_list", "multisite_city_list", "multisite_province_list",
            "multisite_postal_list", "medical_history", "other_medications_history", "social_family_history", "ongoingconcerns",

            "reminders", "risk_factors", "family_history", "risk_factors_json", "family_history_json", "dxregistry", "OHIPdxCode",
            "allergies_des", "allergies_des_no_archived", "recent_rx", "today_rx", "druglist_generic", "druglist_trade",
            "druglist_line", "latest_echart_note", "onGTPAL", "onEDB", "bcGTPAL", "bcEDD", "doctor", "doctor_provider_no",
            "doctor_ohip_no", "doctor_specialty_code", "doctor_cpsid", "appt_provider_cpsid", "appt_provider_specialty",
            "doctor_title", "provider_name", "provider_name_first_init", "provider_specialty", "doctor_work_phone", "doctor_fax",
            "doctor_signature", "appt_provider_name", "appt_provider_ohip_no", "appt_provider_id", "appt_no", "referral_name",

            "referral_address", "referral_phone", "referral_fax", "referral_no", "bc_referral_name", "bc_referral_address",
            "bc_referral_phone", "bc_referral_fax", "bc_referral_no", "clinic_name", "clinic_phone", "clinic_fax", "clinic_label",
            "clinic_addressLine", "clinic_addressLineFull", "clinic_address", "clinic_city", "clinic_province", "clinic_postal",
            "clinic_multi_phone", "clinic_multi_fax", "_eform_values_first", "_eform_values_last", "_eform_values_first_all_json",
            "_eform_values_last_all_json", "_eform_values_count", "_eform_values_countname", "_eform_values_count_ref",
            "_eform_values_countname_ref", "_eform_values_count_refname", "_eform_values_countname_refname", "_other_id",
            "dtap_immunization_date", "flu_immunization_date", "fobt_immunization_date", "mammogram_immunization_date",
            "pap_immunization_date", "cytology_no", "guardian_label", "guardian_label2", "email", "service_date", "practitioner",

            "ref_doctor", "fee_total", "payment_total", "refund_total", "balance_owing", "bill_item_number", "bill_item_description",
            "bill_item_service_code", "bill_item_qty", "bill_item_dx", "bill_item_amount", "urine_tox_test_json", "methadone_induction_assessment_json",
            "who_measurements", "family_doctor_name", "family_doctor_last_name", "family_doctor_address", "family_doctor_phone",

            "family_doctor_fax", "family_doctor_no", "bc_family_doctor_name", "bc_family_doctor_address", "bc_family_doctor_phone",
            "bc_family_doctor_fax", "bc_family_doctor_no", "roster_status"
        ];
	*/

        var $globalSelectedElement = null;
        var $mouseTargetElement = null;
        var currentMousePos = {
            x: -1,
            y: -1
        };
        var signaturePadLoaded = false;

        /** returns the input string with all non alpha-numeric characters removed */
        function stripSpecialChars(string) {
            return string.replace(/[^a-z0-9\s]/gi, '');
        }

        /** HTML-encodes a string for safe insertion into HTML content (e.g. title tags) */
        function escapeHtmlText(value) {
            var htmlEntities = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
            return String(value).replace(/[&<>"']/g, function(match) { return htmlEntities[match]; });
        }

        function destroy_gen_widgets($elementSelector) {
            $elementSelector.find(".gen-resizable").resizable("destroy").removeClass("gen-resizable").addClass("gen-resize-destroyed");
            $elementSelector.find(".gen-draggable").draggable("destroy").removeClass("gen-draggable").addClass("gen-draggable-destroyed");
            $elementSelector.find(".gen-droppable").droppable("destroy").removeClass("gen-droppable").addClass("gen-droppable-destroyed");
        }

        function undestroy_gen_widgets($elementSelector) {
            $elementSelector.find(".gen-draggable-destroyed").each(function() {
                makeDraggable($(this), false, ".gen-layer1, .gen-layer2, .gen-layer3");
                $(this).removeClass("gen-draggable-destroyed");
            });
            $elementSelector.find(".gen-resize-destroyed").each(function() {
                makeResizable($(this));
                $(this).removeClass("gen-resize-destroyed");
            });
            $elementSelector.find(".gen-droppable-destroyed").each(function() {
                makeDroppable($(this), "divHighlight", ".gen-layer2, .gen-layer3", true);
                $(this).removeClass("gen-droppable-destroyed");
            });
        }

        function getOscarDBTags() {
            var dbTagList = null;
            if (runStandaloneVersion) {
                dbTagList = oscarDatabaseTags.sort()
            } else {

                getMeasures(); //get measurementList

                $.ajax({
                    type: "GET",
                    url: OSCAR_EFORM_SEARCH_URL + 'databaseTags',
                    dataType: 'json',
                    async: false,
                    success: function(data) {
                        var status = data.status;
                        if (status === "SUCCESS") {
                            dbTagList = data.body;
                        }
                    },
                    error: function(jqXHR, textStatus, errorThrown) {
                        console.error("Failed to load database tags:", textStatus, errorThrown);
                    }
                });
            }
            return dbTagList ? dbTagList.sort() : [];
        }

        function addOscarImagePath(string) {
            if (!runStandaloneVersion) {
                //alert("native")
                // remove the oscar loading path
                // have to do escape for regex because of changing context path
                var regexFixed = OSCAR_DISPLAY_IMG_SRC.replace(/\//g, "\\/").replace(/\./g, "\\.").replace(/\?/g, "\\?");
                //string = string.replace(new RegExp(regexFixed, "g"), "${oscar_image_path}");
                string = string.replace(new RegExp(regexFixed, "g"), "$" + "{oscar_image_path}");
            } else {
                //alert("stand alone")
                string = string.replace(/(img.*?)src\s*=\s*(\"|\')(?!data:image\/png;base64,)/gi, "$1src=$2${"$"}{oscar_image_path}"); // ${"$"}{oscar_image_path} is a template marker replaced at runtime
            }
            return string;
        }

        function removeOscarImagePath(string) {
            if (!runStandaloneVersion) {
                var regexFixed = OSCAR_DISPLAY_IMG_SRC.replace(/\//g, "\\/").replace(/\./g, "\\.").replace(/\?/g, "\\?");
                return string.replace(/.(%7B|\{)oscar_image_path(%7D|\})/gi, OSCAR_DISPLAY_IMG_SRC);
            } else {
                return string.replace(/\$(%7B|\{)oscar_image_path(%7D|\})/gi, '');
            }
        }
        /** add or remove the current element from the DOM if it doesn't match the given state */
        function toggleElement($root, $element, state) {
            var attached = $.contains(document.body, $element.get(0)); // .get(0) will get the first instance

            if (!state && attached) {
                $element.detach();
            } else if (state && !attached) {
                $root.append($element);
            }
        }

        function addHiddenElements(include_fax) {
            var $patientGender = $("#PatientGender");
            var $inputForm = $("#inputForm");
            var count = $inputForm.find(GENDER_PRECHECK_CLASS_SELECTOR).length;
            if ($patientGender.get(0) == null) {
                $patientGender = $("<input>", {
                    type: "hidden",
                    id: "PatientGender",
                    name: "PatientGender"
                }).attr('oscarDB', 'sex');
            }
            toggleElement($inputForm, $patientGender, (count > 0));

            var stamps = $inputForm.find('.stamp').length;

			var $doctor = $("#DoctorName");
			if ($doctor.get(0) == null) {
				$doctor = $("<input>", {
					type: "hidden",
					id: "DoctorName",
					name: "DoctorName"
				}).attr('oscarDB', 'doctor');
			}
            toggleElement($inputForm, $doctor, (stamps > 0));

			var $current_user = $("#CurrentUserName");
			if ($current_user.get(0) == null) {
				$current_user = $("<input>", {
					type: "hidden",
					id: "CurrentUserName",
					name: "CurrentUserName"
				}).attr('oscarDB', 'current_user');
			}
            toggleElement($inputForm, $current_user, (stamps > 0));


			var $user_id = $("#user_id");
			if ($user_id.get(0) == null) {
				$user_id = $("<input>", {
					type: "hidden",
					id: "user_id",
					name: "user_id"
				}).attr('oscarDB', 'current_user_id');
			}
            toggleElement($inputForm, $user_id, (stamps > 0));

			var $user_ohip = $("#user_ohip_no");
			if ($user_ohip.get(0) == null) {
				$user_ohip = $("<input>", {
					type: "hidden",
					id: "user_ohip_no",
					name: "user_ohip_no"
				}).attr('oscarDB', 'current_user_ohip_no');
			}
            toggleElement($inputForm, $user_ohip, (stamps > 0));

			var $doctor_no = $("#doctor_no");
			if ($doctor_no.get(0) == null) {
				$doctor_no = $("<input>", {
					type: "hidden",
					id: "doctor_no",
					name: "doctor_no"
				}).attr('oscarDB', 'doctor_provider_no');
			}
            toggleElement($inputForm, $doctor_no, (stamps > 0));

			var setTickler = $('#setTickler').is(':checked');
            var $ticklerMD = $("#tickler_send_to");
            if ($ticklerMD.get(0) == null) {
                $ticklerMD = $("<input>", {
					type: "hidden",
					id: "tickler_send_to",
					name: "tickler_send_to"
				}).attr('oscarDB', 'doctor_provider_no ');
            }
            toggleElement($inputForm, $ticklerMD, setTickler);

			var setTickler = $('#setTickler').is(':checked');
            var $ticklerPt = $("#tickler_patient_id");
            if ($ticklerPt.get(0) == null) {
                $ticklerPt = $("<input>", {
					type: "hidden",
					id: "tickler_patient_id",
					name: "tickler_patient_id"
				}).attr('oscarDB', 'patient_id');
            }
            toggleElement($inputForm, $ticklerPt, setTickler);

			var $fax_no = $("#fax_no");
			if ($fax_no.get(0) == null) {
				$fax_no = $("<input>", {
					type: "hidden",
					id: "fax_no",
					name: "fax_no"
				}).attr('value', defaultFaxNo);
				console.log("Setting defaultFaxNo to "+defaultFaxNo);
			}
            toggleElement($inputForm, $fax_no, (defaultFaxNo.length >1) );

            var $faxControl = $("#faxControl");
            if ($faxControl.get(0) == null) {
                $faxControl = $("<div>", {
                    id: "faxControl"
                })
            }
            toggleElement($("#BottomButtons"), $faxControl, include_fax);
        }
        /** this function writes input values into the html, to ensure html/jQuery value synchronization */
        function write_values_to_html() {
            var $input_elem = $(".input_elements");
            // preserve checkbox checked state, since jquery does not modify html
            $input_elem.find("input[type=checkbox]").each(function(index) {
                if ($(this).is(':checked')) {
                    $(this).attr('checked', "checked");
                } else {
                    $(this).removeAttr("checked");
                }
            });
            // preserve text info, since jquery does not modify html
            $input_elem.find("input[type=text]").each(function(index) {
                if ($(this).val() && $(this).val().length > 0) {
                    $(this).attr("value", $(this).val());
                } else {
                    $(this).removeAttr("value");
                }
            });
            $input_elem.find("textarea").each(function(index) {
                if ($(this).val() && $(this).val().length > 0) {
                    $(this).text($(this).val());
                } else {
                    $(this).text("");
                }
            });
        }


        function findSelectedFunctions(string2parse){
            var selected = "\n\tfunction mydclick2(id){}\n\n";

            for (var i = 0; i < functionTitles.length; i++) {
                if (string2parse.includes(functionTitles[i])) {
                    selected += "\t" + functionCode[i] + "\n\n"
                }
            }

            return selected;
        }

        function generate_eform_source_html(escapeHtml, include_fax) {
            write_values_to_html();

            var $input_elements = $(".input_elements");
            var $eform_container = $("#eform_container");
            var htmlElements = document.getElementById('eform_container');

            var include_stamp = Boolean($eform_container.find(".stamp").length > 0);
            var include_signature = Boolean($eform_container.find(".signature_data").length > 0);
            var include_xbox = Boolean($eform_container.find(XBOX_INPUT_SELECTOR).length > 0);
            var include_link = Boolean(htmlElements.innerHTML.includes('LinkXBoxfunction'));
            var include_radio = Boolean($eform_container.find(".cradio").length > 0 || include_link);
			var include_gender = Boolean($eform_container.find(GENDER_PRECHECK_CLASS_SELECTOR).length > 0);
			var include_allno = Boolean($eform_container.find(".all-no-button2").length > 0);
			var include_date = Boolean($eform_container.find(".hasDatepicker").length > 0);
			var include_standAlone = $('#standAlone').is(':checked');
			var include_tickler = $('#setTickler').is(':checked');


            console.log("detecting " + $eform_container.find(".xBox").length + " xBox, " +  $eform_container.find(".cradio").length + " radio boxes, " + $eform_container.find(".stamp").length + " stamps and " + $eform_container.find(".signature_data").length + " canvas signatures");

            if ($globalSelectedElement) $globalSelectedElement.removeClass("selectedHighlight");
            $globalSelectedElement = null;
            destroy_gen_widgets($input_elements);
            addHiddenElements(include_fax);
            // detatch elements so they are not added to generated code
            var detached = [];
            $(".gen-snapGuide,.inputOverride,.signature_image").each(function() {
                var $override = $(this);
                var $parent = $override.parent();
                toggleElement($parent, $override, false);
                detached.push([$parent, $override]);
            });
            var loadFunctions = "";

            var source = "<!DOCTYPE html><html><head>";
            source += "\<META http-equiv='Content-Type' content='text/html; charset=UTF-8'\>";
            source += "<title>" + escapeHtmlText(eformName) + "</title>";

            // ------- the eforms generated are currently dependent on jQuery ------
            source += "\<script src='../library/jquery/jquery-3.7.1.min.js'\>\<\/script\>"; // present in CARLOS
            source += "\<script src='../library/jquery/jquery-3.6.4.min.js'\>\<\/script\>"; // present in OSCAR 19 and OPEN OSP
            source += "\<script src='$\{oscar_javascript_path\}jquery/jquery-2.2.4.min.js'\>\<\/script\>"; // only present in JUNO
            source += "\<script\>window.jQuery || document.write(\"\\x3cscript src='../js/jquery-1.12.3.js'\\x3e\\x3c\\/script\\x3e\");\<\/script\>"; // present in WELL and others as
            source += "\<script\>window.jQuery || document.write(\"\\x3cscript src='https://code.jquery.com/jquery-3.6.4.min.js' integrity='sha256-oP6HI9z1XaZNBrJURtCoUT5SUnxFr8s3BzRl+cbzUq8=' crossorigin='anonymous'\\x3e\\x3c\\/script\\x3e\");\<\/script\>"; // if all else fails refer to a CND

            if (include_date) {
                source += "\<link rel='stylesheet' href='../share/calendar/calendar.css'>";
                source += "\<script src='../share/calendar/calendar.js'><\/script>";
                source += "\<script src='../share/calendar/lang/calendar-en.js'><\/script>";
                source += "\<script src='../share/calendar/calendar-setup.js'><\/script>";
                loadFunctions += " initCalendar();";
            }

            if (include_fax) {
                source += "\<script src='$\{oscar_javascript_path\}eforms/printControl.js'\>\<\/script\>";
                source += "\<script src='$\{oscar_javascript_path\}eforms/faxControl.js'\>\<\/script\>";
                if ($('#defaultFaxNo').val().length > 6) { loadFunctions += " setFaxNo();"; }
            }
            if (include_signature) {
                source += "\<script src='$\{oscar_image_path\}signature_pad.min.js'\>\<\/script\>";
                // source += "\<script src='$\{oscar_javascript_path\}eforms/signature_pad.min.js'\>\<\/script\>"; // OSCAR 19 and Juno
            }
            if (setSideBar == "on") {
                loadFunctions += " openNav();";
            }

            source += "<style>";

            var baseStyle = document.getElementById('eform_style'); //base style
            var shapeStyles = document.getElementById('eform_style_shapes');
			var signatureStyles = document.getElementById('eform_style_signature');
            var stampStyles = document.getElementById('stamp_style');
			var sidenavStyles = document.getElementById('sidenav_style');
			var radioStyles = document.getElementById('radio_style');
			var allnoStyles = document.getElementById('allno_style');
			var xboxStyles = document.getElementById('xbox_style');

            var script = document.getElementById('eform_script'); //base javascript
            var standAlone_script = document.getElementById('standAlone_script');
			var date_script = document.getElementById('date_script');
			var xbox_script = document.getElementById('xbox_script');
			var gender_script = document.getElementById('gender_script');
			var allno_script = document.getElementById('allno_script');
			var radio_script = document.getElementById('radio_script');
            var signature_script = document.getElementById('signature_script');
            var stamp_script = document.getElementById('stamp_script');
            var faxno_script = document.getElementById('faxno_script');
            var link_script = document.getElementById('link_script');

			// style ORDER is important!
            source += baseStyle.innerHTML;
            if ($eform_container.find(".circle,.square-rounded,.square").length > 0) {
                source += shapeStyles.innerHTML;
            }
            if (include_allno) {
                source += allnoStyles.innerHTML;
            }
            if (setSideBar == "on") {
                source += sidenavStyles.innerHTML;
            }
            if (include_xbox) {
                source += xboxStyles.innerHTML;
            }
            if (include_radio) {
                source += radioStyles.innerHTML;
            }
            if (include_stamp) {
                source += stampStyles.innerHTML;
            }
            if ($eform_container.find(".signaturePad").length > 0) {
                source += signatureStyles.innerHTML;
            }
            source += "</style>"
            source += "<script>" + script.innerHTML;  //base javascript
			if (include_standAlone) {
                source += standAlone_script.innerHTML;
				loadFunctions += " reImg(); disableButtons();";
            }
			if (include_date) {
                source += date_script.innerHTML;
				loadFunctions += " initCalendar();";
            }
			if (include_xbox) {
                source += xbox_script.innerHTML;
				loadFunctions += " initXBoxes();";
            }
			if (include_gender) {
                source += gender_script.innerHTML;
                loadFunctions += " initPrecheckedCheckboxes();";
            }
            if (include_radio) {
			    source += radio_script.innerHTML;
            }
            if (include_link) {
			    source += link_script.innerHTML;
            }
			if (include_allno) {
                source += allno_script.innerHTML;
                loadFunctions += " initAllNo();";
            }
            if (include_signature) {
                source += signature_script.innerHTML;
            }
            if ($('#defaultFaxNo').val().length > 6) {
                source += faxno_script.innerHTML;
            }
            if (include_stamp) {
                source += stamp_script.innerHTML;
                loadFunctions += " signForm()";
            }
            source += findSelectedFunctions(htmlElements.innerHTML) + "\<\/script></"+"head><body onload='focusAll();" + loadFunctions + "'>";
            if (setSideBar == "on") {
                source += "<iframe src='${"$"}{oscar_image_path}SideBarTemplate.html' id='mySidenavGen2' class='sidenav DoNotPrint' style='margin-top:-60px;margin-left:-100px;height:600px'></iframe>"; // ${"$"}{oscar_image_path} is a template marker replaced at runtime
            }

            source += "<div id='eform_container' " + "style='max-width: " + eFormPageWidth + "px'>";
            source += htmlElements.innerHTML;

            source += "</div></body></html>";
            source = addOscarImagePath(source);
            source = source.replace(/>\s*</g, ">\n<");
            //now we need to escape the html special chars
            if (escapeHtml) {
                var htmlChars = { '&': '&amp;', '<': '&lt;', '>': '&gt;' };
                source = source.replace(/[&<>]/g, function(match) { return htmlChars[match]; });
                //now we add <pre> tags to preserve whitespace
                source = "<pre>" + source + "</pre>";
            }
            undestroy_gen_widgets($input_elements);
            dragAndDropEnable(false);

            for (var i = 0; i < detached.length; i++) {
                toggleElement(detached[i][0], detached[i][1], true);
            }
            return source;
        }

        function showSource(include_fax) {
            var source = generate_eform_source_html(true, include_fax);
            // Use a Blob URL to load the content into a new window, avoiding document.write
            // with DOM-sourced HTML (CodeQL: DOM text reinterpreted as HTML).
            var blob = new Blob([source], {type: 'text/html;charset=UTF-8'});
            var url = URL.createObjectURL(blob);
            // Revoke the blob URL after BLOB_URL_REVOKE_TIMEOUT_MS as a safety fallback (handles popup-blocked case too)
            var revokeTimer = setTimeout(function() { URL.revokeObjectURL(url); }, BLOB_URL_REVOKE_TIMEOUT_MS);
            var sourceWindow = window.open(url, 'Source of page', 'height=800,width=800,scrollbars=1,resizable=1');
            if (sourceWindow) {
                sourceWindow.opener = null;
                // Revoke as soon as the page has loaded to free memory earlier
                sourceWindow.addEventListener('load', function() {
                    clearTimeout(revokeTimer);
                    URL.revokeObjectURL(url);
                });
            } else {
                // Popup was blocked — revoke immediately
                clearTimeout(revokeTimer);
                URL.revokeObjectURL(url);
            }
            if (sourceWindow && window.focus) sourceWindow.focus();
        }

        function download(text, name, type) {
            var a = document.createElement("a");
            var file = new Blob([text], {
                type: type
            });
            a.href = URL.createObjectURL(file);
            a.download = name;
            // create mouse event for initiating the download
            var event = document.createEvent("MouseEvents");
            event.initMouseEvent(
                "click", true, false, window, 0, 0, 0, 0, 0, false, false, false, false, 0, null
            );
            a.dispatchEvent(event);
        }

        function downloadSource(include_fax) {
            var name = stripSpecialChars(eformName).replace(/\s/g, "_") + '.html';
            return download(generate_eform_source_html(false, include_fax), name, 'text/html');
        }

        /** Save the eform html as a new eform */
        function saveToOscarEforms(include_fax) {
            var eformCode = generate_eform_source_html(false, include_fax);
            var url = OSCAR_EFORM_ENTITY_URL + ((eFormFid > 0) ? eFormFid + "/" : "") + "json";
            var type = (eFormFid > 0) ? "PUT" : "POST";

            $.ajax({
                type: type,
                url: url,
                contentType: "application/json; charset=utf-8",
                dataType: 'json',
                async: false,
                data: JSON.stringify({
                    "id": eFormFid,
                    "formName": eformName,
                    "formHtml": eformCode
                }),
                success: function(data) {
                    console.info(data);
                    var status = data.status;
                    if (status === "SUCCESS") {
                        custom_alert(EFORM_I18N.alertSaveSuccessful);
                        setEformId(data.body.id);
                    } else {
                        custom_alert(data.error.message);
                    }
                },
                error: function(jqXHR, textStatus, errorThrown) {
                    console.error("Failed to save eForm:", textStatus, errorThrown);
                    custom_alert(EFORM_I18N.alertSaveFailure);
                }
            });
        }

        /** make the given element draggable */
        function makeDraggable($element, cloneable, stackClasses) {
            $element.draggable({
                appendTo: "body",
                revert: "invalid",
                revertDuration: 500,
                stack: stackClasses,
                scroll: false,
                snap: ".gen-snapLine:visible",
                snapMode: "inner",
                snapTolerance: 10 // ".gen-snapLine:visible" 10
           });
            if (cloneable) {
                $element.draggable("option", "helper", "clone");
                $element.addClass("gen-cloneable");
            }
            $element.addClass("gen-draggable");
        }
        /** make the given element resizable */
        function makeResizable($element) {
            $element.resizable({
                aspectRatio: ($element.find(':checkbox').length > 0),
                containment: "#inputForm"
            });
            $element.resizable("disable");
            $element.addClass("gen-resizable");
        }
        /** make the given element accept draggable elements */
        function makeDroppable($element, hoverClasses, acceptClasses, greedy) {
            $element.droppable({
                accept: acceptClasses,
                hoverClass: hoverClasses,
                greedy: greedy,
                drop: function(event, ui) {
                    dropOnForm(ui, $(this));
                }
            });
            $element.addClass("gen-droppable");
        }

        function makeSignatureCanvas($element) {

            var $canvasFrame = $element.children(".canvas_frame");
            var $clearBtn = $element.children(".clearBtn");
            var canvas = $canvasFrame.children("canvas").get(0);
            var $data = $canvasFrame.children(".signature_data");
            var src = $data.val();
            var $img = null;
            if (src && src.length > 0 && isValidImageSrc(src)) {
                $img = $("<img>", {
                    src: src,
                    alt: "signature",
                    class: "signature_image"
                });
                $img.appendTo($canvasFrame);
            } else if (src && src.length > 0) {
                $canvasFrame.append($("<div>").text("Signature could not be displayed").css({color: 'red', fontSize: '0.8em', padding: '4px'}));
            }

            if (signaturePadLoaded) {
                if ($img) $img.hide();
                console.info("loading editable signature pads");
                var updateSlaveSignature = function(src_canvas, dest_canvas) {
                    // write to the destination with image scaling
                    var dest_context = dest_canvas.getContext("2d");
                    dest_context.clearRect(0, 0, dest_canvas.width, dest_canvas.height);
                    dest_context.drawImage(src_canvas, 0, 0, dest_canvas.width, dest_canvas.height);
                };
                var setCanvasSize = function() {
                    canvas.width = $element.width();
                    canvas.height = $element.height();
                    $element.trigger("signatureChange");
                };
                // initialize the signature pad
                var signPad = new SignaturePad(canvas, {
                    minWidth: 2,
                    maxWidth: 4,
                    onEnd: function() {
                        $element.trigger("signatureChange");
                    }
                });
                // load the image data to the canvas ofter initialization
                if (src != null && src != "") {
                    signPad.fromDataURL(src);
                } else {
                    setCanvasSize();
                }
                // so that the signature image resizes correctly in generator
                $element.on("resize", setCanvasSize);

                // define a custom update trigger action. this allows the eform to store the signature.
                $element.on("signatureChange", function() {
                    var dataUrl = signPad.toDataURL();
                    $data.val(dataUrl);
                    if (!$img) {
                        $img = $("<img>", { src: dataUrl, alt: "signature", class: "signature_image" });
                        $img.appendTo($canvasFrame).hide();
                    } else {
                        $img.prop('src', dataUrl);
                    }
                    if ($element.attr('slaveSigPad')) {
                        var $slavePad = $("#" + $element.attr('slaveSigPad')); // get slave pad by id
                        updateSlaveSignature(canvas, $slavePad.find("canvas").get(0));
                        $slavePad.trigger("signatureChange"); // be careful of infinite loops
                    }
                    return false;
                });
                // init the clear button
                $clearBtn.on('click', function() {
                    signPad.clear();
                    $element.trigger("signatureChange");
                    return false;
                });
            }
            // not loaded so not using the canvas, show signature as an image instead.
            else {
                if ($img) $img.show();
            }
        }

        function createBasicDraggableDiv(widgetId, width, height, customClasses) {
            return $("<div>", {
                id: widgetId,
                class: "gen-widget " + customClasses,
                width: width + "px",
                height: height + "px"
            });
        }

        function createInputOverrideDiv() {
            return $("<div>", {
                class: "inputOverride"
            });
        }

        function addDraggableInputType($parent, widgetId, type, width, height, customClasses) {
            var $widget = createBasicDraggableDiv(widgetId, width, height, customClasses + " gen-layer3");

            if (type === "textarea") {  // TODO is resizing still a bit buggy
                $widget.append($("<textarea>", {
                    css: {
                        resize: 'none'
                    },
                    class: 'gen_input' //2020-May-14
                }));
            } else {
                $widget.append($("<input>", {
                    type: type,
                    class: 'gen_input'
                }));
            }
            $widget.append(createInputOverrideDiv());
            $parent.append($widget);

            makeDraggable($widget, true, ".gen-layer1, .gen-layer2, .gen-layer3");
            return $widget;
        }

        function addDraggableShape($parent, widgetId, width, height, customClasses) {
            var $widget = createBasicDraggableDiv(widgetId, width, height, customClasses + " gen-layer2");
            $parent.append($widget);
            makeDraggable($widget, true, ".gen-layer1, .gen-layer2");
            makeDroppable($widget, "divHighlight", ".gen-layer2, .gen-layer3", true);
            return $widget;
        }

        function addDraggableLabel($parent, widgetId, text, customClasses) {
            var $widget = $("<label>", {
                id: widgetId,
                class: "gen-widget gen-layer3 ui-widget-content " + customClasses,
                text: text,
                value: text
            });
            $parent.append($widget);
            makeDraggable($widget, true, ".gen-layer1, .gen-layer2, .gen-layer3");

            return $widget;
        }

        function addDraggableImage($parent, widgetId, width, height, src, customClasses) {  // TODO something missing in the implimentation
            var $widget = createBasicDraggableDiv(widgetId, width, height, customClasses + " gen-layer3");
            var imgClass = "";
            if (isValidImageSrc(src)) {
                $widget.append($("<img>", {
                    src: src,
                    alt: "image",
                    width: "100%",
                    height: "100%"
                }));
            } else {
                $widget.append($("<span>").text("Image could not be loaded (invalid source)").css({color: 'red', fontSize: '0.8em', padding: '4px'}));
            }

            $parent.append($widget);
            makeDraggable($widget, true, ".gen-layer1, .gen-layer2");
			console.log("created widget "+widgetId);
            return $widget;
        }

        function addDraggableStamp($parent, widgetId, width, height, src, customClasses) {  // May 1, 2024  TO DO buggy on resize/drag
            var $widget = createBasicDraggableDiv(widgetId, width, height, customClasses + " gen-layer3");
            var imgClass = "";
            $widget.append($("<img>", {
                src: src,
                alt: "stamp",
                width: "100%",
                height: "100%",
                class: "stamp",
                onclick: "toggleMe(this);"
            }));
            $parent.append($widget);
            makeDraggable($widget, true, ".gen-layer1, .gen-layer2");
			console.log("created widget "+widgetId);
            return $widget;
        }

        function addDraggableSignaturePad($parent, widgetId, width, height, customClasses) {
            var $widget = createBasicDraggableDiv(widgetId, width, height, customClasses + " gen-layer3");

            var $canvas = $("<canvas>");
            var $canvasData = $("<input>", {
                type: "hidden",
                class: "signature_data"
            });
            var $clearBtn = $("<button>", {
                type: "button",
                text: "clear",
                class: "clearBtn DoNotPrint"
            });
            var $flex = $("<div>", {
                class: "canvas_frame"
            });
            $flex.append($canvas).append($canvasData);
            $widget.append($flex).append($clearBtn);
            $flex.append(createInputOverrideDiv());
            $parent.append($widget);
            makeDraggable($widget, true, ".gen-layer1, .gen-layer2");
            return $widget;
        }

        /** generate a unique id for new input elements. */
        function getUniqueId(baseId) {
            var i = 1;
            // if you max this out your eForm is too big anyways
            while (i < 99999) {
                //var returnId = baseId + i;
                var returnId = baseId + myunique + i;  //2021-Jan-10

                if (!document.getElementById(returnId)) {
                    return returnId;
                }
                i++;
            }
            console.error("unique ID generation failed");
            return undefined;
        }
        /** create a copy of the draggable element at the given position
         * the clone will not be re-cloneable, and will be given a generated name/id */
        function cloneDraggableAt($newParent, position, $toClone) {

            var $newDraggable = $toClone.clone();
            var id = getUniqueId(baseWidgetName);
            $newDraggable.attr("id", id);
            //$newDraggable.attr("name", id);
            $newDraggable.removeClass("gen-cloneable");
            $newDraggable.appendTo($newParent);
            $newDraggable.css(position);
            // clone is made draggable with all options except the helper.
            $newDraggable.draggable($toClone.draggable("option"));
            $newDraggable.draggable("option", "helper", false);

            // inputs must all have id's and names to work in oscar
            $newDraggable.children(":input").each(function() {
                var id = getUniqueId(baseInputName);
                $(this).attr({
                    id: id,
                    //title: "NewBox",  //2020-May-14
                    //oscarDB: "e$last#"+id, //2023-May-04
                    name: id
                });
                mydraginput(this)
            });
            // init signature pads when cloning
            if ($newDraggable.hasClass("signaturePad")) {
                // inputs must all have id's and names to work in oscar
                $newDraggable.find(".signature_data").each(function() {
                    var id = getUniqueId(baseSignatureDataName);
                    $(this).attr({
                        id: id,
                        name: id
                    });
                });
                makeSignatureCanvas($newDraggable);
            }
            setNoborderStyle($newDraggable.find(XBOX_INPUT_SELECTOR), xboxBordersVisibleState);
            setNoborderStyle($newDraggable.find(TEXT_INPUT_SELECTOR), textBordersVisibleState);
            return $newDraggable;
        }
        /** recursively clone widget elements and attach them to the parent selector */
        function cloneWidgetAt($newParent, position, $toClone) {
            var isResizable = $toClone.data('uiResizable');
            var $childWidgets = $toClone.children(".gen-widget");
            // remove resizable to prevent errors
            if (isResizable) {
                $toClone.resizable("destroy").removeClass("gen-resizable");
            }
            // clone the element
            var $clone = cloneDraggableAt($newParent, position, $toClone);
            //remove duplicated children elements (they won't be draggable etc)
            $clone.children(".gen-widget").remove();
            // clone all child widgets and attach them to the new clone
            $childWidgets.each(function() {
                var xPos = Math.round($(this).position().left); //2024-May-01
                var yPos = Math.round($(this).position().top); //2024-May-01
                var pos = {
                    top: yPos,
                    left: xPos,
                    position: "absolute"
                };
                cloneWidgetAt($clone, pos, $(this));
            });
            if (isResizable) {
                makeResizable($toClone); //re-enable the resizable
            }
            makeResizable($clone); //make clone resizable
            if ($toClone.data('uiDroppable')) {
                $clone.droppable($toClone.droppable("option"));
            }
            return $clone;
        }

        /** drop a draggable object onto a droppable object, and clone/update the draggable parent */
        function dropOnForm(ui, $new_parent) {
            var $draggable = $(ui.draggable);
            var $old_parent = $draggable.parent();

            if ($draggable.hasClass("gen-cloneable")) {
                var xPos = Math.round(ui.helper.offset().left - $new_parent.offset().left); //2024-May-01
                var yPos = Math.round(ui.helper.offset().top - $new_parent.offset().top); //2024-May-01
                var pos = {
                    top: yPos,
                    left: xPos,
                    position: "absolute"
                };
                cloneWidgetAt($new_parent, pos, $draggable);
            } else if (!($new_parent.is($old_parent))) {
                var xPos = Math.round($old_parent.offset().left + ui.helper.position().left - $new_parent.offset().left); //2024-May-01
                var yPos = Math.round($old_parent.offset().top + ui.helper.position().top - $new_parent.offset().top); //2024-May-01
                var pos = {
                    top: yPos,
                    left: xPos,
                    position: "absolute"
                };
                $draggable.appendTo($new_parent).css(pos);

            }
        }
        /** set up a simple fixed size frame div */
        function createTrashFrame() {
            return $("<div>", {
                class: "gen-trash_frame"
            });
        }
        /** set up a simple frame div */
        function createStitchFrame() {
            return $("<div>", {
                class: "gen-stitch_frame"
            });
        }
        /** set up a drop down menu with the items from the options Array */
        function addSelectMenu($rootElement, menuId, label, optionsArr, valuesArr) {
            var $select = $("<select>", {
                id: menuId
            });
            for (var i = 0; i < optionsArr.length; i++) {
                $option = $("<option>").text(optionsArr[i]);
                if (valuesArr) {
                    $option.attr('value', valuesArr[i])
                }
                $select.append($option);
            }
            $rootElement.append($("<label>", {
                for: menuId,
                text: label
            }));
            $rootElement.append($select);
            $select.selectmenu().selectmenu("menuWidget").addClass("gen-selectOverflow");
            return $select;
        }

        /** set up a spinner html with the given id, label, and value
         * call the spinner() initializer after this method */
        function createSpinnerElem(spinnerId, label, value) {
            return $("<p>")
                .append($("<label>", {
                    for: spinnerId,
                    text: label
                }))
                .append($("<input>", {
                    id: spinnerId,
                    name: spinnerId,
                    value: value
                }));
        }
        /** set up tabs html with the given id and tab names.
         * call the tabs() initializer after this method */
        function addTabs($rootElement, tabBaseId, tabNames) {

            var $root = $("<div>", {
                id: tabBaseId
            });
            var $ul = $("<ul>").appendTo($root);
            var tabs = [];

            for (var i = 0; i < tabNames.length; i++) {
                $ul.append($("<li>")
                    .append($("<a>", {
                        href: "#" + tabBaseId + "-" + i,
                        text: tabNames[i]
                    })));
            }
            for (i = 0; i < tabNames.length; i++) {
                var newTab = $("<div>", {
                    id: tabBaseId + "-" + i
                }).appendTo($root);
                tabs.push(newTab);
            }
            $rootElement.append($root);
            $root.tabs();
            return tabs;
        }

        /** creates a labeled fieldset */
        function createFieldset(id, legend) {
            var $fieldset = $("<fieldset>", {
                id: id
            });
            if (legend != null) {
                $fieldset.append($("<legend>", {
                    text: legend
                }));
            }
            return $fieldset;
        }
        /** set up radio control group, allowing for multiple
         * options with only one selected at a time */
        function addRadioGroup($rootElement, baseId, legendName, optionNames) {
            var $fieldset = createFieldset(baseId, legendName);
            var opts = [];

            for (var i = 0; i < optionNames.length; i++) {
                var id = "#" + baseId + "-" + i;
                var $label = $("<label>", {
                    for: id,
                    text: optionNames[i]
                });
                var $input = $("<input>", {
                    id: id,
                    type: 'radio',
                    name: baseId + '-radio',
                    value: i
                });
                $fieldset.append($label).append($input);
                opts.push($input);
            }
            $rootElement.append($fieldset);
            $(opts).checkboxradio({
                icon: false
            });
            $fieldset.controlgroup();

            return $fieldset;
        }
        /** create a confirmation dialogue box element
         *  call jquery dialog constructor on the returned div element selector */
        function createConfirmationDialogueElements(title, message) {
            return $("<div>", {
                title: title,
                class: "gen-alert"
            }).append($("<p>", {
                text: message
            }));
        }


		function cleanOscarTags(data) {
		//  noborderPrint" title="age" oscardb="age" placeholder="
		//  noborderPrint" title="age" oscardb="" "age"="" placeholder="
			const regex = /oscarDB=([a-z_]+)/gi;
			const replaceStr = 'oscarDB="$1"';
			data = data.replace(regex, replaceStr);
			return data;
		}
		function loadPages($div) {
			//iterate through possible pages of Forengi code
			var pageNo = 1;
			var toReturn = "";
			var aPage;
			while (typeof($div.find('#page'+pageNo).html()) != "undefined") {
				aPage = $div.find('#page'+pageNo).html();
				aPage = "<div id='page_"+pageNo+"' class='page_container ui-droppable' style='width: 800px; height: 1000px;'>"+aPage+"</div>";
				toReturn += aPage;
				pageNo++
			}
			return toReturn;
		}

        function loadEformData(data) {

            // remove oscar image paths in incoming data
            data = removeOscarImagePath(data);
			//use regex to sanitize foreign imported oscarDb tags
			data = cleanOscarTags(data);

			// import the default fax number if present
			const regex = /name="fax_no"\svalue="([\d-]*)"/
			if (regex.test(data)) {
				var matches = data.match(regex);
				defaultFaxNo = matches[1];
				$("#defaultFaxNo").val(defaultFaxNo);
			}
			const regex2 = /tickler_send_to/
			if (regex2.test(data)) {
				//$("#setTickler").prop('checked', true);
				$("#setTickler").click();
			}

            // import the eform name
            eformName = $($.parseHTML(data)).filter('title').text();
            $("#eformNameInput").val(eformName);

            // convert checkboxes to Xbox
            const re = /class=""\stype="checkbox"/g
            if (re.test(data)) {
                data = data.replace(re,'class="Xbox xBox" type="text"');
            }
            // convert radiocheck to Xbox check
            const re2 = /"\stype="checkbox"/g
            if (re2.test(data)) {
                data = data.replace(re2,' Radio" type="text"');
            }

            var $div = $(data);

			var $importedInputForm = $div.find("#inputForm");
			//May-01-2024 Peter Hutten-Czapski
            var ferengi = ($importedInputForm.length === 0);
            if (ferengi) {
                if ( typeof($div.find("[id^='BGImage']").html() ) == "undefined" ){
                    custom_alert("We are currently unable to convert eform "+eformName+" as it lacks any recognisable background images.");
                    return;
                }
                if ( typeof($div.find("#page1").html() ) == "undefined"){
                    custom_alert("We are currently unable to convert eform "+eformName+" as it lacks a recognisable page structure.");
                    return;
                }
            }

            var $inputForm = $("#inputForm");
            if (ferengi) {
                $inputForm.html(loadPages($div));
            } else {
                // Move child nodes directly to avoid the DOM-text-to-HTML roundtrip (.html() read → .html() write).
                // jQuery .append() moves (not copies) nodes, automatically detaching them from $importedInputForm.
                $inputForm.empty().append($importedInputForm.contents());
            }

            // as checkboxes are now Xboxes the only checkboxes left are in the form controls
            $inputForm.find('[checked]').each(function(){
                $(this).val('X');
                $(this).removeAttr('checked');
            });

			//May-01-2024 Peter Hutten-Czapski
            if (ferengi) {

				// import the default fax number if present
				const regex = /"otherFaxInput"\)\.value="([\d-]*)"/
				if (regex.test(data)) {
					var matches = data.match(regex);
					defaultFaxNo = matches[1];
				}

                $("[id^='BGImage']").addClass("gen-layer1");
				$("[id^='BGImage']").attr( "alt", "background" );
				$("[id^='BGImage']").addClass( "gen-layer1" );

				$("#BottomButtons").remove();
				var pageNo = 1;
				var scaleX = 1;
				var scaleY = 1;
				while ($("#page_"+pageNo).length > 0) {
					var imgWidth = $("#BGImage"+pageNo).css('width');
					if ( imgWidth && imgWidth.length > 3) {
						imgWidth = imgWidth.substring(0, imgWidth.length - 2)*1;
						scaleX = (eFormPageWidthPortrait/imgWidth) * 0.972;// PHC Fudge
						scaleY = scaleX *.995; //PHC Fudge
						console.log("Page "+pageNo+" has scaleX of "+scaleX+" and scaleY of "+scaleY);
					}
					var imgHeight = $("#BGImage"+pageNo).css('height');
					if ( imgHeight && imgHeight.length > 3) {
						imgHeight = imgHeight.substring(0, imgHeight.length - 2)*1;
						//scaleY = eFormPageHeightPortrait/imgHeight;
					}

					ii = $("#BGImage"+pageNo).detach();
					$("#page_"+pageNo).wrapInner("<div class='input_elements'>");
					$("#page_"+pageNo).prepend(ii);
					pageNo++
				}
				$("[id^='BGImage']").prop( "style", "width: auto; height: 100%; z-index: 0;" );
				hh = $(':input[type=hidden]').detach();
				pageNo = pageNo -1;
				bb = '<div id="BottomButtons" class="DoNotPrint">\n';
				bb += '\t<label for="subject">Subject:</label>\n\t<input id="subject" name="subject" style="width: 220px;" type="text">\n';
				bb += '\t<input id="SubmitButton" name="SubmitButton" onclick="onEformSubmit();" type="button" value="Submit">\n';
				bb += '\t<input id="PrintButton" name="PrintButton" onclick="onEformPrint()" type="button" value="Print">\n';
				bb += '\t<input id="PrintSubmitButton" name="PrintSubmitButton" onclick="onEformPrintSubmit();" type="button" value="Print &amp; Submit">\n';
				bb += '</div>\n';
				$("#page_"+pageNo).parent().append(bb).append(hh); // get the hidden inputs out from the input_elements !important
				var i=1;
				$inputForm.find('input, textarea, #Stamp').not(':input[type=button], :input[type=submit], :input[type=reset], :input[type=hidden], #subject').each(function() {
					$(this).attr("placeholder", $(this).attr("oscarDB"));
					var wrapStyle = $(this).attr("style");
					var wrapClass = "";
					$(this).addClass("gen_input");
					if ($(this).hasClass("Xbox")) {
						$(this).addClass("xBox");
						$(this).removeClass("Xbox");
						wrapClass = "gen-xBox ";
						wrapStyle += " width: 14px; height: 14px;"
					}
					if ($(this).hasClass("Radio")) {
						var names = $(this).attr('class').split(/\s+/);
						j=0
						while (j < names.length) {
							var name = names[j];
							if (name.indexOf('only-one-') === 0) {
								found = name.split('only-one-');
								if (found[1] != "radio"  && found[1] != "") {
									$(this).addClass("only-one-radio#"+found[1]);
									$(this).removeClass(name);
								}
							}
							++j;
						}
						$(this).addClass("cradio");
						$(this).removeClass("Radio");
						$(this).attr("autocomplete", "off");
						$(this).attr("onclick", "myupdate(this)");
						var wrap_top = $(this).css("top");
						var wrap_left = $(this).css("left");
						wrapClass = "only-one-radio ";
						wrapStyle = " width: 14px; height: 14px; position: absolute; z-index: 10; top: "+wrap_top+"; left: "+wrap_left+";"

					}
					if ($(this).hasClass("noborder")) {
						$(this).addClass("TextBox noborderPrint");
						$(this).removeClass("noborder");
					}
					if ($(this).is('#Stamp')){
                        if (runStandaloneVersion){
						    $(this).attr("src", "BNK.png");
                        } else {
						    $(this).attr("src", "../eform/displayImage.do?imagefile=BNK.png");
                        }
						$(this).attr("onclick", "toggleMe(this);");
						$(this).attr("alt", "stamp");
						var wrap_width= $(this).attr("width");
						$(this).removeAttr("width");
						$(this).removeAttr("id");
						var wrap_height= $(this).attr("height");
						$(this).removeAttr("height");
						$(this).removeClass();
						$(this).addClass("stamp");
						$(this).attr("style", "width: 100%; height: 100%;");
						var wrap_top = $(this).parent().css("top");
						var wrap_left = $(this).parent().css("left");
						$(this).unwrap();
						wrapClass = "signatureStamp ";
						wrapStyle = "width: "+wrap_width+"px; height: "+wrap_height+"px; position: absolute; z-index: 10; top: "+wrap_top+"; left: "+wrap_left+";"
						$(this).wrap('<div id="gen_widgetIdF'+i+'" class="gen-widget signatureStamp gen-layer3 gen-resize-destroyed gen-draggable-destroyed">');
						$("#signatureDisplay").remove();
					} else {
						$(this).removeAttr("style");
						$(this).wrap('<div id="gen_widgetIdF'+i+'" class="gen-widget '+wrapClass+'gen-layer3 gen-resize-destroyed gen-draggable-destroyed" title="Click to drag. DoubleClick to activate" ondblclick="mydclick2(this)">');
					}

					$("#gen_widgetIdF"+i).attr("style",wrapStyle);

					// rescale widget locations
					widgetLeft = $("#gen_widgetIdF"+i).css("left").substring(0, $("#gen_widgetIdF"+i).css("left").length - 2);
					$("#gen_widgetIdF"+i).css("left", Math.round(widgetLeft*scaleX)+"px");
					widgetTop = $("#gen_widgetIdF"+i).css("top").substring(0, $("#gen_widgetIdF"+i).css("top").length - 2);
					$("#gen_widgetIdF"+i).css("top", Math.round(widgetTop*scaleY)+"px");

					i++;

				});
				console.log(i + " input elements transformed from a "+pageNo+" page Ferengi eform with scaling of "+scaleX+", "+scaleY);
            }

			//console.log($inputForm.html());
			$('#defaultFaxNo').val(defaultFaxNo);
            // inputForm content was already set above (via DOM node movement for standard forms,
            // or via loadPages for Ferengi forms). The original redundant re-read and re-injection
            // has been removed to prevent an empty-string overwrite after DOM node movement.

            // TODO -- combine with generic makeDraggables and addNewPage
            var $input_elements = $(".input_elements");
			// however check if this is a foreign conversion

            var $pages = $(".page_container");
            $pages.droppable({
                accept: ".gen-layer2, .gen-layer3",
                drop: function(event, ui) {
                    dropOnForm(ui, $(this).find(".input_elements"));
                }
            });
            $("#pagesControlGroup").find(".page_control_item").remove();
            $pages.each(function() { //add the grid to loaded pages
                $("#pagesControlGroup").append(createPageControlDiv($(this)));
                addSnapGuidesTo($(this));
            });
            $pages.find(XBOX_INPUT_SELECTOR).parent().append(createInputOverrideDiv());
            $pages.find(CHEK_INPUT_SELECTOR).parent().append(createInputOverrideDiv());
            $pages.find(TEXT_INPUT_SELECTOR).parent().append(createInputOverrideDiv());
            $pages.find(".signature_data").parent().append(createInputOverrideDiv());
            $pages.find(".signaturePad").each(function() {
                makeSignatureCanvas($(this));
            });

            setNoborderStyle($pages.find(XBOX_INPUT_SELECTOR), xboxBordersVisibleState);
            setNoborderStyle($pages.find(TEXT_INPUT_SELECTOR), textBordersVisibleState);

            undestroy_gen_widgets($input_elements);
            //dragAndDropEnable(false);  //lets have it true May-01-2024

            var pageW = eFormPageWidth;
            var pageH = eFormPageHeight;
            $pages.each(function() {
                pageW = $(this).width();
                pageH = $(this).height();
            });
            $("#gen-setPageWidth").val(pageW);
            $("#gen-setPageHeight").val(pageH);
            //console.info(pageW, pageH);
            var index = 2;
            if (pageW === eFormPageWidthPortrait && pageH === eFormPageHeightPortrait) {
                index = 0;
            } else if (pageW === eFormPageWidthLandscape && pageH === eFormPageHeightLandscape) {
                index = 1;
            }
            setPageOrientation(index);
            return true;
        }

        function init_form_load($element) {

            if (!runStandaloneVersion) {
                $.ajax({
                    // populate the eform list from the server
                    type: "GET",
                    url: OSCAR_EFORM_SEARCH_URL,
                    dataType: 'json',
                    async: true,
                    success: function(data) {

                        var status = data.status;
                        if (status === "SUCCESS") {
                            var options = [""];
                            var values = [0];
                            var selectedId = 0;
                            for (var i = 0; i < data.body.length; i++) {
                                options.push(data.body[i].formName);
                                values.push(data.body[i].id);
                            }

                            var $root = $("<div>", {
                                class: "page_control_item"
                            }).appendTo($element);
                            var $eFormSelect = addSelectMenu($root, "eFormSelect", "Select EForm", options, values);
                            $eFormSelect.selectmenu();
                            var $loadButton = $("<button>", {
                                text: "Load Selected EForm"
                            }).button().click(function(event) {
                                if (selectedId > 0) {
                                    // load the selected eform from the html by id
                                    $.ajax({
                                        type: "GET",
                                        url: OSCAR_EFORM_ENTITY_URL + selectedId,
                                        dataType: 'json',
                                        async: true,
                                        success: function(data) {
                                            var status = data.status;
                                            if (status === "SUCCESS") {
                                                // setup the generator with the existing eform data
                                                loadEformData(data.body.formHtml);
                                                setEformId(data.body.id);
                                                console.info("EForm Loaded from Server");
                                            } else {
                                                custom_alert(data.error.message);
                                            }
                                        },
                                        error: function(jqXHR, textStatus, errorThrown) {
                                            console.error("Failed to load eForm:", textStatus, errorThrown);
                                            custom_alert(EFORM_I18N.alertSaveFailure);
                                        }
                                    });
                                }
                                event.preventDefault();
                            }).appendTo($root);

                            $eFormSelect.on("selectmenuchange", (function(event, data) {
                                selectedId = data.item.value;
                            }));
                        }
                    },
                    error: function(jqXHR, textStatus, errorThrown) {
                        console.error("Failed to load eForm list:", textStatus, errorThrown);
                    }
                });
            } else {
                $element.append($("<input>", {
                        type: "file",
                        accept: ".html"
                    })
                    .change(function() {
                        if (this.files && this.files[0]) {
                            var reader = new FileReader();
                            reader.onload = function(e) {
                                $.get(e.target.result, function(data) {
                                    loadEformData(data);
                                    console.info("EForm Loaded from File.");
                                })
                            };
                            reader.readAsDataURL(this.files[0]);
                        }
                    })
                ).append($("<div>").append(
                    $("<label>").text("Note: any custom scripts or styles in the loaded form will not be preserved")
                ));
            }
        }

        function addBackgroundImage($parentElement, srcString) {
            var id = getUniqueId(baseBackImageName);
            var $img = $("<img>", {
				alt: "background",
                id: id,
                class: "gen-layer1"
            }).prependTo($parentElement);
            if (srcString) {
                $img.attr('src', srcString);
            }
            return $img;
        }

        function createPageControlDiv($pageDiv) {

            var $img = $pageDiv.children("img");
            var $root = $("<div>", {
                class: "page_control_item"
            });

            var $fileSelector;

            if (runStandaloneVersion) {
                $fileSelector = $("<input>", {
                    type: "file",
                    accept: ".png"
                }).change(function() {
                    if (this.files && this.files[0]) {
                        var reader = new FileReader();
                        var $fileInput = $(this);
                        reader.onload = function(e) {
                            var src = $fileInput.val().replace(/C:\\fakepath\\/i, '');
                            if (!isValidImageSrc(src)) return;
                            var encodedSrc = encodeURIComponent(src);
                            if ($img == null || $img.length <= 0) {
                                $img = addBackgroundImage($pageDiv, encodedSrc);
                            } else {
                                $img.attr('src', encodedSrc);
                            }
                            $img.on('load', function() {
                                var css;
                                var ratio = $(this).width() / $(this).height();
                                var pratio = (eFormPageWidth / eFormPageHeight);
                                if (ratio < pratio) css = {
                                    width: 'auto',
                                    height: '100%'
                                };
                                else css = {
                                    width: '100%',
                                    height: 'auto'
                                };
                                $(this).css(css);
                            });
                        };
                        reader.readAsDataURL(this.files[0]);
                    }
                });
            }

            var $clearButton = $("<button>", {
                text: "Clear"
            }).button().click(function(event) {
                if ($img != null) {
                    $img.remove();
                    $img = null;
                }
                event.preventDefault();
            });

            var $removePageButton = $("<button>", {
                text: "Remove Page"
            }).button({
                icon: "ui-icon-circle-minus",
                showLabel: false
            }).click(function(event) {
                var $confirm = createConfirmationDialogueElements(CONFIRM_PAGE_REMOVE_TITLE, CONFIRM_PAGE_REMOVE_MESSAGE);
                $confirm.dialog({
                    resizable: false,
                    height: "auto",
                    width: 400,
                    modal: true,
                    buttons: {
                        "Delete": function() {
                            $pageDiv.remove();
                            $root.remove();
                            $(this).dialog("close");
                        },
                        "Cancel": function() {
                            $(this).dialog("close");
                        }
                    },
                    close: function() {
                        $(this).remove();
                    }
                });
                event.preventDefault();
            });

            $root.append($removePageButton).append($clearButton)
            if (!runStandaloneVersion) {

                var options = [""];
                for (var i = 0; i < eFormImageList.length; i++) {
                    options.push(eFormImageList[i]);
                }

                $fileSelector = addSelectMenu($root, "imageSelect", "Select Background Image", options);
                $fileSelector.selectmenu();

                $fileSelector.on("selectmenuchange", (function(event, data) {
                    var selectedFile = $fileSelector.val();
                    if (selectedFile.length < 1) {
                        return;
                    }
                    var src = OSCAR_DISPLAY_IMG_SRC + encodeURIComponent(selectedFile);
                    if ($img == null || $img.length <= 0) {
                        $img = addBackgroundImage($pageDiv, src);
                    } else {
                        $img.attr('src', src);
                    }
                    $img.on('load', function() {
                        var css;
                        var ratio = $(this).width() / $(this).height();
                        var pratio = (eFormPageWidth / eFormPageHeight);
                        if (ratio < pratio) css = {
                            width: 'auto',
                            height: '100%'
                        };
                        else css = {
                            width: '100%',
                            height: 'auto'
                        };
                        $(this).css(css);
                    });

                }));
            } else {
                $root.append($fileSelector);
            }
            return $root;
        }

        function setPageDimensions(newWidth, newHeight) {
            eFormPageWidth = newWidth;
            eFormPageHeight = newHeight;
            $(".page_container").css({
                width: eFormPageWidth,
                height: eFormPageHeight
            });
            $("#eform_container").css({
                'max-width': eFormPageWidth + 'px'
            });

            var $wrapper = $("#eform_view_wrapper");
            var maxWidth = eFormPageWidth + eFormViewPadding;
            //console.info(eFormPageWidth, eFormViewPadding, maxWidth);
            $wrapper.resizable("option", "maxWidth", maxWidth);
            if ($wrapper.width() > maxWidth) {
                $wrapper.width(maxWidth);
            }
        }

        function setEformId(id) {
            var asInt = parseInt(id);
            var $saveBtn = $("#saveToOscarButton")
            if (Number.isInteger(asInt) && asInt > 0) {
                eFormFid = asInt;
                $saveBtn.button('option', 'label', OSCAR_SAVE_MESSAGE_UPDATE);
            } else {
                eFormFid = 0;
                $saveBtn.button('option', 'label', OSCAR_SAVE_MESSAGE_NEW);
            }
        }

        function setPageOrientation(newIndex) {

            var $custWidth = $("#gen-setPageWidth");
            var $custHeight = $("#gen-setPageHeight");
            var $defaultShow = $("#gen-orientationLabel");

            switch (newIndex) {
                case 2: {
                    $custWidth.parent().show();
                    $custHeight.parent().show();
                    $defaultShow.hide();
                    setPageDimensions(parseInt($custWidth.val(), 10), parseInt($custHeight.val()), 10);
                    orientationIndex = 2;
                    break;
                }
                case 1: {
                    $custWidth.parent().hide();
                    $custHeight.parent().hide();
                    $defaultShow.show();
                    setPageDimensions(eFormPageWidthLandscape, eFormPageHeightLandscape);
                    orientationIndex = 1;
                    break;
                }
                default: {
                    $custWidth.parent().hide();
                    $custHeight.parent().hide();
                    $defaultShow.show();
                    setPageDimensions(eFormPageWidthPortrait, eFormPageHeightPortrait);
                    orientationIndex = 0;
                    break;
                }
            }
            $("#gen-orientation").find("input").filter("[value='" + orientationIndex + "']").prop("checked", true).button("refresh");
            $("#gen-orientation").find("input").checkboxradio("refresh");
        }

        function init_setup_controls($element) {

            if (!runStandaloneVersion) {
                $.ajax({
                    type: "GET",
                    url: OSCAR_EFORM_SEARCH_URL + 'images',
                    dataType: 'json',
                    async: false,
                    success: function(data) {
                        var status = data.status;
                        if (status === "SUCCESS") {
                            eFormImageList = data.body;
                        }
                    },
                    error: function(jqXHR, textStatus, errorThrown) {
                        console.error("Failed to load eForm images:", textStatus, errorThrown);
                    }
                });
            }


            var $pagesControlgroup = createFieldset("pagesControlGroup", "Pages");
            var $addPageButtonControlGroup = createFieldset("addPagesControlGroup", null);
            var $addPageButton = $("<button>", {
                text: "Add Page"
            }).button({
                icon: "ui-icon-circle-plus"
                //showLabel: false
            }).click(function(event) {
                var $newPage = createNewPage();
                $pagesControlgroup.append(createPageControlDiv($newPage));
                event.preventDefault();
            });

            var $custDimensionX = $("<div>").append($("<label>", {
                text: "Width:",
                for: "gen-setPageWidth"
            })).append($("<input>", {
                id: "gen-setPageWidth",
                type: "text",
                value: eFormPageWidth
            }));
            var $custDimensionY = $("<div>").append($("<label>", {
                text: "Height:",
                for: "gen-setPageHeight"
            })).append($("<input>", {
                id: "gen-setPageHeight",
                type: "text",
                value: eFormPageHeight
            }));
            var $dimensionInputs = $("<div>", {
                class: "flexH"
            }).append($custDimensionX).append($custDimensionY);

            var labels = ["Portrait", "Landscape", "Custom"];
            var $orinetationRadioGroup = addRadioGroup($element, "gen-orientation", "Orientation", labels);
            $orinetationRadioGroup.on("change", function(e) {
                var value = parseInt($(e.target).val());
                setPageOrientation(value);
            });
            $element.append($dimensionInputs);
            // set inital value index
            setPageOrientation(orientationIndex);

            $element.append($addPageButtonControlGroup.append($addPageButton));
            $addPageButtonControlGroup.append($pagesControlgroup);
            $("<div>").append($("<label>", {
                id: "gen-orientationLabel",
                text: "Page Dimensions: " + eFormPageWidthPortrait + "x" + eFormPageHeightPortrait +
                    " (Landscape: " + eFormPageWidthLandscape + "x" + eFormPageHeightLandscape + ")"
            })).appendTo($dimensionInputs);

            return $pagesControlgroup;
        }

        /** tab 0 setup */

        function initCheckboxTemplateTab($tab) {

            var $options_menu0 = $("<div>", {
                class: "gen-control-menu"
            });
            var $dragFrame0 = createStitchFrame();
            var $checkBoxTemplate = addDraggableInputType($dragFrame0, "checkBoxTemplate", "checkbox", checkboxSize, checkboxSize, ""); //2020-May-02
            //var $x = addDraggableInputType($dragFrame0, "checkBoxTemplate", "hidden", checkboxSize, checkboxSize, ""); //Spacer
            var $rBoxTemplate = addDraggableInputType($dragFrame0, "rBoxTemplate", "text", checkboxSize, checkboxSize, "only-one-radio");
            //var $x = addDraggableInputType($dragFrame0, "checkBoxTemplate", "hidden", checkboxSize, checkboxSize, "");  //Spacer
            var $xBoxTemplate = addDraggableInputType($dragFrame0, "xBoxTemplate", "text", checkboxSize, checkboxSize, "gen-xBox");
            //var $x = addDraggableInputType($dragFrame0, "checkBoxTemplate", "hidden", checkboxSize, checkboxSize, ""); //Spacer
            var $bBoxTemplate = addDraggableInputType($dragFrame0, "bBoxTemplate", "button", checkboxSize, checkboxSize, "all-no-button1");

            $xBoxTemplate.attr("title", "click") // 2020-May-17


            //************************Check box Title Snippet***************************2020-May-14
            var customTitle = $('<input/>')
                .attr('type', "input")
                .attr('name', "boxTitle")
                .attr('id', "boxTitle")
                //.attr('value', "boxTitle")
                .change(function() {
                    newTitle = $('#boxTitle').val()

                    if (newTitle == "") {
                    }
                    $xBoxTemplateInput.attr("title", newTitle);
                    $rBoxTemplateInput.attr("title", newTitle);
                });

            var linkTitle = $('<input/>')
                .attr('type', "input")
                .attr('name', "linkTitle")
                .attr('id', "linkTitle")
                .change(function() {
                    alert("this changed");
                });

            customTitle.css("font-size", "12");
            linkTitle.css("font-size", "12");
            $xBoxTemplate.after(customTitle);
			$xBoxTemplate.before('&nbsp;');
            var $label = $("<label>").text('  Custom Title: ');
            $label.css("fontSize", 12);
            $label.attr('id', "theLabel");
            customTitle.before($label);
            customTitle.after(linkTitle);
            var $label2 = $("<label>").text(' LinkTo:');
            $label2.css("fontSize", 12);
            $label2.attr('id', "theLabel2");
            linkTitle.before($label2);


            //************************ Add classes and functions to boxes ************************
            $xBoxTemplate.attr('ondblclick', 'mydclick2(this)');
            $xBoxTemplate.attr('title', 'xBox - Click to drag. DoubleClick to activate'); //2020-May-12
            //$textBoxTemplate.attr('title', 'TextBox - Click to drag. DoubleClick to activate');  //2020-May-12
            //$checkBoxTemplate.attr('title', 'Pre-check by Gender');
            $checkBoxTemplate.attr('title', 'Not in use - use pre-check box'); //2020-Sep-23
            $checkBoxTemplate.hide() //2020-Sep-23
            $rBoxTemplate.attr('ondblclick', 'mydclick(this)');
            //$rBoxTemplate.attr('onmouseover', 'mymso(this)' );
            $rBoxTemplate.attr('title', 'Radio Box - Click to drag. DoubleClick to activate');
            $bBoxTemplate.attr('ondblclick', 'myextrastep(this)');
            //$rBoxTemplate.attr('onkeypress','GetKeyCode(event.keyCode)')
            $bBoxTemplate.removeClass("gen-widget")
            //$bBoxTemplate.removeClass("cradio")
            $bBoxTemplate.addClass("DoNotPrint")
            $bBoxTemplate.attr('title', 'Click on WHITE FRAME to drag. DoubleClick to activate');
            //$bBoxTemplate.attr('id', 'gen_buttonWidget' )

            //***************************************************************
            var $checkBoxTemplateInput = $checkBoxTemplate.find(":input");
            var $xBoxTemplateInput = $xBoxTemplate.find(":input");
            var $rBoxTemplateInput = $rBoxTemplate.find(":input");
            var $bBoxTemplateInput = $bBoxTemplate.find(":input");
            $xBoxTemplateInput.attr("title", "click") // 2020-May-17
            $checkBoxTemplateInput.addClass("GenderBox") //2020-May-14
            $xBoxTemplateInput.addClass("xBox").attr('autocomplete', 'off');
            $rBoxTemplateInput.addClass("only-one-radio").attr('autocomplete', 'off');
            //$rBoxTemplateInput.addClass("cradio").attr('autocomplete', 'off');
            $rBoxTemplateInput.addClass("cradio").attr('onclick', 'myupdate(this)'); //newwork

            $bBoxTemplateInput.addClass("all-no-button2").attr('value', 'All No');
			$bBoxTemplateInput.attr('onclick', 'allno();');
            $bBoxTemplateInput.removeClass("gen_input")
			$bBoxTemplate.hide();
            //$bBoxTemplateInput.removeClass("cradio")

            var $checkboxSizeSpinner = createSpinnerElem("checkboxSizeSpinner", "Template Size:", checkboxSize);
            var changeTemplateSize = function(event, ui) {
                checkboxSize = this.value;
                $checkBoxTemplate.css({
                    width: checkboxSize,
                    height: checkboxSize
                });
                $xBoxTemplate.css({
                    width: checkboxSize,
                    height: checkboxSize
                });
                $xBoxTemplate.find(XBOX_INPUT_SELECTOR).css({
                    'font-size': (checkboxSize - 1) + 'px'
                });
                $rBoxTemplate.css({
                    width: checkboxSize,
                    height: checkboxSize
                });
                $rBoxTemplate.find(XBOX_INPUT_SELECTOR).css({
                    'font-size': (checkboxSize - 1) + 'px'
                });
                $bBoxTemplate.css({
                    width: checkboxSize,
                    height: checkboxSize
                });
                $bBoxTemplate.find(XBOX_INPUT_SELECTOR).css({
                    'font-size': (checkboxSize - 1) + 'px'
                });
            };
            $checkboxSizeSpinner.find(":input").spinner({
                min: checkboxSizeRange[0],
                max: checkboxSizeRange[1],
                stop: changeTemplateSize,
                spin: changeTemplateSize
            });
            var $checkByGenderChkbox = $("<input>", {
                id: "gen-precheckByGender",
                type: "checkbox",
                checked: false
            });
            var $allNoChkbox = $("<input>", {
                id: "gen-all-no",
                type: "checkbox",
                checked: false,
				change: function(event, ui) {
					$bBoxTemplate.toggle();
					// PHC TODO
				}
            });
            var $preCheckCheckbox = $("<input>", {
                id: "gen-precheckCheckboxId",
                type: "checkbox",
                checked: false,
                change: function(event, ui) {
                    var $xbox = $xBoxTemplate.find(":input");
                    //var $chkbox = $checkBoxTemplate.find(":input");
					var $rbox = $rBoxTemplate.find(":input");
                    $xbox.val($xbox.val() === 'X' ? '' : 'X');
					$rbox.val($rbox.val() === 'X' ? '' : 'X');
                    //$chkbox.prop('checked', !($chkbox.is(':checked')));
                    if ($(this).is(':checked') && $checkByGenderChkbox.is(':checked')) {
                        $checkByGenderChkbox.prop('checked', false);
                        $checkByGenderChkbox.change();
                    }
                }
            });
            $tab.append($options_menu0);
            $tab.append($dragFrame0);
            $options_menu0.append($checkboxSizeSpinner);
            $options_menu0.append($("<div>").append($("<label>", {
                text: "Pre-Check:",
                for: "gen-precheckCheckboxId"
            })).append($preCheckCheckbox));
            $options_menu0.append($("<div>").append($("<label>", {
                for: "gen-all-no",
                text: "All No function"
            })).append($allNoChkbox));
            $options_menu0.append($("<div>").append($("<label>", {
                for: "gen-precheckByGender",
                text: "Pre-Check by Gender"
            })).append($checkByGenderChkbox));
            var $div01 = $("<div>").appendTo($options_menu0);
            var $gender_select = addSelectMenu($div01, "genderSelect0", "Check When", ["M", "F", "T", "O", "U"]);
            $gender_select.selectmenu("disable");
            var removeGenderPrecheckClasses = function(index, curclass) {
                return (curclass.match(/(^|\s)gender_precheck_\S+/g) || []).join(' ');
            };
            $gender_select.on("selectmenuchange", (function(event, data) {
                $checkBoxTemplateInput.removeClass(removeGenderPrecheckClasses).addClass("gender_precheck_" + $gender_select.val());
				$rBoxTemplateInput.removeClass(removeGenderPrecheckClasses).addClass("gender_precheck_" + $gender_select.val());
                $xBoxTemplateInput.removeClass(removeGenderPrecheckClasses).addClass("gender_precheck_" + $gender_select.val());
            }));
            $checkByGenderChkbox.on('change', function() {
                if ($(this).is(':checked')) {
                    $gender_select.selectmenu("enable");
                    $checkBoxTemplateInput.removeClass(removeGenderPrecheckClasses).addClass("gender_precheck_" + $gender_select.val());
					$rBoxTemplateInput.removeClass(removeGenderPrecheckClasses).addClass("gender_precheck_" + $gender_select.val());
                    $xBoxTemplateInput.removeClass(removeGenderPrecheckClasses).addClass("gender_precheck_" + $gender_select.val());
                    if ($preCheckCheckbox.is(':checked')) {
                        $preCheckCheckbox.prop('checked', false);
                        $preCheckCheckbox.change();
                    }
                } else {
                    $gender_select.selectmenu("disable");
                    $checkBoxTemplateInput.removeClass(removeGenderPrecheckClasses);
					$rBoxTemplateInput.removeClass(removeGenderPrecheckClasses);
                    $xBoxTemplateInput.removeClass(removeGenderPrecheckClasses);
                }
            });


            //************************************add function to checkbox****************************** //2020-May-02
            var $oscarCheckbox2 = $('<input>', {
                id: 'toggleCheckbox2',
                type: 'checkbox'
            });
            var $oscarCheckbox2label = $('<label>', {
                text: 'Add function',
                for: 'toggleCheckbox2'
            });

            var $oscarCheckbox22 = $('<input>', { // 2020-May-19
                id: 'toggleCheckbox22',
                type: 'checkbox'
            });
            var $oscarCheckbox22label = $('<label>', {
                text: 'Add SideBar',
                for: 'toggleCheckbox22'
            });

            $preCheckCheckbox.after($oscarCheckbox2)
            $('#toggleCheckbox2').before($oscarCheckbox2label)

            $preCheckCheckbox.after($oscarCheckbox22) // 2020-May-19  2020-Sep-23
            $('#toggleCheckbox22').before($oscarCheckbox22label)

            $oscarCheckbox22.on('change', function(event, ui) {
                if ($oscarCheckbox22.is(':checked')) {
                    setSideBar = "on"
                } else {
                    setSideBar = ""
                }

            })


            $( function() {
                var dialog, form,

                event_name = $( "#event_name" ),
                parameters = $( "#parameters" ),
                allFields = $( [] ).add( event_name ).add( parameters ),
                tips = $( ".validateTips" );

                function attachFunction() {
		            allFields.removeClass( "ui-state-error" );
		            etrigger=event_name.val();
		            parameter=parameters.val();
                    if ($('#typeFlag').text()=='Checkbox'){
                        $('#xBoxTemplate').find(':input').attr(etrigger, parameter);
                        $('#rBoxTemplate').find(':input').attr(etrigger, parameter);
                    } else {
                        $('#textBoxTemplate').find(':input').attr(etrigger, parameter);
                        $('#textAreaTemplate').find(':input').attr(etrigger, parameter);
                    }
                    dialog.dialog( "close" );
		            return
                }

                dialog = $( "#dialog-form" ).dialog({
                  autoOpen: false,
                  height: "auto",
                  width: 600,
                  modal: true,
                  buttons: {
                    "Attach Function": attachFunction,
                    "Close": function() {
                      if ($('#typeFlag').text()=='Checkbox'){
                        $('#xBoxTemplate').find(':input').css('background-color', 'aquamarine');
                        $('#rBoxTemplate').find(':input').attr('onclick', 'myupdate(this)');
                        $('#rBoxTemplate').find(':input').css('background-color', 'pink');
                      } else {
                        $('#textBoxTemplate').find(':input').removeAttr('style');
                        $('#textAreaTemplate').find(':input').attr('style', 'resize: none');
                      }
                      dialog.dialog( "close" );
                    }
                  },
                  close: function() {
                    form[ 0 ].reset();
                    allFields.removeClass( "ui-state-error" );
                  }
                });

                form = dialog.find( "form" ).on( "submit", function( event ) {
                  event.preventDefault();
                  attachFunction();
                });

                $( "#create-fcn" ).button().on( "click", function() {
                  dialog.dialog( "open" );
                });
              } );

            $oscarCheckbox2.on('change', function(event, ui) {
                var x; // placeholder for the string for the event attribute
                var y; //placeholder for the string for the parameterized function
                if ($oscarCheckbox2.is(':checked')) {
                    listfc(); //create select list of functions
                    var e = document.getElementById('mySelect');
                    $(e).css('background-color', 'yellow')
                    $(e).focus();
                    $(e).attr('size', e.length + 1);
                    e.onclick = function() {
                        var strUser = e.options[e.selectedIndex].text;
                        var strContent = e.options[e.selectedIndex].value;

                        //****For CheckBox******************************
                        $( "#daFcn" ).text(strContent);
                        $( "#typeFlag" ).text("Checkbox");
                        switch (strUser) {
                            case "LinkTextBoxfunction":
                                $('#event_name').val('onblur').change();
                                $( "#parameters" ).val(strUser + '(this)');
                                break;

                            case "LinkXBoxfunction": // if{} below
                                $('#event_name').val('onclick').change();
                                $( "#parameters" ).val('myupdate(this); LinkXBoxfunction(this)');
                                break;

                            case "fixSin":
                                //      /(\d)(?=(\d\d\d)+(?!\d))/g, '$1-')       for SIN number
                                //      /(\d{3})(\d{3})(\d{4})/, '($1) $2-$3'   for Phone number
                                var myRe = "/(\\d)(?=(\\d\\d\\d)+(?!\\d))/g,'$1-'"; //2020-Mar-23
                                zz = myRe.split(",")
                                $('#event_name').val('onblur').change();
                                $( "#parameters" ).val(strUser + '(this,' + zz[0] + ',' + zz[1] + ')');
                                break;

                            default:
                                $('#event_name').val('onclick').change();
                                $( "#parameters" ).val(strUser + '(this)');

                        }
                        $( "#dialog-form" ).dialog( "open" );

                        //**********************************
                        $xBoxTemplate.find(':input').css('background-color', 'lime'); // 2020-May-17
                        $rBoxTemplate.find(':input').css('background-color', 'red');


                        if (strUser == "LinkXBoxfunction") {
                            $rBoxTemplate.find(':input').attr('onclick', 'myupdate(this);LinkXBoxfunction(this)');
                            $rBoxTemplate.find(':input').attr('onmouseover', 'LinkXBoxfunctionMouseover(this)');
                            $rBoxTemplate.find(':input').attr('onmouseout', 'LinkXBoxfunctionMouseout(this)');
                            $xBoxTemplate.find(':input').attr('onclick', 'myupdate(this);LinkXBoxfunction(this)');
                            $xBoxTemplate.find(':input').attr('onmouseover', 'LinkXBoxfunctionMouseover(this)');
                            $xBoxTemplate.find(':input').attr('onmouseout', 'LinkXBoxfunctionMouseout(this)');
                        }

                        $('#mySelect').remove() //close select list
                        closeNavGen() //close side bar
                    }

                } else {
                    $('#mySelect').remove(); //close select list
                    closeNavGen(); //close side bar
                    $xBoxTemplate.find(':input').css('background-color', 'aquamarine');
                    $rBoxTemplate.find(':input').attr('onclick', 'myupdate(this)'); //2020-May-27
                    $rBoxTemplate.find(':input').css('background-color', 'pink');
                    if (x != null) {
                        $xBoxTemplate.find(':input').removeAttr(x);
                        $rBoxTemplate.find(':input').removeAttr(x);
                    }
                }

            })

        }


        /** tab 1 setup */

        function initTextboxTemplateTab($tab) {
            var $options_menu1 = $("<div>", {
                class: "gen-control-menu"
            });

            var $dragFrame11 = createStitchFrame().attr('id', 'dragframe11');
            var $dragFrame12 = createStitchFrame().attr('id', 'dragframe12');
            var $textBoxTemplate = addDraggableInputType($dragFrame11, "textBoxTemplate", "text", textBoxWidth, textBoxHeight, "");
            var $textAreaTemplate = addDraggableInputType($dragFrame12, "textAreaTemplate", "textarea", textBoxWidth, textBoxHeight, "");
            var $textSizeSpinnerW = createSpinnerElem("textSizeSpinnerW", "Template Width:", textBoxWidth);
            var onTextSizeSpinnerW = function(event, ui) {
                textBoxWidth = this.value;
                $textBoxTemplate.css({
                    width: textBoxWidth
                });
                $textAreaTemplate.css({
                    width: textBoxWidth
                });
            };
            var onTextSizeSpinnerH = function(event, ui) {
                textBoxHeight = this.value;
                $textBoxTemplate.css({
                    height: textBoxHeight
                });
                $textAreaTemplate.css({
                    height: textBoxHeight
                });
            };
            $textSizeSpinnerW.find(":input").spinner({
                min: textBoxSizeRange[0],
                max: textBoxSizeRange[1],
                stop: onTextSizeSpinnerW,
                spin: onTextSizeSpinnerW
            });
            var $textSizeSpinnerH = createSpinnerElem("textSizeSpinnerH", "Template Height:", textBoxHeight);
            $textSizeSpinnerH.find(":input").spinner({
                min: textBoxSizeRange[0],
                max: textBoxSizeRange[1],
                stop: onTextSizeSpinnerH,
                spin: onTextSizeSpinnerH
            });

            $tab.append($options_menu1);
            $tab.append($("<label>", {
                for: $dragFrame11.attr('id'),
                text: "Single Line Input"
            })).append($dragFrame11);
            $tab.append($("<label>", {
                for: $dragFrame12.attr('id'),
                text: "Multi Line Input"
            })).append($dragFrame12);

            $options_menu1.append($textSizeSpinnerW)
                .append($textSizeSpinnerH)
                .append($("<div>").append($("<label>", {
                    text: "Prefilled Text:",
                    for: "gen-textBoxDefaultTextId"
                })).append($("<input>", {
                    id: "gen-textBoxDefaultTextId",
                    type: "text",
                    value: "",
                    change: function(event, ui) {
                        $textBoxTemplate.find(":input").val($(this).val());
                        $textBoxTemplate.find(":input").attr("title", $(this).val());
                        $textAreaTemplate.find("textarea").text($(this).val());
                    }
                }))).append($("<div>").append($("<label>", {
                    text: "Placeholder:",
                    for: "gen-textBoxPlaceholderId"
                })).append($("<input>", {
                    id: "gen-textBoxPlaceholderId",
                    type: "text",
                    value: "",
                    change: function(event, ui) {
                        $textBoxTemplate.find(":input").attr('placeholder', ($(this).val()));
                        $textAreaTemplate.find("textarea").attr('placeholder', ($(this).val()));
                    }
                })));

            /* set up oscar database tag selection */
            var $oscarDbCheckbox = $("<input>", {
                id: "toggleGOscarDbCheckbox",
                type: "checkbox",
                name: "radio1"
            });

            //************************************
            var $oscarDbCheckbox2 = $('<input>', {
                id: 'toggleGOscarDbCheckbox2',
                type: 'checkbox'
            });
            var $oscarDbCheckbox3 = $('<input>', {
                id: 'toggleGOscarDbCheckbox3',
                type: 'checkbox'
            });
            var $oscarDbCheckbox4 = $('<input>', {
                id: 'toggleGOscarDbCheckbox4',
                type: 'checkbox'
            });
            //********************************
            var y = '<label for="toggleGOscarDbCheckbox5">Measurements:</label><input id="toggleGOscarDbCheckbox5" name = "radio1" type="checkbox">' +
                ' Push to Chart<input id="toggleGOscarDbCheckbox10" onclick="measureTagfx(this)"  type="checkbox">'
            $('<div>').append($('<label>', {
                text: 'Use Database Tag:',
                for: 'toggleGOscarDbCheckbox'
            })).append($oscarDbCheckbox).appendTo($options_menu1).append(y)
            //***********************************

            var y = '<label for="toggleGOscarDbCheckbox7">Value</label><input id="toggleGOscarDbCheckbox7" onclick="measureTagfx(this)"  name = "radio2" type="radio" checked>'
            var z = '<label for="toggleGOscarDbCheckbox8">Date Observed</label><input id="toggleGOscarDbCheckbox8" onclick="measureTagfx(this)"  name = "radio2" type="radio">'
            var q = '<label for="toggleGOscarDbCheckbox9">Comment</label><input id="toggleGOscarDbCheckbox9" onclick="measureTagfx(this)"  name = "radio2" type="radio">'
            $('<div>').append($('<label>', {
                text: 'Add function:',
                for: 'toggleGOscarDbCheckbox2'
            })).append($oscarDbCheckbox2).appendTo($options_menu1).append(y);
            $('<div>').append($('<label>', {
                text: 'Make DatePicker:',
                for: 'toggleGOscarDbCheckbox3'
            })).append($oscarDbCheckbox3).appendTo($options_menu1).append(z);
            //*********************************
            var $div1 = $('<div>').appendTo($options_menu1);
            var $db_tag_select = addSelectMenu($div1, 'oscarDbTagSelect', 'Select Tag:', getOscarDBTags());
            $db_tag_select.selectmenu('disable');
            $db_tag_select.on('selectmenuchange', (function(event, data) {

                $textBoxTemplate.find(':input').attr('oscarDB', $db_tag_select.val());
                $('#someName').val($db_tag_select.val()); //2020-May-01
                $textAreaTemplate.find('textarea').attr('oscarDB', $db_tag_select.val());
				$textBoxTemplate.find(':input').attr('placeholder', $db_tag_select.val()); //2024-May-01
				$textAreaTemplate.find('textarea').attr('placeholder', $db_tag_select.val()); //2024-May-01
                $textBoxTemplate.find(':input').attr('title', $db_tag_select.val()); //2019-Feb-14
                $textAreaTemplate.find('textarea').attr('title', $db_tag_select.val()); //2019-Feb-14

            }));
            $('#oscarDbTagSelect-button').hide() //???????????



            //****************Text Box Title Snippet *************************************
            function titleSnippet() {
                //$textBoxTemplate.css('background-color', 'yellow')  //2020-May-01
                $textBoxTemplate.attr('title', 'TextBox - Click to drag. DoubleClick to activate'); // 2020-May-13
                $textAreaTemplate.attr('title', 'TextBox - Click to drag. DoubleClick to activate'); // 2020-May-17

                //$('#textBoxTemplate').find(':input').attr('ondblclick', 'mydclick2(this)');
                $textBoxTemplate.attr('ondblclick', 'mydclick2(this)'); // 2020-May-14
                $textAreaTemplate.attr('ondblclick', 'mydclick2(this)'); // 2020-May-17 now
                var customTitle = $('<input/>')
                    .attr('type', "input")
                    .attr('name', "someName")
                    .attr('id', "someName")
                    .attr('value', ""); // 2020-May-03
                customTitle.css("font-size", "12");
                $textBoxTemplate.after(customTitle);
                //$('#someName').val($db_tag_select.val());

                var $label = $("<label>").text('  Custom Title: ');
                $label.css("fontSize", 12);
                $label.attr('id', "theLabel");

                customTitle.before($label);

                document.getElementById("someName").style.width = textBoxWidth;
                document.getElementById("someName").style.height = textBoxHeight;

                $("#someName").change(function() {
                    $textBoxTemplate.find(':input').attr('title', $('#someName').val());
                    $textAreaTemplate.find(':input').attr('title', $('#someName').val());
                });


                $("#someName").focus(function() {
                    $textBoxTemplate.find(':input').attr('title', $('#someName').val());
                    $textAreaTemplate.find(':input').attr('title', $('#someName').val()); // 2020-May-12
                });


                $textBoxTemplate.find(':input').attr('title', ""); // 2020-May-03
                $textAreaTemplate.find('textarea').attr('title', ""); //2020-May-03
                $textBoxTemplate.find(':input').addClass('TextBox') //2020-May-14
                $textAreaTemplate.find('textarea').addClass('TextArea') //2020-May-14
            }

            //************************************************
            titleSnippet() //2020-May-03

            $oscarDbCheckbox.on('change', function(event, ui) { //2020-May-15

                if ($(this).is(':checked')) {

                    dbWindow($db_tag_select) //open db tag window  //2021-May-06

                    $textBoxTemplate.find(':input').val("")
                    $textBoxTemplate.find(':input').removeAttr('oscarDB');
                    $('#toggleGOscarDbCheckbox5').prop('checked', false);
                    $('#toggleGOscarDbCheckbox10').prop('checked', false);
                    $('#MeasureSelect').hide()
                    $('#oscarDbTagSelect-button').show()
                    $db_tag_select.selectmenu('enable');
                    $('#oscarDbTagSelect-button').prop('disabled', false);

                    $('#someName').val($db_tag_select.val()); // PHC TO DO
                    $textBoxTemplate.find(":input").attr('placeholder', $db_tag_select.val());
                    $textAreaTemplate.find("textarea").attr('placeholder', $db_tag_select.val());
                    $('#someName').trigger("change"); //  2020-May-12

                    //**************************************************************************************
                    $textBoxTemplate.find(':input').attr('oscarDB', $db_tag_select.val());
                    $textAreaTemplate.find('textarea').attr('oscarDB', $db_tag_select.val());
                } else {

                    $('#someName').val(""); // 2020-May-03

                    $db_tag_select.selectmenu('disable');
                    $textBoxTemplate.find(':input').removeAttr('oscarDB');
                    $textAreaTemplate.find('textarea').removeAttr('oscarDB');
                    $textBoxTemplate.find(':input').removeAttr('title');
                    $textAreaTemplate.find('textarea').removeAttr('title');
                    $textBoxTemplate.find(':input').removeAttr('placeholder'); //2024-May-01
                    $textAreaTemplate.find('textarea').removeAttr('placeholder'); //2024-May-01
                    $('#oscarDbTagSelect-button').hide()

                }
            });

            //**************************************
            var x; // placeholder for the string for the event attribute
            var y; //placeholder for the string for the parameterized function
            $oscarDbCheckbox2.on('change', function(event, ui) {
                if ($(this).is(':checked')) {

                    listfc() //create select list of functions
                    var e = document.getElementById('mySelect');
                    $(e).css('background-color', 'yellow')
                    $(e).focus() //alert(e.length)
                    $(e).attr('size', e.length + 1);
                    e.onclick = function() {
                        var strUser = e.options[e.selectedIndex].text;
                        var strContent = e.options[e.selectedIndex].value;

                        //****For Textbox******************************
                        $textBoxTemplate.find(':input').removeClass('mydateclass');
                        $( "#daFcn" ).text(strContent);
                        $( "#typeFlag" ).text("Text box");
                        switch (strUser) {
                            case "LinkTextBoxfunction":
                                $('#event_name').val('onblur').change();
                                $( "#parameters" ).val(strUser + '(this)');
                                break;

                            case "LinkXBoxfunction": // if{} below
                                $('#event_name').val('onclick').change();
                                $( "#parameters" ).val('myupdate(this); LinkXBoxfunction(this)');
                                break;

                            case "fixSin":
                                //      /(\d)(?=(\d\d\d)+(?!\d))/g, '$1-')       for SIN number
                                //      /(\d{3})(\d{3})(\d{4})/, '($1) $2-$3'   for Phone number
                                var myRe = "/(\\d)(?=(\\d\\d\\d)+(?!\\d))/g,'$1-'"; //2020-Mar-23
                                zz = myRe.split(",")
                                $('#event_name').val('onblur').change();
                                $( "#parameters" ).val(strUser + '(this,' + zz[0] + ',' + zz[1] + ')');
                                break;

                            case "dateReformat":
                                var zz = "'yyyy-mmm-dd'";
                                $('#event_name').val('onclick').change();
                                $( "#parameters" ).val(strUser + '(this,' + zz + ')');
                                $textBoxTemplate.find(':input').addClass('mydateclass');
                                break;

                            case "removeLineFeeds":
                                $('#event_name').val('onclick').change();
                                $( "#parameters" ).val(strUser + '(this)');
                                $textAreaTemplate.find(':input').attr('title', 'Double-click to compress, shift-click to reset');
                                break;

                            case "makehyperlink":
                                $('#event_name').val('onclick').change();
                                $( "#parameters" ).val(strUser + '(this)');
                                $textBoxTemplate.find(':input').attr('style', 'color:blue;');
                                document.getElementById('gen-textBoxDefaultTextId').value = 'http://';
                                alert("http://www.cnn.com  -  for webpage link \nmailto:joe@cnn.com  -  for email link \nThe value of this field can be edited later for pretty printing; the html link data is retained in the field title");
                                break;

                            default:
                                $('#event_name').val('onclick').change();
                                $( "#parameters" ).val(strUser + '(this)');

                        }
                        $( "#dialog-form" ).dialog( "open" );
                        $(e).remove() //close select list
                        closeNavGen() //close side bar

                    };
                } else {
                    //alert("unchecked")
                    var e = document.getElementById('mySelect');
                    document.getElementById('gen-textBoxDefaultTextId').value = ''
                    $(e).remove() //close select list
                    closeNavGen() //close side bar

                    $textBoxTemplate.find(':input').removeAttr('style');
                    $textBoxTemplate.find(':input').removeClass('mydateclass')
                }
            });
            $oscarDbCheckbox3.on('change', function(event, ui) {
                if ($(this).is(':checked')) {
                    $textBoxTemplate.find(":input").addClass("hasDatepicker");
                } else {
                    $textBoxTemplate.find(":input").removeClass("hasDatepicker");
                }
            });
            //**************************

            $('#toggleGOscarDbCheckbox5').on('change', function(event, ui) { //??????
                if ($(this).is(':checked')) {

                    $('#toggleGOscarDbCheckbox').prop('checked', false); //2020-May-01
                    $db_tag_select.selectmenu('disable');
                    $textBoxTemplate.find(':input').removeAttr('oscarDB');
                    $textAreaTemplate.find('textarea').removeAttr('oscarDB');
					$textBoxTemplate.find(':input').removeAttr('placeholder'); //2024-May-01
					$textAreaTemplate.find('textarea').removeAttr('placeholder'); //2024-May-01 invalid pseudo
                    //$('#someName').remove();  //2020-May-01
                    //$('#theLabel').remove();  //2020-May-01
                    $('#someName').val(""); // 2020-May-03

                    measureArray = measureArray.sort()
                    var myDiv = document.getElementById('oscarDbTagSelect-button'); //Append option list to page
                    var selectList2 = document.createElement('select');
                    selectList2.id = 'MeasureSelect';
                    $(myDiv).after(selectList2);

                    var e = document.getElementById("MeasureSelect");

                    $(e).change(function() {
                        var y = $(this);

                        measureTagfx(y)
                    });
                    //Create and append the options
                    for (var i = 0; i < measureArray.length; i++) {
                        var option = document.createElement('option');
                        // option.value = measureArray[i];
                        // option.title = measureArray[i];
                        option.text = measureArray[i]
                        selectList2.appendChild(option);
                        $('#oscarDbTagSelect-button').hide() //working here
                    }

                    measureTagfx()

                } else {
                    $('#MeasureSelect').hide()
                    $('#oscarDbTagSelect-button').show()
                    measureTagfx(y)
                    $('#oscarDbTagSelect-button').hide() //working here
                    $textBoxTemplate.find(':input').val("")
                    $textBoxTemplate.find(':input').removeAttr('oscarDB');
                    $textBoxTemplate.find(':input').removeAttr('title'); //2019-Feb-15
                    //$textAreaTemplate.find('textarea').removeAttr('title'); //2019-Feb-15
                    $('#toggleGOscarDbCheckbox10').prop('checked', false);
                }
            });
            //*************************
            $oscarDbCheckbox4.on('change', function(event, ui) {
                if ($(this).is(':checked')) {
                    measureArray = measureArray.sort()
                    var myDiv = document.getElementById('oscarDbTagSelect-button'); //Append option list to page
                    var selectList2 = document.createElement('select');
                    selectList2.id = 'MeasureSelect';
                    $(myDiv).after(selectList2);
                    //Create and append the options
                    for (var i = 0; i < measureArray.length; i++) {
                        var option = document.createElement('option');
                        // option.value = measureArray[i];
                        // option.title = measureArray[i];
                        option.text = measureArray[i]
                        selectList2.appendChild(option);
                    }
                } else {
                    $('#MeasureSelect').hide()
                }
            });

            //***************************************
        }
        /** tab 2 setup */
        function initLabelTemplateTab($tab) {
            var $options_menu2 = $("<div>", {
                class: "gen-control-menu"
            });
            var $dragFrame21 = createStitchFrame();
            var $labelTemplate = addDraggableLabel($dragFrame21, "textlabelTemplate", "sample text", "label-style_1");

            $tab.append($options_menu2);
            $tab.append($dragFrame21);

            $options_menu2.append($("<label>", {
                text: "Label Text:",
                for: "gen-textLabelValueId"
            })).append($("<input>", {
                id: "gen-textLabelValueId",
                type: "text",
                value: "sample text",
                change: function(event, ui) {
                    $labelTemplate.text($(this).val());
                }
            }));
        }
        /** tab 3 setup */
        function initShapeTemplateTab($tab) {
            var $dragFrame31 = createStitchFrame();
            addDraggableShape($dragFrame31, "square", defaultShapeSize, defaultShapeSize, "square");
            addDraggableShape($dragFrame31, "square-rounded", defaultShapeSize, defaultShapeSize, "square-rounded");
            addDraggableShape($dragFrame31, "circle", defaultShapeSize, defaultShapeSize, "circle");
            $tab.append($dragFrame31);
        }
        /** tab 4 setup */
        function initImageTemplateTab($tab) {

            var $dragFrame41 = createStitchFrame();

            if (!runStandaloneVersion) {
                var options = [""];
                for (var i = 0; i < eFormImageList.length; i++) {
                    options.push(eFormImageList[i]);
                }

                var $widget = null;

                var $fileSelector = addSelectMenu($tab, "imageSelect2", "Select Image", options);
                $fileSelector.selectmenu();
                $tab.append($dragFrame41);

                $fileSelector.on("selectmenuchange", (function(event, data) {
                    var selectedFile = $fileSelector.val();
                    if (selectedFile.length < 1) {
                        return;
                    }
                    var src = OSCAR_DISPLAY_IMG_SRC + encodeURIComponent(selectedFile);

                    // remove the old widget
                    if ($widget) {
                        $widget.remove();
                    }
                    // create a fake element to load the image (need to get the attributes height/width)
                    var $img = $("<img>", {
                        src: src,
                        hidden: "hidden"
                    }).appendTo($dragFrame41);

                    $img.on('load', function() {
                        $widget = addDraggableImage($dragFrame41, getUniqueId(baseImageWidgetName), $(this).width(), $(this).height(), src, "");
                        $img.remove(); //remove the fake, not needed now
                    });

                }));
            } else {

                $tab.append($("<span>", {
                    text: "Images must be in same folder as the generator ",
                    css: {
                        flex: 1
                    }
                }));

                var $fileSelector2 = $("<input>", {
                    type: "file",
                    accept: ".png"
                }).change(function() {
                    if (this.files && this.files[0]) {
                        var reader = new FileReader();
                        var $fileInput = $(this);
                        reader.onload = function(readerEvt) {
                            var img = new Image();
                            img.src = readerEvt.target.result;
                            var src = $fileInput.val().replace(/C:\\fakepath\\/i, '');
                            img.onload = function() {
                                console.log(img.width, img.height);
                                addDraggableImage($dragFrame41, getUniqueId(baseImageWidgetName), img.width, img.height, src, "");
                            }
                        };
                        reader.readAsDataURL(this.files[0]);

                    }
                }).appendTo($tab);
            }
			$tab.append($dragFrame41);
        }
        /** signature tab init */  //reworked May 01 2024
        function initSignatureTemplateTab($tab) {


            var $dragFrame51 = createStitchFrame();
            var src = "BNK.png";
            if (!runStandaloneVersion){ src = OSCAR_DISPLAY_IMG_SRC + src;}
            addDraggableStamp($dragFrame51, "signatureStamp", 255, 50, src, "signatureStamp");
            $tab.append($dragFrame51);
            var $label = $("<label>").text('  Add Signature Stamp: ');
            $label.css("fontSize", 12);
            $label.attr('id', "stampLabel");
            $dragFrame51.before($label);

            if (!signaturePadLoaded) {
                $tab.append($("<span>", {
                    text: "Missing External Signature Source Code File for Wet Signatures",
                    style: 'font-size:9px'
                }));

            } else {
                var $dragFrame52 = createStitchFrame();
                addDraggableSignaturePad($dragFrame52, "signaturePad", 255, 50, "signaturePad");
                $tab.append($dragFrame52);
                $label = $("<label>").text('  Add Wet Signature: ');
                $label.css("fontSize", 12);
                $label.attr('id', "padLabel");
                $dragFrame52.before($label);
            }
        }

        function init_input_controls($element) {

            var tabNames = [EFORM_I18N.tabCheckbox, EFORM_I18N.tabTextBox, EFORM_I18N.tabLabel, EFORM_I18N.tabShapes, EFORM_I18N.tabImages, EFORM_I18N.tabSignature];
            var $tabs = addTabs($element, "control_menu_1-placement-tabs", tabNames);
            /* tab 0 -- Checkbox */
            initCheckboxTemplateTab($tabs[0]);
            /* tab 1 -- Text Box */
            initTextboxTemplateTab($tabs[1]);
            /* tab 2 -- Labels */
            initLabelTemplateTab($tabs[2]);
            /* tab 3 -- Shapes */
            initShapeTemplateTab($tabs[3]);
            /* tab 4 -- Images */
            initImageTemplateTab($tabs[4]);
            /* tab 5 -- Signaure Pad */
            initSignatureTemplateTab($tabs[5]);

            for (var i = 0; i < $tabs.length; i++) {
                $tabs[i].addClass("flexV");
            }

            /* input common footer */
            var $footer = createFieldset("form-building-controls", "Controls")
                .append($("<div>")).append($("<label>", {
                    text: EFORM_I18N.helpAltResize
                }))
                .append($("<div>")).append($("<label>", {
                    text: EFORM_I18N.helpShiftAspectRatio
                }))
                .append($("<div>")).append($("<label>", {
                    text: EFORM_I18N.helpCtrlCVCopy
                }));
            var $control_fieldset = createFieldset("grid-guide_options", "Guide Options");
            var $trash_box = createTrashFrame();
            $trash_box.append("<span class='ui-icon ui-icon-trash' style='-webkit-transform: scale(2);'></span> Trash");
            $trash_box.droppable({
                accept: ".ui-draggable",
                hoverClass: "gen-trashHover",
                drop: function(event, ui) {
                    var ele = $(ui.draggable);
                    if (!ele.hasClass("gen-cloneable")) {
                        ui.draggable.remove();
                    }
                }
            });
            //$trash_box.appendTo($rootElement);
            var $guideRulerEnabled = $("<div>")
                .append($("<label>", {
                    text: EFORM_I18N.labelShowRulerMarks,
                    for: "toggleRuler"
                }))
                .append($("<input>", {
                    id: "toggleRuler",
                    type: "checkbox",
                    checked: defaultShowRuler,
                    css: {
                        width: "16px",
                        height: "16px"
                    }
                }).change(function() {
                    $(".gen-snapGuide").find(".handle").toggleClass("ruler", $(this).is(':checked'));
                }));
            $(".gen-snapGuide").find(".handle").toggleClass("ruler", defaultShowRuler); //initialize
            var $guideToggleCheckbox = $("<div>")
                .append($("<label>", {
                    text: EFORM_I18N.labelEnableSnapGuides,
                    for: "toggleSnapGuides"
                }))
                .append($("<input>", {
                    id: "toggleSnapGuides",
                    type: "checkbox",
                    checked: defaultEnableSnapGuides,
                    css: {
                        width: "16px",
                        height: "16px"
                    }
                }).change(function() {
                    $(".gen-snapGuide").toggle($(this).is(':checked'));
                }));
            $control_fieldset.append($guideToggleCheckbox).append($guideRulerEnabled);

            var $hbox = $("<div>").append($trash_box).append($control_fieldset).append($footer);
            $element.append($hbox);
        }

        function setNoborderStyle($selector, value) {
            // sets radiobox border to Always Visible
            var x = $selector.attr('class')
            if (x) {
                var n = x.indexOf('cradio');
                if (n > 0) {
                    value = 3
                }
            }

            if ($selector == null) return false;
            switch (value) {
                case 2:
                    $selector.toggleClass("noborder", true).toggleClass("noborderPrint", false);
                    break;
                case 1:
                    $selector.toggleClass("noborder", false).toggleClass("noborderPrint", true);
                    break;
                case 3: // always visible
                    $selector.toggleClass("noborder", false).toggleClass("noborderPrint", false);
                    break;
                default: // always visible
                    $selector.toggleClass("noborder", false).toggleClass("noborderPrint", false); //2022-May-30
                    break;
            }
        }

        function init_style_controls($element) {

            var visibilityLables = ["Always Visible", "Invisible on Print", "Always Invisible"];
            var $textHideOptions = addRadioGroup($element, "gen-text-border-radio", "Text Borders", visibilityLables);
            $textHideOptions.on("change", function(e) {
                var value = parseInt($(e.target).val());
                var $selector = $(".input_elements").find(TEXT_INPUT_SELECTOR);
                textBordersVisibleState = value;
                setNoborderStyle($selector, value);
            });
            // set inital value index
            $textHideOptions.find("input").filter("[value='" + textBordersVisibleState + "']").prop("checked", true).button("refresh");

            var $xboxHideOptions = addRadioGroup($element, "gen-xbox-border-radio", "xBox Borders", visibilityLables);
            $xboxHideOptions.on("change", function(e) {
                var value = parseInt($(e.target).val());
                var $selector = $(".input_elements").find(XBOX_INPUT_SELECTOR);
                xboxBordersVisibleState = value;
                setNoborderStyle($selector, value);
            });
            // set inital value index
            $xboxHideOptions.find("input").filter("[value='" + xboxBordersVisibleState + "']").prop("checked", true).button("refresh");

        }

        function init_finalize_controls($element) {
            var $options_menu = $("<div>", {
                class: "gen-control-menu"
            });
            var $toggleFaxControls = $("<input>", {
                id: "toggleFaxControls",
                type: "checkbox",
                css: {
                    width: "16px",
                    height: "16px"
                },
                checked: defaultIncludeFaxControl
            })
            var $toggleTickler = $("<input>", {
                id: "setTickler",
                type: "checkbox",
                css: {
                    width: "16px",
                    height: "16px"
                },
				click: function(event) {
                        html = "<div id='tickle_dialog'>This form will set a tickler for the MRP when faxed or PDF in <input type='number' id='tickler_weeks' size='3' required='required' pattern='^\d*$'  value='5' /> weeks </div>";
						if ($("#tickle_dialog").length < 1) {
							$("#BottomButtons").prepend(html);
						} else {
							$("#tickle_dialog").remove();
						}
                    }
            })

            $options_menu.append($("<div>").append($("<label>", {
                text: "Eform Name",
                for: "eformNameInput"
            })).append($("<input>", {
                id: "eformNameInput",
                type: "text",
                value: eformName,
				css: {
                    width: "160px",
                    height: "16px"
                },
                change: function(event, ui) {
                    eformName = $(this).val();
                }
            })));
            $options_menu.append($("<div>").append($("<label>", {
                text: "Include Fax Controls",
                for: "toggleFaxControls"
            })).append($toggleFaxControls));

            $options_menu.append($("<div>").append($("<label>", {
                text: "Default Fax No",
                for: "defaultFaxNo"
            })).append($("<input>", {
                id: "defaultFaxNo",
                type: "text",
				css: {
                    width: "160px",
                    height: "16px"
                },
                title: "555-555-5555",
                change: function(event, ui) {
                    defaultFaxNo = $(this).val();
                }
            })));
            $options_menu.append($("<div>").append($("<label>", {
                text: "Include Set Tickler",
                for: "setTickler"
            })).append($toggleTickler));

            $options_menu.append($("<div>").append($("<label>", {
                text: "Stand alone testing",
                for: "standAlone"
            })).append($("<input>", {
                id: "standAlone",
                type: "checkbox"
            })));

            $element.append($options_menu);

            $element.append($('<button>', {
                    id: "showSource",
                    text: EFORM_I18N.buttonViewSource,
                    click: function(event) {
                        showSource($toggleFaxControls.is(':checked'));
                        event.preventDefault();
                    }
                }).button({
                    icon: "ui-icon-newwin"
                }))
                .append($('<button>', {
                    id: "downloadSource",
                    text: EFORM_I18N.buttonDownload,
                    click: function(event) {
                        downloadSource($toggleFaxControls.is(':checked'));
                        event.preventDefault();
                    }
                }).button({
                    icon: "ui-icon-document"
                }))
                .append($('<button>', {
                    id: "printPreview",
                    text: EFORM_I18N.buttonPrintPreview,
                    click: function(event) {
                        onEformPrint();
                        event.preventDefault();
                    }
                }).button({
                    icon: "ui-icon-print"
                }));

            // button for saving directly to oscar eforms
            if (!runStandaloneVersion) {
                $element.append($('<button>', {
                    id: "saveToOscarButton",
                    text: OSCAR_SAVE_MESSAGE_NEW,
                    click: function(event) {
                        saveToOscarEforms($toggleFaxControls.is(':checked'));
                        event.preventDefault();
                    }
                }).button({
                    icon: "ui-icon-disk"
                }));
            }
        }

        function addSnapGuidesTo($element) {
            var $vertSnapBox = $("<div>", {
                class: "gen-snapGuide",
                height: "100%"
            }).append($("<div>", {
                class: "gen-snapLine vertical"
            }).append($("<p>", {
                class: "handle" //ruler
            }).disableSelection())).appendTo($element);
            var $horzSnapBox = $("<div>", {
                class: "gen-snapGuide",
                width: "100%"
            }).append($("<div>", {
                class: "gen-snapLine horizontal"
            }).append($("<p>", {
                class: "handle" //ruler
            }).disableSelection())).appendTo($element);
            $vertSnapBox.draggable({
                containment: $element,
                handle: "p"
            });
            $horzSnapBox.draggable({
                containment: $element,
                handle: "p"
            });
            $vertSnapBox.toggle(defaultEnableSnapGuides);
            $horzSnapBox.toggle(defaultEnableSnapGuides);
        }
        /** create and return html for a new eform page */
        function createNewPageDiv() {
            return $("<div>", {
                id: getUniqueId(basePageName),
                class: "page_container",
                css: {
                    width: eFormPageWidth,
                    height: eFormPageHeight
                }
            }).append($("<div>", {
                class: "input_elements"
            }));
        }
        /** add a new page to the eform */
        function createNewPage() {
            var $newpage = createNewPageDiv();
            //TODO better way of appending pages to the form (that won't rely on BottomButtons placement)
            $newpage.insertBefore($("#BottomButtons"));

            $newpage.droppable({
                accept: ".gen-layer2, .gen-layer3",
                drop: function(event, ui) {
                    dropOnForm(ui, $(this).find(".input_elements"));
                }
            });
            addSnapGuidesTo($newpage);
            return $newpage;
        }

        function dragAndDropEnable(enable) {
            if (enable) {
                $('.gen-draggable').draggable("enable");
                $('.inputOverride').attr("disabled", false);
            } else {
                $('.gen-draggable').draggable("disable");
                $('.inputOverride').attr("disabled", true);
                //make xBoxes work in generator (have to unbind and rebind click events)
                var $input_elements = $(".input_elements");
                $input_elements.find(".xBox").unbind("click");
                initXBoxes($input_elements);
            }
            dragAndDropEnabled = enable;
        }

        function onKeyUp(e) {

            if (!e.altKey) {
                $('.gen-resizable').resizable("disable");
            }
            if (!e.shiftKey) {
                enableElementHighlights = false;
            }
        }

        function onKeyDown(e) {
            if (e.altKey) {
                $('.gen-resizable').resizable("enable");
                e.preventDefault();
            }
            if (e.shiftKey && !e.altKey) {
                enableElementHighlights = true;
            }

            if (e.ctrlKey && e.which == 67) { //Ctrl C
                var $widget;
                if ($mouseTargetElement.hasClass("gen-widget")) {
                    $widget = $mouseTargetElement;
                } else {
                    $widget = $mouseTargetElement.parent(".gen-widget");
                }
                if ($globalSelectedElement) {
                    $globalSelectedElement.removeClass("selectedHighlight");
                }
                if ($widget.length > 0) {
                    $globalSelectedElement = $widget;
                    $globalSelectedElement.addClass("selectedHighlight");
                } else {
                    $globalSelectedElement = null;
                }
            } else if (dragAndDropEnabled && $globalSelectedElement && $mouseTargetElement && e.ctrlKey && e.which == 86) { //Ctrl V
                // paste an element to the closest input_elements div to mouse target
                var $appendTo;
                if ($mouseTargetElement.hasClass("input_elements")) {
                    $appendTo = $mouseTargetElement;
                } else {
                    $appendTo = $mouseTargetElement.parents(".input_elements");
                }
                if ($appendTo.length > 0) {
                    var relativeXPosition =  Math.round((currentMousePos.x - $appendTo.offset().left)); //2024-May-01
                    var relativeYPosition = Math.round((currentMousePos.y - $appendTo.offset().top)); //2024-May-01
                    var pos = {
                        top: relativeYPosition,
                        left: relativeXPosition,
                        position: "absolute"
                    };

                    var $clone = cloneWidgetAt($appendTo, pos, $globalSelectedElement);
                    $clone.removeClass("selectedHighlight");
                }
            }
            if ($mouseTargetElement && e.which == 46) { //DEL key
                var $toDelete;
                if ($mouseTargetElement.hasClass("gen-widget")) {
                    $toDelete = $mouseTargetElement;
                } else if ($mouseTargetElement.parents(".gen-widget").length > 0) {
                    $toDelete = $mouseTargetElement.parents(".gen-widget");
                }
                if ($toDelete) {
                    if ($globalSelectedElement && ($toDelete.is($globalSelectedElement) ||
                            $.contains($toDelete.get(0), $globalSelectedElement.get(0)))) {
                        $globalSelectedElement.removeClass("selectedHighlight");
                        $globalSelectedElement = null;
                    }
                    $toDelete.remove();
                    $mouseTargetElement = null;
                }
            }
        }

        function onContainerMouseMove(e) {

            var elem = e.target || null;
            if ($mouseTargetElement != null) {
                $mouseTargetElement.removeClass("divHighlight");
            }
            if (elem != null) {
                $mouseTargetElement = $(elem);
                if (enableElementHighlights) {
                    $mouseTargetElement.addClass("divHighlight");
                }
            }
        }

        function init() {
            /* browser check */
            if (!(inFirefox || inChrome)) {
                $("#main_container").html("This page only works when loaded in Mozilla Firefox or Google Chrome.");
                return false;
            }
            signaturePadLoaded = (typeof SignaturePad !== 'undefined');
            console.info("signature loaded: " + signaturePadLoaded);

            /* set up element positioning */
            var $eform_container = $("#eform_container");
            $("#eform_view_wrapper").resizable({
                handles: "e",
                minWidth: eFormViewMinWidth,
                maxWidth: eFormPageWidthLandscape + 25
            });

            var $page1 = createNewPage();
            //add_background_image($image_frame);
            init_form_load($('#control_menu_1-load'));
            var $pageControl = init_setup_controls($('#control_menu_1-page_setup'));
            $pageControl.append(createPageControlDiv($page1));
            init_input_controls($('#control_menu_1-placement'));
            init_style_controls($('#control_menu_1-stylize'));
            init_finalize_controls($('#control_menu_1-finalize'));
            //init_control_info_bar($('#control'), $eform_container);

            $("#control_menu_1").accordion({
                heightStyle: "content",
                collapsible: true,
                active: defaultMenuOpenIndex,
                activate: function(event, ui) {
                    dragAndDropEnable(ui.newPanel.hasClass("gen-allow_drag"));
                }
            });

            $(document).keydown(onKeyDown);
            $(document).keyup(onKeyUp);

            /* In the generator we don't want to print/submit like a normal e-form
             * so we override the functions here to disable/change their actions within the e-form generator! */
            onEformSubmit = function() {
                custom_alert('This would submit the eform');
                return false;
            };
            onEformPrint = function() {
                write_values_to_html();
                var divToPrint = document.getElementById('eform_container');
                var style1 = document.getElementById('eform_style').innerHTML;
                var style2 = document.getElementById('eform_style_shapes').innerHTML;
                var style3 = document.getElementById('eform_style_signature').innerHTML;
                // Build the print HTML; window.print() + close are triggered via onload in the page itself.
                // A 1 s close delay ensures Firefox shows the print dialog before the window closes.
                var htmlPrint = '<html><head><title>' + escapeHtmlText(eformName) + '</title><style>' + style1 + style2 + style3 +
                    '</style></'+'head><body onload="window.print();setTimeout(function(){window.close();},1000);">' + divToPrint.innerHTML + '</body></html>';
                // Use a Blob URL instead of document.write to avoid DOM text reinterpreted as HTML
                var blob = new Blob([htmlPrint], {type: 'text/html;charset=UTF-8'});
                var url = URL.createObjectURL(blob);
                // Revoke after BLOB_URL_REVOKE_TIMEOUT_MS as a safety fallback (handles popup-blocked case too)
                var revokeTimer = setTimeout(function() { URL.revokeObjectURL(url); }, BLOB_URL_REVOKE_TIMEOUT_MS);
                var newWin = window.open(url, 'Print-Window');
                if (newWin) {
                    newWin.opener = null;
                    newWin.addEventListener('load', function() {
                        clearTimeout(revokeTimer);
                        URL.revokeObjectURL(url);
                    });
                } else {
                    // Popup was blocked — revoke immediately
                    clearTimeout(revokeTimer);
                    URL.revokeObjectURL(url);
                }
            };
            onEformPrintSubmit = function() {
                onEformPrint();
                custom_alert('This would submit the eform');
                return false;
            };
            $eform_container.mousemove(onContainerMouseMove);
            $(document).mousemove(function(event) {
                currentMousePos.x = event.pageX;
                currentMousePos.y = event.pageY;
            });
        }
    </script>

    <script id="link_script" class="toSource">

        /** This function is called on mouseover for linked xboxes and show them as yellow */
        function LinkXBoxfunctionMouseover(e) {
            var res = "LinkedTo-" + e.id + "-"
            var x = '[title*="' + res + '"]'
            var valAll = document.querySelectorAll(x)

            for (i = 0; i < valAll.length; i++) {
                $(valAll[i]).css('background-color', 'yellow')
            }
        }

        /** This function is called on mouseout for linked xboxes to remove the background */
        function LinkXBoxfunctionMouseout(e) {
            var res = "LinkedTo-" + e.id + "-"
            var x = '[title*="' + res + '"]'
            var valAll = document.querySelectorAll(x)

            for (i = 0; i < valAll.length; i++) {
                $(valAll[i]).css('background-color', '')
            }
        }
    </script>

    <script id="signature_script" class="toSource">
        /** this function is run on page load to make signature pads work. */
        $(function() {
            $(".signaturePad").each(function() {
                var $this = $(this);
                var $canvasFrame = $this.children(".canvas_frame");
                var $clearBtn = $this.children(".clearBtn");
                var canvas = $canvasFrame.children("canvas").get(0);
                var $data = $canvasFrame.children(".signature_data");
                var src = $data.val();
                // the image is needed even when signature pads are loaded for printing/faxing
                var $img = null;
                if (src && src.length > 0 && isValidImageSrc(src)) {
                    $img = $("<img>", {
                        src: src,
                        alt: "signature",
                        class: "signature_image"
                    });
                    $img.appendTo($canvasFrame);
                } else if (src && src.length > 0) {
                    $canvasFrame.append($("<div>").text("Signature could not be displayed").css({color: 'red', fontSize: '0.8em', padding: '4px'}));
                }

                // if signature pad loaded correctly and eform viewed on screen
                /* NOTE: media type does not currently work in wkhtmltopdf
                 See https://github.com/wkhtmltopdf/wkhtmltopdf/issues/1737 */
                if (typeof SignaturePad !== 'undefined' && window.matchMedia("screen").matches) {
                    console.info("editable signature pad initializing ");
                    if ($img) $img.hide();
                    var updateSlaveSignature = function(src_canvas, dest_canvas) {
                        // write to the destination with image scaling
                        var dest_context = dest_canvas.getContext("2d");
                        dest_context.clearRect(0, 0, dest_canvas.width, dest_canvas.height);
                        dest_context.drawImage(src_canvas, 0, 0, dest_canvas.width, dest_canvas.height);
                    };
                    // initialize the signature pad
                    var signPad = new SignaturePad(canvas, {
                        minWidth: 1,
                        maxWidth: 2,
                        onEnd: function() {
                            $this.trigger("signatureChange");
                        }
                    });
                    // load the image data to the canvas ofter initialization
                    if (src != null && src != "") {
                        signPad.fromDataURL(src);
                    }
                    // define a custom update trigger action. this allows the eform to store the signature.
                    $this.on("signatureChange", function() {
                        var dataUrl = signPad.toDataURL();
                        $data.val(dataUrl);
                        if (!$img) {
                            $img = $("<img>", { src: dataUrl, alt: "signature", class: "signature_image" });
                            $img.appendTo($canvasFrame).hide();
                        } else {
                            $img.prop('src', dataUrl);
                        }
                        if ($this.attr('slaveSigPad')) {
                            var $slavePad = $("#" + $this.attr('slaveSigPad')); // get slave pad by id
                            updateSlaveSignature(canvas, $slavePad.find("canvas").get(0));
                            $slavePad.trigger("signatureChange"); // be careful of infinite loops
                        }
                        return false;
                    });
                    // init the clear button
                    $clearBtn.on('click', function() {
                        signPad.clear();
                        $this.trigger("signatureChange");
                        return false;
                    });
                }
                // not using the canvas, show signature as an image instead.
                else {
                    console.info("static signature pad initializing");
                    if ($img) $img.show();
                }
            });
        });
    </script>

	<script id="stamp_script" class="toSource">
    <!-- functions to support signature stamps -->

        /** this function toggles visibility of the individual stamp. */
        function toggleMe(el){
	        if (el.src.indexOf("BNK.png")>-1){
		        sign(el);
	        } else {
		        el.src = "../eform/displayImage.do?imagefile=BNK.png";
	        }
        }
        /** this function stamp by delegation model */
        function sign(el){
	        var provNum = '';
			if (!document.getElementById('user_ohip_no')) { return; }
	        var userBillingNo = document.getElementById('user_ohip_no').value;
	        if (parseInt(userBillingNo) > 100) {
		        // then a valid billing number so use the current user id
		        provNum = document.getElementById('user_id').value;
		        if (provNum != document.getElementById('doctor_no').value && !!document.getElementById('doctor')) {
			        document.getElementById('doctor').value=document.getElementById('CurrentUserName').value + ' CC: ' + document.getElementById('doctor').value;
		        }
	        } else {
		        provNum = document.getElementById('doctor_no').value;
	        }
			if ( provNum.length > 0 ) {
				el.src = '../eform/displayImage.do?imagefile=consult_sig_'+provNum+'.png';
			}
        }

        /** this function initialises each stamp */
        function signForm(){
            var els = document.getElementsByClassName( "stamp" );
            Array.prototype.forEach.call(els, function(el) {
                sign(el);
            });
        }
    </script>

    <script id="faxno_script" class="toSource">

        /** this function is run on page load to load a defined fax number. */
        function setFaxNo(){
            if (document.getElementById("fax_no")) {
                if (document.getElementById("fax_no").value.length >1 ) {
                    setTimeout('document.getElementById("otherFaxInput").value=document.getElementById("fax_no").value;',1000);
                }
            }
        }
    </script>

	<script id="xbox_script" class="toSource">

        /** initializes x-box input functionality.
         *  should be called once on eform load */
        function initXBoxes() {
			var $selector = $(".input_elements");
            $selector.find(".xBox").click(function() {
                $(this).val($(this).val() === 'X' ? '' : 'X');
				$(this).css("background", "white");
            }).keypress(function(event) {
                // any key press except tab will constitute a value change to the checkbox
                if (event.which != 0) {
                    $(this).click();
                    return false;
                }
            });
        }
    </script>

	<script id="gender_script" class="toSource">

        /** pre-check checkboxes and xboxes based on patient gender */
        function initPrecheckedCheckboxes() {
            var $selector = $(".input_elements");
            var $patientGender = $("#PatientGender");
            if ($patientGender) {
                var filter = ".gender_precheck_" + $patientGender.val();
                $selector.find("input[type=checkbox]").filter(filter).prop('checked', true);
                $selector.find(".xBox").filter(filter).val('X');
				$selector.find(".cradio").filter(filter).val('X');
            }
        }
    </script>

	<script id="radio_script" class="toSource">

        /** This function ensures only one box in the radio group will be checked */
        function myupdate(vid) {
            var x = document.getElementsByClassName(vid.getAttribute("class"));
            var mygroup = $(x).attr("class").split(" ");
            for (i = 0; i < mygroup.length; i++) {
                if (mygroup[i].indexOf("only-one-radio") > -1) {
                    var getmyclass = mygroup[i]
                }
            }
            var x = document.getElementsByClassName(getmyclass);
            if (getmyclass.indexOf("only-one-radio#") < 0) { //if there is only one member of the radiogroup...
                alert("This will act like a checkbox");
                if (vid.value == "X") {
                    vid.value = "";
                    return
                } else {
                    vid.value = "X"
                    return
                }
            } else {
                for (var i = 0; i < x.length; i++) {
                    x[i].style.boxShadow = "inset 0 0 0 1000px white"
                    x[i].value = "";
                    vid.value = "X"
                    //vid.style.boxShadow = "inset 0 0 0 1000px black"  //comment this line to inactivate black box*************************************
                }
            }
        }
    </script>

	<script id="date_script" class="toSource">

        /** This function is called on load to initialse DHTML date picker */
		function initCalendar() {
            var dlist = document.getElementsByClassName("hasDatepicker")
            for (i = 0; i < dlist.length; i++) {
                Calendar.setup( { inputField : dlist[i].id, ifFormat : "%Y-%m-%d",  button : dlist[i].id } ); //reference to calendar-setup.js
            }
			// Format date fields to date only
            var dlist = document.getElementsByClassName("hasDatepicker")
            for (i = 0; i < dlist.length; i++) {
            dlist[i].value = dlist[i].value.substring(0, 10);
            }
			//update all date classes
            var x = document.getElementsByClassName('mydateclass');
            for (i = 0; i < x.length; i++) {
                x[i].focus()
            }
        }
    </script>

	<script id="standAlone_script" class="toSource">

        /** This function is called on load to change paths to local */
        function reImg() {
            // for stand alone development without uploading to OSCAR
            var strLoc = window.location.href.toLowerCase();
            if (strLoc.indexOf("https") == -1) {
                if (document.getElementById("gen_backgroundImage1")) {
                    var src1 = document.getElementById("gen_backgroundImage1").src;
                    document.getElementById("gen_backgroundImage1").src = src1.replace(/\$%7Boscar_image_path%7D/g, "");
                }
				if (document.getElementById("BGImage1")) {
                    var src1 = document.getElementById("BGImage1").src;
                    document.getElementById("BGImage1").src = src1.replace(/\$%7Boscar_image_path%7D/g, "");
                }
				if (document.getElementById("signature")) {
                    var src1 = document.getElementById("signature").src;
                    document.getElementById("signature").src = src1.replace(/\$%7Boscar_image_path%7D/g, "");
                }
				var els = document.getElementsByClassName( "stamp" );
				if (els.length > 0) {
					Array.prototype.forEach.call(els, function(el) {
						el.src = el.src.replace(/\$%7Boscar_image_path%7D/g, "");
					});
				}
                if (document.getElementById("mySidenavGen2")) {
                    var src2 = document.getElementById("mySidenavGen2").src;
                    document.getElementById("mySidenavGen2").src = src2.replace(/\$%7Boscar_image_path%7D/g, "");
                }
            }
        }

        /** This function called on init to disable form buttons */
        function disableButtons() {

            //Check if this is standalone
            var x = window.location.toString()
            if (x.indexOf("eform") > -1) {
                $('#SubmitButton').prop('disabled', false);
                $('#PrintSubmitButton').prop('disabled', false);

                if ($('#SaveAsButton')) {
                    $('#SaveAsButton').prop('hidden', false);
                }
            } else {
                $('#SubmitButton').prop('disabled', true);
                $('#PrintSubmitButton').prop('disabled', true);
                if ($('#SaveAsButton')) {
                    $('#SaveAsButton').prop('hidden', true);
                }
            }

        }
    </script>


	<script id="allno_script" class="toSource">

        /** This function is called on load to initialse all no button */
        function initAllNo() {
            var z = document.getElementsByClassName("all-no-button2");
            for (var i = 0; i < z.length; i++) {
                z[i].addEventListener("click", function() {
                    allno();
                });
            }
        }

        /** This function this checks All No on click */
        function allno() {
            var x = document.getElementsByClassName("all-no");
            for (var i = 0; i < x.length; i++) {
                x[i].value = "X";
				//x[i].style.boxShadow = "inset 0 0 0 1000px black"  //comment this line to inactivate black box
            }
            var x = document.getElementsByClassName("clear-yes");
            for (var i = 0; i < x.length; i++) {
                x[i].value = "";
                x[i].style.boxShadow = "inset 0 0 0 1000px white";
            }
        }

	</script>


	<script id="eform_script" class="toSource">

    <!-- a collection of standard scripts for the base eform -->

        var needToConfirm = false;
        document.onkeyup=setDirtyFlag();

        /** call this to prevent closing the window without a confirmation */
        function setDirtyFlag() {
            needToConfirm = true;
        }
        /** call this to prevent the exit confirmation popup when closing the window */
        function releaseDirtyFlag() {
            needToConfirm = false;
        }

        window.onbeforeunload = confirmExit;
        /** This function will prevent the window from closing
         * without confirmation when unsaved changes have been made */
        function confirmExit(){
            if (needToConfirm){
                return "You have attempted to leave this page. If you have made any changes to the fields without clicking the Save button, your changes will be lost. Are you sure you want to exit this page?";
            }
        }

        /** This function is called onload to activate onblur events if needed */
        function focusAll() {
            $('.gen_input').each(function() {
                var x = $(this).attr("onblur")
                if (x) {
                    if (x.indexOf("fixSin(this)") > -1) {
                        $(this).blur()
                    }
                    if (x.indexOf("dateReformat(this") > -1) {
                        $(this).blur()
                    }
                }
            });
        }

        /** This function is called when the print button is clicked */
        function onEformPrint() {
            window.print();
        }

        /** This function is called when the eform submit button is clicked */
        function onEformSubmit() {
            releaseDirtyFlag();
            document.forms[0].submit();
        }

        /** This function is called when the eform print & submit button is clicked */
        function onEformPrintSubmit() {
            onEformPrint();
            releaseDirtyFlag();
            setTimeout('document.forms[0].submit()', 2000);
        }

    <!-- eForm specific scripts below -->
</script>
<script>
       function SELECT_FUNCTION() {} //Start function list here and below

       function copyToEncounter(myelement) {
            text = myelement.value
	          try {
		        if( window.parent.opener.document.forms["caseManagementEntryForm"] != undefined ) {
			        window.parent.opener.pasteToEncounterNote(text);
		          }else if( window.parent.opener.document.encForm != undefined ){
			        window.parent.opener.document.encForm.enTextarea.value = window.parent.opener.document.encForm.enTextarea.value + text;
		          }else if( window.parent.opener.document.getElementById(noteEditor) != undefined ){
			        window.parent.opener.document.getElementById(noteEditor).value = window.parent.opener.document.getElementById(noteEditor).value + text;
		          }else if(window.parent.opener.parent.opener.document.forms["caseManagementEntryForm"] != undefined){
			          window.parent.opener.parent.opener.pasteToEncounterNote(text);
		          }else if( window.parent.opener.parent.opener.document.encForm != undefined ){
			        window.parent.opener.parent.opener.document.encForm.enTextarea.value = window.parent.opener.parent.opener.document.encForm.enTextarea.value + text;
		          }else if( window.parent.opener.parent.opener.document.getElementById(noteEditor) != undefined ){
			        window.parent.opener.parent.opener.document.getElementById(noteEditor).value = window.parent.opener.parent.opener.document.getElementById(noteEditor).value + text;
			        }
	        } catch(e) {
		        alert(text + " could not be copied to the encounter note.");
	        }
       }

       function maketime(myelement){  //2020-Oct-07
           var dt=new Date();
           //var time=dt.getHours() + ':' + (dt.getMinutes()<10?'0':'') + dt.getMinutes()
           var time= (dt.getHours()<10?'0':'') + dt.getHours() + ':' + (dt.getMinutes()<10?'0':'') + dt.getMinutes()
           myelement.value=time
           }

        function myerror(myelement) { //not used for now. Rather use reImg()
            //function to load image as standalone
            var newsrc = myelement.src.split('%7D')[1]
            myelement.src = newsrc
        }

        function makehyperlink(myelement) {  //2021-Apr-01
            document.getElementById(myelement.id).readOnly = true;
            document.getElementById(myelement.id).style.color = "blue";
            window.open(myelement.value)
        }

        function removeLineFeeds(myelement) {
            var str = myelement.value.replace(/(\r\n\t|\n|\r\t)/gm, '; ');
            document.getElementById(myelement.id).value = str;
            document.getElementById(myelement.id).style.fontSize = '12px';
            $(document).click(function(e) {
                if (e.shiftKey) {
                    document.getElementById(myelement.id).style.fontSize = '14px';
                }
            });
        }

        function dateReformat(mydate, drfString) {
            // yyyy-mmm-dd
            var f_Str = new Array(5)
            for (i = 0; i < 5; i++) {
                f_Str[i] = ""
            }

            var count = 0
            for (i = 0; i < drfString.length; i++) {

                var x = drfString.charCodeAt(i)
                var nextx = drfString.charCodeAt(i + 1)

                var letter = x > 64 && x < 123
                var nextletter = nextx > 64 && nextx < 123

                switch (true) {
                    case letter:
                        f_Str[count] = f_Str[count] + drfString[i]
                        if (nextletter == false) {
                            count = count + 1
                        }
                        break;


                    case !letter:
                        f_Str[count] = f_Str[count] + drfString[i]
                        if (nextletter == true) {
                            count = count + 1
                        }
                        break;


                    default:
                        alert("Neither")

                }

            }

            //Today's date
            var month = new Array(11);
            month[0] = "Jan";
            month[1] = "Feb";
            month[2] = "Mar";
            month[3] = "Apr";
            month[4] = "May";
            month[5] = "Jun";
            month[6] = "Jul";
            month[7] = "Aug";
            month[8] = "Sep";
            month[9] = "Oct";
            month[10] = "Nov";
            month[11] = "Dec";

            var today = new Date(mydate.value);
            today.setTime(today.getTime() + (today.getTimezoneOffset() * 60 * 1000))
            if (isNaN(today)) {
                return false
            }
            var dd = today.getDate();
            var mm = today.getMonth() + 1; //January is 0!
            var mmm = month[today.getMonth()]
            var yy = today.getFullYear().toString().substr(-2);
            var yyyy = today.getFullYear();


            if (dd < 10) {
                dd = '0' + dd
            }
            if (mm < 10) {
                mm = '0' + mm
            }
            var today = eval(f_Str[0]) + f_Str[1] + eval(f_Str[2]) + f_Str[3] + eval(f_Str[4]); //change this manually
            document.getElementById(mydate.id).value = today;

            return today
        }


        //---Start Float Box
        function openNav() {
            window.resizeTo(1100, 900)
            document.getElementById("mySidenavGen2").style.width = "250px";
        }

        function closeNav() {
            document.getElementById('mySidenavGen2').style.width = '0';
        }

        function updateReq(myelement, mytype) {
            //alert(myelement)
            if (myelement == 'Clear All') {
                for (i = 48; i < 51; i++) {
                    g = '#gen_inputId' + i
                    //alert(g)
                    $(g).val('')
                }
            }

            for (i = 48; i < 51; i++) {
                g = '#gen_inputId' + i
                t = '#gen_inputId' + mytype
                //alert(t)
                if ($(g).val() == '' && myelement != 'Clear All') {
                    $(g).val(myelement)
                    $(t).click()
                    return
                }
            }
        }
        //---END Float Box


        function fixSin(elem, myexp, myexp2) {
            var e = document.getElementById(elem.id).value
            //alert(e)
            if (e.indexOf('-') > -1 || e.indexOf(' ') > -1) {
                //alert(e)
                return
            }
            //**convert string of numbers to 999-999-999 format
            //myRe = "/(\\d)(?=(\\d\\d\\d)+(?!\\d))/g, '$1-'"
            var myRe = new RegExp(myexp)
            var myRe2 = myexp2
            //var newsin = e.replace(/(\d)(?=(\d\d\d)+(?!\d))/g, '$1-');
            var newsin = e.replace(myRe, myRe2);
            document.getElementById(elem.id).value = newsin
        }

        function SaveHTML(eformname) {
            var blob = new Blob([$("html").html()], {
                type: "text/html;charset=utf-8"
            });
            saveAs(blob, eformname); //saveAs() defined in FileSaver.js
        }

        function LinkXBoxfunction(e,msg) {
            var res = "LinkedTo-" + e.id + "-"
            var x = '[title*="' + res + '"]'
            var valAll = document.querySelectorAll(x)

            for (i = 0; i < valAll.length; i++) {
                $(valAll[i]).css('background-color', '')
                if(msg){
                $(valAll[i]).val(msg)
                }
                if (e.value != "X") {
                    var valAll = document.querySelectorAll(x)
                    $(valAll[i]).css('background-color', 'lightyellow')
                    $(valAll[i]).val("")
                }
            }
        }

        function LinkTextBoxfunction(e) {
            var y = e.title.split("-")
            document.getElementById(y[1]).title = e.value
            document.getElementById(y[1]).value = "X"
            if(!e.value){
            document.getElementById(y[1]).value = ""
            }
        }

        function stretchy(e) {
            var width = e.parentNode.offsetWidth
            e.onfocus = function() {
                e.parentNode.style.width = (e.value.length + 10) * 8;
            };
            e.onkeyup = function() {
                e.parentNode.style.width = (e.value.length + 10) * 8;
            };
            e.onblur = function() {
                e.parentNode.style.width = width;
                LinkTextBoxfunction(e)
            };
        }

        function copyField(e) {
            e.select();
            document.execCommand('copy');
            $(e).css('background-color', 'pink');
        }

        function copyToSubject(myelement) {
            text = myelement.value;
            $('#subject').val($('#subject').val()+text+" ");
        }

        function textToSubject(text) {
            $('#subject').val($('#subject').val()+text+" ");
        }
    </script>
</head>
<body onload="init();">
    <!--Float bar Main Gen-->
    <div id="mySidenavGen" class="sidenav DoNotPrint">
    </div>
    <!--End Float bar Main Gen-->
    <div id="main_container">
        <div id="eform_view_wrapper">
            <div id="eform_container" class="toSource">
                <form id="inputForm" action="get">
                    <div id="BottomButtons" class="DoNotPrint">
                        <!-- Form Control Buttons -->
						<label for="subject"><fmt:message key="eform.visual.editor.label.subject"/></label>
                        <input id="subject" name="subject" style="width: 220px;" type="text">
                        <input id="SubmitButton" name="SubmitButton" onclick="onEformSubmit();" type="button" value="<fmt:message key="eform.visual.editor.button.submit"/>">
                        <input id="PrintButton" name="PrintButton" onclick="onEformPrint()" type="button" value="<fmt:message key="eform.visual.editor.button.print"/>">
                        <input id="PrintSubmitButton" name="PrintSubmitButton" onclick="onEformPrintSubmit();" type="button" value="<fmt:message key="eform.visual.editor.button.printAndSubmit"/>">
                    </div>
                </form>
            </div>
        </div>
        <div id="control">
            <div id="control_menu_1">
                <h3><fmt:message key="eform.visual.editor.heading.loadExistingEForm"/></h3>
                <div id="control_menu_1-load" class="flexV">
                </div>
                <h3><fmt:message key="eform.visual.editor.heading.pageSetup"/></h3>
                <div id="control_menu_1-page_setup" class="flexV">
                </div>
                <h3><fmt:message key="eform.visual.editor.heading.formBuilding"/></h3>
                <div id="control_menu_1-placement" class="flexV gen-allow_drag">
                </div>
                <h3><fmt:message key="eform.visual.editor.heading.formStylization"/></h3>
                <div id="control_menu_1-stylize">
                </div>
                <h3><fmt:message key="eform.visual.editor.heading.finalize"/></h3>
                <div id="control_menu_1-finalize">
                </div>
            </div>
        </div>
    </div>
<div id="dialog-form" title="<fmt:message key="eform.visual.editor.dialog.attachNewFunction"/>">
  <p class="validateTips"><fmt:message key="eform.visual.editor.dialog.functionParticularsPrefix"/><span id="typeFlag"></span><fmt:message key="eform.visual.editor.dialog.functionParticularsSuffix"/></p>
  <form>
    <fieldseter>
      <label for="event_name"><fmt:message key="eform.visual.editor.label.triggeringEventName"/></label>
      <select name="event_name" id="event_name" style="width:100%; padding: 0.4em; height:2em; background-color: white;">
	  <option val="onclick">onclick</option>
	  <option val="onblur">onblur</option>
	  <option val="onmousedown">onmousedown</option>
	  <option val="onmouseup">onmouseup</option>
	  <option val="onchange">onchange</option>
	  <option val="oninput">oninput</option>
	  <option val="onfocus">onfocus</option>
	  </select><br><br>
      <label for="parameters"><fmt:message key="eform.visual.editor.label.functionParameters"/></label>
      <input type="text" name="parameters" id="parameters" value="this" style="width:100%; padding: 0.4em; height:1em;">
        <br><br>
        <pre id="daFcn"></pre>
      <!-- Allow form submission with keyboard without duplicating the dialog button -->
      <input type="submit" tabindex="-1" style="position:absolute; top:-1000px">
    </fieldset>
  </form>
</div>
<script>
/* Suppress the leave-page confirmation dialog when a logout broadcast signal is received.
 * logout.jsp broadcasts 'logout' on the 'carlos_logout' BroadcastChannel and sets
 * the 'carlos_logout_signal' localStorage key. Either signal releases the dirty flag
 * so the beforeunload handler does not prompt the user, then closes this popup window. */
(function() {
    var logoutChannel = null;
    function handleLogoutSignal() {
        releaseDirtyFlag();
        if (logoutChannel) { try { logoutChannel.close(); } catch(e) {} logoutChannel = null; }
        try { window.close(); } catch(e) {}
    }
    try {
        logoutChannel = new BroadcastChannel('carlos_logout');
        logoutChannel.onmessage = function(e) {
            if (e.data === 'logout') { handleLogoutSignal(); }
        };
    } catch(e) {}
    window.addEventListener('storage', function(e) {
        if (e.key === 'carlos_logout_signal' && e.newValue !== null) { handleLogoutSignal(); }
    });
    window.addEventListener('beforeunload', function() {
        if (logoutChannel) { try { logoutChannel.close(); } catch(e) {} logoutChannel = null; }
    });
}());
</script>
</body>

</html>
