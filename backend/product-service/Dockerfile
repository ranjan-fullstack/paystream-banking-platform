# ----------- Runtime Image -----------
FROM eclipse-temurin:17-jre-alpine

# App directory
WORKDIR /app

# Copy Spring Boot fat jar
COPY target/product-service-1.0.0.jar app.jar

# Expose internal container port
EXPOSE 9008

# Run Spring Boot
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","app.jar"]

