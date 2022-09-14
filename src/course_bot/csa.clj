(ns course-bot.csa
  (:require [morse.handlers :as handlers]
            [morse.polling :as polling])
  (:require [codax.core :as codax])
  (:require [course-bot.misc :as misc]
            [course-bot.quiz :as quiz]
            [course-bot.general :as general])
  (:require [course-bot.talk :as talk]))

(def conf (misc/get-config "/Users/penskoi/src/edu-csa-internal"))

(def token (-> conf :token))
(def db-path (-> conf :db-path))

(defn assert-admin [tx token id]
  (when-not (= id (-> conf :general :admin-chat))
    (talk/send-text token id "That action requires admin rights.")
    (talk/stop-talk tx)))

(def db (codax/open-database! db-path))

(declare bot-api id)

(handlers/defhandler bot-api
  (general/start-talk db conf)
  (general/restart-talk db conf)
  (general/whoami-talk db conf)
  (general/listgroups-talk db conf)

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
