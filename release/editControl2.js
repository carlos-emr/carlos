/*  editControl2.js - WYSIWYG rich text editor for the Rich Text Letter eForm
    Copyright (C) 2009-2020 Peter Hutten-Czapski
    Modernized 2026 by CARLOS EMR Project (2026.3.0)

    This file is deployed from WEB-INF/eform-assets/ to the eForm images directory
    by EFormAssetDeployer at Tomcat startup. It is loaded by the RTL eForm's form_html
    via: <script src="../eform/displayImage?imagefile=editControl2.js"></script>

    Key architecture notes:
    - This script builds a WYSIWYG toolbar and editor iframe using document.designMode
    - Content insertion uses the Selection/Range API (execCommand('insertHtml') is deprecated)
    - The RTL eForm's sidebar buttons (Patient Name, Allergies, etc.) call printKey()
      which uses APCache.js to fetch patient data via AJAX, then calls doHtml() to insert it
    - Template management loads .rtl files via efmformrtl_templates
    - The Start() function is called from <body onload="Start()"> in the DB-stored form_html

    Version 1.6 now about 600 lines of code
        NEW in 0.2 button styles, links, select box
        NEW in 0.3 help, date, rule, select all, and clean functions
        NEW in 0.4 code completely rewritten, more functions including images and
            now more modular and can support IE, including spelling for IE under 300 lines
        NEW in 0.5 template loading with support for placeholder form letter fields
        NEW in 0.8 minor code cleanup and bugfixes
        NEW in 1.1 first commit to cvs
        NEW in 1.2 bugfix for button style mouse behavior and 5 more buttons/functions
        NEW in 1.3 support for IE template loading, cut, copy, paste buttons/functions
        NEW in 1.3i grafted on compatibility with signature and faxing features
        NEW in 1.4 support for Firefox FF18+ browsers (ionMonkey series)
        NEW in 1.5 restored support for images, measurements and user template default values lost in 1.3i
        NEW in 1.6 restored button support for newest Firefox ESR 24
        NEW in 2.0 added support for page break and file attachment and font awesome
2.01 tweaks
    * Requirements: DesignMode and other Dom 2 methods
    * Mozilla 1.3+ IE 5.5+ Netscape 6+ Opera 9+ Konqueror 3.5.7+ Safari 1.3+ Chrome
    * designed for and tested on Firefox 2 - 20.  Tested on Opera 10, Chromium 25 and IE 6/7

    This is a simplistic emulation of Xinha and TinyMCE javascript texteditors

    Released under the GNU Lesser General Public License as published
    by the Free Software Foundation; either version 2.1 of the License,
    or (at your option) any later version.

    * * *
    *
    * USAGE: Put the following snippit in your webpage where you want the control to appear
    *        Put editControl.js, blank.rtl in the same directory as the webpage
    *
    * * *

		//some of the optional configuration variables with their defaults go here
		// cfg_width = '700';					//editor control width in pixels
		// cfg_height = '400';					//editor control height in pixels
		// cfg_layout = '[select-block]|[bold][italic]|[unordered][ordered][rule]|[undo][redo]|[indent][outdent][select-all][clean]|[clock][help]<br />[edit-area]';
						// [select-block] an option list for paragraph and header styles
						// [select-face] an option list for selecting font face
						// [select-size] an option list for selecting font size
						// [select-template] an option list for selecting base content and style
						// | a cosmetic seperator bar
						// [bold] a button that toggles bold of the selected text
						// [italic] a button that toggles emphasis text
						// [underlined] a button that toggles underlined text
						// [strike] a button that toggles strike trough text
						// [subscript] a button that toggles subscript text
						// [superscript] a button that toggles superscript text
						// [text-colour] a button that applies text colour
						// [hilight] a button that applies text high lighting colour
						// [left] a button that left justifies text
						// [center] a button that center justifies text
						// [full] a button that fully justifies text
						// [right] a button that right justifies text
						// [unordered]a button that creates a bulleted list
						// [ordered] a button that creates an ordered list
						// [rule]a button that creates a horizontal rule
						// [undo]a button that undoes the last action(s)
						// [redo]a button that redoes the last action(s)
						// [heading1] inserts a heading IF select-block is not available
						// [indent]a button that indents the text
						// [outdent]a button that outdents the text
						// [select-all]a button that selects all text
						// [clean]a button that removes font formatting from all selected text
						// [table]a button that inserts a table
						// [link]a button that inserts a URL of a link
						// [image]a button that inserts an image
						// [date] a button that adds the current date to the form
						// [clock] a button that adds the current time to the form
						// [help] a button that loads a help window
						// [spell] a button that invokes a spell checker
						// [cut] a button that cuts the selected text
						// [copy] a button that copies to windows clipboard
						// [delete] a button that deletes the selected content
						// [attach] a button that attaches Documents and other files to the RTL
						// [npage] a button that inserts a page break
						// [export] a button that exports the iframe contents in html to a rtl file
						// [edit] a button that edits the current template
						// deprecated [paste] a button that pastes from the windows clipboard for IE
						// deprecated [spell] a button that pastes from the windows clipboard for IE
						// <br /> an embedded html element; you can add them freely in the layout
						// [edit-area] the location of the iFrame that contains the editor

		insertEditControl(); // Initialise the edit control

		// To set the HTML contents of this edit control use JavaScript to call: seteditControlContents(IdString,html)
		//  - e.g. for putting "bar" into a control called "foo", call seteditControlContents("foo","<p>bar</p>");

		// To retrieve the HTML contents of this edit control call: editControlContents(string)
		//   - e.g. for a control called "foo", call editControlContents("foo");

*/
//GLOBALS
var cfg_layout = '[select-block]|[bold][italic]|[unordered][ordered][rule]|[undo][redo]|[indent][outdent][select-all][clean]|[clock][spell][help]<br />[edit-area]';
var cfg_formatblock = '<option value="">&mdash; format &mdash;</option>  <option value="<p>">Paragraph</option>  <option value="<h1>">Heading 1</option>  <option value="<h2>">Heading 2</option>  <option value="<h3>">Heading 3</option>  <option value="<h4>">Heading 4</option>  <option value="<h5>">Heading 5</option>  <option value="<h6>">Heading 6</option>  </select>';
var cfg_formatface = '<option value="">&mdash; font face &mdash;</option>  <option value="Arial,Helvetica,sans-serif">Arial</option> <option value="Courier">Courier</option> <option value="Times New Roman">Times</option> </select>';
// Approximate CSS pixels at default browser font settings; retain legacy 1-7 values
// so formatting commands and existing saved letters keep their original behavior.
var cfg_formatfontsize = '<option value="">&mdash; font size &mdash;</option>  <option value="1">&asymp;10px</option>  <option value="2">&asymp;13px</option> <option value="3">&asymp;16px</option> <option value="4">&asymp;18px</option> <option value="5">&asymp;24px</option> <option value="6">&asymp;32px</option> <option value="7">&asymp;48px</option> </select>';
var cfg_formattemplate = '<option value="">&mdash; template &mdash;</option>  <option value="blank.rtl">blank</option>  </select>';
var cfg_isrc = '';  				// path to icons degrades to text buttons if icons not found
var cfg_filesrc = '';				// path to blank.html and editor_help.html
var cfg_template = 'blank.rtl';		// style and content template of the editor's iframe itself.
var cfg_width = 720;				// editor control width in pixels
var cfg_height = 500;				// editor control height in pixels
var cfg_editorname ="edit";		// handle for the editor control itself
var cfg_bstyle = 'width:24px;height:24px;border: solid 2px #ccccff; background-color: #ccccff;'; 	//the CSS of the button elements
window.formIsRTL = false;			// efmshowform_data reads this global to detect RTL eForms
var cfg_boutstyle = 'solid 2px #ccccff'; 	//the CSS of the button elements om mouse out
var cfg_sstyle = 'vertical-align: top; height:24px;';//the CSS of the option select box.  Selects will take font and background but not border.
var cfg_sepstyle = 'width:6px;height:24px;border: solid 2px #ccccff; background-color: #ccccff;';	//the CSS of the seperator icon


/**
 * Sanitizes HTML using DOMPurify when available. Returns null when DOMPurify
 * is not loaded, signaling the caller to use a safe fallback (e.g., textContent).
 * Centralizes the sanitization policy so every innerHTML sink goes through one gate.
 *
 * @param {string} html - Raw HTML string to sanitize
 * @param {Object} [config] - Optional DOMPurify configuration (e.g., ALLOWED_TAGS)
 * @returns {string|null} Sanitized HTML string, or null if DOMPurify is unavailable
 */
function sanitizeHtml(html, config) {
	if (typeof DOMPurify !== 'undefined') {
		return DOMPurify.sanitize(html, config);
	}
	if (typeof console !== 'undefined' && typeof console.warn === 'function') {
		console.warn('editControl2: DOMPurify not loaded — caller should use safe fallback to prevent XSS.');
	}
	return null;
}

