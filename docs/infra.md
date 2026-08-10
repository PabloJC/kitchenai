# Infraestructura de KitchenAI

Todo lo que no es código de la app: cómo está montado el repositorio, qué hace cada
workflow y las trampas que cuestan una tarde si no las conoces de antemano.

Las reglas de arquitectura viven en [`CLAUDE.md`](../CLAUDE.md) y son normativas: las lee
tanto quien implementa como el revisor automático.

---

## El ciclo de una tarea

```
gh issue create                      plantilla con plan  ──► tarjeta en Todo
git checkout -b feat/12-slug
gh pr create --fill                  cuerpo con Closes #12 ──► In progress
                                     Claude revisa         ──► In review si aprueba
gh pr edit --add-label ready-to-merge                      ──► merge solo ──► Done
```

Nada de esto requiere intervención manual salvo escribir la issue y poner la etiqueta.

---

## Workflows

| Fichero | Qué hace |
|---|---|
| `ci.yml` | Lint, detekt, tests JVM, APK de Android y framework de iOS. Agrega en el check **`CI passed`** |
| `ai-code-review.yml` | Claude revisa el diff contra `CLAUDE.md`, comenta en línea y emite veredicto. Check **`Claude review`** |
| `auto-merge.yml` | Con la etiqueta `ready-to-merge` y todo en verde, mergea con squash y borra la rama |
| `project-sync.yml` | Mueve la tarjeta del tablero según lo que pasa con la issue y su PR |

`CI passed` y `Claude review` son los dos únicos checks obligatorios en `main`. Así se
pueden añadir o quitar jobs internos sin tocar la configuración de la rama protegida.

---

## Puesta en marcha desde cero

```bash
gh secret set GOOGLE_SERVICES_JSON          # base64 del fichero de Android
gh secret set GOOGLE_SERVICE_INFO_PLIST     # base64 del fichero de iOS
gh secret set CLAUDE_CODE_OAUTH_TOKEN       # claude setup-token
gh secret set PROJECT_TOKEN                 # token clásico, ver abajo

gh auth refresh -s project,read:project
./scripts/setup-project.sh
./scripts/setup-branch-protection.sh
```

