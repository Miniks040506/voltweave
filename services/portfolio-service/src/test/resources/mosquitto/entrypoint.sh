#!/bin/sh
set -eu

mosquitto_ctrl dynsec init /tmp/dynamic-security.json "$MOSQUITTO_ADMIN_USERNAME" "$MOSQUITTO_ADMIN_PASSWORD"
chown mosquitto:mosquitto /tmp/dynamic-security.json
exec mosquitto -c /opt/mosquitto.conf
