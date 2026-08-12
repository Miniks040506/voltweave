#!/bin/sh
set -eu

config_file=/mosquitto/data/dynamic-security.json

if [ ! -f "$config_file" ]; then
  mosquitto_ctrl dynsec init \
    "$config_file" \
    "$MOSQUITTO_ADMIN_USERNAME" \
    "$MOSQUITTO_ADMIN_PASSWORD"
fi

chown mosquitto:mosquitto "$config_file"
chmod 600 "$config_file"

exec mosquitto -c /mosquitto/config/mosquitto.conf
