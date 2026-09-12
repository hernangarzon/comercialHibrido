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
RUN ./mvnw clean package -DskipTests

# 2. ETAPA DE EJECUCIÓN (RUNTIME LIGERO)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Crear usuario sin privilegios por seguridad
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copiar el artefacto compilado
COPY --from=builder /app/target/*.jar app.jar

# Puerto de Railway
EXPOSE 8080
ENV PORT=8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]