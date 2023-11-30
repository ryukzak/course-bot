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

(defn get-by-lang-and-type [lang type k] 
  (get-in @*tr-options-dict  [lang type k]))

(defn find-command [text]
  (some (fn [[_ types]]
          (some (fn [[_ words]]
                  (some (fn [[k v]] (when (= v text) k)) words))
                types))
        @*tr-options-dict))

(defn normalize-yes-no-text [text] 
  (if (= (str/lower-case text) (tr :talk/yes))
    "yes"
    (if (= (str/lower-case text) (tr :talk/no))
      "no"
      text)))
