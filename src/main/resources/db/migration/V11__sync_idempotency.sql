-- Migración V11: sincronización por lote con garantía de idempotencia.
--
-- Nombres de tablas y columnas en inglés por convención del proyecto.
--
-- La garantía que sostiene esta tarea es "el mismo envío, mandado N veces, deja UN registro". Eso no
-- se puede garantizar en la capa de servicio: entre un `select` y un `insert` entran los otros 19
-- hilos, y mañana hay dos instancias del backend. Por eso las dos protecciones son restricciones de
-- la base, y el código se limita a interpretar el error que devuelven.

create schema if not exists sync;

-- ---------------------------------------------------------------------------------------------
-- Claves de idempotencia (nivel lote)
-- ---------------------------------------------------------------------------------------------
--
-- Una fila por envío. El cliente manda `Idempotency-Key`; el backend la reserva insertando acá en
-- `IN_PROGRESS`, procesa, y la cierra guardando la respuesta. Un reintento encuentra la fila:
--   - COMPLETED   -> se devuelve `response_json` tal cual, sin reprocesar nada.
--   - IN_PROGRESS -> hay otro hilo trabajando con esa clave; se responde 409.
--
-- `user_id` no es decorativo: sin él, quien adivinara (o interceptara) la clave de otro operador
-- recibiría la respuesta cacheada de un expediente ajeno. La clave es única globalmente, pero sólo
-- la devuelve a quien la creó.
--
-- `request_hash` es SHA-256 del cuerpo. Es lo que distingue un reintento legítimo (mismo cuerpo) de
-- un cliente que reusa una clave para otra cosa (cuerpo distinto -> 409). Se guarda la huella y no
-- el cuerpo: alcanza para comparar y no duplica datos del expediente.

create table sync.idempotency_keys (
    idempotency_key uuid        primary key,
    user_id         uuid        not null references core.users (id),
    -- SHA-256 en hexadecimal: 64 caracteres exactos. `varchar` y no `char` porque PostgreSQL expone
    -- `char(n)` como `bpchar`, un tipo distinto que la validación de esquema de Hibernate rechaza.
    request_hash    varchar(64) not null,
    status          varchar(20) not null,
    response_json   jsonb,
    created_at      timestamptz not null default now(),
    completed_at    timestamptz,

    constraint ck_idempotency_keys_status
        check (status in ('IN_PROGRESS', 'COMPLETED')),
    -- Una clave cerrada siempre tiene qué devolver. Sin esto, un reintento podría recibir `null`
    -- como si fuera la respuesta original.
    constraint ck_idempotency_keys_completed
        check (status <> 'COMPLETED' or (response_json is not null and completed_at is not null))
);

-- Las claves viejas se purgan por fecha (fuera del alcance de esta tarea, pero el índice ya está).
create index idx_idempotency_keys_created_at on sync.idempotency_keys (created_at);

-- ---------------------------------------------------------------------------------------------
-- Idempotencia por operación
-- ---------------------------------------------------------------------------------------------
--
-- La clave de lote no alcanza sola: si el cliente recorta o reorganiza un lote, la misma acción
-- puede volver a viajar con otra clave. `sync_operation_id` es el identificador que el móvil le da
-- a la operación cuando la encola, y viaja intacto en todos los reintentos.
--
-- El UNIQUE es lo que hace que dos escrituras concurrentes de la misma operación terminen en una
-- sola fila: la segunda choca contra el índice y el servicio la resuelve como duplicada.

alter table visits.visits
    add column if not exists sync_operation_id uuid,
    -- El formulario llegó por el endpoint de lote y no por la carga en línea. Es lo que el
    -- backoffice muestra como "Diferida".
    add column if not exists synced_deferred   boolean     not null default false,
    add column if not exists synced_at         timestamptz;

-- Índice único parcial: las visitas sin formulario sincronizado (la enorme mayoría) no ocupan lugar
-- en el índice.
create unique index if not exists uq_visits_sync_operation_id
    on visits.visits (sync_operation_id)
    where sync_operation_id is not null;

-- Una visita marcada como diferida siempre dice cuándo y por qué operación llegó.
alter table visits.visits
    add constraint ck_visits_synced_deferred
        check (
            not synced_deferred
            or (sync_operation_id is not null and synced_at is not null)
        );
