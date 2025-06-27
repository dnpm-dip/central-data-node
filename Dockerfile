FROM openjdk:21

COPY core/target/scala-2.13/dnpm-ccdn-core.jar  /opt/
COPY connectors/target/scala-2.13/dnpm-ccdn-connectors.jar /opt/connectors/
COPY --chmod=755 entrypoint.sh /

LABEL org.opencontainers.image.licenses=MIT
LABEL org.opencontainers.image.source=https://github.com/dnpm-dip/central-data-node
LABEL org.opencontainers.image.description="DNPM - MVGenomSeq Central Data Node"

ENV CONFIG_DIR=/ccdn_config
ENV DATA_DIR=/ccdn_data

ENV CCDN_CONFIG_FILE=$CONFIG_DIR/config.json
ENV CCDN_QUEUE_DIR=$DATA_DIR/queue

VOLUME $CONFIG_DIR
VOLUME $DATA_DIR

ENTRYPOINT ["/entrypoint.sh"]
