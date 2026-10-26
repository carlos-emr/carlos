-- Rich Text Letter attachment flow fix
--
-- The legacy RTL template still opens attachment UI through dead JSP paths and
-- reads fid/demographic_no from the page URL. After the first save, the RTL
-- page is rendered from efmshowform_data/addEForm and the live saved instance
-- is carried in hidden inputs instead. That left the Attach button opening:
--   /eform/attachEform.jsp?demo=&requestId=
--
-- This migration rewires the DB-stored RTL template to:
--   1. read demographicNo/fdid from hidden inputs when available
--   2. call the gated Struts routes instead of public JSP paths
--
UPDATE `eform`
SET `form_html` = REPLACE(
    REPLACE(
        REPLACE(
            REPLACE(
                `form_html`,
                'fid = gup("fid");',
                'fid = document.getElementById("fdid") ? document.getElementById("fdid").value : gup("fid");'
            ),
            'demographic_no= gup("demographic_no");',
            'demographic_no = document.getElementById("demographicNo") ? document.getElementById("demographicNo").value : gup("demographic_no");'
        ),
        '../eform/attachEform.jsp',
        '../eform/attachEform'
    ),
    '../eform/displayAttachedFiles.jsp',
    '../eform/displayAttachedFiles'
)
WHERE `form_name` = 'Rich Text Letter'
  AND `subject` LIKE 'Rich Text Letter Generator%';
