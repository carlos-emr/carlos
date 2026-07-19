/**
 *  Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved. *
 *  This software is published under the GPL GNU General Public License.
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version. *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details. * * You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA. *
 *
 *
 *  This software was written for the
 *  Department of Family Medicine
 *  McMaster University
 *  Hamilton
 *  Ontario, Canada   Creates a new instance of CommonLabResultData
 *
 *  Refactored to remove prototype.js dependencies and use native JS/fetch API
 */


/************init global data methods*****************/
let oldestLab;

/**
 * Helper function to show an element
 * @param {HTMLElement|string} el - Element or element ID
 */
function showElement(el) {
    const element = typeof el === 'string' ? document.getElementById(el) : el;
    if (element) element.style.display = '';
}

/**
 * Helper function to hide an element
 * @param {HTMLElement|string} el - Element or element ID
 */
function hideElement(el) {
    const element = typeof el === 'string' ? document.getElementById(el) : el;
    if (element) element.style.display = 'none';
}

/**
 * Helper function to serialize form data to URL-encoded string
 * @param {HTMLFormElement|string} form - Form element or form ID
 * @returns {string}
 */
function serializeForm(form) {
    const formElement = typeof form === 'string' ? document.getElementById(form) : form;
    if (!formElement) return '';
    const formData = new FormData(formElement);
    return new URLSearchParams(formData).toString();
}

/**
 * Helper function to serialize form data to object
 * @param {HTMLFormElement|string} form - Form element or form ID
 * @returns {Object}
 */
function serializeFormToObject(form) {
    const formElement = typeof form === 'string' ? document.getElementById(form) : form;
    if (!formElement) return {};
    const formData = new FormData(formElement);
    const obj = {};
    formData.forEach((value, key) => {
        if (obj.hasOwnProperty(key)) {
            if (!Array.isArray(obj[key])) {
                obj[key] = [obj[key]];
            }
            obj[key].push(value);
        } else {
            obj[key] = value;
        }
    });
    return obj;
}

/**
 * Helper function to make a POST request with form-urlencoded data.
 * Centralizes fetch boilerplate for form submissions.
 * @param {string} url - The URL to POST to
 * @param {string|Object} data - URL-encoded string or object to be converted
 * @returns {Promise<Response>}
 */
function postForm(url, data) {
    return fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: typeof data === 'string' ? data : new URLSearchParams(data).toString()
    });
}

/**
 * Helper function to make a GET request.
 * @param {string} url - The URL to fetch
 * @returns {Promise<Response>}
 */
function fetchGet(url) {
    return fetch(url, { method: 'GET' });
}

/**
 * Helper function to append HTML content to a container and execute any scripts.
 * Browsers don't execute scripts inserted via innerHTML/insertAdjacentHTML,
 * so this function parses the HTML, extracts script tags, and re-adds them
 * as real script elements to ensure execution.
 * @param {HTMLElement} container - The container element to append HTML to
 * @param {string} html - HTML string that may contain script tags
 */
function appendHtmlWithScripts(container, html) {
    if (!container || !html) return;

    container.insertAdjacentHTML('beforeend', html);

    const parser = new DOMParser();
    const doc = parser.parseFromString(html, 'text/html');

    doc.querySelectorAll('script').forEach(script => {
        const newScript = document.createElement('script');
        if (script.src) {
            newScript.src = script.src;
        } else {
            newScript.textContent = script.textContent;
        }
        document.head.appendChild(newScript).parentNode.removeChild(newScript);
    });
}

function updateDocStatusInQueue(docid) {//change status of queue document link row to I=inactive
    console.log('in updateDocStatusInQueue, docid ' + docid);
    const url = ctx + "/documentManager/inboxManage.do";
    const data = "docid=" + docid + "&method=updateDocStatusInQueue";

    postForm(url, data)
        .then(response => response.text())
        .then(text => console.log(text))
        .catch(error => console.error('Error:', error));
}

function saveNext(docid) {
    updateDocumentAndNext('forms_' + docid);
}

function initPatientIds(s) {
    const r = [];
    const t = s.split(',');
    for (let i = 0; i < t.length; i++) {
        const e = t[i];
        e.replace(/\s/g, '');
        if (e.length > 0) {
            r.push(e);
        }
    }
    return r;
}

function initTypeDocLab(s) {
    return initHashtableWithList(s);
}

function initPatientDocs(s) {
    return initHashtableWithList(s);
}

function initDocStatus(s) {
    return initHashtableWithString(s);
}

function initDocType(s) {
    return initHashtableWithString(s);
}

function initNormals(s) {
    return initList(s);
}

function initAbnormals(s) {
    return initList(s);
}

function initPatientIdNames(s) {//;1=abc,def;2=dksi,skal;3=dks,eiw
    const ar = s.split(';');
    const r = new Object();
    for (let i = 0; i < ar.length; i++) {
        const e = ar[i];
        if (e.length > 0) {
            const ear = e.split('=');
            if (ear && ear != null && ear.length > 1) {
                const k = ear[0];
                const v = ear[1];
                r[k] = v;
            }
        }
    }
    return r;
}

function initHashtableWithList(s) {//for typeDocLab,patientDocs
    s = s.replace('{', '');
    s = s.replace('}', '');
    if (s.length > 0) {
        const sar = s.split('],');
        const r = new Object();
        for (let i = 0; i < sar.length; i++) {
            let ele = sar[i];
            ele = ele.replace(/\s/g, '');
            const elear = ele.split('=');
            const key = elear[0];
            let val = elear[1];
            val = val.replace('[', '');
            val = val.replace(']', '');
            val = val.replace(/\s/g, '');
            //console.log(key);
            //console.log(val);
            const valar = val.split(',');
            r[key] = valar;
        }
        return r;
    } else {
        return new Object();
    }

}

function initHashtableWithString(s) {//for docStatus,docType
    s = s.replace('{', '');
    s = s.replace('}', '');
    s = s.replace(/\s/g, '');
    const sar = s.split(',');
    const r = new Object();
    for (let i = 0; i < sar.length; i++) {
        const ele = sar[i];
        if (ele.length > 0) {
            const ear = ele.split('=');
            if (ear.length > 0) {
                const key = ear[0];
                const val = ear[1];
                r[key] = val;
            }
        }
    }
    return r;
}

function initList(s) {//normals,abnormals
    s = s.replace('[', '');
    s = s.replace(']', '');
    s = s.replace(/\s/g, '');
    if (s.length > 0) {
        const sar = s.split(',');
        return sar;
    } else {
        const re = [];
        return re;
    }
}

/********************global data util methods *****************************/
function getDocLabFromCat(cat) {
    if (cat.length > 0) {
        return typeDocLab[cat];
    }
}

function removeIdFromDocStatus(doclabid) {
    delete docStatus[doclabid];
}

function removeIdFromDocType(doclabid) {
    if (doclabid && doclabid != null) {
        delete docType[doclabid];
    }
}

function removeIdFromTypeDocLab(doclabid) {
    for (let j = 0; j < types.length; j++) {
        const cat = types[j];
        const a = typeDocLab[cat];
        if (a && a != null) {
            if (a.length > 0) {
                const i = a.indexOf(doclabid);
                if (i != -1) {
                    a.splice(i, 1);
                    typeDocLab[cat] = a;
                }
            } else {
                delete typeDocLab[cat];
            }
        }
    }
}

function removeNormal(doclabid) {
    const index = normals.indexOf(doclabid);
    if (index != -1) {
        normals.splice(index, 1);
    }
}

function removeAbnormal(doclabid) {
    const index = abnormals.indexOf(doclabid);
    if (index != -1) {
        abnormals.splice(index, 1);
    }
}

function removePatientId(pid) {
    if (pid) {
        const i = patientIds.indexOf(pid);
        //console.log('i='+i+'patientIds='+patientIds);
        if (i != -1) {
            patientIds.splice(i, 1);
        }
        //console.log(patientIds);
    }
}

function removeEmptyPairFromPatientDocs() {
    const notUsedPid = [];
    for (let i = 0; i < patientIds.length; i++) {
        const pid = patientIds[i];
        const e = patientDocs[pid];

        if (!e) {
            notUsedPid.push(pid);
        } else if (e == null || e.length == 0) {
            delete patientDocs[pid];
        }
    }
    //console.log(notUsedPid);
    for (let i = 0; i < notUsedPid.length; i++) {
        removePatientId(notUsedPid[i]);//remove pid if it doesn't relate to any doclab
    }
}

function removeIdFromPatientDocs(doclabid) {
//	console.log('in removeidfrompatientdocs'+doclabid);
//console.log(patientIds);
//console.log(patientDocs);
    for (let i = 0; i < patientIds.length; i++) {
        const pid = patientIds[i];
        const a = patientDocs[pid];
        //console.log('a');
        //console.log(a);
        if (a && a.length > 0) {
            const f = a.indexOf(doclabid);
            //console.log('before splice');
            //console.log(patientDocs);
            if (f != -1) {
                a.splice(f, 1);
                patientDocs[pid] = a;
            }
            //console.log('after splice');
            //console.log(patientDocs);
        } else {
            delete patientDocs[pid];
            //console.log('after delete');
            //console.log(patientDocs);
        }
    }
//console.log('after remove');
//console.log(patientDocs);
}

function addIdToPatient(did, pid) {
    const a = patientDocs[pid];
    if (a && a != null) {
        a.push(did);
        patientDocs[pid] = a;
    } else {
        const ar = [did];
        patientDocs[pid] = ar;
    }
}

function addPatientId(pid) {
    patientIds.push(pid);
}

function addPatientIdName(pid, name) {
    const n = patientIdNames[pid];
    if (n || n == null) {
        patientIdNames[pid] = name;
    }

}

