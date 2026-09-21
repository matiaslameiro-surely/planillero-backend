-- Migración V12: Tablero Central de Supervisión, Mapa Operativo y Monitoreo de Turnos.
--
-- Nombres de tablas y columnas en inglés por convención del proyecto.
-- Las tablas de supervisión viven en el esquema `visits`.
-- Se crea la vista de compatibilidad `visits.operador_turnos` para el dominio en español.
--
-- Datos de seed FICTICIOS para desarrollo: turnos, coordenadas y métricas
-- inventadas para probar los diferentes estados del tablero sin ninguna persona ni dato real.

create table if not exists visits.operator_shifts (
    id                 uuid         primary key,
    operator_id        uuid         not null references core.users (id) on delete cascade,
    shift_date         date         not null,
    jurisdiction       varchar(80)  not null,
    status             varchar(30)  not null check (status in ('EN_CAMPO', 'DEMORADO', 'OFFLINE', 'TURNO_COMPLETO')),
    started_at         timestamptz  not null default now(),
    ended_at           timestamptz,
    last_heartbeat_at  timestamptz  not null default now(),
    last_latitude      numeric(9,6),
    last_longitude     numeric(9,6),
    battery_level      numeric(4,2) check (battery_level is null or (battery_level >= 0 and battery_level <= 1.00)),
    network_status     varchar(30)  not null default 'ONLINE' check (network_status in ('ONLINE', 'OFFLINE', 'UNKNOWN')),
    observations       text,
    created_at         timestamptz  not null default now(),
    constraint uk_operator_shifts_operator_date unique (operator_id, shift_date)
);

-- Vista de compatibilidad en español
create or replace view visits.operador_turnos as
select * from visits.operator_shifts;

-- Índices compuestos optimizados para consultas analíticas de supervisión
create index if not exists idx_operator_shifts_jurisdiction_date_status
    on visits.operator_shifts (jurisdiction, shift_date, status);

create index if not exists idx_visits_jurisdiction_status_created
    on visits.visits (jurisdiction, status, created_at);

create index if not exists idx_route_sheets_date_operator
    on visits.route_sheets (route_date, operator_id);

-- Turnos de desarrollo ficticios para la fecha actual (reutiliza usuarios existentes)
insert into visits.operator_shifts (
    id, operator_id, shift_date, jurisdiction, status, started_at, ended_at,
    last_heartbeat_at, last_latitude, last_longitude, battery_level, network_status, observations
) values
    (
        'e0000001-0001-4000-8000-000000000001',
        '11111111-1111-4111-8111-111111111111', -- operador.demo (ZONA_NORTE)
        current_date,
        'ZONA_NORTE',
        'EN_CAMPO',
        now() - interval '3 hours',
        null,
        now() - interval '2 minutes',
        -34.522345,
        -58.478901,
        0.82,
        'ONLINE',
        'Operador en ruta normal cumpliendo SLA'
    ),
    (
        'e0000001-0001-4000-8000-000000000002',
        '44444444-4444-4444-8444-444444444444', -- operador.norte2 (ZONA_NORTE)
        current_date,
        'ZONA_NORTE',
        'DEMORADO',
        now() - interval '4 hours',
        null,
        now() - interval '4 minutes',
        -34.543123,
        -58.452123,
        0.18,
        'ONLINE',
        'Demora por corte de transito y espera prolongada en domicilio'
    ),
    (
        'e0000001-0001-4000-8000-000000000003',
        '55555555-5555-4555-8555-555555555555', -- operador.sur (ZONA_SUR)
        current_date,
        'ZONA_SUR',
        'EN_CAMPO',
        now() - interval '2 hours',
        null,
        now() - interval '1 minute',
        -34.660123,
        -58.375678,
        0.91,
        'ONLINE',
        'Operador en zona sur'
    )
on conflict (operator_id, shift_date) do nothing;
