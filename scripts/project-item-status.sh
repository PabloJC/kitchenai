#!/usr/bin/env bash
# Mueve una issue a una columna del tablero de GitHub Projects.
#
#   ./scripts/project-item-status.sh <numero-de-issue> <columna>
#
# Ejemplo:
#   ./scripts/project-item-status.sh 12 "In progress"
#
# Si la issue todavía no está en el tablero, la añade. Es idempotente:
# mover algo a la columna en la que ya está no hace nada y sale bien.
#
# Variables necesarias:
#   GH_TOKEN         PAT con scope `project` (el GITHUB_TOKEN de Actions NO sirve)
#   PROJECT_OWNER    dueño del proyecto, p.ej. PabloJC
#   PROJECT_NUMBER   número del proyecto, el de su URL
#   GH_REPO          owner/repo de la issue
set -euo pipefail

ISSUE="${1:?Falta el número de issue}"
STATUS="${2:?Falta la columna de destino}"

: "${GH_TOKEN:?Falta GH_TOKEN}"
: "${PROJECT_OWNER:?Falta PROJECT_OWNER}"
: "${PROJECT_NUMBER:?Falta PROJECT_NUMBER}"
: "${GH_REPO:?Falta GH_REPO}"

PROJECT_ID=$(gh project view "$PROJECT_NUMBER" --owner "$PROJECT_OWNER" \
             --format json --jq .id)

# ------------------------------------------------------------------ #
# El item de esta issue en el tablero. Puede no existir todavía.
# ------------------------------------------------------------------ #
ITEM_ID=$(gh project item-list "$PROJECT_NUMBER" --owner "$PROJECT_OWNER" \
          --format json --limit 500 \
          --jq ".items[] | select(.content.type == \"Issue\" and .content.number == $ISSUE) | .id" \
          | head -1)

if [ -z "$ITEM_ID" ]; then
    echo "La issue #$ISSUE no estaba en el tablero. Añadiéndola."
    URL="https://github.com/$GH_REPO/issues/$ISSUE"
    ITEM_ID=$(gh project item-add "$PROJECT_NUMBER" --owner "$PROJECT_OWNER" \
              --url "$URL" --format json --jq .id)
fi

# ------------------------------------------------------------------ #
# Los IDs del campo Status y de la opción de destino. Se resuelven en
# caliente en vez de fijarlos: GitHub los regenera si recreas el campo,
# y un ID caducado falla de forma muy poco evidente.
# ------------------------------------------------------------------ #
FIELDS=$(gh project field-list "$PROJECT_NUMBER" --owner "$PROJECT_OWNER" --format json)

FIELD_ID=$(echo "$FIELDS" | jq -r '.fields[] | select(.name == "Status") | .id')

# La comparación ignora mayúsculas a propósito. Projects crea la columna como
# «In Progress» y es muy fácil acabar con «In progress» tras un renombrado;
# hacer que el tablero deje de funcionar por una letra sería absurdo.
OPTION_ID=$(echo "$FIELDS" | jq -r --arg s "$STATUS" \
            '.fields[] | select(.name == "Status") | .options[]?
             | select((.name | ascii_downcase) == ($s | ascii_downcase)) | .id')

if [ -z "$OPTION_ID" ] || [ "$OPTION_ID" = "null" ]; then
    echo "::error::La columna '$STATUS' no existe en el tablero."
    echo "Columnas disponibles:"
    echo "$FIELDS" | jq -r '.fields[] | select(.name == "Status") | .options[]?.name | "  - " + .'
    exit 1
fi

gh project item-edit \
    --id "$ITEM_ID" \
    --project-id "$PROJECT_ID" \
    --field-id "$FIELD_ID" \
    --single-select-option-id "$OPTION_ID" >/dev/null

echo "Issue #$ISSUE -> $STATUS"
