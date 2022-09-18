(ns course-bot.csa
  (:require [morse.handlers :as handlers]
            [morse.polling :as polling]
            [codax.core :as codax])
  (:require [course-bot.misc :as misc]
            [course-bot.quiz :as quiz]
            [course-bot.presentation :as pres]
            [course-bot.general :as general]
            [course-bot.talk :as talk]))

(def conf (misc/get-config "../edu-csa-internal"))

(def token (-> conf :token))
(def db-path (-> conf :db-path))

(def db (codax/open-database! db-path))

(declare bot-api id message)

(handlers/defhandler bot-api
  (general/start-talk db conf)
  (general/restart-talk db conf)
  (general/whoami-talk db conf)
  (general/listgroups-talk db conf)

  (pres/setgroup-talk db conf "lab1")
  (pres/submit-talk db conf "lab1")
  (pres/submissions-talk db conf "lab1")
  (pres/check-talk db conf "lab1")
  (pres/schedule-talk db conf "lab1")
  (pres/agenda-talk db conf "lab1")
  (pres/feedback-talk db conf "lab1")
  (pres/drop-talk db conf "lab1" false)
  (pres/drop-talk db conf "lab1" true)

  (quiz/startquiz-talk db conf)
  (quiz/stopquiz-talk db conf)
  (quiz/quiz-talk db conf)

  (handlers/command "help" {{id :id} :chat} (talk/send-text (-> conf :token) id (talk/helps)))

  (handlers/message {{id :id} :chat :as message}
                    (println "Unknown message: " message)
                    (talk/send-text token id "Unknown message")))

(defn run
  "I don't do a whole lot ... yet."
  [& args]
  (println "Bot activated, my Lord!")
  (loop [channel (polling/start token bot-api)]
    (Thread/sleep 1000)
    (print ".") (flush)
    (when-not (.closed? channel)
      (recur channel)))
  (println "Bot is dead, my Lord!"))
