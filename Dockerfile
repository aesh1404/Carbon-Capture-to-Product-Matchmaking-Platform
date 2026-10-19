# Build the React UI, then a single Spring Boot JAR that serves both UI and API.
# One free Render web service is enough; PostgreSQL is a separate Render database.

FROM node:22-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /app
COPY backend/ ./
COPY --from=frontend /frontend/dist/ src/main/resources/static/
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S carbonlink && adduser -S carbonlink -G carbonlink
COPY --from=backend /app/target/*.jar app.jar
USER carbonlink
ENV JAVA_OPTS="-Xms128m -Xmx384m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