function insertEditControl() {
	// The main initialising function which writes the edit control as per passed variables
	// ...OR... if it fails, degrades nicely by supplying a text area with the same ID (cfg_editorname)

	// FIRST BUILD BUTTONS WITH USEFUL ATTRIUBUTES
	// Mozilla requires the title attribute for tool tips, and that works in IE as well
	// The ID matches a execCommand argument and carries the action associated with the button
	// when the action needs a value cmdValue="promptUser" will prompt the user with the promptText attribute
	// the class="editControlButton" identifies the elements that will have button function
	//   -these will change appearance on mouse events and will trigger on mouse click a default action

    var boldButton = '<span class="editControlButton" value="Bold" title="Bold" name="' + cfg_editorname + '" onclick="ex(\'bold\');"><i class="fa-solid fa-bold"></i></span>';
    var italicButton =  '<span class="editControlButton" value="Italic" title="Italic" name="' + cfg_editorname + '" onclick="ex(\'italic\');"><i class="fa-solid fa-italic"></i></span>';
    var underlinedButton =  '<span class="editControlButton" value="Underline" title="Underline" name="' + cfg_editorname + '" onclick="ex(\'underline\');"><i class="fa-solid fa-underline"></i></span>';
    var strikethroughButton = '<span class="editControlButton" title="Strikethrough" name="' + cfg_editorname + '" onclick="ex(\'strikethrough\');"><i class="fa-solid fa-strikethrough"></i></span>';
    var superscriptButton = '<span class="editControlButton" value="Superscript" title="Superscript" name="' + cfg_editorname + '" onclick="ex(\'superscript\');"><i class="fa-solid fa-superscript"></i></span>';
    var subscriptButton = '<span class="editControlButton" title="Subscript" name="' + cfg_editorname + '" onclick="ex(\'subscript\');"><i class="fa-solid fa-subscript"></i></span>';
    var leftButton = '<span class="editControlButton" title="Align Left" name="' + cfg_editorname + '" onclick="ex(\'justifyLeft\');"><i class="fa-solid fa-align-left"></i></span>';
    var centerButton = '<span class="editControlButton" title="Center Align" name="' + cfg_editorname + '" onclick="ex(\'justifyCenter\');"><i class="fa-solid fa-align-center"></i></span>';
    var fullButton = '<span class="editControlButton" title="Justify" name="' + cfg_editorname + '" onclick="ex(\'justifyFull\');"><i class="fa-solid fa-align-justify"></i></span>';
    var rightButton = '<span class="editControlButton" title="Align Right" name="' + cfg_editorname + '" onclick="ex(\'justifyRight\');"><i class="fa-solid fa-align-right"></i></span>';
	var unorderedlistButton = '<span class="editControlButton" title="List" name="' + cfg_editorname + '" onclick="ex(\'insertUnorderedList\');"><i class="fa-solid fa-list-ul"></i></span>';
	var orderedlistButton = '<span class="editControlButton" title="Numbered List" name="' + cfg_editorname + '" onclick="ex(\'insertOrderedList\');"><i class="fa-solid fa-list-ol"></i></span>';
	var ruleButton = '<span class="editControlButton" title="Horizontal Rule" name="' + cfg_editorname + '" onclick="ex(\'insertHorizontalRule\');"><i class="fa-solid fa-ellipsis"></i></span>';
	var selectBlock = '<select name="' + cfg_editorname + '" id="formatblock" onchange="Select(this.id);" style="' + cfg_sstyle +'">'+ cfg_formatblock ;
	var selectFace = '<select name="' + cfg_editorname + '" id="fontname" onchange="Select(this.id);" style="' + cfg_sstyle +'">'+ cfg_formatface ;
	var selectSize = '<select name="' + cfg_editorname + '" id="fontsize" onchange="Select(this.id);" style="' + cfg_sstyle +'">'+ cfg_formatfontsize ;
    var selectTemplate = '<select name="' + cfg_editorname + '" id="template" onchange="loadTemplate(this.id);" style="' + cfg_sstyle +'">'+ cfg_formattemplate ;
	var undoButton = '<span class="editControlButton" title="Undo" name="' + cfg_editorname + '" onclick="ex(\'undo\');"><i class="fa-solid fa-rotate-left"></i></span>';
	var redoButton = '<span class="editControlButton" title="Redo" name="' + cfg_editorname + '" onclick="ex(\'redo\');"><i class="fa-solid fa-rotate-right"></i></span>';
	var indentButton = '<span class="editControlButton" title="Indent" name="' + cfg_editorname + '" onclick="ex(\'indent\');"><i class="fa-solid fa-indent"></i></span>';
	var outdentButton = '<span class="editControlButton" title="Outdent" name="' + cfg_editorname + '" onclick="ex(\'outdent\');"><i class="fa-solid fa-outdent"></i></span>';
	var selectAllButton = '<span class="editControlButton" title="Select All" name="' + cfg_editorname + '" onclick="ex(\'selectAll\');"><i class="fa-solid fa-expand"></i></span>';
    var cleanButton = '<span class="editControlButton" title="Remove Formatting" name="' + cfg_editorname + '" onclick="ex(\'removeFormat\');"><i class="fa-solid fa-eraser"></i></span>';
	var tableButton = '<span class="editControlButton" title="Table" name="' + cfg_editorname + '" onclick="doHtml(doTable());"><i class="fa-solid fa-table"></i></span>';
	var textcolourButton = '<span class="editControlButton colours" title="Text Colour" name="' + cfg_editorname + '" onclick="exprompt(\'foreColor\',\'Text Colour?[red]\');"><i class="fa-solid fa-droplet"></i></span>';
	var hilightcolourButton = '<span class="editControlButton highlights" title="Highlight" name="' + cfg_editorname + '" onclick="exprompt(\'backColor\',\'Highlight Colour?[yellow]\');"><i class="fa-regular fa-square"></i></span>';
    var insertImageButton = '<span class="editControlButton" title="Insert Image" name="' + cfg_editorname + '" onclick="exprompt(\'insertImage\',\'URL of image?\');"><i class="fa-solid fa-image"></i></span>';
    var insertLinkButton = '<span class="editControlButton" title="Link" name="' + cfg_editorname + '" onclick="exprompt(\'createLink\',\'URL of link?[http://www.srpc.ca]\');"><i class="fa-solid fa-link"></i></span>';
	var attachButton = '<span class="editControlButton" title="Attach" name="' + cfg_editorname + '" onclick="popupEformUpload();"><i class="fa-solid fa-paperclip"></i></span>';
	var newpageButton = '<span class="editControlButton" title="Page Break" name="' + cfg_editorname + '" onclick="doHtml(doBreak());"><i class="fa-solid fa-file"></i></span>';
	var clockButton = '<span class="editControlButton" title="Time" name="' + cfg_editorname + '" onclick="doHtml(doTime());"><i class="fa-regular fa-clock"></i></span>';
	var dateButton = '<span class="editControlButton" title="Date" name="' + cfg_editorname + '" onclick="doHtml(doDate());"><i class="fa-regular fa-calendar"></i></span>';
	var helpButton = '<span class="editControlButton" title="Help" name="' + cfg_editorname + '" onclick="window.open (\''+cfg_filesrc+'editor_help.html\',\'mywindow\',\'resizable=1,width=300,height=500\');"><i class="fa-solid fa-circle-question"></i></span>';
	var editButton = '<span class="editControlButton" title="Edit Template" name="' + cfg_editorname + '" onclick="doEdit();"><i class="fa-solid fa-pen-to-square"></i></span>';
	var exportButton = '<span class="editControlButton" title="Export" name="' + cfg_editorname + '" onclick="doExport();"><i class="fa-solid fa-floppy-disk"></i></span>';
	var insertHeading1Button = '<span class="editControlButton" title="Heading" name="' + cfg_editorname + '" onclick="ex(\'formatBlock\',\'<H1>\');"><i class="fa-solid fa-heading"></i></span>';
    var cutButton = '<span class="editControlButton" title="Cut" name="' + cfg_editorname + '" onclick="ex(\'cut\');"><i class="fa-solid fa-scissors"></i></span>';
    var deleteButton = '<span class="editControlButton" title="Delete" name="' + cfg_editorname + '" onclick="ex(\'delete\');"><i class="fa-solid fa-trash"></i></span>';
    // paste deprecated long ago by all browsers
    //var pasteButton = '<span class="editControlButton" title="Paste" name="' + cfg_editorname + '" onclick="ex(\'paste\');"><i class="fa-solid fa-paste"></i></span>';
    var copyButton = '<span class="editControlButton" title="Copy" name="' + cfg_editorname + '" onclick="ex(\'copy\');"><i class="fa-solid fa-copy"></i></span>';

	var separator = '|';

	var editControl =  '<iframe id="' + cfg_editorname + '" style="width:' + cfg_width + 'px; height:' + cfg_height + 'px; border-style:inset; border-width:thin;" frameborder="0px"></iframe>';
	
	// SECOND GET THE LAYOUT STRING PASSED AND REPLACE IT WITH THE BUTTONS AS REQUESTED
	var editControlHTML = cfg_layout;

	if (editControlHTML=="[all]"){
        editControlHTML =  '<table style="background-color:  #E1E1E1; width:'+cfg_width+'px"><tr id=control1><td align=center>\
[bold][italic][underlined][strike][subscript][superscript]|[left][center][full][right]|[unordered][ordered][rule][undo][redo]|[indent][outdent][select-all][clean]\
</td></tr><tr id=control2><td align=center>\
[heading1][table][text-colour][hilight]|[image][link][attach][new-page]|[clock][date][cut][copy][delete][help]|[edit][export]\
</td></tr><tr id=control3><td align=center>[select-block][select-face][select-size][select-template]\
</td></tr></table>[edit-area]';    
    };
	editControlHTML = editControlHTML.replace("[bold]", boldButton);
	editControlHTML = editControlHTML.replace("[italic]", italicButton);
	editControlHTML = editControlHTML.replace("[underlined]", underlinedButton);
	editControlHTML = editControlHTML.replace("[strike]", strikethroughButton);
	editControlHTML = editControlHTML.replace("[superscript]", superscriptButton);
	editControlHTML = editControlHTML.replace("[subscript]", subscriptButton);
	editControlHTML = editControlHTML.replace("[left]", leftButton);
	editControlHTML = editControlHTML.replace("[center]", centerButton);
	editControlHTML = editControlHTML.replace("[full]", fullButton);
	editControlHTML = editControlHTML.replace("[right]", rightButton);
	editControlHTML = editControlHTML.replace("[select-face]", selectFace);
	editControlHTML = editControlHTML.replace("[select-size]", selectSize);
	editControlHTML = editControlHTML.replace("[undo]", undoButton);
	editControlHTML = editControlHTML.replace("[redo]", redoButton);
	editControlHTML = editControlHTML.replace("[select-all]", selectAllButton);
	editControlHTML = editControlHTML.replace("[text-colour]", textcolourButton);
	editControlHTML = editControlHTML.replace("[hilight]", hilightcolourButton);
	editControlHTML = editControlHTML.replace("[image]", insertImageButton);
	editControlHTML = editControlHTML.replace("[link]", insertLinkButton);
	editControlHTML = editControlHTML.replace("[unordered]", unorderedlistButton);
	editControlHTML = editControlHTML.replace("[ordered]", orderedlistButton);
	editControlHTML = editControlHTML.replace("[rule]", ruleButton);
	editControlHTML = editControlHTML.replace("[clean]", cleanButton);
	editControlHTML = editControlHTML.replace("[indent]", indentButton);
	editControlHTML = editControlHTML.replace("[outdent]", outdentButton);
	editControlHTML = editControlHTML.replace("[select-block]", selectBlock);
	editControlHTML = editControlHTML.replace("[heading1]", insertHeading1Button);
	editControlHTML = editControlHTML.replace("[table]", tableButton);
	editControlHTML = editControlHTML.replace("[help]", helpButton);
   	editControlHTML = editControlHTML.replace("[clock]", clockButton);
   	editControlHTML = editControlHTML.replace("[date]", dateButton);
	editControlHTML = editControlHTML.replace(/\|/g, separator);
	editControlHTML = editControlHTML.replace("[edit-area]", editControl);
	editControlHTML = editControlHTML.replace("[select-template]", selectTemplate);
    editControlHTML = editControlHTML.replace("[new-page]", newpageButton);
    editControlHTML = editControlHTML.replace("[attach]", attachButton);
    editControlHTML = editControlHTML.replace("[export]", exportButton);
    editControlHTML = editControlHTML.replace("[edit]", editButton);
	editControlHTML = editControlHTML.replace("[cut]", cutButton);
	editControlHTML = editControlHTML.replace("[copy]", copyButton);
	editControlHTML = editControlHTML.replace("[delete]", deleteButton);
	editControlHTML = editControlHTML.replace("[paste]", "");

	// THIRD WRITE THE EDIT CONTROL TO THE WEB PAGE
	// Use document.currentScript to insert at the script's location rather than end of body
	// This ensures correct placement for JSPs that include the script mid-form
	var _insertionPoint = document.currentScript || (function() {
		var scripts = document.getElementsByTagName('script');
		return scripts[scripts.length - 1];
	})();
	if (document.designMode) {
        //console.log(editControlHTML);
		_insertionPoint.insertAdjacentHTML('afterend', editControlHTML);
		//InitToolbarButtons(cfg_editorname);
	} else {
		// create a normal <textarea> if document.designMode does not exist
		//alert("Design mode is not supported by your browser \n- reverting to classic mode");
		var ta = document.createElement('textarea');
		ta.id = cfg_editorname;
		var editControlStyle = '';
		ta.setAttribute('style', 'width:' + cfg_width + '; height:' + cfg_height + 'px; ' + editControlStyle);
		_insertionPoint.parentNode.insertBefore(ta, _insertionPoint.nextSibling);
	}
}

function isIE(){
	//this function introduced in v1.4 required as object testing for window[editorname] fails in ionMonkey
	var agent=navigator.userAgent.toLowerCase();
	if ((agent.indexOf("msie") != -1) && (agent.indexOf("opera") == -1)) {
		//Browser is Microsoft Internet Explorer : Can load browser specific code
		return true;
	} else {
	return false;
	}
}

function exprompt(command, promptText){
    var value = prompt(promptText);
    ex(command,value);
}

function ex(command,value){
	document.getElementById(cfg_editorname).contentWindow.document.execCommand(command, false, value); 
}

function editControlContents(editorname, allowPendingMeasurements) {
	// Template expansion reads content too; only those internal reads may bypass
	// the save/print gate so changing a template still completes while a request runs.
	if (!allowPendingMeasurements) { assertMeasurementHistoryReady(); }
	var value = "";
	// HARD STOP: the stored letter never made it into the editor (see seteditControlContents'
	// fallback branch). Every caller of this function feeds its result to a save, so returning the
	// empty editor here is what overwrites a clinician's stored letter with nothing. Throwing
	// aborts the inline onclick handler before document.RichTextLetter.submit() runs, so the save
	// cannot proceed. Detection alone was not enough — the previous version only logged.
	if (window.__carlosEditorLoadFailed) {
		alert('Saving is disabled for this letter.\n\nThe saved content never loaded into the editor, '
			+ 'so saving now would erase it. Your stored letter is still intact — close this form '
			+ 'without saving and contact your administrator.');
		throw new Error('editControl: refusing to read contents; the stored letter failed to load');
	}
	// this function retrieves the HTML contents of the edit control "editorname"
	if (document.designMode) {
		// Explorer reformats HTML during document.write() removing quotes on element ID names
		// so we need to address Explorer elements as window[elementID]
		if (isIE()) { value = window[editorname].document.body.innerHTML; }
		else { value = document.getElementById(editorname).contentWindow.document.body.innerHTML; }
	} else {
		// play nice and at least return the value from the <textarea> if document.designMode does not exist
		value = document.getElementById(editorname).value;
	}
	return jQuery().restoreImagePaths(value);
}

