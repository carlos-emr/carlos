<%@ page import="io.github.carlos_emr.OscarProperties" %>
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

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_fax" rights="r" reverse="<%=true%>">
	<%authed=false; %>
	<%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_fax");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<html>
<head>
    <title>OSCAR Fax</title>

    <c:set var="ctx" value="${ pageContext.request.contextPath }" scope="page"/>
    <link rel="stylesheet" href="${ctx}/library/bootstrap/5.3.3/css/bootstrap.min.css" type="text/css"/>
    <link rel="stylesheet" href="${ctx}/css/fontawesome-all.min.css" type="text/css"/>
    <link href="${ctx}/library/jquery/jquery-ui-1.14.2.min.css" rel="stylesheet" type="text/css"/>

    <script type="text/javascript" src="${ctx}/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="${ctx}/library/jquery/jquery-compat.js"></script>
    <script type="text/javascript" src="${ctx}/library/jquery/jquery.validate.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/jquery/jquery-ui-1.14.2.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>

    <script type="text/javascript">

        top.window.resizeTo("800", "850");

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

        #additionalRecipientControlPanel, #form-control-buttons {
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
<jsp:include page="/images/spinner.jsp" flush="true"/>
<div id="bodyrow" class="container-fluid">

    <div id="bodycolumn" class="col-sm-12">

        <div id="page-header">

            <table id="oscarFaxHeader">
                <tr>
                    <td id="oscarFaxHeaderLeftColumn"><h1>OSCAR Fax</h1></td>

                    <td id="oscarFaxHeaderCenterColumn"><e:forHtml value="${ transactionType }" /></td>
                    <td id="oscarFaxHeaderRightColumn" align=right>
						<span class="HelpAboutLogout"> 
							<a style="font-size: 10px; font-style: normal;" href="${ ctx }oscarEncounter/About.jsp"
                               target="_new">About</a>
							<a style="font-size: 10px; font-style: normal;" target="_blank"
                               href="http://www.oscarmanual.org/search?SearchableText=&Title=Chart+Interface&portal_type%3Alist=Document">Help</a>
						</span>
					</td>
				</tr>
			</table>
		</div>
		
		<div id="page-body">
		
			<c:set var="formAction" value="${ctx}/fax/faxAction.do" />
			<c:if test="${ transactionType eq 'CONSULTATION' }">
				<c:set var="formAction" value="${ctx}/oscarEncounter/oscarConsultationRequest/ConsultationFormFax.do" />
			</c:if>
			
			<form id="coverPageForm" class="d-flex flex-wrap align-items-center gap-2" action='${ formAction }' onsubmit="return submitForm(event)" method="post" novalidate>
			
				<input type="hidden" name="requestId" value="<e:forHtmlAttribute value='${ reqId }' />" />
				<input type="hidden" name="reqId" value="<e:forHtmlAttribute value='${ reqId }' />" />
				<input type="hidden" name="transactionId" value="<e:forHtmlAttribute value='${ not empty reqId ? reqId : transactionId }' />" />
				<input type="hidden" name="transactionType" value="<e:forHtmlAttribute value='${ transactionType }' />" />
				<input type="hidden" name="demographicNo" value="<e:forHtmlAttribute value='${ not empty demographicNo ? demographicNo : param.demographicNo }' />" />
		  		<input type="hidden" name="faxFilePath" value="<e:forHtmlAttribute value='${ faxFilePath }' />" />
		  		
		  		<%-- to be removed soon below --%>
		  		<input type="hidden" name="documents" value="<e:forHtmlAttribute value='${ documents }' />" />
		  		<input type="hidden" name="transType" value="<e:forHtmlAttribute value='${ transType }' />" />
							
				<div class="card">
				  	<div class="card-header">
						<h3 class="card-title">From</h3>
					</div>
					<div class="card-body">
						<div class="container">
							<div class="row">	
							<div class="col-sm-12">				
							  <label for="senderFaxAccount">Fax account</label>
							  <select class="form-select" name="senderFaxNumber"  id="senderFaxAccount">
									<c:forEach items="${ requestScope.accounts }" var="account">
							    		<option value="<e:forHtmlAttribute value='${ account.faxNumber }' />" ${ account.id eq requestScope.faxAccount or account.faxNumber eq param.letterheadFax ? 'selected' : '' } >
							    			<c:out value="${ account.accountName }"/> <c:out value="(${ account.faxNumber })"/>
							    		</option>
									</c:forEach>
							  </select>
	
							  <%-- to be removed soon below --%>
							  <input type="hidden" name="sendersFax" value="<e:forHtmlAttribute value='${ not empty letterheadFax ? letterheadFax : param.letterheadFax }' />" />
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
						<h3 class="card-title">To</h3>
					</div>
				  	<div class="card-body">
						<div class="container">
						  	<div class="row" id="fax-recipients">	
								<div class="col-sm-6 mb-3">
									<label for="searchProfessionalSpecialist_name">Name</label>
								 	<input class="autocomplete form-control" type="text" name="recipient" value="<e:forHtmlAttribute value='${ professionalSpecialistName }' />"
								 		id="searchProfessionalSpecialist_name" placeholder="Search: last, first" required/>
								 </div>	
								 <div class="col-sm-6 mb-3">
									<label for="searchProfessionalSpecialist_fax">Fax</label>
									<input class="form-control" type="text" name="recipientFaxNumber" value="<e:forHtmlAttribute value='${ not empty fax ? fax : param.fax }' />"
										id="searchProfessionalSpecialist_fax" placeholder="xxx-xxx-xxxx"  required/>
								</div>
							</div>
						</div>
					</div>
				</div>
		
				<div class="card">
				  	<div class="card-header">
						<h3 class="card-title">Copy(s) to</h3>
					</div>
				  	<div class="card-body">
				  		<div class="container" id="fax-additional-recipients" >
	
				  			<div class="row" id="additionalRecipientControlPanel">			  			
				  				<div class="col-sm-5 mb-3">
						  			<label for="additionalRecipient_name" >Name</label>
								 	<input class="autocomplete form-control" type="text" value=""  
								 		id="additionalRecipient_name" name="additionalRecipient_name" placeholder="Search: last, first"  />
								</div>
									<div class="col-sm-5 mb-3">	
								 	<label for="additionalRecipient_fax">Fax</label>
								 	<input class="autocomplete form-control" name="additionalRecipient_fax" type="text" value=""  
								 		id="additionalRecipient_fax" placeholder="xxx-xxx-xxxx"  />
								</div>
								<div class="col-sm-2 mb-3">
									<label for="additionalRecipient_fax_btn">&nbsp;</label>
							        <button class="btn btn-primary" id="additionalRecipient_fax_btn" title="Add recipient to list" type="button">
							        	<span class="fa-solid fa-plus"></span>
							        </button>
							   </div>
						 	</div>

					  		<%-- Only show existing recipients if not displaying submission results --%>
					  		<c:if test="${ empty faxSuccessful }">
						  		<c:forEach items="${ copyToRecipients }" var="recipient" >
							  			<div class="row">
								  			<div class="col-sm-12 input-group recipientGroup">
								  				<label></label>
											      <input type="text" class="form-control" value="<e:forHtmlAttribute value='${ recipient.name }' /> <e:forHtmlAttribute value='${ recipient.fax }' />" disabled/>
											      <button class="btn btn-danger" type="button">
											        <span class="fa-solid fa-xmark"></span>
											      </button>
	                                    </div>
	                                    <input type="hidden" name="copyToRecipients"
	                                           value='"name":"<e:forHtmlAttribute value='${ recipient.name }' />","fax":"<e:forHtmlAttribute value='${ recipient.fax }' />"'/>

	                                        <%-- to be removed below --%>
	                                    <input type="hidden" name="faxRecipients"
	                                           value='"name":"<e:forHtmlAttribute value='${ recipient.name }' />","fax":"<e:forHtmlAttribute value='${ recipient.fax }' />"'/>
	                                </div>
	                            </c:forEach>
                            </c:if>
                        </div>
                    </div>
                </div>

                <c:if test="${ not empty documents and transactionType eq 'CONSULTATION' }">
                    <div class="card">
                        <div class="card-header">
                            <h3 class="card-title">Attachments</h3>
                        </div>
                        <div class="card-body">
                            <div class="container">
                                <div class="row">
                                    <ol class="list-group list-group-numbered col-sm-12">
                                        <c:forEach items="${ documents }" var="document">
                                            <li class="list-group-item"><c:out value="${ document }"/></li>
                                            <input type="hidden" name="documents" value="<e:forHtmlAttribute value='${ document }' />"/>
                                        </c:forEach>
                                    </ol>
                                </div>
                            </div>
                        </div>
                    </div>
                </c:if>

                <div class="card">
                    <div class="card-header">
                        <h3 class="card-title">Cover page</h3>
                    </div>
                    <div class="card-body">
                        <div class="container">
                            <div class="row">
                                <div class="col-sm-12">
                                    <div class="form-check form-check-inline">
                                        <input class="form-check-input" type="radio" name="coverpage" id="coverpageyes" value="true"
                                               onchange="document.getElementById('comments_container').style.display = 'block';"/>
                                        <label class="form-check-label" for="coverpageyes">Yes</label>
                                    </div>
                                    <div class="form-check form-check-inline">
                                        <input class="form-check-input" type="radio" checked="checked" name="coverpage" id="coverpageno"
                                               value="false"
                                               onchange="document.getElementById('comments_container').style.display = 'none';"/>
                                        <label class="form-check-label" for="coverpageno">No</label>
                                    </div>
                                </div>
                            </div>
                            <div class="row" id="comments_container" style="display:none;">
                                <div class="col-sm-12">
                                    <label for="commentsTextArea">Comments</label>
                                    <textarea class="form-control" name="comments" id="commentsTextArea"
                                              rows="5"><%= OscarProperties.getInstance().getProperty("DEFAULT_FAX_COVERPAGE_COMMENT", "") %></textarea>
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
                                <span class="btn-label"><i class="fa-solid fa-paper-plane"></i></span>
                                Send
                            </button>
                            <button formnovalidate="formnovalidate" id="btnCancel" type="submit"
                                    class="btn btn-danger btn-md float-end" value="Cancel"
                                    onclick="document.getElementById('submitMethod').value = 'cancel'">
                                <span class="btn-label"><i class="fa-solid fa-circle-xmark"></i></span>
                                Cancel
                            </button>
                        </div>
                    </div>
                </div>
            </form>
            <%-- Only show preview before submission, not after --%>
            <c:if test="${ transactionType ne 'CONSULTATION' and empty faxSuccessful }">
                <div class="card" id="preview-panel">
                    <div class="card-header">
                        <h3 class="card-title">Preview</h3>
                    </div>
                    <div class="card-body">
                        <div class="container">
                            <object id="previewPDF"
                                    data="${ctx}/fax/faxAction.do?method=getPreview&faxFilePath=<e:forUriComponent value='${faxFilePath}' />"
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
                        <div class="alert alert-success" role="alert">
                            Failed to add fax to outgoing queue: <c:out
                                value="${ faxJob.recipient } at ${ faxJob.destination } ${ faxJob.status }: ${ faxJob.statusString }"/>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="alert alert-success" role="alert">
                            Successfully added fax to outgoing queue: <c:out
                                value="${ faxJob.recipient } at ${ faxJob.destination }"/>
                        </div>
                    </c:otherwise>
                </c:choose>
            </c:forEach>
            <input type="button" class="btn btn-danger btn-md float-end" value="Close" onclick="window.close();"/>
        </c:if>
    </div>
