FROM postgres:17.5-alpine

RUN apk add --no-cache bash bats coreutils findutils jq python3 util-linux \
    && mkdir -p /opt/stocket/backup /var/lib/stocket/backups /var/lib/stocket/attachments \
    && chown -R 70:70 /var/lib/stocket/backups /var/lib/stocket/attachments

COPY deploy/backup/*.sh /opt/stocket/backup/
RUN chmod 0555 /opt/stocket/backup/*.sh

USER 70:70
WORKDIR /var/lib/stocket
ENTRYPOINT ["/opt/stocket/backup/backup.sh"]
