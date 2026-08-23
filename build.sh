#!/usr/bin/env bash
# SPDX-License-Identifier: MPL-2.0
#
# Smoketest build for the android app — the local equivalent of what
# build/all/run.sh does for android:
#   1. regenerate the string resources from the localization store
#      (../localizations/keys). The pipeline regenerates before every build,
#      so a string that exists only in strings.xml but not in the store
#      disappears there — running this locally reproduces that failure mode.
#   2. run the pipeline's host gradle build (play + dApp store flavors), plus
#      a compile check of the github flavor (its APK is built by
#      build/all/build-fdroid.sh in the F-Droid buildserver container).
#
# The version comes from app/local.properties (warp.version/warp.version_code)
# or falls back to `warpctl ls version`.
#
# Usage:
#   ./build.sh
#   BUILD_SDK=1 ./build.sh    also rebuild sdk/build/android/URnetworkSdk.aar
#                             from the local sdk/connect/glog trees first
#                             (gradle goclientBuild; the pipeline rebuilds it
#                             every release)
#   SKIP_FDROID=1 ./build.sh  skip the F-Droid container step (fast iteration;
#                             the github flavor still gets a compile check)
#   URNETWORK_ROOT=<dir>      sibling-repo root (default: parent of this repo)
#   WARP_HOME=<dir>           release signing config root for the F-Droid step
#                             (default: URNETWORK_ROOT)
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="${URNETWORK_ROOT:-$(dirname "$here")}"

echo "== sync localizations (store -> res/values*/strings.xml)"
(cd "$root/localizations" &&
    { [ -d node_modules ] || npm ci --no-audit --no-fund; } &&
    npm run gen:android)

if [ "${BUILD_SDK:-}" ]; then
    echo "== rebuild the android sdk aar from the local sdk/connect/glog trees"
    (cd "$here/app" && ./gradlew goclientBuild)
fi

echo "== gradle build (pipeline host flavors + github compile check)"
(cd "$here/app" &&
    ./gradlew clean assemblePlayRelease bundlePlayRelease \
        assembleSolana_dappRelease assembleEthos_dappRelease \
        compileGithubReleaseKotlin)

if [ -z "${SKIP_FDROID:-}" ]; then
    echo "== fdroid container build (github flavor, like the pipeline)"
    # Stage the local trees the container consumes, write the staged
    # local.properties (the container reads warp.version* from it, like
    # run.sh does on the ungoogle branch), then build the staged copy —
    # build-fdroid.sh is invoked without SRC_* so it does not re-stage.
    (
        export BUILD_HOME="${BUILD_HOME:-$root/build}"
        export SRC_HOME="$root"
        source "$root/build/all/stage-local-repos.sh"
        stage_local_repos sdk connect glog goidenticons android
        ver="$(sed -n 's/^warp\.version=//p' "$here/app/local.properties" 2>/dev/null | tail -1 || true)"
        code="$(sed -n 's/^warp\.version_code=//p' "$here/app/local.properties" 2>/dev/null | tail -1 || true)"
        [ -n "$ver" ] || ver="$(warpctl ls version)"
        [ -n "$code" ] || code="$(warpctl ls version-code)"
        printf '\nwarp.version=%s\nwarp.version_code=%s\n' "$ver" "$code" \
            > "$BUILD_HOME/android/app/local.properties"
        unset SRC_HOME SRC_SDK SRC_CONNECT SRC_GLOG SRC_ANDROID
        WARP_HOME="${WARP_HOME:-$root}" "$root/build/all/build-fdroid.sh"
    )
fi

echo "== android build OK"
