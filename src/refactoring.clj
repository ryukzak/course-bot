(ns refactoring
  "Just a scripts to automate refactoring in the project."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [rewrite-clj.zip :as z]))

(defn str-tr->tr [src]
  (let [str-tr? (fn [zloc]
                  (if (str/starts-with? (z/string zloc) "#_:clj-kondo")
                    false
                    (let [sexpr (z/sexpr zloc)]
                      (and (-> sexpr list?)
                           (-> sexpr first (= 'str))
                           (-> sexpr count (= 2))
                           (-> sexpr second list?)
                           (-> sexpr second (first) (= 'tr))))))]
    (-> (loop [zloc (z/of-string src)]
          (let [str-tr (z/find zloc z/next str-tr?)
                tr (-> str-tr z/down z/right)]
            (if (some? str-tr)
              (recur (z/replace str-tr (z/sexpr tr)))
              zloc)))
        z/root-string)))

(defn str-format->format [src]
  (let [str-tr? (fn [zloc]
                  (if (str/starts-with? (z/string zloc) "#_:clj-kondo")
                    false
                    (let [sexpr (z/sexpr zloc)]
                      (and (-> sexpr list?)
                           (-> sexpr first (= 'str))
                           (-> sexpr count (= 2))
                           (-> sexpr second list?)
                           (-> sexpr second (first) (= 'format))))))]
    (-> (loop [zloc (z/of-string src)]
          (let [str-tr (z/find zloc z/next str-tr?)
                tr (-> str-tr z/down z/right)]
            (if (some? str-tr)
              (recur (z/replace str-tr (z/sexpr tr)))
              zloc)))
        z/root-string)))

(defn rewrite [filename]
  (println filename)
  (spit filename (-> (slurp filename)
                     str-tr->tr
                     str-format->format
                     ;; TODO: force-new-test-style
                     )))

(defn clj-source [dir]
  (->> (file-seq (io/as-file dir))
       (filter #(.isFile %))
       (map #(.getPath %))
       (filter #(str/ends-with? % ".clj"))))

(->> (clj-source ".")
     (map rewrite)
     doall)
