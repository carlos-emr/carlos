-- Rich Text Letter eForm v2.2 Modernization
-- Fixes broken library references, saveRTL() escaping bug, and SQL injection in fpreventions()
--
-- Changes:
--   a) Remove CDN jQuery 1.12.4 (host page injects jQuery 3.7.1)
--   b) Remove jQuery UI 1.8.18 reference (host page injects jQuery UI 1.14.2)
--   c) Replace font-awesome.min.css with fontawesome-all.min.css (FA6)
--   d) Remove jQuery UI colorPicker CSS and JS (not needed, color prompts use prompt())
--   e) Fix saveRTL() escaping bug (chain from myNewString, add & escaping)
--   f) Replace SQL injection in fpreventions() with safe server-side AJAX call
--   g) Update version marker to v2.2

-- Only apply if RTL eForm exists
DROP PROCEDURE IF EXISTS modernize_rtl_eform;
DELIMITER //
CREATE PROCEDURE modernize_rtl_eform()
BEGIN
    DECLARE row_count INT;

    SELECT COUNT(*) INTO row_count FROM eform
    WHERE form_name = 'Rich Text Letter'
      AND subject LIKE 'Rich Text Letter Generator%';

    IF row_count = 0 THEN
        SELECT 'RTL eForm not found, skipping modernization' AS info;
    ELSE
        -- (a) Remove CDN jQuery 1.12.4
        UPDATE eform SET form_html = REPLACE(form_html,
            '<script src=\"https://code.jquery.com/jquery-1.12.4.min.js\"   integrity=\"sha256-ZosEbRLbNQzLpnKIkEdrPv7lOy9C27hHQ+Xp8a4MxAQ=\" crossorigin=\"anonymous\"></script>',
            '<!-- jQuery 3.7.1 injected by host page -->')
        WHERE form_name = 'Rich Text Letter';

        -- (b) Remove jQuery UI 1.8.18
        UPDATE eform SET form_html = REPLACE(form_html,
            '<script src=\"../js/jquery-ui-1.8.18.custom.min.js\" type=\"text/javascript\"></script>',
            '<!-- jQuery UI 1.14.2 injected by host page -->')
        WHERE form_name = 'Rich Text Letter';

        -- (c) Replace FA3 CSS with FA6
        UPDATE eform SET form_html = REPLACE(form_html,
            '<link rel=\"stylesheet\" href=\"../css/font-awesome.min.css\">',
            '<link rel=\"stylesheet\" href=\"../css/fontawesome-all.min.css\">')
        WHERE form_name = 'Rich Text Letter';

        -- (d) Remove jQuery UI colorPicker CSS
        UPDATE eform SET form_html = REPLACE(form_html,
            '<link href=\"../css/jquery.ui.colorPicker.css\" rel=\"stylesheet\" type=\"text/css\" />',
            '<!-- colorPicker removed: colour prompts use browser prompt() -->')
        WHERE form_name = 'Rich Text Letter';

        -- (d) Remove jQuery UI colorPicker JS
        UPDATE eform SET form_html = REPLACE(form_html,
            '<script src=\"../js/jquery.ui.colorPicker.min.js\" type=\"text/javascript\"></script>',
            '')
        WHERE form_name = 'Rich Text Letter';

        -- (e) Fix saveRTL() escaping bug: replace the broken function with a correct version
        UPDATE eform SET form_html = REPLACE(form_html,
            'function saveRTL() {\r\n\tneedToConfirm=false;\r\n\tvar theRTL=editControlContents(\'edit\');\r\n\tvar myNewString = theRTL.replace(/\"/g, \'&quot;\');\r\n\tmyNewString = theRTL.replace(/</g, \'&lt;\');\r\n\tmyNewString = theRTL.replace(/>/g, \'&gt;\');\r\n\tdocument.getElementById(\'Letter\').value=myNewString.replace(/\'/g, \"&#39;\");',
            'function saveRTL() {\r\n\tneedToConfirm=false;\r\n\tvar theRTL=editControlContents(\'edit\');\r\n\tvar myNewString = theRTL.replace(/&/g, \'&amp;\');\r\n\tmyNewString = myNewString.replace(/\"/g, \'&quot;\');\r\n\tmyNewString = myNewString.replace(/</g, \'&lt;\');\r\n\tmyNewString = myNewString.replace(/>/g, \'&gt;\');\r\n\tdocument.getElementById(\'Letter\').value=myNewString.replace(/\'/g, \"&#39;\");')
        WHERE form_name = 'Rich Text Letter';

        -- (f) Replace SQL injection in fpreventions() with safe AJAX call
        UPDATE eform SET form_html = REPLACE(form_html,
            'function fpreventions(){\r\n    var sql2pass=\"SELECT   \'<br>\',  prevention_type as \'<b>PREVENTIONS\',\'&#160;&#160;&#160;Date:&#160;\' as \':\', LEFT(prevention_date,10) as \'</b>\' FROM preventions  WHERE demographic_no=\" + demographicNo + \" AND deleted=\'0\' GROUP by prevention_type, prevention_date \";\r\n    $(document).ready(function () {  \r\n        $.ajax({  \r\n            url: \"../oscarReport/RptByExample.do\" ,\r\n            data: { sql: sql2pass }\r\n        }).then(function(data) {  \r\n           //alert(data)\r\n           var elements = $(data);\r\n           var found = elements.find(\'.MainTableRightColumn table table\');\r\n           doHtml(found.html())\r\n        });  \r\n    });\r\n}',
            'function fpreventions(){\r\n    $.ajax({\r\n        url: \"../eform/rtlPreventions.do\",\r\n        data: { demographic_no: demographicNo },\r\n        type: \'get\',\r\n        success: function(data) {\r\n            doHtml(\"<font size=\'3\'><b>Preventions:</b></font><br>\" + data);\r\n        }\r\n    });\r\n}')
        WHERE form_name = 'Rich Text Letter';

        -- (g) Update version marker
        UPDATE eform SET subject = 'Rich Text Letter Generator v2.2'
        WHERE form_name = 'Rich Text Letter'
          AND subject LIKE 'Rich Text Letter Generator%';

        -- Update version comment in HTML
        UPDATE eform SET form_html = REPLACE(form_html,
            '<!--V2.1 Dec 15, 2021 -->',
            '<!--V2.2 Mar 22, 2026 -->')
        WHERE form_name = 'Rich Text Letter';

        SELECT 'RTL eForm modernized to v2.2' AS info;
    END IF;
END //
DELIMITER ;
CALL modernize_rtl_eform();
DROP PROCEDURE IF EXISTS modernize_rtl_eform;
