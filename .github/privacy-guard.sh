#!/usr/bin/env bash
# What may be published in this repository, checked on every push (CI) and before it (local hook).
#
#   privacy-guard.sh tree [<ref>]        files tracked at <ref> (default HEAD)
#   privacy-guard.sh range <base> <head> every commit in base..head: paths, added lines, authors
#
# 1. Paths: only the files an Android app and its site (docs/, GitHub Pages) need (allow-list). Anything else -- notes, editor or
#    tool folders, build output, keys -- fails, whatever its name.
# 2. Content: no private network addresses, local host names, e-mail addresses or credentials.
# 3. Commits: authors use the GitHub no-reply address; no co-author trailers.
# 4. Screenshots: every file in docs/screenshots is shown in README.md, on every branch: nothing spare is published.
# Exit 0 = clean, 1 = findings, 2 = usage error.
set -uo pipefail

ALLOWED='^(app/(build\.gradle|lint\.xml|src/(main|test)/.+)|gradle/wrapper/gradle-wrapper\.(jar|properties)|gradlew|gradlew\.bat|docs/screenshots/[a-z0-9-]+\.png|docs/([a-z-]+/)?index\.html|docs/(site\.(css|js)|icon\.svg|icon-192\.png|og\.png|sitemap\.xml|\.nojekyll|google[0-9a-f]+\.html)|\.github/(workflows/[a-z0-9-]+\.yml|ISSUE_TEMPLATE/[a-z0-9-]+\.yml|dependabot\.yml|privacy-guard\.sh)|build\.gradle|settings\.gradle|gradle\.properties|sonar-project\.properties|README\.md|ARCHITECTURE\.md|CHANGELOG\.md|SECURITY\.md|LICENSE|\.gitignore|\.gitattributes)$'
NEVER='\.(jks|keystore|p12|pfx|pem|key|apk|aab)$|(^|/)(local|signing)\.properties$|(^|/)\.env'

CONTENT=(
  '(^|[^0-9.])(10|127)\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}([^0-9.]|$)'
  '(^|[^0-9.])192\.168\.[0-9]{1,3}\.[0-9]{1,3}'
  '(^|[^0-9.])172\.(1[6-9]|2[0-9]|3[01])\.[0-9]{1,3}\.[0-9]{1,3}'
  '(^|[^0-9.])100\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\.[0-9]{1,3}\.[0-9]{1,3}'
  '[A-Za-z0-9-]+\.(local|lan|home|internal|localdomain|ts\.net|duckdns\.org|dyndns\.org|no-ip\.(com|org|biz))([^A-Za-z0-9-]|$)'
  '[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}'
  '(api[_-]?key|secret|token|passw(or)?d)["'"'"']?[[:space:]]*[:=][[:space:]]*["'"'"'][^"'"'"']{8,}'
)
SELF='.github/privacy-guard.sh'

fail=0
report() { echo "  $1" >&2; fail=1; }

check_paths() {  # paths on stdin
  local p
  while IFS= read -r p; do
    [ -n "$p" ] || continue
    if printf '%s\n' "$p" | grep -qE "$NEVER"; then report "never publish: $p"
    elif ! printf '%s\n' "$p" | grep -qE "$ALLOWED"; then report "not an app file: $p"; fi
  done
}

check_screenshots() {  # commit
  local readme f
  readme="$(git show "$1:README.md" 2>/dev/null || true)"
  while IFS= read -r f; do
    [ -n "$f" ] || continue
    [[ "$readme" == *"$f"* ]] || report "screenshot not in README: $f (commit ${1:0:9})"
  done < <(git ls-tree -r --name-only "$1" -- docs/screenshots)
}

check_text() {  # text lines on stdin, read once and matched against every pattern
  local re hits all
  all="$(cat)"
  for re in "${CONTENT[@]}"; do
    hits="$(printf '%s\n' "$all" | grep -E -- "$re" || true)"
    [ -n "$hits" ] && while IFS= read -r h; do report "content: ${h:0:160}"; done <<< "$hits"
  done
}

mode="${1:-tree}"
for r in "${@:2}"; do  # an unknown ref must fail, not pass with nothing checked
  git rev-parse --verify --quiet "$r^{commit}" >/dev/null || { echo "privacy-guard: unknown ref $r" >&2; exit 2; }
done
case "$mode" in
  tree)
    ref="${2:-HEAD}"
    git ls-tree -r --name-only "$ref" | check_paths
    check_screenshots "$ref"
    text="$(git grep -I -n -e '' "$ref" -- . ":(exclude)$SELF" 2>/dev/null | sed "s|^$ref:||")"
    ;;
  range)
    [ $# -ge 3 ] || { echo "usage: privacy-guard.sh range <base> <head>" >&2; exit 2; }
    git log --pretty=format: --name-only "$2..$3" -- | sort -u | sed '/^$/d' | check_paths
    # the tip, and each commit not published yet: one that adds a spare file and a later one that removes it still count
    check_screenshots "$3"
    while IFS= read -r c; do check_screenshots "$c"; done < <(git rev-list "$2..$3" --not --remotes)
    # every added line in every commit: a file added and removed within one push still counts
    text="$(git log -p --no-color --pretty=format:'commit %H' "$2..$3" -- . ":(exclude)$SELF" \
      | grep -E '^\+[^+]' | sed 's/^+//')"
    while IFS='|' read -r sha ae ce; do
      [ -n "$sha" ] || continue
      for e in "$ae" "$ce"; do
        case "$e" in *@users.noreply.github.com|noreply@github.com) ;; *) report "commit ${sha:0:9}: author/committer is not a no-reply address" ;; esac
      done
    done < <(git log --format='%H|%ae|%ce' "$2..$3")
    git log --format='%B' "$2..$3" | grep -qi '^co-authored-by:' && report "a commit message carries a co-author trailer"
    ;;
  *) echo "usage: privacy-guard.sh {tree [ref]|range <base> <head>}" >&2; exit 2 ;;
esac
# commit messages are published too
[ "$mode" = range ] && text="$text"$'\n'"$(git log --format='%B' "$2..$3")"
printf '%s\n' "$text" | check_text

if [ $fail -ne 0 ]; then
  echo "PRIVACY GUARD FAILED (see above)." >&2
  exit 1
fi
echo "Privacy guard passed ($mode)."
