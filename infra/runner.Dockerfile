FROM maven:3.9.9-eclipse-temurin-17
COPY projects/npe/pom.xml /opt/warmup/pom.xml
COPY infra/warmup /opt/warmup/src
WORKDIR /opt/warmup
RUN mvn -B -ntp -Dmaven.repo.local=/opt/m2 dependency:go-offline test
WORKDIR /work
