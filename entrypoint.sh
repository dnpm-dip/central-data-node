#!/bin/bash

java -Dlogback.configurationFile="$CONFIG_DIR/logback.xml" \
     -cp /opt/dnpm-ccdn-core.jar:/opt/connectors/dnpm-ccdn-connectors.jar \
     de.dnpm.ccdn.core.MVHReportingService

