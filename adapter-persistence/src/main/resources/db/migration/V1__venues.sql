-- Venues: places where events happen. Owned by catalogue-service (one database per service, ADR-028).
-- Flyway migrations are immutable once applied: change the schema with a new V<n> file, never by
-- editing this one (Flyway stores a checksum per file and refuses to start if it changes).
create table venue (
    id      uuid         primary key,          -- assigned by the domain (VenueId), not by the database
    name    varchar(200) not null,
    city    varchar(100) not null,
    zone_id varchar(64)  not null              -- IANA time zone, e.g. Europe/London
);
