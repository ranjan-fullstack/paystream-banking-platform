FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/rtgs-service-*.jar app.jar

EXPOSE 9012

ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","app.jar"]
