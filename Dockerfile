FROM clojure:temurin-8-noble

# Install locales and generate required locales
RUN apt-get update && \
    apt-get install -y locales && \
    locale-gen en_US.UTF-8 ru_RU.UTF-8 && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set locale environment variables directly
ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8

# Create and set the working directory
RUN mkdir -p /course-bot
WORKDIR /course-bot

# Copy application JAR to the working directory
COPY ./target/course-bot.jar /course-bot

# Set timezone
ENV TZ="Europe/Moscow"

# Run the application
CMD ["java", "-jar", "course-bot.jar"]
