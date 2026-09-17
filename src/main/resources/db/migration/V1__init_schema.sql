-- Migración inicial V1: Configuración de esquemas lógicos y extensiones base

-- Habilitación de extensión pgcrypto para generación de UUIDs criptográficos y funciones hash
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Esquemas lógicos canónicos en inglés
CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS visits;
CREATE SCHEMA IF NOT EXISTS forms;
CREATE SCHEMA IF NOT EXISTS audit;

-- Esquemas secundarios para compatibilidad directa con especificaciones de dominio en español
CREATE SCHEMA IF NOT EXISTS visitas;
CREATE SCHEMA IF NOT EXISTS formularios;
CREATE SCHEMA IF NOT EXISTS auditoria;
