-- Migración V8: inicio de visita con georreferenciación y doble referencia temporal.
--
-- Nombres de columnas en inglés por convención del proyecto. Los datos del inicio viven en la misma
-- fila de `visits.visits`: hay un único inicio por visita y el tablero del supervisor los lee junto
-- con la visita.
--
-- Todas las columnas nuevas son nulables: las visitas existentes (que nunca se iniciaron) no cambian.

alter table visits.visits
    add column if not exists start_latitude        numeric(9,6),
    add column if not exists start_longitude       numeric(9,6),
    -- Radio de incertidumbre de la lectura GPS, en metros.
    add column if not exists start_accuracy_meters numeric(8,2),
    -- Hora del reloj del dispositivo y hora del reloj del servidor al recibir el inicio.
    add column if not exists started_at_device     timestamptz,
    add column if not exists started_at_server     timestamptz,
    -- started_at_server - started_at_device, en segundos, con signo.
    add column if not exists drift_seconds         bigint,
    add column if not exists started_by            uuid references core.users (id);

-- Se reemplaza el CHECK de `status` para admitir IN_PROGRESS. En vez de borrarlo por su nombre
-- (autogenerado, y por lo tanto frágil) se busca el CHECK que nombra a la columna `status`.
do $$
declare
    existing record;
begin
    for existing in
        select conname
        from pg_constraint
        where conrelid = 'visits.visits'::regclass
          and contype = 'c'
          and pg_get_constraintdef(oid) like '%status%'
    loop
        execute format('alter table visits.visits drop constraint %I', existing.conname);
    end loop;
end
$$;

alter table visits.visits
    add constraint ck_visits_status
        check (status in ('PENDING', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

-- Una visita en curso siempre tiene la evidencia completa de su inicio. Las completadas o canceladas
-- pueden no tenerla (por ejemplo, las del seed), así que la restricción sólo mira IN_PROGRESS.
alter table visits.visits
    add constraint ck_visits_start_data
        check (
            status <> 'IN_PROGRESS'
            or (start_latitude is not null
                and start_longitude is not null
                and start_accuracy_meters is not null
                and started_at_device is not null
                and started_at_server is not null
                and drift_seconds is not null
                and started_by is not null)
        );