// this function sets the HTML contents of the edit control "editorname" to "value"
// isStoredContent: true only when restoring a SAVED letter. The new-form template path
// (populateTemplate) also writes through here before designMode is enabled, and losing a blank
// template costs nothing — so only a stored-content write that cannot land is treated as data loss.
function seteditControlContents(editorname, value, isStoredContent){

	// Converting image paths with template style tag to URL format using 'cfg_isrc' using imageControl library.
	value = jQuery().convertImagePaths(value, cfg_isrc);

	// Sanitize HTML via centralized helper; null means DOMPurify unavailable → fail closed with textContent.
	var sanitized = sanitizeHtml(value);

	// Get the editor iframe's document — designMode is set on the iframe, not the top-level document
	var editorDoc;
	if (isIE()) {
		editorDoc = window[editorname] ? window[editorname].document : null;
	} else {
		var editorIframe = document.getElementById(editorname);
		editorDoc = editorIframe && editorIframe.contentWindow ? editorIframe.contentWindow.document : null;
	}

	if (editorDoc && editorDoc.designMode === 'on') {
		if (sanitized !== null) {
		    editorDoc.body.innerHTML = sanitized; // nosemgrep: javascript.browser.security.insecure-document-method.insecure-document-method
		} else {
		    editorDoc.body.textContent = value;
		}
		return
	} else {
		// Fallback for a browser without designMode, where createEditControl() built a plain
		// <textarea> instead of an editable iframe. Guard on the element actually being a form
		// control: assigning .value to an iframe silently discards the letter, which is exactly
		// how saved letters used to vanish on reopen. Fail loudly instead of losing content.
		var fallbackTarget = document.getElementById(editorname);
		if (fallbackTarget && typeof fallbackTarget.value === 'string'
				&& fallbackTarget.tagName && fallbackTarget.tagName.toLowerCase() !== 'iframe') {
			// Sanitize here too: the designMode branch above writes `sanitized`, and this branch
			// must not be the weaker path just because the target is a textarea.
			fallbackTarget.value = sanitized !== null ? sanitized : value;
			return
		}
		if (typeof console !== 'undefined' && console.error) {
			console.error('editControl: cannot set editor contents — the editor document is not in '
				+ 'designMode and the target is not a text control.');
		}
		if (isStoredContent) {
			// A SAVED letter is not in the editor and there is nowhere safe to put it. A console
			// message is invisible to the clinician, who sees an empty editor for a letter that has
			// content and may then save over it. Latch a flag editControlContents() refuses to read
			// past, and say so where it will actually be seen.
			window.__carlosEditorLoadFailed = true;
			alert('This letter could not be loaded into the editor.\n\nDo NOT save — your stored letter '
				+ 'is still intact. Close this form and contact your administrator.');
		}
		return
	}
}


function Select(selectname){
  	var cursel = document.getElementById(selectname).selectedIndex;
  	if (cursel != 0) { // First one is a label
    	var selected = document.getElementById(selectname).options[cursel].value;
    	if (isIE()) { window[cfg_editorname].document.execCommand(selectname, false, selected); } //if browser supports M$ conventions
	else { document.getElementById(cfg_editorname).contentWindow.document.execCommand(selectname, false, selected); }
    	document.getElementById(selectname).selectedIndex = 0;
  	document.getElementById(cfg_editorname).contentWindow.focus();
  	}
}

/**
 * Turns designMode on in the editor iframe's CURRENT document. Every template load (blank.rtl or a
 * clinic .rtl) navigates the iframe, and the parent's iframe.onload handler runs BEFORE the template's
 * own <body onload="document.designMode='on'"> has executed, so parseTemplate() reached
 * seteditControlContents() with designMode still off. That function refuses to write into a
 * non-designMode iframe (it is the guard that stops saved letters vanishing), logged a console error
 * on every new letter, and dropped the parsed template — harmless for the empty blank.rtl, but a
 * clinic template with letterhead and ##placeholders## never populated. Enabling it here, before the
 * parse, is order-independent: the template's own onload then finds it already on.
 */
function enableEditorDesignMode() {
	var frame = document.getElementById(cfg_editorname);
	var frameDoc;
	try { frameDoc = frame && frame.contentWindow ? frame.contentWindow.document : null; }
	catch (e) { frameDoc = null; } // cross-origin frame: nothing to do (and nothing we could edit)
	if (frameDoc && frameDoc.designMode !== 'on') { frameDoc.designMode = 'on'; }
}

/**
 * Marks the letter dirty on edits so remotePrint() ("Save and then print") and confirmExit() know
 * there is something to save. Registered on the iframe's CURRENT window and re-registered after
 * every template load: loading blank.rtl (or a clinic .rtl) navigates the iframe, which replaces
 * its Window object and silently drops every listener registered on the old one. Registering once
 * from Start() therefore only ever covered the pre-template about:blank document, so on every NEW
 * letter typing never set needToConfirm — the toolbar's Print printed the letter without saving it,
 * and closing the window never warned about the unsaved text.
 *
 * `keypress` alone misses Backspace/Delete, paste, and the toolbar's execCommand formatting, all of
 * which change what would be saved; `input` fires for those too. setDirtyFlag() is idempotent, so
 * double-firing on ordinary typing is harmless.
 */
function attachDirtyFlagListener() {
	if (typeof setDirtyFlag !== 'function') { return; }
	var frame = document.getElementById(cfg_editorname);
	var frameWindow = frame ? frame.contentWindow : null;
	if (!frameWindow || typeof frameWindow.addEventListener !== 'function') { return; }
	frameWindow.addEventListener('keypress', setDirtyFlag, true);
	frameWindow.addEventListener('input', setDirtyFlag, true);
}

function existsTemplate(template) {
	var exists = false;
	$("#template option").each(function() { if ($(this).val() == template) { exists = true; } })
	return exists;	
}

function loadDefaultTemplate() {
	// Skipping loading of default template if the letter already has content.
	if (editControlContents(cfg_editorname, true).trim() != '') { return; }
	if (existsTemplate(cfg_template)) {
		var selected = cfg_template;
		window.frames[0].location = cfg_filesrc + selected; //FF & IE ***ASSUMES 1 iframe!
		document.getElementById('subject').value = cfg_template == 'blank.rtl' ? "" : selected.substring(0, selected.lastIndexOf("."));		
    	document.getElementById('template').selectedIndex = 0;
		//need to ensure that the new src is loaded before we parse it FF only IE doesn't do nada
		var obj = document.getElementById(cfg_editorname);
		// The navigation replaced the iframe's Window: re-register the dirty-flag listener on the new one.
		obj.onload = function() { enableEditorDesignMode(); parseTemplate(); attachDirtyFlagListener(); };
		//for IE put some delay to ensure that the new src is loaded before we parse it
    	if (isIE()) { setTimeout(parseTemplate, 1000); } //if M$ like browser
	} else {
		var blankTemplate = '<html><head><title>Blank Document Template</title><meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\"><style type=\"text/css\">body {font-size: 1em; font-family:\"Times New Roman\", Times, serif; background-color: #FFFFFF;}</style><style type=\"text/css\" media=\"print\">* {color: #000000;}.DoNotPrint {display: none;}</style></head><body contenteditable onLoad=\"document.designMode = \'on\';\"></body></html>';
		var blankFrame = document.getElementById(cfg_editorname);
		// srcdoc navigates the iframe too, so the listener has to follow the new Window here as well.
		blankFrame.onload = function() { enableEditorDesignMode(); attachDirtyFlagListener(); };
		blankFrame.srcdoc = blankTemplate;
	}
}

function loadTemplate(selectname){
	//change the iframe src to that selected in the template select box
	//TODO fix the eventlistener! in the meantime just set the dirty flag
	setDirtyFlag();
  	var cursel = document.getElementById(selectname).selectedIndex;
  	if (cursel != 0) { // First one is a label
    	var selected = document.getElementById(selectname).options[cursel].value;
		// Security: validate template name to prevent path traversal or script injection via iframe src
		if (!/^[\w.\- ]+$/.test(selected)) { console.warn('loadTemplate: invalid template name:', selected); return; }
		//document.getElementById(cfg_editorname).src = cfg_filesrc + selected + '.html' ; //FF != IE
		window.frames[0].location = cfg_filesrc + selected; //FF & IE ***ASSUMES 1 iframe!
		document.getElementById('subject').value = selected == 'blank.rtl' ? "" : selected.substring(0, selected.lastIndexOf("."));		
    	document.getElementById('template').selectedIndex = 0;
		//need to ensure that the new src is loaded before we parse it FF only IE doesn't do nada
		var obj = document.getElementById(cfg_editorname);
		// The navigation replaced the iframe's Window: re-register the dirty-flag listener on the new one.
		obj.onload = function() { enableEditorDesignMode(); parseTemplate(); attachDirtyFlagListener(); };
		//for IE put some delay to ensure that the new src is loaded before we parse it
    		if (isIE()) { setTimeout(parseTemplate, 1000); } //if M$ like browser
    	}
}

function parseTemplate(){
	//replace template placeholders with database pulls
	var contents=editControlContents(cfg_editorname, true);
	var temp = contents.split('##'); //parse for template place holders identified by ##value##
	var keys = [];
	var needLookup = false;
	var x;
	for (x in temp) {
		if ((x % 2)){ //odd numbered values contain placeholders
			if(!cache.contains(temp[x])){
				needLookup = true;
				if (cache.getMapping(temp[x]) != null) {
					var mapKeys = cache.getMapping(temp[x]).values;
					var index;					
					for (index = 0; index < mapKeys.length; index++) {
						keys.push(mapKeys[index]);
					}					
				}
				keys.push(temp[x]);				
			}
		}		
	}
	if (!needLookup) { populateTemplate(); }
	else {
		var templateMapping = cache.getMapping("template");
		if (templateMapping != null) {			
			templateMapping.values = keys;
			cache.lookup("template");	
		}		 
	}
}

function populateTemplate(){
	//replace template placeholders with database pulls
	var contents=editControlContents(cfg_editorname, true);
	var temp = contents.split('##'); //parse for template place holders identified by ##value##
	contents='';
	var x;
	for (x in temp) {		 
		if ((x % 2)){ //odd numbered values contain placeholders
			if(cache.contains(temp[x]) && (cache.get(temp[x]).length>0)){
				//known field placeholder with a value so use it
				temp[x]=cache.get(temp[x]);
			} else {
				//try to get the placeholder value from measurements
				if((document.getElementById(temp[x]))&&(document.getElementById(temp[x]).value.length>0)){
					//supplied measurement placeholder with a value so use it
					temp[x]=document.getElementById(temp[x]).value;
				} else {
				//get the placeholder value from the user
				var prompttext = temp[x].split('=');
					if (prompttext[1]==undefined){prompttext[1]="";}
				temp[x]= prompt("Please supply a value for "+ prompttext[0], prompttext[1]);
				if (temp[x] == null) { temp[x] = ""; }
				}
			}
		}
		contents += temp[x];
	}
	seteditControlContents(cfg_editorname,contents);
}

function parseText(obs) {
	//clean up OSCAR formated case management notes leaving the last entry
	var myRe= /^(.|\n|\r)*-{5}/g;
	if (obs.match(myRe)) {
		var obs=obs.replace(myRe,"");
	}
	return obs;
}

