FROM openjdk:8-alpine

COPY target/uberjar/doggallery.jar /doggallery/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/doggallery/app.jar"]
