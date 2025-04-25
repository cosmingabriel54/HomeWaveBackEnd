# Use Maven image to build the app
FROM maven:3.9.4-eclipse-temurin-17 as build

# Set working directory inside the container
WORKDIR /app

# Copy Maven project files first (for better caching)
COPY pom.xml .
COPY src ./src

# Package the app (skip tests if you want faster builds)
RUN mvn clean package -DskipTests

# ------------------------------

# Now use a lighter image for running the app
FROM eclipse-temurin:17-jre

# Set working directory
WORKDIR /app

# Copy built JAR from the previous build stage
COPY --from=build /app/target/*.jar app.jar

# Expose port (adjust if different)
EXPOSE 8080

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]