Además hace falta instalar la **GitHub App de Claude** (<https://github.com/apps/claude>) en
el repositorio: el action intercambia un token OIDC por un token de app, y sin la app
devuelve 401 antes de llegar a Anthropic.

Los ficheros de configuración de Firebase **no se commitean**; en CI se restauran desde los
secrets. Ojo con la ruta: el de iOS vive en `iosApp/GoogleService-Info.plist`, no en
`iosApp/iosApp/`.

---

## Trampas

### El workflow de revisión debe ser idéntico al de `main`

```
Warning: Skipping action due to workflow validation: Workflow validation failed.
```

El action de Claude se niega a ejecutarse si `ai-code-review.yml` difiere de la versión de la
rama por defecto. Es una medida de seguridad obvia —si no, cualquier PR podría modificar el
workflow para ejecutar lo que quisiera con los secrets—, pero tiene dos consecuencias:

1. Para probar un cambio en ese workflow hay que llevarlo antes a `main`.
2. Ninguna PR que toque `.github/workflows/` será revisada.

Y **se salta en silencio**: el paso queda en verde y no publica ningún output. Si un output
del action viene vacío, lo primero es buscar `workflow validation` en el log del paso, no
depurar quien consume el output.

### Un check obligatorio que no reporta bloquea igual que uno en rojo

Por eso el salto de la revisión (etiqueta `skip-ai-review`, borradores) no está en el `if:`
del job sino dentro: el job siempre se ejecuta y siempre termina, revise o no.

### El revisor publica; el action no

En modo agente Claude tiene que llamar él mismo a `gh pr comment` y a
`mcp__github_inline_comment__create_inline_comment`. Esas herramientas van en
`--allowedTools` dentro de `claude_args`: con `track_progress: true`, el bloque `settings`
**no** llega al SDK y las peticiones acaban en denegaciones de permiso.

### El token de Claude falla de dos formas distintas

| Síntoma | Causa |
|---|---|
| `401 Invalid bearer token`, con diez reintentos | token válido en forma pero rechazado |
| `Header 'Authorization' has invalid value`, falla en <100 ms | carácter ilegal: salto de línea o espacio |

Para guardar cualquier token sin corromperlo, sin portapapeles y sin dejarlo en el historial:

```bash
cat > /tmp/tok            # pega, Enter, Ctrl-D
tr -d '[:space:]' < /tmp/tok > /tmp/tok2
gh secret set NOMBRE < /tmp/tok2 && rm -f /tmp/tok /tmp/tok2
```

`pbpaste` no vale: si copias el comando para pegarlo, machacas el portapapeles. `read -r`
tampoco: se detiene en el primer salto de línea y trunca el token.

### `enforce_admins` no se aplica con el PUT

El `PUT` de protección de rama acepta `"enforce_admins": true`, responde 200 y lo deja en
`false`. Se activa con su endpoint dedicado, y hay que verificarlo después: sin eso la
protección parece puesta y no aplica al dueño del repositorio, que es el único que iba a
empujar. `setup-branch-protection.sh` lo hace y falla si no queda activo.

### `required_conversation_resolution` es incompatible con un revisor automático

Cada comentario en línea abre un hilo, y un hilo sin resolver deja la PR en `BLOCKED` aunque
el veredicto sea `approve` y todo esté verde. GitHub responde *"the base branch policy
prohibits the merge"* sin mencionar los hilos. Está desactivado a propósito: la barrera la
pone el check `Claude review`.

### El tablero necesita un token clásico con `project` y `read:org`

El `GITHUB_TOKEN` de Actions no alcanza a Projects, que vive fuera del repositorio. Y los
tokens *fine-grained* tampoco: su lista de permisos de cuenta no incluye `Projects`. Tiene
que ser un token clásico con `project` **y** `read:org` — sin el segundo, `gh` no puede
resolver si el dueño es usuario u organización y falla con `unknown owner type`.

Los nombres de las columnas se comparan sin distinguir mayúsculas, así que da igual
`In Progress` que `In progress`.

---

## Decisiones de estructura

### Por qué existe `:androidApp`

Desde AGP 9, `com.android.application` y `com.android.library` son incompatibles con el
plugin de Kotlin Multiplatform en el mismo módulo. `:shared` y `:composeApp` usan
`com.android.kotlin.multiplatform.library` con la configuración dentro de
`kotlin { androidLibrary { } }`, y `:androidApp` queda como punto de entrada con el
`MainActivity`, el `Application`, el manifest y los recursos. Sin lógica ni composables.

Dos detalles que no son evidentes:

- `:composeApp` necesita `androidResources { enable = true }`. Sin esa línea los recursos de
  Compose no se empaquetan y la app crashea al arrancar ([CMP-9547](https://youtrack.jetbrains.com/issue/CMP-9547)).
- `:androidApp` **no** aplica `kotlin-android`: AGP 9 trae soporte de Kotlin integrado y
  aplicarlo da conflicto.

### GitLive fijado en 2.5.0

La release 2.6.0 está incompleta en Maven Central: `firebase-auth` y `firebase-firestore` se
publicaron, pero el módulo raíz `firebase-app` no, y los POM lo declaran como dependencia.
Antes de subir la versión, comprobar que existe:

```bash
curl -s https://repo1.maven.org/maven2/dev/gitlive/firebase-app/maven-metadata.xml | grep '<version>'
```

El BOM de Firebase va en `androidMain` de `:shared` como `api`, no como `implementation`: los
POM de GitLive declaran las dependencias de Google sin versión y esperan que el consumidor
aporte el BOM, y la restricción tiene que propagarse a `:composeApp` y `:androidApp`.

### Los tests de Kotlin/Native están desactivados

El linker no encuentra los frameworks de Firebase para iOS, que aporta Xcode vía SPM. La
solución real es separar `:shared` en `:domain` (Kotlin puro, testeable en todas las
plataformas) y `:data`. Está pendiente.

---

## Deuda conocida

- Restringir la API key de iOS en Google Cloud Console al bundle id de la app.
- Separar `:shared` en `:domain` y `:data` para recuperar los tests de iOS.
- Volver a añadir Analytics y Crashlytics **con su plugin de Gradle**: sin él falta el build
  id y la app crashea al arrancar.
- Fijar en CI los nombres exactos de las tareas de test en lugar de usar `check`.
