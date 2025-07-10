FROM maven:3.9.10-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .                
COPY app/pom.xml app/pom.xml 
COPY app/src app/src
COPY gatling/pom.xml gatling/pom.xml
COPY gatling/src gatling/src
RUN mvn -pl app -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ENV JAVA_XMS_SIZE=256m
ENV JAVA_XMX_SIZE=512m

COPY --from=build /app/app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Xms${JAVA_XMS_SIZE}", "-Xmx${JAVA_XMX_SIZE}", "-jar", "app.jar"]
