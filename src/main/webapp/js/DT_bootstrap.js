/**
 * DataTables Bootstrap 5 compatibility shim.
 *
 * This file previously contained a custom Bootstrap pagination plugin for
 * DataTables 1.9.4. Now that we use DataTables 1.13.4 with its official
 * Bootstrap 5 integration (dataTables.bootstrap5.js), this shim only
 * sets project-wide defaults.
 *
 * Pages that load the official dataTables.bootstrap5.js do NOT need this file.
 * Pages that still reference this file will get sensible defaults.
 */
if ($.fn.dataTable) {
    $.extend(true, $.fn.dataTable.defaults, {
        "dom": "<'row'<'col-sm-6'l><'col-sm-6'f>>t<'row'<'col-sm-6'i><'col-sm-6'p>>",
        "language": {
            "lengthMenu": "_MENU_ records per page"
        }
    });
}
