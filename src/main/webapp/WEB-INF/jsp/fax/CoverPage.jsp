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
    CoverPage.jsp

    Loaded via POST from FaxAnnotateViewer.jsp on forward from
    FaxDocument?faxReady=true to CoverPage.jsp.

    Provides UI for autocomplete fax numbers and outgoing fax 
    coordinates.

    @param docId        (request attribute, int) Document number
    @param faxReady     (request attribute, boolean)
    @since 2026-06
--%>
<!DOCTYPE html>

<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_fax" rights="r" reverse="<%=true%>">
	<%authed=false; %>
	<%response.sendRedirect(request.getContextPath() + "/securityError?type=_fax");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <fmt:setBundle basename="oscarResources"/>
    <title><fmt:message key="coverPage.title"/></title>

    <c:set var="ctx" value="${ pageContext.request.contextPath }" scope="page"/>
    <link rel="stylesheet" href="${ctx}/library/bootstrap/5.3.8/css/bootstrap.min.css" type="text/css"/>
    <link href="${ctx}/library/jquery/jquery-ui-1.14.2.min.css" rel="stylesheet" type="text/css"/>
    <link rel="stylesheet" href="${ctx}/css/fontawesome-all.min.css" type="text/css"/>

    <script type="text/javascript" src="${ctx}/library/jquery/jquery-3.7.1.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/jquery/jquery.validate-1.21.0.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/jquery/jquery-ui-1.14.2.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
    <script type="text/javascript" src="${ctx}/js/faxRecipientAutocomplete.js"></script>

    <script type="text/javascript">

        top.window.resizeTo("1050", "850");

        // Action to remove additional recipients from the form.
        function removeRecipient(element) {
            $(element).parent().parent().parent().remove();
        }

        // Show loading screen after submiting and validating the form.
        function submitForm(event) {
            const submit = event.submitter;
            if (submit.id === 'btnCancel') {
                return true;
            }

            const coverPageForm = document.getElementById('coverPageForm');
            if (coverPageForm.checkValidity()) {
                return ShowSpin(true);
            }
            return false;
        }
    </script>

    <%--
        Action return flashy confirmation messages.
    --%>
    <c:if test="${ not empty faxSuccessful }">
        <script type="text/javascript">
            $(document).ready(function () {
                $("#page-body").slideUp("slow");
            })
        </script>
    </c:if>

    <style type="text/css">

        * {
            font-family: Arial, Helvetica, sans-serif;
            font-size: small;
        }

        img {
            max-width: 100%;
            height: auto;
            width: auto \9;
        }

        #form-control-buttons {
            margin-bottom: 15px;
        }

        #form-control-buttons button {
            margin-left: 15px;
        }

        ul.ui-widget {
            margin: 10px;
            max-width: 100%;
            height: auto;
            max-height: 400px;
            overflow-y: scroll;
        }

        .recipientGroup {
            margin-bottom: 3px;
        }

        #oscarFaxHeader {
            width: 100%;
            border-collapse: collapse;
            margin-top: .5%;
            margin-bottom: 15px;
        }

        table#oscarFaxHeader tr td {
            padding: 1px 5px;
            background-color: #F3F3F3;
        }

        #oscarFaxHeader #oscarFaxHeaderLeftColumn {
            width: 19.5% !important;
            background-color: white;
            padding: 0px;
            padding-right: .5% !important;
            width: 20%;
        }

        #oscarFaxHeader #oscarFaxHeaderLeftColumn h1 {
            margin: 0px;
            padding: 7px !important;
            display: block;
            font-size: large !important;
            background-color: black;
            color: white;
            font-weight: bold;
        }

        #oscarFaxHeaderRightColumn {
            vertical-align: top;
            text-align: right;
            padding-top: 3px;
            padding-right: 3px;
        }

        span.HelpAboutLogout a {
            font-size: x-small;
            color: black;
            float: right;
            padding: 0 3px;
        }

        label.invalid {
            color: red;
            font-weight: normal;
        }

        input.invalid {
            border-color: red;
        }

    </style>