function sendMRP(ele) {
    let doclabid = ele.id;
    doclabid = doclabid.split('_')[1];
    const demofindEl = document.getElementById('demofind' + doclabid);
    const demoId = demofindEl ? demofindEl.value : '-1';
    if (demoId == '-1') {
        alert('Please enter a valid demographic');
        ele.checked = false;
    } else if (confirm('Send to Most Responsible Provider?')) {
        const type = checkType(doclabid);
        const url = contextpath + "/oscarMDS/SendMRP.do";
        const data = 'demoId=' + demoId + '&docLabType=' + type + '&docLabId=' + doclabid;

        postForm(url, data)
            .then(response => {
                if (response.ok) {
                    ele.disabled = true;
                    hideElement('mrp_fail_' + doclabid);
                } else {
                    throw new Error('Request failed');
                }
            })
            .catch(error => {
                ele.checked = false;
                showElement('mrp_fail_' + doclabid);
            });
    } else {
        ele.checked = false;
    }
}

function rotate180(id) {
    jQuery("#rotate180btn_" + id).attr('disabled', 'disabled');
    const displayDocumentAsEl = document.getElementById('displayDocumentAs_' + id);
    const displayDocumentAs = displayDocumentAsEl ? displayDocumentAsEl.value : '';

    postForm(contextpath + "/documentManager/SplitDocument.do", "method=rotate180&document=" + id)
        .then(response => response.text())
        .then(data => {
            jQuery("#rotate180btn_" + id).removeAttr('disabled');
            if (displayDocumentAs == "PDF") {
                showPDF(id, contextpath);
            } else {
                jQuery("#docImg_" + id).attr('src', contextpath + "/documentManager/ManageDocument.do?method=viewDocPage&doc_no=" + id + "&curPage=1&rand=" + (new Date().getTime()));
            }
        })
        .catch(error => console.error('Error:', error));
}

function rotate90(id) {
    jQuery("#rotate90btn_" + id).attr('disabled', 'disabled');
    const displayDocumentAsEl = document.getElementById('displayDocumentAs_' + id);
    const displayDocumentAs = displayDocumentAsEl ? displayDocumentAsEl.value : '';

    postForm(contextpath + "/documentManager/SplitDocument.do", "method=rotate90&document=" + id)
        .then(response => response.text())
        .then(data => {
            jQuery("#rotate90btn_" + id).removeAttr('disabled');
            if (displayDocumentAs == "PDF") {
                showPDF(id, contextpath);
            } else {
                jQuery("#docImg_" + id).attr('src', contextpath + "/documentManager/ManageDocument.do?method=viewDocPage&doc_no=" + id + "&curPage=1&rand=" + (new Date().getTime()));
            }
        })
        .catch(error => console.error('Error:', error));
}

function removeFirstPage(id) {
    jQuery("#removeFirstPagebtn_" + id).attr('disabled', 'disabled');
    if (confirm("!! This is a destructive action that can cause loss of document data !! \n Click OK to delete the first page of this document, or Cancel to abort.")) {
        ShowSpin(true);
        const displayDocumentAsEl = document.getElementById('displayDocumentAs_' + id);
        const displayDocumentAs = displayDocumentAsEl ? displayDocumentAsEl.value : '';

        postForm(contextpath + "/documentManager/SplitDocument.do", "method=removeFirstPage&document=" + id)
            .then(response => response.text())
            .then(data => {
                if (displayDocumentAs == "PDF") {
                    showPDF(id, contextpath);
                } else {
                    jQuery("#docImg_" + id).attr('src', contextpath + "/documentManager/ManageDocument.do?method=viewDocPage&doc_no=" + id + "&curPage=1&rand=" + (new Date().getTime()));
                }
                const numPages = parseInt(jQuery("#numPages_" + id).text()) - 1;
                jQuery("#numPages_" + id).text("" + numPages);

                if (numPages <= 1) {
                    jQuery("#numPages_" + id).removeClass("multiPage");
                    jQuery("#removeFirstPagebtn_" + id).remove();
                }
                HideSpin();
                jQuery("#removeFirstPagebtn_" + id).removeAttr('disabled');
            })
            .catch(error => {
                console.error('Error:', error);
                HideSpin();
                jQuery("#removeFirstPagebtn_" + id).removeAttr('disabled');
            });
    }
}

function split(id) {
    const loc = contextpath + "/oscarMDS/Split.jsp?document=" + id;
    popupStart(1400, 1400, loc, "Splitter");
}

function hideTopBtn() {
    hideElement('topFRBtn');
    hideElement('topFBtn');
    hideElement('topFileBtn');
}

function showTopBtn() {
    showElement('topFRBtn');
    showElement('topFBtn');
    showElement('topFileBtn');
}

function popupStart(vheight, vwidth, varpage, windowname) {
    //console.log("in popupstart 4 args");
    //console.log(vheight+"--"+ vwidth+"--"+ varpage+"--"+ windowname);
    if (!windowname)
        windowname = "helpwindow";
    //console.log(vheight+"--"+ vwidth+"--"+ varpage+"--"+ windowname);
    const page = varpage;
    const windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
    const popup = window.open(varpage, windowname, windowprops);
}

function reportWindow(page, height, width) {
    //console.log(page);
    let windowprops;
    if (height && width) {
        windowprops = "height=" + height + ", width=" + width + ", location=no, scrollbars=yes, menubars=no, toolbars=no, resizable=yes, top=0, left=0";
    } else {
        windowprops = "height=660, width=960, location=no, scrollbars=yes, menubars=no, toolbars=no, resizable=yes, top=0, left=0";
    }
    const popup = window.open(encodeURI(page), "labreport", windowprops);
    popup.focus();
}

function FileSelectedRows(files, searchProviderNo, status) {
    const filelabs = {"flaggedLabs": "{\"files\" : " + JSON.stringify(files) + "}"};
    const url = ctx + "/oscarMDS/FileLabs.do";
    bulkInboxAction(url, filelabs);
}

function submitFile(searchProviderNo, status) {
    let files = [];
    jQuery("input[name='flaggedLabs']:checkbox:checked").each(function (key, value) {
        files[key] = value.value;
    })
    if (files.length === 0) {
        alert("Select documents to be filed.");
        return;
    }
    console.log("File lab from: oscarMDSIndex.js Status: " + status + " Provider: " + searchProviderNo + " Files: " + files);
    FileSelectedRows(files, searchProviderNo, status);
}

function submitForward(searchProviderNo, status) {
    const files = [];
    jQuery("input[name='flaggedLabs']:checkbox:checked").each(function (key, value) {
        files[key] = value.value;
    })
    if (files.length === 0) {
        alert("Select documents to be forwarded");
        return;
    }

    ForwardSelectedRows(files, searchProviderNo, status);
}

function bulkInboxAction(url, filelabs) {

    jQuery.ajax({
        type: "POST",
        url: url,
        data: filelabs,
        success: function (data) {
            if (data.success) {
                let files = data.files;
                let file;
                let fileId;
                for (let i = 0; i < files.length; i++) {
                    // remove the filed lab from the DOM
                    file = files[i];
                    fileId = file.split(":")[0];
                    jQuery("#labdoc_" + fileId + " input[name='flaggedLabs']").attr("checked", false)

                    if (url.includes("FileLabs.do")) {
                        jQuery("#labdoc_" + fileId).remove();
                    }
                }

                jQuery("input[name='checkA']").attr("checked", false);

                if (jQuery("input[name='isListView']").length) {
                    updateCategoryList();
             } else if (jQuery("#btnViewMode").length) {
				fetchInboxhubData();
                } else {
                    location.reload();
                }

                jQuery("#dialog").dialog("close");
            }
        }
    });
}

function isRowShown(rowid) {
    const el = document.getElementById(rowid);
    if (el && el.style.display == 'none')
        return false;
    else
        return true;
}

function checkAllLabs(formId) {
    const val = document.getElementsByName("checkA")[0].checked;
    const labs = document.getElementsByName("flaggedLabs");
    for (let i = 0; i < labs.length; i++) {
        if (labs[i].disabled === false) {
            labs[i].checked = val;
        }
    }
}

function wrapUp() {
    if (opener.callRefreshTabAlerts) {
        opener.callRefreshTabAlerts("oscar_new_lab");
        setTimeout("window.close();", 100);
    } else {
        window.close();
    }
}

function showDocLab(childId, docNo, providerNo, searchProviderNo, status, demoName, showhide) {//showhide is 0 = document currently hidden, 1=currently shown
    //create child element in docViews
    docNo = docNo.replace(' ', '');//trim
    const type = checkType(docNo);
    //oscarLog('type'+type);
    //var div=childId;

    //var div=window.frames[0].document.getElementById(childId);
    const div = document.getElementById(childId);
    //alert(div);
    let url = '';
    if (type == 'DOC')
        url = "../documentManager/showDocument.jsp";
    else if (type == 'MDS')
        url = "";
    else if (type == 'HL7')
        url = "../lab/CA/ALL/labDisplayAjax.jsp";
    else if (type == 'CML')
        url = "";
    else
        url = "";

    docNo = docNo.replace('d', '');
    //oscarLog('url='+url);
    const data = "segmentID=" + docNo + "&providerNo=" + providerNo + "&searchProviderNo=" + searchProviderNo + "&status=" + status + "&demoName=" + demoName;
    //oscarLog('url='+url+'+-+ \n data='+data);

    fetchGet(url + '?' + data)
        .then(response => response.text())
        .then(html => {
            appendHtmlWithScripts(div, html);
            focusFirstDocLab();
        })
        .catch(error => console.error('Error loading document:', error));
}

function createNewElement(parent, child) {
    //oscarLog('11 create new leme');
    const newdiv = document.createElement('div');
    //oscarLog('22 after create new leme');
    newdiv.setAttribute('id', child);
    const parentdiv = document.getElementById(parent);
    parentdiv.appendChild(newdiv);
    //oscarLog('55 after create new leme');
}

