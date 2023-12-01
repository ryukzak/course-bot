(ns course-bot.internationalization
  (:require [clojure.string :as str])
  (:require [taoensso.tempura :as tempura]))

(def *tr-options-dict (atom {}))
(defn add-dict [dict]
  (swap! *tr-options-dict (partial merge-with merge) dict))

(def *tr-locales (atom [:en]))
(defn set-locales [langs]
  (compare-and-set! *tr-locales @*tr-locales langs)
  @*tr-locales)

(defn tr [& in]
  (let [resource (conj (apply vector in)
                       (str "missing resource: " (str/join " " in)))]
    (tempura/tr {:dict @*tr-options-dict}
                @*tr-locales
                resource)))

(defn normalize-yes-no-text [text]
  (cond
    (= (str/lower-case text) (tr :talk/yes)) "yes"
    (= (str/lower-case text) (tr :talk/no)) "no"
    :else text))
