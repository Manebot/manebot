FROM alpine/git as clone
WORKDIR /build
RUN git clone https://github.com/manevolent/manebot.git

FROM maven:3.6-jdk-11-slim as build
WORKDIR /build
COPY --from=clone /build/manebot /build
RUN mvn package

FROM openjdk:12
RUN yum install -y wget python python-dev build-essential python-pip libav-tools opus openjfx swig sox libatlas-base-dev git cron && pip install youtube-dl
WORKDIR /app
COPY --from=build /build/target /app
CMD java -jar manebot.jar
