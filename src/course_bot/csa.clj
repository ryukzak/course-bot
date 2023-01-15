(ns course-bot.csa
  (:require [morse.handlers :as handlers]
            [morse.polling :as polling]
            [codax.core :as codax]
            [course-bot.report :as report])
  (:require [course-bot.misc :as misc]
            [course-bot.quiz :as quiz]
            [course-bot.presentation :as pres]
            [course-bot.general :as general]
            [course-bot.essay :as essay]
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
  (general/renameme-talk db conf)
  (general/listgroups-talk db conf)

  (pres/setgroup-talk db conf "lab1")
  (pres/submit-talk db conf "lab1")
  (pres/submissions-talk db conf "lab1")
  (pres/check-talk db conf "lab1")
  (pres/schedule-talk db conf "lab1")
  (pres/agenda-talk db conf "lab1")
  (pres/soon-talk db conf "lab1")
  (pres/feedback-talk db conf "lab1")
  (pres/drop-talk db conf "lab1" false)
  (pres/drop-talk db conf "lab1" true)
  (pres/all-scheduled-descriptions-dump-talk db conf "lab1")

  (essay/submit-talk db conf "essay1")
  (essay/status-talk db conf "essay1")
  (essay/assignreviewers-talk db conf "essay1")
  (essay/review-talk db conf "essay1")
  (essay/myfeedback-talk db conf "essay1")

  (essay/submit-talk db conf "essay2")
  (essay/status-talk db conf "essay2")
  (essay/assignreviewers-talk db conf "essay2")
  (essay/review-talk db conf "essay2")
  (essay/myfeedback-talk db conf "essay2")

  (essay/submit-talk db conf "essay3")
  (essay/status-talk db conf "essay3")
  (essay/assignreviewers-talk db conf "essay3")
  (essay/review-talk db conf "essay3")
  (essay/myfeedback-talk db conf "essay3")

  (quiz/startquiz-talk db conf)
  (quiz/stopquiz-talk db conf)
  (quiz/quiz-talk db conf)

  (report/report-talk db conf
                      "ID" report/stud-id
                      "name" report/stud-name
                      "group" report/stud-group
                      "lab1-group" (pres/report-presentation-group "lab1")
                      "lab1-rank" (pres/report-presentation-avg-rank conf "lab1")
                      "lab1-score" (pres/report-presentation-score conf "lab1")
                      "lab1-count" (pres/lesson-count "lab1")
                      "failed-tests" (quiz/fail-tests conf)
                      "success-test-percent" (quiz/success-tests-percent conf)
                      "essay1" (essay/essay-score conf "essay1")
                      "essay1-reviews" (essay/review-score conf "essay1")
                      "essay2" (essay/essay-score conf "essay2")
                      "essay2-reviews" (essay/review-score conf "essay2")
                      "essay3" (essay/essay-score conf "essay3")
                      "essay3-reviews" (essay/review-score conf "essay3"))

  (handlers/command "help" {{id :id} :chat} (talk/send-text (-> conf :token) id (talk/helps)))

  (handlers/message {{id :id} :chat :as message}
                    (println "Unknown message: " message)
                    (talk/send-text token id "Unknown message")))

(defn run
  "I don't do a whole lot ... yet."
  [& args]
  (println "Bot activated, my Lord!")
  (loop [channel (polling/start token bot-api)]
    (Thread/sleep 500)
    (print ".") (flush)
    (if (.closed? channel)
      (do (print "Restart bot")
          (recur (polling/start token bot-api)))
      (recur channel)))
  (println "Bot is dead, my Lord!"))
