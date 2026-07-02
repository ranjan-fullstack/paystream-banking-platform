FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/imps-service-*.jar app.jar

EXPOSE 9006

ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","app.jar"]
