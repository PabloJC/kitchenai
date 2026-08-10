#!/usr/bin/env bash
# Crea la estructura de paquetes de clean architecture para KitchenAI.
# Idempotente: se puede ejecutar varias veces sin romper nada.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PKG_SHARED="com/kitchenai/shared"
PKG_UI="com/kitchenai/ui"
PKG_APP="com/kitchenai/app"

say() { printf '  \033[32m✓\033[0m %s\n' "$1"; }

mk() {
  mkdir -p "$1"
  # .gitkeep para que git conserve el árbol vacío
  [ -z "$(ls -A "$1")" ] && touch "$1/.gitkeep"
}

echo "→ Módulo :shared (domain + data, sin UI)"
for sset in commonMain commonTest androidMain iosMain; do
  BASE="shared/src/$sset/kotlin/$PKG_SHARED"
  mk "$BASE/domain/model"
  mk "$BASE/domain/repository"
  mk "$BASE/domain/usecase"
  mk "$BASE/data/remote/firebase"
  mk "$BASE/data/local"
  mk "$BASE/data/dto"
  mk "$BASE/data/repository"
  mk "$BASE/di"
  mk "$BASE/core"
done
say "shared/src/{commonMain,commonTest,androidMain,iosMain}"

echo "→ Módulo :composeApp (presentation, biblioteca KMP)"
# Sin androidMain: el entry point de Android vive en :androidApp (AGP 9).
for sset in commonMain commonTest iosMain; do
  BASE="composeApp/src/$sset/kotlin/$PKG_UI"
  mk "$BASE/presentation"
  mk "$BASE/navigation"
  mk "$BASE/designsystem/theme"
  mk "$BASE/designsystem/component"
  mk "$BASE/di"
done
say "composeApp/src/{commonMain,commonTest,iosMain}"

echo "→ Módulo :androidApp (sólo entry point)"
mk "androidApp/src/main/kotlin/$PKG_APP"
mk "androidApp/src/main/res/values"
say "androidApp/src/main"

echo "→ Primitivas de core"
CORE="shared/src/commonMain/kotlin/$PKG_SHARED/core"
if [ ! -f "$CORE/AppResult.kt" ]; then
cat > "$CORE/AppResult.kt" <<'KT'
package com.kitchenai.shared.core

/**
 * Resultado explícito de una operación de dominio.
 * Ninguna capa debe propagar excepciones a través de sus fronteras.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

sealed class AppError(open val cause: Throwable? = null) {
    data class Network(override val cause: Throwable? = null) : AppError(cause)
    data class Unauthorized(override val cause: Throwable? = null) : AppError(cause)
    data class NotFound(val resource: String) : AppError()
    data class Validation(val field: String, val reason: String) : AppError()
    data class Unknown(override val cause: Throwable? = null) : AppError(cause)
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}
KT
say "AppResult.kt"
fi

if [ ! -f "$CORE/DispatcherProvider.kt" ]; then
cat > "$CORE/DispatcherProvider.kt" <<'KT'
package com.kitchenai.shared.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Inyectable para que los tests puedan sustituir los dispatchers. */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val io: CoroutineDispatcher get() = Dispatchers.Default
    override val default: CoroutineDispatcher get() = Dispatchers.Default
}
KT
say "DispatcherProvider.kt"
fi

if [ ! -f "shared/src/commonMain/kotlin/$PKG_SHARED/domain/usecase/UseCase.kt" ]; then
cat > "shared/src/commonMain/kotlin/$PKG_SHARED/domain/usecase/UseCase.kt" <<'KT'
package com.kitchenai.shared.domain.usecase

import com.kitchenai.shared.core.AppResult

/** Un caso de uso = una operación. Sin dependencias de framework. */
fun interface UseCase<in P, out R> {
    suspend operator fun invoke(params: P): AppResult<R>
}

object NoParams
KT
say "UseCase.kt"
fi

echo
echo "Estructura lista. Recuerda añadir a settings.gradle.kts:"
echo '    include(":shared")'
echo '    include(":composeApp")'
echo '    include(":androidApp")'
