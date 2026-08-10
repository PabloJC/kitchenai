#!/usr/bin/env bash
# Crea una PR deliberadamente mala para comprobar que el revisor de Claude la rechaza.
#
#   ./scripts/test-ai-review.sh          crea la rama y abre la PR
#   ./scripts/test-ai-review.sh --clean  cierra la PR y borra la rama
#
# El fichero que introduce viola cinco reglas de CLAUDE.md a la vez. Si el revisor
# aprueba esto, es que CLAUDE.md no está haciendo su trabajo.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

GREEN=$'\033[32m'; YELLOW=$'\033[33m'; BOLD=$'\033[1m'; OFF=$'\033[0m'
BRANCH="test/ai-review"
BAD_FILE="shared/src/commonMain/kotlin/com/kitchenai/shared/domain/usecase/BadUseCase.kt"

if [ "${1:-}" = "--clean" ]; then
    gh pr close "$BRANCH" --delete-branch 2>/dev/null || true
    git checkout main
    git branch -D "$BRANCH" 2>/dev/null || true
    printf '%s✓%s Rama y PR de prueba eliminadas\n' "$GREEN" "$OFF"
    exit 0
fi

git diff --quiet && git diff --cached --quiet || {
    echo "Tienes cambios sin commitear. Guárdalos antes de lanzar la prueba."
    exit 1
}

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
