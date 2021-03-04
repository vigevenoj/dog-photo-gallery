create table photos (
  id bigint primary key,
  name varchar(30),
  taken timestamp,
  metadata jsonb,
  photo bytea
);