/**
 * Inserts HTML content at the current cursor position in the editor iframe.
 * This is the primary content insertion mechanism used by all sidebar buttons
 * (Patient Name, Allergies, Prescriptions, etc.) and the AP cache system.
 *
 * Uses the standard Selection/Range API to insert a document fragment at the
 * current cursor position. Falls back to appending to the body if no selection
 * exists (e.g., editor was never clicked/focused).
 *
 * @param {string} value - HTML string to insert (e.g., "<b>Aleshia Jones</b>")
 */
function doHtml(value) {
	var editorDoc = document.getElementById(cfg_editorname).contentWindow.document;
	// Sidebar/APCache HTML is untrusted and must be sanitized before DOM insertion.
	var safeValue = sanitizeHtml(value);

	// Insert at cursor using the Selection/Range API
	var sel = editorDoc.getSelection ? editorDoc.getSelection() : null;
	if (sel && sel.rangeCount > 0) {
		var range = sel.getRangeAt(0);
		range.deleteContents();
		if (safeValue !== null) {
			// createContextualFragment parses the sanitized HTML string into DOM nodes
			var frag = range.createContextualFragment(safeValue);
			range.insertNode(frag);
		} else {
			range.insertNode(editorDoc.createTextNode(value));
		}
		// Move cursor to end of inserted content so subsequent inserts append
		range.collapse(false);
		sel.removeAllRanges();
		sel.addRange(range);
	} else {
		// No selection/cursor — append to end of document body.
		// Sanitize via centralized helper; null means DOMPurify unavailable → fail closed with textContent.
		if (safeValue !== null) {
			var appendRange = editorDoc.createRange();
			appendRange.selectNodeContents(editorDoc.body);
			var appendFrag = appendRange.createContextualFragment(safeValue);
			editorDoc.body.appendChild(appendFrag);
		} else {
			editorDoc.body.appendChild(editorDoc.createTextNode(value));
		}
	}
	bindStampFallbacks(editorDoc);
	// Return focus to the editor iframe so the user can continue typing
	// immediately after a sidebar button inserts content
	document.getElementById(cfg_editorname).contentWindow.focus();
}

/**
 * Attaches the stamps.js / stamp.png fallback to a freshly inserted provider stamp.
 *
 * pickStamp() marks a per-provider stamp with STAMP_MARKER_ATTRIBUTE. The handler cannot be an
 * inline onerror: doHtml() runs every insertion through DOMPurify, which strips event-handler
 * attributes, so it is bound here instead — after insertion, in the same task, before the image can
 * finish loading.
 *
 * This is what makes the fallback a guarantee rather than a race. probeProviderStamp() usually
 * answers first and pickStamp() then emits the right URL outright, but on a form_html that predates
 * the hidden identity inputs the provider number only arrives with the APCache lookup, so the very
 * first Stamp click can be issued before any probe has completed. Without this the letter would
 * keep a broken image and the documented fallback would never run.
 *
 * SECURITY: the marker carries no data. The fallback FILENAME is held in carlosPendingStampFallback
 * and never round-trips through the DOM, because this scans the whole editor document — which after
 * a reopen holds the restored saved letter, i.e. stored, user-authored HTML. DOMPurify permits
 * data-* attributes, so a filename read back out of an attribute would be attacker-influenced DOM
 * text flowing into an image URL (CodeQL js/html-constructed-from-input). Keeping the value in
 * module state means the URL is only ever built from legacyStampFile(), the same trusted
 * ImgArray/APCache source pickStamp() has always used. The marker is removed as it is consumed, so
 * it never reaches the stored letter either.
 */
function bindStampFallbacks(editorDoc) {
	if (!editorDoc || !editorDoc.querySelectorAll) { return; }
	var fallback = carlosPendingStampFallback;
	carlosPendingStampFallback = "";
	var stamps = editorDoc.querySelectorAll('img[' + STAMP_MARKER_ATTRIBUTE + ']');
	for (var index = 0; index < stamps.length; index++) {
		(function (image) {
			image.removeAttribute(STAMP_MARKER_ATTRIBUTE);
			// Defence in depth: a stray marker on restored content has no pending fallback, and a
			// filename is a plain basename or it is not used.
			if (!/^[\w.\- ]+$/.test(fallback)) { return; }
			// Closure flag, not an attribute: the guard must not leave a marker in the stored
			// letter, and swapping src can itself fire another error event.
			var swapped = false;
			var swap = function () {
				if (swapped) { return; }
				swapped = true;
				console.log('editControl: stored signature unavailable; falling back to ' + fallback);
				image.src = stampImageSrc(fallback);
			};
			image.addEventListener('error', swap);
			// The image may already have failed from cache before this ran.
			if (image.complete && image.naturalWidth === 0) { swap(); }
		})(stamps[index]);
	}
}

function block(blockElements) {
	for(i=0; i<blockElements.length; i++) {
		var htm='<div>'+blockElements[i]+'</div>';
		doHtml(htm);
	}
}

function doTime() {
	// need to supply the time!
	var digital = new Date();
	var hours = digital.getHours();
	var minutes = digital.getMinutes();
	var seconds = digital.getSeconds();
	var amOrPm = "AM";
	if (hours > 11) amOrPm = "PM";
	if (hours > 12) hours = hours - 12;
	if (hours == 0) hours = 12;						//0 hour
	if (minutes <= 9) minutes = "0" + minutes;		//pad with 0
	if (seconds <= 9) seconds = "0" + seconds;		//pad with 0
	var time =" " + hours + ":" + minutes + ":" + seconds + " " + amOrPm +" ";
	return time;
}

function doDate() {
	// need to supply the date!
	var digital = new Date();
	var days = digital.getDate();
	var months = digital.getMonth()*1 +1;
	var years = digital.getFullYear();
	var date =" " + days + "/" + months + "/" + years + " " ;
	return date;
}

// Public API reference block.
// The functions and variables below are part of the public API consumed by external JSP pages
// that include this script (e.g. via <script src="editControl2.js">). Static analysis tools
// cannot see those call sites and incorrectly flag them as unused. This void expression
// references each item without calling any function or producing any side-effects at runtime.
void [
	cfg_isrc, cfg_bstyle, cfg_boutstyle, cfg_sepstyle,
	insertEditControl, exprompt, Select, loadTemplate,
	parseText, block, doTime, doDate,
	doTable, doExport, doEdit, doBreak,
	viewsource, usecss, popup,
	printKey, submitFaxButton,
	// Functions defined later in the file; used by external JSP pages,
	// inline onclick handlers, and the DB-stored eForm HTML that static
	// analysis cannot see. These are NOT dead code — removing them would
	// break the RTL eForm at runtime.
	isGenderLookup, Start, htmlLine,
	// formIsRTL: read by efmshowform_data to detect RTL eForm type.
	formIsRTL, getMeasures,
	// collapseFooter, consultantSearch, populateInputField: called from
	// inline onclick/onKeyup handlers in the DB-stored form_html.
	// tempBinHover (not in this list): called from onmouseover in form_html.
	collapseFooter, consultantSearch, populateInputField
];

function doTable() {
	var rowstext = prompt("enter rows");
	var colstext = prompt("enter cols");
	var rows = parseInt(rowstext);
	var cols = parseInt(colstext);
	var table;
	if ((rows > 0) && (cols > 0)) {
		table = '<table style="text-align: left; width: 100%;" border="1" cellpadding="2" cellspacing="2"><tbody>';
		for (var i=0; i < rows; i++) {
			table +='<tr>';
			for (var j=0; j < cols; j++) {
				table +='<td>&nbsp;</td>';
			}
			table +='</tr>';
		}
		table += '</tbody></table>';
    	}
	return table;
}

function doExport() {
	var blob = new Blob([document.getElementById('edit').contentWindow.document.documentElement.outerHTML], { type: 'text/html;charset=utf-8'}); 
	saveAs(blob, document.getElementById('subject').value+'.rtl');
}

function doEdit() {
	$.ajax({ url: window.frames[0].location, success: function(data) {
		var iframe = window[cfg_editorname];
		if (iframe) {
			// Use srcdoc to avoid document.write (eval-like behavior)
			iframe.srcdoc = data;
		}
	} });
}

function doBreak(){
    var t='<p style="page-break-before: always;">&nbsp;<hr class="DoNotPrint" title="page break" style="border: 1px dashed lightblue;"><br>'
    return t;
}

function viewsource(source) {
	// load the html into a variable, blank the body, import as text, disable gui
	var html;
	if (isIE()){
		html=window[cfg_editorname].document.body.innerHTML ; //if browser supports M$ conventions
		alert(html) ; //load into an alert as importnode not supported by IE
		return;
	}
	if (source) {
		html = document.createTextNode(jQuery().restoreImagePaths(document.getElementById('edit').contentWindow.document.body.innerHTML));		
		document.getElementById(cfg_editorname).contentWindow.document.body.innerHTML = "";
		html = document.getElementById(cfg_editorname).contentWindow.document.importNode(html,false);		
		document.getElementById(cfg_editorname).contentWindow.document.body.appendChild(html);
		document.getElementById("control1").style.visibility="hidden";
		document.getElementById("control2").style.visibility="hidden";
		document.getElementById("control3").style.visibility="hidden";
		document.getElementById("control4").style.visibility="hidden";
	} else {
		// Read the raw HTML source text that was being edited in source view
		var sourceText = document.getElementById(cfg_editorname).contentWindow.document.body.textContent;
		var convertedHtml = jQuery().convertImagePaths(sourceText);
		var safeConvertedHtml = sanitizeHtml(convertedHtml);
		// Use DOMParser to reconstruct the DOM from the source view HTML, preventing
		// DOM text from being reinterpreted as HTML without going through a parser context
		var editorBody = document.getElementById(cfg_editorname).contentWindow.document.body;
		editorBody.textContent = '';
		if (safeConvertedHtml !== null) {
			var parser = new DOMParser();
			var parsedDoc = parser.parseFromString('<!DOCTYPE html><html><body>' + safeConvertedHtml + '</body></html>', 'text/html');
			var fragment = document.getElementById(cfg_editorname).contentWindow.document.createDocumentFragment();
			Array.prototype.forEach.call(parsedDoc.body.childNodes, function (node) {
				fragment.appendChild(editorBody.ownerDocument.importNode(node, true));
			});
			editorBody.appendChild(fragment);
		} else {
			editorBody.textContent = convertedHtml;
		}
		document.getElementById("control1").style.visibility="visible";
		document.getElementById("control2").style.visibility="visible";
		document.getElementById("control3").style.visibility="visible";
		document.getElementById("control4").style.visibility="visible";
	}
	return;
}

function usecss(source) {
	if (isIE()){
		//if browser supports M$ conventions it may error on this execCommand
		return;
	}
	// a Mozilla only feature
	document.getElementById('edit').contentWindow.document.execCommand("styleWithCSS", false, (source));
}

// end of traditional editControl.js functions
// page loading

function addJavascript(jsname,pos) {
	var th = document.getElementsByTagName(pos)[0];
	var s = document.createElement('script');
	s.setAttribute('type','text/javascript');
	s.setAttribute('src',jsname);
	th.appendChild(s);
}

jQuery(document).ready(function(){
	if (jQuery.fn.convertImagePaths === undefined) {
		addJavascript("../share/javascript/eforms/imageControl.js", "head");
	}
});

function popup(location) {
    var DocPopup = window.open(location,"_blank","height=380,width=580");
    if (DocPopup != null) {
	     if (DocPopup.opener == null) {
	     DocPopup.opener = self;
	     }
	}
}
	
function gup(name, url) {
	// Use URLSearchParams for reliable query-string parsing (copilot suggestion).
	// Falls back to window.location.search when no url argument is provided.
	var searchStr = (url != null)
		? (url.indexOf('?') >= 0 ? url.substring(url.indexOf('?')) : '')
		: window.location.search;
	var params = new URLSearchParams(searchStr);
	return params.get(name) || "";
}

var demographicNo ="";

