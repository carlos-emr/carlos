<%@ page import="io.github.carlos_emr.CarlosProperties" %>
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
    CoverPage.jsp — Fax cover-page composition and pre-send preview.

    Purpose:
      Renders the "compose fax" screen shown before an eForm (and any attachments) is queued for
      faxing. The user reviews the assembled document, sets the recipient/cover-page details, and
      submits the job.

    Access control:
      Requires the _fax READ security object. The gate scriptlet redirects to
      /securityError?type=_fax when the session lacks it, so the page never renders for
      unauthorized users.

    Preview behavior (see docs/eform-browser-pdf-renderer.md):
      The document is previewed two ways. Inline page IMAGES are produced via
      NioFileManager.createCacheVersion2 and require _edoc READ; a user without _edoc still gets a
      working "Open PDF" link (soft degradation), so the preview never hard-fails on missing _edoc.

    Key request parameters / attributes:
      faxFilePath   - server-generated path of the assembled PDF being previewed/faxed
      showAs        - "image" selects the inline page-image preview; otherwise the PDF is served
      pageNumber    - 1-based page selector for the image preview
      demographicNo - patient the fax relates to
      letterheadFax - clinic/sender fax number used to select and prefill the sending fax account
      fax           - recipient fax number prefilled into the form

    @since 2014-08-29
--%>

<!DOCTYPE html>

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
    <title>CARLOS Fax</title>

    <c:set var="ctx" value="${ pageContext.request.contextPath }" scope="page"/>
    <link rel="stylesheet" href="${ctx}/library/bootstrap/5.3.8/css/bootstrap.min.css" type="text/css"/>
    <link href="${ctx}/library/jquery/jquery-ui-1.14.2.min.css" rel="stylesheet" type="text/css"/>

    <script type="text/javascript" src="${ctx}/library/jquery/jquery-3.7.1.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/jquery/jquery.validate-1.21.0.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/jquery/jquery-ui-1.14.2.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>

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
    <c:if test="${ not empty faxSuccessful and faxSuccessful }">
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
<jsp:include page="/WEB-INF/jsp/includes/spinner.jspf" flush="true"/>
<div id="bodyrow" class="container-fluid">

    <div id="bodycolumn" class="col-sm-12">

        <div id="page-header">

            <table id="oscarFaxHeader">
                <tr>
                    <td id="oscarFaxHeaderLeftColumn"><h1>CARLOS Fax</h1></td>

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
						<h3 class="card-title">From</h3>
					</div>
					<div class="card-body">
						<div class="container">
							<div class="row">	
							<div class="col-sm-12">				
							  <label for="senderFaxAccount">Fax account</label>
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
						<h3 class="card-title">To</h3>
					</div>
				  	<div class="card-body">
						<div class="container">
						  	<div class="row" id="fax-recipients">	
								<div class="col-sm-6 mb-3">
									<label for="searchProfessionalSpecialist_name">Name</label>
								 	<input class="autocomplete form-control" type="text" name="recipient" value="<carlos:encode value='${ professionalSpecialistName }' context="htmlAttribute"/>"
								 		id="searchProfessionalSpecialist_name" placeholder="Search: last, first" required/>
								 </div>	
								 <div class="col-sm-6 mb-3">
									<label for="searchProfessionalSpecialist_fax">Fax</label>
									<input class="form-control" type="text" name="recipientFaxNumber" value="<carlos:encode value='${ not empty fax ? fax : param.fax }' context="htmlAttribute"/>"
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
											      <input type="text" class="form-control" value="<carlos:encode value='${ recipient.name }' context="htmlAttribute"/> <carlos:encode value='${ recipient.fax }' context="htmlAttribute"/>" disabled/>
											      <button class="btn btn-danger" type="button">
											        <span class="fa-solid fa-xmark"></span>
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
                            <h3 class="card-title">Attachments</h3>
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
                            <p class="text-muted">
                                Preview shows the generated fax PDF as server-rendered images.
                                <a id="previewPdfLink"
                                   href="${ctx}/fax/faxAction?method=getPreview&faxFilePath=${carlos:forUriComponent(faxFilePath)}"
                                   target="_blank" rel="noopener noreferrer">Open PDF</a>
                            </p>
                            <div id="previewStatus" class="text-muted">Loading preview…</div>
                            <div id="previewImages" class="d-flex flex-column gap-3"></div>
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
                            Failed to add fax to outgoing queue: ${carlos:forHtml(faxJob.recipient)} at ${carlos:forHtml(faxJob.destination)} ${carlos:forHtml(faxJob.status)}: ${carlos:forHtml(faxJob.statusString)}
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="alert alert-success" role="alert">
                            Successfully added fax to outgoing queue: ${carlos:forHtml(faxJob.recipient)} at ${carlos:forHtml(faxJob.destination)}
                        </div>
                    </c:otherwise>
                </c:choose>
            </c:forEach>
            <input type="button" class="btn btn-danger btn-md float-end" value="Close" onclick="window.close();"/>
        </c:if>

        <%-- cancel() no longer redirects (and silently discards the message) when
             faxManager.flush fails to clear the preview cache / temporary file; render the
             failure here instead so the user knows the cleanup (and PHI removal) did not
             complete. --%>
        <c:if test="${ not empty faxCleanupFailed }">
            <div class="alert alert-danger" role="alert">
                The fax was cancelled, but the preview cache or temporary file could not be fully removed.
                Please retry Cancel or contact your system administrator.
            </div>
        </c:if>
    </div>
