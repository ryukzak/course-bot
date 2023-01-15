(ns course-bot.report
  (:require [codax.core :as codax]
            [codax.core :as c]
            [course-bot.presentation :as pres]
            [course-bot.misc :as misc]
            [course-bot.general :as general]
            [clojure.string :as str])
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv])
  (:require [course-bot.talk :as talk]
            [course-bot.quiz :as quiz]
            [course-bot.presentation :as pres]
            [course-bot.essay :as essay]))

(defn stud-id [_tx _data id] id)
(defn stud-name [_tx data id] (-> data (get id) :name))
(defn stud-group [_tx data id] (-> data (get id) :group))

(defn send-report [tx token id fields]
  (let [data (codax/get-at tx [])
        ids (->> data keys (filter number?))
        columns (map first fields)
        data (cons columns
                   (map (fn [id] (map (fn [[_key get]] (get tx data id)) fields)) ids))]

    (let [dt (.format (java.text.SimpleDateFormat. "yyyy-MM-dd-HH-mm-Z") (misc/today))
          fn (str dt "-report.csv")]
      (with-open [writer (io/writer fn)]
        (csv/write-csv writer data :separator \;))
      (talk/send-document token id (io/file fn)))))

(defn report-talk [db {token :token :as conf} & fields]
  (talk/def-command db "report" "receive report"
    (fn [tx {{id :id} :from}]
      (when-not (some #(= id %) (-> conf :can-receive-reports))
        (talk/send-text token id "That action was restricted for specific users.")
        (talk/stop-talk tx))

      (talk/send-text token id "Report file:")
      (send-report tx token id (partition 2 fields))
      tx)))
