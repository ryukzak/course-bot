(ns course-bot.plagiarism
  (:require [clojure.java.io :as io]
            [consimilo.core :as consimilo]
            [course-bot.plagiarism :as plagiarism])
  (:require [course-bot.general :as general :refer [tr]]))

(general/add-dict
 {:en
  {:plagiarism
   {:forest-failure  "I failed to reach forest file, my Lord!"}}
  :ru
  {:plagiarism
   {:forest-failure  "Не удалось подключиться к хэш-лесу, мой господин!"}}})

(def default-conf {:top-k 10
                   :cosine-threshold 25.00
                   :sim-fn :cosine})

(comment 'plagiarism-db
         (assoc default-conf
                :forest-filename "tmp/forest/data2"
                :forest 'CONSIMILO-FOREST
                :texts-path "tmp/texts"))

(defn open-path-or-fail [path]
  (let [forest-filename (str path "/forest")
        texts-path (str path "/texts")
        db (assoc default-conf
                  :forest-filename forest-filename
                  :texts-path texts-path)]
    (try
      (assoc db :forest (consimilo/thaw-forest forest-filename))
      (catch java.io.FileNotFoundException _
        (println "Forest file not found, creating new one...")
        (let [forest (consimilo/add-strings-to-forest [])]
          (io/make-parents forest-filename)
          (consimilo/freeze-forest forest forest-filename)
          (assoc db :forest forest)))
      (catch Exception e
        (println (tr :forest-failure))
        (println e)
        (System/exit 1)))))

(defn register-text!
  "Add new entry to lsh-forest.

  key should be a string to be consistent with in
  input and output and valid as a filename."
  ([{forest-filename :forest-filename forest :forest texts-path :texts-path :as plagiarism-db} key text]
   (consimilo/add-strings-to-forest [{:id key :features text}] :forest forest)
   (when forest-filename
     (consimilo/freeze-forest forest forest-filename))
   (when texts-path
     (let [filename (str texts-path "/" key ".txt")]
       (io/make-parents filename)
       (spit filename text)))
   plagiarism-db)
  ([forest essay-text essay-code id] ; FIXME: remove this
   (consimilo/add-strings-to-forest
    [{:id (str essay-code id) :features essay-text}]
    :forest forest)))

(defn similars [{forest :forest top-k :top-k sim-fn :sim-fn} text]
  (->> (consimilo/similarity-k forest top-k text :sim-fn sim-fn)
       (map (fn [[key similarity]]
              {:key (name key)
               :similarity (if (.equals ##NaN similarity)
                             0 ;; for some reason NaN is returned sometimes.
                             similarity)}))))

(defn sorted-similars [plagiarism-db text]
  (->> (similars plagiarism-db text)
       (sort-by :similarity)))

(defn find-original [{cosine-threshold :cosine-threshold :as plagiarism-db} text]
  (let [similars (->> (similars plagiarism-db text)
                      (filter #(< (:similarity %) cosine-threshold)))]
    (when (not (empty? similars))
      (apply min-key :similarity similars))))

(defn get-text [{texts-path :texts-path} key]
  (when texts-path
    (let [filename (str texts-path "/" key ".txt")]
      (when (io/file filename)
        (slurp filename)))))
