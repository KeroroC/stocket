create extension if not exists pg_trgm;
create table app_schema_marker (
    version integer primary key,
    installed_at timestamptz not null default now()
);
insert into app_schema_marker (version) values (1);
