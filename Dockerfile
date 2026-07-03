# Build stage — imagem pinada com CAs mais recentes
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# Opcional: certificado de CA para proxy/inspeção SSL no CI (conteúdo PEM em base64).
# Ex.: docker build --build-arg CUSTOM_CA_B64="$(base64 -w0 proxy-ca.crt)" .
ARG CUSTOM_CA_B64=
RUN if [ -n "$CUSTOM_CA_B64" ]; then \
      echo "$CUSTOM_CA_B64" | base64 -d > /tmp/custom-ca.crt && \
      keytool -importcert -noprompt -trustcacerts -alias custom-build-ca \
        -file /tmp/custom-ca.crt \
        -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit && \
      rm /tmp/custom-ca.crt; \
    fi

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src ./src
RUN ./mvnw -B package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
