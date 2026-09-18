-- Migración V4: Planificación de rutas (visitas y hojas de ruta).
--
-- Nombres de tablas y columnas en inglés por convención del proyecto. Las tablas de negocio viven
-- en el esquema `visits`; la jurisdicción de cada usuario queda en `core.users` y es la base del
-- control de acceso horizontal (OWASP A01): un supervisor sólo opera sobre su zona.
--
-- Datos de seed FICTICIOS para desarrollo: usuarios, jurisdicciones, direcciones y coordenadas
-- inventadas; no hay ninguna persona ni dato real acá.

-- Jurisdicción del usuario (zona en la que opera).
alter table core.users add column jurisdiction varchar(80);

update core.users
set jurisdiction = case
    when username = 'admin.demo'      then 'GLOBAL'
    when username = 'supervisor.demo' then 'ZONA_NORTE'
    when username = 'operador.demo'   then 'ZONA_NORTE'
    else 'ZONA_NORTE'
end;

alter table core.users alter column jurisdiction set not null;

-- Visitas: la unidad de trabajo que el supervisor asigna a un operador.
create table visits.visits (
    id           uuid         primary key,
    code         varchar(40)  not null unique,
    address      varchar(200) not null,
    latitude     numeric(9,6) not null,
    longitude    numeric(9,6) not null,
    jurisdiction varchar(80)  not null,
    status       varchar(20)  not null check (status in ('PENDING', 'ASSIGNED', 'COMPLETED', 'CANCELLED')),
    urgency      varchar(20)  not null check (urgency in ('LOW', 'MEDIUM', 'HIGH')),
    created_at   timestamptz  not null default now()
);

create index idx_visits_jurisdiction_status on visits.visits (jurisdiction, status);
create index idx_visits_urgency on visits.visits (urgency);

-- Hoja de ruta: qué visitas le tocan a qué operador un día, en qué orden.
-- `position` es la posición en el recorrido, que arma el backend por urgencia.
create table visits.route_sheets (
    id          uuid         primary key,
    operator_id uuid         not null references core.users (id) on delete cascade,
    route_date  date         not null,
    visit_id    uuid         not null references visits.visits (id) on delete cascade,
    position    integer      not null,
    assigned_by uuid         not null references core.users (id),
    created_at  timestamptz  not null default now(),
    -- Una visita no se agenda dos veces el mismo día (ni al mismo operador ni a dos).
    constraint uk_route_sheet_visit_date unique (visit_id, route_date)
);

create index idx_route_sheets_operator_date on visits.route_sheets (operator_id, route_date, position);

-- Seed de desarrollo (todo ficticio). Los operadores nuevos reutilizan un hash bcrypt de desarrollo
-- ya existente; la contraseña documentada es la misma de los otros usuarios de demo: Operador123!.
insert into core.users (id, username, password_hash, two_factor_enabled, enabled, created_at, jurisdiction) values
    ('44444444-4444-4444-8444-444444444444', 'operador.norte2',
     '$2a$12$cN6/yxSkIISqlYD798skYuUJDoPhu5zJlCjzYDex66HTwIB6IF/MG', false, true, now(), 'ZONA_NORTE'),
    ('55555555-5555-4555-8555-555555555555', 'operador.sur',
     '$2a$12$cN6/yxSkIISqlYD798skYuUJDoPhu5zJlCjzYDex66HTwIB6IF/MG', false, true, now(), 'ZONA_SUR');

insert into core.user_roles (user_id, role_id)
select u.id, r.id from core.users u, core.roles r
where u.username in ('operador.norte2', 'operador.sur') and r.name = 'OPERATOR';

-- Visitas de la zona norte (6: cinco por asignar y una completada, para variedad de filtros).
insert into visits.visits (id, code, address, latitude, longitude, jurisdiction, status, urgency) values
    ('a0000001-0000-4000-8000-000000000001', 'V-1001', 'Av. Cabildo 1234, CABA',          -34.543123, -58.452123, 'ZONA_NORTE', 'PENDING',   'HIGH'),
    ('a0000001-0000-4000-8000-000000000002', 'V-1002', 'Av. Maipu 2450, Vicente Lopez',    -34.522345, -58.478901, 'ZONA_NORTE', 'PENDING',   'MEDIUM'),
    ('a0000001-0000-4000-8000-000000000003', 'V-1003', 'Uruguay 3100, Olivos',             -34.510912, -58.487654, 'ZONA_NORTE', 'PENDING',   'MEDIUM'),
    ('a0000001-0000-4000-8000-000000000004', 'V-1004', 'Melo 890, San Isidro',             -34.472987, -58.536543, 'ZONA_NORTE', 'PENDING',   'LOW'),
    ('a0000001-0000-4000-8000-000000000005', 'V-1005', 'Blanco Encalada 4560, Martinez',   -34.491234, -58.504321, 'ZONA_NORTE', 'PENDING',   'LOW'),
    ('a0000001-0000-4000-8000-000000000006', 'V-1006', 'Ituzaingo 1120, Acassuso',         -34.480876, -58.518765, 'ZONA_NORTE', 'COMPLETED', 'HIGH');

-- Visitas de la zona sur: sirven para demostrar que el supervisor del norte no puede tocarlas.
insert into visits.visits (id, code, address, latitude, longitude, jurisdiction, status, urgency) values
    ('a0000002-0000-4000-8000-000000000001', 'V-2001', 'Av. Hipolito Yrigoyen 890, Avellaneda', -34.660123, -58.375678, 'ZONA_SUR', 'PENDING', 'HIGH'),
    ('a0000002-0000-4000-8000-000000000002', 'V-2002', 'Mitre 1550, Lanus',                    -34.674456, -58.392345, 'ZONA_SUR', 'PENDING', 'LOW');