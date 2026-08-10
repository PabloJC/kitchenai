# Paso 3 — AI Code Review automático

> Objetivo: cada PR recibe una revisión de Claude con comentarios inline y un veredicto
> estructurado que sirve de *required status check* para el auto-merge del Paso 4.

Ficheros que intervienen:

| Fichero | Rol |
|---|---|
| `.github/workflows/ai-code-review.yml` | Ejecuta `anthropics/claude-code-action@v1` en cada PR |
| `.github/workflows/ci.yml` | Build + tests + análisis estático (check `CI passed`) |
| `CLAUDE.md` | Reglas contra las que revisa el agente. **Es el fichero que hay que afinar** |
| `.github/pull_request_template.md` | Da contexto al revisor (issue, alcance, riesgos) |

---

## 3.1 Secrets

El revisor se autentica con el **token OAuth de la suscripción**, no con una clave de API:
así el consumo va contra tu plan de Claude en vez de facturarse aparte.

```bash
cd ~/Documents/kitchenAI

claude setup-token          # abre el navegador e imprime un sk-ant-oat01-...
```

Guardar ese token es más delicado de lo que parece. Un salto de línea o un espacio de más
lo invalidan, y el error que produces es distinto según por dónde se rompa:

| Síntoma en el log | Causa |
|---|---|
| `401 Invalid bearer token`, reintenta 10 veces | token válido en forma pero rechazado (caducado o de otra cuenta) |
| `Header 'Authorization' has invalid value`, falla en <100 ms | el valor tiene un carácter ilegal: salto de línea, espacio, comilla |

Para guardarlo sin corromperlo, sin pasar por el portapapeles y sin dejarlo en el historial:

```bash
cat > /tmp/raw            # pega el token, Enter, Ctrl-D
tr -d '[:space:]' < /tmp/raw > /tmp/tok
wc -c < /tmp/tok && head -c 13 /tmp/tok && echo    # debe empezar por sk-ant-oat01-

gh secret set CLAUDE_CODE_OAUTH_TOKEN < /tmp/tok
rm -f /tmp/raw /tmp/tok
```

`pbpaste` **no** sirve aquí: si copias el comando desde el chat o desde un documento para
pegarlo en la terminal, machacas el portapapeles y acabas leyendo el propio comando.
Y `read -r` tampoco, porque se detiene en el primer salto de línea y trunca el token
cuando el terminal lo copió partido en varias líneas.

