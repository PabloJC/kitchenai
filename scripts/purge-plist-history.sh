#!/usr/bin/env bash
# Elimina iosApp/GoogleService-Info.plist de TODO el historial del repositorio.
#
#   ./scripts/purge-plist-history.sh
#
# Reescribe la historia: todos los SHA cambian. Antes de ejecutarlo, cierra las
# PRs abiertas y borra las ramas de prueba; después, cualquier clon existente
# queda inservible y hay que volver a clonar.
#
# Hace copia de seguridad completa antes de tocar nada.
set -euo pipefail

GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; BOLD=$'\033[1m'; OFF=$'\033[0m'

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TARGET="iosApp/GoogleService-Info.plist"
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="${ROOT}/../kitchenai-backup-${STAMP}.git"
SAVED="/tmp/GoogleService-Info-${STAMP}.plist"

# ------------------------------------------------------------------ #
# 0. Precondiciones
# ------------------------------------------------------------------ #
command -v git-filter-repo >/dev/null 2>&1 || {
    printf '%s✗%s Falta git-filter-repo. Instálalo con:\n\n    brew install git-filter-repo\n\n' "$RED" "$OFF"
    exit 1
}

git diff --quiet && git diff --cached --quiet || {
    printf '%s✗%s Tienes cambios sin commitear. Guárdalos antes de reescribir el historial.\n' "$RED" "$OFF"
    exit 1
}

BRANCH="$(git branch --show-current)"
[ "$BRANCH" = "main" ] || {
    printf '%s✗%s Ejecútalo desde main (estás en %s).\n' "$RED" "$OFF" "$BRANCH"
    exit 1
}

REMOTE="$(git remote get-url origin)"

OTHER_BRANCHES="$(git branch --format='%(refname:short)' | grep -v '^main$' || true)"
if [ -n "$OTHER_BRANCHES" ]; then
    printf '%s!%s Hay ramas locales además de main:\n%s\n\n' "$YELLOW" "$OFF" "$OTHER_BRANCHES"
    printf 'Sus SHA quedarán huérfanos. Bórralas antes o acepta perderlas.\n'
    read -r -p "¿Continuar de todos modos? [s/N] " ok
    [ "$ok" = "s" ] || exit 1
fi

printf '\n%s%sReescritura del historial de %s%s\n\n' "$BOLD" "$YELLOW" "$(basename "$ROOT")" "$OFF"
printf 'Se eliminará de todos los commits: %s\n' "$TARGET"
printf 'Copia de seguridad en:            %s\n' "$BACKUP"
printf 'Remoto:                           %s\n\n' "$REMOTE"
read -r -p "Escribe REESCRIBIR para confirmar: " confirm
[ "$confirm" = "REESCRIBIR" ] || { echo "Cancelado."; exit 1; }

# ------------------------------------------------------------------ #
# 1. Copia de seguridad y rescate del fichero local
# ------------------------------------------------------------------ #
git clone --mirror . "$BACKUP" >/dev/null 2>&1
printf '%s✓%s Copia de seguridad creada\n' "$GREEN" "$OFF"

if [ -f "$TARGET" ]; then
    cp "$TARGET" "$SAVED"
    printf '%s✓%s Fichero local guardado en %s\n' "$GREEN" "$OFF" "$SAVED"
fi

# ------------------------------------------------------------------ #
# 2. La reescritura
#
# filter-repo elimina el remoto a propósito, para que no fuerces un push
# por accidente sobre un repositorio equivocado. Lo volvemos a poner.
# ------------------------------------------------------------------ #
git filter-repo --path "$TARGET" --invert-paths --force
printf '%s✓%s Historial reescrito\n' "$GREEN" "$OFF"

git remote add origin "$REMOTE" 2>/dev/null || git remote set-url origin "$REMOTE"

# ------------------------------------------------------------------ #
# 3. Restaurar el fichero en el árbol de trabajo, ya ignorado por git
# ------------------------------------------------------------------ #
if [ -f "$SAVED" ]; then
    cp "$SAVED" "$TARGET"
    rm -f "$SAVED"
    printf '%s✓%s Fichero restaurado en %s (ahora ignorado por git)\n' "$GREEN" "$OFF" "$TARGET"
fi

git status --porcelain --ignored "$TARGET" | grep -q '^!!' \
    && printf '%s✓%s Confirmado: git lo está ignorando\n' "$GREEN" "$OFF" \
    || printf '%s!%s Revisa el .gitignore: el fichero NO está siendo ignorado\n' "$YELLOW" "$OFF"

# ------------------------------------------------------------------ #
cat <<NEXT

${BOLD}${GREEN}Falta empujarlo${OFF}

  git push --force --all
  git push --force --tags

${BOLD}Y una advertencia que conviene no ignorar${OFF}

Un force-push NO borra los objetos antiguos de GitHub. Los commits quedan
inalcanzables desde cualquier rama, pero siguen siendo accesibles por su SHA
—y las PRs cerradas los enlazan— hasta que GitHub los recolecta, cosa que no
garantiza ni programa.

Para una eliminación real hay dos caminos:

  · Pedirlo a GitHub Support (https://support.github.com), que ejecuta el gc.
  · Borrar el repositorio remoto y volver a crearlo desde este clon ya limpio.
    Es lo único inmediato y seguro. Perderías secrets, PRs cerradas e
    instalación de la GitHub App, que habría que rehacer.

Mientras tanto, restringe la API key en Google Cloud Console:
Credentials -> la key de iOS -> Application restrictions -> iOS apps -> tu bundle id.
Eso la deja inservible fuera de tu app, esté donde esté publicada.

Copia de seguridad íntegra en:
  $BACKUP
NEXT
