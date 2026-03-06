/*  editControl - a WYSIWYG edit control using iFrames and designMode
    Copyright (C) 2009-2020 Peter Hutten-Czapski
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
var cfg_formatblock = '<option value="">&mdash; format &mdash;</option>  <option value="<p>">Paragraph</option>  <option value="<h1>">Heading 1</option>  <option value="<h2>">Heading 2 <H2></option>  <option value="<h3>">Heading 3 <H3></option>  <option value="<h4>">Heading 4 <H4></option>  <option value="<h5>">Heading 5 <H5></option>  <option value="<h6>">Heading 6 <H6></option>  </select>';
var cfg_formatface = '<option value="">&mdash; font face &mdash;</option>  <option value="Arial,Helvetica,sans-serif">Arial</option> <option value="Courier">Courier</option> <option value="Times New Roman">Times</option> </select>';
var cfg_formatfontsize = '<option value="">&mdash; font size &mdash;</option>  <option value="1">1</option>  <option value="2">2</option> <option value="3">3</option> <option value="4">4</option> <option value="5">5</option> <option value="6">6</option> <option value="7">7</option> </select>';
var cfg_formattemplate = '<option value="">&mdash; template &mdash;</option>  <option value="blank">blank</option>  </select>';
var cfg_isrc = '';  				// path to icons degrades to text buttons if icons not found
var cfg_filesrc = '';				// path to blank.html and editor_help.html
var cfg_template = 'blank.rtl';		// style and content template of the editor's iframe itself.
var cfg_width = 720;				// editor control width in pixels
var cfg_height = 500;				// editor control height in pixels
var cfg_editorname ="edit";		// handle for the editor control itself
var cfg_bstyle = 'width:24px;height:24px;border: solid 2px #ccccff; background-color: #ccccff;'; 	//the CSS of the button elements
var cfg_boutstyle = 'solid 2px #ccccff'; 	//the CSS of the button elements om mouse out
var cfg_sstyle = 'vertical-align: top; height:24px;';//the CSS of the option select box.  Selects will take font and background but not border.
var cfg_sepstyle = 'width:6px;height:24px;border: solid 2px #ccccff; background-color: #ccccff;';	//the CSS of the seperator icon


function insertEditControl() {
	// The main initialising function which writes the edit control as per passed variables
	// ...OR... if it fails, degrades nicely by supplying a text area with the same ID (cfg_editorname)

	// FIRST BUILD BUTTONS WITH USEFUL ATTRIUBUTES
	// Mozilla requires the title attribute for tool tips, and that works in IE as well
	// The ID matches a execCommand argument and carries the action associated with the button
	// when the action needs a value cmdValue="promptUser" will prompt the user with the promptText attribute
	// the class="editControlButton" identifies the elements that will have button function
	//   -these will change appearance on mouse events and will trigger on mouse click a default action

    var boldButton = '<span class="editControlButton" value="Bold" title="Bold" name="' + cfg_editorname + '" onclick="ex(\'bold\');"><i class="icon-bold"></i></span>';
    var italicButton =  '<span class="editControlButton" value="Italic" title="Italic" name="' + cfg_editorname + '" onclick="ex(\'italic\');"><i class="icon-italic"></i></span>';
    var underlinedButton =  '<span class="editControlButton" value="Underline" title="Underline" name="' + cfg_editorname + '" onclick="ex(\'underline\');"><i class="icon-underline"></i></span>';
    var strikethroughButton = '<span class="editControlButton" title="Strikethrough" name="' + cfg_editorname + '" onclick="ex(\'strikethrough\');"><i class="icon-strikethrough"></i></span>';
    var superscriptButton = '<span class="editControlButton" value="Underline" title="Superscript" name="' + cfg_editorname + '" onclick="ex(\'superscript\');"><i class="icon-superscript"></i></span>';
    var subscriptButton = '<span class="editControlButton" title="Subscript" name="' + cfg_editorname + '" onclick="ex(\'subscript\');"><i class="icon-subscript"></i></span>';
    var leftButton = '<span class="editControlButton" title="Align Left" name="' + cfg_editorname + '" onclick="ex(\'justifyLeft\');"><i class="icon-align-left"></i></span>';
    var centerButton = '<span class="editControlButton" title="Center Align" name="' + cfg_editorname + '" onclick="ex(\'justifyCenter\');"><i class="icon-align-center"></i></span>';
    var fullButton = '<span class="editControlButton" title="Justify" name="' + cfg_editorname + '" onclick="ex(\'justifyFull\');"><i class="icon-align-justify"></i></span>';
    var rightButton = '<span class="editControlButton" title="Align Right" name="' + cfg_editorname + '" onclick="ex(\'justifyRight\');"><i class="icon-align-right"></i></span>';
	var unorderedlistButton = '<span class="editControlButton" title="List" name="' + cfg_editorname + '" onclick="ex(\'insertUnorderedList\');"><i class="icon-list-ul"></i></span>';
	var orderedlistButton = '<span class="editControlButton" title="Numbered List" name="' + cfg_editorname + '" onclick="ex(\'insertOrderedList\');"><i class="icon-list-ol"></i></span>';
	var ruleButton = '<span class="editControlButton" title="Horizontal Rule" name="' + cfg_editorname + '" onclick="ex(\'insertHorizontalRule\');"><i class="icon-ellipsis-horizontal"></i></span>';
	var selectBlock = '<select name="' + cfg_editorname + '" id="formatblock" onchange="Select(this.id);" style="' + cfg_sstyle +'">'+ cfg_formatblock ;
	var selectFace = '<select name="' + cfg_editorname + '" id="fontname" onchange="Select(this.id);" style="' + cfg_sstyle +'">'+ cfg_formatface ;
	var selectSize = '<select name="' + cfg_editorname + '" id="fontsize" onchange="Select(this.id);" style="' + cfg_sstyle +'">'+ cfg_formatfontsize ;
    var selectTemplate = '<select name="' + cfg_editorname + '" id="template" onchange="loadTemplate(this.id);" style="' + cfg_sstyle +'">'+ cfg_formattemplate ;
	var undoButton = '<span class="editControlButton" title="Undo" name="' + cfg_editorname + '" onclick="ex(\'undo\');"><i class="icon-undo"></i></span>';
	var redoButton = '<span class="editControlButton" title="Redo" name="' + cfg_editorname + '" onclick="ex(\'redo\');"><i class="icon-repeat"></i></span>';
	var indentButton = '<span class="editControlButton" title="Indent" name="' + cfg_editorname + '" onclick="ex(\'indent\');"><i class="icon-indent-right"></i></span>';
	var outdentButton = '<span class="editControlButton" title="Outdent" name="' + cfg_editorname + '" onclick="ex(\'outdent\');"><i class="icon-indent-left"></i></span>';
	var selectAllButton = '<span class="editControlButton" title="Select All" name="' + cfg_editorname + '" onclick="ex(\'selectAll\');"><i class="icon-tablet"></i></span>';
    var cleanButton = '<span class="editControlButton" title="Remove Formatting" name="' + cfg_editorname + '" onclick="ex(\'removeFormat\');"><i class="icon-eraser"></i></span>';
	var tableButton = '<span class="editControlButton" title="Table" name="' + cfg_editorname + '" onclick="doHtml(doTable());"><i class="icon-table"></i></span>';
	var textcolourButton = '<span class="editControlButton colours" title="Text Colour" name="' + cfg_editorname + '" onclick="exprompt(\'foreColor\',\'Text Colour?[red]\');"><i class="icon-tint"></i></span>';
	var hilightcolourButton = '<span class="editControlButton highlights" title="Highlight" name="' + cfg_editorname + '" onclick="exprompt(\'backColor\',\'Highlight Colour?[yellow]\');"><i class="icon-check-empty"></i></span>';
    var insertImageButton = '<span class="editControlButton" title="Insert Image" name="' + cfg_editorname + '" onclick="exprompt(\'insertImage\',\'URL of image?\');"><i class="icon-picture"></i></span>';
    var insertLinkButton = '<span class="editControlButton" title="Link" name="' + cfg_editorname + '" onclick="exprompt(\'createLink\',\'URL of link?[http://www.srpc.ca]\');"><i class="icon-link"></i></span>';
	var attachButton = '<span class="editControlButton" title="Attach" name="' + cfg_editorname + '" onclick="popupEformUpload();"><i class="icon-paper-clip"></i></span>';
	var newpageButton = '<span class="editControlButton" title="Page Break" name="' + cfg_editorname + '" onclick="doHtml(doBreak());"><i class="icon-file"></i></span>';
	var clockButton = '<span class="editControlButton" title="Time" name="' + cfg_editorname + '" onclick="doHtml(doTime());"><i class="icon-time"></i></span>';
	var dateButton = '<span class="editControlButton" title="Date" name="' + cfg_editorname + '" onclick="doHtml(doDate());"><i class=" icon-calendar"></i></span>';
	var helpButton = '<span class="editControlButton" title="Help" name="' + cfg_editorname + '" onclick="window.open (\''+cfg_filesrc+'editor_help.html\',\'mywindow\',\'resizable=1,width=300,height=500\');"><i class=" icon-question-sign"></i></span>';
	var editButton = '<span class="editControlButton" title="Edit Template" name="' + cfg_editorname + '" onclick="doEdit();"><i class="icon-edit"></i></span>';
	var exportButton = '<span class="editControlButton" title="Export" name="' + cfg_editorname + '" onclick="doExport();"><i class="icon-save"></i></span>';
	var insertHeading1Button = '<span class="editControlButton" title="Heading" name="' + cfg_editorname + '" onclick="ex(\'formatBlock\',\'<H1>\');"><i class="icon-h-sign"></i></span>';
    var cutButton = '<span class="editControlButton" title="Cut" name="' + cfg_editorname + '" onclick="ex(\'cut\');"><i class="icon-cut"></i></span>';
    var deleteButton = '<span class="editControlButton" title="Delete" name="' + cfg_editorname + '" onclick="ex(\'cut\');"><i class="icon-trash"></i></span>';
    // paste deprecated long ago by all browsers
    //var pasteButton = '<span class="editControlButton" title="Paste" name="' + cfg_editorname + '" onclick="ex(\'paste\');"><i class="icon-paste"></i></span>';
    var copyButton = '<span class="editControlButton" title="Copy" name="' + cfg_editorname + '" onclick="ex(\'copy\');"><i class="icon-copy"></i></span>';

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
	if (document.designMode) {
        //console.log(editControlHTML);
		document.write(editControlHTML);
		//InitToolbarButtons(cfg_editorname);
	} else {
		// create a normal <textarea> if document.designMode does not exist
		//alert("Design mode is not supported by your browser \n- reverting to classic mode");
		document.write('<textarea id="' + cfg_editorname + '" style="width:' + cfg_width + '; height:' + editControlHeight + 'px; ' + editControlStyle + '"></textarea>');
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

function editControlContents(editorname) {
	var value = "";
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
function seteditControlContents(editorname, value){

	// Converting image paths with template style tag to URL format using 'cfg_isrc' using imageControl library.
	value = jQuery().convertImagePaths(value, cfg_isrc);
	
    if (document.designMode) {
		if (isIE()){
		    window[editorname].document.body.innerHTML = value; //if browser supports M$ conventions
		    return
		} else {
		    document.getElementById(editorname).contentWindow.document.body.innerHTML = value;
		    return
		}
	} else {
		// play nice and at least set the value to the <textarea> if document.designMode does not exist
		document.getElementById(cfg_editorname).value = value;
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

function existsTemplate(template) {
	var exists = false;
	$("#template option").each(function() { if ($(this).val() == template) { exists = true; } })
	return exists;	
}

function loadDefaultTemplate() {
	// Skipping loading of default template if the letter already has content.
	if (editControlContents(cfg_editorname).trim() != '') { return; }
	if (existsTemplate(cfg_template)) {
		var selected = cfg_template;
		window.frames[0].location = cfg_filesrc + selected; //FF & IE ***ASSUMES 1 iframe!
		document.getElementById('subject').value = cfg_template == 'blank.rtl' ? "" : selected.substring(0, selected.lastIndexOf("."));		
    	document.getElementById('template').selectedIndex = 0;
		//need to ensure that the new src is loaded before we parse it FF only IE doesn't do nada
		var obj = document.getElementById(cfg_editorname);
		obj.onload = function() { parseTemplate(); }
		//for IE put some delay to ensure that the new src is loaded before we parse it
    	if (isIE()) { setTimeout('parseTemplate()',1000); } //if M$ like browser    	
	} else {
		var blankTemplate = '<html><head><title>Blank Document Template</title><meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\"><style type=\"text/css\">body {font-size: 1em; font-family:\"Times New Roman\", Times, serif; background-color: #FFFFFF;}</style><style type=\"text/css\" media=\"print\">* {color: #000000;}.DoNotPrint {display: none;}</style></head><body contenteditable onLoad=\"document.designMode = \'on\';\"></body></html>';
		document.getElementById(cfg_editorname).src = "data:text/html;charset=utf-8," + escape(blankTemplate);
	}	
}

function loadTemplate(selectname){
	//change the iframe src to that selected in the template select box
	//TODO fix the eventlistener! in the meantime just set the dirty flag
	setDirtyFlag();
  	var cursel = document.getElementById(selectname).selectedIndex;
  	if (cursel != 0) { // First one is a label
    	var selected = document.getElementById(selectname).options[cursel].value;
		//document.getElementById(cfg_editorname).src = cfg_filesrc + selected + '.html' ; //FF != IE
		window.frames[0].location = cfg_filesrc + selected; //FF & IE ***ASSUMES 1 iframe!
		document.getElementById('subject').value = selected == 'blank.rtl' ? "" : selected.substring(0, selected.lastIndexOf("."));		
    	document.getElementById('template').selectedIndex = 0;
		//need to ensure that the new src is loaded before we parse it FF only IE doesn't do nada
		var obj = document.getElementById(cfg_editorname);
		obj.onload = function() { parseTemplate(); }
		//for IE put some delay to ensure that the new src is loaded before we parse it
    		if (isIE()) { setTimeout('parseTemplate()',1000); } //if M$ like browser
    	}
}

function parseTemplate(){
	//replace template placeholders with database pulls
	var contents=editControlContents(cfg_editorname);
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
	var contents=editControlContents(cfg_editorname);
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

function doHtml(value) {
	//insert HTML of value
	if (isIE()){  //if you can't support insertHtml do something else
		var tmp=window[cfg_editorname].document.body.innerHTML;
		tmp=tmp+value;  // for IE this means append the text
		window[cfg_editorname].document.body.innerHTML=tmp;
	} else {
		document.getElementById(cfg_editorname).contentWindow.document.execCommand("insertHtml", false, value);
	}
	return;
}

function block(blockElements) {
	for(i=0; i<blockElements.length; i++) {
		var htm='<div>'+blockElements[i]+'<\div>';
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
	parseText, block, doTime, doDate
];

function doTable() {
	var rowstext = prompt("enter rows");
	var colstext = prompt("enter cols");
	var rows = parseInt(rowstext);
	var cols = parseInt(colstext);
	var table;
	if ((rows > 0) && (cols > 0)) {
		table = '<table style="text-align: left; width: 100%;" border="1" cellpadding="2" cellspacing="2"><tbody>'
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
	$.ajax({ url: window.frames[0].location, success: function(data) { window[cfg_editorname].contentWindow.document.write(data); } });
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
		html = document.getElementById(cfg_editorname).contentWindow.document.body.ownerDocument.createRange();
		html.selectNodeContents(document.getElementById(cfg_editorname).contentWindow.document.body);
		document.getElementById(cfg_editorname).contentWindow.document.body.innerHTML = jQuery().convertImagePaths(html.toString());
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
	if (url == null) { url = window.location.href; }
	name = name.replace(/[\[]/,"\\\[").replace(/[\]]/,"\\\]");
	var regexS = "[\\?&]"+name+"=([^&#]*)";
	var regex = new RegExp(regexS);
	var results = regex.exec(url);
	if (results == null) { return ""; }
	else { return results[1]; }
}

var demographicNo ="";

jQuery(document).ready(function(){
	demographicNo = gup("demographic_no");
	if (demographicNo == "") { demographicNo = gup("efmdemographic_no", jQuery("form").attr('action')); }
	if (typeof signatureControl != "undefined") {
		signatureControl.initialize({
			sigHTML:"../signature_pad/tabletSignature.jsp?inWindow=true&saveToDB=true&demographicNo=",
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
	setTimeout('document.RichTextLetter.submit()',1000);
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
		values: ["provider_name_first_init", "current_user_fname_lname", "doctor", "current_user"],	
		storeInCacheHandler: function (key,value) {		
			var imgsrc=pickStamp();
			var frag = ("<p>Yours Sincerely<p>" + imgsrc + "<p>");
			var salutation = frag;
			if (!cache.isEmpty("provider_name_first_init")) {
					salutation=(frag+cache.get("provider_name_first_init") + ", MD");
				}
			// now allow for signing by the current user 
			if (cache.contains("current_user")) {
				for (i=0; i<ImgArray.length;i++){
					var ListItemArr =  ImgArray[i].split("|");
					var UserName = ListItemArr[0];
					var FileName = ListItemArr[1];
					if (cache.get('current_user').indexOf(UserName)>=0){
						console.log('current user has a signature so use it in the closing salutation');					
							salutation=(frag+cache.get("current_user_fname_lname") + ", MD");
						}
					}
			}
			cache.put(this.name, salutation);
		},
		cacheResponseHandler:function () {
			if (checkKeyResponse(this.name)) {				
				doHtml(cache.get(this.name));
			}	
		}
	});
	

	cache.addMapping({
		name: "stamp", 
		values: ["stamp_name", "doctor", "current_user"], 
		storeInCacheHandler: function(_key,_val) { 
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
	
	function Start() {
		
			$.ajax({
				url : "efmformrtl_templates.jsp",
				success : function(data) {
					$("#template").html(data);
					loadDefaultTemplate();
				}
			});

			
			$(".cacheInit").each(function() { 
				cache.put($(this).attr('name'), $(this).val());
				$(this).remove();				
			});
			
			// set eventlistener for the iframe to flag changes in the text displayed 
			var agent = navigator.userAgent.toLowerCase(); //for non IE browsers
			if ((agent.indexOf("msie") == -1) || (agent.indexOf("opera") != -1)) {
				document.getElementById(cfg_editorname).contentWindow
						.addEventListener('keypress', setDirtyFlag, true);
			}
				
			// set the HTML contents of this edit control from the value saved in OSCAR (if any)
			var contents = document.getElementById('Letter').value
			if (contents.length == 0) {
				parseTemplate();
			} else {
				seteditControlContents(cfg_editorname, contents);
//				seteditControlContents(cfg_editorname, decodeURIComponent(contents));
				document.getElementById(cfg_editorname).contentWindow.document.designMode = 'on';
			}
			maximize();
			
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
		// use the doctors name to allow for a secretary to write a letter under direction
		if (cache.contains("doctor")) {
			var str = cache.get('doctor');
			var res = str.split(", "); //last, first
			console.log("doctor="+res[0]);
		}
		// use the current user instead if they have a signature on file 
		if (cache.contains("current_user")) {
			var str = cache.get('current_user');
			console.log(str);
			for (i=0; i<ImgArray.length;i++){
		        var ListItemArr =  ImgArray[i].split("|");
		        var UserName = ListItemArr[0];
		        var FileName = ListItemArr[1];
		        if (str.indexOf(UserName)>=0){
					console.log('current user '+str+' has a signature so use their name');					
		            var str = cache.get('current_user');
					var res = str.split(", "); //last, first
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

 
	function pickStamp() {
		// set the HTML contents of the signature stamp
		// for single user set stamp.png to be that users signature image in the images folder
		// otherwise form a stamp.js file and upload to images with content as below 
		// defining key value pairs of name of doctor and the corresponding image file
		// var ImgArray = [
		//	"doctor|SignatureFile.png",
		//	];

		var mystamp ='<img src="../eform/displayImage.do?imagefile=stamp.png" width="200" height="100">';
		if (cache.contains("doctor")) {
			for (i=0; i<ImgArray.length;i++){
		        var ListItemArr =  ImgArray[i].split("|");
		        var UserName = ListItemArr[0];
		        var FileName = ListItemArr[1];
		        if (cache.get('doctor').indexOf(UserName)>=0){
		            mystamp = '<img src="../eform/displayImage.do?imagefile='+FileName+'" width="200" height="100" />';
			        }
				}
		}
		// now allow for signing by the current user 
		if (cache.contains("current_user")) {
			for (i=0; i<ImgArray.length;i++){
		        var ListItemArr =  ImgArray[i].split("|");
		        var UserName = ListItemArr[0];
		        var FileName = ListItemArr[1];
		        if (cache.get('current_user').indexOf(UserName)>=0){
					console.log('current user has a signature so use it');					
		            mystamp = '<img src="../eform/displayImage.do?imagefile='+FileName+'" width="200" height="100" />';
			        }
				}
		}
		return mystamp;
	}
	var formIsRTL = true;


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


// probably wrong fid  TODO
var formPath = vPath + "/eform/efmshowform_data.jsp?fid=74&LabName="
var measureArray = [];
var measureDateArray = [];

function getMeasures(measure, max) {
    var xmlhttp = new XMLHttpRequest();
    var pathArray = window.location.pathname.split('/');
    var newURL = "..//oscarEncounter/oscarMeasurements/SetupDisplayHistory.do?type=" + measure;
    xmlhttp.onreadystatechange = function() {
        if (xmlhttp.readyState == 4 && xmlhttp.status == 200) {
            var str = xmlhttp.responseText; //local variable
            if (!str) {
                return;
            }
            var myRe = /<td width="10">([0-9.,]+)<\/td>/g; //for the measurement
            var myArray;
            measureArray = []
            measureDateArray = []
            var i = 0;
            while ((myArray = myRe.exec(str)) !== null) {
                measureArray[i] = myArray[1];
                i = i + 1;
            }

            var myRe = /<td width="150">([0-9,-]+)<\/td>\s*<td width="150">/g; //the first date is the observation date
            var myArray;
            var i = 0;
            while ((myArray = myRe.exec(str)) !== null) {
                measureDateArray[i] = myArray[1];
                i = i + 1;
            }
        }
    }
    xmlhttp.open("GET", newURL, false);
    xmlhttp.send();
    //alert(this.patient_name.value)
    if (measureArray.length > 0) {
        //myGraphWindow = "<a href=" + formPath + measure + "&GraphType=Bar" + "&mA=" + measureArray + "&mDA=" + measureDateArray + " target='_blank'>" + measure + ": " + "</a>"
       var myGraphWindow = measure + ": "

       
 //myGraphWindow = formPath + measure + measureArray + measureDateArray + measure + ": "
         doHtml("<font size='3'>"+myGraphWindow +"</font>");
        var displaynum = measureArray.length
        if (measureArray.length > max) {
            displaynum = max
        }
        for (var jj = 0; jj < displaynum; jj++) {
            var d = new Date(measureDateArray[jj])
            var LabDate = "(" + d.getFullYear() + "/" + (d.getMonth() + 1) + "); "
            doHtml("<font size='3'>"+measureArray[jj].bold()+ "</font>"+"<font size='2'>"+LabDate+ "</font>")
        }
           doHtml("<br></br>");
    }
}

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

        function consultantSearch(term) {
            if (term.length < 2) {
                document.getElementById('tempBin').innerHTML = "You must enter at least 2 characters of a patients name!";
                return false;
            }

            var tmpBin = document.getElementById('tempBin');

            loaderImg(tmpBin);

            var request = new XMLHttpRequest();

            //professionalSpecialists  2020-Nov-04
            //request.open('GET', '../oscarEncounter/oscarConsultationRequest/searchprofessionalSpecialists.json?keyword=' + term, true);
            request.open('GET', '../oscarEncounter/oscarConsultationRequest/searchProfessionalSpecialist.json?keyword='+term, true);
            request.setRequestHeader("Content-Type", "application/json");
            request.onload = function() {

                if (request.status >= 200 && request.status < 400) {

                    // Request finished. Do processing here.
                    var data = JSON.parse(this.response);
                    var results_html = "";
                    if (data.length > 0) {
                        results_html += "<ul class=\"custom-dropdown\">";
                        for (i = 0; i <= data.length - 1; i++) {
                            results_html += "<li onclick=\"populateInputField(this, 'consultant')\" data-id=\"" + data[i].id + " \" data-address=\"" + data[i].firstName + ' ' + data[i].lastName + '\r\n' + data[i].streetAddress + ' \r\nFax: ' + data[i].fax + "\">" + data[i].lastName + ", " + data[i].firstName + "</li>";
                        }
                        results_html += "</ul>";
                    } else {
                        results_html = "No results found matching <b>" + term + "</b>.";
                    }


                    //ensure the loader has time to display
                    setTimeout(() => {
                        document.getElementById('tempBin').innerHTML = results_html;
                    }, 500);


                } else {
                    console.log('error')
                }

            } // end onload

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
                document.getElementById("tempBin").innerHTML = "You must enter at least 2 characters of a patients name!";
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