jQuery(document).ready(function(){
	demographicNo = gup("demographic_no");
	if (demographicNo == "") { demographicNo = gup("efmdemographic_no", jQuery("form").attr('action')); }
	if (typeof signatureControl != "undefined") {
		signatureControl.initialize({
			sigHTML:"../signature_pad/tabletSignature?inWindow=true&saveToDB=true&demographicNo=",
			demographicNo:demographicNo,
			refreshImage: function (e) {
				var html = "<img src='"+e.storedImageUrl+"&r="+ Math.floor(Math.random()*1001) +"'></img>";
				doHtml(html);		
			},
		signatureInput: "#signatureInput"	
    	});
	}		
});
		
var cache = createCache({
	defaultCacheResponseHandler: function(type) {
		if (checkKeyResponse(type)) {
			doHtml(cache.get(type));
		}						
	},
	cacheResponseErrorHandler: function(xhr, error) {
		alert("Please contact an administrator, an error has occurred.");						
	}
});	
	
function checkKeyResponse(response) {		
	if (cache.isEmpty(response)) {
		alert("The requested value has no content.");
		return false;
	}
	return true;
}
	
function printKey (key) {
	var value = cache.lookup(key); 
	if (value != null && checkKeyResponse(key)) { doHtml(cache.get(key)); } 		  
}
	
function submitFaxButton() {
	document.getElementById('faxEForm').value=true;
	saveRTL();
	setTimeout(function() { document.RichTextLetter.submit(); }, 1000);
}
	

	cache.addMapping({name: "template", cacheResponseHandler: populateTemplate});

// add RTL specific key values 
// each cache name is an array of key values
// no need to add for standard apconfig.xml tags		
	

// format text for instant access	

	cache.addMapping({
		name: "letterhead", 
		values: ["clinic_name", "clinic_fax", "clinic_phone", "clinic_addressLineFull", "doctor", "doctor_contact_phone", "doctor_contact_fax", "doctor_contact_addr","current_user"], 
		storeInCacheHandler: function (key, value) {
			var text = genericLetterhead();
			cache.put("letterhead", text);
		},
		cacheResponseHandler: function () {
			if (checkKeyResponse(this.name)) {
				doHtml(cache.get(this.name));
			}
		}
	});

	cache.addMapping({
		name: "_ReferringBlock", 
		values: ["referral_name", "referral_address", "referral_phone", "referral_fax"], 	
		storeInCacheHandler: function (key, value) {
			var text = 
				(!cache.isEmpty("referral_name") ? cache.get("referral_name") + "<br>" : "") 
			  + (!cache.isEmpty("referral_address") ? cache.get("referral_address") + "<br>" : "")
			  + (!cache.isEmpty("referral_phone") ? "Tel: " + cache.get("referral_phone") + "<br>" : "")
			  + (!cache.isEmpty("referral_fax") ? "Fax: " + cache.get("referral_fax") + "<br>" : "");
			if (text == "") {
				text = 
					(!cache.isEmpty("bc_referral_name") ? cache.get("bc_referral_name") + "<br>" : "") 
				  + (!cache.isEmpty("bc_referral_address") ? cache.get("bc_referral_address") + "<br>" : "")
				  + (!cache.isEmpty("bc_referral_phone") ? "Tel: " + cache.get("bc_referral_phone") + "<br>" : "")
				  + (!cache.isEmpty("bc_referral_fax") ? "Fax: " + cache.get("bc_referral_fax") + "<br>" : "");
			}						 
			cache.put(this.name, text)
		},
		cacheResponseHandler: function () {
			if (checkKeyResponse(this.name)) {
				doHtml(cache.get(this.name));
			}
		}
	});

// fudge for older apconfig.xml	that lack referal_first_name, patient_nameF etc
	cache.addMapping({
		name: "referral_nameF", 
		values: ["referral_name"], 
		storeInCacheHandler: function(_key,_val) { 
		if (!cache.isEmpty("referral_name")) {
				var mySplitResult =  cache.get("referral_name").toString().split(",");
				cache.put("referral_nameF", mySplitResult[1]);
			} 
		}
	});
	cache.addMapping({
		name: "referral_nameL", 
		values: ["referral_name"], 
		storeInCacheHandler: function(_key,_val) { 
		if (!cache.isEmpty("referral_name")) {
				var mySplitResult =  cache.get("referral_name").toString().split(",");
				cache.put("referral_nameL", mySplitResult[0]);
			} 
		}
	});
	cache.addMapping({
		name: "PatientNameF", 
		values: ["first_last_name"], 
		storeInCacheHandler: function(_key,_val) { 
		if (!cache.isEmpty("first_last_name")) {
				var mySplitResult =  cache.get("first_last_name").toString().split(",");
				cache.put("PatientNameF", mySplitResult[0]);
			} 
		}
		
	});
	cache.addMapping({
		name: "_age", 
		values: ["age","ageComplex"], 
		storeInCacheHandler: function(_key,_val) { 
		if (!cache.isEmpty("ageComplex")) {
				cache.put("_age", cache.get("ageComplex"));
			} else {
				cache.put("_age", cache.get("age")+" year old");
			}
		}
	});
// end fudge

	cache.addMapping({
		name: "_ReferringBlockLite", 
		values: ["referral_name", "referral_address"], 
		storeInCacheHandler: function(key,value) { 
			var text = 
				(!cache.isEmpty("referral_name") ? cache.get("referral_name") + "<br>" : "") 
			  + (!cache.isEmpty("referral_address") ? cache.get("referral_address") + "<br>" : "")						 
			cache.put(this.name, text)
		},
		cacheResponseHandler: function () {
			if (checkKeyResponse(this.name)) {
				doHtml(cache.get(this.name));
			}
		}
	});

	cache.addMapping({
		name: "_SocialFamilyHistory",
		values: ["social_family_history"],
		storeInCacheHandler: function(key,value) {
			var text = cache.get("social_family_history").replace(/(<br>)+/g,"<br>");
			cache.put(this.name, text );
		},
		cacheResponseHandler:function () {
			if (checkKeyResponse(this.name)) {				
				doHtml(cache.get(this.name));
			}	
		}
	});
	
	cache.addMapping({
		name: "medical_historyS", 
		values: ["medical_history"], 
		storeInCacheHandler: function(_key,_val) { 
		if (!cache.isEmpty("medical_history")) {
				var mySplitResult =  cache.get("medical_history").toString().split("]]-----");
				cache.put("medical_historyS", mySplitResult.pop());
			} 
		}
	});

	cache.addMapping({
		name: "_ClosingSalutation", 
		values: ["provider_name_first_init", "current_user_fname_lname", "doctor", "current_user",
			"current_user_id", "current_user_ohip_no", "doctor_provider_no"],	
		storeInCacheHandler: function (key,value) {		
			// The identity may only have arrived with this lookup (a form_html predating the hidden
			// inputs), so re-run the existence probe now that it is known.
			probeProviderStamp();
			var imgsrc=pickStamp();
			var frag = ("<p>Yours Sincerely<p>" + imgsrc + "<p>");
			// The NAME must name whoever the STAMP signed as. Deciding them separately is how a
			// letter ended up with one clinician's signature above another clinician's name.
			var signsAsCurrentUser = stampSignerRole() === "user";
			if (!signsAsCurrentUser && cache.contains("current_user")) {
				// No provider numbers to go on (a pre-migration form_html with no AP values either):
				// fall back to the legacy rule, where the current user having a stamps.js entry is
				// what marks them as the signer.
				for (i=0; i<ImgArray.length;i++){
					var ListItemArr =  ImgArray[i].split("|");
					var UserName = ListItemArr[0];
					var FileName = ListItemArr[1]; void FileName; // retained for external API consumers
					if (UserName && cache.get('current_user').indexOf(UserName)>=0){
						console.log('current user has a signature so use it in the closing salutation');
						signsAsCurrentUser = true;
					}
				}
			}
			var signerName = "";
			if (signsAsCurrentUser && !cache.isEmpty("current_user_fname_lname")) {
				signerName = cache.get("current_user_fname_lname");
			} else if (!cache.isEmpty("provider_name_first_init")) {
				signerName = cache.get("provider_name_first_init");
			}
			var salutation = signerName.length > 0 ? (frag + signerName + ", MD") : frag;
			cache.put(this.name, salutation);
		},
		cacheResponseHandler:function () {
			if (checkKeyResponse(this.name)) {				
				doHtml(cache.get(this.name));
			}	
		}
	});
	

	// current_user_id / current_user_ohip_no / doctor_provider_no drive pickStamp()'s choice of
	// consult_sig_<provider_no>.png. They normally arrive as hidden inputs on the form, but an
	// install whose stored Rich Text Letter form_html predates those inputs has none, so they are
	// listed here too: the lookup that resolves the stamp then also fetches the identity it needs.
	cache.addMapping({
		name: "stamp", 
		values: ["stamp_name", "doctor", "current_user",
			"current_user_id", "current_user_ohip_no", "doctor_provider_no"], 
		storeInCacheHandler: function(_key,_val) { 
				// Re-probe: on a form_html without the hidden inputs the identity only becomes known
				// here, so the probe fired at Start() had no filename to check.
				probeProviderStamp();
				var imgsrc=pickStamp();
				cache.put("stamp",imgsrc);
		}
	});

	// code that loads hidden text to cache	
	
	$('input:hidden').each(function() {
		var value = $(this).val();
		cache.put(this.name, value);
	});

	
	// Setting up many to one mapping for derived gender keys.
	var genderKeys = ["he_she", "his_her", "gender"];	
	var genderIndex;
	for (genderIndex in genderKeys) {
		cache.addMapping({ name: genderKeys[genderIndex], values: ["sex"]});
	}
	cache.addMapping({name: "sex", values: ["sex"], storeInCacheHandler: populateGenderInfo});
	
	function isGenderLookup(key) {
		var y;
		for (y in genderKeys) { if (genderKeys[y] == key) { return true; } }
		return false;
	}

