(ns course-bot.misc
  (:require [clojure.string :as str])
  (:require [clojure.java.io :as io]))

(defn read-config
  ([filename] (read-config filename true))
  ([filename skip]
   (read-string (try
                  (slurp (str (or (System/getenv "CONF_PATH") "conf-example") "/" filename))
                  (catch Exception e
                    (if skip "nil" (throw e)))))))

(defn read-configs
  ([sub-path] (read-configs sub-path true))
  ([sub-path skip]
   (->> (try (let []
               (file-seq (io/file (str (or (System/getenv "CONF_PATH") "conf-example") "/" sub-path))))
             (catch Exception e
               (if skip "()" (throw e))))
        (filter #(and (.isFile %) (str/ends-with? (.getName %) ".edn")))
        (map #(let [name (-> % .getName (str/replace #".edn" ""))
                    dt (read-string (slurp %))]
                [name dt]))
        (into {}))))

;; TODO: make time-zone explicite

(defn today [] (.getTime (new java.util.Date)))

(defn read-time [s] (.getTime (.parse (java.text.SimpleDateFormat. "yyyy.MM.dd HH:mm") s)))

(defn str-time [dt] (.format (java.text.SimpleDateFormat. "yyyy.MM.dd HH:mm") dt))
