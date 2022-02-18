 (ns course-bot.presentation
   (:require [codax.core :as codax])
   (:require [course-bot.talk :as talk]
             [course-bot.general :as general]
             [clojure.string :as str])
   (:require [course-bot.misc :as misc]))

(declare submissions)

(def configs (misc/read-configs "presentations"))

(defn config [tx token id pres-id]
  (let [res (-> configs (get pres-id))]
    (when (nil? res)
      (talk/send-text token id (str "Presentation config not provided for: " pres-id ". Inform admin."))
      (talk/stop-talk tx))
    res))

(defn groups [pres-id]
  (into #{} (keys (-> configs (get pres-id) :groups))))

(defn group [tx token id pres-id]
  (let [pres-group (codax/get-at tx [id :pres pres-id :group])]
    (when (-> pres-group nil?)
      (talk/send-text token id (str "You should specify your group for presentation by /" pres-id "setgroup"))
      (talk/stop-talk tx))
    pres-group))

(defn setgroup-talk [db token pres-id]
  (talk/def-talk db (str pres-id "setgroup")
    :start
    (fn [tx {{id :id} :from}]
      (let [pres-group (codax/get-at tx [id :pres pres-id :group])]
        (if (some? pres-group)
          (do (talk/send-text token id (str "Your presentation group: " pres-group))
              (talk/stop-talk tx))
          (do (talk/send-text token id (str "Please, select your presentation group: "
                                            (->> (groups pres-id) sort (str/join ", "))))
              (talk/change-branch tx :set-group)))))

    :set-group
    (fn [tx {{id :id} :from text :text}]
      (when-not (contains? (groups pres-id) text)
        (talk/send-text token id (str "I don't know this group. Repeat please ("
                                      (->> (groups pres-id) sort (str/join ", ")) "):"))
        (talk/repeat-branch tx))

      (talk/send-text token id (str "Your presentation group setted: " text))
      (-> tx
          (codax/assoc-at [id :pres pres-id :group] text)
          talk/stop-talk))))

(defn submit-talk [db token pres-id]
  (talk/def-talk db (str pres-id "submit")
    :start
    (fn [tx {{id :id} :from}]
      (let [info (general/get-registered tx token id)
            pres (-> info :pres (get pres-id))
            pres-group (group tx token id pres-id)
            conf (config tx token id pres-id)]

        (when (:approved? pres)
          (talk/send-text token id "Already submitted and approved, maybe you need schedule it? /" pres-id "schedule")
          (talk/stop-talk tx))

        (when (:on-review? pres)
          (talk/send-text token id "On review, you will be informed when it finished.")
          (talk/stop-talk tx))

        (talk/send-text token id
                        (or (-> configs (get pres-id) :description-request)
                            "Please, provide description for your presentation (in one message):"))
        (talk/change-branch tx :recieve-description)))

    :recieve-description
    (fn [tx {{id :id} :from text :text}]
      (talk/send-text token id "Your description:")
      (talk/send-text token id text)
      (talk/send-yes-no-kbd token id "Do you approve it?")
      (talk/change-branch tx :approve {:desc text}))

    :approve
    (fn [tx {{id :id :as chat} :from text :text} {desc :desc}]
      (cond
        (= text "yes") (do (talk/send-text token id "Registered, the teacher will check it soon.")
                           (-> tx
                               (codax/assoc-at [id :pres pres-id :on-review?] true)
                               (codax/assoc-at [id :pres pres-id :description] desc)
                               talk/stop-talk))
        (= text "no") (do (talk/send-text token id "You can do this later.")
                          (talk/stop-talk tx))
        :else (do (talk/send-text token id "Please, yes or no?")
                  (talk/repeat-branch tx))))))

(defn get-next-for-review [tx pres-id]
  (->> (codax/get-at tx [])
       (filter (fn [[_id info]] (and (some-> info :pres (get pres-id) :on-review?)
                                     (not (some-> info :pres (get pres-id) :approved?)))))
       first))

(defn topic [desc] (if (nil? desc) "nil" (-> desc str/split-lines first)))

(defn check-talk [db token pres-id assert-admin]
  (talk/def-talk db (str pres-id "check")
    :start
    (fn [tx {{id :id} :from}]
      (assert-admin tx token id)
      (let [row (get-next-for-review tx pres-id)]
        (when (nil? row)
          (talk/send-text token id "Nothing to check.")
          (talk/stop-talk tx))
        (let [[stud-id info] row
              desc (-> info :pres (get pres-id) :description)]
          (talk/send-text token id (submissions tx token id pres-id))
          (talk/send-text token id (str "We receive from the student (group " (-> info :group) "): "
                                        "\n\n"
                                        "Topic: " (topic desc)))
          (talk/send-text token id desc)
          (talk/send-yes-no-kbd token id "Approve (yes or no)?")
          (talk/change-branch tx :approve {:stud-id stud-id}))))

    :approve
    (fn [tx {{id :id :as chat} :from text :text} {stud-id :stud-id}]
      (cond
        (= text "yes") (do (talk/send-text token id (str "OK, student will reveive his approve."
                                                         "\n\n/" pres-id "check"))
                           (talk/send-text token stud-id (str "Your presentation description for " pres-id " approved."))
                           (-> tx
                               (codax/assoc-at [stud-id :pres pres-id :on-review?] false)
                               (codax/assoc-at [stud-id :pres pres-id :approved?] true)
                               talk/stop-talk))
        (= text "no") (do (talk/send-text token id "OK, you need send your remark for the student:")
                          (talk/change-branch tx :remark {:stud-id stud-id}))
        :else (do (talk/send-text token id "Please, yes or no?")
                  (talk/repeat-branch tx))))

    :remark
    (fn [tx {{id :id :as chat} :from remark :text} {stud-id :stud-id}]
      (talk/send-text token id (str "Presentation description declined. The student was informed about your decision."
                                    "\n\n/" pres-id "check"))
      (talk/send-text token stud-id (str "Your presentation description for " pres-id " declined with the following remark:\n\n" remark))
      (-> tx
          (codax/assoc-at [stud-id :pres pres-id :on-review?] false)
          talk/stop-talk))))

(defn today [] (.getTime (new java.util.Date)))

(defn schedule
  ([pres-id group only-new]
   (schedule pres-id group only-new (today)))
  ([pres-id group time-offset now]
   (->> (-> configs (get pres-id) :groups (get group) :schedule)
        (filter #(or (nil? time-offset)
                     (let [time (.getTime (.parse (java.text.SimpleDateFormat. "yyyy.MM.dd HH:mm") (:datetime %)))]
                       (<= time-offset (/ (- time now) (* 1000 60 60 24)))))))))

(def for-schedule 0)

(def for-agenda -1)

(defn presentation [tx id pres-id]
  (str
   (topic (codax/get-at tx [id :pres pres-id :description]))
   " (" (codax/get-at tx [id :name]) ")"))

(defn submission-status [desc]
  (cond
    (nil? desc) "nil"
    (and (= 1 (count desc)) (some? (:group desc))) "UNDEFINED"
    (:approved? desc) "OK"
    (:on-review? desc) "WAIT"
    (false? (:on-review? desc)) "ISSUE"
    :else (str "?? " desc)))

(defn pres-state [stud-tuple pres-id]
  (-> stud-tuple second :pres (get pres-id)))

(defn submissions [tx token id pres-id]
  (let [group (group tx token id pres-id)]
    (str "In group: " group ":\n"
         (->> (codax/get-at tx [])
              (filter #(-> % second :group)) ;; it should be a student
              (filter #(-> % (pres-state pres-id) :group (= group)))
              (map #(str "- " (-> % (pres-state pres-id) submission-status)
                         " " (presentation tx (first %) pres-id)))
              sort
              (str/join "\n")))))

(defn submissions-talk [db token pres-id]
  (talk/def-command db (str pres-id "submissions")
    (fn [tx {{id :id} :from}]
      (let [group (group tx token id pres-id)]
        (talk/send-text token id
                        (submissions tx token id pres-id))

        (talk/stop-talk tx)))))

(defn agenda [tx token id pres-id]
  (let [group (group tx token id pres-id)]
    (map #(let [dt (:datetime %)
                studs (codax/get-at tx [:pres pres-id group dt])]
            (str dt " (" group ")\n"
                 (str/join "\n" (map (fn [e] (str "- " (presentation tx e pres-id))) studs))))
         (schedule pres-id group for-agenda (today)))))

