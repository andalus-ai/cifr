# Stage 1: Build the backend with Maven
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app
# Copy local backend context
COPY . /app

# Build the Quarkus application
RUN mvn clean package -Dmaven.test.skip=true

# Stage 2: Create the minimal runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the Quarkus fast-jar and its dependencies
COPY --from=builder /app/aljabr-api/target/quarkus-app/lib/ /app/lib/
COPY --from=builder /app/aljabr-api/target/quarkus-app/*.jar /app/
COPY --from=builder /app/aljabr-api/target/quarkus-app/app/ /app/app/
COPY --from=builder /app/aljabr-api/target/quarkus-app/quarkus/ /app/quarkus/

EXPOSE 8086
EXPOSE 9000

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

CMD ["java", "-jar", "quarkus-run.jar"]
