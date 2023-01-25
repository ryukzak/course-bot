(ns course-bot.general-test
  (:require [clojure.test :refer [deftest testing is]])
  (:require [codax.core :as codax])
  (:require [course-bot.general :as general]
            [course-bot.report :as report]
            [course-bot.talk-test :as tt]
            [course-bot.misc :as misc]))

(deftest start-talk-test
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

      (testing "group list for admin"
        (talk 0 "/listgroups")
        (tt/match-text *chat 0
                       "gr1 group:"
                       "1) Bot Botovich (@, 1)"))

      (testing "group list"
        (talk 1 "/listgroups")
        (tt/match-text *chat 1
                       "That action requires admin rights."))

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

(deftest restart-talk-test
  (let [conf (misc/get-config "conf-example")
        db (tt/test-database)
        *chat (atom (list))
        talk (tt/handlers (general/start-talk db conf)
                          (general/whoami-talk db conf)
                          (general/restart-talk db conf))]
    (tt/with-mocked-morse *chat
      (talk "bla-bla")
      (is (= '() @*chat))

      (testing "wrong requests"
        (talk "/restart")
        (tt/match-text *chat "That action requires admin rights.")

        (talk 0 "/restart")
        (tt/match-text *chat 0 "Wrong input. Expect: /restart 12345")

        (talk 0 "/restart 1")
        (tt/match-text *chat 0 "User with specific telegram id not found."))

      (testing "register user for restart"
        (talk "/start")
        (talk "Bot Botovich")
        (talk "gr1")
        (talk "/whoami")
        (tt/match-text *chat "Name: Bot Botovich; Group: gr1; Telegram ID: 1")
        (talk "/start")
        (tt/match-text *chat "You are already registered. To change your information, contact the teacher and send /whoami"))

      (testing "try but not actually restart"
        (talk 0 "/restart 1")
        (tt/match-text *chat 0 "Restart this student?")

        (talk 0 "emm")
        (tt/match-text *chat 0 "Please yes or no?")

        (talk 0 "no")
        (tt/match-text *chat 0 "Not restarted."))

      (testing "restart"
        (talk 0 "/restart 1")
        (talk 0 "yes")
        (tt/match-history *chat
                          (tt/text 0 "Restarted and notified: 1")
                          (tt/text 1 "You can use /start once more."))

        (talk "/start")
        (tt/match-text *chat "Hi, I'm a bot for your course. I will help you with your work. What is your name (like in the registry)?")))))

(deftest restart-permitted-test
  (let [conf (assoc (misc/get-config "conf-example") :allow-restart true)
        db (tt/test-database)
        *chat (atom (list))
        talk (tt/handlers (general/start-talk db conf)
                          (general/whoami-talk db conf)
                          (general/restart-talk db conf))]
    (tt/with-mocked-morse *chat

      (testing "register user for restart"
        (talk "/start")
        (talk "Bot Botovich")
        (talk "gr1")
        (talk "/whoami")
        (tt/match-text *chat "Name: Bot Botovich; Group: gr1; Telegram ID: 1")
        (talk "/start")
        (tt/match-text *chat "Hi, I'm a bot for your course. I will help you with your work. What is your name (like in the registry)?")))))

(deftest renameme-talk-test
  (let [conf (misc/get-config "conf-example")
        db (tt/test-database)
        *chat (atom (list))
        talk (tt/handlers (general/start-talk db conf)
                          (general/whoami-talk db conf)
                          (general/renameme-talk db conf))]
    (tt/with-mocked-morse *chat
      (talk "/renameme")
      (tt/match-text *chat "You should be registered to rename yourself!")

      (talk "/start")
      (talk "Bot Botovich")
      (talk "gr1")
      (talk "/whoami")
      (tt/match-text *chat "Name: Bot Botovich; Group: gr1; Telegram ID: 1")

      (talk "/renameme")
      (tt/match-text *chat "What is your new name?")

      (talk "Buddy")
      (tt/match-history *chat
                        (tt/text 1 "Renamed:")
                        (tt/text 1 "Name: Buddy; Group: gr1; Telegram ID: 1"))

      (talk "/whoami")
      (tt/match-text *chat "Name: Buddy; Group: gr1; Telegram ID: 1"))))
