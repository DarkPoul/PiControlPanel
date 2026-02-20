FROM eclipse-temurin:21-jre
WORKDIR /app

# vcgencmd для throttling + сертифікати для можливих HTTP-запитів
RUN apt-get update \
 && apt-get install -y --no-install-recommends libraspberrypi-bin ca-certificates \
 && rm -rf /var/lib/apt/lists/*

COPY deploy/app.jar /app/app.jar

# ліміти JVM для Pi (підкрутиш за потреби)
ENV JAVA_OPTS="-Xms128m -Xmx256m"

EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
