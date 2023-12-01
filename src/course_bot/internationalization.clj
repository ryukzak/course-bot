(ns course-bot.internationalization
  (:require [clojure.string :as str])
  (:require [taoensso.tempura :as tempura]))

(def *tr-options-dict (atom {}))
(defn add-dict [dict]
  (swap! *tr-options-dict
         (fn [m]
           (let [updates (for [[lang namespaces] dict
                               [namespace record] namespaces
                               [key text] record]
                           [lang namespace key text])]
             (reduce (fn [m [lang namespace key text]]
                       (assoc-in m [lang namespace key] text))
                     m
                     updates)))))

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
