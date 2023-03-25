FROM clojure

RUN mkdir -p /course-bot
WORKDIR /course-bot
COPY . /course-bot

RUN clj -X:test
RUN clj -X:uberjar

ENV TZ="Europe/Moscow"
CMD ["java", "-jar", "course-bot.jar"]
