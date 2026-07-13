alter table fax_config add column download tinyint(1);
update fax_config set download = 1 where download is null;