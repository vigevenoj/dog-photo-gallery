alter table photos add column photouuid uuid;
--;;
alter table photos drop column name;
--;;
alter table photos rename column photouuid to name;