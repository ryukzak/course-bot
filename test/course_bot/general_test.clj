(ns course-bot.general-test
  (:require [course-bot.general :as general]
            [course-bot.talk :as talk]
            [course-bot.talk-test :as ttalk]
            [codax.core :as codax]
            [clojure.test :refer :all]))

(declare start-talk-test restart-talk-test)

(talk/deftest start-talk-test [db *chat]
  (let [start-talk (ttalk/mock-talk general/start-talk db "TOKEN")]
    (start-talk "bla-bla")
    (is (= '() @*chat))

    (testing "registration"
      (start-talk "/start")
      (ttalk/in-history *chat "Hi, I'm a bot for your course. I will help you with your work. What is your name?")

      (start-talk "Bot Botovich")
      (ttalk/in-history *chat "What is your group (gr1, gr2)?")

      (start-talk "wrong group")
      (ttalk/in-history *chat "I don't know this group. Please, repeat it (gr1, gr2):")

      (start-talk "gr1")
      (ttalk/in-history *chat "Hi:"
                        "Name: Bot Botovich; Group: gr1; Telegram ID: 1"
                        "Send /help for help."))

    (testing "second registration"
      (start-talk "/start")
      (ttalk/in-history *chat "You are already registered. To change your information, contact the teacher and send /whoami"))))

(talk/deftest restart-talk-test [db *chat]
  (let [start-talk (ttalk/mock-talk general/start-talk db "TOKEN")
        whoami-talk (ttalk/mock-talk general/whoami-talk db "TOKEN")
        restart-talk (ttalk/mock-talk general/restart-talk db "TOKEN" general/assert-admin)]
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
                        [0 "Restarted: 1"]
                        [1 "You can use /start once more."])

      (start-talk "/start")
      (ttalk/in-history *chat "Hi, I'm a bot for your course. I will help you with your work. What is your name?"))))
