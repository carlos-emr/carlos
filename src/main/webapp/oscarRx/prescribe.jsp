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
    Prescription Writing Interface

    Purpose:
    This JSP provides the prescription writing interface for healthcare providers to create and manage
    patient prescriptions. It handles drug selection, dosage instructions, special instructions,
    indication coding (ICD-9), allergy alerts, drug interactions, and prescription printing.

    Features:
    - Drug name entry with ATC code tracking
    - Indication/diagnosis selection using ICD-9 coding system
    - Dosage instructions with instruction history lookup
    - Special instructions with autocomplete functionality
    - Quantity/Mitte specification
    - Allergy alert integration
    - Drug interaction checking (major, moderate, minor, unknown)
    - Favorites management for common prescriptions
    - Support for custom drugs and branded medications
    - Patient compliance tracking
    - Long-term and short-term prescription designation

    Parameters:
    - listRxDrugs: List<RxPrescriptionData.Prescription> - List of prescription objects to display
    - Each prescription contains: drug name, GCN code, ATC code, instructions, special instructions,
      dates (written, start, last refill), frequency, route, duration, PRN status, repeats, quantity

    Security:
    - Requires "_rx" write permission via security:oscarSec tag
    - Uses OWASP Encoder for all user input rendering to prevent XSS
    - Session-based authentication and role validation

    @since 2009-09-18
--%>

<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.rx.util.*" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.util.RxUtil" %>
<%@ page import="io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security"%>

<c:set var="ctx" value="${pageContext.request.contextPath}" />
<%
    String roleName$ = (String)session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed=true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_rx" rights="w" reverse="<%=true%>">
	<%authed=false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_rx");%>
</security:oscarSec>
<%
	if(!authed) {
		return;
	}
%>
    <%
