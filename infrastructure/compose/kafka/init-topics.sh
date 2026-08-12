#!/usr/bin/env bash
set -Eeuo pipefail

bootstrap_server="kafka:19092"
topics=(
  "vw.telemetry.raw.v1:86400000"
  "vw.telemetry.normalized.v1:604800000"
  "vw.portfolio.lifecycle.v1:2592000000"
  "vw.command.lifecycle.v1:2592000000"
  "vw.dispatch.lifecycle.v1:2592000000"
  "vw.settlement.lifecycle.v1:2592000000"
  "vw.audit.v1:7776000000"
)

for topic_config in "${topics[@]}"; do
  topic="${topic_config%%:*}"
  retention_ms="${topic_config##*:}"
  /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$bootstrap_server" \
    --create \
    --if-not-exists \
    --topic "$topic" \
    --partitions 3 \
    --replication-factor 1 \
    --config "retention.ms=$retention_ms"
done

/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$bootstrap_server" --list
