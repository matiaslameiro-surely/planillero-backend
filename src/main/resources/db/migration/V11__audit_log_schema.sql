-- Auditoría inmutable append-only (PLAN-11 / TASK-12).
--
-- El esquema `audit` ya lo crea V1; acá recién se llena. (V1 también crea un esquema `auditoria`,
-- que quedó sin usar desde el arranque del proyecto: no se toca acá para no mezclar una limpieza
-- de nombres con esta tarea.)
--
-- `payload` es `text`, no `jsonb`, a propósito: JSONB normaliza el texto que guarda (puede
-- reordenar claves, reformatear números), y el hash de la fila se calcula sobre el texto exacto de
-- ese campo. Con `jsonb`, releer la fila podría devolver un texto distinto del que se hasheó al
-- escribirla, y la verificación de integridad reportaría una alteración que nunca ocurrió. La
-- aplicación ya entrega ese texto canonicalizado (claves ordenadas), así que `text` no resigna
-- nada: sólo evita que la base lo reescriba por su cuenta.

create table audit.audit_logs (
    id           uuid        primary key,
    event_type   varchar(80) not null,
    entity_type  varchar(80) not null,
    entity_id    varchar(80),
    username     varchar(80) not null,
    ip           varchar(45),
    device_id    varchar(120),
    payload      text        not null,
    hash_previo  varchar(64) not null,
    hash_actual  varchar(64) not null,
    created_at   timestamptz not null default now()
);

-- Consulta más frecuente del módulo de auditoría del backoffice: filtrar y ordenar por fecha.
create index idx_audit_logs_created_at on audit.audit_logs (created_at desc);

-- "Auditar Integridad de Visita" filtra por entidad.
create index idx_audit_logs_entity on audit.audit_logs (entity_type, entity_id);

create index idx_audit_logs_event_type on audit.audit_logs (event_type);
create index idx_audit_logs_username on audit.audit_logs (username);

-- ---------------------------------------------------------------------------------------------
-- Append-only (OWASP A09): ni la aplicación ni ninguna consola conectada directamente pueden
-- alterar o borrar una fila ya escrita. La regla vive acá, no sólo en el repositorio de Java (que
-- ya no expone ningún método de update/delete), porque un `UPDATE` desde afuera de la aplicación
-- saltearía esa capa.
-- ---------------------------------------------------------------------------------------------

create or replace function audit.reject_audit_log_mutation() returns trigger as $$
begin
    raise exception
        'audit.audit_logs es append-only: % no está permitido sobre la fila %',
        tg_op, coalesce(old.id, new.id);
end;
$$ language plpgsql;

create trigger trg_audit_logs_no_update
    before update on audit.audit_logs
    for each row execute function audit.reject_audit_log_mutation();

create trigger trg_audit_logs_no_delete
    before delete on audit.audit_logs
    for each row execute function audit.reject_audit_log_mutation();
