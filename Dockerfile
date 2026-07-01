# Step 1: Build stage using Maven and JDK 22
FROM maven:3.9.6-eclipse-temurin-22-jammy AS build
WORKDIR /app

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Compile and package the application jar, skipping tests for speed
RUN mvn clean package -DskipTests

# Step 2: Run stage using a lightweight JRE 22 image
FROM eclipse-temurin:22-jre-jammy
WORKDIR /app

# Copy the compiled JAR file from the build stage
COPY --from=build /app/target/realtime-chat-app-0.0.1-SNAPSHOT.jar app.jar

# Expose port 8081 (default fallback)
EXPOSE 8081

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
