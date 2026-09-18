-- Datos de seed FICTICIOS para desarrollo.
--
-- No hay ninguna persona ni credencial real acá: son usuarios de prueba inventados. Las contraseñas
-- se guardan ya hasheadas con bcrypt (factor 12) y las de texto plano son sólo para documentar el
-- acceso de desarrollo:
--
--   operador.demo   / Operador123!    (rol OPERATOR)
--   supervisor.demo / Supervisor123!  (rol SUPERVISOR)
--   admin.demo      / Admin123!       (rol ADMINISTRATOR)

insert into core.roles (name) values
    ('OPERATOR'),
    ('SUPERVISOR'),
    ('ADMINISTRATOR');

insert into core.users (id, username, password_hash, two_factor_enabled, enabled, created_at) values
    ('11111111-1111-4111-8111-111111111111', 'operador.demo',
     '$2a$12$cN6/yxSkIISqlYD798skYuUJDoPhu5zJlCjzYDex66HTwIB6IF/MG', false, true, now()),
    ('22222222-2222-4222-8222-222222222222', 'supervisor.demo',
     '$2a$12$gx9L8hwL.EFxY5B2llL64ujPEBPSKKZcB7u.xz3WP48wqsSLj5M4i', false, true, now()),
    ('33333333-3333-4333-8333-333333333333', 'admin.demo',
     '$2a$12$qOf92rIMypzXJpKEnGeW..jmUt8oE85mQres5ohhAb9x99IWB2mH2', false, true, now());

insert into core.user_roles (user_id, role_id)
select '11111111-1111-4111-8111-111111111111', id from core.roles where name = 'OPERATOR';
insert into core.user_roles (user_id, role_id)
select '22222222-2222-4222-8222-222222222222', id from core.roles where name = 'SUPERVISOR';
insert into core.user_roles (user_id, role_id)
select '33333333-3333-4333-8333-333333333333', id from core.roles where name = 'ADMINISTRATOR';