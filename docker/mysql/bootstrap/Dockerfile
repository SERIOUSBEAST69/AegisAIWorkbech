FROM docker.m.daocloud.io/library/mysql:8.0

WORKDIR /bootstrap

COPY bootstrap.sh /bootstrap/bootstrap.sh
COPY 001-latest-compat.sql /bootstrap/001-latest-compat.sql
COPY 002-baseline-init.sql /bootstrap/002-baseline-init.sql
COPY 003-simulation-events.sql /bootstrap/003-simulation-events.sql
COPY 004-cleanup-invalid-rbac.sql /bootstrap/004-cleanup-invalid-rbac.sql

RUN sed -i 's/\r$//' /bootstrap/bootstrap.sh /bootstrap/001-latest-compat.sql /bootstrap/002-baseline-init.sql /bootstrap/003-simulation-events.sql /bootstrap/004-cleanup-invalid-rbac.sql \
 && chmod +x /bootstrap/bootstrap.sh

ENTRYPOINT ["/bin/sh", "/bootstrap/bootstrap.sh"]