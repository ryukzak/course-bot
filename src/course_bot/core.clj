(ns course-bot.core
  (:require [codax.core :as c]
            [clojure.data.csv :as csv])

  (:require [clojure.java.io :as io])
  (:require [course-bot.dialog :as d]
            [course-bot.talk :as talk]
            [course-bot.quiz :as quiz]
            [course-bot.essay :as essay]
            [course-bot.general :as general]
            [course-bot.csa.lab1 :as lab1]
            [course-bot.presentation :as pres]
            [course-bot.report :as report])
  (:require [morse.handlers :as h]
            [morse.api :as t]
            [morse.polling :as p])
  (:require [clojure.string :as str]
            [clojure.pprint :refer [pprint]])
  (:gen-class))

;; depricated

(def db (c/open-database! "codax-db-test"))
(def token "")
(def group-list '())
(def admin-chat 123)
(def assert-admin 0)

(def help-msg
  "start - регистрация
help - посмотреть информацию о существующих командах
whoami - какая у меня группа
listgroups - списки групп по мнению ботов
quiz - начать лекционный тест
essay1 - загрузить первое эссе
essay1review - сделать ревью на первое эссе
essay1status - посмотреть сколько ревью собрано на первое эссе
essay1results - результаты рассмотрения моего первого эссе
essay2 - загрузить второе эссе
essay2review - сделать ревью на второе эссе
essay2status - посмотреть сколько ревью собрано на второе эссе
essay2results - результаты рассмотрения моего второго эссе
essay3 - загрузить третье эссе
essay3review - сделать ревью на третье эссе
essay3status - посмотреть сколько ревью собрано на третье эссе
essay3results - результаты рассмотрения моего третьего эссе
")

(declare bot-api id chat text)
(h/defhandler bot-api
  (general/start-talk db token)
  ;; (general/restart-talk db token assert-admin)
  (general/whoami-talk db token)
  (general/listgroups-talk db token)

  (report/report-talk db token assert-admin)

  (essay/essay-talk db token "essay1")
  (essay/assign-essay-talk db token "essay1" assert-admin)
  (essay/essay-review-talk db token "essay1")
  (essay/essay-status-talk db token "essay1")
  (essay/essay-results-talk db token "essay1")
  (essay/essays-without-review-talk db token "essay1" assert-admin)

  (essay/essay-talk db token "essay2")
  (essay/assign-essay-talk db token "essay2" assert-admin)
  (essay/essay-review-talk db token "essay2")
  (essay/essay-status-talk db token "essay2")
  (essay/essay-results-talk db token "essay2")
  (essay/essays-without-review-talk db token "essay2" assert-admin)

  (essay/essay-talk db token "essay3")
  (essay/assign-essay-talk db token "essay3" assert-admin)
  (essay/essay-review-talk db token "essay3")
  (essay/essay-status-talk db token "essay3")
  (essay/essay-results-talk db token "essay3")
  (essay/essays-without-review-talk db token "essay3" assert-admin)

  (essay/essay-talk db token "essay10")
  (essay/assign-essay-talk db token "essay10" assert-admin)
  (essay/essay-review-talk db token "essay10")
  (essay/essay-status-talk db token "essay10")
  (essay/essay-results-talk db token "essay10")
  (essay/essays-without-review-talk db token "essay10" assert-admin)

  (essay/essay-talk db token "essay20")
  (essay/assign-essay-talk db token "essay20" assert-admin)
  (essay/essay-review-talk db token "essay20")
  (essay/essay-status-talk db token "essay20")
  (essay/essay-results-talk db token "essay20")
  (essay/essays-without-review-talk db token "essay20" assert-admin)

  (essay/essay-talk db token "essay30")
  (essay/assign-essay-talk db token "essay30" assert-admin)
  (essay/essay-review-talk db token "essay30")
  (essay/essay-status-talk db token "essay30")
  (essay/essay-results-talk db token "essay30")
  (essay/essays-without-review-talk db token "essay30" assert-admin)

  ;; (pres/setgroup-talk db token "lab1")
  ;; (pres/submit-talk db token "lab1")
  ;; (pres/submissions-talk db token "lab1")
  ;; (pres/check-talk db token "lab1" assert-admin)
  ;; (pres/schedule-talk db token "lab1")
  ;; (pres/agenda-talk db token "lab1")
  ;; (pres/drop-talk db token "lab1" assert-admin admin-chat)
  ;; (pres/feedback-talk db token "lab1")
  ;; (pres/evaluate-talk db token "lab1" assert-admin)
  ;; (pres/history-talk db token "lab1")
  ;; (pres/participants-talks db token "lab1")

  (h/command "help" {{id :id} :chat} (t/send-text token id (talk/helps)))
  ;; (h/message {{id :id} :chat :as message}
  ;;     (println "Intercepted message: " message)
  ;;     (t/send-text token id "I don't do a whole lot ... yet."))
  )

(c/with-read-transaction [db tx]
  (c/get-at tx [admin-chat]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Bot activated, my Lord!")
  (loop [channel (p/start token bot-api)]
    (Thread/sleep 1000)
    ;; (print ".")(flush)
    (when-not (.closed? channel)
      (recur channel)))
  (println "Bot is dead, my Lord!"))
