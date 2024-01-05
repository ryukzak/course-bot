# course-bot

A course bot for lab work in the "Computer Architecture" discipline.

An up-to-date link to the bot instance for your course can be found in the course repository or in the organizational chat.

You can get a list of commands from the bot via the `/help` command. It may change throughout the year, so don't miss course news.

## Features

1. `course-bot.presentation` -- automation of seminar scheduling, including:
    - asynchronous anonymous review of seminar topics by the instructor with feedback mechanics,
    - registration for a particular seminar by the students themselves,
    - generation of class schedules,
    - collecting student feedback on the seminar in the form of a performance rating.
2. `course-bot.essay` -- automation of collection and double-blind cross-review of student essays on specific topics with plagiarism control and reporting system.
3. `course-bot.quiz` -- small and quick quiz for current control of students' knowledge.
4. report generation.
5. Support for Ru and En languages.

## Usage

Expect course config in `../edu-csa-internal`.

Use [Makefile](Makefile) targets: `backup`, `update`, `run`, `docker-build`, `docker-run`

## License

Copyright © 2023 Aleksandr Penskoi

BSD 3-Clause "New" or "Revised" License
