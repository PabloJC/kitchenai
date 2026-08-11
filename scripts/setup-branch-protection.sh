#!/usr/bin/env bash
# Step 4 — protect `main` and turn on auto-merge.
#
#   ./scripts/setup-branch-protection.sh            apply the configuration
#   ./scripts/setup-branch-protection.sh --show     show the current configuration
#   ./scripts/setup-branch-protection.sh --remove   remove the protection
#
# Idempotent: run it as often as you like.
set -euo pipefail

GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; BOLD=$'\033[1m'; OFF=$'\033[0m'

REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
BRANCH="main"

case "${1:-}" in
    --show)
        gh api "repos/$REPO/branches/$BRANCH/protection" 2>/dev/null \
            || echo "Branch $BRANCH is not protected."
        exit 0
        ;;
    --remove)
        gh api -X DELETE "repos/$REPO/branches/$BRANCH/protection"
        printf '%s✓%s Protection removed from %s\n' "$YELLOW" "$OFF" "$BRANCH"
        exit 0
        ;;
esac

printf '%s%sConfiguring %s%s\n\n' "$BOLD" "$GREEN" "$REPO" "$OFF"

# ------------------------------------------------------------------ #
# 1. Repository options.
#
# Squash only: every issue lands on `main` as a single commit, so the history is the list of
# finished tasks and reverting one is a single command.
# ------------------------------------------------------------------ #
gh repo edit "$REPO" \
    --enable-auto-merge \
    --delete-branch-on-merge \
    --enable-squash-merge \
    --enable-merge-commit=false \
    --enable-rebase-merge=false

printf '%s✓%s Auto-merge, squash-only and branch deletion on merge\n' "$GREEN" "$OFF"

# ------------------------------------------------------------------ #
# 2. Branch protection.
#
# `strict: false` on purpose: with `true`, every merge invalidates the other open pull
# requests and each has to be updated and re-reviewed, which serialises development — the
# opposite of parallel issues. The price is that two independently green pull requests can
# break `main` together; the push CI catches that.
#
# `required_approving_review_count: 0` because GitHub does not let you approve your own pull
# requests. The review comes from the `Claude review` check, not a human. Raise it to 1 once
# AI_REVIEWER_TOKEN points at a machine user.
#
# `enforce_admins` is sent here for completeness, but this PUT accepts it without applying
# it: section 3 turns it on through its own endpoint.
#
# `required_conversation_resolution: false` because it collides head-on with an automated
# reviewer. Every inline comment opens a thread, and a single unresolved one leaves the pull
# request BLOCKED even with an `approve` verdict and green checks — and GitHub's error, "the
# base branch policy prohibits the merge", never mentions threads.
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
  "required_conversation_resolution": false,
  "block_creations": false,
  "lock_branch": false,
  "allow_fork_syncing": false
}
JSON
then
    printf '\n%s✗ Could not apply branch protection.%s\n\n' "$YELLOW" "$OFF"
    cat <<'WHY'
If the error is a 403 with "Upgrade to GitHub Pro or make this repository public", there is
nothing to fix here: branch protection does not exist for private repositories on the Free
plan. GitHub accepts the call and applies nothing.

Ways out:
  · make the repository public  -> gh repo edit --visibility public
  · move to GitHub Pro
  · live without protection     -> auto-merge.yml covers the automatic merge;
                                   see docs/infra.md
WHY
    exit 1
fi

printf '%s✓%s Protection applied to %s\n' "$GREEN" "$OFF" "$BRANCH"

# ------------------------------------------------------------------ #
# 3. enforce_admins, through its own endpoint.
#
# The PUT above accepts "enforce_admins": true, answers 200 and leaves it false. It has to be
# set through the dedicated endpoint, and verified: without this the protection looks applied
# while not covering the owner — the only person who was going to push anyway.
# ------------------------------------------------------------------ #
gh api -X POST "repos/$REPO/branches/$BRANCH/protection/enforce_admins" >/dev/null

ADMINS=$(gh api "repos/$REPO/branches/$BRANCH/protection" --jq .enforce_admins.enabled)
if [ "$ADMINS" != "true" ]; then
    printf '%s✗ enforce_admins is still false.%s The protection will not apply to you.\n' "$RED" "$OFF"
    exit 1
fi
printf '%s✓%s enforce_admins on: the protection applies to you too\n\n' "$GREEN" "$OFF"

cat <<'NEXT'
What just changed:

  · No direct pushes to main. Everything goes through a pull request.
  · A pull request merges only with "CI passed" and "Claude review" green.
  · Squash only. Linear history. No force-push, no deleting main.
  · Reviewer comments do NOT block the merge: the "Claude review" verdict decides.

The flow for a task from now on:

  git checkout -b feat/12-recipe-list
  ...
  gh pr create --fill
  gh pr merge --squash --auto        <- merges on its own once the checks pass

Check it works by trying to break it:

  git checkout main
  echo "// test" >> README.md && git commit -am "chore: test the protection"
  git push        <- the remote must reject this

And undo the test:

  git reset --hard origin/main
NEXT
