(ns course-bot.general-test
  (:require [clojure.test :refer :all])
  (:require [codax.core :as codax])
  (:require [course-bot.general :as general]
            [course-bot.talk :as talk]
            [course-bot.report :as report]
            [course-bot.talk-test :as ttalk]
            [course-bot.misc :as misc]))

(declare db *chat start-talk-test restart-talk-test)

(talk/deftest start-talk-test [db *chat]
  (let [conf (misc/get-config "conf-example")
        start-talk (ttalk/mock-talk general/start-talk db conf)
        listgroup-talk (ttalk/mock-talk general/listgroups-talk db conf)
        report-talk (ttalk/mock-talk report/report-talk db conf
                                     "ID" report/stud-id
                                     "name" report/stud-name
                                     "group" report/stud-group)]
    (start-talk "bla-bla")
    (is (= '() @*chat))

    (testing "registration"
      (start-talk "/start")
      (ttalk/in-history *chat "Hi, I'm a bot for your course. I will help you with your work. What is your name (like in the registry)?")

      (start-talk "Bot Botovich")
      (ttalk/in-history *chat "What is your group (gr1, gr2)?")

      (start-talk "wrong group")
      (ttalk/in-history *chat "I don't know this group. Please, repeat it (gr1, gr2):")

      (start-talk "gr1")
      (ttalk/in-history *chat "Hi Bot Botovich!"
                        "Name: Bot Botovich; Group: gr1; Telegram ID: 1"
                        "Send /help for help.")

      (is (= false (codax/get-at! db [1 :allow-restart])))
      (is (= "gr1" (codax/get-at! db [1 :group])))
      (is (= "Bot Botovich" (codax/get-at! db [1 :name])))
      (is (some? (codax/get-at! db [1 :reg-date]))))

    (testing "group list"
      (listgroup-talk "/listgroups")
      (ttalk/in-history *chat "gr1 group:\n1) Bot Botovich (@, 1)"))

    (testing "simple-report"
      (report-talk 0 "/report")
      (ttalk/in-history *chat [0 "Report file:"]
                        [0
                         "ID;name;group"
                         "1;Bot Botovich;gr1\n"]))

    (testing "second registration"
      (start-talk "/start")
      (ttalk/in-history *chat "You are already registered. To change your information, contact the teacher and send /whoami"))))

(talk/deftest restart-talk-test [db *chat]
  (let [conf (misc/get-config "conf-example")
        start-talk (ttalk/mock-talk general/start-talk db conf)
        whoami-talk (ttalk/mock-talk general/whoami-talk db conf)
        restart-talk (ttalk/mock-talk general/restart-talk db conf)]
    (restart-talk "bla-bla")
    (is (= '() @*chat))

    (testing "wrong requests"
      (restart-talk "/restart")
      (ttalk/in-history *chat "That action requires admin rights.")

      (restart-talk 0 "/restart")
      (ttalk/in-history *chat 0 "Wrong input. Expect: /restart 12345")

      (restart-talk 0 "/restart 1")
      (ttalk/in-history *chat 0 "User with specific telegram id not found."))

    (testing "register user for restart"
      (start-talk "/start")
      (start-talk "Bot Botovich")
      (start-talk "gr1")
      (whoami-talk "/whoami")
      (ttalk/in-history *chat "Name: Bot Botovich; Group: gr1; Telegram ID: 1")
      (start-talk "/start")
      (ttalk/in-history *chat "You are already registered. To change your information, contact the teacher and send /whoami"))

    (testing "try but not actually restart"
      (restart-talk 0 "/restart 1")
      (ttalk/in-history *chat 0 "Restart this student?")

      (restart-talk 0 "emm")
      (ttalk/in-history *chat 0 "Please yes or no?")

      (restart-talk 0 "no")
      (ttalk/in-history *chat 0 "Not restarted."))

    (testing "restart"
      (restart-talk 0 "/restart 1")
      (restart-talk 0 "yes")
      (ttalk/in-history *chat
                        [0 "Restarted and notified: 1"]
                        [1 "You can use /start once more."])

      (start-talk "/start")
      (ttalk/in-history *chat "Hi, I'm a bot for your course. I will help you with your work. What is your name (like in the registry)?"))))

(talk/deftest renameme-talk-test [db *chat]
  (let [conf (misc/get-config "conf-example")
        start-talk (ttalk/mock-talk general/start-talk db conf)
        whoami-talk (ttalk/mock-talk general/whoami-talk db conf)
        renameme-talk (ttalk/mock-talk general/renameme-talk db conf)]

    (renameme-talk "/renameme")
    (ttalk/in-history *chat "You should be registered to rename yourself!")

    (start-talk "/start")
    (start-talk "Bot Botovich")
    (start-talk "gr1")
    (whoami-talk "/whoami")
    (ttalk/in-history *chat "Name: Bot Botovich; Group: gr1; Telegram ID: 1")

    (renameme-talk "/renameme")
    (ttalk/in-history *chat "What is your new name?")

    (renameme-talk "Buddy")
    (ttalk/in-history *chat
                      "Renamed:"
                      "Name: Buddy; Group: gr1; Telegram ID: 1")

    (whoami-talk "/whoami")
    (ttalk/in-history *chat "Name: Buddy; Group: gr1; Telegram ID: 1")))
