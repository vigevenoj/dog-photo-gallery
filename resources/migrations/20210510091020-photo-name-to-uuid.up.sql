alter table photos add column photouuid;
alter table photos drop column name;
alter table photos alter column rename photouuid to name;