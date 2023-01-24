(ns course-bot.misc
  (:require [clojure.string :as str])
  (:require [clojure.java.io :as io]))

(defn keyword-path [path file]
  (let [from-path (-> file
                      .getParent
                      (str/replace (re-pattern (str "^" path)) "")
                      (str/split #"/")
                      (#(->> % (filter not-empty) (map keyword))))
        from-name (-> file
                      .getName
                      (str/replace #".edn" "")
                      keyword)]
    (concat from-path (list from-name))))

(defn get-config [path]
  (let [files (->> (file-seq (io/file path))
                   (filter #(and (.isFile %) (str/ends-with? (.getName %) ".edn"))))

        dt (map (fn [file]
                  [(keyword-path path file) (read-string (slurp file))])
                files)
        {inlines true
         paths false} (group-by #(-> % first last (= :inline)) dt)]

    (reduce (fn [m [ks v]]
              (cond (= ks '(:inline)) (into m v)
                    (= (last ks) :inline) (assoc-in m (drop-last ks) v)
                    :else (assoc-in m ks v))) {} (concat inlines paths))))

(defn today [] (.getTime (new java.util.Date)))

(defn read-time [s] (.getTime (.parse (java.text.SimpleDateFormat. "yyyy.MM.dd HH:mm Z") s)))
(defn str-time [dt] (.format (java.text.SimpleDateFormat. "yyyy.MM.dd HH:mm Z") dt))

(defn normalize-time [dt]
  (str-time (read-time dt)))

(defn round-2 [num] (double (/ (Math/round (* 100.0 num)) 100)))