FROM node:22-alpine AS frontend-build
WORKDIR /workspace/front-end
COPY front-end/package.json front-end/package-lock.json ./
RUN npm ci
COPY front-end/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS backend-build
WORKDIR /workspace/back-end
COPY back-end/pom.xml ./
RUN mvn -B dependency:go-offline
COPY back-end/src ./src
COPY --from=frontend-build /workspace/front-end/dist/frontend/browser/ ./src/main/resources/static/
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend-build /workspace/back-end/target/smart-incident-finder-0.0.1-SNAPSHOT.jar ./app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
