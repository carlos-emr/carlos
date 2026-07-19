// Creates a popup upload window to upload a profile picture with a set URL
function popupUploadPage(varpage, dn) {
    var page = varpage + "?demographicNo=" + dn;
    windowprops = "height=500,width=500,location=no," 
    + "scrollbars=no,menubars=no,toolbars=no,resizable=yes,top=50,left=50";
    var popup = window.open(page, "", windowprops);
    popup.focus();
}

// Sets a timer before reseting the client image back to the default, this is only done if a client image is set
function delay(time, resetUrl) {
    setTimeout(() => {
        document.getElementById('ci').src = resetUrl;
    }, time);
}