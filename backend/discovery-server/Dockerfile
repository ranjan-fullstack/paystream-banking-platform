# ----------- Runtime Image -----------
FROM eclipse-temurin:17-jre-alpine
# App directory
WORKDIR /app
COPY target/discovery-server-*.jar app.jar
EXPOSE 8761
# Run Spring Boot
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","app.jar"]
