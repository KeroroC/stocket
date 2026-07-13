-- Catalog and location schema

create table category (
    id uuid primary key,
    household_id uuid not null references household(id),
    parent_id uuid references category(id),
    name varchar(120) not null,
    normalized_name varchar(120) not null,
    default_inventory_type varchar(16) not null
        check (default_inventory_type in ('BATCH', 'ASSET')),
    attribute_schema jsonb not null default '[]'::jsonb
        check (jsonb_typeof(attribute_schema) = 'array'),
    version bigint not null default 0,
    archived_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index uq_category_active_sibling_name
    on category (household_id, parent_id, normalized_name) nulls not distinct
    where archived_at is null;

create table location (
    id uuid primary key,
    household_id uuid not null references household(id),
    parent_id uuid references location(id),
    name varchar(120) not null,
    normalized_name varchar(120) not null,
    public_code varchar(64) not null,
    version bigint not null default 0,
    archived_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index uq_location_active_sibling_name
    on location (household_id, parent_id, normalized_name) nulls not distinct
    where archived_at is null;

create unique index uq_location_household_public_code
    on location (household_id, public_code);

create table item_definition (
    id uuid primary key,
    household_id uuid not null references household(id),
    category_id uuid references category(id),
    name varchar(120) not null,
    normalized_name varchar(120) not null,
    brand varchar(120),
    model varchar(120),
    specification varchar(255),
    default_unit varchar(32) not null,
    default_shelf_life_value integer,
    default_shelf_life_unit varchar(16)
        check (default_shelf_life_unit in ('DAY', 'MONTH', 'YEAR')),
    custom_attributes jsonb not null default '{}'::jsonb,
    version bigint not null default 0,
    archived_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index item_definition_household_category_idx
    on item_definition (household_id, category_id, archived_at);

create table item_barcode (
    id uuid primary key,
    household_id uuid not null references household(id),
    item_definition_id uuid not null references item_definition(id) on delete cascade,
    raw_value varchar(255) not null,
    normalized_value varchar(255) not null,
    version bigint not null default 0,
    archived_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index uq_item_barcode_household_normalized_value
    on item_barcode (household_id, normalized_value);

create index item_barcode_item_definition_idx
    on item_barcode (item_definition_id);

create table item_tag (
    id uuid primary key,
    household_id uuid not null references household(id),
    item_definition_id uuid not null references item_definition(id) on delete cascade,
    value varchar(120) not null,
    normalized_value varchar(120) not null,
    version bigint not null default 0,
    archived_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index uq_item_tag_item_normalized_value
    on item_tag (item_definition_id, normalized_value);

create index item_tag_household_idx
    on item_tag (household_id, item_definition_id);

create table catalog_search_projection (
    item_definition_id uuid primary key references item_definition(id) on delete cascade,
    household_id uuid not null references household(id),
    display_name varchar(120) not null,
    category_path text,
    brand varchar(120),
    model varchar(120),
    specification varchar(255),
    tags text[] not null default '{}',
    raw_barcodes text[] not null default '{}',
    normalized_barcodes text[] not null default '{}',
    searchable_text text not null default '',
    archived boolean not null default false,
    updated_at timestamptz not null default now()
);

create index gin_catalog_search_text
    on catalog_search_projection using gin (searchable_text gin_trgm_ops);

create index gin_catalog_search_normalized_barcodes
    on catalog_search_projection using gin (normalized_barcodes);

create index catalog_search_stable_page_idx
    on catalog_search_projection (household_id, archived, display_name, item_definition_id);
