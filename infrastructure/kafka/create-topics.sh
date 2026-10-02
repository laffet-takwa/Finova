# ======================================================================
# FINOVA — Kafka topic bootstrap
#
# The platform declares its topics in code (each service owns the topics it
# produces), but a fresh cluster should have them provisioned explicitly with
# the right partition counts and retention so the very first transfer is not
# racing an auto-created single-partition topic.
#
# One topic, one producer, one meaning:
#   transaction.created    transaction-service -> fraud-service        (needs replay: fraud re-scoring)
#   transaction.approved   fraud-service       -> transaction-service  (settlement decision)
#   transaction.completed  transaction-service -> account-service, notification-service
#   transaction.failed     transaction-service -> account-service, notification-service
#   transaction.flagged    fraud-service       -> transaction-service, notification-service
#   account.opened         account-service     -> transaction-service
#   account.blocked        account-service, fraud-service -> notification-service
#   notification.created   notification-service -> (activity feed)
#   audit.recorded         every service       -> user-service
# ======================================================================
#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:9092}"
PARTITIONS="${KAFKA_PARTITIONS:-3}"
RETENTION_HOURS="${KAFKA_RETENTION_HOURS:-168}"

wait_for_kafka() {
  echo "[finova-kafka] waiting for broker at ${BOOTSTRAP} ..."
  for attempt in $(seq 1 60); do
    if kafka-topics.sh --bootstrap-server "${BOOTSTRAP}" --list >/dev/null 2>&1; then
      echo "[finova-kafka] broker is ready."
      return 0
    fi
    sleep 3
  done
  echo "[finova-kafka] broker did not become ready in time" >&2
  return 1
}

create_topic() {
  local topic="$1"
  local description="$2"
  kafka-topics.sh \
    --bootstrap-server "${BOOTSTRAP}" \
    --create \
    --if-not-exists \
    --topic "${topic}" \
    --partitions "${PARTITIONS}" \
    --replication-factor 1 \
    --config "retention.hours=${RETENTION_HOURS}" \
    --config "cleanup.policy=delete" \
    >/dev/null
  echo "[finova-kafka] provisioned ${topic}  (${description})"
}

wait_for_kafka

create_topic "transaction.created"    "transfer accepted -> fraud analysis"
create_topic "transaction.approved"   "fraud cleared the transfer for settlement"
create_topic "transaction.completed"  "settlement fact -> projection + inbox"
create_topic "transaction.failed"     "terminal failure -> projection + inbox"
create_topic "transaction.flagged"    "fraud held the transfer for review"
create_topic "account.opened"         "account created / opening balance restated"
create_topic "account.blocked"        "account blocked"
create_topic "notification.created"   "inbox entry persisted"
create_topic "audit.recorded"         "append-only audit trail"

echo "[finova-kafka] all topics provisioned:"
kafka-topics.sh --bootstrap-server "${BOOTSTRAP}" --list | grep -E '^(transaction|account|notification|audit)\.' | sort

# Audit records are the platform's compliance record: keep them long enough to
# be useful in an investigation.
kafka-configs.sh --bootstrap-server "${BOOTSTRAP}" \
  --entity-type topics --entity-name audit.recorded \
  --alter --add-config "retention.hours=${AUDIT_RETENTION_HOURS:-2160}" || true

echo "[finova-kafka] bootstrap complete."