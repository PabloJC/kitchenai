#!/usr/bin/env bash
# Paso 2 — crea el tablero de GitHub Projects y lo deja listo para el repositorio.
#
#   ./scripts/setup-project.sh          crea el tablero y sus campos
#   ./scripts/setup-project.sh --show   muestra el tablero y sus campos
#
# Idempotente: si el tablero ya existe, lo reutiliza y sólo añade lo que falte.
set -euo pipefail

GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; BOLD=$'\033[1m'; OFF=$'\033[0m'

TITLE="KitchenAI"
REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
OWNER="${REPO%%/*}"

# ------------------------------------------------------------------ #
# El scope `project` no viene con la autenticación por defecto de gh.
# ------------------------------------------------------------------ #
if ! gh auth status 2>&1 | grep -q "project"; then
    printf '%s!%s Tu token de gh no tiene el scope `project`. Añádelo con:\n\n' "$YELLOW" "$OFF"
    printf '    gh auth refresh -s project,read:project\n\n'
    exit 1
fi

PROJECT_NUMBER=$(gh project list --owner "$OWNER" --format json \
                 --jq ".projects[] | select(.title == \"$TITLE\") | .number" | head -1)

if [ "${1:-}" = "--show" ]; then
    [ -n "$PROJECT_NUMBER" ] || { echo "No existe ningún tablero '$TITLE'."; exit 1; }
    gh project view "$PROJECT_NUMBER" --owner "$OWNER"
    echo
    gh project field-list "$PROJECT_NUMBER" --owner "$OWNER"
    exit 0
fi

if [ -z "$PROJECT_NUMBER" ]; then
    PROJECT_NUMBER=$(gh project create --owner "$OWNER" --title "$TITLE" \
                     --format json --jq .number)
    printf '%s✓%s Tablero «%s» creado (número %s)\n' "$GREEN" "$OFF" "$TITLE" "$PROJECT_NUMBER"
else
    printf '%s·%s El tablero «%s» ya existe (número %s)\n' "$YELLOW" "$OFF" "$TITLE" "$PROJECT_NUMBER"
fi

# ------------------------------------------------------------------ #
# Columnas.
#
# Projects crea el campo Status con Todo / In Progress / Done. Nosotros
# necesitamos además «In review», y con la mayúscula exacta que usa el
# workflow: los nombres se comparen literalmente.
# ------------------------------------------------------------------ #
FIELDS=$(gh project field-list "$PROJECT_NUMBER" --owner "$OWNER" --format json)
OPCIONES=$(echo "$FIELDS" | jq -r '.fields[] | select(.name == "Status") | .options[]?.name')

printf '\n%sColumnas actuales:%s\n' "$BOLD" "$OFF"
echo "$OPCIONES" | sed 's/^/  · /'

FALTAN=""
for C in "Todo" "In progress" "In review" "Done"; do
    echo "$OPCIONES" | grep -qxF "$C" || FALTAN="$FALTAN\n  · $C"
done

if [ -n "$FALTAN" ]; then
    printf '\n%s!%s Faltan columnas, y la API de Projects no permite editar las\n' "$YELLOW" "$OFF"
    printf '   opciones de un campo Status existente. Hay que hacerlo a mano:\n'
    printf "$FALTAN\n"
    printf '\n   Abre el tablero -> Status -> Edit field -> añade o renombra:\n'
    printf '   %s\n\n' "$(gh project view "$PROJECT_NUMBER" --owner "$OWNER" --format json --jq .url)"
fi

# ------------------------------------------------------------------ #
# Campos de planificación, para decidir qué se puede paralelizar.
# ------------------------------------------------------------------ #
crear_campo() {
    local nombre="$1"; shift
    if echo "$FIELDS" | jq -e --arg n "$nombre" '.fields[] | select(.name == $n)' >/dev/null; then
        printf '%s·%s El campo «%s» ya existe\n' "$YELLOW" "$OFF" "$nombre"
        return
    fi
    gh project field-create "$PROJECT_NUMBER" --owner "$OWNER" \
        --name "$nombre" --data-type SINGLE_SELECT \
        --single-select-options "$1" >/dev/null
    printf '%s✓%s Campo «%s» creado\n' "$GREEN" "$OFF" "$nombre"
}

echo
crear_campo "Capa" "domain,data,presentation,infra,docs"
crear_campo "Paralelizable" "sí,no"

# ------------------------------------------------------------------ #
# Lo que el workflow necesita saber.
# ------------------------------------------------------------------ #
gh variable set PROJECT_NUMBER --body "$PROJECT_NUMBER" --repo "$REPO"
gh variable set PROJECT_OWNER  --body "$OWNER"          --repo "$REPO"
printf '\n%s✓%s Variables PROJECT_NUMBER y PROJECT_OWNER guardadas en el repositorio\n' "$GREEN" "$OFF"

cat <<NEXT

${BOLD}${GREEN}Falta una cosa que no puedo hacer por ti${OFF}

El GITHUB_TOKEN de Actions no puede tocar Projects. Crea un PAT con scope
\`project\` y guárdalo como secret:

  Tiene que ser un token CLÁSICO. Los fine-grained no sirven: su lista de
  permisos de cuenta no incluye Projects, así que no hay forma de darles
  acceso a un tablero que cuelga de un usuario.

  https://github.com/settings/tokens/new

    Note   : kitchenai-project
    Scopes : project      (Full control of projects)   <-- sólo ése

  gh secret set PROJECT_TOKEN --repo $REPO

Sin ese secret el workflow no falla: se salta el paso y te lo dice en el log.

Tablero:
  $(gh project view "$PROJECT_NUMBER" --owner "$OWNER" --format json --jq .url)
NEXT
