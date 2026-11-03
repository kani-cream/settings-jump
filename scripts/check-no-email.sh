#!/usr/bin/env bash
# This repository is public, so nothing in it may expose an email address: not commit or
# tag identities, commit messages, or added file content. Only GitHub noreply addresses
# pass, which means the check itself never has to name a private address.
#
# Usage: check-no-email.sh <git rev-list arguments>, for example: HEAD --tags
#
# Findings show commit hashes and tag names only, because CI logs are public too.
set -euo pipefail
export LC_ALL=C

email='[[:alnum:]._%+-]+@[[:alnum:].-]+\.[[:alpha:]]{2,}'
allowed='(@users\.noreply\.github\.com|^noreply@[[:alnum:].-]+)$'
failed=0

report() {
    echo "ERROR: $1" >&2
    failed=1
}

has_disallowed_identity() {
    grep -avE "$allowed" >/dev/null
}

has_disallowed_email() {
    local found
    found=$(grep -aoE "$email" || true)
    [ -n "$found" ] && printf '%s\n' "$found" | has_disallowed_identity
}

while read -r commit; do
    if git show -s --format='%ae%n%ce' "$commit" | has_disallowed_identity; then
        report "$commit: author or committer email is not a noreply address"
    fi
    if git show -s --format='%B' "$commit" | has_disallowed_email; then
        report "$commit: commit message contains an email address"
    fi
    if git show --format= --no-color -U0 "$commit" | { grep -a '^+' || true; } | has_disallowed_email; then
        report "$commit: added content contains an email address"
    fi
done < <(git rev-list "$@")

while read -r tag tagger; do
    if [ -n "$tagger" ] && printf '%s\n' "${tagger//[<>]/}" | has_disallowed_identity; then
        report "tag $tag: tagger email is not a noreply address"
    fi
done < <(git for-each-ref refs/tags --format='%(refname:short) %(taggeremail)')

if [ "$failed" -ne 0 ]; then
    echo "To fix a commit identity, see plan/design.md section 20.2." >&2
    exit 1
fi
