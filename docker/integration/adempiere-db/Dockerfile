# Base PostgreSQL de ADempiere 3.9.4 LTS con la semilla oficial (cliente demo GardenWorld).
# Origen: asset publicado en https://github.com/adempiere/adempiere/releases/tag/3.9.4
# El volcado es SQL plano generado con pg_dump 15.1, por eso la base es PostgreSQL 15.
# ADD --checksum hace fallar el build si el asset publicado cambia.
FROM postgres:15.10@sha256:d609c3005478af92bddad773423df829b7402ea0b356d5b72edd2fd54d1ad3ea

ADD --checksum=sha256:ed4b2b3d33bbf831cb2443195cea0ede2f836e7212a123042e0b794ca87bbd1c \
    https://github.com/adempiere/adempiere/releases/download/3.9.4/adempiere_postgresql_seed.tar.gz \
    /seed/adempiere_postgresql_seed.tar.gz

RUN tar -xzf /seed/adempiere_postgresql_seed.tar.gz -C /seed \
 && rm /seed/adempiere_postgresql_seed.tar.gz \
 && test -s /seed/Adempiere_pg.dmp

COPY 10-restaurar-semilla.sh /docker-entrypoint-initdb.d/
