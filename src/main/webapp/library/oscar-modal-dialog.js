/*!
 * Oscar Modal Dialog.
 *
 * Adding to a page is simple: 
 * 
 * 1.) Add this javascript to any HTML page.
 * 
 * 		<script type="text/javascript" src="${pageContext.servletContext.contextPath}/library/oscar-modal-dialog.js"></script> 
 * 
 * 2.) add all the dependent jQuery UI scripts 
 * 
 * 		<link rel="stylesheet" type="text/css" media="all" href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui-1.12.1.min.css" />
 * 		<script type="text/javascript" src="${pageContext.servletContext.contextPath}/library/jquery/jquery-1.12.0.min.js"></script>
 *		<script type="text/javascript" src="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"></script> 	
 *  
 * 3.) create a link to the resource to be displayed in a modal. ie:
 * 
 *		<a href="/dataformodal.jsp">Click for modal</a>
 *
 * 4.) add class name: oscar-dialog-link to the link.
 * 
 * 		<a class="oscar-dialog-link" href="/dataformodal.jsp">Click for modal</a>
 * 
 * 5.) The page where the link points to will open in a modal.
 * 
 * This is a living script. 
 */

// Jquery modal windows
jQuery(document).ready(function () {

    jQuery(function () {

        jQuery("body").first().append('<div style="padding:10px;" id="oscar-dialog"></div>');

        jQuery("#oscar-dialog").dialog({
            autoOpen: false,
            modal: true,
            width: 'auto',
            height: 'auto',
            resizable: true
        });

        jQuery(".oscar-dialog-link").on("click", function (event) {
            event.preventDefault();
            var url = jQuery(this).attr("href");
            jQuery("#oscar-dialog").load(url).dialog('open');
        });
    });
});
