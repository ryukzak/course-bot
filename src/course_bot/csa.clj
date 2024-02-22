(ns course-bot.csa
  (:gen-class)
  (:require [codax.core :as codax]
            [compojure.core :refer [POST defroutes]]
            [compojure.route :as route]
            [course-bot.essay :as essay]
            [course-bot.general :as general]
            [course-bot.internationalization :as i18n :refer [tr]]
            [course-bot.misc :as misc]
            [course-bot.plagiarism :as plagiarism]
            [course-bot.presentation :as pres]
            [course-bot.quiz :as quiz]
            [course-bot.report :as report]
            [course-bot.talk :as talk]
            [morse.api :as api]
            [morse.handlers :as handlers]
            [morse.polling :as polling]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.json :as middleware]))

(i18n/set-locales [:ru :en])
(i18n/add-dict
  {:en
   {:csa
    {:start-:conf "Bot activated, my Lord! Configuration: %s"
     :webhook-:url "Webhook is set, my Lord: %s"
     :dot "."
     :stop "Bot is dead, my Lord!"
     :unknown-:message "Unknown message: %s"
     :db-failure "I failed to reach the database, my Lord!"
     :db-failure-path "Can't find the database path, my Lord!"}}
   :ru
   {:csa
    {:start-:conf "Бот активирован, мой господин! Конфигурация: %s"
     :webhook-:url "Вебхук установлен, мой господин: %s"
     :stop "Бот погиб, мой господин!"
     :unknown-:message "Неизвестное сообщение: %s, а вы точно мой господин?"
     :db-failure "Не удалось подключиться к базе данных, мой господин!"
     :db-failure-path "Не удалось найти путь к базе данных, мой господин!"}}})

(defn open-database-or-fail [path]
  (if path
    (try
      (codax/open-database! path
        :backup-fn (misc/codax-backup-fn
                     {:dir path
                      :archive-deep (* 2 24 60 60 1000)}))
      (catch Exception e
        (println (tr :csa/db-failure))
        (println (.getMessage e))
        (System/exit 1)))
    (do
      (println (tr :csa/db-failure-path))
      (System/exit 1))))

(declare bot-api id message)

(defn -main [& _args]
  (let [conf-file (or (System/getenv "CONF")
                      (throw (ex-info "CONF env variable is not setted" {})))
        {token :token
         db-path :db-path
         plagiarism-path :plagiarism-path
         webhook-url :webhook-url
         webhook-secrete :webhook-secrete
         webhook-port :webhook-port
         :as conf} (misc/get-config conf-file)
        db (open-database-or-fail db-path)
        plagiarism-db (plagiarism/open-path-or-fail plagiarism-path)]

    (handlers/defhandler bot-api
      (general/start-talk db conf)
      (general/whoami-talk db conf)

      (general/listgroups-talk db conf)

      (pres/setgroup-talk db conf "lab1")
      (pres/submissions-talk db conf "lab1")
      (pres/submit-talk db conf "lab1")
      (pres/schedule-talk db conf "lab1")
      (pres/soon-talk db conf "lab1")
      (pres/feedback-talk db conf "lab1")

      (pres/agenda-talk db conf "lab1")

      ;; lab1 for admin
      (pres/check-talk db conf "lab1")
      (pres/drop-talk db conf "lab1" false)
      (pres/drop-talk db conf "lab1" true)
      (pres/all-scheduled-descriptions-dump-talk db conf "lab1")
      (pres/lost-and-found-talk db conf "lab1")

      (talk/when-handlers (:essay1 conf)
        (essay/submit-talk db conf "essay1" plagiarism-db)
        (essay/status-talk db conf "essay1")
        (essay/assignreviewers-talk db conf "essay1")
        (essay/not-assigned-talk db conf "essay1")
        (essay/review-talk db conf "essay1")
        (essay/myfeedback-talk db conf "essay1")
        (essay/reportabuse-talk db conf "essay1")
        (essay/warmup-plagiarism-talk db conf "essay1" plagiarism-db))

      (talk/when-handlers (:essay2 conf)
        (essay/submit-talk db conf "essay2" plagiarism-db)
        (essay/status-talk db conf "essay2")
        (essay/assignreviewers-talk db conf "essay2")
        (essay/not-assigned-talk db conf "essay2")
        (essay/review-talk db conf "essay2")
        (essay/myfeedback-talk db conf "essay2")
        (essay/reportabuse-talk db conf "essay2")
        (essay/warmup-plagiarism-talk db conf "essay2" plagiarism-db))

      (talk/when-handlers (:essay3 conf)
        (essay/submit-talk db conf "essay3" plagiarism-db)
        (essay/status-talk db conf "essay3")
        (essay/assignreviewers-talk db conf "essay3")
        (essay/not-assigned-talk db conf "essay3")
        (essay/review-talk db conf "essay3")
        (essay/myfeedback-talk db conf "essay3")
        (essay/reportabuse-talk db conf "essay3")
        (essay/warmup-plagiarism-talk db conf "essay3" plagiarism-db))

      (quiz/startquiz-talk db conf)
      (quiz/stopquiz-talk db conf)
      (quiz/quiz-talk db conf)

      (report/report-talk db conf
        "ID" report/stud-id
        "name" report/stud-name
        "group" report/stud-group
        "lab1-group" (pres/report-presentation-group "lab1")
        "lab1-rank" (pres/report-presentation-avg-rank "lab1")
        "lab1-score" (pres/report-presentation-score conf "lab1")
        "lab1-count" (pres/lesson-count "lab1")
        "failed-tests" (quiz/fail-tests conf)
        "success-test-percent" (quiz/success-tests-percent conf)
        "essay1" (essay/essay-score "essay1")
        "essay1-reviews" (essay/review-score conf "essay1")
        "essay2" (essay/essay-score "essay2")
        "essay2-reviews" (essay/review-score conf "essay2")
        "essay3" (essay/essay-score "essay3")
        "essay3-reviews" (essay/review-score conf "essay3")
        "stud-chat" report/stud-chat
        "stud-old-info" report/stud-old-info)

      (plagiarism/restore-forest-talk db conf plagiarism-db)
      (general/warning-on-edited-message conf)

      (general/help-talk db conf)
      (general/description-talk db conf)
      (handlers/message {{id :id} :chat :as message}
        (let [err (format (tr :csa/unknown-:message) message)
              cropped (subs err 0 (min 4096 (count err)))]
          (println err)
          (talk/send-text token id cropped))))

    #_:clj-kondo/ignore
    (defroutes bot-routes
      (POST (str "/handler-" webhook-secrete)
        {body :body :as request}
        (bot-api body)
        "ok")
      (route/not-found "Not Found"))

    #_:clj-kondo/ignore
    (def bot-app
      (-> bot-routes
          (middleware/wrap-json-body {:keywords? true})
          (middleware/wrap-json-response)))

    (println (format (tr :csa/start-:conf) conf-file))

    (cond (some? webhook-url)
          (let [url (str webhook-url "-" webhook-secrete)]
            (println (tr :csa/webhook-:url) url)
            (api/set-webhook token url)
            (jetty/run-jetty bot-app {:port webhook-port}))

          :else ; polling
          (do (api/set-webhook token nil)
              (loop [channel (polling/start token bot-api)]
                (Thread/sleep 500)
                (print (tr :csa/dot)) (flush)
                (if (.closed? channel)
                  (do (print (tr :csa/stop))
                      (recur (polling/start token bot-api)))
                  (recur channel)))
              (println (tr :csa/stop))))))
