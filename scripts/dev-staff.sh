#!/usr/bin/env sh
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
umask 077
mkdir -p "$project_dir/.cache/staff-preview"
password_file="$project_dir/.cache/staff-preview/password.txt"
if [ ! -s "$password_file" ]; then
  openssl rand -hex 18 > "$password_file"
fi
chmod 600 "$password_file"
export LOCAL_STAFF_USERNAME=localstaff
export LOCAL_STAFF_PASSWORD=$(cat "$password_file")
export SPRING_PROFILES_ACTIVE=dev,local-staff
if [ "${BOOKING_UI_PREVIEW:-false}" = true ]; then
  export SPRING_PROFILES_ACTIVE=dev,local-staff,booking-ui
fi
if [ "${ACCOUNT_UI_PREVIEW:-false}" = true ]; then
  export SPRING_PROFILES_ACTIVE="$SPRING_PROFILES_ACTIVE,account-ui"
fi
if [ "${STAFF_BOOKING_UI_PREVIEW:-false}" = true ]; then
  export SPRING_PROFILES_ACTIVE="$SPRING_PROFILES_ACTIVE,staff-booking-ui"
fi
export SERVER_ADDRESS=127.0.0.1
printf 'Local staff preview: http://localhost:%s/admin/rooms\n' "${SERVER_PORT:-8080}"
printf 'Username: localstaff; password is in %s (never commit this file).\n' "$password_file"
exec "$script_dir/dev.sh" "$@"
