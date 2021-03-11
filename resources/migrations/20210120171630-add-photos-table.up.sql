create sequence public.photos_photoid_seq
start with 1
increment by 1
no minvalue
no maxvalue
cache 1;

--;;
create table photos (
  id bigint primary key default nextval('photos_photoid_seq') not null,
  userid bigint,
  name varchar(30),
  taken timestamp,
  metadata jsonb,
  photo bytea
);
