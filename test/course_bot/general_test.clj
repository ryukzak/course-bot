(ns course-bot.general-test
  (:require [course-bot.general :as general]
            [course-bot.talk :as talk]
            [codax.core :as codax]
            [clojure.test :refer :all]))

(declare start-talk-test restart-talk-test)

(talk/deftest start-talk-test [db *chat]
  (let [start-talk (general/start-talk db "TOKEN")]
    (start-talk (talk/msg "bla-bla"))
    (is (= '() @*chat))

    (testing "registration"
      (start-talk (talk/msg "/start"))
      (is (= "Hi, I'm a bot for your course. I will help you with your works. What is your name?"
             (-> @*chat first :msg)))

      (start-talk (talk/msg "Bot Botovich"))
      (is (= "What is your group (gr1, gr2)?"
             (-> @*chat first :msg)))

      (start-talk (talk/msg "wrong group"))
      (is (= "I don't know this group. Repeat please (gr1, gr2):"
             (-> @*chat first :msg)))

      (start-talk (talk/msg "gr1"))
      (is (= (list "Hi:"
                   "Name: Bot Botovich; Group: gr1; Telegram ID: 1"
                   "Send /help for help.")
             (->> @*chat (take 3) (map :msg) reverse))))

    (testing "second registration"
      (start-talk (talk/msg "/start"))
      (is (= "You are already registered, to change your unform the teacher and send /whoami."
             (-> @*chat first :msg))))))

(talk/deftest restart-talk-test [db *chat]
  (let [start-talk (general/start-talk db "TOKEN")
        whoami-talk (general/whoami-talk db "TOKEN")
        restart-talk (general/restart-talk db "TOKEN" general/assert-admin)]
    (restart-talk (talk/msg "bla-bla"))
    (is (= '() @*chat))

    (testing "wrong requests"
      (restart-talk (talk/msg "/restart"))
      (is (= "That action require admin rights." (-> @*chat first :msg)))

      (restart-talk (talk/msg general/admin-chat "/restart"))
      (is (= "Wrong input. Expect: /restart 12345" (-> @*chat first :msg)))

      (restart-talk (talk/msg general/admin-chat "/restart 1"))
      (is (= "User with specific telegram id not found." (-> @*chat first :msg))))

    (testing "register user for restart"
      (start-talk (talk/msg "/start"))
      (start-talk (talk/msg "Bot Botovich"))
      (start-talk (talk/msg "gr1"))
      (whoami-talk (talk/msg "/whoami"))
      (is (= "Name: Bot Botovich; Group: gr1; Telegram ID: 1"
             (-> @*chat first :msg)))
      (start-talk (talk/msg "/start"))
      (is (= "You are already registered, to change your unform the teacher and send /whoami."
             (-> @*chat first :msg))))

    (testing "try but not actually restart"
      (restart-talk (talk/msg general/admin-chat "/restart 1"))
      (is (= "Restart this student?" (-> @*chat first :msg)))

      (restart-talk (talk/msg general/admin-chat "emm"))
      (is (= "Please yes or no?" (-> @*chat first :msg)))

      (restart-talk (talk/msg general/admin-chat "no"))
      (is (= "Not restarted." (-> @*chat first :msg))))

    (testing "restart"
      (restart-talk (talk/msg general/admin-chat "/restart 1"))
      (restart-talk (talk/msg general/admin-chat "yes"))
      (is (= (list {:msg "You can use /start once more.", :id 1}
                   {:msg "Restarted: 1", :id 0}) (->> @*chat (take 2))))

      (start-talk (talk/msg "/start"))
      (is (= "Hi, I'm a bot for your course. I will help you with your works. What is your name?"
             (-> @*chat first :msg))))))
