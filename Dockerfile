# ---------------------------------------------------------------------------
# BookLeaf Author Support Portal — production image.
#
# Stage 1 builds the React SPA, stage 2 builds the Spring Boot jar with the
# SPA copied into the static resources, stage 3 is a slim JRE runtime.
# The result is a single container that serves both the API and the UI from
# one origin — no CORS, no separate frontend service.
# ---------------------------------------------------------------------------

# --- Stage 1: build the frontend -------------------------------------------
FROM node:20-alpine AS frontend
WORKDIR /build

COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# --- Stage 2: build the backend --------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build

COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY backend/src ./src
# The built SPA is served from the classpath, so a page refresh keeps working
# and the browser never needs a second origin.
COPY --from=frontend /build/dist ./src/main/resources/static

RUN mvn -B -q -DskipTests package

# --- Stage 3: runtime -------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --create-home --uid 10001 bookleaf
COPY --from=backend /build/target/bookleaf-portal.jar app.jar
RUN chown -R bookleaf:bookleaf /app
USER bookleaf

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
