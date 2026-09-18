-- Plantillas de formulario FICTICIAS para desarrollo y pruebas.
--
-- No corresponden al formulario de ningún cliente ni a ningún dato real: son campos inventados,
-- elegidos para que entre las dos plantillas queden ejercitados todos los tipos de regla que el
-- motor tiene que hacer valer (requerido, tipo, rango, patrón, longitud, enum y propiedad no
-- declarada).
--
-- `mantenimiento-general` va en dos versiones a propósito: la v1 queda inactiva y la v2 activa.
-- Es el caso que demuestra que publicar un cambio es agregar una versión, no editar la anterior.

insert into forms.form_templates (id, template_key, version, name, description, schema_json, active) values
(
    'aaaaaaaa-0001-4000-8000-000000000001',
    'mantenimiento-general',
    1,
    'Mantenimiento general (v1)',
    'Primera versión del parte de mantenimiento. Reemplazada por la v2.',
    '{
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "title": "Mantenimiento general",
      "type": "object",
      "additionalProperties": false,
      "required": ["workedHours", "taskType"],
      "properties": {
        "workedHours": {
          "title": "Horas trabajadas",
          "type": "number",
          "minimum": 0,
          "maximum": 24
        },
        "taskType": {
          "title": "Tipo de tarea",
          "type": "string",
          "enum": ["PREVENTIVO", "CORRECTIVO", "INSPECCION"]
        },
        "observations": {
          "title": "Observaciones",
          "type": "string",
          "maxLength": 500
        }
      }
    }'::jsonb,
    false
),
(
    'aaaaaaaa-0001-4000-8000-000000000002',
    'mantenimiento-general',
    2,
    'Mantenimiento general',
    'Parte de mantenimiento. Suma número de serie del equipo y marca de seguimiento.',
    '{
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "title": "Mantenimiento general",
      "type": "object",
      "additionalProperties": false,
      "required": ["workedHours", "taskType", "observations"],
      "properties": {
        "workedHours": {
          "title": "Horas trabajadas",
          "type": "number",
          "minimum": 0,
          "maximum": 24
        },
        "taskType": {
          "title": "Tipo de tarea",
          "type": "string",
          "enum": ["PREVENTIVO", "CORRECTIVO", "INSPECCION"]
        },
        "observations": {
          "title": "Observaciones",
          "type": "string",
          "maxLength": 500
        },
        "serialNumber": {
          "title": "Número de serie del equipo",
          "type": "string",
          "pattern": "^[A-Z]{3}-[0-9]{4}$"
        },
        "requiresFollowUp": {
          "title": "Requiere seguimiento",
          "type": "boolean"
        }
      }
    }'::jsonb,
    true
),
(
    'aaaaaaaa-0002-4000-8000-000000000001',
    'control-de-acceso',
    1,
    'Control de acceso',
    'Registro de ingreso y egreso en el punto de control.',
    '{
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "title": "Control de acceso",
      "type": "object",
      "additionalProperties": false,
      "required": ["entryTime", "peopleCount"],
      "properties": {
        "entryTime": {
          "title": "Hora de ingreso",
          "type": "string",
          "pattern": "^([01][0-9]|2[0-3]):[0-5][0-9]$"
        },
        "exitTime": {
          "title": "Hora de egreso",
          "type": "string",
          "pattern": "^([01][0-9]|2[0-3]):[0-5][0-9]$"
        },
        "peopleCount": {
          "title": "Cantidad de personas",
          "type": "integer",
          "minimum": 1,
          "maximum": 200
        },
        "incidentNotes": {
          "title": "Novedades",
          "type": "string",
          "maxLength": 300
        }
      }
    }'::jsonb,
    true
);

