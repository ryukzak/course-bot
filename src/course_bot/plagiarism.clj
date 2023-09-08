(ns course-bot.plagiarism
  (:require [consimilo.core :as consimilo]))

(def top-k 7)
(def cosine-threshold 20.00)

(defn add-to-forest
  "Add new entry to lsh-forest"
  [forest essay-text id]
  (consimilo/add-strings-to-forest
    [{:id id :features essay-text}]
    :forest forest))

(defn get-vector-cosine
  "Returns the cosine between two most similar vectors
  in existing forest"
  [forest essay-text]
  (let [similar-vectors (consimilo/similarity-k
                          forest
                          top-k
                          essay-text
                          :sim-fn :cosine)]
    (if (some #(Double/isNaN %) (vals similar-vectors))
      0.00
      (val
        (apply min-key val similar-vectors)))))

(defn is-essay-plagiarised
  [forest essay-text]
  (< (get-vector-cosine forest essay-text) cosine-threshold))