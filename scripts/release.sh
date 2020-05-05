#!/usr/bin/env bash
# Release a new version

set -euo pipefail

if ! hub version > /dev/null; then
    echo "The hub tool is needed. If you're on macOS, you can install it with \`brew install hub\`"
    exit 1
fi

BRANCH=$(git rev-parse --abbrev-ref HEAD)

if [[ "$BRANCH" != "master" ]]; then
    echo "This script only works in the master branch."
    exit 1
fi

if ! git diff-index --quiet HEAD -- project.clj; then
    echo "project.clj contains uncommited changes. Commit them first."
    exit 1
fi

if ! git merge-base --is-ancestor "master" "master@{u}"; then
    echo "The master branch has not been pushed. Please git push."
    exit 1
fi

VERSION=$(lein pprint --no-pretty -- :version)

echo "Version in project.clj: $VERSION"

case "$VERSION" in
    *-SNAPSHOT)
        echo ""
        echo "SNAPSHOT releases are not supported. You can \`lein deploy\` them yourself."
        exit 1
        ;;
esac

echo "Going to release version $VERSION... is this correct? [y/n]"

read -r answer
case "$answer" in
    y | Y | yes | YES)
        ;;
    *)
        echo "Exiting"
        exit 1
        ;;
esac

if hub release show "$VERSION" 2>/dev/null; then
    echo
    echo "Release $VERSION already exists"
    exit 1
fi

# Let's just check if the chanelog contains an appropriate title
if ! grep -q -F "# $VERSION " CHANGELOG.md; then
    echo
    echo "Please update CHANGELOG to contain the release $VERSION"
    exit 1
fi

hub release create -m "$VERSION" "$VERSION"
