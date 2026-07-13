alter table demographic
    add genderId int null;

alter table demographic
    add pronoun varchar(25) null;

alter table demographic
    add pronounId int null;

alter table demographic
    add gender varchar(25) null;

alter table demographicArchive
    add genderId int null;

alter table demographicArchive
    add pronoun varchar(25) null;

alter table demographicArchive
    add pronounId int null;

alter table demographicArchive
    add gender varchar(25) null;