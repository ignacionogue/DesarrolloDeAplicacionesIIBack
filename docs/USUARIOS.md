# Login multiusuario y cuentas de demo

El login consulta PostgreSQL. Cada cuenta tiene username único, contraseña BCrypt
con coste 12, un rol y habilitación. No se guardan contraseñas en texto plano en la
tabla ni se devuelven hashes por HTTP. Flyway V4 crea la tabla vacía, sin tocar V1–V3.

## Contrato HTTP

`POST /api/auth/login`, público, recibe `{username, password}`. Responde 200 con
`{accessToken, username, role}`, igual que antes. El rol siempre viene de la cuenta;
agregar otro rol al body no concede permisos. Usuario/contraseña incorrectos y
cuentas deshabilitadas dan el mismo error 401 con `{message, code, details}`.
Entradas vacías o demasiado largas dan 400. Username se normaliza a minúsculas;
la contraseña se compara exactamente.

`GET /api/auth/me` requiere Bearer y devuelve `{username, role}`.
`POST /api/auth/logout` requiere Bearer, no lleva body y responde 204 sin contenido.
Invalida **todas las sesiones actuales de esa cuenta**, no las de otros usuarios.
Un nuevo login emite un token con la versión actualizada.

El filtro comprueba firma, expiración, ID de cuenta, rol, habilitación y versión
persistida. Los JWT anteriores a esta implementación requieren nuevo login.
Eliminar y recrear un username no reactiva sus tokens anteriores.

## Alta inicial de cuentas

Variable nueva opcional: **AUTH_BOOTSTRAP_USERS**. Es un array JSON de objetos
con username, password y role, proporcionado como secreto del entorno. La aplicación
valida toda la lista, rechaza duplicados, calcula BCrypt y crea solo usuarios nuevos.
No sobrescribe contraseñas, roles ni habilitación de cuentas existentes al reiniciar.

Username: 3–100 caracteres de `a-z`, `0-9`, punto, guion o guion bajo. Password:
al menos 12 caracteres y como máximo 72 bytes UTF-8. Rol: uno de los acordados.
Configuración inválida impide el arranque con un mensaje sin contraseñas.

Generar una cuenta por rol operativo, con contraseñas aleatorias:

```powershell
# PowerShell 7, carpeta local ignorada por Git ya creada.
./scripts/New-DemoUsers.ps1 -OutputPath .local-validation/demo-users.json
$env:AUTH_BOOTSTRAP_USERS = Get-Content .local-validation/demo-users.json -Raw
```

El generador no sobrescribe archivos existentes ni conecta a la DB. El alta sucede
al arrancar el backend con DB_* y JWT_SECRET. El archivo contiene contraseñas para
el equipo autorizado: no subirlo al PR, incluirlo en Docker ni publicarlo.
Para Azure, DevOps debe configurar el secreto mediante su proceso habitual.

Las cinco cuentas son personal.obras (PERSONAL_OBRAS), responsable
(RESPONSABLE_AUTORIZADO), jefe.cuadrilla (JEFE_CUADRILLA), operario
(OPERARIO_CONTRATISTA) e inspector (INSPECTOR_OBRA).

Después del alta se puede quitar AUTH_BOOTSTRAP_USERS: las cuentas siguen funcionando.
Para verify-demo/verify-delivery, iniciar sesión con cada cuenta y armar una
hashtable `$tokens` con rol → accessToken en memoria. Nunca usar JWT_SECRET en el front.

## Compatibilidad y alcance

AUTH_USERNAME/AUTH_PASSWORD/AUTH_ROLE dejan de ser el mecanismo de autenticación;
se conservan para dar de alta una vez la cuenta antigua, si están configurados.
Pueden quedar vacíos si se usa AUTH_BOOTSTRAP_USERS o ya existen cuentas.
Si ambos mecanismos contienen el mismo username, la lista tiene prioridad para
una cuenta nueva; si ya existe, se conserva la base. Cambiar las variables antiguas
no cambia una contraseña/rol ya persistidos.

No hay registro público, selección de roles desde el cliente, panel administrativo
ni recuperación de contraseña. La administración y rotación posterior requieren
un caso de uso autorizado adicional o identidad central; no modificar tablas
manualmente en Azure para resolverlo.

DB_URL, DB_USERNAME, DB_PASSWORD y JWT_SECRET conservan su función. Proxy `/api`,
CORS y despliegue siguen a cargo de DevOps. No hay cambios en frontend ni pipelines.

## Validación local (20/09/2026)

- Maven verify: 60 pruebas H2, sin fallos, JAR generado.
- Maven clean verify con postgres-test: 60 pruebas PostgreSQL 18.6, sin fallos.
- JAR con cinco cuentas reales en la misma instancia: login/me de cada rol,
  131 controles demo y 33 de entrega, sin fallos.
- Logout revoca la sesión; reinicio sin AUTH_BOOTSTRAP_USERS conserva las cinco
  cuentas, la revocación y las órdenes asociadas a proyectos.
- OpenAPI exportado incluye login, me, logout y esquema Bearer.
- PostgreSQL de demo es local y aislada; no se aprovisionaron cuentas en Azure.

El respaldo anterior al cambio de usuarios permanece en el commit `dd89e98`.
