FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/customer-service-*.jar app.jar

EXPOSE 9002

ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","app.jar"]
