(ns course-bot.misc
  (:require [clojure.java.io :as io]
            [codax.core :as codax]))

(defn inline? [v] (when (and (vector? v) (= :inline (first v)))
                    (second v)))

(declare get-config)

(defn inline [path conf]
  (cond (map? conf) (update-vals conf
                                 (fn [v]
                                   (if-let [sub-conf (inline? v)]
                                     (inline path (read-string (slurp (str path "/" sub-conf))))
                                     (inline path v))))
        :else conf))

(defn get-config [filename]
  (let [path (-> (io/file filename) .getAbsolutePath io/file .getParent)
        conf (read-string (slurp filename))]
    (inline path conf)))

(defn today [] (.getTime (new java.util.Date)))

(defn read-time [s] (.getTime (.parse (java.text.SimpleDateFormat. "yyyy.MM.dd HH:mm Z") s)))
(defn str-time [dt] (.format (java.text.SimpleDateFormat. "yyyy.MM.dd HH:mm Z") dt))

(defn filename-time [dt] (.format (java.text.SimpleDateFormat. "yyyyMMddHHmm") dt))

(defn normalize-time [dt]
  (str-time (read-time dt)))

(defn round-2 [num] (double (/ (Math/round (* 100.0 num)) 100)))

(defn count-with-report [n callback coll0]
  (loop [counter 0
         coll coll0]
    (let [head (take n coll)
          tail (drop n coll)
          counter' (+ counter (count head))]
      (callback counter')
      (if (empty? tail)
        count
        (recur counter' tail)))))

(defn codax-backup-fn [info] (println "\n"
                                      "codax-backup:" info))

(defn merge-codaxs
  "Merge two codax databases, a and b, into out-db."

  [a-db-path b-db-path out-db-path]

  (let [a-db (codax/open-database! a-db-path)
        b-db (codax/open-database! b-db-path)
        a-keys (-> (codax/get-at! a-db []) keys)
        merged-data (doall
                     (->> a-keys
                          (map #(try
                                  (let [dt (codax/get-at! b-db [%])] [:ok % dt])
                                  (catch Exception _e [:fail % (codax/get-at! a-db [%])])))))
        failed-records (->> merged-data
                            (filter (fn [[status _k _v]] (= status :fail)))
                            (map (fn [[_status k v]]
                                   (str k " - " (:group v) " - " (:name v)))))
        out-db (codax/open-database! out-db-path :backup-fn codax-backup-fn)]
    (codax/close-database! a-db-path)
    (codax/close-database! b-db-path)
    (doall (->> merged-data
                (map (fn [[_ k v]] (codax/assoc-at! out-db k v)))))
    (codax/close-database! out-db-path)
    failed-records))

(quote (merge-codaxs "../csa-db-snapshot-2023-10-30-22-58"
                     "../csa-db-snapshot-2023-11-05-16-55"
                     "../csa-db-merged"))
