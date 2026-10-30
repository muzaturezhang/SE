#!/usr/bin/env sh
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
export BOOKING_UI_PREVIEW=true
export ACCOUNT_UI_PREVIEW=true
export STAFF_BOOKING_UI_PREVIEW=true
printf 'Staff booking UI preview: http://localhost:%s/admin/stays\n' "${SERVER_PORT:-8080}"
printf 'Account UI preview: http://localhost:%s/preview/account/login\n' "${SERVER_PORT:-8080}"
printf 'Booking UI preview: http://localhost:%s/preview/stays/new\n' "${SERVER_PORT:-8080}"
exec "$script_dir/dev-staff.sh" "$@"
