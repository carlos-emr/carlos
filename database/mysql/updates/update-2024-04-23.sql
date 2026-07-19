create table lst_gender_copy_backup
select * from lst_gender;
truncate table lst_gender;
insert into lst_gender (code,description,isactive,displayorder) values ('M','Male',1,2);
insert into lst_gender (code,description,isactive,displayorder) values ('F','Female',1,1);
insert into lst_gender (code,description,isactive,displayorder) values ('X','Intersex',1,3);
insert into lst_gender (code,description,isactive,displayorder) values ('U','Undisclosed',1,4);