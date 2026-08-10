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

```bash
cd ~/Documents/kitchenAI

# Clave de la API de Anthropic (console.anthropic.com → API Keys)
gh secret set ANTHROPIC_API_KEY

# Opcional pero recomendado: PAT de un usuario máquina para que la IA
# pueda emitir un "Approve" formal. GitHub no permite auto-aprobarse,
# por eso no vale GITHUB_TOKEN.
gh secret set AI_REVIEWER_TOKEN

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
        ├─ 2. devuelve JSON validado:
        │     { verdict, summary, blocking_findings[], risk }
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

**`--json-schema` en vez de parsear texto.** El veredicto llega validado contra el esquema,
así que el paso siguiente puede ser un `if` de shell fiable en lugar de un grep frágil.

**`use_sticky_comment: true`.** En cada push se actualiza el mismo comentario en vez de
acumular veinte. Los comentarios inline sí se renuevan por commit.

**`concurrency` con `cancel-in-progress`.** Si haces tres pushes seguidos sólo se paga la
última revisión. Con tokens de por medio esto importa.

**`--allowedTools` restringido.** El revisor puede leer y hacer `git diff`, `gh pr view`,
`gh issue view` — no puede editar ficheros ni hacer push. Un revisor que puede modificar
el código que revisa no es un revisor.

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

## 3.4 Coste

Una revisión típica de una PR de 200–400 líneas con Sonnet ronda los 15–40 céntimos.
Palancas si se dispara:

- `--max-turns 30` → bájalo a 15 para PRs pequeñas.
- `types: [opened, ready_for_review]` (sin `synchronize`) → una revisión por PR en vez de una por push.
- `paths-ignore: ['**/*.md', 'docs/**']` en el trigger.
- Filtra por tamaño de diff y salta la revisión si son menos de N líneas.

Pon un **límite de gasto mensual** en console.anthropic.com → Billing antes de dejarlo suelto.

---

## ✅ Checklist Paso 3

- [ ] `gh secret list` muestra `ANTHROPIC_API_KEY` (y `AI_REVIEWER_TOKEN` si lo usas)
- [ ] La PR de prueba mala recibe `request_changes` y comentarios inline en las líneas correctas
- [ ] El job `Claude review` aparece en rojo en `gh pr checks`
- [ ] Una PR correcta recibe `approve`, la etiqueta `ai-review:approved` y check en verde
- [ ] El comentario resumen se actualiza (no se duplica) al hacer un segundo push
- [ ] `CLAUDE.md` refleja las reglas que de verdad quieres que se cumplan

Cuando esté, dime **"Paso 3 OK"** y montamos el Paso 2 (GitHub Projects) y el Paso 4
(protección de rama + auto-merge), que ya dependen de que estos dos checks existan.
