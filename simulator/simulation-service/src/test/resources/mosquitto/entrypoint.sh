#!/bin/sh
set -eu

mosquitto_passwd -b -c /tmp/passwords device device-secret
mosquitto_passwd -b /tmp/passwords observer observer-secret
printf 'user device\ntopic write root/telemetry\ntopic write root/status\ntopic write root/ack\ntopic read root/command\n\nuser observer\ntopic readwrite root/#\n' > /tmp/acl
chown mosquitto:mosquitto /tmp/passwords /tmp/acl
exec mosquitto -c /opt/mosquitto.conf