List<RxPrescriptionData.Prescription> listRxDrugs=(List)request.getAttribute("listRxDrugs");

        if(listRxDrugs!=null){

    for(RxPrescriptionData.Prescription rx : listRxDrugs ){
         String rand            = Long.toString(rx.getRandomId());
         String instructions    = rx.getSpecial();
         String specialInstruction=rx.getSpecialInstruction();
         String startDate       = RxUtil.DateToString(rx.getRxDate(), "yyyy-MM-dd");
         String writtenDate     = RxUtil.DateToString(rx.getWrittenDate(), "yyyy-MM-dd");
         String lastRefillDate  = RxUtil.DateToString(rx.getLastRefillDate(), "yyyy-MM-dd");
         String gcnCode         = rx.getGCN_SEQNO();//if gcn is 0, rx is customed drug.
         String customName      = rx.getCustomName();
         Boolean patientCompliance  = rx.getPatientCompliance();
         String frequency       = rx.getFrequencyCode();
         String route           = rx.getRoute();
         String durationUnit    = rx.getDurationUnit();
         boolean prn            = rx.getPrn();
         String repeats         = Integer.toString(rx.getRepeat());
         String takeMin         = rx.getTakeMinString();
         String takeMax         = rx.getTakeMaxString();
         Boolean longTerm       = rx.getLongTerm();
         boolean shortTerm		= rx.getShortTerm();
      //   boolean isCustomNote   =rx.isCustomNote();
         String outsideProvOhip = rx.getOutsideProviderOhip();
         String brandName       = rx.getBrandName();
         String ATC             = rx.getAtcCode();
         String ATCcode			= rx.getAtcCode();
         String genericName     = rx.getGenericName();
		 String drugPrescribed  = rx.getDrugPrescribed();

		 if(customName == null) {
			 customName = "";
		 }

         if( genericName == null || genericName.isEmpty() ) {
        	 genericName = "";
         }

		 if(drugPrescribed == null) {
			 drugPrescribed = "";
		 }

		 /*
		  * The alternate name is applies when a generic is being prescribed
		  * and when this is not a custom drug.
		  */
		 String alternatename = genericName;
		 if(customName.isEmpty() && drugPrescribed.trim().equalsIgnoreCase(genericName.trim())) {
			 alternatename = brandName;
		 }

		 if(alternatename == null) {
			 alternatename = "";
		 }

         String dosage = rx.getDosage();
 
         String pickupDate      = RxUtil.DateToString(rx.getPickupDate(), "yyyy-MM-dd");
         String pickupTime      = RxUtil.DateToString(rx.getPickupTime(), "HH:mm");
         String eTreatmentType  = rx.getETreatmentType()!=null ? rx.getETreatmentType() : "";
         String rxStatus        = rx.getRxStatus()!=null ? rx.getRxStatus() : "";		    
         String protocol        = rx.getProtocol()!=null ? rx.getProtocol() : ""; 
		/*  Field not required. Commented out because it may be reactivated in the future. 
         String priorRxProtocol	= rx.getPriorRxProtocol()!=null ? rx.getPriorRxProtocol() : "";
         */
         String drugForm		= rx.getDrugForm();
         //remove from the rerx list
         int DrugReferenceId = rx.getDrugReferenceId();
         
         if( ATCcode == null || ATCcode.trim().length() == 0 ) {
             ATCcode = "";
         }
         
         if(ATC != null && ATC.trim().length()>0)
             ATC="ATC: "+ATC;
         String drugName;

         if("0".equals(gcnCode)){//it's a custom drug
             drugName=customName;
         }else if ( drugPrescribed != null && ! drugPrescribed.isEmpty() ){
             drugName = drugPrescribed;
         } else {
			 drugName = brandName;
         }
	     boolean isSpecInstPresent = (specialInstruction != null && ! "null".equalsIgnoreCase(specialInstruction)&&specialInstruction.trim().length() > 0);

         //for display
         if(drugName==null || "null".equalsIgnoreCase(drugName)) {
	         drugName = "";
         }

         String comment  = rx.getComment();
         if(rx.getComment() == null) {
        	 comment = "";
         }
         Boolean pastMed            = rx.getPastMed();
         boolean dispenseInternal = rx.getDispenseInternal();
         boolean startDateUnknown	= rx.getStartDateUnknown();
         boolean nonAuthoritative   = rx.isNonAuthoritative();
         boolean nosubs = rx.getNosubs();
         String quantity            = rx.getQuantity();
         String quantityText="";
         String unitName=rx.getUnitName();
         if(unitName==null || unitName.equalsIgnoreCase("null") || unitName.trim().length()==0){
             quantityText=quantity;
         }
         else{
             quantityText=quantity+" "+rx.getUnitName();
         }
         String duration        = rx.getDuration();
         String method          = rx.getMethod();
         String outsideProvName = rx.getOutsideProviderName();
         boolean isDiscontinuedLatest = rx.isDiscontinuedLatest();
         String archivedDate="";
         String archivedReason="";
         boolean isOutsideProvider ;
         int refillQuantity=rx.getRefillQuantity();
         int refillDuration=rx.getRefillDuration();
         String dispenseInterval=rx.getDispenseInterval();
         if(isDiscontinuedLatest){
                archivedReason=rx.getLastArchReason();
                archivedDate=rx.getLastArchDate();
         }

          if((outsideProvOhip!=null && !outsideProvOhip.equals("")) || (outsideProvName!=null && !outsideProvName.equals(""))){
             isOutsideProvider=true;
         }
         else{
             isOutsideProvider=false;
         }
         if(route==null || route.equalsIgnoreCase("null")) route="";
                    String methodStr = method;
                    String routeStr = route;
                    String frequencyStr = frequency;
                    String minimumStr = takeMin;
                    String maximumStr = takeMax;
                    String durationStr = duration;
                    String durationUnitStr = durationUnit;
                    String quantityStr = quantityText;
                    String unitNameStr="";
                    if(rx.getUnitName()!=null && !rx.getUnitName().equalsIgnoreCase("null"))
                        unitNameStr=rx.getUnitName();
                    String prnStr="";
                    if(prn) { prnStr="prn"; }

                drugName=drugName.replace("'", "\\'");
                drugName=drugName.replace("\"","\\\"");
                byte[] drugNameBytes = drugName.getBytes("ISO-8859-1");
                drugName= new String(drugNameBytes, "UTF-8");
                String fieldSetId = "set_" + rand;
%>
<%-- i18n variable declarations for this prescription card --%>
<fmt:setBundle basename="oscarResources"/>
<fmt:message key="WriteScript.msgMore" var="i18nMore"/>
<fmt:message key="WriteScript.msgAddtoFavorites" var="i18nAddToFavorites"/>
<fmt:message key="WriteScript.msgName" var="i18nName"/>
<fmt:message key="WriteScript.msgIndication" var="i18nIndication"/>
<fmt:message key="WriteScript.msgSearchDx" var="i18nSearchDx"/>
<fmt:message key="WriteScript.msgShowHideSpecialInstructions" var="i18nShowHideSpecInst"/>
<fmt:message key="WriteScript.msgInstructionExamples" var="i18nInstructionExamples"/>
<fmt:message key="WriteScript.msgInstructionsFieldReference" var="i18nInstructionsFieldRef"/>
<fmt:message key="WriteScript.msgEnterSpecialInstruction" var="i18nEnterSpecInst"/>
<fmt:message key="WriteScript.msgQtyMitte" var="i18nQtyMitte"/>
<fmt:message key="WriteScript.msgIngredient" var="i18nIngredient"/>
<fmt:message key="WriteScript.msgMethod" var="i18nMethod"/>
<fmt:message key="WriteScript.msgRoute" var="i18nRoute"/>
<fmt:message key="WriteScript.msgFrequency" var="i18nFrequency"/>
<fmt:message key="WriteScript.msgMin" var="i18nMin"/>
<fmt:message key="WriteScript.msgMax" var="i18nMax"/>
<fmt:message key="WriteScript.msgPrescribedRefillDuration" var="i18nDuration"/>
<fmt:message key="WriteScript.msgDurationUnit" var="i18nDurationUnit"/>
<fmt:message key="WriteScript.msgSaveChanges" var="i18nSaveChanges"/>
<fmt:message key="WriteScript.msgOHIPNO" var="i18nOHIPNO"/>
<fmt:message key="WriteScript.msgStartDate" var="i18nStartDate"/>
<fmt:message key="WriteScript.msgLastRefillDate" var="i18nLastRefillDate"/>
<fmt:message key="WriteScript.msgWrittenDate" var="i18nWrittenDate"/>
<fmt:message key="WriteScript.msgAddToFavoriteLink" var="i18nAddToFavoriteLink"/>
<fmt:message key="WriteScript.msgRefillDurationError" var="i18nRefillDurationError"/>
<fmt:message key="WriteScript.msgClose" var="i18nClose"/>

<fieldset style="margin-top:2px;" id="<%=fieldSetId%>">
    <a tabindex="-1" href="javascript:void(0);"  style="float:right;margin-left:5px;margin-top:0px;padding-top:0px;" onclick="removePrescribingDrug(<%=fieldSetId%>, <%=DrugReferenceId%>);"><img src='<c:out value="${ctx}/images/close.png"/>' border="0"></a>
    <a tabindex="-1" href="javascript:void(0);"  style="float:right;;margin-left:5px;margin-top:0px;padding-top:0px;" title="${i18nAddToFavorites}" onclick="addFav('<%=rand%>','<%=Encode.forJavaScript(drugName)%>')">F</a>
    <a tabindex="-1" href="javascript:void(0);" style="float:right;margin-top:0px;padding-top:0px;" onclick="var el=document.getElementById('rx_more_<%=rand%>');el.style.display=el.style.display==='none'?'':'none';">  <span id="moreLessWord_<%=rand%>" onclick="updateMoreLess(id)" >${i18nMore}</span> </a>

    <%-- Modern flexbox layout for drug name field - replaces float-based layout for better alignment and responsiveness --%>
    <div style="display:flex;flex-wrap:wrap;align-items:center;gap:5px;margin-bottom:5px;">
        <label style="width:101px;flex-shrink:0;" title="<%=Encode.forHtmlAttribute(ATC)%>" >${i18nName}:</label>
        <input type="hidden" name="atcCode" value="<%=Encode.forHtmlAttribute(ATCcode)%>" />
        <input tabindex="-1" type="text" id="drugName_<%=rand%>"  name="drugName_<%=rand%>"  size="30" <%if("0".equals(gcnCode)){%> onkeyup="saveCustomName(this);" value="<%=Encode.forHtmlAttribute(drugName)%>"<%} else{%> value="<%=Encode.forHtmlAttribute(drugName)%>"  onchange="changeDrugName('<%=rand%>','<%=Encode.forJavaScript(drugName)%>');" <%}%> TITLE="<%=Encode.forHtmlAttribute(drugName)%>"/>&nbsp;<span id="inactive_<%=rand%>" style="color:red;"></span>
    </div>

	<!-- Allergy Alert Table-->

	<table style="margin-top:5px; margin-bottom:5px; border-collapse: collapse; display: none; width:100%;" id="alleg_tbl_<%=rand%>">
		<tr>
			<td style="background-color:#CCCCCC;height:10px;width:100%;">
				<!--spacer cell-->
			</td>
		</tr>
	
		<tr>
			<td >    
	    		<span id="alleg_<%=rand%>" style="font-size:11px;"></span>
			</td>
		</tr>
	</table>

    <%-- Splice in the Indication field --%>
    <%-- Modern flexbox layout for indication field - ensures consistent label width and field alignment --%>
    <div style="display:flex;flex-wrap:wrap;align-items:center;gap:5px;margin-bottom:5px;">
        <label style="width:101px;flex-shrink:0;" for="jsonDxSearch_<%=rand%>" >${i18nIndication}: </label>
        <select name="codingSystem_<%=rand%>" id="codingSystem_<%=rand%>" >
            <option value="icd9">icd9</option>
            <%-- option value="limitUse">Limited Use</option --%>
        </select>
        <input type="hidden" name="reasonCode_<%=rand%>" id="codeTxt_<%=rand%>" />
        <input type="text" class="codeTxt" name="jsonDxSearch_<%=rand%>" id="jsonDxSearch_<%=rand%>" placeholder="${i18nSearchDx}" />
    </div>
     <%-- Splice in the Indication field --%>

    <%-- Modern flexbox layout for instructions field - improves alignment and collapsible special instructions section --%>
    <div style="margin-bottom:5px;">
        <div style="display:flex;align-items:center;gap:5px;">
            <a tabindex="-1" href="javascript:void(0);" onclick="showHideSpecInst('siAutoComplete_<%=rand%>')" style="width:101px;flex-shrink:0;">${i18nShowHideSpecInst}: </a>
            <input type="text" id="instructions_<%=Encode.forHtmlAttribute(rand)%>" name="instructions_<%=Encode.forHtmlAttribute(rand)%>" onkeypress="handleEnter(this,event);"
                   value="<%=Encode.forHtmlAttribute(instructions)%>" size="60" onchange="parseIntr(this);"/><a href="javascript:void(0);" tabindex="-1"
                                                                                   onclick="displayMedHistory('<%=Encode.forJavaScriptAttribute(rand)%>');"
                                                                                   style="color:red;font-size:13pt;vertical-align:super;text-decoration:none"
                                                                                   title="${i18nInstructionExamples}"><b>*</b></a>
            <a href="javascript:void(0);" tabindex="-1" onclick="displayInstructions('<%=Encode.forJavaScriptAttribute(rand)%>');"><img
                    src="<%= request.getContextPath() %>/images/icon_help_sml.gif" border="0" title="${i18nInstructionsFieldRef}" /></a>
            <span id="major_<%=Encode.forHtmlAttribute(rand)%>" style="display:none;background-color:red"></span>&nbsp;<span id="moderate_<%=Encode.forHtmlAttribute(rand)%>"
                                                                                                    style="display:none;background-color:orange"></span>&nbsp;<span
                id='minor_<%=Encode.forHtmlAttribute(rand)%>' style="display:none;background-color:yellow;"></span>&nbsp;<span id='unknown_<%=Encode.forHtmlAttribute(rand)%>'
                                                                                                      style="display:none;background-color:#B1FB17"></span>
        </div>
        <div id="siAutoComplete_<%=Encode.forHtmlAttribute(rand)%>" <%if (isSpecInstPresent) {%> style="overflow:visible;margin-top:1px;margin-bottom:10px"<%} else {%>
             style="overflow:visible;display:none;margin-top:1px;margin-bottom:10px"<%}%> >
            <label style="float:left;width:106px;">&nbsp;&nbsp;</label><input id="siInput_<%=Encode.forHtmlAttribute(rand)%>" type="text" size="60"
                                                                         <%if(!isSpecInstPresent) {%>style="color:gray; width:auto"
                                                                         value="${i18nEnterSpecInst}" <%} else {%>
                                                                         style="color:black; width:auto"
                                                                         value="<%=Encode.forHtmlAttribute(specialInstruction)%>" <%}%>
                                                                         onblur="changeText('siInput_<%=Encode.forJavaScriptAttribute(rand)%>');updateSpecialInstruction('siInput_<%=Encode.forJavaScriptAttribute(rand)%>');"
                                                                         onfocus="changeText('siInput_<%=Encode.forJavaScriptAttribute(rand)%>');">
            <div id="siContainer_<%=Encode.forHtmlAttribute(rand)%>" style="float:right">
            </div>
            <div style="clear:both;"></div>
        </div>
        <br>
    </div>
		<div>
        <label id="labelQuantity_<%=Encode.forHtmlAttribute(rand)%>" style="float:left;width:80px;">${i18nQtyMitte}:</label><input
            size="8" <%if (rx.isCustomNote()) {%> disabled <%}%> type="text" id="quantity_<%=Encode.forHtmlAttribute(rand)%>"
            name="quantity_<%=Encode.forHtmlAttribute(rand)%>" value="<%=Encode.forHtmlAttribute(quantityText)%>" onblur="updateQty(this);"/>
        <label style=""><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgRepeats"/>:</label><input type="text" size="5" id="repeats_<%=Encode.forHtmlAttribute(rand)%>"  <%if (rx.isCustomNote()) {%>
                                               disabled <%}%> name="repeats_<%=Encode.forHtmlAttribute(rand)%>" value="<%=Encode.forHtmlAttribute(repeats)%>"
                                               onInput="updateLongTerm('<%=Encode.forJavaScriptAttribute(rand) %>',this)"
                                               onblur="updateProperty(this.id)"/>
		</div>
    <div id="medTerm_<%=Encode.forHtmlAttribute(rand)%>">
        <label><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgLongTermMedication"/>: </label>
			<span>
				<label for="longTermY_<%=Encode.forHtmlAttribute(rand)%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgYes"/> </label>
			  	<input type="radio" id="longTermY_<%=Encode.forHtmlAttribute(rand)%>" name="longTerm_<%=Encode.forHtmlAttribute(rand)%>" value="yes"
                       class="med-term" <%if (longTerm != null && longTerm) {%> checked="checked" <%}%>
                       onChange="updateShortTerm('<%=Encode.forJavaScriptAttribute(rand)%>',false)"/>
			  	
			  	<label for="longTermN_<%=Encode.forHtmlAttribute(rand)%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgNo"/> </label>
			  	<input type="radio" id="longTermN_<%=Encode.forHtmlAttribute(rand)%>" name="longTerm_<%=Encode.forHtmlAttribute(rand)%>" value="no"
                       class="med-term" <%if (longTerm != null && !longTerm) {%> checked="checked" <%}%>
                       onChange="updateShortTerm('<%=Encode.forJavaScriptAttribute(rand)%>',true)"/>
			  	
			  	<label for="longTermE_<%=Encode.forHtmlAttribute(rand)%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgUnset"/> </label>
			  	<input type="radio" id="longTermE_<%=Encode.forHtmlAttribute(rand)%>" name="longTerm_<%=Encode.forHtmlAttribute(rand)%>" value="unset"
                       class="med-term" <%if (longTerm == null) {%> checked="checked" <%}%>
                       onChange="updateShortTerm('<%=Encode.forJavaScriptAttribute(rand)%>',false)"/>
				<div style="display:none">
					<label for="shortTerm_<%=Encode.forHtmlAttribute(rand)%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgSortTermMedication"/> </label>
	        		<input type="checkbox" id="shortTerm_<%=Encode.forHtmlAttribute(rand)%>" name="shortTerm_<%=Encode.forHtmlAttribute(rand)%>"
                           class="med-term" <%if (shortTerm) {%> checked="checked" <%}%> />
	        	</div>
	        </span>
		</div>
        
        <%if(genericName!=null&&!genericName.equalsIgnoreCase("null")){%>
        <div><a>${i18nIngredient}:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<%=genericName%></a></div><%}%>
       <div class="rxStr" title="not what you mean?" >
           <a tabindex="-1" href="javascript:void(0);" onclick="focusTo('method_<%=rand%>')">${i18nMethod}:</a><a   id="method_<%=rand%>" onclick="focusTo(this.id)" onfocus="lookEdittable(this.id)" onblur="lookNonEdittable(this.id);updateProperty(this.id);"><%=methodStr%></a>
           <a tabindex="-1" href="javascript:void(0);" onclick="focusTo('route_<%=rand%>')">${i18nRoute}:</a><a id="route_<%=rand%>" onclick="focusTo(this.id)" onfocus="lookEdittable(this.id)" onblur="lookNonEdittable(this.id);updateProperty(this.id);"> <%=routeStr%></a>
           <a tabindex="-1" href="javascript:void(0);" onclick="focusTo('frequency_<%=rand%>')">${i18nFrequency}:</a><a  id="frequency_<%=rand%>" onclick="focusTo(this.id) " onfocus="lookEdittable(this.id)" onblur="lookNonEdittable(this.id);updateProperty(this.id);"> <%=frequencyStr%></a>
           <a tabindex="-1" href="javascript:void(0);" onclick="focusTo('minimum_<%=rand%>')">${i18nMin}:</a><a  id="minimum_<%=rand%>" onclick="focusTo(this.id) " onfocus="lookEdittable(this.id)" onblur="lookNonEdittable(this.id);updateProperty(this.id);"> <%=minimumStr%></a>
           <a tabindex="-1" href="javascript:void(0);" onclick="focusTo('maximum_<%=rand%>')">${i18nMax}:</a><a id="maximum_<%=rand%>" onclick="focusTo(this.id) " onfocus="lookEdittable(this.id)" onblur="lookNonEdittable(this.id);updateProperty(this.id);"> <%=maximumStr%></a>
           <a tabindex="-1" href="javascript:void(0);" onclick="focusTo('duration_<%=rand%>')">${i18nDuration}:</a><a  id="duration_<%=rand%>" onclick="focusTo(this.id) " onfocus="lookEdittable(this.id)" onblur="lookNonEdittable(this.id);updateProperty(this.id);"> <%=durationStr%></a>
           <a tabindex="-1" href="javascript:void(0);" onclick="focusTo('durationUnit_<%=rand%>')">${i18nDurationUnit}:</a><a  id="durationUnit_<%=rand%>" onclick="focusTo(this.id) " onfocus="lookEdittable(this.id)" onblur="lookNonEdittable(this.id);updateProperty(this.id);"> <%=durationUnitStr%></a>
           <a tabindex="-1" >${i18nQtyMitte}:</a><a tabindex="-1" id="quantityStr_<%=rand%>"> <%=quantityStr%></a>
           <a> </a><a tabindex="-1" id="unitName_<%=rand%>"> </a>
           <a> </a><a tabindex="-1" href="javascript:void(0);" id="prn_<%=rand%>" onclick="setPrn('<%=rand%>');updateProperty('prnVal_<%=rand%>');"><%=prnStr%></a>
           <input id="prnVal_<%=rand%>"  style="display:none" <%if(prnStr.trim().length()==0){%>value="false"<%} else{%>value="true" <%}%> />
           <input id="rx_save_updates_<%=rand%>" type="button" value="${i18nSaveChanges}" onclick="saveLinks('<%=rand%>')"/>
       </div>
       <div id="rx_more_<%=rand%>" style="display:none;padding:2px;">
        <div>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgPrescribedRefill"/>:
       	  &nbsp;
            <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgPrescribedRefillDuration"/>
            <input type="text" size="6" id="refillDuration_<%=rand%>" name="refillDuration_<%=rand%>"
                   value="<%=refillDuration%>"
                   onchange="var errEl=document.getElementById('refillDurationError_<%=rand%>');var v=Number(this.value);if(Number.isNaN(v)||v<0){errEl.classList.remove('d-none');this.focus();return false;}errEl.classList.add('d-none');"/><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgPrescribedRefillDurationDays"/>
            <div id="refillDurationError_<%=rand%>" class="alert alert-danger d-none" role="alert" style="margin-top:4px;padding:6px 10px;">
                <button type="button" class="btn-close float-end" style="font-size:0.75rem;" onclick="document.getElementById('refillDurationError_<%=rand%>').classList.add('d-none');" aria-label="${i18nClose}"></button>
                ${i18nRefillDurationError}
            </div>
       	  &nbsp;       	  
            <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgPrescribedRefillQuantity"/>
       	  <input type="text" size="6" id="refillQuantity_<%=rand%>" name="refillQuantity_<%=rand%>" value="<%=refillQuantity%>" />
       	  </div><div> 
    	  
            <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgPrescribedDispenseInterval"/>
       	  <input type="text" size="6" id="dispenseInterval_<%=rand%>" name="dispenseInterval_<%=rand%>" value="<%=dispenseInterval%>" />
       	  </div>
       	  
	     <%if(CarlosProperties.getInstance().getProperty("rx.enable_internal_dispensing","false").equals("true")) {%>  
	       <div>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgDispenseInternal"/>
			  <input type="checkbox" name="dispenseInternal_<%=rand%>" id="dispenseInternal_<%=rand%>" <%if(dispenseInternal) {%> checked="checked" <%}%> />
      	 </div>
      	 <% } %>
		<div>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgPrescribedByOutsideProvider"/>
            <input type="checkbox" id="ocheck_<%=rand%>" name="ocheck_<%=rand%>"
                   onclick="var el=document.getElementById('otext_<%=rand%>');el.style.display=el.style.display==='none'?'':'none';" <%if (isOutsideProvider) {%> checked="checked" <%
                } else {
                }
            %>/>
            <div id="otext_<%=rand%>" <%if(isOutsideProvider){%>style="display:table;padding:2px;"
                 <%}else{%>style="display:none;padding:2px;"<%}%> >
                <b><label style="float:left;width:80px;">${i18nName}:</label></b> <input type="text"
                                                                                   id="outsideProviderName_<%=rand%>"
                                                                                   name="outsideProviderName_<%=rand%>" <%if (outsideProvName != null) {%>
                                                                                   value="<%=Encode.forHtmlAttribute(outsideProvName)%>"<%} else {%>
                                                                                   value=""<%}%> />
                <b><label style="width:80px;">${i18nOHIPNO}:</label></b> <input type="text" id="outsideProviderOhip_<%=rand%>"
                                                                          name="outsideProviderOhip_<%=rand%>"
                                                                          <%if(outsideProvOhip!=null){%>value="<%=Encode.forHtmlAttribute(outsideProvOhip)%>"<%} else {%>
                                                                          value=""<%}%>/>
            </div>
          </div>
        <div>

            <label for="pastMedSelection" title="Medications taken at home that were previously ordered."><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgPastMedication"/></label>
        
        <span id="pastMedSelection">
        	<label for="pastMedY_<%=rand%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgYes"/></label>
            <input  type="radio" value="yes" name="pastMed_<%=rand%>" id="pastMedY_<%=rand%>" <%if(pastMed != null && pastMed) {%> checked="checked" <%}%>  />
            
            <label for="pastMedN_<%=rand%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgNo"/></label>
            <input  type="radio" value="no" name="pastMed_<%=rand%>" id="pastMedN_<%=rand%>" <%if(pastMed != null && ! pastMed) {%> checked="checked" <%}%>  />
            
            <label for="pastMedE_<%=rand%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgUnknown"/></label>
            <input  type="radio" value="unset" name="pastMed_<%=rand%>" id="pastMedE_<%=rand%>" <%if(pastMed == null) {%> checked="checked" <%}%>  />
         </span>         
	</div><div>
	
            <label for="patientCompliantSelection"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgPatientCompliance"/>:</label>
	<span id="patientCompliantSelection">
         <label for="patientComplianceY_<%=rand%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgYes"/></label>
            <input type="radio" value="yes" name="patientCompliance_<%=rand%>" id="patientComplianceY_<%=rand%>" <%if(patientCompliance!=null && patientCompliance) {%> checked="checked" <%}%> />

          <label for="patientComplianceN_<%=rand%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgNo"/></label>
            <input type="radio" value="no" name="patientCompliance_<%=rand%>" id="patientComplianceN_<%=rand%>" <%if(patientCompliance!=null && !patientCompliance) {%> checked="checked" <%}%> />
	
		<label for="patientComplianceE_<%=rand%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgUnset"/></label>
            <input type="radio" value="unset" name="patientCompliance_<%=rand%>" id="patientComplianceE_<%=rand%>" <%if(patientCompliance==null) {%> checked="checked" <%}%> />
    </span>
	</div><div>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgNonAuthoritative"/>
            <input type="checkbox" name="nonAuthoritativeN_<%=rand%>" id="nonAuthoritativeN_<%=rand%>" <%if(nonAuthoritative) {%> checked="checked" <%}%> />
    </div><div>
    
            <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgSubNotAllowed"/>
    		<input type="checkbox" name="nosubs_<%=rand%>" id="nosubs_<%=rand%>" <%if(nosubs) {%> checked="checked" <%}%> />
    </div><div>

        <label style="float:left;width:80px;">${i18nStartDate}:</label>
           <input type="text" id="rxDate_<%=rand%>" name="rxDate_<%=rand%>" value="<%=startDate%>" <%if(startDateUnknown) {%> disabled="disabled" <%}%>/>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgUnknown"/>
           <input  type="checkbox" name="startDateUnknown_<%=rand%>" id="startDateUnknown_<%=rand%>" <%if(startDateUnknown) {%> checked="checked" <%}%> onclick="toggleStartDateUnknown('<%=rand%>');"/>
           
           </div><div>
	<label style="">${i18nLastRefillDate}:</label>
           <input type="text" id="lastRefillDate_<%=rand%>"  name="lastRefillDate_<%=rand%>" value="<%=lastRefillDate%>" />
	</div><div>
        <label style="float:left;width:80px;">${i18nWrittenDate}:</label>
           <input type="text" id="writtenDate_<%=rand%>"  name="writtenDate_<%=rand%>" value="<%=writtenDate%>" />
           <a href="javascript:void(0);" style="float:right;margin-top:0px;padding-top:0px;" onclick="addFav('<%=rand%>','<%=Encode.forJavaScript(drugName)%>');return false;">${i18nAddToFavoriteLink}</a>
       
           </div><div>
           			           
            <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgProtocolReference"/>:
           <input type="text" id="protocol_<%=rand%>"  name="protocol_<%=rand%>" value="<%=protocol%>" />          

           <%--  OMD Revalidation: field not required currently. Commented out as this may be used again in the future. 
          <label style="">Prior Rx Protocol:</label>
           <input type="text" id="protocol_<%=rand%>"  name="priorRxProtocol_<%=rand%>" value="<%=priorRxProtocol%>" />
            --%>
            
           </div><div>
           
            <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgPickUpDate"/>:
            <input type="text" id="pickupDate_<%=rand%>" name="pickupDate_<%=rand%>" value="<%=pickupDate%>"
                   onchange="if (!isValidDate(this.value)) {this.value=null}"/>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgPickUpTime"/>:
            <input type="text" id="pickupTime_<%=rand%>" name="pickupTime_<%=rand%>" value="<%=pickupTime%>"
                   onchange="if (!isValidTime(this.value)) {this.value=null}"/>
        </div>
        <div>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgComment"/>:
           <input type="text" id="comment_<%=rand%>" name="comment_<%=rand%>" value="<%=comment%>" size="60"/>
           </div><div>  
            <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgETreatmentType"/>:
           <select name="eTreatmentType_<%=rand%>">
           		<option>--</option>
                <option value="CHRON" <%=eTreatmentType.equals("CHRON") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgETreatment.Continuous"/></option>
                <option value="ACU" <%=eTreatmentType.equals("ACU") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgETreatment.Acute"/></option>
                <option value="ONET" <%=eTreatmentType.equals("ONET") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgETreatment.OneTime"/></option>
                <option value="PRNL" <%=eTreatmentType.equals("PRNL") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgETreatment.LongTermPRN"/></option>
                <option value="PRNS" <%=eTreatmentType.equals("PRNS") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgETreatment.ShortTermPRN"/></option>
            </select>
           <select name="rxStatus_<%=rand%>">
           		<option>--</option>
                <option value="New" <%=rxStatus.equals("New") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgRxStatus.New"/></option>
                <option value="Active" <%=rxStatus.equals("Active") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgRxStatus.Active"/></option>
                <option value="Suspended" <%=rxStatus.equals("Suspended") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgRxStatus.Suspended"/></option>
                <option value="Aborted" <%=rxStatus.equals("Aborted") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgRxStatus.Aborted"/></option>
                <option value="Completed" <%=rxStatus.equals("Completed") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgRxStatus.Completed"/></option>
                <option value="Obsolete" <%=rxStatus.equals("Obsolete") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgRxStatus.Obsolete"/></option>
                <option value="Nullified" <%=rxStatus.equals("Nullified") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgRxStatus.Nullified"/></option>
           </select>
                </div><div>                
            <fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgDrugForm"/>:
                <%if(rx.getDrugFormList()!=null && rx.getDrugFormList().indexOf(",")!=-1){ %>
                <select name="drugForm_<%=rand%>">
                	<%
                		String[] forms = rx.getDrugFormList().split(",");
                		for(String form:forms) {
                	%>
                		<option value="<%=form%>" <%=form.equals(drugForm)?"selected":"" %>><%=form%></option>
                	<% } %>
                </select>    
				<%} else { %>
					<%=drugForm%>
				<% } %>

       			</div>

        </div>
           
           <div id="renalDosing_<%=rand%>" ></div>
           <div id="luc_<%=rand%>" style="margin-top:2px;" >
            </div> 
           
           <oscar:oscarPropertiesCheck property="RENAL_DOSING_DS" value="yes">
            <script type="text/javascript">getRenalDosingInformation('renalDosing_<%=rand%>','<%=rx.getAtcCode()%>');</script>
            </oscar:oscarPropertiesCheck>
           <oscar:oscarPropertiesCheck property="billregion" value="ON" >
               <script type="text/javascript">getLUC('luc_<%=rand%>','<%=rand%>','<%=rx.getRegionalIdentifier()%>');</script>
            </oscar:oscarPropertiesCheck>
			
</fieldset>
<style type="text/css" >
/*
 * jQuery UI Autocomplete 1.8.18
 *
 * Copyright 2011, AUTHORS.txt (http://jqueryui.com/about)
 * Dual licensed under the MIT or GPL Version 2 licenses.
 * http://jquery.org/license
 *
 * http://docs.jquery.com/UI/Autocomplete#theming
 */
.ui-autocomplete { position: absolute; cursor: default; }	

/* workarounds */
* html .ui-autocomplete { width:1px; } /* without this, the menu expands to 100% in IE6 */

/*
 * jQuery UI Menu 1.8.18
 *
 * Copyright 2010, AUTHORS.txt (http://jqueryui.com/about)
 * Dual licensed under the MIT or GPL Version 2 licenses.
 * http://jquery.org/license
 *
 * http://docs.jquery.com/UI/Menu#theming
 */
.ui-menu {
	list-style:none;
	padding: 2px;
	margin: 0;
	display:block;
	float: left;
}
.ui-menu .ui-menu {
	margin-top: -3px;
}
.ui-menu .ui-menu-item {
	margin:0;
	padding: 0;
	zoom: 1;
	float: left;
	clear: left;
	width: 100%;
}
.ui-menu .ui-menu-item a {
	text-decoration:none;
	display:block;
	padding:.2em .4em;
	line-height:1.5;
	zoom:1;
}
.ui-menu .ui-menu-item a.ui-state-hover,
.ui-menu .ui-menu-item a.ui-state-active {
	font-weight: normal;
	margin: -1px;
}


	.ui-autocomplete-loading { 
        background: white url('<%= request.getContextPath() %>/images/ui-anim_basic_16x16.gif') right center no-repeat;
	} 
	.ui-autocomplete {
		max-height: 200px;
		overflow-y: auto;
		overflow-x: hidden;
		background-color: whitesmoke;
			border:#ccc thin solid;
	}

	.ui-menu .ui-menu {
	
		background-color: whitesmoke;
	}
	
	.ui-menu .ui-menu-item a {
		border-bottom:white thin solid;
	}
	.ui-menu .ui-menu-item a.ui-state-hover,
	.ui-menu .ui-menu-item a.ui-state-active {
		background-color: yellow;
	}

</style>
<script type="text/javascript">
       jQuery("document").ready(function() {
    	   
                jQuery('#rx_save_updates_<%=rand%>').hide();

				var idindex = "";
               jQuery( "input[id*='jsonDxSearch']" ).autocomplete({	
       			source: function(request, response) {
       				
       				var elementid = this.element[0].id;
	   				if( elementid.indexOf("_") > 0 ) {
	   					idindex = "_" + elementid.split("_")[1];
	   				}
       				       				
       				jQuery.ajax({
       				    url: ctx + "/dxCodeSearchJSON.do",
       				    type: 'POST',
       				    data: 'method=search' + ( jQuery( '#codingSystem' + idindex ).find(":selected").val() ).toUpperCase()
       				    				+ '&keyword=' 
       				    				+ jQuery( "#jsonDxSearch" + idindex ).val(),
       				  	dataType: "json",
       				    success: function(data) {
       						response(jQuery.map( data, function(item) { 
       							return {
       								label: item.description.trim() + ' (' + item.code + ')',
       								value: item.code,
       								id: item.id
       							};
       				    	}))
       				    }			    
       				})					  
       			},
       			delay: 100,
       			minLength: 2,
       			select: function( event, ui) {
       				event.preventDefault();
       				jQuery( "#jsonDxSearch" + idindex ).val(ui.item.label);
       				jQuery( '#codeTxt' + idindex ).val(ui.item.value);
       			},
       			focus: function(event, ui, idindex) {
       		        event.preventDefault();
       		        jQuery( "#jsonDxSearch" + idindex ).val(ui.item.label);
       		    },
       			open: function() {
       				jQuery( this ).removeClass( "ui-corner-all" ).addClass( "ui-corner-top" );
       			},
       			close: function() {
       				jQuery( this ).removeClass( "ui-corner-top" ).addClass( "ui-corner-all" );
       			}
       		})		

		<%-- Autocomplete for instructions field - draws from med history (same source as displayMedHistory).
		     Controlled by AUTOCOMPLETE_RX_INSTRUCTIONS property (default: true). --%>
		<% if (CarlosProperties.getInstance().isPropertyActive("AUTOCOMPLETE_RX_INSTRUCTIONS")) { %>
			jQuery("input[id^='instructions_']").autocomplete({
				source: function(request, response) {
					var randId = this.element[0].id.split("_")[1];
					jQuery.ajax({
						url: "${ctx}/oscarRx/WriteScript.do?parameterValue=getInstructionsAutocomplete",
						type: "POST",
						data: "randomId=" + randId + "&term=" + encodeURIComponent(request.term),
						dataType: "json",
						success: function(data) {
							response(jQuery.map(data.results, function(item) {
								return { label: item, value: item };
							}));
						},
						error: function() {
							response([]);
						}
					});
				},
				minLength: 1,
				delay: 200,
				select: function(event, ui) {
					event.preventDefault();
					jQuery(this).val(ui.item.value);
					parseIntr(this);
				},
				open: function() {
					jQuery(this).removeClass("ui-corner-all").addClass("ui-corner-top");
				},
				close: function() {
					jQuery(this).removeClass("ui-corner-top").addClass("ui-corner-all");
				}
			});
		<% } %>

		<%--   if number of refills more than 0 set long term flag.  May not be OMD but is convenient --%>
			jQuery("#repeats_<%=rand%>").keyup(function(){
            	var rand = this.id.split("_")[1];
            	var repeatsVal = this.value;
            	if(repeatsVal>0){
            		jQuery("#longTermY_" + rand).prop("checked", true).trigger('change');
            	}
            });
      
		  
       });
</script>


        <script type="text/javascript">
            document.getElementById('drugName_'+'<%=rand%>').value=decodeURIComponent(encodeURIComponent('<%=Encode.forJavaScript(drugName)%>'));
            calculateRxData('<%=rand%>');
            handleEnter=function handleEnter(inField, ev){
                var charCode;
                if(ev && ev.which)
                    charCode=ev.which;
                else if(window.event){
                    ev=window.event;
                    charCode=ev.keyCode;
                }
                var id=inField.id.split("_")[1];
                if(charCode==13)
                    showHideSpecInst('siAutoComplete_'+id);
            }
            showHideSpecInst=function showHideSpecInst(elementId){
              var el = document.getElementById(elementId);
              if(el.style.display==='none'){
                  el.classList.remove('carlos-collapsed');
                  el.style.display='';
              }else{
                  el.classList.add('carlos-collapsed');
                  el.style.display='none';
              }
            }

            jQuery("#siInput_<%=rand%>").autocomplete({
                source: function(request, response) {
                    jQuery.ajax({
                        url: "<%= request.getContextPath() %>/oscarRx/search.do?parameterValue=searchSpecialInstructions",
                        type: "POST",
                        data: { query: request.term },
                        dataType: "json",
                        success: function(data) {
                            response(data.results ? data.results.slice(0, 40) : []);
                        },
                        error: function() {
                            console.error("Special Instructions autocomplete failed");
                            response([]);
                        }
                    });
                },
                minLength: 1,
                select: function(event, ui) {
                    jQuery("#siInput_<%=rand%>").val(ui.item.value);
                    return false;
                }
            });

            checkAllergy('<%=rand%>','<%=rx.getAtcCode()%>');
            checkIfInactive('<%=rand%>','<%=rx.getRegionalIdentifier()%>');

            var isDiscontinuedLatest=<%=isDiscontinuedLatest%>;
            //oscarLog("isDiscon "+isDiscontinuedLatest);
            //pause(1000);
            var archR='<%=archivedReason%>';
            if(isDiscontinuedLatest && archR!="represcribed"){
               var archD='<%=archivedDate%>';
               //oscarLog("in js discon "+archR+"--"+archD);

                    if(confirm('This drug was discontinued on <%=archivedDate%> because of <%=archivedReason%> are you sure you want to continue it?')==true){
                        //do nothing
                    }
                    else{
                        document.getElementById('<%=fieldSetId%>').remove();
                        //call java class to delete it from stash pool.
                        var randId='<%=rand%>';
                        deletePrescribe(randId);
                    }
            }
            var listRxDrugSize=<%=listRxDrugs.size()%>;
            //oscarLog("listRxDrugsSize="+listRxDrugSize);
            counterRx++;
            //oscarLog("counterRx="+counterRx);
           var gcn_val="<%=gcnCode%>";
           if(gcn_val === "0"){
               document.getElementById('drugName_<%=rand%>').focus();
           } else if(counterRx==listRxDrugSize){
               //oscarLog("counterRx="+counterRx+"--listRxDrugSize="+listRxDrugSize);
               document.getElementById('instructions_<%=rand%>').focus();
           }
        </script>
                <%}%>
<script type="text/javascript">
counterRx=0;

if(skipParseInstr) {
	skipParseInstr = false;
}
</script>
<%}%>

