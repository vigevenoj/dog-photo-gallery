FROM clojure:lein AS BUILD_CONTAINER
RUn mkdir -p /usr/local/app
WORKDIR /opt/app
COPY . /opt/app
RUN lein uberjar


FROM openjdk:11-jdk-slim

COPY --from=BUILD_CONTAINER /opt/app/target/uberjar/doggallery.jar /doggallery/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/doggallery/app.jar"]
