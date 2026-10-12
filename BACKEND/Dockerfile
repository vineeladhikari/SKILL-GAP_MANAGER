FROM maven:3.10.0-eclipse-temurin-25-alpine AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B package

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /workspace/target/skill-gap-manager-api-1.0.0.jar app.jar

USER app
ENV SPRING_PROFILES_ACTIVE=postgres
EXPOSE 8080 8081
ENTRYPOINT ["sh", "-c", "if [ -z \"${DB_HOST:-}\" ] && [ -z \"${SPRING_DATASOURCE_URL:-}\" ]; then echo 'Database is not configured: set DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD or SPRING_DATASOURCE_URL.' >&2; exit 1; fi; if [ -n \"${DB_HOST:-}\" ]; then export SPRING_DATASOURCE_URL=\"jdbc:postgresql://${DB_HOST}:${DB_PORT:-5432}/${DB_NAME}\"; export SPRING_DATASOURCE_USERNAME=\"${DB_USER:-skillgap_app}\"; export SPRING_DATASOURCE_PASSWORD=\"$DB_PASSWORD\"; fi; exec java -jar /app/app.jar"]