function clearDocView() {
    const docview = document.getElementById('docViews');
    //var docview=window.frames[0].document.getElementById('docViews');
    if (docview) docview.textContent = '';
}

function showhideSubCat(plus_minus, patientId) {
    if (plus_minus == 'plus') {
        hideElement('plus' + patientId);
        showElement('minus' + patientId);
        showElement('labdoc' + patientId + 'showSublist');
    } else {
        hideElement('minus' + patientId);
        showElement('plus' + patientId);
        hideElement('labdoc' + patientId + 'showSublist');
    }
}

function un_bold(ele) {
    //oscarLog('currentbold='+currentBold+'---ele.id='+ele.id);
    if (ele == null || currentBold == ele.id) {
        ;
    } else {
        const currentBoldEl = document.getElementById(currentBold);
        if (currentBold && currentBoldEl != null) {
            currentBoldEl.style.fontWeight = '';
        }
        ele.style.fontWeight = 'bold';
        currentBold = ele.id;
    }
    //oscarLog('currentbold='+currentBold+'---ele.id='+ele.id);
}

function re_bold(id) {
    const el = document.getElementById(id);
    if (id && el != null) {
        el.style.fontWeight = 'bold';
    }
}

function showPageNumber(page) {
    const totalNoRowEl = document.getElementById('totalNumberRow');
    const totalNoRow = totalNoRowEl ? totalNoRowEl.value : 0;
    const newStartIndex = number_of_row_per_page * (parseInt(page) - 1);
    let newEndIndex = parseInt(newStartIndex) + 19;
    let isLastPage = false;
    if (newEndIndex > totalNoRow) {
        newEndIndex = totalNoRow;
        isLastPage = true;
    }
    //oscarLog("new start="+newStartIndex+";new end="+newEndIndex);
    for (let i = 0; i < totalNoRow; i++) {
        const rowEl = document.getElementById('row' + i);
        if (rowEl && parseInt(newStartIndex) <= i && i <= parseInt(newEndIndex)) {
            //oscarLog("show row-"+i);
            showElement(rowEl);
        } else if (rowEl) {
            //oscarLog("hide row-"+i);
            hideElement(rowEl);
        }
    }
    //update current page
    const currentPageNumEl = document.getElementById('currentPageNum');
    if (currentPageNumEl) currentPageNumEl.textContent = page;
    if (page == 1) {
        const msgPreviousEl = document.getElementById('msgPrevious');
        if (msgPreviousEl) hideElement(msgPreviousEl);
    } else if (page > 1) {
        const msgPreviousEl = document.getElementById('msgPrevious');
        if (msgPreviousEl) showElement(msgPreviousEl);
    }
    if (isLastPage) {
        const msgNextEl = document.getElementById('msgNext');
        if (msgNextEl) hideElement(msgNextEl);
    } else {
        const msgNextEl = document.getElementById('msgNext');
        if (msgNextEl) showElement(msgNextEl);
    }
}

function showTypePageNumber(page, type) {
    let eles;
    const numberPerPage = 20;
    if (type == 'D') {
        eles = document.getElementsByName('scannedDoc');
        const length = eles.length;
        const startindex = (parseInt(page) - 1) * numberPerPage;
        let endindex = startindex + numberPerPage - 1;
        if (endindex > length - 1) {
            endindex = length - 1;
        }
        //only display current page
        for (let i = startindex; i < endindex + 1; i++) {
            const ele = eles[i];
            ele.style.display = 'table-row';
        }
        //hide previous page
        for (let i = 0; i < startindex; i++) {
            const ele = eles[i];
            ele.style.display = 'none';
        }
        //hide later page
        for (let i = endindex; i < length; i++) {
            const ele = eles[i];
            ele.style.display = 'none';
        }
        //hide all labs
        eles = document.getElementsByName('HL7lab');
        for (let i = 0; i < eles.length; i++) {
            const ele = eles[i];
            ele.style.display = 'none';
        }
    } else if (type == 'H') {
        eles = document.getElementsByName('HL7lab');
        const length = eles.length;
        const startindex = (parseInt(page) - 1) * numberPerPage;
        let endindex = startindex + numberPerPage - 1;
        if (endindex > length - 1) {
            endindex = length - 1;
        }
        //only display current page
        for (let i = startindex; i < endindex + 1; i++) {
            const ele = eles[i];
            ele.style.display = 'table-row';
        }
        //hide previous page
        for (let i = 0; i < startindex; i++) {
            const ele = eles[i];
            ele.style.display = 'none';
        }
        //hide later page
        for (let i = endindex; i < length; i++) {
            const ele = eles[i];
            ele.style.display = 'none';
        }
        //hide all labs
        eles = document.getElementsByName('scannedDoc');
        for (let i = 0; i < eles.length; i++) {
            const ele = eles[i];
            ele.style.display = 'none';
        }
    } else if (type == 'N') {
        const eles1 = document.getElementsByClassName('NormalRes');
        const length = eles1.length;
        const startindex = (parseInt(page) - 1) * numberPerPage;
        let endindex = startindex + numberPerPage - 1;
        if (endindex > length - 1) {
            endindex = length - 1;
        }

        for (let i = startindex; i < endindex + 1; i++) {
            const ele = eles1[i];
            ele.style.display = 'table-row';
        }
        //hide previous page
        for (let i = 0; i < startindex; i++) {
            const ele = eles1[i];
            ele.style.display = 'none';
        }
        //hide later page
        for (let i = endindex; i < length; i++) {
            const ele = eles1[i];
            ele.style.display = 'none';
        }
        //hide all abnormals
        const eles2 = document.getElementsByClassName('AbnormalRes');
        for (let i = 0; i < eles2.length; i++) {
            const ele = eles2[i];
            ele.style.display = 'none';
        }
    } else if (type == 'AB') {
        const eles1 = document.getElementsByClassName('AbnormalRes');
        const length = eles1.length;
        const startindex = (parseInt(page) - 1) * numberPerPage;
        let endindex = startindex + numberPerPage - 1;
        if (endindex > length - 1) {
            endindex = length - 1;
        }
        for (let i = startindex; i < endindex + 1; i++) {
            const ele = eles1[i];
            ele.style.display = 'table-row';
        }
        //hide previous page
        for (let i = 0; i < startindex; i++) {
            const ele = eles1[i];
            ele.style.display = 'none';
        }
        //hide later page
        for (let i = endindex; i < length; i++) {
            const ele = eles1[i];
            ele.style.display = 'none';
        }
        //hide all normals
        const eles2 = document.getElementsByClassName('NormalRes');
        for (let i = 0; i < eles2.length; i++) {
            const ele = eles2[i];
            ele.style.display = 'none';
        }
    }
}

function setTotalRows() {
    const ds = document.getElementsByName('scannedDoc');
    const ls = document.getElementsByName('HL7lab');
    for (let i = 0; i < ds.length; i++) {
        const ele = ds[i];
        total_rows.push(ele.id);
    }
    for (let i = 0; i < ls.length; i++) {
        const ele = ls[i];
        total_rows.push(ele.id);
    }
    total_rows = sortRowId(uniqueArray(total_rows));
    current_category = [];
    current_category[0] = document.getElementsByName('scannedDoc');
    current_category[1] = document.getElementsByName('HL7lab');
    current_category[2] = document.getElementsByClassName('NormalRes');
    current_category[3] = document.getElementsByClassName('AbnormalRes');
}

function checkBox() {
    let view = "all";
    const documentCB = document.getElementById('documentCB');
    const hl7CB = document.getElementById('hl7CB');
    const normalCB = document.getElementById('normalCB');
    const abnormalCB = document.getElementById('abnormalCB');
    const unassignedCB = document.getElementById('unassignedCB');

    if (documentCB && documentCB.checked == 1) {
        view = "documents";
    } else if (hl7CB && hl7CB.checked == 1) {
        view = "labs";
    } else if (normalCB && normalCB.checked == 1) {
        checkedArray.push('normal');
        view = "normal";
    } else if (abnormalCB && abnormalCB.checked == 1) {
        view = "abnormal";
    } else if (unassignedCB && unassignedCB.checked == 1) {
        view = "unassigned";
    }
    window.location.search = replaceQueryString(window.location.search, "view", view);

}

function displayCategoryPage(page) {
    //oscarLog('in displaycategorypage, page='+page);
    //write all row ids to an array
    let displayrowids = [];
    for (let p = 0; p < current_category.length; p++) {
        let elements = [];
        elements = current_category[p];
        //oscarLog("elements.lenght="+elements.length);
        for (let j = 0; j < elements.length; j++) {
            const e = elements[j];
            const rowid = e.id;
            displayrowids.push(rowid);
        }
    }
    //make array unique
    displayrowids = uniqueArray(displayrowids);
    displayrowids = sortRowId(displayrowids);
    //oscarLog('sort and unique displaywords='+displayrowids);

    const numOfRows = displayrowids.length;
    //oscarLog(numOfRows);
    current_numberofpages = Math.ceil(numOfRows / number_of_row_per_page);
    //oscarLog(current_numberofpages);
    const startIndex = (parseInt(page) - 1) * number_of_row_per_page;
    let endIndex = startIndex + (number_of_row_per_page - 1);
    if (endIndex > displayrowids.length - 1) {
        endIndex = displayrowids.length - 1;
    }
    //set current displaying rows
    current_rows = [];
    for (let i = startIndex; i < endIndex + 1; i++) {
        if (document.getElementById(displayrowids[i])) {
            current_rows.push(displayrowids[i]);
        }
    }
    //loop through every thing,if it's in displayrowids, show it , if it's not hide it.
    for (let i = 0; i < total_rows.length; i++) {
        const rowid = total_rows[i];
        if (a_contain_b(current_rows, rowid)) {
            showElement(rowid);
        } else
            hideElement(rowid);
    }
}

