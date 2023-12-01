(ns course-bot.plagiarism
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [course-bot.misc :as misc])
  (:require [consimilo.core :as consimilo])
  (:require [course-bot.general :as general]
            [course-bot.internationalization :as i18n :refer [tr]]
            [course-bot.plagiarism :as plagiarism]
            [course-bot.talk :as talk]))

(i18n/add-dict
 {:en
  {:plagiarism
   {:forest-failure "I failed to reach forest file, my Lord!"
    :processed-1 "Processed %d texts, my Lord!"
    :restore-forest-done "Forest restored, my Lord!"}}
  :ru
  {:plagiarism
   {:forest-failure "Не удалось подключиться к хэш-лесу, мой господин!"
    :processed-1 "Обработано %d текстов, мой господин!"
    :restore-forest-done "Хэш-лес восстановлен, мой господин!"}}})

(def default-conf {:top-k 10
                   :cosine-threshold 30.00
                   :sim-fn :cosine})

(comment 'plagiarism-db
         (assoc default-conf
                :forest-filename "tmp/plagiarism/forest"
                :forest 'CONSIMILO-FOREST
                :texts-path "tmp/plagiarism/texts"
                :bad-texts-path "tmp/plagiarism/bad-texts-path"))

(defn open-path-or-fail [path]
  (let [forest-filename (str path "/forest")
        texts-path (str path "/texts")
        db (assoc default-conf
                  :forest-filename forest-filename
                  :texts-path texts-path
                  :bad-texts-path (str path "/bad-texts"))]
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
  [{forest-filename :forest-filename forest :forest texts-path :texts-path :as plagiarism-db} key text]
  (consimilo/add-strings-to-forest [{:id key :features text}] :forest forest)
  (when forest-filename
    (consimilo/freeze-forest forest forest-filename))
  (when texts-path
    (let [filename (str texts-path "/" key ".txt")]
      (io/make-parents filename)
      (spit filename text)))
  plagiarism-db)

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

(defn find-original
  ([plagiarism-db text] (find-original plagiarism-db text nil))
  ([{cosine-threshold :cosine-threshold :as plagiarism-db} text skip-key]
   (let [similars (->> (similars plagiarism-db text)
                       (filter #(not= (:key %) skip-key))
                       (filter #(< (:similarity %) cosine-threshold)))]
     (when (not (empty? similars))
       (apply min-key :similarity similars)))))

(defn get-text [{texts-path :texts-path} key]
  (when texts-path
    (let [filename (str texts-path "/" key ".txt")]
      (when (io/file filename)
        (slurp filename)))))

(defn restore-forest-talk [db {token :token :as conf} {texts-path :texts-path :as plagiarism-db}]
  (let [cmd "restoreforest"
        help (tr :essay/restore-forest-help)]
    (talk/def-command db cmd help
      (fn [tx {{id :id} :from}]
        (general/assert-admin tx conf id)
        (->> (file-seq (io/file texts-path))
             (filter #(.isFile %))
             (filter #(str/ends-with? (.getName %) ".txt"))
             (map (fn [file] {:file file :key (str/replace (.getName file) #"\.txt$" "")}))
             (map (fn [{file :file key :key}]
                    (register-text! plagiarism-db key (slurp file))))
             (misc/count-with-report
              50 #(talk/send-text token id (format (tr :plagiarism/processed-1) %))))
        (talk/send-text token id (tr :plagiarism/restore-forest-done))
        (talk/stop-talk tx)))))
