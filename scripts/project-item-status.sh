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
# Se habla con la API GraphQL en vez de con `gh project`. El subcomando resuelve
# primero si el owner es un usuario o una organización, y para eso necesita
# `read:org` aunque el tablero sea personal; sin ese scope falla con
# `unknown owner type`, que no menciona ni el token ni el tablero. Preguntando
# por `user` y `organization` a la vez y quedándose con el que conteste, un
# tablero personal sólo necesita `project`.
#
# Variables necesarias:
#   GH_TOKEN         PAT con scope `project` (el GITHUB_TOKEN de Actions NO sirve)
#   PROJECT_OWNER    dueño del proyecto, p.ej. PabloJC
#   PROJECT_NUMBER   número del proyecto, el de su URL
#   GH_REPO          owner/repo de la issue
#
# Opcional:
#   REPO_TOKEN       token para leer la issue. Por defecto GH_TOKEN. En Actions
#                    se le pasa el GITHUB_TOKEN, que ya puede leer el
#                    repositorio, y así el PAT del tablero no necesita `repo`.
set -euo pipefail

ISSUE="${1:?Falta el número de issue}"
STATUS="${2:?Falta la columna de destino}"

: "${GH_TOKEN:?Falta GH_TOKEN}"
: "${PROJECT_OWNER:?Falta PROJECT_OWNER}"
: "${PROJECT_NUMBER:?Falta PROJECT_NUMBER}"
: "${GH_REPO:?Falta GH_REPO}"

# ------------------------------------------------------------------ #
# El tablero, el campo Status y sus opciones, en una sola consulta.
#
# `gh` sale con código 1 si la respuesta trae `errors`, aunque `data` venga
# rellena, y aquí siempre trae uno: el mismo login no puede ser usuario y
# organización a la vez. Por eso se guarda el cuerpo y se decide mirándolo.
# ------------------------------------------------------------------ #
RESPUESTA=$(gh api graphql -f login="$PROJECT_OWNER" -F number="$PROJECT_NUMBER" -f query='
  query($login: String!, $number: Int!) {
    user(login: $login)         { projectV2(number: $number) { ...tablero } }
    organization(login: $login) { projectV2(number: $number) { ...tablero } }
  }
  fragment tablero on ProjectV2 {
    id
    field(name: "Status") {
      ... on ProjectV2SingleSelectField { id options { id name } }
    }
  }' 2>&1 || true)

TABLERO=$(printf '%s' "$RESPUESTA" \
          | jq -c '(.data.user.projectV2 // .data.organization.projectV2) // empty' 2>/dev/null || true)

if [ -z "$TABLERO" ]; then
    echo "::error::No se puede leer el tablero #$PROJECT_NUMBER de $PROJECT_OWNER."
    echo "O el número no existe, o el token no tiene scope 'project' (tiene que ser clásico)."
    echo "Respuesta de la API:"
    printf '%s\n' "$RESPUESTA" | head -20
    exit 1
fi

PROJECT_ID=$(printf '%s' "$TABLERO" | jq -r '.id')
FIELD_ID=$(printf '%s' "$TABLERO" | jq -r '.field.id // empty')

if [ -z "$FIELD_ID" ]; then
    echo "::error::El tablero no tiene un campo 'Status' de tipo single-select."
    exit 1
fi

# La comparación ignora mayúsculas a propósito. Projects crea la columna como
# «In Progress» y es muy fácil acabar con «In progress» tras un renombrado;
# hacer que el tablero deje de funcionar por una letra sería absurdo.
OPTION_ID=$(printf '%s' "$TABLERO" | jq -r --arg s "$STATUS" \
            '.field.options[] | select((.name | ascii_downcase) == ($s | ascii_downcase)) | .id')

if [ -z "$OPTION_ID" ] || [ "$OPTION_ID" = "null" ]; then
    echo "::error::La columna '$STATUS' no existe en el tablero."
    echo "Columnas disponibles:"
    printf '%s' "$TABLERO" | jq -r '.field.options[].name | "  - " + .'
    exit 1
fi

# ------------------------------------------------------------------ #
# El item de esta issue en el tablero. `addProjectV2ItemById` es
# idempotente: si la issue ya está, devuelve el item que ya existía en
# vez de duplicarlo, así que no hace falta buscarlo antes.
# ------------------------------------------------------------------ #
CONTENT_ID=$(GH_TOKEN="${REPO_TOKEN:-$GH_TOKEN}" gh api "repos/$GH_REPO/issues/$ISSUE" --jq .node_id)

ITEM_ID=$(gh api graphql -f project="$PROJECT_ID" -f content="$CONTENT_ID" -f query='
  mutation($project: ID!, $content: ID!) {
    addProjectV2ItemById(input: { projectId: $project, contentId: $content }) {
      item { id }
    }
  }' --jq '.data.addProjectV2ItemById.item.id')

gh api graphql -f project="$PROJECT_ID" -f item="$ITEM_ID" \
               -f field="$FIELD_ID" -f option="$OPTION_ID" -f query='
  mutation($project: ID!, $item: ID!, $field: ID!, $option: String!) {
    updateProjectV2ItemFieldValue(input: {
      projectId: $project,
      itemId:    $item,
      fieldId:   $field,
      value:     { singleSelectOptionId: $option }
    }) { projectV2Item { id } }
  }' >/dev/null

echo "Issue #$ISSUE -> $STATUS"
