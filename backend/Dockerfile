# Build from the repository root: docker build -f backend/Dockerfile .
FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /opt/protys

COPY backend/ backend/
COPY research/runtime/ research/runtime/

# Exact retained parent POM; validator and SWRL worker keep separate dependencies.
RUN set -eu; echo '1c61b3c4a7da306d9c5dc3d1a759f5f7c81604ed2bcf6a495a96d9c495866f28  research/runtime/vendor/owlapi-parent-4.5.27.pom' | sha256sum -c - \
 && mvn -q -f research/runtime/swrl-worker/pom.xml install:install-file \
      -Dfile=/opt/protys/research/runtime/vendor/owlapi-parent-4.5.27.pom \
      -DgroupId=net.sourceforge.owlapi -DartifactId=owlapi-parent \
      -Dversion=4.5.27 -Dpackaging=pom -DgeneratePom=false \
 && for module in swrl-worker validator; do \
      mvn -q -f "research/runtime/$module/pom.xml" package dependency:copy-dependencies \
        -DoutputDirectory=target/dependency || exit 1; \
      find "/opt/protys/research/runtime/$module/target/dependency" -name '*.jar' \
        | LC_ALL=C sort | paste -sd: - > "research/runtime/$module/target/classpath.txt"; \
      test -s "research/runtime/$module/target/classpath.txt"; \
    done

# Fuseki's command-line entrypoint needs its optional Log4j backend. These are
# Jena4.10's declared versions, isolated from the validator/worker classpaths.
RUN set -eu; for artifact in log4j-api log4j-core log4j-slf4j-impl; do \
      mvn -q -f research/runtime/validator/pom.xml dependency:copy \
        -Dartifact="org.apache.logging.log4j:$artifact:2.21.0" \
        -DoutputDirectory=/opt/protys/research/runtime/validator/target/fuseki-dependency; \
    done; \
    { tr ':' '\n' < research/runtime/validator/target/classpath.txt | sed '/\/slf4j-simple-/d'; \
      find /opt/protys/research/runtime/validator/target/fuseki-dependency -name '*.jar'; } \
      | LC_ALL=C sort | paste -sd: - > research/runtime/validator/target/fuseki-classpath.txt

RUN java -Djava.awt.headless=true \
      -cp "research/runtime/swrl-worker/target/classes:$(cat research/runtime/swrl-worker/target/classpath.txt)" \
      org.protys.research.SwrlSmokeTest research/runtime/swrl-worker/target/docker-smoke.json \
 && java -Djava.awt.headless=true \
      -cp "research/runtime/validator/target/classes:$(cat research/runtime/validator/target/fuseki-classpath.txt)" \
      org.apache.jena.fuseki.main.cmds.FusekiMainCmd --help \
 && mvn -q -f backend/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre-jammy AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/protys
COPY --from=builder /opt/protys/backend/target/protys-ws-*.jar backend/app.jar
COPY --from=builder /opt/protys/research/runtime/ research/runtime/
COPY ontologies/ ontologies/
COPY research/catalog.json research/catalog.json
COPY research/model/ research/model/
COPY research/data/ research/data/
COPY docker/fuseki-config.ttl docker/fuseki-config.ttl

ENV PROTYS_RESEARCH_ROOT=/opt/protys
WORKDIR /opt/protys/backend
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=10 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-Xmx2g", "-Djava.awt.headless=true", "-jar", "/opt/protys/backend/app.jar"]
