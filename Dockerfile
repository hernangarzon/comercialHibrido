# 0. PANEL WEB (React + Vite): se compila hacia src/main/resources/static
FROM node:24-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 1. ETAPA DE CONSTRUCCIÓN (BUILD)
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copiar wrapper y pom.xml para aprovechar caché de dependencias
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Copiar código fuente y compilar JAR
COPY src ./src
COPY --from=frontend /app/src/main/resources/static ./src/main/resources/static
RUN ./mvnw clean package -DskipTests

# 2. ETAPA DE EJECUCIÓN (RUNTIME LIGERO)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Crear usuario sin privilegios por seguridad
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copiar el artefacto compilado
COPY --from=builder /app/target/*.jar app.jar

# Railway inyecta PORT en ejecución; 8080 es el valor por defecto (Docker Compose).
EXPOSE 8080
ENV PORT=8080

# MaxRAMPercentage: el heap se ajusta a la memoria del contenedor en lugar de la del servidor.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]