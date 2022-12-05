(ns course-bot.general
  (:require [clojure.string :as str])
  (:require [codax.core :as codax])
  (:require [course-bot.talk :as talk]))

(defn assert-admin
  ([tx {token :token admin-chat-id :admin-chat-id} id]
   (when-not (= id admin-chat-id)
     (talk/send-text token id "That action requires admin rights.")
     (talk/stop-talk tx))))

(defn send-whoami
  ([tx token id] (send-whoami tx token id id))
  ([tx token id about]
   (let [{name :name group :group} (codax/get-at tx [about])]
     (talk/send-text token id (str "Name: " name "; "
                                   "Group: " group "; "
                                   "Telegram ID: " about)))))

(defn whoami-talk [db {token :token}]
  (talk/def-command db "whoami" "send me my registration info"
    (fn [tx {{id :id} :from}]
      (send-whoami tx token id)
      (talk/stop-talk tx))))

(defn send-list-groups
  ([tx token id]
   (doall
    (->> (codax/get-at tx [])
         (group-by (fn [[_id desc]] (:group desc)))
         (map (fn [[group records]]
                (let [studs (->> records
                                 (map second)
                                 (filter #(some? (:group %)))
                                 (sort-by :name)
                                 (map (fn [i x] (str (+ 1 i) ") " (:name x) " (@" (-> x :chat :username) ", " (-> x :chat :id) ")"))
                                      (range)))
                      msg (str group " group:\n" (str/join "\n" studs))]
                  (talk/send-text token id msg))))))))

(defn listgroups-talk [db {token :token}]
  (talk/def-command db "listgroups" "send me group list know by the bot"
    (fn [tx {{id :id} :from}] (send-list-groups tx token id) tx)))

(defn start-talk [db {token :token groups-raw :groups allow-restart :allow-restart}]
  (let [groups (-> groups-raw keys set)]
    (talk/def-talk db "start" "register student"
      :start
      (fn [tx {{id :id} :from}]
        (let [info (codax/get-at tx [id])]
          (when (and (some? (:name info))
                     (-> info :allow-restart not)
                     (not allow-restart))
            (talk/send-text token id "You are already registered. To change your information, contact the teacher and send /whoami")
            (talk/stop-talk tx))
          (talk/send-text token id (str "Hi, I'm a bot for your course. "
                                        "I will help you with your work. "
                                        "What is your name (like in the registry)?"))
          (talk/change-branch tx :get-name)))

      :get-name
      (fn [tx {{id :id} :from text :text}]
        (talk/send-text token id (str "What is your group (" (str/join ", " (sort groups)) ")?"))
        (talk/change-branch tx :get-group {:name text}))

      :get-group
      (fn [tx {{id :id :as chat} :from text :text} {name :name}]
        (when-not (contains? groups text)
          (talk/send-text token id (str "I don't know this group. Please, repeat it (" (str/join ", " (sort groups)) "):"))
          (talk/repeat-branch tx))
        (let [tx (-> tx
                     (codax/assoc-at [id :chat] chat)
                     (codax/assoc-at [id :name] name)
                     (codax/assoc-at [id :group] text)
                     (codax/assoc-at [id :reg-date] (str (new java.util.Date)))
                     (codax/assoc-at [id :allow-restart] false))]
          (talk/send-text token id (str "Hi " name "!"))
          (send-whoami tx token id)
          (talk/send-text token id "Send /help for help.")
          (talk/stop-talk tx))))))

(defn renameme-talk [db {token :token groups-raw :groups}]
  (talk/def-talk db "renameme" "rename me"
    :start
    (fn [tx {{id :id} :from}]
      (let [info (codax/get-at tx [id])]
        (when (nil? (:name info))
          (talk/send-text token id "You should be registered to rename yourself!")
          (talk/stop-talk tx))
        (talk/send-text token id (str "What is your new name?"))
        (talk/change-branch tx :get-name)))

    :get-name
    (fn [tx {{id :id :as chat} :from text :text}]
      (let [tx (-> tx
                   (codax/assoc-at [id :name] text)
                   (codax/assoc-at [id :rename-date] (str (new java.util.Date))))]
        (talk/send-text token id "Renamed:")
        (send-whoami tx token id)
        (talk/stop-talk tx)))))

(defn restart-talk [db {token :token :as conf}]
  (talk/def-talk db "restart"
    :start
    (fn [tx {{id :id} :from text :text}]
      (assert-admin tx conf id)
      (let [stud-id (talk/command-num-arg text)]
        (if (some? stud-id)
          (let [stud (codax/get-at tx [stud-id])]
            (when (nil? (:name stud))
              (talk/send-text token id "User with specific telegram id not found.")
              (talk/stop-talk tx))
            (send-whoami tx token id stud-id)
            (talk/send-yes-no-kbd token id "Restart this student?")
            (-> tx
                (talk/change-branch :approve {:restart-stud stud-id})))
          (do
            (talk/send-text token id "Wrong input. Expect: /restart 12345")
            (talk/stop-talk tx)))))

    :approve
    (fn [tx {{id :id} :from text :text} {stud-id :restart-stud}]
      (cond
        (= text "yes") (do (talk/send-text token id (str "Restarted and notified: " stud-id))
                           (talk/send-text token stud-id (str "You can use /start once more."))
                           (-> tx
                               (codax/assoc-at [stud-id :allow-restart] true)
                               (talk/stop-talk)))
        (= text "no") (do (talk/send-text token id "Not restarted.")
                          (talk/stop-talk tx))
        :else (do (talk/send-text token id "Please yes or no?")
                  (talk/repeat-branch tx))))))
