ALTER TABLE billing_on_diskname
  ADD UNIQUE KEY billing_on_diskname_ohipfilename_uq (ohipfilename);

ALTER TABLE billing_on_filename
  ADD UNIQUE KEY billing_on_filename_htmlfilename_uq (htmlfilename);
