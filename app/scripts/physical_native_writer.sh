#!/usr/bin/env bash
# The writer supervisor owns exit handling and atomic terminal publication.
# Outer zsh/bash callers must not manufacture receipts or assign `status`.
set -Eeuo pipefail
writer_helper_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
exec node "$writer_helper_directory/physical_native_writer.mjs" "$@"
