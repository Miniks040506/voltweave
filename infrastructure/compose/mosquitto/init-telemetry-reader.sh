#!/bin/sh
set -eu

role=telemetry-reader
topic='voltweave/+/+/+/telemetry'
username="$MQTT_TELEMETRY_USERNAME"

control() {
  mosquitto_ctrl -h "$MQTT_HOST" -p 1883 \
    -u "$MOSQUITTO_ADMIN_USERNAME" \
    -P "$MOSQUITTO_ADMIN_PASSWORD" \
    dynsec "$@"
}

control deleteClient "$username" >/dev/null 2>&1 || true
control deleteRole "$role" >/dev/null 2>&1 || true
control createRole "$role"
control addRoleACL "$role" subscribeLiteral "$topic" allow
control addRoleACL "$role" publishClientReceive "$topic" allow
control createClient "$username" -i telemetry-service -p "$MQTT_TELEMETRY_PASSWORD"
control addClientRole "$username" "$role" 1