// Valid gender codes include F M O T U and empty	
	function populateGenderInfo(key, val){
		switch (val){
			case 'F':
				cache.put("sex", "F");
				cache.put("he_she", "she");
				cache.put("his_her", "her");
				cache.put("gender", "female");
				break;
			case 'M':
				cache.put("sex", "M");
				cache.put("he_she", "he");
				cache.put("his_her", "his");
				cache.put("gender", "male");
				break;
			case 'O':
				cache.put("sex", "O");
				cache.put("he_she", "they");
				cache.put("his_her", "their");
				cache.put("gender", "genderqueer");
				break;
			case 'T':
				cache.put("sex", "T");
				cache.put("he_she", "they");
				cache.put("his_her", "their");
				cache.put("gender", "trans gender");
				break;			
			default:
				cache.put("sex", "X");
				cache.put("he_she", "they");
				cache.put("his_her", "their");
				cache.put("gender", "unspecified gender");
		}
	}
	
	/**
	 * Initializes the Rich Text Letter editor on page load.
	 * Called from <body onload="Start()"> in the DB-stored form_html.
	 *
	 * Performs three setup tasks:
	 * 1. Loads available letter templates via AJAX from efmformrtl_templates
	 * 2. Populates the AP cache from hidden .cacheInit fields injected by the JSP
	 * 3. Initializes gender pronouns from the cached sex field
	 * 4. Loads existing letter content into the editor (for saved forms)
	 * 5. Calls updateAttached() to refresh the attachments panel
	 *
	 * Note: updateAttached() is defined in the DB-stored form_html, not here.
	 */
	function Start() {

			// Load template <option> elements into the template dropdown
			$.ajax({
				url : "efmformrtl_templates",
				success : function(data) {
					var cleanData = sanitizeHtml(data, {ALLOWED_TAGS: ['option'], ALLOWED_ATTR: ['value', 'selected']});
					if (cleanData !== null) {
						$("#template").html(cleanData);
					} else {
						// DOMPurify not available — fallback safely constructs <option> elements
						var parser = new DOMParser();
						var doc = parser.parseFromString(data, 'text/html');
						var templateSelect = $("#template").empty();
						doc.querySelectorAll('option').forEach(function(opt) {
							var safeOpt = document.createElement('option');
							safeOpt.value = opt.value;
							safeOpt.textContent = opt.textContent;
							if (opt.selected) safeOpt.selected = true;
							templateSelect.append(safeOpt);
						});
					}
					loadDefaultTemplate();
				},
				error : function(xhr, status, error) {
					console.error('Failed to load letter templates: ' + status);
					loadDefaultTemplate();
				}
			});

			
			$(".cacheInit").each(function() { 
				cache.put($(this).attr('name'), $(this).val());
				$(this).remove();				
			});
			
			// set eventlistener for the iframe to flag changes in the text displayed
			attachDirtyFlagListener();
				
			// set the HTML contents of this edit control from the value saved in OSCAR (if any)
			var contents = document.getElementById('Letter').value;
			if (contents.length == 0) {
				// A NEW letter: the template dropdown has not loaded yet, so this parses the editor's
				// initial about:blank document. Its designMode is off until enabled here, and
				// seteditControlContents() refuses to write into a non-designMode frame — which
				// logged "cannot set editor contents" on every new letter. The real template is
				// parsed again from loadDefaultTemplate()'s onload once it has loaded.
				enableEditorDesignMode();
				parseTemplate();
			} else {
				// Decode HTML entities that saveRTL() encoded before saving.
				// saveRTL() escapes & " < > ' so the content survives being stored in a
				// textarea value. We reverse that here so the editor renders actual HTML
				// (e.g., <br> as a line break, not literal "&lt;br&gt;" text).
				// Decode in reverse order: entities containing & must be decoded last.
				contents = contents.replace(/&#39;/g, "'");
				contents = contents.replace(/&gt;/g, ">");
				contents = contents.replace(/&lt;/g, "<");
				contents = contents.replace(/&quot;/g, '"');
				contents = contents.replace(/&amp;/g, "&");
				// designMode MUST be enabled BEFORE the contents are written.
				// seteditControlContents() only writes into the iframe when its document is
				// already in designMode, and otherwise falls through to a branch that assigns
				// .value to the iframe element — a no-op. With the two statements in the other
				// order, reopening a saved letter showed an empty editor, and the toolbar's
				// save-and-download then persisted that empty editor over the stored letter.
				document.getElementById(cfg_editorname).contentWindow.document.designMode = 'on';
				seteditControlContents(cfg_editorname, contents, true);
			}
			maximize();

			// Body onload, so every hidden input the server populated is in the DOM. Warm the
			// per-provider signature check now rather than when the Stamp button is pressed: the
			// answer decides whether the stamp falls back to stamps.js, and pickStamp() is called
			// synchronously from a cache handler that cannot wait for a round trip.
			probeProviderStamp();

			console.log('updating attached');
			updateAttached();
	}



	function htmlLine(text) {
		return text.replace(/\r?\n/g,"<br>");
	}

	function genericLetterhead() {
		// set the HTML contents of the letterhead
		var address = '<table border=0><tbody><tr><td><font size=6>'
				+ cache.get('clinic_name')
				+ '</font></td></tr><tr><td><font size=2>'
				+ cache.get('doctor_contact_addr')
				+ ' Fax: ' + cache.get('doctor_contact_fax')
				+ ' Phone: ' + cache.get('doctor_contact_phone')
				+ '</font><hr></td></tr></tbody></table><br>';
		if ( (cache.get('clinic_name').toLowerCase()).indexOf('amily health team',0)>-1){
		address=fhtLetterhead(); }
		if ( (cache.get('clinic_name').toLowerCase()).indexOf('fht',0)>-1){
		address=fhtLetterhead(); }
		return address;
	}


	function fhtLetterhead() {
		// set the HTML contents of the letterhead using FHT colours
		var address = cache.get('clinic_addressLineFull')
				+ '<br>Fax:' + cache.get('clinic_fax')
				+ ' Phone:' + cache.get('clinic_phone');
		// Initialize res with a safe default (clinic name as fallback)
		var res = ['', cache.get('clinic_name') || 'Clinic'];
		// use the doctors name to allow for a secretary to write a letter under direction
		if (cache.contains("doctor")) {
			var str = cache.get('doctor');
			res = str.split(", "); //last, first
			console.log("doctor="+res[0]);
		}
		// use the current user instead if they have a signature on file
		if (cache.contains("current_user")) {
			var str = cache.get('current_user');
			console.log(str);
			for (i=0; i<ImgArray.length;i++){
		        var ListItemArr =  ImgArray[i].split("|");
		        var UserName = ListItemArr[0];
		        var FileName = ListItemArr[1]; void FileName; // retained for external API consumers
		        if (str.indexOf(UserName)>=0){
					console.log('current user '+str+' has a signature so use their name');
					res = str.split(", "); //last, first
			        }
				}
		}

		address = '<table style=\'text-align: right;\' border=\'0\'><tbody><tr class=\'t71\' style=\'font-style: italic;\'><td><font size=\'+2\'>'
				+ cache.get('clinic_name')
				+ '</font> <hr class=\'b212\'></td> </tr> <tr class=\'t71\'> <td><font size=\'+1\'>'
				+  res[1] + " " + res[0] + '</font></td> </tr> <tr class=\'b212\'> <td><small>'
				+ address + '</small></td> </tr> </tbody> </table>';
		return address;
	}

 
	/**
	 * Lowest ohip_no that identifies a billing practitioner (MD / NP / RMW).
	 *
	 * Numbers below this are reserved for non-billing accounts that still need a schedule
	 * (residents, nurses, clerical staff, room/resource pseudo-providers), so a letter written
	 * under one of those logins is signed by the patient's MRP rather than by the typist. Mirrors
	 * sign() in visualEformEditor.jsp, which uses the same delegation rule.
	 */
	var MIN_BILLING_PROVIDER_OHIP_NO = 1000;

	/**
	 * Provider identity the stamp is chosen from, in the two places it can reach this page.
	 *
	 * The hidden inputs are the primary source: efmformadd_data/efmshowform_data populate them
	 * server-side from the form's oscarDB= attributes, and the $('input:hidden') sweep in Start()
	 * copies them into the cache before any button can be clicked. The AP keys are the same values
	 * fetched over APCache, and cover an install whose stored form_html predates those inputs (a
	 * clinic that customized the Rich Text Letter row, or one that has not run the migration yet):
	 * they are listed on the "stamp" and "_ClosingSalutation" mappings so a lookup pulls them.
	 */
	var STAMP_PROVIDER_FIELDS = {
		userId:     { inputId: "user_id",            apKey: "current_user_id" },
		userOhipNo: { inputId: "user_ohip_no",       apKey: "current_user_ohip_no" },
		doctorNo:   { inputId: "doctor_provider_no", apKey: "doctor_provider_no" }
	};

	function stampProviderField(field) {
		// Trimmed to match ConsultationSignatureService.isNumericProviderNo(), which trims before
		// testing \d+. An AP output that arrives padded must resolve to the same provider on both
		// sides, or the letter silently falls back to stamp.png for a provider who has a signature.
		var element = document.getElementById(field.inputId);
		if (element && typeof element.value === "string" && element.value.trim().length > 0) {
			return element.value.trim();
		}
		if (cache.contains(field.apKey) && !cache.isEmpty(field.apKey)) {
			return String(cache.get(field.apKey)).trim();
		}
		return "";
	}

	/**
	 * Who signs this letter: "user" (the logged-in provider), "mrp" (the patient's most responsible
	 * provider), or "" when neither can be determined.
	 *
	 * A billing practitioner signs their own letters; anyone else is writing under the direction of
	 * the MRP and signs with that provider's signature. Both the stamp IMAGE and the typed NAME in
	 * the closing salutation derive from this single answer — they used to be decided separately,
	 * and a billing user with a signature file but no stamps.js entry got their own signature
	 * printed above the MRP's name.
	 */
	function stampSignerRole() {
		var userOhipNo = parseInt(stampProviderField(STAMP_PROVIDER_FIELDS.userOhipNo), 10);
		if (!isNaN(userOhipNo) && userOhipNo > MIN_BILLING_PROVIDER_OHIP_NO
				&& stampProviderField(STAMP_PROVIDER_FIELDS.userId).length > 0) {
			return "user";
		}
		if (stampProviderField(STAMP_PROVIDER_FIELDS.doctorNo).length > 0) { return "mrp"; }
		return "";
	}

	/** Provider number whose signature should sign this letter, or "" when it cannot be determined. */
	function stampProviderNumber() {
		var role = stampSignerRole();
		if (role === "user") { return stampProviderField(STAMP_PROVIDER_FIELDS.userId); }
		if (role === "mrp") { return stampProviderField(STAMP_PROVIDER_FIELDS.doctorNo); }
		return "";
	}

	/**
	 * Image URL for an eForm asset.
	 *
	 * cfg_isrc is what the host form configures (the Rich Text Letter sets
	 * "../eform/displayImage.do?imagefile="), but it defaults to the empty string, and an empty
	 * base would make the stamp a bare relative filename that resolves against the action path and
	 * 404s. Fall back to the extensionless route, which is the canonical one.
	 */
	function stampImageSrc(file) {
		var base = (typeof cfg_isrc === "string" && cfg_isrc.length > 0)
			? cfg_isrc
			: "../eform/displayImage?imagefile=";
		return base + file;
	}

	/** Signature file CARLOS stores per provider (Administration > Provider > signature). */
	function providerStampFile() {
		var providerNumber = stampProviderNumber();
		// Defensive, not cosmetic: this becomes an imagefile= query value, and the eForm image
		// route treats it as a path component. Provider numbers are digits in every supported
		// install; anything else means the AP returned something unexpected, so stamp nothing
		// rather than assemble a filename out of it.
		if (!/^[0-9]{1,20}$/.test(providerNumber)) { return ""; }
		return "consult_sig_" + providerNumber + ".png";
	}

	/**
	 * Valueless marker pickStamp() puts on a per-provider stamp, and the fallback filename that goes
	 * with it. The filename lives here rather than in the markup on purpose — see
	 * {@link bindStampFallbacks}, which scans the whole editor document including restored saved
	 * letters, so anything read back out of an attribute is attacker-influenced DOM text.
	 */
	var STAMP_MARKER_ATTRIBUTE = "data-carlos-stamp";
	var carlosPendingStampFallback = "";

	/**
	 * Records whether the per-provider signature file actually exists, so a provider who has not
	 * uploaded one still gets the clinic's stamps.js / stamp.png fallback instead of a broken image.
	 *
	 * The probe is fired once from Start(), well before the Stamp button can be clicked. If it has
	 * not answered yet the per-provider file is used anyway — that is the intended stamp, and the
	 * legacy fallback is the exception.
	 */
	var carlosProviderStampProbe = { file: "", missing: false };

	function probeProviderStamp() {
		var file = providerStampFile();
		if (file === "" || file === carlosProviderStampProbe.file) { return; }
		carlosProviderStampProbe = { file: file, missing: false };
		var probe = new Image();
		probe.onerror = function() {
			// Only trust the result if it is still about the file we asked for.
			if (carlosProviderStampProbe.file === file) { carlosProviderStampProbe.missing = true; }
			console.log("editControl: no stored signature for " + file + "; falling back to stamps.js");
		};
		probe.src = stampImageSrc(file);
	}

	function legacyStampFile() {
		// Clinic-supplied override: a stamps.js in the eForm images folder defining
		// var ImgArray = ["doctor|SignatureFile.png", ...];
		// Current user wins over the MRP, matching the _ClosingSalutation rule.
		var fileName = "stamp.png";
		var sources = ["doctor", "current_user"];
		// `var` on both counters deliberately: the loops this replaces assigned a bare `i`,
		// which is an implicit global shared with fhtLetterhead() and the closing-salutation
		// handler.
		for (var s = 0; s < sources.length; s++) {
			if (cache.isEmpty(sources[s])) { continue; }
			var signer = String(cache.get(sources[s]));
			for (var entry = 0; entry < ImgArray.length; entry++) {
				var pair = String(ImgArray[entry]).split("|");
				var userName = pair[0];
				var signatureFile = pair[1];
				if (userName && signatureFile && signer.indexOf(userName) >= 0) {
					fileName = signatureFile;
				}
			}
		}
		return fileName;
	}

	function pickStamp() {
		// set the HTML contents of the signature stamp
		//
		// Preferred source is the provider's own signature image, consult_sig_<provider_no>.png,
		// the same file the consultation module and the visual eForm editor stamp with. stamps.js
		// (an ImgArray of "doctor|SignatureFile.png" pairs) and the single-user stamp.png remain as
		// fallbacks for an install that has not loaded per-provider signatures: before this, they
		// were the ONLY sources, so every letter from a multi-provider clinic without a stamps.js
		// was signed with the same stamp.png.
		var file = providerStampFile();
		if (file === "" || (carlosProviderStampProbe.file === file && carlosProviderStampProbe.missing)) {
			return '<img src="' + stampImageSrc(legacyStampFile()) + '" width="200" height="100" />';
		}
		// The per-provider file is the intended stamp, but the probe may not have answered yet.
		// bindStampFallbacks() turns the marker into a load-error handler once this is inserted.
		// The fallback filename rides module state, not the markup: see bindStampFallbacks().
		carlosPendingStampFallback = legacyStampFile();
		return '<img src="' + stampImageSrc(file) + '" ' + STAMP_MARKER_ATTRIBUTE
			+ '="1" width="200" height="100" />';
	}
	// Flag read by efmshowform_data and the eForm framework to identify this
	// as a Rich Text Letter eForm (vs. a regular eForm). Static analysis may flag
	// this as "unused" because the read happens in JSP/server-side code, not JS.
	window.formIsRTL = true;


// lab_grid2.js //

var elements = (window.location.pathname.split('/', 2))
firstElement = (elements.slice(1))
vPath = ("https://" + location.host + "/" + firstElement)
//alert(vPath)

//get parameters
var params = {};
if (location.search) {
    var parts = location.search.substring(1).split('&');
    for (var i = 0; i < parts.length; i++) {
        var nv = parts[i].split('=');
        if (!nv[0]) continue;
        params[nv[0]] = nv[1] || true;
    }
}


// RTL measurement batches share one request and retain their original insertion point.
var pendingMeasureBatch = null;
var measureRequestQueue = [];
var activeMeasureBatch = null;
var measureBatchesPending = 0;
var measureBatchFailed = false;

function measurementHistoryStillLoading() {
    return measureBatchesPending > 0;
}

function assertMeasurementHistoryReady() {
    if (measurementHistoryStillLoading()) {
        // Legacy saveRTL implementations clear this flag before serializing. A
        // blocked save must retain the unload warning even if the request fails.
        window.needToConfirm = true;
        alert('Measurements are still loading. Please wait before saving or printing this letter.');
        throw new Error('RTL measurements are still loading');
    }
}

function showMeasurementStatus(message, failed) {
    var notice = document.getElementById('rtl-measurement-status');
    if (!notice) {
        notice = document.createElement('div');
        notice.id = 'rtl-measurement-status';
        notice.setAttribute('role', 'status');
        var editor = document.getElementById(cfg_editorname);
        if (!editor || !editor.parentNode) { return; }
        editor.parentNode.insertBefore(notice, editor);
    }
    notice.textContent = message;
    notice.style.color = failed ? '#a00000' : '';
    notice.hidden = !message;
}

function measurementPatientId() {
    var field = document.getElementById('demographicNo');
    var patient = field ? field.value : gup('demographic_no') || gup('efmdemographic_no');
    if (!/^[1-9][0-9]*$/.test(String(patient || ''))) {
        throw new Error('The letter has no patient selected.');
    }
    return String(patient);
}

function createMeasureBatch() {
    var frame = document.getElementById(cfg_editorname);
    var editorDoc = frame && frame.contentDocument;
    if (!editorDoc || !editorDoc.body) { throw new Error('The letter editor is not ready.'); }
    var patient = measurementPatientId();
    var marker = editorDoc.createComment('RTL measurement insertion');
    var selection = editorDoc.getSelection();
    if (selection && selection.rangeCount && editorDoc.body.contains(selection.getRangeAt(0).endContainer)) {
        var range = selection.getRangeAt(0).cloneRange();
        range.collapse(false);
        range.insertNode(marker);
        range.setStartAfter(marker);
        range.collapse(true);
        selection.removeAllRanges();
        selection.addRange(range);
    } else {
        editorDoc.body.appendChild(marker);
    }
    return {patient: patient, document: editorDoc, marker: marker, requests: []};
}

/**
 * Queue a measurement type for the current letter. Same-tick Lab Grid/Vitals calls
 * use one authenticated JSON request; batches run serially in click order.
 * The promise always settles, including failure, because legacy sidebar callers
 * do not await it. A visible error distinguishes failure from an empty history.
 */
function getMeasures(measure, max) {
    return new Promise(function(resolve) {
        try {
            if (!pendingMeasureBatch) {
                pendingMeasureBatch = createMeasureBatch();
                if (measureBatchesPending === 0) { measureBatchFailed = false; }
                measureBatchesPending++;
                showMeasurementStatus('Loading measurements. Please wait before saving or printing.', false);
                window.setTimeout(flushMeasureRequests, 0);
            }
            pendingMeasureBatch.requests.push({measure: String(measure), max: max, resolve: resolve});
        } catch (error) {
            showMeasurementStatus('Measurements could not be loaded. Check that a patient and letter are open.', true);
            resolve({values: [], dates: [], failed: true});
        }
    });
}

function flushMeasureRequests() {
    if (pendingMeasureBatch) {
        measureRequestQueue.push(pendingMeasureBatch);
        pendingMeasureBatch = null;
    }
    startNextMeasureBatch();
}

function startNextMeasureBatch() {
    if (activeMeasureBatch || !measureRequestQueue.length) { return; }
    var batch = measureRequestQueue.shift();
    activeMeasureBatch = batch;
    fetchMeasureHistory(batch).then(function(measurements) {
        var histories = batch.requests.map(function(request) {
            return normalizeMeasureHistory(measurements, request, batch.patient);
        });
        insertMeasureBatch(batch, histories);
        return histories;
    }).catch(function() {
        measureBatchFailed = true;
        return batch.requests.map(function() { return {values: [], dates: [], failed: true}; });
    }).then(function(histories) {
        if (batch.marker.parentNode) { batch.marker.parentNode.removeChild(batch.marker); }
        measureBatchesPending--;
        activeMeasureBatch = null;
        if (measureBatchFailed) {
            showMeasurementStatus('Some requested measurements were not inserted. Please retry Lab Grid or Vitals before saving an incomplete letter.', true);
        } else if (!measureBatchesPending) {
            showMeasurementStatus('', false);
        }
        // Settle callers only after releasing this batch's save/print gate.
        batch.requests.forEach(function(request, index) { request.resolve(histories[index]); });
        startNextMeasureBatch();
    });
}

function fetchMeasureHistory(batch) {
    return new Promise(function(resolve, reject) {
        var request = new XMLHttpRequest();
        // Existing read-authorized REST service binds the request to this letter's patient.
        // The legacy HTML-history endpoint instead uses the mutable encounter session patient.
        var url = new URL('../ws/rs/measurements/' + batch.patient, window.location.href);
        request.open('POST', url.pathname, true);
        request.setRequestHeader('Content-Type', 'application/json');
        request.setRequestHeader('Accept', 'application/json');
        request.timeout = 15000;
        request.onload = function() {
            if (request.status !== 200) { reject(new Error('Measurement request failed')); return; }
            try {
                var data = JSON.parse(request.responseText);
                if (!data || !data.measurements || typeof data.measurements !== 'object' || Array.isArray(data.measurements)) {
                    throw new Error('Invalid measurement response');
                }
                resolve(data.measurements);
            } catch (error) { reject(error); }
        };
        request.onerror = request.ontimeout = request.onabort = function() {
            reject(new Error('Measurement request did not complete'));
        };
        request.send(JSON.stringify({types: Array.from(new Set(batch.requests.map(function(item) { return item.measure; })))}));
    });
}

function normalizeMeasureHistory(measurements, request, patient) {
    var rows = Object.prototype.hasOwnProperty.call(measurements, request.measure) ? measurements[request.measure] : [];
    if (!Array.isArray(rows)) { throw new Error('Invalid measurement history'); }
    rows.forEach(function(row) {
        if (!row || String(row.demographicId) !== patient || row.type !== request.measure || typeof row.dataField !== 'string') {
            throw new Error('Measurement response does not match the request');
        }
    });
    var limit = Math.floor(Number(request.max));
    if (!Number.isFinite(limit) || limit < 1) { limit = 1; }
    rows = rows.slice().sort(function(a, b) {
        return measurementDateTime(b.dateObserved) - measurementDateTime(a.dateObserved);
    }).slice(0, Math.min(limit, 100));
    return {values: rows.map(function(row) { return row.dataField; }),
        dates: rows.map(function(row) { return row.dateObserved; })};
}

function measurementDateTime(value) {
    var timestamp = typeof value === 'number' ? value : Date.parse(value);
    return Number.isFinite(timestamp) ? timestamp : 0;
}

function measurementMonth(value) {
    // The REST service's default Jackson date format is epoch milliseconds. Also
    // accept ISO dates from installations with the optional date serializer.
    if (typeof value === 'number' && Number.isFinite(value)) {
        var date = new Date(value);
        if (!Number.isNaN(date.getTime())) { return date.getUTCFullYear() + '/' + (date.getUTCMonth() + 1); }
    }
    var parts = /^(\d{4})-(\d{2})-\d{2}/.exec(String(value || ''));
    if (parts) { return parts[1] + '/' + Number(parts[2]); }
    return 'date unavailable';
}

function appendMeasureText(parent, text, size, bold) {
    var node = parent.ownerDocument.createElement('font');
    node.setAttribute('size', size);
    var content = bold ? parent.ownerDocument.createElement('b') : node;
    content.textContent = text;
    if (bold) { node.appendChild(content); }
    parent.appendChild(node);
}

function insertMeasureBatch(batch, histories) {
    var frame = document.getElementById(cfg_editorname);
    if (!frame || frame.contentDocument !== batch.document || measurementPatientId() !== batch.patient
            || !batch.document.body.contains(batch.marker)) {
        // The user deleted the placeholder or loaded another letter/template. Never
        // fall back to the current caret, which could silently put results in another letter.
        throw new Error('The original measurement insertion point is no longer available');
    }
    var fragment = batch.document.createDocumentFragment();
    histories.forEach(function(history, index) {
        if (!history.values.length) { return; }
        appendMeasureText(fragment, batch.requests[index].measure + ': ', '3', false);
        history.values.forEach(function(value, i) {
            appendMeasureText(fragment, value, '3', true);
            appendMeasureText(fragment, '(' + measurementMonth(history.dates[i]) + '); ', '2', false);
        });
        fragment.appendChild(batch.document.createElement('br'));
    });
    var changed = fragment.childNodes.length > 0;
    batch.marker.parentNode.insertBefore(fragment, batch.marker);
    if (changed) {
        if (typeof setDirtyFlag === 'function') { setDirtyFlag(); }
        else { window.needToConfirm = true; }
    }
}

// Legacy forms can print before calling saveRTL(), so intercept their controls in
// the capture phase. The floating toolbar has its own pre-workflow guard below.
document.addEventListener('click', function(event) {
    if (!measurementHistoryStillLoading() || !event.target.closest) { return; }
    var control = event.target.closest('input, button');
    if (!control) { return; }
    var outputControl = /^(SubmitButton|PrintButton|PrintSaveButton|PrintSubmitButton|pdfButton|pdfSaveButton)$/;
    if (control.type === 'submit' || outputControl.test(control.name) || outputControl.test(control.id)) {
        event.preventDefault();
        event.stopImmediatePropagation();
        alert('Measurements are still loading. Please wait before saving or printing this letter.');
    }
}, true);
document.addEventListener('submit', function(event) {
    if (measurementHistoryStillLoading()) {
        event.preventDefault();
        event.stopImmediatePropagation();
        alert('Measurements are still loading. Please wait before saving or printing this letter.');
    }
}, true);

// end lab grid //


function collapseFooter() {
	document.getElementById('arrow').classList.toggle('chevron');
	document.getElementById('controls4').classList.toggle('hide');
	console.log('changing from existing height of '+$('#edit').prop('style')['height']+' to ');
	if ($('#edit').prop('style')['height'] == cfg_height+'px'){
		$('#edit').css({height: '830px'});
	} else {
		$('#edit').css({height: (cfg_height+'px')});
		}
	console.log($('#edit').prop('style')['height']);
}


//-- Autocomplete  script --
        var searchDropDownFlag = false;

        /**
         * Searches for a consultant/specialist by name for the letter addressee field.
         * Makes an XHR request to the provider search endpoint and displays matching
         * providers in the tempBin dropdown. Selected consultant's address is inserted
         * into the CopyTo textarea for "Paste Selected" to use.
         *
         * @param {string} term - Search term (minimum 2 characters, "lastname, firstname" format)
         */
        function consultantSearch(term) {
            if (term.length < 2) {
                document.getElementById('tempBin').textContent = "You must enter at least 2 characters of a patient's name!";
                return false;
            }

            var tmpBin = document.getElementById('tempBin');

            loaderImg(tmpBin);

            var request = new XMLHttpRequest();

            //professionalSpecialists  2020-Nov-04
            //request.open('GET', '../encounter/oscarConsultationRequest/searchprofessionalSpecialists.json?keyword=' + term, true);
            request.open('GET', '../encounter/oscarConsultationRequest/searchProfessionalSpecialist.json?keyword='+encodeURIComponent(term), true);
            request.setRequestHeader("Content-Type", "application/json");
            request.onload = function() {

                if (request.status >= 200 && request.status < 400) {

                    // Parse JSON response — may fail if session expired and server
                    // returned an HTML login page with a 200 status
                    var data;
                    try {
                        data = JSON.parse(this.response);
                    } catch (parseError) {
                        console.error('Failed to parse consultant search response:', parseError);
                        var tempBin = document.getElementById('tempBin');
                        while (tempBin.firstChild) { tempBin.removeChild(tempBin.firstChild); }
                        tempBin.textContent = 'Search failed. Please try again.';
                        return;
                    }
                    //ensure the loader has time to display
                    setTimeout(() => {
                        var tempBin = document.getElementById('tempBin');
                        // Clear previous content
                        while (tempBin.firstChild) { tempBin.removeChild(tempBin.firstChild); }
                        if (data.length > 0) {
                            var ul = document.createElement('ul');
                            ul.className = 'custom-dropdown';
                            for (var idx = 0; idx < data.length; idx++) {
                                var li = document.createElement('li');
                                li.setAttribute('data-id', String(data[idx].id).trim());
                                li.setAttribute('data-address', data[idx].firstName + ' ' + data[idx].lastName + '\r\n' + data[idx].streetAddress + ' \r\nFax: ' + data[idx].fax);
                                li.textContent = data[idx].lastName + ', ' + data[idx].firstName;
                                li.addEventListener('click', function() { populateInputField(this, 'consultant'); });
                                ul.appendChild(li);
                            }
                            tempBin.appendChild(ul);
                        } else {
                            var noResult = document.createElement('span');
                            noResult.textContent = 'No results found matching ';
                            var bold = document.createElement('b');
                            bold.textContent = term;
                            noResult.appendChild(bold);
                            tempBin.appendChild(noResult);
                        }
                    }, 500);


                } else {
                    console.error('Consultant search failed with status: ' + request.status);
                    var tempBin = document.getElementById('tempBin');
                    tempBin.style.display = 'block';
                    tempBin.textContent = 'Search failed. Please try again.';
                }

            }; // end onload

            // Handle network-level failures (DNS, connection refused, timeout)
            request.onerror = function() {
                console.error('Consultant search network error');
                var tempBin = document.getElementById('tempBin');
                tempBin.style.display = 'block';
                tempBin.textContent = 'Network error. Please check your connection and try again.';
            };

            request.send();
        }


        function populateInputField(el, type) {
            document.getElementById("referral_name").value = el.firstChild.data;
            if (type == "consultant")
                document.getElementById("consultantFilter").value = el.getAttribute("data-id").trim();
            //populate address: possible names = referral_address
            document.getElementById("CopyTo").value = el.getAttribute("data-address").trim();
            searchDropDownFlag = false;
            toggleTempBin(0, null);
        }


        function toggleTempBin(a, parentElement) {
            if (a === 1) {
                var position = getOffset(document.getElementById(parentElement));
                new_top = position.top + document.getElementById(parentElement).offsetHeight
                new_left = position.left - 10;
                document.getElementById("tempBin").style.top = "58px";
                document.getElementById("tempBin").style.left = "-2px";
                document.getElementById("tempBin").style.width = document.getElementById(parentElement).offsetWidth + "px";
                document.getElementById("tempBin").style.display = 'block';
            } else if (a === 0 && searchDropDownFlag === false) {
                document.getElementById("tempBin").style.display = 'none';
                document.getElementById("tempBin").textContent = "You must enter at least 2 characters of a patient's name!";
            }
        }

        function getOffset(el) {
            var _x = 0;
            var _y = 0;
            while (el && !isNaN(el.offsetLeft) && !isNaN(el.offsetTop)) {
                _x += el.offsetLeft - el.scrollLeft;
                _y += el.offsetTop - el.scrollTop;
                el = el.offsetParent;
            }
            return {
                top: _y,
                left: _x
            };
        }

        function loaderImg(bin) {
            bin.innerHTML = "";
            var img = document.createElement('img');
            img.src = '../images/loader.gif';
            img.style.marginLeft = "40%";
            bin.appendChild(img);
        }

        // Called from inline onmouseover/onmouseout in the DB-stored form_html:
        //   <div id="tempBin" onmouseover="tempBinHover(true)" onmouseout="tempBinHover(false)">
        // Static analysis flags this as "unused" because the caller is in the database, not in JS.
        function tempBinHover(h) {
            if (h) {
                searchDropDownFlag = true;
            } else {
                searchDropDownFlag = false;
            }
        }

// filesaver.js
/*
* FileSaver.js
* A saveAs() FileSaver implementation.
*
* By Eli Grey, http://eligrey.com
*
* License : https://github.com/eligrey/FileSaver.js/blob/master/LICENSE.md (MIT)
* source  : http://purl.eligrey.com/github/FileSaver.js
*/

/*
//Paste this code to saveAs
var blob = new Blob([$("html").html()], {
    type: "text/html;charset=utf-8"
});
saveAs(blob, "page.html");
}
*/

// The one and only way of getting global scope in all environments
// https://stackoverflow.com/q/3277182/1008999
var _global = typeof window === 'object' && window.window === window
  ? window : typeof self === 'object' && self.self === self
  ? self : typeof global === 'object' && global.global === global
  ? global
  : this

function bom (blob, opts) {
  if (typeof opts === 'undefined') opts = { autoBom: false }
  else if (typeof opts !== 'object') {
    console.warn('Deprecated: Expected third argument to be a object')
    opts = { autoBom: !opts }
  }

  // prepend BOM for UTF-8 XML and text/* types (including HTML)
  // note: your browser will automatically convert UTF-16 U+FEFF to EF BB BF
  if (opts.autoBom && /^\s*(?:text\/\S*|application\/xml|\S*\/\S*\+xml)\s*;.*charset\s*=\s*utf-8/i.test(blob.type)) {
    return new Blob([String.fromCharCode(0xFEFF), blob], { type: blob.type })
  }
  return blob
}

function download (url, name, opts) {
  var xhr = new XMLHttpRequest()
  xhr.open('GET', url)
  xhr.responseType = 'blob'
  xhr.onload = function () {
    saveAs(xhr.response, name, opts)
  }
  xhr.onerror = function () {
    console.error('could not download file')
  }
  xhr.send()
}

function corsEnabled (url) {
  var xhr = new XMLHttpRequest()
  // use sync to avoid popup blocker
  xhr.open('HEAD', url, false)
  try {
    xhr.send()
  } catch (e) {}
  return xhr.status >= 200 && xhr.status <= 299
}

// `a.click()` doesn't work for all browsers (#465)
function click (node) {
  try {
    node.dispatchEvent(new MouseEvent('click'))
  } catch (e) {
    var evt = document.createEvent('MouseEvents')
    evt.initMouseEvent('click', true, true, window, 0, 0, 0, 80,
                          20, false, false, false, false, 0, null)
    node.dispatchEvent(evt)
  }
}

// Detect WebView inside a native macOS app by ruling out all browsers
// We just need to check for 'Safari' because all other browsers (besides Firefox) include that too
// https://www.whatismybrowser.com/guides/the-latest-user-agent/macos
var isMacOSWebView = /Macintosh/.test(navigator.userAgent) && /AppleWebKit/.test(navigator.userAgent) && !/Safari/.test(navigator.userAgent)

var saveAs = _global.saveAs || (
  // probably in some web worker
  (typeof window !== 'object' || window !== _global)
    ? function saveAs () { /* noop */ }

  // Use download attribute first if possible (#193 Lumia mobile) unless this is a macOS WebView
  : ('download' in HTMLAnchorElement.prototype && !isMacOSWebView)
  ? function saveAs (blob, name, opts) {
    var URL = _global.URL || _global.webkitURL
    var a = document.createElement('a')
    name = name || blob.name || 'download'

    a.download = name
    a.rel = 'noopener' // tabnabbing

    // TODO: detect chrome extensions & packaged apps
    // a.target = '_blank'

    if (typeof blob === 'string') {
      // Support regular links
      a.href = blob
      if (a.origin !== location.origin) {
        corsEnabled(a.href)
          ? download(blob, name, opts)
          : (a.target = '_blank', click(a))
      } else {
        click(a)
      }
    } else {
      // Support blobs
      a.href = URL.createObjectURL(blob)
      setTimeout(function () { URL.revokeObjectURL(a.href) }, 4E4) // 40s
      setTimeout(function () { click(a) }, 0)
    }
  }

  // Use msSaveOrOpenBlob as a second approach
  : 'msSaveOrOpenBlob' in navigator
  ? function saveAs (blob, name, opts) {
    name = name || blob.name || 'download'

    if (typeof blob === 'string') {
      if (corsEnabled(blob)) {
        download(blob, name, opts)
      } else {
        var a = document.createElement('a')
        a.href = blob
        a.target = '_blank'
        setTimeout(function () { click(a) })
      }
    } else {
      navigator.msSaveOrOpenBlob(bom(blob, opts), name)
    }
  }

  // Fallback to using FileReader and a popup
  : function saveAs (blob, name, opts, popup) {
    // Open a popup immediately do go around popup blocker
    // Mostly only available on user interaction and the fileReader is async so...
    popup = popup || open('', '_blank')
    if (popup) {
      popup.document.title =
      popup.document.body.innerText = 'downloading...'
    }

    if (typeof blob === 'string') return download(blob, name, opts)

    var force = blob.type === 'application/octet-stream'
    var isSafari = /constructor/i.test(_global.HTMLElement) || _global.safari
    var isChromeIOS = /CriOS\/[\d]+/.test(navigator.userAgent)

    if ((isChromeIOS || (force && isSafari) || isMacOSWebView) && typeof FileReader !== 'undefined') {
      // Safari doesn't allow downloading of blob URLs
      var reader = new FileReader()
      reader.onloadend = function () {
        var url = reader.result
        url = isChromeIOS ? url : url.replace(/^data:[^;]*;/, 'data:attachment/file;')
        if (popup) popup.location.href = url
        else location = url
        popup = null // reverse-tabnabbing #460
      }
      reader.readAsDataURL(blob)
    } else {
      var URL = _global.URL || _global.webkitURL
      var url = URL.createObjectURL(blob)
      if (popup) popup.location = url
      else location.href = url
      popup = null // reverse-tabnabbing #460
      setTimeout(function () { URL.revokeObjectURL(url) }, 4E4) // 40s
    }
  }
)

_global.saveAs = saveAs.saveAs = saveAs

if (typeof module !== 'undefined') {
  module.exports = saveAs;
}
