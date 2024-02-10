(ns course-bot.misc
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [codax.core :as codax]))

(defn pp-str [content]
  (with-out-str (pprint/pprint content)))

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
(defn str-time-in-utc [dt]
  (.format (let [f (java.text.SimpleDateFormat. "yyyy.MM.dd HH:mm Z")]
             (. f setTimeZone (java.util.TimeZone/getTimeZone "UTC"))
             f) dt))

(defn today-str-utc [] (str-time-in-utc (today)))

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

(defn codax-clean-archives [dir archive-dir archive-deep]
  (let [now (new java.util.Date)
        files (->> (io/file dir)
                   (file-seq)
                   (filter #(.isFile %))
                   (map (fn [file]
                          ;; manifest_20231111T161402Z_195657001998041 or "nodes_20231111T161402Z_195657001998041"
                          (let [filename (.getName file)
                                dt-str (re-find #"\d{8}T\d{6}Z" filename)
                                suffix (re-find #"_\d+$" filename)]
                            {:filename filename
                             :dt-str dt-str
                             :dir (.getParent file)
                             :key (str dt-str suffix)})))
                   (filter #(and (= dir (:dir %))
                                 (some? (:dt-str %))))
                   (map (fn [{filename :filename dt-str :dt-str :as info}]
                          (let [dt (.parse (java.text.SimpleDateFormat. "yyyyMMdd'T'HHmmss") dt-str)
                                ymd (.format (java.text.SimpleDateFormat. "yyyyMMdd") dt)
                                delta (- (.getTime now) (.getTime dt))]
                            (assoc info
                                   :filename filename
                                   :dt dt
                                   :ymd ymd
                                   :delta delta)))))

        archives (->> files
                      (group-by :key)
                      (map (fn [[_key files]]
                             (-> (first files)
                                 (dissoc :filename :key :dt-str :dir)
                                 (assoc :files (map :filename files))))))

        old-archives (->> archives
                          (filter #(> (:delta %) archive-deep)))

        grouped-by-days (->> old-archives
                             (group-by :ymd)
                             (map (fn [[_ymd archives]]
                                    (->> archives
                                         (sort-by :dt)
                                         reverse))))

        for-process (->> grouped-by-days
                         (map #(->> % (drop 1)
                                    (map :files)
                                    flatten))
                         flatten)]
    (doall (for [file for-process]
             (let [src (str dir "/" file)
                   dst (when (some? archive-dir)
                         (str archive-dir "/" file))]
               (println "move" src "->" dst)
               (try
                 (when (some? dst)
                   (io/make-parents (io/file dst))
                   (io/copy (io/file src) (io/file dst)))
                 (io/delete-file (io/file src))
                 (catch Exception e (println e))))))))

(defn codax-backup-fn [{:keys [dir archive-dir archive-deep]}]
  (fn [info]
    (println "\n" "codax-backup:" info)
    (codax-clean-archives dir archive-dir archive-deep)))

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

(defn doall* [s]
  (doall (tree-seq #(or (seq? %) (vector? %) (map? %)) identity s)) s)
