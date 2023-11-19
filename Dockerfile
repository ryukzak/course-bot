FROM clojure

RUN mkdir -p /course-bot
WORKDIR /course-bot
COPY ./target/course-bot.jar /course-bot

ENV TZ="Europe/Moscow"
CMD ["java", "-jar", "course-bot.jar"]