También hace falta la **GitHub App de Claude** instalada en el repo
(<https://github.com/apps/claude>): el action intercambia un token OIDC por un token de app,
y sin la app instalada devuelve 401 antes incluso de llegar a Anthropic.

```bash
gh secret set AI_REVIEWER_TOKEN     # opcional, ver abajo
gh secret list
```

**Sobre `AI_REVIEWER_TOKEN`** — crea un *fine-grained PAT* desde una cuenta distinta a la tuya
(o una GitHub App), con acceso sólo a este repo y permisos:

- `Pull requests`: Read and write
- `Contents`: Read-only
- `Metadata`: Read-only

Invita a ese usuario al repo como *Write*. Sin este token el workflow sigue funcionando:
comenta y pone la etiqueta `ai-review:approved`, pero no emite un Approve que cuente para
las reglas de protección de rama.

---

## 3.2 Cómo funciona el workflow

```
PR abierta / actualizada
   │
   ├─ ci.yml ─────────► detekt · ktlint · tests · assembleDebug · framework iOS
   │                     └─► check agregado "CI passed"
   │
   └─ ai-code-review.yml
        │
        ├─ 1. claude-code-action lee el diff + CLAUDE.md + la issue enlazada
        │     y deja comentarios inline en las líneas problemáticas
        │
        ├─ 2. termina su respuesta con la línea centinela
        │     VEREDICTO: approve | request_changes
        │
        ├─ 3. gh pr review --approve  |  --request-changes
        │     + etiqueta ai-review:approved
        │
        └─ 4. exit 1 si verdict != approve
              └─► el check "AI Code Review / Claude review" queda en rojo
```

Los dos checks agregados — **`CI passed`** y **`Claude review`** — son los únicos que marcarás
como *required* en el Paso 4. Así puedes añadir o quitar jobs internos sin tocar la
configuración de la rama protegida.

### Decisiones del diseño

**El veredicto viaja como línea centinela, no como `structured_output`.** La idea original
era `--json-schema`, pero el output venía siempre vacío. Resultó ser un síntoma, no la
causa (ver más abajo); aun así el centinela `VEREDICTO: approve|request_changes` extraído
con `grep` del `execution_file` es inmune al formato de la transcripción y no depende de
que el action rellene un output concreto.

**Un veredicto ausente no es un rechazo.** Si el `grep` no encuentra el centinela, el paso
falla con "revisión no concluyente" en vez de tratarlo como `request_changes`. Sin esa
distinción, cualquier avería del revisor se disfraza de hallazgo real y acabas depurando
el código equivocado.

**El revisor publica, el action no.** En modo agente —el que se activa con un `prompt`
explícito y un evento `pull_request`— el action **no publica nada por su cuenta**. Es Claude
quien tiene que llamar a `gh pr comment` y a
`mcp__github_inline_comment__create_inline_comment` (con `confirmed: true`), así que ambas
herramientas tienen que estar en la lista de permitidas *y* el prompt tiene que pedirlo
explícitamente. Con una lista de permisos de sólo lectura, el revisor emite un veredicto
perfectamente válido y no lo publica en ningún sitio: la PR se queda sin un solo comentario.

**`track_progress: true`, no `use_sticky_comment`.** `use_sticky_comment` sólo aplica a los
modos que responden a `@claude`; en modo agente se ignora en silencio. `track_progress`
mantiene un comentario de seguimiento que se actualiza en cada push y además inyecta el
contexto completo de la PR.

**`concurrency` con `cancel-in-progress`.** Si haces tres pushes seguidos sólo se paga la
última revisión. Con tokens de por medio esto importa.

**Las herramientas permitidas van en `--allowedTools`; `settings` sólo deniega.** El revisor
puede leer, hacer `git diff` y comentar — no puede editar ficheros ni hacer push. Un revisor
que puede modificar el código que revisa no es un revisor.

El reparto entre los dos sitios no es estético. Con `track_progress: true`, el action
construye su propia lista de `allowedTools` y **`settings.permissions.allow` no llega al
SDK**: el log lo enseña sin ambigüedad.

```
"allowedTools": ["Glob","Grep","LS","Read","mcp__github_comment__update_claude_comment", ...]
"permission_denials_count": 4
```

Cuatro denegaciones = Claude intentando comentar cuatro veces con herramientas que creía
tener. La lista de `--allowedTools` sí se suma a la que aporta `track_progress`. Las
denegaciones de `settings` sí se respetan, así que ahí se queda el `deny`.

Y las comillas importan: `--allowedTools "a,Bash(x:*),b"` se parsea como **un solo
argumento**, paréntesis incluidos.

**Evento `pull_request`, no `pull_request_target`.** `pull_request_target` ejecuta el
workflow de `main` con secrets sobre código no confiable: en un repo con forks es un vector
de exfiltración de la API key. Con `pull_request` las PRs desde forks no tendrán secrets y
el job se saltará — correcto para este proyecto, donde todas las ramas son internas.

**Escape hatch.** Etiqueta `skip-ai-review` en una PR para saltarse la revisión (útil para
cambios de sólo documentación o para desbloquearte si la API está caída).

---

## 3.3 Puesta en marcha y prueba

```bash
git add .github CLAUDE.md scripts docs
git commit -m "chore(ci): AI code review + pipeline de CI"
git push -u origin main
```

Prueba con una PR que **debe** ser rechazada, para comprobar que el revisor muerde:

```bash
git checkout -b test/ai-review

mkdir -p shared/src/commonMain/kotlin/com/kitchenai/shared/domain/usecase
cat > shared/src/commonMain/kotlin/com/kitchenai/shared/domain/usecase/BadUseCase.kt <<'KT'
package com.kitchenai.shared.domain.usecase

import dev.gitlive.firebase.Firebase          // ❌ domain importando Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers         // ❌ dispatcher hardcodeado
import kotlinx.coroutines.withContext

class BadUseCase {
    private val apiKey = "AIzaSyD-fake-hardcoded-key-1234567890"  // ❌ secreto

    suspend operator fun invoke(id: String): String = withContext(Dispatchers.Default) {
        try {
            Firebase.firestore.collection("recipes").document(id).get().toString()
        } catch (e: Exception) {
            ""                                  // ❌ excepción tragada
        }
    }
}
KT

git add -A
git commit -m "test: PR deliberadamente mala para validar el revisor"
git push -u origin test/ai-review

gh pr create --base main --title "test: validar AI review" \
  --body "PR de prueba. Debe recibir request_changes." --draft=false
```

Sigue la ejecución:

```bash
gh run watch
gh pr checks
gh pr view --comments
```

**Resultado esperado:** 4–5 comentarios inline (import de Firebase en domain, dispatcher
hardcodeado, clave hardcodeada, catch vacío, sin test), veredicto `request_changes`,
job en rojo y **sin** etiqueta `ai-review:approved`.

Después borra la rama:

```bash
gh pr close --delete-branch
```

Y valida el camino feliz con una PR trivial y correcta (por ejemplo añadir un test a
`AppResult`): debe salir `approve`, etiqueta puesta y check en verde.

---

## 3.4 La restricción que más tiempo cuesta: validación del workflow

> El fichero del workflow tiene que ser **idéntico** al de la rama por defecto, o el action
> no se ejecuta.

```
Warning: Skipping action due to workflow validation: Workflow validation failed.
The workflow file must exist and have identical content to the version on the
repository's default branch.
```

Es una medida de seguridad evidente en cuanto la ves: si no existiera, cualquiera podría
abrir una PR que modificase el workflow para que Claude ejecutase lo que quisiera con los
secrets del repositorio.

Tiene dos consecuencias prácticas y permanentes:

1. **No puedes iterar sobre el workflow en una rama.** Cada cambio en
   `.github/workflows/ai-code-review.yml` hay que llevarlo a `main` antes de poder probarlo:

   ```bash
   git add .github/workflows/ai-code-review.yml && git commit -m "chore(ci): ..."
   git checkout main
   git checkout <rama> -- .github/workflows/ai-code-review.yml
   git commit -m "chore(ci): ..." && git push
   git checkout <rama> && git merge main && git push
   ```

2. **Ninguna PR que toque `.github/workflows/` será revisada por Claude** hasta que ese
   cambio esté en `main`. No es configurable.

Y lo importante: **el action se salta en silencio**. El paso aparece en verde, pero no
publica ningún output — ni `conclusion`, ni `execution_file`, ni `structured_output`.
Todo el rato que perdimos persiguiendo por qué `structured_output` venía vacío fue esto:
Claude no llegaba a ejecutarse. Si un output del action viene vacío, **lo primero** es
mirar el log del propio paso del action buscando `workflow validation`, no depurar el
paso que consume el output.

---

## 3.5 Coste

Una revisión típica de una PR de 200–400 líneas con Sonnet ronda los 15–40 céntimos.
Palancas si se dispara:

- `--max-turns 30` → bájalo a 15 para PRs pequeñas.
- `types: [opened, ready_for_review]` (sin `synchronize`) → una revisión por PR en vez de una por push.
- `paths-ignore: ['**/*.md', 'docs/**']` en el trigger.
- Filtra por tamaño de diff y salta la revisión si son menos de N líneas.

Pon un **límite de gasto mensual** en console.anthropic.com → Billing antes de dejarlo suelto.

---

## ✅ Checklist Paso 3

- [ ] `gh secret list` muestra `CLAUDE_CODE_OAUTH_TOKEN` (y `AI_REVIEWER_TOKEN` si lo usas)
- [ ] La GitHub App de Claude está instalada en el repositorio
- [ ] `.github/workflows/ai-code-review.yml` es idéntico en la rama y en `main`
- [ ] La PR de prueba mala recibe `request_changes` y comentarios inline en las líneas correctas
- [ ] El job `Claude review` aparece en rojo en `gh pr checks`
- [ ] Una PR correcta recibe `approve`, la etiqueta `ai-review:approved` y check en verde
- [ ] El comentario resumen se actualiza (no se duplica) al hacer un segundo push
- [ ] `CLAUDE.md` refleja las reglas que de verdad quieres que se cumplan

Cuando esté, dime **"Paso 3 OK"** y montamos el Paso 2 (GitHub Projects) y el Paso 4
(protección de rama + auto-merge), que ya dependen de que estos dos checks existan.
