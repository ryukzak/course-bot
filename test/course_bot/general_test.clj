(ns course-bot.general-test
  (:require [clojure.test :refer [deftest testing is]])
  (:require [codax.core :as codax])
  (:require [course-bot.general :as general]
            [course-bot.misc :as misc]
            [course-bot.report :as report]
            [course-bot.talk-test :as tt :refer [answers?]]))

(deftest start-talk-test
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))

        {talk :talk *chat :*chat}
        (tt/test-handler (general/start-talk db conf)
                         (general/listgroups-talk db conf)
                         (report/report-talk db conf
                                             "ID" report/stud-id
                                             "name" report/stud-name
                                             "group" report/stud-group))]
    (tt/with-mocked-morse *chat
      (testing "registration"
        (is (answers? (talk 1 "/start")
                      "Hi, I'm a bot for your course. I will help you with your work. What is your name (like in the registry)?"))
        (is (answers? (talk 1 "Bot Botovich")
                      "What is your group (gr1, gr2)?"))
        (is (answers? (talk 1 "wrong group")
                      "I don't know this group. Please, repeat it (gr1, gr2):"))
        (talk 1 "gr1")
        (tt/match-history *chat
                          (tt/text 1 "Hi, Bot Botovich!")
                          (tt/text 1 "Name: Bot Botovich; Group: gr1; Telegram ID: 1")
                          (tt/text 1 "Send /help for help."))

        (is (= false (codax/get-at! db [1 :allow-restart])))
        (is (= "gr1" (codax/get-at! db [1 :group])))
        (is (= "Bot Botovich" (codax/get-at! db [1 :name])))
        (is (some? (codax/get-at! db [1 :reg-date]))))

      (testing "group list for admin"
        (is (answers? (talk 0 "/listgroups")
                      (tt/unlines "gr1 group:"
                                  "1) Bot Botovich (@, 1)"))))

      (testing "group list"
        (is (answers? (talk 1 "/listgroups")
                      "That action requires admin rights.")))

      (testing "simple-report"
        (talk 0 "/report")
        (tt/match-history *chat
                          (tt/text 0 "Report file:")
                          (tt/csv 0
                                  ["ID" "name" "group"]
                                  ["1" "Bot Botovich" "gr1"])))

      (testing "second registration"
        (is (answers? (talk 1 "/start")
                      "You are already registered. To change your information, contact the teacher and send /whoami"))))))

(deftest restart-permitted-test
  (let [conf (assoc (misc/get-config "conf-example/csa-2023.edn") :allow-restart true)
        db (tt/test-database (-> conf :db-path))

        {talk :talk, *chat :*chat}
        (tt/test-handler (general/start-talk db conf)
                         (general/whoami-talk db conf))]
    (tt/with-mocked-morse *chat
      (testing "register user for restart"
        (talk 1 "/start" "Bot Botovich" "gr1")
        (is (answers? (talk 1 "/whoami")
                      "Name: Bot Botovich; Group: gr1; Telegram ID: 1"))
        (is (answers? (talk 1 "/start")
                      "Hi, I'm a bot for your course. I will help you with your work. What is your name (like in the registry)?"))
        (is (answers? (talk 1 "Alice")
                      "What is your group (gr1, gr2)?"))
        (is (answers? (talk 1 "gr2")
                      "Hi, Alice!"
                      "Name: Alice; Group: gr2; Telegram ID: 1"
                      "Send /help for help."))

        (is (= '({:name "Bot Botovich", :group "gr1"})
               (codax/get-at! db [1 :old-info])))))))

(deftest renameme-talk-test
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))

        {talk :talk, *chat :*chat}
        (tt/test-handler (general/start-talk db conf)
                         (general/whoami-talk db conf)
                         (general/renameme-talk db conf))]
    (tt/with-mocked-morse *chat
      (is (answers? (talk 1 "/renameme")
                    "You should be registered to rename yourself!"))

      (is (answers? (talk 1 "/start" "Bot Botovich" "gr1")
                    "Hi, I'm a bot for your course. I will help you with your work. What is your name (like in the registry)?"
                    "What is your group (gr1, gr2)?"
                    "Hi, Bot Botovich!"
                    "Name: Bot Botovich; Group: gr1; Telegram ID: 1"
                    "Send /help for help."))

      (is (answers? (talk 1 "/whoami")
                    "Name: Bot Botovich; Group: gr1; Telegram ID: 1"))

      (is (answers? (talk 1 "/renameme" "Buddy")
                    "What is your new name?"
                    "Renamed:"
                    "Name: Buddy; Group: gr1; Telegram ID: 1"))

      (is (answers? (talk 1 "/whoami")
                    "Name: Buddy; Group: gr1; Telegram ID: 1")))))