</div>

<script type="text/javascript">
    var ctx = "<e:forJavaScript value='${ ctx }' />";
    
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
        * Auto complete methods.
        */
        $("#fax-additional-recipients .autocomplete, #fax-recipients .autocomplete").autocomplete({
            source: function (request, response) {
                var url = ctx + "/demographic/Contact.do?method=searchAllContacts&searchMode=search_name&orderBy=c.lastName,c.firstName";
                jQuery.ajax({
                    url: url,
                    type: "GET",
                    dataType: "json",
                    data: {
                        term: request.term
                    },
                    contentType: "application/json",
                    success: function (data) {
                        response(jQuery.map(data, function (item) {
                            return {
                                label: item.lastName + ", "
                                    + item.firstName + " :: "
                                    + item.residencePhone
                                    + " :: " + item.address
                                    + " " + item.city,
                                value: item.id,
                                contact: item
                            }
                        }));
                    }
                });
            },
            minLength: 2,
            focus: function (event, ui) {
                event.preventDefault();
                return false;
            },
            select: function (event, ui) {
                event.preventDefault();
                $("#" + this.id).val(ui.item.contact.lastName + ", " + ui.item.contact.firstName);
                $("#" + this.id.split("_")[0] + "_fax").val(ui.item.contact.fax);
            }
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
						        <span class="fa-solid fa-trash"></span>\
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