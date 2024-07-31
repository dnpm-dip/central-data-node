#!/bin/bash


CONFIG_FILE=/home/lucien/Work/Software/DNPM_DIP/central-data-node/core/src/test/resources/config.json
LOGBACK_CONFIG_FILE=/home/lucien/Work/Software/DNPM_DIP/central-data-node/core/src/test/resources/logback.xml
QUEUE_DIR=/home/lucien/Work/Software/DNPM_DIP/central-data-node/queue
DNPM_BROKER_URL=http://localhost:8081
BFARM_URL=http://localhost:8081/bfarm


java \
  -Dlogback.configurationFile="$LOGBACK_CONFIG_FILE" \
  -Ddnpm.ccdn.config.file="$CONFIG_FILE" \
  -Ddnpm.ccdn.broker.baseurl="$DNPM_BROKER_URL" \
  -Ddnpm.ccdn.bfarm.baseurl="$BFARM_URL" \
  -Ddnpm.ccdn.queue.dir="$QUEUE_DIR" \
  -cp core/target/scala-3.4.2/dnpm-ccdn-core.jar:connectors/target/scala-3.4.2/* de.dnpm.ccdn.core.exec




