FROM openjdk:11-jre-slim

ARG VERSION
ENV ENV_VERSION=$VERSION
ENV APPLICATION_USER ktor
RUN adduser --disabled-password --gecos '' $APPLICATION_USER

RUN mkdir /app
RUN chown -R $APPLICATION_USER /app

USER $APPLICATION_USER

COPY ./build/libs/melee.neat.model-store-all.jar /app/model-store-$VERSION.jar
WORKDIR /app

ENTRYPOINT exec java -server $JAVA_OPTS -jar model-store-$ENV_VERSION.jar