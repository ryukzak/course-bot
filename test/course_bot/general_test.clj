(ns course-bot.general-test
  (:require [clojure.test :refer :all]
            [clojure.test :as test])
  (:require [codax.core :as codax]
            [morse.handlers :as handlers])
  (:require [course-bot.general :as general]
            [course-bot.talk :as talk]
            [course-bot.report :as report]
            [course-bot.talk-test :as ttalk]
            [course-bot.talk-test :as tt]
            [course-bot.misc :as misc]))

(declare db *chat start-talk-test restart-talk-test)

(test/deftest start-talk-test
  (let [conf (misc/get-config "conf-example")
        db (tt/test-database)
        *chat (atom (list))
        talk (tt/handlers (general/start-talk db conf)
                          (general/listgroups-talk db conf)
                          (report/report-talk db conf
                                              "ID" report/stud-id
                                              "name" report/stud-name
                                              "group" report/stud-group))]
    (tt/with-mocked-morse *chat
      (talk "bla-bla")
      (is (= '() @*chat))

      (testing "registration"
        (talk "/start")
        (tt/match-text *chat "Hi, I'm a bot for your course. I will help you with your work. What is your name (like in the registry)?")

        (talk "Bot Botovich")
        (tt/match-text *chat "What is your group (gr1, gr2)?")

        (talk "wrong group")
        (tt/match-text *chat "I don't know this group. Please, repeat it (gr1, gr2):")

        (talk "gr1")
        (tt/match-history *chat
                          (tt/text 1 "Hi Bot Botovich!")
                          (tt/text 1 "Name: Bot Botovich; Group: gr1; Telegram ID: 1")
                          (tt/text 1 "Send /help for help."))

        (is (= false (codax/get-at! db [1 :allow-restart])))
        (is (= "gr1" (codax/get-at! db [1 :group])))
        (is (= "Bot Botovich" (codax/get-at! db [1 :name])))
        (is (some? (codax/get-at! db [1 :reg-date]))))

      (testing "group list"
        (talk "/listgroups")
        (tt/match-text *chat
                       "gr1 group:"
                       "1) Bot Botovich (@, 1)"))

      (testing "simple-report"
        (talk 0 "/report")
        (tt/match-history *chat
                          (tt/text 0 "Report file:")
                          (tt/csv 0
                                  ["ID" "name" "group"]
                                  ["1" "Bot Botovich" "gr1"])))

      (testing "second registration"
        (talk "/start")
        (tt/match-text *chat "You are already registered. To change your information, contact the teacher and send /whoami")))))

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

(talk/deftest restart-permitted-test [db *chat]
  (let [conf (assoc (misc/get-config "conf-example") :allow-restart true)
        start-talk (ttalk/mock-talk general/start-talk db conf)
        whoami-talk (ttalk/mock-talk general/whoami-talk db conf)
        restart-talk (ttalk/mock-talk general/restart-talk db conf)]

    (testing "register user for restart"
      (start-talk "/start")
      (start-talk "Bot Botovich")
      (start-talk "gr1")
      (whoami-talk "/whoami")
      (ttalk/in-history *chat "Name: Bot Botovich; Group: gr1; Telegram ID: 1")
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
