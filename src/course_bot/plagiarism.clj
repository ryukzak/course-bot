(ns course-bot.plagiarism
  (:require [consimilo.core :as consimilo])
  (:require [codax.core :as codax]))

(def top-k 7)
(def cosine-threshold 25.00)

(defn add-to-forest
  "Add new entry to lsh-forest"
  [forest essay-text essay-code id]
  (consimilo/add-strings-to-forest
    [{:id (str essay-code id) :features essay-text}]
    :forest forest))

(defn get-test-forest
  [path]
  (try
    (consimilo/thaw-forest path)
    (catch Exception e
      (consimilo/add-strings-to-forest
        [{:id "test-id" :features "test id text"}]))))

(defn get-vector-cosine
  "Returns the cosine between two most similar vectors
  in existing forest"
  [forest essay-text]
  (let [similar-vectors (consimilo/similarity-k
                          forest
                          top-k
                          essay-text
                          :sim-fn :cosine)]
    (apply min-key val similar-vectors)))

(defn print-plagiarise-log
  [forest-key cosine plag-essay-text plag-id plag-code tx]
  (let [code-list (split-at 6 forest-key)
        essay-code (apply str (first code-list))
        id (apply str (second code-list))
        essay-text (codax/get-at tx [id :essays essay-code :text])]
    (println
      (format "Essay with code '%s' submitted by '%s' was plagiarised from '%s', essay code: '%s' with cosine %s.\nOriginal essay text:\n%s\nPlagiarised essay text:\n%s",
              plag-code, plag-id, id, essay-code, cosine, essay-text, plag-essay-text))
    true))

(defn is-essay-plagiarised
  [forest essay-text id essay-code tx]
  (let [minimal-vector
        (get-vector-cosine forest essay-text)]
    (if (< (val minimal-vector) cosine-threshold)
      (print-plagiarise-log (name (key minimal-vector)) (val minimal-vector) essay-text id essay-code tx)
      false)))