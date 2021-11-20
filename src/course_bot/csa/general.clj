(ns course-bot.csa.general
  (:require [codax.core :as c])
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
