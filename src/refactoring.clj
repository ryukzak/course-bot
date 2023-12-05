(ns refactoring
  "Just a scripts to automate refactoring in the project.

  Requires manual finiliazing: imports, cljfmt, fix kondo suggestions, etc."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [rewrite-clj.parser :as p]
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

(defn handlers->test-handler [src]
  (let [p? (fn [zloc]
             (if (str/starts-with? (z/string zloc) "#_:clj-kondo")
               false
               (and (-> zloc z/sexpr (= 'talk)) ; talk
                    (some-> zloc z/right z/down z/sexpr (= 'tt/handlers)) ; (tt/handers ...)
                    )))]
    (-> (loop [zloc (z/of-string src)]
          (let [talk-handlers (z/find zloc z/next p?)]
            (if (some? talk-handlers)
              (recur (-> talk-handlers
                         (z/replace {'talk :talk '*chat :*chat})
                         z/insert-newline-left
                         z/insert-newline-right
                         z/right
                         z/down
                         (z/replace 'tt/test-handler)))
              zloc)))
        z/root-string)))

(defn talk-match-text->answers?-talk [src]
  (let [p? (fn [zloc]
             (if (str/starts-with? (z/string zloc) "#_:clj-kondo")
               false
               (and (some-> zloc z/down z/sexpr (= 'talk)) ; (talk ...)
                    (some-> zloc z/right z/down z/sexpr (= 'tt/match-text)) ; (tt/match-text ...)
                    )))]

    (-> (loop [zloc (z/of-string src)]
          (let [item (z/find zloc z/next p?)]
            (if (some? item)
              (recur (let [talk (-> item z/node) ; (talk ...)
                           match-text (-> item z/right z/sexpr last) ; (tt/match-text ... "msg") -> "msg"
                           ]
                       (-> item
                           (z/replace (list 'is (list 'answers? talk (p/parse-string "\n") match-text)))
                           z/right z/remove)))
              zloc)))
        z/root-string)))

;; (rewrite "test/course_bot/quiz_test.clj")
;; (rewrite "./test/course_bot/general_test.clj")

(defn rewrite [filename]
  (println filename)
  (spit filename (-> (slurp filename)
                     str-tr->tr
                     str-format->format
                     handlers->test-handler
                     talk-match-text->answers?-talk)))

(defn clj-source [dir]
  (->> (file-seq (io/as-file dir))
       (filter #(.isFile %))
       (map #(.getPath %))
       (filter #(str/ends-with? % ".clj"))))

;; (->> (clj-source ".") (map rewrite) doall)
