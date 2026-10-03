#!/bin/sh
# Auxiliary Docker loader; primary verified route is research/reproduce.sh + local REST.
set -eu
exec python3 "$(dirname "$0")/load_canonical.py"
