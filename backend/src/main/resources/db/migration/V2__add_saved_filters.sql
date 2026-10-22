-- Saved queue filters: an admin can store a named set of queue filters
-- (for example "Overdue royalties") and re-apply it with one click.
create table saved_filter (
    id          uuid primary key default gen_random_uuid(),
    owner_id    uuid          not null references app_user (id) on delete cascade,
    name        varchar(120)  not null,
    filters     text          not null,
    created_at  timestamptz   not null default now(),
    constraint uk_saved_filter_owner_name unique (owner_id, name)
);

create index idx_saved_filter_owner on saved_filter (owner_id);
