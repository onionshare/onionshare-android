#!/bin/bash

# this imports secret vars and then passes arguments to fastlane

MYDIR="$(dirname "$(realpath "$0")")"
source "$MYDIR/vars.sh"

set -ex

fastlane "$@"
