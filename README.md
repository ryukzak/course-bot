# course-bot

Course bot for performing laboratory works for "Computer Architecture" discipline.

## Usage

`docker run -e BOT_TOKEN=... -e BOT_DATABASE=/data/csa -v $HOME/course-data:/data --restart=always -d --name csa-bot ghcr.io/ryukzak/course-bot:master`

## License

Copyright © 2021 Aleksandr Penskoi

BSD 3-Clause "New" or "Revised" License
