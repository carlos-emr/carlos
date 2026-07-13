-- this index is designed to reduce lab opening time by optimizing this query in labdisplay.jsp
-- CaseManagementNoteLink cml = caseManagementManager.getLatestLinkByTableId(CaseManagementNoteLink.LABTEST,Long.valueOf(segmentID),j+"-"+k);                                       
-- note, this create index if not exists only works in mariadb
CREATE INDEX IF NOT EXISTS `casemgmt_note_link_table_table_name_index` ON `casemgmt_note_link` (`table_name`, `table_id`, `other_id`);