function initializeNavigation() {
    const currentPageNumEl = document.getElementById('currentPageNum');
    if (currentPageNumEl) currentPageNumEl.textContent = 1;
    //update the page number shown and update previous and next words
    const msgNextEl = document.getElementById('msgNext');
    const msgPreviousEl = document.getElementById('msgPrevious');
    if (current_numberofpages > 1) {
        if (msgNextEl != null) showElement(msgNextEl);
        if (msgPreviousEl != null) hideElement(msgPreviousEl);
    } else if (current_numberofpages < 1) {
        if (msgNextEl != null) hideElement(msgNextEl);
        if (msgPreviousEl != null) hideElement(msgPreviousEl);
    } else if (current_numberofpages == 1) {
        if (msgNextEl != null) hideElement(msgNextEl);
        if (msgPreviousEl != null) hideElement(msgPreviousEl);
    }
    //oscarLog("current_numberofpages "+current_numberofpages);
    const currentIndividualPagesEl = document.getElementById('current_individual_pages');
    if (currentIndividualPagesEl != null) {
        currentIndividualPagesEl.textContent = "";
        if (current_numberofpages > 1) {
            for (let i = 1; i <= current_numberofpages; i++) {
                const link = document.createElement('a');
                link.style.textDecoration = 'none';
                link.href = 'javascript:void(0);';
                link.setAttribute('data-page', i);
                link.textContent = ' [ ' + i + ' ] ';
                link.addEventListener('click', function() {
                    navigatePage(parseInt(this.getAttribute('data-page')));
                });
                currentIndividualPagesEl.appendChild(link);
            }
        }
    }
}

function sortRowId(a) {
    const numArray = [];
    //sort array
    for (let i = 0; i < a.length; i++) {
        const id = a[i];
        const n = id.replace('row', '');
        numArray.push(parseInt(n));
    }
    numArray.sort(function (a, b) {
        return a - b;
    });
    a = [];
    for (let i = 0; i < numArray.length; i++) {
        a.push('row' + numArray[i]);
    }
    return a;
}

function a_contain_b(a, b) {//a is an array, b maybe an element in a.
    for (let i = 0; i < a.length; i++) {
        if (a[i] == b) {
            return true;
        }
    }
    return false;
}

function uniqueArray(a) {
    const r = [];
    o:for (let i = 0, n = a.length; i < n; i++) {
        for (let x = 0, y = r.length; x < y; x++) {
            if (r[x] == a[i]) continue o;
        }
        r[r.length] = a[i];
    }
    return r;
}

function navigatePage(p) {
    const currentPageNumEl = document.getElementById('currentPageNum');
    const pagenum = parseInt(currentPageNumEl ? currentPageNumEl.textContent : 1);
    if (p == 'Previous') {
        navigatePage(pagenum - 1);
    } else if (p == 'Next') {
        navigatePage(pagenum + 1);
    } else if (parseInt(p) > 0) {
        window.location.search = replaceQueryString(window.location.search, "page", parseInt(p));
    }
}

// TODO: Remove unused function.
function changeNavigationBar() {
    const currentPageNumEl = document.getElementById('currentPageNum');
    const pagenum = parseInt(currentPageNumEl ? currentPageNumEl.textContent : 1);
    const msgNextEl = document.getElementById('msgNext');
    const msgPreviousEl = document.getElementById('msgPrevious');
    if (current_numberofpages == 1) {
        hideElement(msgNextEl);
        hideElement(msgPreviousEl);
    } else if (current_numberofpages > 1 && current_numberofpages == pagenum) {
        hideElement(msgNextEl);
        showElement(msgPreviousEl);
    } else if (current_numberofpages > 1 && pagenum == 1) {
        showElement(msgNextEl);
        hideElement(msgPreviousEl);
    } else if (pagenum < current_numberofpages && pagenum > 1) {
        showElement(msgNextEl);
        showElement(msgPreviousEl);
    }
}

function syncCB(ele) {
    const id = ele.id;
    if (id == 'documentCB') {
        const cb2 = document.getElementById('documentCB2');
        if (cb2) cb2.checked = ele.checked == 1 ? 1 : 0;
    } else if (id == 'documentCB2') {
        const cb = document.getElementById('documentCB');
        if (cb) cb.checked = ele.checked == 1 ? 1 : 0;
    } else if (id == 'hl7CB') {
        const cb2 = document.getElementById('hl7CB2');
        if (cb2) cb2.checked = ele.checked == 1 ? 1 : 0;
    } else if (id == 'hl7CB2') {
        const cb = document.getElementById('hl7CB');
        if (cb) cb.checked = ele.checked == 1 ? 1 : 0;
    } else if (id == 'normalCB') {
        const cb2 = document.getElementById('normalCB2');
        if (cb2) cb2.checked = ele.checked == 1 ? 1 : 0;
    } else if (id == 'normalCB2') {
        const cb = document.getElementById('normalCB');
        if (cb) cb.checked = ele.checked == 1 ? 1 : 0;
    } else if (id == 'abnormalCB') {
        const cb2 = document.getElementById('abnormalCB2');
        if (cb2) cb2.checked = ele.checked == 1 ? 1 : 0;
    } else if (id == 'abnormalCB2') {
        const cb = document.getElementById('abnormalCB');
        if (cb) cb.checked = ele.checked == 1 ? 1 : 0;
    }
}

function showAb_Normal(ab_normal) {

    let ids = [];
    if (ab_normal == 'normal') {
        ids = normals;
    } else if (ab_normal == 'abnormal') {
        ids = abnormals;
    }
    let childId;
    if (ab_normal == 'normal') {
        childId = 'normals';
    } else if (ab_normal == 'abnormal') {
        childId = 'abnormals';
    }
    //oscarLog(childId);
    if (childId != null && childId.length > 0) {
        clearDocView();
        createNewElement('docViews', childId);
        for (let i = 0; i < ids.length; i++) {
            const docLabId = ids[i].replace(/\s/g, '');
            const ackStatus = getAckStatusFromDocLabId(docLabId);
            const patientId = getPatientIdFromDocLabId(docLabId);
            const patientName = getPatientNameFromPatientId(patientId);
            if (current_first_doclab == 0) current_first_doclab = docLabId;
            showDocLab(childId, docLabId, providerNo, searchProviderNo, ackStatus, patientName, ab_normal + 'show');
        }
    }
}

function showSubType(patientId, subType) {
    const labdocsArr = getLabDocFromPatientId(patientId);
    if (labdocsArr && labdocsArr != null) {
        const childId = 'subType' + subType + patientId;
        if (labdocsArr.length > 0) {
            //if(toggleElement(childId));
            // else{
            clearDocView();
            createNewElement('docViews', childId);
            for (let i = 0; i < labdocsArr.length; i++) {
                let labdoc = labdocsArr[i];
                labdoc = labdoc.replace(' ', '');
                //oscarLog('check type input='+labdoc);
                const type = checkType(labdoc);
                const ackStatus = getAckStatusFromDocLabId(labdoc);
                const patientName = getPatientNameFromPatientId(patientId);
                if (current_first_doclab == 0) current_first_doclab = labdoc;
                //oscarLog("type="+type+"--subType="+subType);
                if (type == subType)
                    showDocLab(childId, labdoc, providerNo, searchProviderNo, ackStatus, patientName, 'subtype' + subType + patientId + 'show');
                else ;
            }
            //toggleMarker('subtype'+subType+patientId+'show');
            //}
        }
    }
}

function getPatientNameFromPatientId(patientId) {
    const pn = patientIdNames[patientId];
    if (pn && pn != null) {
        return pn;
    } else {
        const url = contextpath + "/documentManager/ManageDocument.do";
        const data = 'method=getDemoNameAjax&demo_no=' + patientId;

        postForm(url, data)
            .then(response => response.json())
            .then(json => {
                if (json != null) {
                    const pn = json.demoName;//get name from id
                    addPatientIdName(patientId, pn);
                    addPatientId(patientId);
                    return pn;
                }
            })
            .catch(error => console.error('Error:', error));
    }
}

function getAckStatusFromDocLabId(docLabId) {
    return docStatus[docLabId];
}

function showAllDocLabs() {

    clearDocView();
    for (let i = 0; i < patientIds.length; i++) {
        const id = patientIds[i];
        //oscarLog("ids in showalldoclabs="+id);
        if (id.length > 0) {
            showThisPatientDocs(id, true);

        }
    }

}

function showCategory(cat) {
    if (cat.length > 0) {
        const sA = getDocLabFromCat(cat);
        if (sA && sA.length > 0) {
            //oscarLog("sA="+sA);
            const childId = "category" + cat;
            //if(toggleElement(childId));
            // else{
            clearDocView();
            createNewElement('docViews', childId);
            for (let i = 0; i < sA.length; i++) {
                let docLabId = sA[i];
                docLabId = docLabId.replace(/\s/g, "");
                //oscarLog("docLabId="+docLabId);
                const patientId = getPatientIdFromDocLabId(docLabId);
                //oscarLog("patientId="+patientId);
                const patientName = getPatientNameFromPatientId(patientId);
                const ackStatus = getAckStatusFromDocLabId(docLabId);
                //oscarLog("patientName="+patientName);
                //oscarLog("ackStatus="+ackStatus);

                if (patientName != null) {
                    if (current_first_doclab == 0) current_first_doclab = docLabId;
                    showDocLab(childId, docLabId, providerNo, searchProviderNo, ackStatus, patientName, cat + 'show');
                }
            }

        }
    }
}

function getPatientIdFromDocLabId(docLabId) {
    //console.log('in getpatientidfromdoclabid='+docLabId);
    //console.log(patientIds);
    //console.log(patientDocs);
    const notUsedPid = [];
    for (let i = 0; i < patientIds.length; i++) {

        const pid = patientIds[i];
        const e = patientDocs[pid];
        //console.log('e'+e);
        if (!e) {
            //console.log('if');
            notUsedPid.push(pid);
        } else {
            //console.log('in else='+docLabId);
            if (e.indexOf(docLabId) > -1) {
                return pid;
            }
        }
    }
    //console.log(notUsedPid);
    for (let i = 0; i < notUsedPid.length; i++) {

        removePatientId(notUsedPid[i]);
    }
}

