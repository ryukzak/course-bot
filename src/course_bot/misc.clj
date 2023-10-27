(ns course-bot.misc
  (:require [clojure.java.io :as io]))

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
