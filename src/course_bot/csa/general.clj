(ns course-bot.csa.general
  (:require [codax.core :as c])
  (:require [clojure.string :as str])
  (:require [course-bot.talk :as t]))

(defn send-whoami
  ([tx token id] (send-whoami tx token id nil))
  ([tx token id me]
   (let [me (or me id)
         {name :name group :group} (c/get-at tx [me])]
     (t/send-text token id (str "Ваше имя: " name ". "
                                "Ваша группа: " group " (примечание: группа четверга это отдельная группа). "
                                "Ваш телеграмм id: " me)))))

(defn send-whoami!
  ([db token id] (send-whoami! db token id nil))
  ([db token id me]
  (declare tx)
  (c/with-read-transaction [db tx]
    (send-whoami tx token id me))))

(defn studs-by-group [db group]
  (->> (c/get-at! db [])
       (filter #(-> % second :group (= group)))))

(defn send-group-lists
  ([tx token id]
   (doall
    (->> (c/get-at tx [])
         (group-by (fn [[_id desc]] (:group desc)))
         (map (fn [[group records]]
                (let [studs (->> records
                                 (map second)
                                 (sort-by :name)
                                 (map (fn [i x] (str (+ 1 i) ") " (:name x) " (@" (-> x :chat :username) ", " (-> x :chat :id) ")"))
                                      (range)))
                      msg (str "Группа: " group "\n" (str/join "\n" studs))]
                  (t/send-text token id msg))))))))