function getLabDocFromPatientId(patientId) {//return array of doc ids and lab ids from patient id.
    //console.log(patientId+"--");
    //console.log(patientDocs);
    return patientDocs[patientId];
}

function showThisPatientDocs(patientId, keepPrevious) {
    //oscarLog("patientId in show this patientdocs="+patientId);
    const labDocsArr = getLabDocFromPatientId(patientId);
    const patientName = getPatientNameFromPatientId(patientId);
    if (patientName != null && patientName.length > 0 && labDocsArr != null && labDocsArr.length > 0) {
        //oscarLog(patientName);
        const childId = 'patient' + patientId;
        //if(toggleElement(childId));
        //else{
        if (keepPrevious) ;
        else clearDocView();
        createNewElement('docViews', childId);
        for (let i = 0; i < labDocsArr.length; i++) {
            const docId = labDocsArr[i].replace(' ', '');
            if (current_first_doclab == 0) current_first_doclab = docId;
            const ackStatus = getAckStatusFromDocLabId(docId);
            //oscarLog('childId='+childId+',docId='+docId+',ackStatus='+ackStatus);
            showDocLab(childId, docId, providerNo, searchProviderNo, ackStatus, patientName, 'labdoc' + patientId + 'show');
        }
    }
}

function popupConsultation(segmentId) {
    const page = contextpath + '/oscarEncounter/ViewRequest.do?segmentId=' + segmentId;
    const windowprops = "height=960,width=700,location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
    const popup = window.open(page, msgConsReq, windowprops);
    if (popup != null) {
        if (popup.opener == null) {
            popup.opener = self;
        }
    }
}

function checkType(docNo) {
    return docType[docNo];
}

function ForwardSelectedRows(files, searchProviderNo, status) {
    const isListView = jQuery("input[name=isListView]").val();
    const url = ctx + "/oscarMDS/SelectProvider.jsp";

    // not sure why this is a parameter, but, just in case...
    const data = {
        "isListView": isListView,
        "forwardList": files + ""
    };
    let dialogContainer = jQuery("#dialog");
    if (!dialogContainer.length) {
        dialogContainer = drawDialogContainer();
    }
    const dialog = dialogContainer.load(url, data).dialog({
        modal: true,
        width: 685,
        height: 355,
        draggable: false,
        title: "Forward Documents",
        buttons: {
            "Forward": function () {
                // workaround for JQuery bug with multiselect items that are not "selected"
                const fwdProviders = jQuery(this).find("select[multiple]#fwdProviders option").map(function (i, e) {
                    return jQuery(e).val();
                }).toArray();

                const fwdFavorites = jQuery(this).find("select[multiple]#favorites option").map(function (i, e) {
                    return jQuery(e).val();
                }).toArray();

                if (fwdProviders.length === 0) {
                    jQuery(this).find("select[multiple]#fwdProviders").addClass("input-error");
                    return;
                }

                forwardLabs(files, fwdProviders, fwdFavorites);
            },
            Cancel: function () {
                jQuery(this).dialog("close");
            }
        },
		open: function() {
			// Applies Bootstrap 5 card styles if Bootstrap is included; otherwise, it will render as a normal jQuery dialog box.
			styleDialogAsCard();
		},
        close: function () {
            jQuery(this).find("select[multiple]#fwdProviders").val('');
            jQuery(this).find("select[multiple]#fwdFavorites").val('');
        }
    }).dialog("open");
}

function drawDialogContainer() {
    const $div = jQuery('<div />').appendTo('body');
    $div.attr('id', 'dialog');
    return $div;
}

function styleDialogAsCard() {
	// Add Bootstrap card classes
	jQuery(".ui-dialog").addClass("card shadow-lg border-0 rounded");
	jQuery(".ui-dialog-titlebar").addClass("card-header bg-transparent mx-2 mt-3 border-0");
	jQuery(".ui-dialog-title").addClass("h5 mb-0 ms-2");
	jQuery(".ui-dialog-titlebar-close").addClass("btn-close");

	// Style the content area as card body
	jQuery(".ui-dialog-content").addClass("card-body mx-3 mt-3");

	// Style buttons like card footer
	jQuery(".ui-dialog-buttonpane").addClass("card-footer bg-light border-top-0 d-flex justify-content-end rounded");
	jQuery(".ui-dialog-buttonset button").addClass("btn btn-primary btn-sm");

	// Change 'Cancel' button to a secondary button
	jQuery(".ui-dialog-buttonset button:contains('Cancel')").removeClass("btn-primary").addClass("btn-secondary btn-sm");
}

function forwardLabs(files, providers, favorites) {
    const url = ctx + "/oscarMDS/ReportReassign.do";

        const filesArray = Array.isArray(files) ? files : [files];

        const filelabs = {
            "flaggedLabs": JSON.stringify({ "files": filesArray }),
            "selectedProviders": JSON.stringify({ "providers": providers }),
            "selectedFavorites": JSON.stringify({ "favorites": favorites }),
            "searchProviderNo": jQuery("input[name='searchProviderNo']").val(),
            "ajax": "yes"
        };
        bulkInboxAction(url, filelabs);
}

function updateDocLabData(doclabid) {//remove doclabid from global variables
    const doclabidNum = doclabid
    if (checkType(doclabid + "d") == "DOC") {
        doclabid += "d";
    }
    doclabid = doclabid.replace(/\s/g, '');
    updateSideNav(doclabid);
    hideRowUsingId(doclabidNum);
    removeIdFromTypeDocLab(doclabid);
    removeIdFromDocType(doclabid);
    removeIdFromPatientDocs(doclabid);
    removeEmptyPairFromPatientDocs();
    removeIdFromDocStatus(doclabid);
    removeNormal(doclabid);
    removeAbnormal(doclabid);
}

function checkAb_normal(doclabid) {
    if (normals.indexOf(doclabid) != -1)
        return 'normal';
    else if (abnormals.indexOf(doclabid) != -1)
        return 'abnormal';
}

function updateSideNav(doclabid) {
    //oscarLog('in updatesidenav');
    const totalNumDocsEl = document.getElementById('totalNumDocs');
    let n = totalNumDocsEl ? totalNumDocsEl.textContent : '0';
    n = parseInt(n);
    if (n > 0) {
        n = n - 1;
        if (totalNumDocsEl) totalNumDocsEl.textContent = n;
    }
    const type = checkType(doclabid);
    //oscarLog('type='+type);
    if (type == 'DOC') {
        const totalDocsNumEl = document.getElementById('totalDocsNum');
        n = totalDocsNumEl ? totalDocsNumEl.textContent : '0';
        //oscarLog('n='+n);
        n = parseInt(n);
        if (n > 0) {
            n = n - 1;
            if (totalDocsNumEl) totalDocsNumEl.textContent = n;
        }
    } else if (type == 'HL7') {
        const totalHL7NumEl = document.getElementById('totalHL7Num');
        n = totalHL7NumEl ? totalHL7NumEl.textContent : '0';
        n = parseInt(n);
        if (n > 0) {
            n = n - 1;
            if (totalHL7NumEl) totalHL7NumEl.textContent = n;
        }
    }
    const ab_normal = checkAb_normal(doclabid);
    //oscarLog('normal or abnormal?'+ab_normal);
    if (ab_normal == 'normal') {
        const normalNumEl = document.getElementById('normalNum');
        n = normalNumEl ? normalNumEl.textContent : '0';
        //oscarLog('normal inner='+n);
        n = parseInt(n);
        if (n > 0) {
            n = n - 1;
            if (normalNumEl) normalNumEl.textContent = n;
        }
    } else if (ab_normal == 'abnormal') {
        const abnormalNumEl = document.getElementById('abnormalNum');
        n = abnormalNumEl ? abnormalNumEl.textContent : '0';
        n = parseInt(n);
        if (n > 0) {
            n = n - 1;
            if (abnormalNumEl) abnormalNumEl.textContent = n;
        }
    }

    //update patient and patient's subtype
    const patientId = getPatientIdFromDocLabId(doclabid);
    //oscarLog('xx '+patientId+'--'+n);
    const patientNumDocsEl = document.getElementById('patientNumDocs' + patientId);
    n = patientNumDocsEl ? patientNumDocsEl.textContent : '0';
    //oscarLog('xx xx '+patientId+'--'+n);
    n = parseInt(n);
    if (n > 0) {
        if (patientNumDocsEl) patientNumDocsEl.textContent = n - 1;
    }

    if (type == 'DOC') {
        const pDocNumEl = document.getElementById('pDocNum_' + patientId);
        n = pDocNumEl ? pDocNumEl.textContent : '0';
        n = parseInt(n);
        if (n > 0) {
            if (pDocNumEl) pDocNumEl.textContent = n - 1;
        }
    } else if (type == 'HL7') {
        const pLabNumEl = document.getElementById('pLabNum_' + patientId);
        n = pLabNumEl ? pLabNumEl.textContent : '0';
        n = parseInt(n);
        if (n > 0) {
            if (pLabNumEl) pLabNumEl.textContent = n - 1;
        }
    }
}

function getRowIdFromDocLabId(doclabid) {
    let rowid;
    for (let i = 0; i < doclabid_seq.length; i++) {
        if (doclabid == doclabid_seq[i]) {
            rowid = 'row' + i;
            break;
        }
    }
    return rowid;
}

function hideRowUsingId(doclabid) {
    if (doclabid != null) {
        let rowid;
        doclabid = doclabid.replace(' ', '');
        rowid = getRowIdFromDocLabId(doclabid);
        const rowEl = document.getElementById(rowid);
        if (rowEl) rowEl.remove();
    }
}

