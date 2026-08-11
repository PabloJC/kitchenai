#!/usr/bin/env bash
# Moves an issue to a column of the GitHub Projects board.
#
#   ./scripts/project-item-status.sh <issue-number> <column>
#
# Idempotent: moving something to the column it is already in does nothing and exits 0. If
# the issue is not on the board yet, it is added.
#
# It talks to GraphQL rather than `gh project`, which resolves whether the owner is a user or
# an organisation first and needs `read:org` to do it even for a personal board — failing
# with `unknown owner type`, which mentions neither the token nor the board.
#
# Required:
#   GH_TOKEN         classic PAT with the `project` scope (the Actions GITHUB_TOKEN will not do)
#   PROJECT_OWNER    board owner login
#   PROJECT_NUMBER   project number, the one in its URL
#   GH_REPO          owner/repo of the issue
#
# Optional:
#   REPO_TOKEN       token used to read the issue. Defaults to GH_TOKEN; Actions passes the
#                    GITHUB_TOKEN so the board PAT does not need `repo`.
set -euo pipefail

ISSUE="${1:?Missing issue number}"
STATUS="${2:?Missing target column}"

: "${GH_TOKEN:?Missing GH_TOKEN}"
: "${PROJECT_OWNER:?Missing PROJECT_OWNER}"
: "${PROJECT_NUMBER:?Missing PROJECT_NUMBER}"
: "${GH_REPO:?Missing GH_REPO}"

# ------------------------------------------------------------------ #
# The board, the Status field and its options, in one query.
#
# `gh` exits 1 whenever the response carries `errors`, even with `data` filled in — and it
# always does here: the same login cannot be both a user and an organisation.
# ------------------------------------------------------------------ #
RESPONSE=$(gh api graphql -f login="$PROJECT_OWNER" -F number="$PROJECT_NUMBER" -f query='
  query($login: String!, $number: Int!) {
    user(login: $login)         { projectV2(number: $number) { ...board } }
    organization(login: $login) { projectV2(number: $number) { ...board } }
  }
  fragment board on ProjectV2 {
    id
    field(name: "Status") {
      ... on ProjectV2SingleSelectField { id options { id name } }
    }
  }' 2>&1 || true)

BOARD=$(printf '%s' "$RESPONSE" \
          | jq -c '(.data.user.projectV2 // .data.organization.projectV2) // empty' 2>/dev/null || true)

if [ -z "$BOARD" ]; then
    echo "::error::Cannot read board #$PROJECT_NUMBER of $PROJECT_OWNER."
    echo "Either the number does not exist, or the token lacks the 'project' scope."
    echo "API response:"
    printf '%s\n' "$RESPONSE" | head -20
    exit 1
fi

PROJECT_ID=$(printf '%s' "$BOARD" | jq -r '.id')
FIELD_ID=$(printf '%s' "$BOARD" | jq -r '.field.id // empty')

if [ -z "$FIELD_ID" ]; then
    echo "::error::The board has no single-select 'Status' field."
    exit 1
fi

# Case-insensitive on purpose: Projects creates «In Progress» and a rename easily leaves
# «In progress».
OPTION_ID=$(printf '%s' "$BOARD" | jq -r --arg s "$STATUS" \
            '.field.options[] | select((.name | ascii_downcase) == ($s | ascii_downcase)) | .id')

if [ -z "$OPTION_ID" ] || [ "$OPTION_ID" = "null" ]; then
    echo "::error::Column '$STATUS' does not exist on the board."
    echo "Available columns:"
    printf '%s' "$BOARD" | jq -r '.field.options[].name | "  - " + .'
    exit 1
fi

# ------------------------------------------------------------------ #
# `addProjectV2ItemById` is idempotent: it returns the existing item instead of duplicating
# it, so there is no need to look it up first.
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
