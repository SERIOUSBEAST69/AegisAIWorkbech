FROM mysql:8.0

WORKDIR /bootstrap

COPY bootstrap.sh /bootstrap/bootstrap.sh
COPY 001-latest-compat.sql /bootstrap/001-latest-compat.sql
COPY 002-baseline-init.sql /bootstrap/002-baseline-init.sql

RUN sed -i 's/\r$//' /bootstrap/bootstrap.sh /bootstrap/001-latest-compat.sql /bootstrap/002-baseline-init.sql \
 && chmod +x /bootstrap/bootstrap.sh

ENTRYPOINT ["/bin/sh", "/bootstrap/bootstrap.sh"]