</head>
<body>
<jsp:include page="/WEB-INF/jsp/includes/spinner.jspf" flush="true"/>
<div id="bodyrow" class="container-fluid">

    <div id="bodycolumn" class="col-sm-12">

        <div id="page-header">

            <table id="oscarFaxHeader">
                <tr>
                    <td id="oscarFaxHeaderLeftColumn"><h1><fmt:message key="coverPage.title"/></h1></td>

                    <td id="oscarFaxHeaderCenterColumn"><carlos:encode value='${ transactionType }' context="forHtml"/></td>
                    <td id="oscarFaxHeaderRightColumn" align=right>
						<span class="HelpAboutLogout"> 
							<a style="font-size: 10px; font-style: normal;" href="${pageContext.request.contextPath}/encounter/ViewAbout"
                               target="_new">About</a>
							<a style="font-size: 10px; font-style: normal;" target="_blank"
                               href="http://www.oscarmanual.org/search?SearchableText=&Title=Chart+Interface&portal_type%3Alist=Document">Help</a>
						</span>
					</td>
				</tr>
			</table>
		</div>
		
		<div id="page-body">
		
			<c:set var="formAction" value="${ctx}/fax/faxAction" />
			<c:if test="${ transactionType eq 'CONSULTATION' }">
				<c:set var="formAction" value="${ctx}/encounter/oscarConsultationRequest/ConsultationFormFax" />
			</c:if>
			
			<form id="coverPageForm" class="d-flex flex-wrap align-items-center gap-2" action='${ formAction }' onsubmit="return submitForm(event)" method="post" novalidate>
			
				<input type="hidden" name="requestId" value="<carlos:encode value='${ reqId }' context="htmlAttribute"/>" />
				<input type="hidden" name="reqId" value="<carlos:encode value='${ reqId }' context="htmlAttribute"/>" />
				<input type="hidden" name="transactionId" value="<carlos:encode value='${ not empty reqId ? reqId : transactionId }' context="htmlAttribute"/>" />
				<input type="hidden" name="transactionType" value="<carlos:encode value='${ transactionType }' context="htmlAttribute"/>" />
				<input type="hidden" name="demographicNo" value="<carlos:encode value='${ not empty demographicNo ? demographicNo : param.demographicNo }' context="htmlAttribute"/>" />
				<input type="hidden" name="faxFilePath" value="<carlos:encode value='${ faxFilePath }' context="htmlAttribute"/>" />
		  		
		  		<%-- to be removed soon below --%>
		  		<input type="hidden" name="documents" value="<carlos:encode value='${ documents }' context="htmlAttribute"/>" />
		  		<input type="hidden" name="transType" value="<carlos:encode value='${ transType }' context="htmlAttribute"/>" />
							
				<div class="card">
				  	<div class="card-header">
						<h3 class="card-title"><fmt:message key="coverPage.card.from"/></h3>
					</div>
					<div class="card-body">
						<div class="container">
							<div class="row">
							<div class="col-sm-12 mb-3">
							  <label for="senderFaxAccount"><fmt:message key="coverPage.lbl.faxAccount"/></label>
							  <select class="form-select" name="senderFaxNumber"  id="senderFaxAccount">
									<c:forEach items="${ requestScope.accounts }" var="account">
							    		<option value="<carlos:encode value='${ account.faxNumber }' context="htmlAttribute"/>" ${ account.id eq requestScope.faxAccount or account.faxNumber eq param.letterheadFax ? 'selected' : '' } >
							    			${carlos:forHtml(account.accountName)} (${carlos:forHtml(account.faxNumber)})
							    		</option>
									</c:forEach>
							  </select>
	
							  <%-- to be removed soon below --%>
							  <input type="hidden" name="sendersFax" value="<carlos:encode value='${ not empty letterheadFax ? letterheadFax : param.letterheadFax }' context="htmlAttribute"/>" />
							</div>
							</div>
							<!-- <div class="row">
								<label >Override Return Fax Number?</label>
							</div>
							<div class="row">
							
								<label class="form-check form-check-inline" for="overridefaxyes">
									<input type="radio" name="isOverrideFaxNumber" id="overridefaxyes" value="true" 
										onchange="document.getElementById('overridefax_container').style.display = 'block';" />Yes
								</label>
								<label class="form-check form-check-inline" for="overridefaxno">
									<input type="radio" checked="checked" name="isOverrideFaxNumber" id="overridefaxno" 
										value="false" onchange="document.getElementById('overridefax_container').style.display = 'none';" />No
								</label>
							</div>
							<div class="row" id="overridefax_container" style="display:none;">
								<input type="text" class="form-control" name="overrideFaxNumber" value="" placeholder="xxx-xxx-xxxx"/>
							</div> -->
						</div>
					</div>
				</div>
				
				<div class="card">
				  	<div class="card-header">
						<h3 class="card-title"><fmt:message key="coverPage.card.to"/></h3>
					</div>
				  	<div class="card-body">
						<div class="container">
						  	<div class="row" id="fax-recipients">
								<div class="col-sm-8 mb-3" style="position:relative;">
									<label for="searchProfessionalSpecialist_name"><fmt:message key="coverPage.lbl.name"/></label>
								 	<input class="form-control" type="text" name="recipient" value="<carlos:encode value='${ professionalSpecialistName }' context="htmlAttribute"/>"
								 		id="searchProfessionalSpecialist_name" placeholder="<fmt:message key='coverPage.ph.nameSearch'/>" required autocomplete="off"/>
								 	<div id="faxRecipientDropdown" class="fax-ac-dropdown"></div>
								 </div>
								 <div class="col-sm-4 mb-3">
									<label for="searchProfessionalSpecialist_fax"><fmt:message key="coverPage.lbl.fax"/></label>
									<input class="form-control" type="text" name="recipientFaxNumber" value="<carlos:encode value='${ not empty fax ? fax : param.fax }' context="htmlAttribute"/>"
										id="searchProfessionalSpecialist_fax" placeholder="<fmt:message key='coverPage.ph.faxNumber'/>" required/>
								</div>
							</div>
						</div>
					</div>
				</div>
		
				<div class="card">
				  	<div class="card-header">
						<h3 class="card-title"><fmt:message key="coverPage.card.copies"/></h3>
					</div>
				  	<div class="card-body">
				  		<div class="container" id="fax-additional-recipients" >

				  			<div class="row" id="additionalRecipientControlPanel">
				  				<div class="col-sm-7 mb-3" style="position:relative;">
						  			<label for="additionalRecipient_name"><fmt:message key="coverPage.lbl.name"/></label>
								 	<input class="form-control" type="text" value=""
								 		id="additionalRecipient_name" name="additionalRecipient_name" placeholder="<fmt:message key='coverPage.ph.nameSearch'/>" autocomplete="off"/>
								 	<div id="faxCcDropdown" class="fax-ac-dropdown"></div>
								</div>
									<div class="col-sm-3 mb-3">
								 	<label for="additionalRecipient_fax"><fmt:message key="coverPage.lbl.fax"/></label>
								 	<input class="autocomplete form-control" name="additionalRecipient_fax" type="text" value=""
								 		id="additionalRecipient_fax" placeholder="<fmt:message key='coverPage.ph.faxNumber'/>"/>
								</div>
								<div class="col-sm-2 mb-3">
									<label for="additionalRecipient_fax_btn">&nbsp;</label>
							        <button class="btn btn-primary" id="additionalRecipient_fax_btn" title="<fmt:message key='coverPage.btn.addRecipient'/>" type="button">
							        	<i class="fa-solid fa-plus"></i>
							        </button>
							   </div>
						 	</div>

					  		<%-- Only show existing recipients if not displaying submission results --%>
					  		<c:if test="${ empty faxSuccessful }">
						  		<c:forEach items="${ copyToRecipients }" var="recipient" >
							  			<div class="row">
								  			<div class="col-sm-12 input-group recipientGroup">
								  				<label></label>
											      <input type="text" class="form-control" value="<carlos:encode value='${ recipient.name }' context="htmlAttribute"/> <carlos:encode value='${ recipient.fax }' context="htmlAttribute"/>" disabled/>
											      <button class="btn btn-danger" type="button">
											        <i class="fa-solid fa-xmark"></i>
											      </button>
	                                    </div>
	                                    <input type="hidden" name="copyToRecipients"
	                                           value='"name":"<carlos:encode value='${ recipient.name }' context="htmlAttribute"/>","fax":"<carlos:encode value='${ recipient.fax }' context="htmlAttribute"/>"'/>

	                                        <%-- to be removed below --%>
	                                    <input type="hidden" name="faxRecipients"
	                                           value='"name":"<carlos:encode value='${ recipient.name }' context="htmlAttribute"/>","fax":"<carlos:encode value='${ recipient.fax }' context="htmlAttribute"/>"'/>
	                                </div>
	                            </c:forEach>
                            </c:if>
                        </div>
                    </div>
                </div>

                <c:if test="${ not empty documents and transactionType eq 'CONSULTATION' }">
                    <div class="card">
                        <div class="card-header">
                            <h3 class="card-title"><fmt:message key="coverPage.card.attachments"/></h3>
                        </div>
                        <div class="card-body">
                            <div class="container">
                                <div class="row">
                                    <ol class="list-group list-group-numbered col-sm-12">
                                        <c:forEach items="${ documents }" var="document">
                                            <li class="list-group-item">${carlos:forHtml(document)}</li>
                                            <input type="hidden" name="documents" value="<carlos:encode value='${ document }' context="htmlAttribute"/>"/>
                                        </c:forEach>
                                    </ol>
                                </div>
                            </div>
                        </div>
                    </div>
                </c:if>

                <div class="card">
                    <div class="card-header">
                        <h3 class="card-title"><fmt:message key="coverPage.card.coverPage"/></h3>
                    </div>
                    <div class="card-body">
                        <div class="container">
                            <div class="row">
                                <div class="col-sm-12">
                                    <div class="form-check form-check-inline">
                                        <input class="form-check-input" type="radio" name="coverpage" id="coverpageyes" value="true"
                                               onchange="document.getElementById('comments_container').style.display = 'block';"/>
                                        <label class="form-check-label" for="coverpageyes"><fmt:message key="coverPage.lbl.yes"/></label>
                                    </div>
                                    <div class="form-check form-check-inline">
                                        <input class="form-check-input" type="radio" checked="checked" name="coverpage" id="coverpageno"
                                               value="false"
                                               onchange="document.getElementById('comments_container').style.display = 'none';"/>
                                        <label class="form-check-label" for="coverpageno"><fmt:message key="coverPage.lbl.no"/></label>
                                    </div>
                                </div>
                            </div>
                            <div class="row" id="comments_container" style="display:none;">
                                <div class="col-sm-12">
                                    <label for="commentsTextArea"><fmt:message key="coverPage.lbl.comments"/></label>
                                    <textarea class="form-control" name="comments" id="commentsTextArea"
                                              rows="5"><%= CarlosProperties.getInstance().getProperty("DEFAULT_FAX_COVERPAGE_COMMENT", "") %></textarea>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="container" id="form-control-buttons">
                    <div class="row">
                        <div class="col-sm-12">
                            <input type="hidden" id="submitMethod" name="method" value="queue"/>
                            <button type="submit" id="btnSend" class="btn btn-primary btn-md float-end" value="Send">
                                <i class="fa-solid fa-paper-plane"></i>
                                <fmt:message key="coverPage.btn.send"/>
                            </button>
                            <button formnovalidate="formnovalidate" id="btnCancel" type="submit"
                                    class="btn btn-danger btn-md float-end" value="Cancel"
                                    onclick="document.getElementById('submitMethod').value = 'cancel'">
                                <i class="fa-solid fa-circle-xmark"></i>
                                <fmt:message key="coverPage.btn.cancel"/>
                            </button>
                        </div>
                    </div>
                </div>
            </form>
            <%-- Only show preview before submission, not after --%>
            <c:if test="${ transactionType ne 'CONSULTATION' and empty faxSuccessful }">
                <div class="card" id="preview-panel">
                    <div class="card-header">
                        <h3 class="card-title"><fmt:message key="coverPage.card.preview"/></h3>
                    </div>
                    <div class="card-body">
                        <div class="container">
                            <object id="previewPDF"
                                    data="${ctx}/fax/faxAction?method=getPreview&faxFilePath=<carlos:encode value='${faxFilePath}' context="uriComponent"/>"
                                    type="application/pdf" width="100%" height="800">
                            </object>
                        </div>
                    </div>
                </div>
            </c:if>
        </div>

        <%-- the confirmation tags. --%>
        <c:if test="${ not empty faxSuccessful }">
            <c:forEach items="${ faxJobList }" var="faxJob">
                <c:choose>
                    <c:when test="${ faxJob.status eq 'ERROR' }">
                        <div class="alert alert-danger" role="alert">
                            <fmt:message key="coverPage.msg.faxError">
                                <fmt:param value="${carlos:forHtml(faxJob.recipient)}"/>
                                <fmt:param value="${carlos:forHtml(faxJob.destination)}"/>
                                <fmt:param value="${carlos:forHtml(faxJob.status)}"/>
                            </fmt:message>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="alert alert-success" role="alert">
                            <fmt:message key="coverPage.msg.faxSuccess">
                                <fmt:param value="${carlos:forHtml(faxJob.recipient)}"/>
                                <fmt:param value="${carlos:forHtml(faxJob.destination)}"/>
                            </fmt:message>
                        </div>
                    </c:otherwise>
                </c:choose>
            </c:forEach>
            <input type="button" class="btn btn-danger btn-md float-end" value="<fmt:message key='coverPage.btn.close'/>" onclick="window.close();"/>
        </c:if>
    </div>
