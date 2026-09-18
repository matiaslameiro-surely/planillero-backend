-- Formularios tipificados: catálogo de plantillas con JSON Schema y respuestas en JSONB.
--
-- Numerada V9 a propósito: se ejecuta después de V7 (PLAN-8), que crea `visits.visits`, y de V8
-- (PLAN-9), que le agrega el inicio de visita. Flyway acepta los huecos de numeración.
--
-- La forma de cada formulario es un dato, no código: se guarda como JSON Schema en la columna
-- `schema_json`. Agregar un campo a un formulario es publicar una versión nueva de la plantilla,
-- no recompilar el backend.
--
-- Nombres de tablas y columnas en inglés por convención del proyecto. Los esquemas lógicos `forms`
-- y `visits` ya los crea V1; acá recién se llenan.

-- ---------------------------------------------------------------------------------------------
-- Plantillas de formulario
-- ---------------------------------------------------------------------------------------------

create table forms.form_templates (
    id          uuid        primary key,
    template_key varchar(80) not null,
    version     integer     not null,
    name        varchar(160) not null,
    description varchar(500),
    schema_json jsonb       not null,
    active      boolean     not null default true,
    created_at  timestamptz not null default now(),

    -- Una versión de una plantilla existe una sola vez: es lo que hace que "versión" signifique algo.
    constraint uq_form_templates_key_version unique (template_key, version),
    constraint ck_form_templates_version_positive check (version > 0)
);

-- Índice GIN para poder consultar por el contenido del schema (por ejemplo, qué plantillas declaran
-- cierto campo) sin recorrer la tabla entera. jsonb_path_ops es más chico y más rápido que el
-- default para el operador de contención, que es el único que se usa acá.
create index idx_form_templates_schema_gin
    on forms.form_templates using gin (schema_json jsonb_path_ops);

-- Resolver "la última versión activa de esta clave" es la consulta más frecuente del catálogo.
create index idx_form_templates_key_version
    on forms.form_templates (template_key, version desc);

-- ---------------------------------------------------------------------------------------------
-- Inmutabilidad de las plantillas publicadas (OWASP A03)
-- ---------------------------------------------------------------------------------------------
--
-- Una plantilla publicada no se edita: se publica una versión nueva. Si se pudiera editar, las
-- respuestas ya guardadas quedarían validadas contra un schema que ya no existe, y nadie podría
-- reconstruir contra qué reglas se aceptó un formulario.
--
-- La regla vive en la base y no sólo en el servicio, porque un UPDATE desde una consola o desde
-- otro proceso saltearía la capa de aplicación. Dar de baja una plantilla (`active`) sí se permite:
-- eso no cambia las reglas con las que se validó nada.

create or replace function forms.reject_form_template_mutation() returns trigger as $$
begin
    if new.schema_json   is distinct from old.schema_json
       or new.template_key is distinct from old.template_key
       or new.version    is distinct from old.version
       or new.name       is distinct from old.name
       or new.description is distinct from old.description
       or new.created_at is distinct from old.created_at then
        raise exception
            'La plantilla %/v% es inmutable: publicá una versión nueva en lugar de modificarla',
            old.template_key, old.version;
    end if;
    return new;
end;
$$ language plpgsql;

create trigger trg_form_templates_immutable
    before update on forms.form_templates
    for each row execute function forms.reject_form_template_mutation();

-- ---------------------------------------------------------------------------------------------
-- Formulario de la visita
-- ---------------------------------------------------------------------------------------------
--
-- La tabla `visits.visits` es de PLAN-8 (V7): acá sólo se le agregan las columnas del formulario.
-- `add column if not exists` para que la migración sea segura aunque alguna otra tarea ya las haya
-- creado con la misma forma.

alter table visits.visits
    -- Qué versión exacta de qué plantilla validó estas respuestas. Sin esto, el JSON guardado no
    -- se puede interpretar más adelante.
    add column if not exists form_template_id  uuid references forms.form_templates (id),
    add column if not exists responses_json    jsonb,
    add column if not exists form_submitted_at timestamptz;

-- Índice GIN sobre las respuestas: es lo que permite buscar visitas por el valor de un campo del
-- formulario (por ejemplo, las que reportaron cierto tipo de tarea) sin scan completo.
create index if not exists idx_visits_responses_gin
    on visits.visits using gin (responses_json jsonb_path_ops);
