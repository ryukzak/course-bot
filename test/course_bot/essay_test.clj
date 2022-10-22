(ns course-bot.essay-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str])
  (:require [codax.core :as codax])
  (:require
   [course-bot.general :as general]
   [course-bot.essay :as essay]
   [course-bot.misc :as misc]
   [course-bot.talk :as talk]
   [course-bot.talk-test :as ttalk]))

(defn register-user [*chat start-talk id name]
  (testing "register user"
    (start-talk id "/start")
    (start-talk id name)
    (start-talk id "gr1")
    (start-talk id "/start")
    (ttalk/in-history *chat [id "You are already registered. To change your information, contact the teacher and send /whoami"])))

(declare db *chat)

(talk/deftest essay-submit-talk-test [db *chat]
  (let [conf (misc/get-config "conf-example")
        start-talk (ttalk/mock-talk general/start-talk db conf)
        essay-submit-talk (ttalk/mock-talk essay/submit-talk db conf "essay1")
        essay-status-talk (ttalk/mock-talk essay/status-talk db conf "essay1")]

    (register-user *chat start-talk 1 "u1")
    (register-user *chat start-talk 2 "u2")
    (essay-submit-talk 1 "/essay1submit")

    (ttalk/in-history *chat [1 "Отправьте текст эссе 'essay1' одним сообщением. Тема(-ы):"
                             ""
                             "essay1-topics-text"])
    (essay-submit-talk 1 "u1 essay1 text")
    (ttalk/in-history *chat
                      [1 "Текст вашего эссе\n<<<<<<<<<<<<<<<<<<<<"]
                      [1 "u1 essay1 text"]
                      [1 ">>>>>>>>>>>>>>>>>>>>"]
                      [1 "Загружаем (yes/no)?"])

    (testing "cancelation"
      (essay-submit-talk 1 "hmmm")
      (ttalk/in-history *chat [1 "What (yes or no)?"])
      (essay-submit-talk 1 "no")
      (ttalk/in-history *chat [1 "Cancelled."])
      (= nil (codax/get-at! db [1 :essays]))

      (essay-status-talk 1 "/essay1status")
      (ttalk/in-history *chat [1 "Всего эссе: 0"
                               "Человек сделало ревью: 0"
                               "Есть комплект ревью на: 0 эссе."]))

    (testing "submittion"
      (essay-submit-talk 1 "/essay1submit")
      (essay-submit-talk 1 "u1 essay1 text")
      (ttalk/in-history *chat [1 "Загружаем (yes/no)?"])
      (essay-submit-talk 1 "yes")
      (ttalk/in-history *chat [1 "Спасибо, текст загружен и скоро попадёт на рецензирование."])
      (essay-status-talk 1 "/essay1status")
      (ttalk/in-history *chat [1 "Всего эссе: 1"
                               "Человек сделало ревью: 0"
                               "Есть комплект ревью на: 0 эссе."])
      (is (= {:text "u1 essay1 text"}
             (codax/get-at! db [1 :essays "essay1"]))))

    (testing "re-submit"
      (essay-submit-talk 1 "/essay1submit")
      (ttalk/in-history *chat [1 "Ваше эссе 'essay1' уже загружено"]))))
