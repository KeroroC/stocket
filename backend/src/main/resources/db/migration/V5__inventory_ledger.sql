-- Inventory ledger schema

create table inventory_entry (
    id uuid primary key,
    household_id uuid not null references household(id),
    item_definition_id uuid not null references item_definition(id),
    location_id uuid not null references location(id),
    inventory_type varchar(16) not null
        check (inventory_type in ('BATCH', 'ASSET')),
    available_quantity numeric(19,4) not null
        check (available_quantity >= 0),
    received_at timestamptz not null,
    production_date date,
    expiration_date date,
    custom_attributes jsonb not null default '{}'::jsonb
        check (jsonb_typeof(custom_attributes) = 'object'),
    version bigint not null default 0,
    archived_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (inventory_type = 'BATCH' or available_quantity in (0, 1))
);

create index inventory_entry_item_idx
    on inventory_entry (household_id, item_definition_id, archived_at);

create index inventory_entry_location_idx
    on inventory_entry (household_id, location_id, archived_at);

create index inventory_entry_expiration_idx
    on inventory_entry (household_id, expiration_date, id)
    where archived_at is null;

create table batch_detail (
    inventory_entry_id uuid primary key references inventory_entry(id),
    batch_number varchar(120),
    source_entry_id uuid references inventory_entry(id),
    shelf_life_value integer check (shelf_life_value > 0),
    shelf_life_unit varchar(8)
        check (shelf_life_unit in ('DAY', 'MONTH', 'YEAR')),
    check ((shelf_life_value is null) = (shelf_life_unit is null))
);

create table asset_detail (
    inventory_entry_id uuid primary key references inventory_entry(id),
    household_id uuid not null references household(id),
    asset_number varchar(80) not null,
    serial_number varchar(160),
    purchase_date date,
    warranty_expires_on date,
    status varchar(16) not null
        check (status in ('AVAILABLE', 'IN_USE', 'LOANED', 'LOST', 'RETIRED')),
    constraint uq_asset_number unique (household_id, asset_number)
);

create table idempotency_record (
    id uuid primary key,
    household_id uuid not null references household(id),
    account_id uuid not null references user_account(id),
    operation varchar(48) not null,
    idempotency_key varchar(120) not null,
    request_hash char(64) not null,
    status varchar(16) not null
        check (status in ('PROCESSING', 'COMPLETED')),
    http_status integer,
    response_body jsonb,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    check ((status = 'PROCESSING' and http_status is null and response_body is null)
        or (status = 'COMPLETED' and http_status between 100 and 599 and response_body is not null)),
    constraint uq_idempotency_account_operation_key
        unique (account_id, operation, idempotency_key)
);

create index idempotency_record_expiration_idx
    on idempotency_record (expires_at);

create table inventory_movement (
    id uuid primary key,
    household_id uuid not null references household(id),
    entry_id uuid not null references inventory_entry(id),
    related_entry_id uuid references inventory_entry(id),
    movement_type varchar(24) not null
        check (movement_type in ('RECEIVE', 'CONSUME', 'RETURN', 'ADJUSTMENT',
                                 'LOSS', 'RETIRE', 'TRANSFER', 'TRANSFER_OUT',
                                 'TRANSFER_IN', 'REVERSAL')),
    quantity_delta numeric(19,4) not null,
    from_location_id uuid references location(id),
    to_location_id uuid references location(id),
    reason varchar(240),
    actor_account_id uuid not null references user_account(id),
    idempotency_record_id uuid not null references idempotency_record(id),
    request_id varchar(80) not null,
    occurred_at timestamptz not null
);

create index inventory_movement_entry_timeline_idx
    on inventory_movement (entry_id, occurred_at desc, id desc);

create index inventory_movement_household_request_idx
    on inventory_movement (household_id, request_id);

create table inventory_reconciliation_issue (
    id uuid primary key,
    household_id uuid not null references household(id),
    entry_id uuid not null references inventory_entry(id),
    expected_quantity numeric(19,4) not null,
    actual_quantity numeric(19,4) not null,
    status varchar(16) not null check (status in ('OPEN', 'RESOLVED')),
    detected_at timestamptz not null,
    resolved_at timestamptz,
    check ((status = 'OPEN' and resolved_at is null)
        or (status = 'RESOLVED' and resolved_at is not null))
);

create unique index uq_inventory_reconciliation_open_issue
    on inventory_reconciliation_issue (entry_id, expected_quantity, actual_quantity)
    where status = 'OPEN';

create index inventory_reconciliation_household_status_idx
    on inventory_reconciliation_issue (household_id, status, detected_at desc);
