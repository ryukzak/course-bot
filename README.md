# course-bot

Course bot for performing laboratory works for "Computer Architecture" discipline.

## CSA course bot
### Команды общего характера:
- `/help` -- посмотреть справку по командам.
- `/listgroups` -- посмотреть списки групп с зарегистрированными студентами.
- `/quiz` -- начать тест; подтверждаете действие (yes/no), отвечаете цифрами на вопросы; по закрытии теста придёт информация о результатах.
- `/renameme` -- изменить своё имя в реестре.
- `/start` -- зарегистрироваться, укажите своё имя как в ведомости и свою группу.
- `/whoami` -- посмотреть информацию в реестре о себе (имя, группа, ID).

### Лабораторная работа №1:
[Описание](https://gitlab.se.ifmo.ru/Vsevolod01/csa-rolling#%D0%BB%D0%B0%D0%B1%D0%BE%D1%80%D0%B0%D1%82%D0%BE%D1%80%D0%BD%D0%B0%D1%8F-%D1%80%D0%B0%D0%B1%D0%BE%D1%82%D0%B0-1-%D1%80%D0%B0%D0%B7%D0%B1%D0%BE%D1%80-%D0%BF%D0%BE%D0%BB%D0%B5%D1%82%D0%BE%D0%B2) и [FAQ](https://gitlab.se.ifmo.ru/computer-systems/csa-rolling/-/blob/master/lab1faq.md) для лабораторной работы.

Для её выполнения следует взаимодействовать с ботом, используя следующие команды:
  - `/lab1agenda` -- расписание выступлений групп для лабораторной работы №1.
  - `/lab1schedule` -- выбрать день своего выступления.
  - `/lab1setgroup` -- укажите свою группу для лабораторной работы №1. Помните про виртуальную группу, сами её сменить вы не сможете.
  - `/lab1soon` -- список выступлений на ближайшее лабораторное занятие.
  - `/lab1submissions` -- список согласованных докладов.
  - `/lab1submit` -- загрузка описания вашего доклада.
  - После проверки (не менее суток) вам придут результаты.
  Если пришёл отзыв -- улучшаете описание и загружаете повторно.
  Если подтверждение, то регистрируетесь на один из дней (`/lab1schedule`). Будьте внимательны, регистрация закрывается автоматически где-то за полчаса до занятия.
  - `/lab1feedback` -- сразу после занятия составьте свой рейтинг докладов. Приём закрывается автоматически.

### Run

Expect course config in `../edu-csa-internal`.

``` sh
clj -X course-bot.csa/run
```

### Deploy

``` sh
docker build -t csa-bot .
docker run --name csa-bot --restart=always -d -v $PWD/../edu-csa-internal:/edu-csa-internal -v $PWD/../csa-db:/csa-db csa-bot
```

## License

Copyright © 2022 Aleksandr Penskoi 

BSD 3-Clause "New" or "Revised" License
