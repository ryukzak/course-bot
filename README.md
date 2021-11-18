# course-bot

Course bot for performing laboratory works for "Computer Architecture" discipline.

## Usage

```sh
docker run \
  -v $HOME/course-data:/data \ 
  -e BOT_TOKEN=... \ 
  -e BOT_DATABASE=csa \ 
  -e QUIZ=edu-csa-test/lec-5-6.edn \
  -e GROUP_DESC=edu-csa-test/group-desc.edn \
  --restart=always -d \
  --name csa-bot ghcr.io/ryukzak/course-bot:master
```

## License

Copyright © 2021 Aleksandr Penskoi

BSD 3-Clause "New" or "Revised" License
