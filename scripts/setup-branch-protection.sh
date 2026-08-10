#!/usr/bin/env bash
# Paso 4 — protege `main` y activa el auto-merge.
#
#   ./scripts/setup-branch-protection.sh            aplica la configuración
#   ./scripts/setup-branch-protection.sh --show     muestra la configuración actual
#   ./scripts/setup-branch-protection.sh --remove   quita la protección
#
# Idempotente: puedes ejecutarlo tantas veces como quieras.
set -euo pipefail

GREEN=$'\033[32m'; YELLOW=$'\033[33m'; BOLD=$'\033[1m'; OFF=$'\033[0m'

REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
BRANCH="main"

case "${1:-}" in
    --show)
        gh api "repos/$REPO/branches/$BRANCH/protection" 2>/dev/null \
            || echo "La rama $BRANCH no tiene protección."
        exit 0
        ;;
    --remove)
        gh api -X DELETE "repos/$REPO/branches/$BRANCH/protection"
        printf '%s✓%s Protección eliminada de %s\n' "$YELLOW" "$OFF" "$BRANCH"
        exit 0
        ;;
esac

printf '%s%sConfigurando %s%s\n\n' "$BOLD" "$GREEN" "$REPO" "$OFF"

# ------------------------------------------------------------------ #
# 1. Opciones del repositorio.
#
# Sólo squash: cada issue entra en `main` como un único commit, así el
# historial de `main` es la lista de tareas completadas y `git revert`
# de una tarea es un solo comando.
# ------------------------------------------------------------------ #
gh repo edit "$REPO" \
    --enable-auto-merge \
    --delete-branch-on-merge \
    --enable-squash-merge \
    --enable-merge-commit=false \
    --enable-rebase-merge=false

printf '%s✓%s Auto-merge, squash-only y borrado de rama al mergear\n' "$GREEN" "$OFF"

# ------------------------------------------------------------------ #
# 2. Protección de la rama.
#
# `strict: false` a propósito: con `true`, cada merge invalida el resto
# de PRs abiertas y hay que actualizarlas y re-revisarlas una por una.
# Eso serializa el desarrollo, que es justo lo contrario de poder
# paralelizar issues independientes. El precio es que dos PRs verdes
# por separado pueden romper `main` juntas; el CI de `push` lo detecta.
#
# `required_approving_review_count: 0` porque GitHub no deja aprobar tus
# propias PRs. La revisión la aporta el check `Claude review`, no un
# humano. Si más adelante configuras AI_REVIEWER_TOKEN con un usuario
# máquina, súbelo a 1.
#
# `enforce_admins: false` para que puedas desbloquearte si el revisor se
# cae. Es una puerta trasera consciente: úsala y déjalo anotado en la PR.
# ------------------------------------------------------------------ #
if ! gh api -X PUT "repos/$REPO/branches/$BRANCH/protection" --input - <<'JSON'
{
  "required_status_checks": {
    "strict": false,
    "contexts": ["CI passed", "Claude review"]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": 0,
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true,
  "block_creations": false,
  "lock_branch": false,
  "allow_fork_syncing": false
}
JSON
then
    printf '\n%s✗ No se pudo aplicar la protección de rama.%s\n\n' "$YELLOW" "$OFF"
    cat <<'WHY'
Si el error es un 403 con "Upgrade to GitHub Pro or make this repository public",
no hay nada que arreglar aquí: la protección de rama no existe para repositorios
privados en plan Free. GitHub acepta la llamada y no aplica nada.

Salidas:
  · hacer el repositorio público  -> gh repo edit --visibility public
  · pasar a GitHub Pro
  · quedarte sin protección       -> el workflow auto-merge.yml cubre el merge
                                     automático; ver docs/step-04-branch-protection.md
WHY
    exit 1
fi

printf '%s✓%s Protección aplicada a %s\n\n' "$GREEN" "$OFF" "$BRANCH"

cat <<'NEXT'
Lo que acaba de cambiar:

  · No se puede empujar directamente a main. Todo pasa por PR.
  · Una PR sólo se puede mergear con "CI passed" y "Claude review" en verde.
  · Sólo squash. Historial lineal. Sin force-push ni borrado de main.
  · Los hilos de conversación abiertos bloquean el merge.

Flujo de una tarea a partir de ahora:

  git checkout -b feat/12-listado-recetas
  ...
  gh pr create --fill
  gh pr merge --squash --auto        <- se mergea solo cuando los checks pasen

Comprueba que funciona intentando romperlo:

  git checkout main
  echo "// prueba" >> README.md && git commit -am "chore: probar protección"
  git push        <- debe ser rechazado por el remoto

Y deshaz la prueba:

  git reset --hard origin/main
NEXT
