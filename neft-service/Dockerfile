FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/neft-service-*.jar app.jar

EXPOSE 9004

ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","app.jar"]
