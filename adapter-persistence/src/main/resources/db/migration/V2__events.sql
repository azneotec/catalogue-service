-- Events with their seat map stored as one JSONB document (ADR-006).
create table event (
    id            uuid         primary key,
    venue_id      uuid         not null references venue (id),
    title         varchar(200) not null,
    starts_at     timestamptz  not null,       -- an instant (stored as UTC); zone_id rebuilds local time
    zone_id       varchar(64)  not null,
    status        varchar(16)  not null,
    published_at  timestamptz,
    cancelled_at  timestamptz,
    cancel_reason varchar(500),
    seat_map      jsonb        not null,       -- sections -> rows -> seats
    version       bigint       not null,       -- optimistic locking (JPA @Version)
    constraint event_status_known
        check (status in ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    -- The database repeats the domain's state rules as a last line of defence.
    constraint event_published_has_time
        check ((status = 'PUBLISHED') = (published_at is not null)),
    constraint event_cancelled_has_time_and_reason
        check ((status = 'CANCELLED') = (cancelled_at is not null and cancel_reason is not null))
);

-- ListEvents: "where status in (...) order by starts_at" can read this index in order.
create index event_status_starts_at_idx on event (status, starts_at);
-- Postgres does not index foreign keys by itself; this one serves joins and venue deletes.
create index event_venue_id_idx on event (venue_id);
-- GIN index for JSONB containment queries (seat_map @> '{...}'). jsonb_path_ops is smaller and
-- faster than the default operator class, but supports only @> (and jsonpath) queries.
create index event_seat_map_idx on event using gin (seat_map jsonb_path_ops);
