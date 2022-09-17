FROM clojure

RUN mkdir -p /course-bot
WORKDIR /course-bot
COPY . /course-bot

RUN clj -X:test

ENV TZ="Europe/Moscow"
CMD ["clj", "-X", "course-bot.csa/run"]
