#!/usr/bin/env sh
# Fixed scoped iOS qualification workload. The retained owner supplies paths.
# Preserve ordinary completed probe failures and run cleanup; the arm evaluates
# every child afterwards. Deadline/owner failures cannot qualify quiet.
set -u
node "$RECEIPT" child --name wiki -- node "$SCRIPTS/chrome_page_benchmark.mjs" \
  --port "$CDP_PORT" --runs 5 https://www.wikipedia.org/ >"$PRIVATE_DIR/wiki.jsonl" 2>"$PRIVATE_DIR/wiki.stderr"
for n in 1 2 3; do
  node "$RECEIPT" child --name "fast-$n" --timeout-ms 95000 -- node "$SCRIPTS/chrome_fast_benchmark.mjs" \
    --port "$CDP_PORT" --timeout-ms 90000 >"$PRIVATE_DIR/fast-$n.json" 2>"$PRIVATE_DIR/fast-$n.stderr"
done
node "$RECEIPT" cleanup --serial "$SERIAL" >"$PRIVATE_DIR/chrome-cleanup.stdout" 2>"$PRIVATE_DIR/chrome-cleanup.stderr"