function resetCurrentFirstDocLab() {
    current_first_doclab = 0;
}

function focusFirstDocLab() {
    if (current_first_doclab > 0) {
        const doc_lab = checkType(current_first_doclab);
        if (doc_lab == 'DOC') {
            //oscarLog('docDesc_'+current_first_doclab);
            const docDescEl = document.getElementById('docDesc_' + current_first_doclab);
            if (docDescEl) docDescEl.focus();
        } else if (doc_lab == 'HL7') {
            //do nothing
        }
    }
}

/***methos for showDocument.jsp***/
function updateGlobalDataAndSideNav(doclabid, patientId) {
    doclabid = doclabid.replace(/\s/g, '');

    if (doclabid.length > 0 && typeof patientDocs !== 'undefined') {
        //delete doclabid from not assigned list
        const na = patientDocs['-1'];
        const index = na.indexOf(doclabid);
        if (index !== -1) {
            na.splice(index, 1);
            addIdToPatient(doclabid, patientId);//add to patient
        }
        return true;
    }
}

function updatePatientDocLabNav(num, patientId) {
    //oscarLog(num+';;'+patientId);
    if (num && patientId) {
        let changed = false;
        const type = checkType(num);
        //oscarLog($('patient'+patientId+'all'));
        if (document.getElementById('patient' + patientId + 'all')) {
            //oscarLog('if');
            //case 1,patientName exists
            //check the type of doclab,
            //check if the type is present, if yes, increase by 1; if not, create and set to 1.

            if (type == 'DOC') {
                if (document.getElementById('patient' + patientId + 'docs')) {
                    increaseCount('pDocNum_' + patientId);
                    changed = true;
                } else {
                    const newEle = createNewDocEle(patientId);
                    //oscarLog($('labdoc'+patientId+'showSublist'));
                    const sublistEl = document.getElementById('labdoc' + patientId + 'showSublist');
                    if (sublistEl) sublistEl.insertAdjacentHTML('beforeend', newEle);
                    changed = true;
                }
            } else if (type == 'HL7') {
                if (document.getElementById('patient' + patientId + 'hl7s')) {
                    increaseCount('pLabNum_' + patientId);
                    changed = true;
                } else {
                    const newEle = createNewHL7Ele(patientId);
                    const sublistEl = document.getElementById('labdoc' + patientId + 'showSublist');
                    if (sublistEl) sublistEl.insertAdjacentHTML('beforeend', newEle);
                    changed = true;
                }
            }
            if (changed) {
                increaseCount('patientNumDocs' + patientId);
            }
        } else {
            //oscarLog('else');
            //case 2, patientname doesn't exists in nav bar at all
            //create patientname, check if labdoc is a lab or a doc.
            //create lab/doc nav
            const ele = createPatientDocLabEle(patientId, num);
            changed = true;
        }
        if (changed) {//decrease Not,Assigned by 1
            decreaseCount('patientNumDocs-1');
            if (type == 'DOC') {
                decreaseCount('pDocNum_-1');
            } else if (type == 'HL7') {
                decreaseCount('pLabNum_-1');
            }
            return true;
        }
    }
}

function createPatientDocLabEle(patientId, doclabid) {
    const url = ctx + "/documentManager/ManageDocument.do";
    const data = 'method=getDemoNameAjax&demo_no=' + patientId;

    postForm(url, data)
        .then(response => response.json())
        .then(json => {
        //oscarLog(json);
        if (json != null) {
            const patientName = json.demoName;//get name from id
            addPatientId(patientId);
            addPatientIdName(patientId, patientName);
            let e = '<dt><img id="plus' + patientId + '" alt="plus" src="' + ctx + '/images/plus.png" onclick="showhideSubCat(\'plus\',\'' + patientId + '\');"/><img id="minus' + patientId + '" alt="minus" style="display:none;" src="' + ctx + '/images/minus.png" onclick="showhideSubCat(\'minus\',\'' + patientId + '\');"/>' +
            '<a id="patient' + patientId + 'all" href="javascript:void(0);" onclick="resetCurrentFirstDocLab();showThisPatientDocs(\'' + patientId + '\');un_bold(this);" title="' + patientName + '">' + patientName + ' (<span id="patientNumDocs' + patientId + '">1</span>)</a>' +
            '<dl id="labdoc' + patientId + 'showSublist" style="display:none">';
            const type = checkType(doclabid);
            let s;
            //oscarLog('type='+type);
            //oscarLog('eee='+e);
            if (type == 'DOC') {
                s = createNewDocEle(patientId);
            } else if (type == 'HL7') {
                s = createNewHL7Ele(patientId);
            } else {
                return '';
            }
            e += s;
            e += '</dl></dt>';
            //oscarLog('jjjjje='+e);
            //oscarLog('before return e');
            const patientsdoclabsEl = document.getElementById('patientsdoclabs');
            if (patientsdoclabsEl) patientsdoclabsEl.insertAdjacentHTML('beforeend', e);
            return e;
        }
    })
    .catch(error => console.error('Error:', error));

}

function createNewDocEle(patientId) {
    const newEle = '<dt><a id="patient' + patientId + 'docs" href="javascript:void(0);" onclick="resetCurrentFirstDocLab();showSubType(\'' + patientId + '\',\'DOC\');un_bold(this);" title="Documents">Documents(<span id="pDocNum_' + patientId + '">1</span>)</a></dt>';
    //oscarLog('newEle='+newEle);
    return newEle;
}

function createNewHL7Ele(patientId) {
    const newEle = '<dt><a id="patient' + patientId + 'hl7s" href="javascript:void(0);" onclick="resetCurrentFirstDocLab();showSubType(\'' + patientId + '\',\'HL7\');un_bold(this);" title="HL7s">HL7s(<span id="pLabNum_' + patientId + '">1</span>)</a></dt>';
    //oscarLog('newEle='+newEle);
    return newEle;
}

function increaseCount(eleId) {
    const el = document.getElementById(eleId);
    if (el) {
        let n = el.textContent;
        if (n.length > 0) {
            n = parseInt(n);
            n++;
            el.textContent = n;
        }
    }
}

function decreaseCount(eleId) {
    const el = document.getElementById(eleId);
    if (el) {
        let n = el.textContent;
        if (n.length > 0) {
            n = parseInt(n);
            if (n > 0) {
                n--;
            } else {
                n = 0;
            }
            el.textContent = n;
        }
    }
}

function updateDocumentAndNext(eleId) {//save doc info
    const url = "../documentManager/ManageDocument.do"
    const formEl = document.getElementById(eleId);
    const data = serializeForm(formEl);

    postForm(url, data)
        .then(response => response.json())
        .then(json => {
        if (json != null) {
            const patientId = json.patientId;

            const ar = eleId.split("_");
            const num = ar[1].replace(/\s/g, '');

            showElement("saveSucessMsg_" + num);
            const savedEl = document.getElementById('saved' + num);
            if (savedEl) savedEl.value = 'true';

            const msgBtnEl = document.getElementById("msgBtn_" + num);
            if (msgBtnEl) {
                msgBtnEl.onclick = function () {
                    popup(700, 960, contextpath + '/messenger/SendDemoMessage.do?demographic_no=' + patientId, 'msg');
                };
            }

            updateDocStatusInQueue(num);

            if (typeof _in_window !== 'undefined' && _in_window) {
                if (typeof self.opener.removeReport !== 'undefined') {
                    self.opener.removeReport(num);
                }
                window.close();
            } else {
                //Hide document with slide up animation
                jQuery('#labdoc_' + num).slideUp();
                const success = updateGlobalDataAndSideNav(num, patientId);
                if (success) {
                    const innerSuccess = updatePatientDocLabNav(num, patientId);
                    if (innerSuccess) {
                        //disable demo input
                        const autocompletedemoEl = document.getElementById('autocompletedemo' + num);
                        if (autocompletedemoEl) autocompletedemoEl.disabled = true;
                    }
                }
            }
        }
    })
    .catch(error => console.error('Error:', error));

    return false;
}

function updateDocument(eleId) {
    if (!checkObservationDate(eleId)) {
        return false;
    }

    //save doc info
    const url = "../documentManager/ManageDocument.do";
    const formEl = document.getElementById(eleId);
    const data = serializeForm(formEl);

    postForm(url, data)
        .then(response => response.text())
        .then(responseText => {
        const ar = eleId.split("_");
        const num = ar[1].replace(/\s/g, '');

        const msg = document.getElementById("saveSucessMsg_" + num);
        if (msg) msg.style.display = "inline";

        const savedField = document.getElementById("saved" + num);
        if (savedField) savedField.value = "true";

        let success = false;
        let patientId = null;

        try {
            const json = JSON.parse(responseText);
            if (json && json.patientId) {
                patientId = json.patientId;

                const msgBtn = document.getElementById("msgBtn_" + num);
                if (msgBtn) {
                    msgBtn.onclick = function () {
                        popup(700, 960, contextpath + '/messenger/SendDemoMessage.do?demographic_no=' + patientId, 'msg');
                    };
                }

                if (typeof _in_window !== 'undefined' && _in_window) {
                    if (typeof self.opener.removeReport !== 'undefined') {
                        self.opener.removeReport(num);
                        success = true;
                    }
                } else {
                    success = updateGlobalDataAndSideNav(num, patientId);
                }

                if (success) {
                    const updateSuccess = updatePatientDocLabNav(num, patientId);
                    if (updateSuccess) {
                        const ac = document.getElementById("autocompletedemo" + num);
                        if (ac) ac.disabled = true;
                    }
                }
            }
        } catch (e) {
            console.warn("Not JSON");
        }
    })
    .catch(error => console.error("Save failed:", error));

    return false;
}