</div>

<script type="text/javascript">
    var ctx = "<carlos:encode value='${ ctx }' context="javaScript"/>";
    
    // HTML entity encoding function to prevent XSS
    function escapeHtml(text) {
        if (!text) return '';
        var map = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        };
        return text.toString().replace(/[&<>"']/g, function(m) { return map[m]; });
    }

    $(document).ready(function () {

        /*
        * Fax recipient autocomplete (pharmacy + specialist) for the primary To field.
        */
        setupFaxRecipientAutocomplete({
            contextPath: ctx,
            nameInputId: 'searchProfessionalSpecialist_name',
            faxInputId:  'searchProfessionalSpecialist_fax',
            dropdownId:  'faxRecipientDropdown'
        });

        /*
        * Fax recipient autocomplete for the CC field.
        */
        setupFaxRecipientAutocomplete({
            contextPath: ctx,
            nameInputId: 'additionalRecipient_name',
            faxInputId:  'additionalRecipient_fax',
            dropdownId:  'faxCcDropdown'
        });

        /*
        * Action to add additional recipients to this fax transmission
        */
        $("#additionalRecipient_fax_btn").click(function () {

            var nameElement = $("#additionalRecipient_name");
            var faxElement = $("#additionalRecipient_fax");
            var name = nameElement.val();
            var fax = faxElement.val();

            if (!fax) {
                faxElement.addClass('invalid').focus();
                return;
            }

            // For display
            var inputValue = escapeHtml(name + " " + fax);
            
            // For the data format the server expects
            // First escape double quotes and backslashes in the actual values to prevent breaking the JSON format
            var safeName = name.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
            var safeFax = fax.replace(/\\/g, "\\\\").replace(/"/g, '\\"');

            // Build the format the server expects (proper JSON format with double quotes)
            var submitValue = '"name":"' + safeName + '","fax":"' + safeFax + '"';

            $("#fax-additional-recipients").append(
                '<div class="row">\
                    <div class="col-sm-12 input-group recipientGroup">\
                        <input type="text" class="form-control" value="' + inputValue + '" disabled/>\
						      <button class="btn btn-danger remove-additional-recipient-btn" type="button" onclick="removeRecipient(this)" >\
						        <i class="fa-solid fa-trash"></i>\
						      </button>\
					    </div>\
						<input type="hidden" name="copyToRecipients" value=\'' + submitValue + '\' />\
						<input type="hidden" name="faxRecipients" value=\'' + submitValue + '\' />\
					</div>'
            );

            faxElement.val("");
            nameElement.val("");

        })

        /*
        * Clear the add recipient fields.
        */
        $('#coverPageForm').submit(function () {
            if ($("#additionalRecipient_name").val().length > 0 && $("#additionalRecipient_fax").val().length > 6) {
                $('#additionalRecipient_fax_btn').trigger('click');
            }
        })

        /*
         * Validate the form before submission
         */
        $('#coverPageForm').validate({
            rules: {
                recipientFaxNumber: {
                    required: true,
                    minlength: 7
                },
                recipient: {
                    required: true
                },
                additionalRecipient_fax: {
                    required: {
                        depends: function (element) {
                            return $("#additionalRecipient_name").val().length > 0;
                        }
                    },
                    minlength: 7
                }
            },
            messages: {
                additionalRecipient_fax: {
                    required: "Recipient fax number required"
                },
                recipientFaxNumber: {
                    required: "Recipient fax number required",
                    minlength: "Recipient fax is invalid"
                },
                recipient: "Recipient name required"
            },
            errorClass: 'invalid',
            validClass: 'valid'

        })

    })

</script>
</body>
</html>
