#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

# Closed local-API distribution. Ordinary build.sh remains readable/debuggable for development.
PROTECTED_BUILD=true exec bash ./build.sh
