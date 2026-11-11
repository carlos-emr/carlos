<%--

    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
    <%@ page import="io.github.carlos_emr.CarlosProperties" contentType="text/javascript; charset=UTF-8" pageEncoding="UTF-8"%>

    <%
    CarlosProperties props = CarlosProperties.getInstance();
    if (props.isEFormSignatureEnabled()) {
    %>

    if (typeof jQuery == "undefined") {
        alert("The signatureControl library requires jQuery. Please ensure that it is loaded first");
    }

    var signaturePadIFrameStart = "Signature:<br/><iframe style=\"width:500px; height:132px;\"id=\"signatureFrame\" src=\"";
    var signaturePadIFrameEnd = "\" ></iframe>";
    // The stored-signature address CARLOS gives a signature (tabletSignature.jsp, EForm.setSignatureCode),
    // with the refresh number the pad adds: a root-relative path ending in /imageRenderingServlet, the
    // stored source and a numeric id. A saved value of any other shape is not shown.
    var storedSignatureAddress = /^(?:\/[A-Za-z0-9._~-]+)*\/imageRenderingServlet\?source=signature_stored&digitalSignatureId=[0-9]+(?:&r=[0-9]+)?$/;
    // An image size option: a whole number of pixels, or a percentage.
    var signatureImageSize = /^[0-9]+%?$/;
    var signatureControl = {
        initialize: function (options) {
            var element = this;
            // Generating an eform configuration.
            if (typeof options == "undefined" || options == null || options.eform) {
                options = typeof options == "undefined" ? {} : options;
                element.sigHTML = "<%= request.getContextPath() %>/signature_pad/tabletSignature?inWindow=true&saveToDB=true&demographicNo=";
                element.signatureDisplay = "#signatureDisplay";
                element.signatureInput = "#signatureInput";
                element.signatureValue = "#signatureValue"
                element.demographicNo = 0; //TODO: Keep on trucking
                element.imgWidth = null;
                element.imgHeight = null;

                var placeholder = jQuery(element.signatureInput);
                if (!placeholder) {
                    if (jQuery(".DoNotPrint")) {
                        placeholder = jQuery("<div id='signatureInput'>&nbsp;</div>");
                        jQuery(".DoNotPrint").append(placeholder);
                    } else {
                        alert("Missing placeholder please ensure a div with the id signatureInput or a div with class DoNotPrint exists on the page .");
                        return;
                    }
                }

                if (options.left != null) {
                    jQuery(element.signatureDisplay).css("position", "absolute");
                    jQuery(element.signatureDisplay).css("left", options.left);
                }
                if (options.right != null) {
                    jQuery(element.signatureDisplay).css("position", "absolute");
                    jQuery(element.signatureDisplay).css("right", options.right);
                }
                if (options.top != null) {
                    jQuery(element.signatureDisplay).css("position", "absolute");
                    jQuery(element.signatureDisplay).css("top", options.top);
                }
                if (options.bottom != null) {
                    jQuery(element.signatureDisplay).css("position", "absolute");
                    jQuery(element.signatureDisplay).css("bottom", options.bottom);
                }
                if (options.width != null && signatureImageSize.test(String(options.width))) {
                    element.imgWidth = String(options.width);
                }
                if (options.height != null && signatureImageSize.test(String(options.height))) {
                    element.imgHeight = String(options.height);
                }

                // Shows the signature at a stored-signature address as an image, its address set as the
                // image source only. Any other value shows no image.
                element.showSignature = function (address) {
                    var display = jQuery(element.signatureDisplay);
                    display.empty();
                    if (typeof address !== "string" || !storedSignatureAddress.test(address)) {
                        return;
                    }
                    var image = jQuery("<img>").attr({src: address, alt: "signature"});
                    if (element.imgWidth != null) {
                        image.attr("width", element.imgWidth);
                    }
                    if (element.imgHeight != null) {
                        image.attr("height", element.imgHeight);
                    }
                    display.append(image);
                };

                var savedSignature = jQuery(element.signatureValue).val();
                if (savedSignature != null && "" != savedSignature) {
                    element.showSignature(savedSignature);
                }

                element.refreshImage =
                    function (e) {
                        var address = e.storedImageUrl + "&r=" + Math.floor(Math.random() * 1001);
                        element.showSignature(address);
                        if (jQuery(element.signatureValue)) {
                            jQuery(element.signatureValue).val(address);
                        }
                    };

            }
            // Configuring signature pad with user provided settings.
            else {
                if (options.sigHTML == null) {
                    alert("Error, missing url for tabletSignature.jsp");
                    return;
                }
                if (options.demographicNo == null) {
                    alert("Error, missing demographic number.");
                    return;
                }
                element.sigHTML = options.sigHTML;
                element.signatureDisplay = options.signatureDisplay;
                element.signatureInput = options.signatureInput;
                element.demographicNo = options.demographicNo;
                element.isSignatureDirty = false;
                element.isSignatureSaved = false;
                element.refreshImage = options.refreshImage == null
                    ? function (e) {
                        if (jQuery(element.signatureDisplay)) {
                            jQuery(element.signatureDisplay).attr("src", e.storedImageUrl + "&r=" + Math.floor(Math.random() * 1001));
                        }
                    } : options.refreshImage;
            }

            // Inserting a hook into the window that the signature pad will
            // use to inform us when the signature's state has changed.
            window.signatureHandler = options.signatureHandler == null
                ? function (e) {
                    element.isSignatureDirty = e.isDirty;
                    element.isSignatureSaved = e.isSave;
                    if (e.isSave) {
                        element.refreshImage(e);
                    }
                }
                : options.signatureHandler;
            if ($(element.signatureInput)) {
                $(element.signatureInput).html(signaturePadIFrameStart + element.sigHTML + signaturePadIFrameEnd);
            }
            return element;
        }
    };
    <% } %>
