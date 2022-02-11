(ns course-bot.general
  (:require [codax.core :as codax]
            [codax.core :as c])
  (:require [morse.handlers :as handlers])
  (:require [clojure.string :as str])
  (:require [course-bot.misc :as misc])
  (:require [course-bot.talk :as talk]))

(def conf (misc/read-config "general.edn" false))
(def admin-chat (-> conf :admin-chat))
(def group-list (-> conf :groups keys set))
(def chat-token (-> conf :chat-token))

(defn assert-admin [tx token id]
  (when-not (= id admin-chat)
    (talk/send-text token id "That action require admin rights.")
    (talk/stop-talk tx)))

(defn send-whoami
  ([tx token id] (send-whoami tx token id id))
  ([tx token id about]
   (let [{name :name group :group} (codax/get-at tx [about])]
     (talk/send-text token id (str "Name: " name "; "
                                   "Group: " group "; "
                                   "Telegram ID: " about)))))

(defn whoami-talk [db token]
  (talk/def-command db "whoami"
    (fn [tx {{id :id} :from}]
      (send-whoami tx token id)
      (talk/stop-talk tx))))

(defn students-from-group [tx group]
  (->> (codax/get-at tx [])
       (filter #(-> % second :group (= group)))))

(defn send-list-groups
  ([tx token id]
   (doall
    (->> (codax/get-at tx [])
         (group-by (fn [[_id desc]] (:group desc)))
         (map (fn [[group records]]
                (let [studs (->> records
                                 (map second)
                                 (sort-by :name)
                                 (map (fn [i x] (str (+ 1 i) ") " (:name x) " (@" (-> x :chat :username) ", " (-> x :chat :id) ")"))
                                      (range)))
                      msg (str "Group: " group "\n" (str/join "\n" studs))]
                  (talk/send-text token id msg))))))))

(defn listgroups-talk [db token]
  (talk/def-command db "listgroups"
    (fn [tx {{id :id} :chat}] (send-list-groups tx token id))))

(defn save-chat-info [tx id chat]
  (reduce (fn [[tx key value]] (c/assoc-at tx [id :chat key] value)) tx chat))

(defn start-talk [db token]
  (talk/def-talk db "start"
    :start
    (fn [tx {{id :id} :from}]
      (let [info (c/get-at tx [id])]
        (when (and (some? (:name info)) (not (:allow-restart info)))
          (talk/send-text token id "You are already registered, to change your unform the teacher and send /whoami.")
          (talk/stop-talk tx))
        (talk/send-text token id (str "Hi, I'm a bot for your course. I will help you with your works. What is your name?"))
        (talk/change-branch tx :get-name)))

    :get-name
    (fn [tx {{id :id} :from text :text}]
      (talk/send-text token id (str "What is your group (" (str/join ", " (sort group-list)) ")?"))
      (talk/change-branch tx :get-group {:name text}))

    :get-group
    (fn [tx {{id :id :as chat} :from text :text} {name :name}]
      (when-not (contains? group-list text)
        (talk/send-text token id (str "I don't know this group. Repeat please (" (str/join ", " (sort group-list)) "):"))
        (talk/repeat-branch tx))
      (let [tx (-> tx
                   (codax/assoc-at [id :chat] chat)
                   (codax/assoc-at [id :name] name)
                   (codax/assoc-at [id :group] text)
                   (codax/assoc-at [id :reg-date] (str (new java.util.Date)))
                   (codax/assoc-at [id :allow-restart] false))]
        (talk/send-text token id "Hi:")
        (send-whoami tx token id)
        (talk/send-text token id "Send /help for help.")
        (talk/stop-talk tx)))))

(defn restart-talk [db token assert-admin]
  (talk/def-talk db "restart"
    :start
    (fn [tx {{id :id} :from text :text}]
      (assert-admin tx token id)
      (let [args (talk/command-args text)]
        (if (and (= (count args) 1) (re-matches #"^\d+$" (first args)))
          (let [stud-id (Integer/parseInt (first args))
                stud (codax/get-at tx [stud-id])]
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
        (= text "yes") (do (talk/send-text token id (str "Restarted: " stud-id))
                           (talk/send-text token stud-id (str "You can use /start once more."))
                           (-> tx
                               (codax/assoc-at [stud-id :allow-restart] true)
                               (talk/stop-talk)))
        (= text "no") (do (talk/send-text token id "Not restarted.")
                          (talk/stop-talk tx))
        :else (do (talk/send-text token id "Please yes or no?")
                  (talk/repeat-branch tx))))))