function checkObservationDate(formid) {
    // regular expression to match required date format
    const re = /^\d{4}\-\d{1,2}\-\d{1,2}$/;
    const re2 = /^\d{4}\/\d{1,2}\/\d{1,2}$/;

    const form = document.getElementById(formid);
    if (form.elements["observationDate"].value == "") {
        alert("Blank Date: " + form.elements["observationDate"].value);
        form.elements["observationDate"].focus();
        return false;
    }

    if (!form.elements["observationDate"].value.match(re)) {
        if (!form.elements["observationDate"].value.match(re2)) {
            alert("Invalid date format: " + form.elements["observationDate"].value);
            form.elements["observationDate"].focus();
            return false;
        } else if (form.elements["observationDate"].value.match(re2)) {
            form.elements["observationDate"].value = form.elements["observationDate"].value.replace("/", "-");
            form.elements["observationDate"].value = form.elements["observationDate"].value.replace("/", "-");
        }
    }
    const regs = form.elements["observationDate"].value.split("-");
    // day value between 1 and 31
    if (regs[2] < 1 || regs[2] > 31) {
        alert("Invalid value for day: " + regs[2]);
        form.elements["observationDate"].focus();
        return false;
    }
    // month value between 1 and 12
    if (regs[1] < 1 || regs[1] > 12) {
        alert("Invalid value for month: " + regs[1]);
        form.elements["observationDate"].focus();
        return false;
    }
    // year value between 1902 and 2015
    if (regs[0] < 1902 || regs[0] > (new Date()).getFullYear()) {
        alert("Invalid value for year: " + regs[0] + " - must be between 1902 and " + (new Date()).getFullYear());
        form.elements["observationDate"].focus();
        return false;
    }
    return true;
}

function updateStatus(formid) {//acknowledge
    const num = formid.split("_");
    const doclabid = num[1];
    if (doclabid) {

        let demoId = "0";
        let saved = true
        if (jQuery('#demofind' + doclabid).length) {
            demoId = jQuery('#demofind' + doclabid).val();
        }
        if (jQuery('#saved' + doclabid).length) {
            saved = jQuery('#saved' + doclabid).val();
        }
        console.log("Update status for demoid: " + demoId + " doclabid: " + doclabid);
        console.log("Previously saved: " + saved);

        if (demoId === '-1' || !saved) {
            alert('Document is not assigned and saved to a patient,please file it');
        } else {
            const url = contextpath + "/oscarMDS/UpdateStatus.do";
            const formEl = document.getElementById(formid);
            const data = serializeFormToObject(formEl);
            console.log("Updating status. URL: " + url);
            console.log(data);

            jQuery.post(url, data).success(function () {
                updateDocStatusInQueue(doclabid);
				if (window.frameElement) {
					// Hide the parent <div> of the iframe only for new inbox previews loaded in an iframe
					jQuery(window.frameElement).closest('.document-card.card').slideUp();
				} else if (typeof _in_window !== 'undefined' && _in_window) {
                    if (typeof self.opener.removeReport !== 'undefined') {
						/**
						 * When a user acknowledges any lab version, it automatically files away older versions
						 * as well as the acknowledged version. This function removes those versions from the
						 * inbox results by calling the jQuery `removeReport` function on IDs up to and including
						 * the doclabid.
						 */
						const multiIds = data.multiID.split(",");
						for (const id of multiIds) {
							self.opener.removeReport(id);
							if (id === doclabid) break;
						}
                    }
                    window.close();
                } else {
                    //Hide document
                    jQuery('#labdoc_' + doclabid).slideUp();
                    updateGlobalDataAndSideNav(doclabid, null);
                }
            })
        }
    }
}

function fileDoc(docId) {
    if (docId) {
        docId = docId.replace(/\s/, '');
        if (docId.length > 0) {
            const demofindEl = document.getElementById('demofind' + docId);
            const demoId = demofindEl ? demofindEl.value : '-1';
            let isFile = true;
            if (demoId == '-1') {
                isFile = confirm('Document is not assigned to any patient, do you still want to file it?');
            }
            if (isFile) {
                const type = 'DOC';
                if (type) {
                    const url = '../oscarMDS/FileLabs.do';
                    const data = 'method=fileLabAjax&flaggedLabId=' + docId + '&labType=' + type;

                    postForm(url, data)
                        .then(response => response.text())
                        .then(responseText => {
                            updateDocStatusInQueue(docId);

                            if (typeof _in_window !== 'undefined' && _in_window) {
                                if (typeof self.opener.removeReport !== 'undefined') {
                                    self.opener.removeReport(docId);
                                }

                                window.close();
                            } else {
                                // Slide up animation using jQuery
                                jQuery('#labdoc_' + docId).slideUp();
                            }
                        })
                        .catch(error => console.error('Error:', error));
                }
            }
        }
    }
}

function handleQueueListChange(queueListSelectElement, refileBtnElement, docCurrentFiledQueue) {
	refileBtnElement.disabled = queueListSelectElement.value === docCurrentFiledQueue;
}

function refileDoc(id) {
    const queueListEl = document.getElementById('queueList_' + id);
    const queueId = queueListEl.options[queueListEl.selectedIndex].value;
    const url = contextpath + "/documentManager/ManageDocument.do";
    const data = 'method=refileDocumentAjax&documentId=' + id + "&queueId=" + queueId;

    postForm(url, data)
        .then(response => response.text())
        .then(responseText => {
            fileDoc(id);
        })
        .catch(error => console.error('Error:', error));
}

function addDocToList(provNo, provName, docId) {
    const bdoc = document.createElement('a');
    bdoc.setAttribute("onclick", "removeProv(this);");
    bdoc.setAttribute("style", "cursor: pointer;");
    bdoc.appendChild(document.createTextNode(" -remove- "));
    //oscarLog("--");
    const adoc = document.createElement('div');
    adoc.appendChild(document.createTextNode(provName));
    //oscarLog("--==");
    const idoc = document.createElement('input');
    idoc.setAttribute("type", "hidden");
    idoc.setAttribute("name", "flagproviders");
    idoc.setAttribute("value", provNo);

    adoc.appendChild(idoc);

    adoc.appendChild(bdoc);
    const providerList = document.getElementById('providerList' + docId);
    if (providerList) providerList.appendChild(adoc);
}

function removeLink(docType, docId, providerNo, e) {
    const url = "../documentManager/ManageDocument.do";
    const data = 'method=removeLinkFromDocument&docType=' + docType + '&docId=' + docId + '&providerNo=' + providerNo;

    postForm(url, data)
    .then(response => response.text())
    .then(responseText => {
        updateDocLabData(docId);
    })
    .catch(error => console.error('Error:', error));

    e.parentNode.remove(e);
}

function replaceQueryString(url, param, value) {
    const re = new RegExp("([?|&])" + param + "=.*?(&|$)", "i");
    if (url.match(re))
        return url.replace(re, '$1' + param + "=" + value + '$2');
    else
        return url + '&' + param + "=" + value;
}

const CATEGORY_ALL = 1,
    CATEGORY_DOCUMENTS = 2,
    CATEGORY_HL7 = 3,
    CATEGORY_NORMAL = 4,
    CATEGORY_ABNORMAL = 5,
    CATEGORY_PATIENT = 6,
    CATEGORY_PATIENT_SUB = 7,
    CATEGORY_TYPE_DOC = 'DOC',
    CATEGORY_TYPE_HL7 = 'HL7';

function reloadChangeView() {
    resetCurrentFirstDocLab();

    switch (selected_category) {
        case CATEGORY_ALL:
            showAllDocLabs();
            un_bold(document.getElementById('totalAll'))
            break;
        case CATEGORY_DOCUMENTS:
            showCategory('DOC');
            un_bold(document.getElementById('totalDocs'));
            break;
        case CATEGORY_HL7:
            showCategory('HL7');
            un_bold(document.getElementById('totalHL7s'));
            break;
        case CATEGORY_NORMAL:
            showAb_Normal('normal');
            un_bold(document.getElementById('totalNormals'));
            break;
        case CATEGORY_ABNORMAL:
            showAb_Normal('abnormal');
            un_bold(document.getElementById('totalAbnormals'));
            break;
        case CATEGORY_PATIENT:
            showThisPatientDocs(selected_category_patient);
            un_bold(document.getElementById('patient' + selected_category_patient + 'all'));
            break;
        case CATEGORY_PATIENT_SUB:
            showSubType(selected_category_patient, selected_category_type);
            showhideSubCat('plus', selected_category_patient);
            switch (selected_category_type) {
                case CATEGORY_TYPE_DOC:
                    un_bold(document.getElementById('patient' + selected_category_patient + 'docs'));
                    break;
                case CATEGORY_TYPE_HL7:
                    un_bold(document.getElementById('patient' + selected_category_patient + 'hl7s'));
                    break;
            }
            break;
    }
}

function inSummaryView() {
    const summaryViewEl = document.getElementById('summaryView');
    if (!summaryViewEl) return false;
    const computedDisplay = window.getComputedStyle(summaryViewEl).display;
    return computedDisplay !== 'none';
}

function refreshView() {
    if (inSummaryView()) {
        location.reload();
    } else {
        const cat = selected_category;
        const patId = selected_category_patient;
        const catType = selected_category_type;
        const preview = inSummaryView() ? "0" : "1";
        let search = location.search;
        search = replaceQueryString(search, "selectedCategory", cat);
        search = replaceQueryString(search, "selectedCategoryPatient", patId);
        search = replaceQueryString(search, "selectedCategoryType", catType);
        search = replaceQueryString(search, "inPreview", preview);
        location.search = search;
    }
}

function getWidth() {
    let myWidth = 0;
    if (typeof (window.innerWidth) == 'number') {
        //Non-IE
        myWidth = window.innerWidth;
    } else if (document.documentElement && document.documentElement.clientWidth) {
        //IE 6+ in 'standards compliant mode'
        myWidth = document.documentElement.clientWidth;
    } else if (document.body && document.body.clientHeight) {
        //IE 4 compatible
        myWidth = document.body.clientWidth;
    }
    return myWidth;
}


