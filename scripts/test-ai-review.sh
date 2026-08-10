#!/usr/bin/env bash
# Valida el revisor de Claude por los dos lados: que rechace lo malo y apruebe lo bueno.
#
#   ./scripts/test-ai-review.sh          PR mala   -> debe salir request_changes
#   ./scripts/test-ai-review.sh --good   PR buena  -> debe salir approve
#   ./scripts/test-ai-review.sh --clean  cierra ambas PRs y borra las ramas
#
# Las dos mitades importan. Un revisor que rechaza absolutamente todo pasa la
# primera prueba con nota y no sirve para nada.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

GREEN=$'\033[32m'; YELLOW=$'\033[33m'; BOLD=$'\033[1m'; OFF=$'\033[0m'
BRANCH="test/ai-review"
GOOD_BRANCH="test/ai-review-ok"
BAD_FILE="shared/src/commonMain/kotlin/com/kitchenai/shared/domain/usecase/BadUseCase.kt"
GOOD_SRC="shared/src/commonMain/kotlin/com/kitchenai/shared/core/AppResultExt.kt"
GOOD_TEST="shared/src/commonTest/kotlin/com/kitchenai/shared/core/AppResultExtTest.kt"

if [ "${1:-}" = "--clean" ]; then
    for b in "$BRANCH" "$GOOD_BRANCH"; do
        gh pr close "$b" --delete-branch 2>/dev/null || true
    done
    git checkout main
    for b in "$BRANCH" "$GOOD_BRANCH"; do
        git branch -D "$b" 2>/dev/null || true
    done
    printf '%s✓%s Ramas y PRs de prueba eliminadas\n' "$GREEN" "$OFF"
    exit 0
fi

git diff --quiet && git diff --cached --quiet || {
    echo "Tienes cambios sin commitear. Guárdalos antes de lanzar la prueba."
    exit 1
}

if [ "${1:-}" = "--good" ]; then
    git checkout main
    git pull --ff-only
    git checkout -b "$GOOD_BRANCH"

    mkdir -p "$(dirname "$GOOD_SRC")" "$(dirname "$GOOD_TEST")"

    cat > "$GOOD_SRC" <<'KT'
package com.kitchenai.shared.core

/**
 * Devuelve el valor si la operación fue bien, o el resultado de [fallback] si no.
 *
 * Existe para que quien consume un [AppResult] no tenga que escribir un `when`
 * cuando lo único que necesita es un valor por defecto.
 */
inline fun <T> AppResult<T>.getOrElse(fallback: (AppError) -> T): T =
    when (this) {
        is AppResult.Success -> data
        is AppResult.Failure -> fallback(error)
    }
KT

    cat > "$GOOD_TEST" <<'KT'
package com.kitchenai.shared.core

import kotlin.test.Test
import kotlin.test.assertEquals

class AppResultExtTest {
    @Test
    fun `getOrElse devuelve el valor de un Success sin llamar al fallback`() {
        var llamadas = 0
        val result: AppResult<Int> = AppResult.Success(42)

        val valor =
            result.getOrElse {
                llamadas++
                0
            }

        assertEquals(42, valor)
        assertEquals(0, llamadas)
    }

    @Test
    fun `getOrElse aplica el fallback y recibe el error de un Failure`() {
        val error = AppError.NotFound("recipe")
        val result: AppResult<Int> = AppResult.Failure(error)

        val valor = result.getOrElse { if (it == error) -1 else -2 }

        assertEquals(-1, valor)
    }
}
KT

    git add "$GOOD_SRC" "$GOOD_TEST"
    git commit -m "feat(core): añadir AppResult.getOrElse"
    git push -u origin "$GOOD_BRANCH"

    gh pr create \
        --base main \
        --title "feat(core): añadir AppResult.getOrElse" \
        --body "PR de prueba del camino feliz del Paso 3. Debe recibir \`approve\`.

Añade una extensión de \`AppResult\` para obtener el valor con un fallback, en vez
de repetir el mismo \`when\` en cada consumidor.

- Kotlin puro en \`core\`: sin Firebase, sin Android, sin iOS
- Cubierta por tests en \`commonTest\`, incluida la rama de error
- No toca ninguna otra parte del proyecto

Si el revisor rechaza esto, está rechazando por sistema y hay que revisar \`CLAUDE.md\`."

    printf '\n%s%sQué esperar%s\n' "$BOLD" "$GREEN" "$OFF"
    cat <<'NEXT'
  · veredicto approve
  · etiqueta ai-review:approved puesta
  · el check "Claude review" en verde
  · ningún comentario inline, o a lo sumo alguno no bloqueante
NEXT
    exit 0
fi

git checkout main
git pull --ff-only
git checkout -b "$BRANCH"

mkdir -p "$(dirname "$BAD_FILE")"
cat > "$BAD_FILE" <<'KT'
package com.kitchenai.shared.domain.usecase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fichero de prueba del revisor automático. NO es código real.
 *
 * Viola, a propósito:
 *  1. domain importando dev.gitlive (regla de dependencias)
 *  2. Dispatchers hardcodeado en vez de DispatcherProvider
 *  3. clave de API hardcodeada
 *  4. excepción tragada en un catch vacío
 *  5. caso de uso sin ningún test
 *
 * Está formateado correctamente a propósito: ktlint y detekt deben pasar, para que
 * lo único que pueda hacer fallar la PR sea el criterio del revisor.
 */
class BadUseCase {
    private val apiKey = "AIzaSyD-fake-hardcoded-key-1234567890"

    suspend operator fun invoke(id: String): String =
        withContext(Dispatchers.Default) {
            try {
                Firebase.firestore.collection("recipes").document(id).get().toString()
            } catch (e: Exception) {
                ""
            }
        }
}
KT

git add "$BAD_FILE"
git commit -m "test: PR deliberadamente mala para validar el revisor"
git push -u origin "$BRANCH"

gh pr create \
    --base main \
    --title "test: validar el revisor de IA" \
    --body "PR de prueba del Paso 3. Debe recibir \`request_changes\`.

Introduce cinco violaciones de \`CLAUDE.md\` en un solo fichero:

- \`domain\` importando \`dev.gitlive\`
- \`Dispatchers.Default\` hardcodeado
- clave de API en el código
- \`catch\` que se traga la excepción
- caso de uso nuevo sin test

Si el revisor aprueba esto, hay que afinar \`CLAUDE.md\`."

printf '\n%s%sQué esperar%s\n' "$BOLD" "$GREEN" "$OFF"
cat <<'NEXT'
  · 4-5 comentarios inline en las líneas concretas
  · veredicto request_changes
  · el check "Claude review" en rojo
  · SIN la etiqueta ai-review:approved

Sigue la ejecución con:

  gh pr checks --watch
  gh pr view --comments

Cuando termines, limpia con:

  ./scripts/test-ai-review.sh --clean
NEXT