(defn agenda-talk [db token pres-id]
  (talk/def-command db (str pres-id "agenda")
    (fn [tx {{id :id} :from}]
      (doall (map #(talk/send-text token id %) (agenda tx token id pres-id)))
      (talk/stop-talk tx))))

(defn schedule-talk [db token pres-id]
  (talk/def-talk db (str pres-id "schedule")
    :start
    (fn [tx {{id :id} :from}]
      (let [info (general/get-registered tx token id)
            pres (-> info :pres (get pres-id))
            group (group tx token id pres-id)]
        (when-not (-> pres :approved?)
          (talk/send-text token id (str "You should submit and receive approve before scheduling. Use /" pres-id "submit"))
          (talk/stop-talk tx))

        (when (-> pres :scheduled?)
          (talk/send-text token id (str "Already scheduled, check /" pres-id "agenda."))
          (talk/stop-talk tx))

        (let [future (schedule pres-id group for-schedule (today))]
          (if (empty? future)
            (do (talk/send-text token id "Ended")
                (talk/stop-talk tx))
            (do (doall (map #(talk/send-text token id %) (agenda tx token id pres-id)))
                (talk/send-text token id (str "Select your option: "
                                              (->> future (map :datetime) (str/join ", "))))
                (talk/change-branch tx :get-date))))))

    :get-date
    (fn [tx {{id :id} :from text :text}]
      (let [group (group tx token id pres-id)
            future (schedule pres-id group for-schedule (today))
            dt (some #(-> % :datetime (= text)) future)]
        (when (nil? dt)
          (talk/send-text token id (str "Not found, allow only: "
                                        (->> future (map :datetime) (str/join ", "))))
          (talk/repeat-branch tx))
        (talk/send-text token id (str "OK, you can check it by: /" pres-id "agenda"))
        (-> tx
            (codax/update-at [:pres pres-id group text] conj id)
            (codax/assoc-at [id :pres pres-id :scheduled?] true)
            talk/stop-talk)))))

(defn drop-talk [db token pres-id assert-admin admin-id]
  (talk/def-talk db (str pres-id "drop")
    :start
    (fn [tx {{id :id} :from text :text}]
      (assert-admin tx token id)
      (let [args (talk/command-args text)]
        (if (and (= (count args) 1) (re-matches #"^\d+$" (first args)))
          (let [stud-id (Integer/parseInt (first args))
                stud (codax/get-at tx [stud-id])]
            (when-not stud
              (talk/send-text token id "Not found.")
              (talk/stop-talk tx))
            (general/send-whoami tx token id stud-id)
            (talk/send-yes-no-kbd token id (str "Drop presentation config for " pres-id "?"))
            (talk/change-branch tx :approve {:stud-id stud-id}))
          (do
            (talk/send-text token id (str "Wrong input: /" pres-id "drop 12345"))
            (talk/wait tx)))))

    :approve
    (fn [tx {{id :id} :from text :text} {stud-id :stud-id}]
      (cond
        (= text "yes") (let [group (group tx token stud-id pres-id)
                             sch (codax/get-at tx [:pres pres-id group])]
                         (talk/send-text token id (str "We drop student: " pres-id))
                         (talk/send-text token stud-id (str "We drop your state for " pres-id))
                         (-> tx
                             (codax/assoc-at [stud-id :pres pres-id] nil)
                             (codax/assoc-at [:pres pres-id group]
                                             (into {}
                                                   (map (fn [[dt studs]]
                                                          [dt (filter #(not= stud-id %) studs)])
                                                        sch)))
                             talk/stop-talk))
        (= text "no") (do (talk/send-text token id "Not droped.")
                          (talk/stop-talk tx))
        :else (do (talk/send-text token id "What?")
                  (talk/repeat-branch tx))))))
