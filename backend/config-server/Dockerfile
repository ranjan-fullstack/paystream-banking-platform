# ----------- Runtime Image -----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/config-server-*.jar app.jar
EXPOSE 8888
# Run Spring Boot
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","app.jar"]
