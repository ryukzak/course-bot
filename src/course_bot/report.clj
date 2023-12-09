(ns course-bot.report
  (:require [codax.core :as codax]
            [course-bot.misc :as misc])
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint])
  (:require [course-bot.talk :as talk]))

(defn stud-id [_tx _data id] id)
(defn stud-name [_tx data id] (-> data (get id) :name))
(defn stud-group [_tx data id] (-> data (get id) :group))
(defn stud-chat [_tx data id] (-> data (get id) :stud-chat (#(with-out-str (pprint/pprint %)))))
(defn stud-old-info [_tx data id] (-> data (get id) :old-info (#(with-out-str (pprint/pprint %)))))

(defn send-report [tx token id fields]
  (let [data (codax/get-at tx [])
        ids (->> data keys (filter number?))
        columns (map first fields)
        data (cons columns
                   (map (fn [id] (map (fn [[_key get]] (get tx data id)) fields)) ids))
        dt (.format (java.text.SimpleDateFormat. "yyyy-MM-dd-HH-mm-Z") (misc/today))
        filename (str "tmp/" dt "-report.csv")]
    (io/make-parents filename)
    (with-open [writer (io/writer filename)]
      (csv/write-csv writer data :separator \;))
    (talk/send-document token id (io/file filename))))

(defn report-talk [db {token :token :as conf} & fields]
  (talk/def-command db "report" "receive report"
    (fn [tx {{id :id} :from}]
      (when-not (some #(= id %) (-> conf :can-receive-reports))
        (talk/send-text token id "That action was restricted for specific users.")
        (talk/stop-talk tx))

      (talk/send-text token id "Report file:")
      (send-report tx token id (partition 2 fields))
      tx)))