function getHeight() {
    let myHeight = 0;
    if (typeof (window.innerHeight) == 'number') {
        //Non-IE
        myHeight = window.innerHeight;
    } else if (document.documentElement && document.documentElement.clientHeight) {
        //IE 6+ in 'standards compliant mode'
        myHeight = document.documentElement.clientHeight;
    } else if (document.body && (document.body.clientHeight)) {
        //IE 4 compatible
        myHeight = document.body.clientHeight;
    }
    return myHeight;
}

function showPDF(docid, cp) {

    // var height=700;
    // if(getHeight()>750) {
    //     height=getHeight()-50;
    // }

    // var width=700;
    // if(getWidth()>1350)
    // {
    //     width=getWidth()-650;
    // }

    const url = cp + '/documentManager/ManageDocument.do?method=display&doc_no=' + encodeURIComponent(docid) + '&rand=' + Math.random() + '#view=fitV&page=1';

    const container = document.getElementById('docDispPDF_' + docid);
    if (container) {
        container.textContent = '';
        const pdfObject = document.createElement('object');
        pdfObject.style.width = '100%';
        pdfObject.style.height = '92vh';
        pdfObject.type = 'application/pdf';
        pdfObject.data = url;
        pdfObject.id = 'docPDF_' + docid;
        container.appendChild(pdfObject);
    }
}

function showPageImg(docid, pn, cp) {
    const displayDocumentAsEl = document.getElementById('displayDocumentAs_' + docid);
    const displayDocumentAs = displayDocumentAsEl ? displayDocumentAsEl.value : '';
    if (displayDocumentAs == "PDF") {
        showPDF(docid, cp);
    } else if (docid && pn && cp) {
        const e = document.getElementById('docImg_' + docid);
        const url = cp + '/documentManager/ManageDocument.do?method=viewDocPage&doc_no=' + docid + '&curPage=' + pn;
        if (e) e.setAttribute('src', url);
    }
}

function nextPage(docid, cp) {
    const curPageEl = document.getElementById('curPage_' + docid);
    const totalPageEl = document.getElementById('totalPage_' + docid);
    let curPage = curPageEl ? parseInt(curPageEl.value) : 1;
    const totalPage = totalPageEl ? parseInt(totalPageEl.value) : 1;
    curPage++;
    if (curPage > totalPage) {
        curPage = totalPage;
        hideNext(docid);
        showPrev(docid);
    }
    if (curPageEl) curPageEl.value = curPage;
    const viewedPageEl = document.getElementById('viewedPage_' + docid);
    if (viewedPageEl) viewedPageEl.textContent = curPage;

    showPageImg(docid, curPage, cp);
    if (curPage + 1 > totalPage) {
        hideNext(docid);
        showPrev(docid);
    } else {
        showNext(docid);
        showPrev(docid);
    }
}

function prevPage(docid, cp) {
    const curPageEl = document.getElementById('curPage_' + docid);
    let curPage = curPageEl ? parseInt(curPageEl.value) : 1;
    curPage--;
    if (curPage < 1) {
        curPage = 1;
        hidePrev(docid);
        showNext(docid);
    }
    if (curPageEl) curPageEl.value = curPage;
    const viewedPageEl = document.getElementById('viewedPage_' + docid);
    if (viewedPageEl) viewedPageEl.textContent = curPage;

    showPageImg(docid, curPage, cp);
    if (curPage == 1) {
        hidePrev(docid);
        showNext(docid);
    } else {
        showPrev(docid);
        showNext(docid);
    }

}

function firstPage(docid, cp) {
    const curPageEl = document.getElementById('curPage_' + docid);
    if (curPageEl) curPageEl.value = 1;
    const viewedPageEl = document.getElementById('viewedPage_' + docid);
    if (viewedPageEl) viewedPageEl.textContent = 1;
    showPageImg(docid, 1, cp);
    hidePrev(docid);
    showNext(docid);
}

function lastPage(docid, cp) {
    const totalPageEl = document.getElementById('totalPage_' + docid);
    const totalPage = totalPageEl ? parseInt(totalPageEl.value) : 1;

    const curPageEl = document.getElementById('curPage_' + docid);
    if (curPageEl) curPageEl.value = totalPage;
    const viewedPageEl = document.getElementById('viewedPage_' + docid);
    if (viewedPageEl) viewedPageEl.textContent = totalPage;
    showPageImg(docid, totalPage, cp);
    hideNext(docid);
    showPrev(docid);
}

function hidePrev(docid) {
    //disable previous link
    const prevP = document.getElementById("prevP_" + docid);
    const firstP = document.getElementById("firstP_" + docid);
    const prevP2 = document.getElementById("prevP2_" + docid);
    const firstP2 = document.getElementById("firstP2_" + docid);
    if (prevP) prevP.style.display = 'none';
    if (firstP) firstP.style.display = 'none';
    if (prevP2) prevP2.style.display = 'none';
    if (firstP2) firstP2.style.display = 'none';
}

function hideNext(docid) {
    //disable next link
    const nextP = document.getElementById("nextP_" + docid);
    const lastP = document.getElementById("lastP_" + docid);
    const nextP2 = document.getElementById("nextP2_" + docid);
    const lastP2 = document.getElementById("lastP2_" + docid);
    if (nextP) nextP.style.display = 'none';
    if (lastP) lastP.style.display = 'none';
    if (nextP2) nextP2.style.display = 'none';
    if (lastP2) lastP2.style.display = 'none';
}

function showPrev(docid) {
    //disable previous link
    const prevP = document.getElementById("prevP_" + docid);
    const firstP = document.getElementById("firstP_" + docid);
    const prevP2 = document.getElementById("prevP2_" + docid);
    const firstP2 = document.getElementById("firstP2_" + docid);
    if (prevP) prevP.style.display = 'inline';
    if (firstP) firstP.style.display = 'inline';
    if (prevP2) prevP2.style.display = 'inline';
    if (firstP2) firstP2.style.display = 'inline';
}

function showNext(docid) {
    //disable next link
    const nextP = document.getElementById("nextP_" + docid);
    const lastP = document.getElementById("lastP_" + docid);
    const nextP2 = document.getElementById("nextP2_" + docid);
    const lastP2 = document.getElementById("lastP2_" + docid);
    if (nextP) nextP.style.display = 'inline';
    if (lastP) lastP.style.display = 'inline';
    if (nextP2) nextP2.style.display = 'inline';
    if (lastP2) lastP2.style.display = 'inline';
}

function handleDocSave(docid, action) {
    const url = contextpath + "/documentManager/inboxManage.do";
    const data = 'method=isDocumentLinkedToDemographic&docId=' + docid;

    postForm(url, data)
    .then(response => response.json())
    .then(json => {
        if (json != null) {
            const success = json.isLinkedToDemographic;
            let demoid = '';

            if (success) {
                if (action == 'addTickler') {
                    demoid = json.demoId;
                    if (demoid != null && demoid.length > 0)
                        popupStart(450, 600, contextpath + '/tickler/ForwardDemographicTickler.do?docType=DOC&docId=' + docid + '&demographic_no=' + demoid, 'tickler')
                }
            } else {
                alert("Make sure demographic is linked and document changes saved!");
            }
        }
    })
    .catch(error => console.error('Error:', error));
}


function addDocComment(docId, providerNo) {

    let ret = true;
    let comment = "";
    const text = jQuery("#comment_" + docId + "_" + providerNo);
    if (text.length > 0) {
        comment = jQuery("#comment_" + docId + "_" + providerNo).html();
        if (comment == null || comment == "no comment") {
            comment = "";
        }
    }
    const commentVal = prompt("Please enter a comment (max. 255 characters)", comment);

    if (commentVal == null) {
        ret = false;
    } else if (commentVal != null && commentVal.length > 0)
        jQuery("#" + "comment_" + docId).val(commentVal);
    else
        jQuery("#" + "comment_" + docId).val(comment);

    if (ret) {
        const statusEl = document.getElementById("status_" + docId);
        if (statusEl) statusEl.value = 'N';
        const url = ctx + "/oscarMDS/UpdateStatus.do";
        const formid = "acknowledgeForm_" + docId;
        let data = serializeForm(formid);
        data += "&method=addComment";

        postForm(url, data)
        .then(response => response.json())
        .then(json => {
            if (json != null) {
                const date = json.date;
                const timestampEl = document.getElementById("timestamp_" + docId + "_" + providerNo);
                if (timestampEl) timestampEl.textContent = date;
            }
            const statusEl2 = document.getElementById("status_" + docId);
            if (statusEl2) statusEl2.value = "A";
            const commentDisplayEl = document.getElementById("comment_" + docId + "_" + providerNo);
            const commentInputEl = document.getElementById("comment_" + docId);
            if (commentDisplayEl && commentInputEl) {
                commentDisplayEl.textContent = commentInputEl.value;
                commentInputEl.value = "";
            }
        })
        .catch(error => console.error('Error:', error));
    }
}

function getDocComment(docId, providerNo, inQueueB) {

    let ret = true;
    let comment = "";
    const text = jQuery("#comment_" + docId + "_" + providerNo);
    if (text.length > 0) {
        comment = jQuery("#comment_" + docId + "_" + providerNo).html();
        if (comment == null || comment == "no comment") {
            comment = "";
        }
    }
    const commentVal = prompt("Please enter a comment (max. 255 characters)", comment);

    if (commentVal == null) {
        ret = false;
    } else if (commentVal != null && commentVal.length > 0)
        jQuery("#" + "comment_" + docId).val(commentVal);
    else
        jQuery("#" + "comment_" + docId).val(comment);

    if (ret) {
        updateStatus("acknowledgeForm_" + docId, inQueueB);
    }

}
