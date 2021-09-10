# course-bot

Course bot for performing laboratory works for "Computer Architecture" discipline.

## Usage

- `docker build -t course-bot . && docker stop csa-bot && docker rm csa-bot`
- `docker run -e BOT_TOKEN=.............. -v $HOME/course-data:/data --restart=on-failure:10 --name csa-bot course-bot`

## License

Copyright © 2021 Aleksandr Penskoi

BSD 3-Clause "New" or "Revised" License
