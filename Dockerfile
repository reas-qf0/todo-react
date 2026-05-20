FROM node:24-trixie

RUN apt-get update && apt-get upgrade -y && apt-get install -y openjdk-21-jdk-headless
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64/

ADD . /app
WORKDIR /app/webApp
RUN npm install && npx vite build --outDir /app/src/main/resources/static
WORKDIR /app
RUN ./gradlew --no-daemon compileJava

EXPOSE 8080
ENTRYPOINT ["./gradlew", "--no-daemon", "run"]