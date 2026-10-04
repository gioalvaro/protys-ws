#!/bin/sh
# Canonical asserted bootstrap; refuses foreign or partially initialized datasets.
set -eu
exec python3 "$(dirname "$0")/load_canonical.py"