</div>

<script type="text/javascript">
    var ctx = "${carlos:forJavaScript(ctx)}";
    
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

    function getPreviewPageCount(callback) {
        var faxFilePath = $("input[name='faxFilePath']").val();
        if (!faxFilePath) {
            callback(0);
            return;
        }

        $.post(ctx + "/fax/faxAction", {
            method: "getPageCount",
            faxFilePath: faxFilePath
        }).done(function (resultdata) {
            callback(resultdata.pageCount || 0);
        }).fail(function (jqXHR) {
            // Surface the status: a 403 (missing document privilege) needs different operator
            // action than a 404/500, and collapsing them all into "Preview unavailable" hid that.
            console.error("Fax preview page count request failed with HTTP status " + jqXHR.status);
            callback(0, jqXHR.status);
        });
    }

    // Cap how many page-image elements are materialized up front so a very large PDF cannot make the
    // cover page slow or unresponsive; the rest render on demand via a "Show remaining" button. Each
    // <img> is also natively lazy-loaded.
    var PREVIEW_INITIAL_PAGE_CAP = 25;

    function appendPreviewImage(container, faxFilePath, pageNumber) {
        var image = $("<img />")
            .attr("src", ctx + "/fax/faxAction?method=getPreview&showAs=image&faxFilePath=" + encodeURIComponent(faxFilePath) + "&pageNumber=" + pageNumber)
            .attr("alt", "Fax preview page " + pageNumber)
            .attr("loading", "lazy")
            .addClass("img-fluid border rounded bg-white")
            .css("background-image", "url('" + ctx + "/images/loader.gif')")
            .css("background-position", "50% 50%")
            .css("background-repeat", "no-repeat")
            // A user without the _edoc privilege gets a 403 from getPreview for every page image;
            // without this handler each broken <img> sits on the page captioned
            // "Showing N pages", which reads as a successful preview instead of a degraded one.
            .on("error", function () {
                $(this).remove();
                var previewStatus = $("#previewStatus");
                if (!previewStatus.data("degraded")) {
                    previewStatus.data("degraded", true)
                        .text("Preview images are not available for your role or this document. Use Open PDF to review the generated fax document.");
                }
            });

        $("<div />")
            .addClass("mb-3")
            .append($("<div />").addClass("small text-muted mb-1").text("Page " + pageNumber))
            .append(image)
            .appendTo(container);
    }

    function renderPreviewImages(pageCount, failureStatus) {
        var faxFilePath = $("input[name='faxFilePath']").val();
        var previewStatus = $("#previewStatus");
        var previewImages = $("#previewImages");

        previewImages.empty();

        if (!faxFilePath || pageCount < 1) {
            // A 403 means this user lacks the document privilege the inline image preview needs
            // (soft degradation: Open PDF still works); other failures are generic.
            if (failureStatus === 403) {
                previewStatus.text("Preview images are not available for your role. Use Open PDF to review the generated fax document.");
            } else {
                previewStatus.text("Preview unavailable. Use Open PDF to review the generated fax document.");
            }
            return;
        }

        var initialCount = Math.min(pageCount, PREVIEW_INITIAL_PAGE_CAP);
        // Reflect the initial cap so the count is not misleading for a large fax: only the first
        // initialCount pages render up front until the user clicks "Show remaining …".
        if (pageCount > initialCount) {
            previewStatus.text("Showing first " + initialCount + " of " + pageCount + " pages.");
        } else {
            previewStatus.text("Showing " + pageCount + " page" + (pageCount === 1 ? "" : "s") + ".");
        }

        for (var i = 1; i <= initialCount; i++) {
            appendPreviewImage(previewImages, faxFilePath, i);
        }

        if (pageCount > initialCount) {
            var remaining = pageCount - initialCount;
            var showMore = $("<button />")
                .attr("type", "button")
                .addClass("btn btn-outline-secondary btn-sm mb-3")
                .text("Show remaining " + remaining + " page" + (remaining === 1 ? "" : "s"))
                .on("click", function () {
                    $(this).remove();
                    for (var j = initialCount + 1; j <= pageCount; j++) {
                        appendPreviewImage(previewImages, faxFilePath, j);
                    }
                    // All pages are now materialized, so the "first N of M" status no longer holds.
                    previewStatus.text("Showing all " + pageCount + " page" + (pageCount === 1 ? "" : "s") + ".");
                });
            previewImages.append(showMore);
        }
    }

    $(document).ready(function () {
        if ($("#previewImages").length) {
            getPreviewPageCount(renderPreviewImages);
        }

        /*
        * Auto complete methods.
        */
        $("#fax-additional-recipients .autocomplete, #fax-recipients .autocomplete").autocomplete({
            source: function (request, response) {
                var url = ctx + "/demographic/Contact?method=searchAllContacts&searchMode=search_name&orderBy=c.lastName,c.firstName";
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
            var inputValue = name + " " + fax;

            // For the data format the server expects
            // First escape double quotes and backslashes in the actual values to keep the JSON valid
            var safeName = name.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
            var safeFax = fax.replace(/\\/g, "\\\\").replace(/"/g, '\\"');

            // Build the format the server expects (proper JSON format with double quotes)
            var submitValue = '"name":"' + safeName + '","fax":"' + safeFax + '"';

            // Build the row as DOM nodes and assign the user-derived values with .val(), never by
            // splicing them into an HTML string. A name containing an apostrophe (e.g. O'Brien) would
            // otherwise break out of a single-quoted value attribute and inject markup (DOM XSS);
            // .val() sets the value property directly, so no HTML parsing of the value occurs.
            var $group = $('<div class="col-sm-12 input-group recipientGroup"></div>')
                .append($('<input type="text" class="form-control" disabled/>').val(inputValue))
                .append($('<button class="btn btn-danger remove-additional-recipient-btn" type="button"></button>')
                    .attr('onclick', 'removeRecipient(this)')
                    .append('<span class="fa-solid fa-trash"></span>'));
            var $row = $('<div class="row"></div>')
                .append($group)
                .append($('<input type="hidden" name="copyToRecipients"/>').val(submitValue))
                .append($('<input type="hidden" name="faxRecipients"/>').val(submitValue));
            $("#fax-additional-recipients").append($row);

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