 (ns course-bot.presentation
   (:require [clojure.java.io :as io]
             [clojure.string :as str])
   (:require [codax.core :as codax])
   (:require [course-bot.talk :as talk]
             [course-bot.general :as general]
             [course-bot.misc :as misc]))

(defn send-please-set-group [token id pres-key-name name]
  (talk/send-text token id (str "Please, set your '" name "' group "
                                "by /" pres-key-name "setgroup")))

(defn setgroup-talk [db {token :token :as conf} pres-key-name]
  (let [cmd (str pres-key-name "setgroup")
        pres-key (keyword pres-key-name)
        name (-> conf (get pres-key) :name)
        groups (-> conf (get pres-key) :groups)
        groups-text (->> groups keys sort (str/join ", "))]
    (talk/def-talk db cmd
      (str "set your group for '" name "'")

      :start
      (fn [tx {{id :id} :from}]
        (let [pres-group (codax/get-at tx [id :presentation pres-key :group])]
          (if (some? pres-group)
            (do (talk/send-text token id (str "Your " name " group is already set: " pres-group))
                (talk/stop-talk tx))
            (do (talk/send-text token id (str "Please, select your " name " group: " groups-text))
                (talk/change-branch tx :set-group)))))

      :set-group
      (fn [tx {{id :id} :from text :text}]
        (when-not (get groups text)
          (talk/send-text token id (str "I don't know this group. Try again (" groups-text ")"))
          (talk/wait tx))

        (talk/send-text token id (str "Your " name " group set: " text))
        (-> tx
            (codax/assoc-at [id :presentation pres-key :group] text)
            talk/stop-talk)))))

(defn report-presentation-group [pres-key-name]
  (fn [_tx data id]
    (-> data (get id) :presentation (get (keyword pres-key-name)) :group)))

(defn report-presentation-classes [pres-key-name]
  (fn [_tx data id]
    (let [pres-key (keyword pres-key-name)
          group (-> data (get id) :presentation (get pres-key) :group)
          lessons (-> data :presentation (get pres-key) (get group))]
      (->> lessons
           (filter (fn [[_k v]] (-> v :stud-ids empty? not)))
           count))))

(defn submit-talk [db {token :token :as conf} pres-key-name]
  (let [cmd (str pres-key-name "submit")
        pres-key (keyword pres-key-name)
        hint (-> conf (get pres-key) :submition-hint)
        name (-> conf (get pres-key) :name)]

    (talk/def-talk db cmd
      (str "submit your '" name "' description")

      :start
      (fn [tx {{id :id} :from}]
        (let [pres (codax/get-at tx [id :presentation pres-key])
              group (-> pres :group)]
          (when-not group
            (send-please-set-group token id pres-key-name name)
            (talk/stop-talk tx))

          (when (:on-review? pres)
            (talk/send-text token id "On review, you will be informed when it is finished.")
            (talk/stop-talk tx))

          (when (:approved? pres)
            (talk/send-text token id (str "Already submitted and approved, maybe you need to schedule it? /" pres-key-name "schedule"))
            (talk/stop-talk tx))

          (talk/send-text token id (if hint
                                     hint
                                     (str "Please, provide description for your '"
                                          name "' (in one message):")))
          (talk/change-branch tx :recieve-description)))

      :recieve-description
      (fn [tx {{id :id} :from text :text}]
        (talk/send-text token id "Your description:")
        (talk/send-text token id text)
        (talk/send-yes-no-kbd token id "Do you approve it?")
        (talk/change-branch tx :approve {:desc text}))

      :approve
      (fn [tx {{id :id} :from text :text} {desc :desc}]
        (cond
          (= text "yes") (do (talk/send-text token id "Registered, the teacher will check it soon.")
                             (-> tx
                                 (codax/assoc-at [id :presentation pres-key :on-review?] true)
                                 (codax/assoc-at [id :presentation pres-key :description] desc)
                                 talk/stop-talk))
          (= text "no") (do (talk/send-text token id "You can do this later.")
                            (talk/stop-talk tx))
          :else (do (talk/send-text token id "Please, yes or no?")
                    (talk/repeat-branch tx)))))))

(defn wait-for-reviews [tx pres-key]
  (->> (codax/get-at tx [])
       (filter (fn [[_id info]]
                 (and (some-> info :presentation (get pres-key) :on-review?)
                      (not (some-> info :presentation (get pres-key) :approved?)))))))

(defn topic [desc] (if (nil? desc) "nil" (-> desc str/split-lines first)))

(defn presentation [tx id pres-id]
  (str (topic (codax/get-at tx [id :presentation pres-id :description]))
       " (" (codax/get-at tx [id :name]) ")"))

(defn approved-submissions [tx pres-key group]
  (str "Approved presentation in '" group "':\n"
       (->> (codax/get-at tx [])
            (filter #(and (-> % second :presentation (get pres-key) :group (= group))
                          (-> % second :presentation (get pres-key) :approved?)))
            (map #(str "- " (-> % second :presentation (get pres-key) :description topic)
                       " (" (-> % second :name) ")"))
            sort
            (str/join "\n"))))

(defn all-submissions [tx pres-key group]
  (str "Submitted presentation in '" group "':\n"
       (->> (codax/get-at tx [])
            (filter #(and (-> % second :presentation (get pres-key) :group (= group))
                          (-> % second :presentation (get pres-key) :description some?)))
            (map #(str "- " (-> % second :presentation (get pres-key) :description topic)
                       " (" (-> % second :name) ") - "
                       (cond
                         (-> % second :presentation (get pres-key) :scheduled?) "SCHEDULED"
                         (-> % second :presentation (get pres-key) :on-review?) "ON-REVIEW"
                         (-> % second :presentation (get pres-key) :approved?) "APPROVED"
                         :else "REJECTED")))
            sort
            (str/join "\n"))))

(defn check-talk [db {token :token :as conf} pres-key-name]
  (let [cmd (str pres-key-name "check")
        pres-key (keyword pres-key-name)
        name (-> conf (get pres-key) :name)]
    (talk/def-talk db cmd
      "for teacher, check submitted presentation description"
      :start
      (fn [tx {{id :id} :from}]
        (general/assert-admin tx conf id)
        (let [submitions (wait-for-reviews tx pres-key)
              submition (first submitions)]
          (when (nil? submition)
            (talk/send-text token id "Nothing to check.")
            (talk/stop-talk tx))
          (let [[stud-id info] submition
                group (-> info :presentation (get pres-key) :group)
                desc (-> info :presentation (get pres-key) :description)
                remarks (codax/get-at tx [stud-id :presentation pres-key :remarks])]
            (talk/send-text token id (str "Wait for review: " (count submitions)))
            (talk/send-text token id (approved-submissions tx pres-key group))
            (when (some? remarks)
              (talk/send-text token id "Remarks:")
              (doall (->> remarks reverse (map #(talk/send-text token id %)))))
            (talk/send-text token id (str "We receive from the student (group " (-> info :group) "): "
                                          "\n\n"
                                          "Topic: " (topic desc)))
            (talk/send-text token id desc)
            (talk/send-yes-no-kbd token id "Approve (yes or no)?")
            (talk/change-branch tx :approve {:stud-id stud-id}))))

      :approve
      (fn [tx {{id :id} :from text :text} {stud-id :stud-id}]
        (talk/if-parse-yes-or-no
         tx token id text
         (do (talk/send-text token id (str "OK, student will receive his approve.\n\n/" cmd))
             (talk/send-text token stud-id (str "'" name "' description was approved."))
             (-> tx
                 (codax/assoc-at [stud-id :presentation pres-key :on-review?] false)
                 (codax/assoc-at [stud-id :presentation pres-key :approved?] true)
                 talk/stop-talk))

         (do (talk/send-text token id "OK, you need to send your remark for the student:")
             (talk/change-branch tx :remark {:stud-id stud-id}))))

      :remark
      (fn [tx {{id :id} :from remark :text} {stud-id :stud-id}]
        (talk/send-text token id (str "Presentation description was declined. The student was informed about your decision."
                                      "\n\n/" cmd))
        (talk/send-text token stud-id (str "'" name "' description was rejected. Remark:\n\n" remark))
        (-> tx
            (codax/assoc-at [stud-id :presentation pres-key :on-review?] false)
            (codax/update-at [stud-id :presentation pres-key :remarks] conj remark)
            talk/stop-talk)))))

(defn submissions-talk [db {token :token admin-chat-id :admin-chat-id :as conf} pres-key-name]
  (let [cmd (str pres-key-name "submissions")
        pres-key (keyword pres-key-name)
        name (-> conf (get pres-key) :name)
        groups (-> conf (get pres-key) :groups)
        groups-text (->> groups keys sort (str/join ", "))]

    (talk/def-command db cmd
      "list submissions and their status (no args -- your group, with args -- specified)"
      (fn [tx {{id :id} :from text :text}]
        (let [arg (talk/command-text-arg text)]
          (cond
            (and (= id admin-chat-id) (= arg ""))
            (doall (->> groups keys sort (map #(talk/send-text token id (all-submissions tx pres-key %)))))

            (= arg "")
            (let [group (codax/get-at tx [id :presentation pres-key :group])]
              (if (nil? group)
                (send-please-set-group token id pres-key-name name)
                (talk/send-text token id (all-submissions tx pres-key group))))

            (get groups arg)
            (talk/send-text token id (all-submissions tx pres-key arg))

            :else
            (talk/send-text token id (str "I don't know '" arg "', you should specify one from: " groups-text)))
          (talk/stop-talk tx))))))

(defn lessons [conf pres-id group]
  (-> conf (get pres-id) :groups (get group) :lessons))

(defn filter-lesson [cut-off-in-min now lessons]
  (let [scale (* 1000 60)]
    (filter #(let [dt (misc/read-time (:datetime %))]
               (or (nil? now)
                   (<= cut-off-in-min (/ (- dt now) scale))))
            lessons)))

(defn future-lessons [conf pres-id group now]
  (let [cut-off-in-min (-> conf (get pres-id) :schedule-cut-off-time-in-min)]
    (->> (lessons conf pres-id group)
         (filter-lesson cut-off-in-min now))))

(defn agenda [tx conf pres-id group now]
  (let [cut-off-in-min (-> conf (get pres-id) :agenda-hide-cut-off-time-in-min)
        comment (-> conf (get pres-id) :groups (get group) :comment)]
    (->> (lessons conf pres-id group)
         (filter-lesson cut-off-in-min now)
         (map #(let [dt (:datetime %)
                     studs (codax/get-at tx [:presentation pres-id group dt :stud-ids])]
                 (str "Agenda " dt " (" group ")" (when (some? comment) (str ", " comment)) ":\n"
                      (str/join "\n" (map-indexed (fn [idx e] (str (+ 1 idx) ". " (presentation tx e pres-id))) studs))))))))

(defn soon [tx conf pres-id group now]
  (let [scale (* 1000 60 60)]
    (->> (lessons conf pres-id group)
         (filter #(let [dt (misc/read-time (:datetime %))
                        diff (/ (- dt now) scale)]
                    (and (> diff -24) (<= diff 48))))
         (map #(let [dt (:datetime %)
                     comment (-> conf (get pres-id) :groups (get group) :comment)
                     studs (codax/get-at tx [:presentation pres-id group dt :stud-ids])]
                 (str "Agenda " dt " (" group ")" (when (some? comment) (str ", " comment)) ":\n"
                      (str/join "\n" (map-indexed (fn [idx e] (str (+ 1 idx) ". " (presentation tx e pres-id))) studs))))))))

(defn agenda-talk [db {token :token admin-chat-id :admin-chat-id :as conf} pres-key-name]
  (let [cmd (str pres-key-name "agenda")
        pres-key (keyword pres-key-name)
        name (-> conf (get pres-key) :name)
        groups (-> conf (get pres-key) :groups)
        groups-text (->> groups keys sort (str/join ", "))]

    (talk/def-command db cmd
      "agenda (no args -- your group, with args -- specified)"
      (fn [tx {{id :id} :from text :text}]

        (let [arg (talk/command-text-arg text)]

          (cond
            (and (= id admin-chat-id) (= arg ""))
            (doall (->> groups keys sort
                        (map #(agenda tx conf pres-key % (misc/today)))
                        (apply concat)
                        (map #(talk/send-text token id %))))

            (= arg "")
            (let [group (codax/get-at tx [id :presentation pres-key :group])]
              (if (nil? group)
                (send-please-set-group token id pres-key-name name)
                (doall (->> (agenda tx conf pres-key group (misc/today))
                            (map #(talk/send-text token id %))))))

            (get groups arg)
            (doall (->> (agenda tx conf pres-key arg (misc/today))
                        (map #(talk/send-text token id %))))

            :else
            (talk/send-text token id (str "I don't know '" arg "', you should specify one from: " groups-text)))
          (talk/stop-talk tx))))))

(defn soon-talk [db {token :token  :as conf} pres-key-name]
  (let [cmd (str pres-key-name "soon")
        pres-key (keyword pres-key-name)
        name (-> conf (get pres-key) :name)
        groups (-> conf (get pres-key) :groups)]
    (talk/def-command db cmd
      "what will happen soon"
      (fn [tx {{id :id} :from}]
        (talk/send-text token id (str "We will expect for " name " soon:"))
        (doall (->> groups keys sort
                    (map #(soon tx conf pres-key % (misc/today)))
                    (apply concat)
                    (map #(talk/send-text token id %))))
        (talk/stop-talk tx)))))

(defn schedule-talk [db {token :token :as conf} pres-key-name]
  (let [cmd (str pres-key-name "schedule")
        pres-key (keyword pres-key-name)
        name (-> conf (get pres-key) :name)]
    (talk/def-talk db cmd
      "select your presentation day"
      :start
      (fn [tx {{id :id} :from}]
        (let [pres (codax/get-at tx [id :presentation pres-key])
              group (-> pres :group)]
          (when-not group
            (send-please-set-group token id pres-key-name name)
            (talk/stop-talk tx))

          (when-not (-> pres :approved?)
            (talk/send-text token id (str "You should submit and receive approve before scheduling. Use /" pres-key-name "submit"))
            (talk/stop-talk tx))

          (when (-> pres :scheduled?)
            (talk/send-text token id (str "Already scheduled, check /" pres-key-name "agenda."))
            (talk/stop-talk tx))

          (let [future (future-lessons conf pres-key group (misc/today))]
            (when (empty? future)
              (talk/send-text token id "I don't have options for you.")
              (talk/stop-talk tx))

            (doall (map #(talk/send-text token id %)
                        (agenda tx conf pres-key group (misc/today))))
            (talk/send-text token id
                            (str "Select your option:\n"
                                 (->> future
                                      (map #(str "- " (:datetime %)))
                                      (str/join "\n"))))
            (talk/change-branch tx :get-date))))

      :get-date
      (fn [tx {{id :id} :from text :text}]
        (let [pres (codax/get-at tx [id :presentation pres-key])
              group (-> pres :group)
              future (future-lessons conf pres-key group (misc/today))
              dt (some #(-> % :datetime (= text)) future)]
          (when (nil? dt)
            (talk/send-text token id
                            (str "Not found, allow only:\n"
                                 (->> future
                                      (map #(str "- " (:datetime %)))
                                      (str/join "\n"))))
            (talk/repeat-branch tx))
          (talk/send-text token id (str "OK, you can check it by: /" pres-key-name "agenda"))
          (-> tx
              (codax/update-at [:presentation pres-key group text :stud-ids]
                               #(concat % [id]))
              (codax/assoc-at [id :presentation pres-key :scheduled?] true)
              talk/stop-talk))))))

(defn feedback-str [studs]
  (str "Enter the number of the best presentation in the list:\n"
       (->> studs
            (map-indexed #(str %1 ". " (:name %2) " (" (:topic %2) ")"))
            (str/join "\n"))))

(defn feedback-talk [db {token :token :as conf} pres-key-name]
  (let [pres-key (keyword pres-key-name)
        cmd (str pres-key-name "feedback")]
    (talk/def-talk db cmd
      "send feedback for report"
      :start
      (fn [tx {{id :id} :from}]
        (let [now (misc/today)
              pres (codax/get-at tx [id :presentation pres-key])
              name (-> conf (get pres-key) :name)
              group (-> pres :group)
              {dt :datetime} (->> (future-lessons conf pres-key group nil)
                                  (some #(let [dt (misc/read-time (:datetime %))
                                               offset (/ (- now dt) (* 1000 60))]
                                           (when (and (<= 30 offset) (<= offset 180)) %))))
              stud-ids (codax/get-at tx [:presentation pres-key group dt :stud-ids])
              studs (->> stud-ids
                         (map #(let [info (codax/get-at tx [%])]
                                 {:id %
                                  :name (-> info :name)
                                  :topic (-> info
                                             :presentation
                                             (get pres-key)
                                             :description
                                             topic)})))]

          (when (nil? group)
            (talk/send-text token id (str "To send feedback, you should set your group for " name " by /" pres-key-name "setgroup"))
            (talk/stop-talk tx))

          (when (nil? dt)
            (talk/send-text token id "Feedback collecting disabled (too early or too late).")
            (talk/stop-talk tx))

          (when (some #(= id %)
                      (codax/get-at tx [:presentation pres-key group :feedback-from dt]))
            (talk/send-text token id "Already received.")
            (talk/stop-talk tx))

          (talk/send-text token id
                          (str "Collect feedback for '" name "' (" group ") at " dt))
          (talk/send-text token id (feedback-str studs))
          (talk/change-branch tx :select {:rank [] :remain studs :group group :dt dt})))

      :select
      (fn [tx {{id :id} :from text :text} {rank :rank studs :remain group :group dt :dt :as state}]
        (let [n (if (re-matches #"^\d+$" text) (Integer/parseInt text) nil)]
          (when (or (nil? n) (not (< n (count studs))))
            (talk/send-text token id "Wrong input. Enter the number of the best presentation in the list.")
            (talk/wait tx))

          (when (> (count studs) 1)
            (let [best (nth studs n)
                  studs (concat (take n studs) (drop (+ n 1) studs))]
              (talk/send-text token id (feedback-str studs))
              (talk/change-branch tx :select
                                  (assoc state
                                         :rank (conj rank best)
                                         :remain studs))))

          (talk/send-text token id "Thanks, your feedback saved!")
          (-> tx
              ;; TODO: move :feedback-from into [:feedback :from]
              (codax/update-at [:presentation pres-key group dt :feedback-from]
                               conj id)
              (codax/update-at [:presentation pres-key group dt :feedback]
                               conj {:receive-at (misc/str-time (misc/today))
                                     :rank (conj rank (first studs))})
              talk/stop-talk))))))

(defn drop-talk [db {token :token :as conf} pres-key-name drop-all]
  (let [cmd (str pres-key-name "drop" (when drop-all "all"))
        pres-key (keyword pres-key-name)
        name (-> conf (get pres-key) :name)
        help (str "for teacher, drop '" name "' for specific student ("
                  (if drop-all "all" "only schedule") ")")
        groups (-> conf (get pres-key) :groups)
        groups-text (->> groups keys sort (str/join ", "))]
    (talk/def-talk db cmd help

      :start
      (fn [tx {{id :id} :from text :text}]
        (general/assert-admin tx conf id)
        (let [stud-id (talk/command-num-arg text)]

          (when (nil? stud-id)
            (talk/send-text token id (str "Wrong input: /" cmd " 12345"))
            (talk/stop-talk tx))

          (let [stud (codax/get-at tx [stud-id])]
            (when-not stud
              (talk/send-text token id "Not found.")
              (talk/stop-talk tx))

            (general/send-whoami tx token id stud-id)
            (talk/send-yes-no-kbd token id (str "Drop '" name "' config for " stud-id "?"))

            (talk/change-branch tx :approve {:stud-id stud-id}))))

      :approve
      (fn [tx {{id :id} :from text :text} {stud-id :stud-id}]
        (talk/when-parse-yes-or-no
         tx token id text
         (let [group (codax/get-at tx [stud-id :presentation pres-key :group])
               lessons (codax/get-at tx [:presentation pres-key group])]
           (talk/send-text token id (str "We drop student: " stud-id))
           (talk/send-text token stud-id (str "We drop your state for " name))
           (-> (if drop-all
                 (codax/assoc-at tx [stud-id :presentation pres-key] nil)
                 (codax/assoc-at tx [stud-id :presentation pres-key :scheduled?] nil))
               (codax/assoc-at [:presentation pres-key group]
                               (->> lessons
                                    (map (fn [[dt desc]]
                                           [dt (assoc desc
                                                      :stud-ids (filter #(not= stud-id %) (:stud-ids desc)))]))
                                    (into {})))
               talk/stop-talk)))))))

(defn avg-rank [tx pres-key stud-id]
  (let [group (codax/get-at tx [stud-id :presentation pres-key :group])
        feedback (some
                  (fn [[_dt fb]] (when (some #(= % stud-id) (:stud-ids fb)) fb))
                  (codax/get-at tx [:presentation pres-key group]))
        ranks (->> (:feedback feedback)
                   (map #(map-indexed (fn [idx rank] (assoc rank :rank (+ 1 idx)))
                                      (:rank %)))
                   (apply concat)
                   (filter #(-> % :id (= stud-id))))]
    (when (> (count ranks) 0)
      (let [avg (/ (->> ranks (map :rank) (apply +)) (count ranks))]
        (Double/parseDouble (format "%.2f" (double avg)))))))

(defn score "by the configuration" [tx conf pres-key stud-id]
  (let [all-scores (-> conf (get pres-key) :feedback-scores)
        group (codax/get-at tx [stud-id :presentation pres-key :group])

        {stud-ids :stud-ids feedback :feedback}
        (some
         (fn [[_dt fb]] (when (some #(= % stud-id) (:stud-ids fb)) fb))
         (codax/get-at tx [:presentation pres-key group]))
        scores (get all-scores (count stud-ids))]
    (cond
      (or (empty? stud-ids) (nil? group)) nil

      (empty? feedback) (->> (map vector (sort stud-ids) scores)
                             (some (fn [[id score]] (when (= stud-id id) score))))
      (some? feedback)
      (let [ranks (->> stud-ids
                       (map (fn [id] {:stud-id id
                                      :avg-rank (avg-rank tx pres-key id)}))
                       (sort-by :avg-rank)
                       (map-indexed (fn [idx rank] (assoc rank :rank (+ 1 idx)))))

            rank (some #(when (= stud-id (:stud-id %)) (:rank %)) ranks)]
        (-> scores (nth (- rank 1)))))))

(defn report-presentation-avg-rank [conf pres-key-name]
  (fn [tx _data id]
    (-> (avg-rank tx (keyword pres-key-name) id)
        str
        (str/replace #"\." ","))))

(defn report-presentation-score [conf pres-key-name]
  (fn [tx _data id]
    (score tx conf (keyword pres-key-name) id)))

(defn lesson-count [pres-name]
  (fn [_tx data id]
    (let [pres-key (keyword pres-name)
          group (-> data (get id) :presentation (get pres-key) :group)
          schedule (-> data :presentation (get pres-key) (get group))]
      (->> schedule
           (filter #(-> % second :stud-ids empty? not))
           count))))

(defn scheduled-descriptions-dump [data pres-key group]
  (->> data
       (filter #(and (-> % second :presentation (get pres-key) :group (= group))
                     (-> % second :presentation (get pres-key) :scheduled?)))
       (map #(let [name (-> % second :name)
                   desc (-> % second :presentation (get pres-key) :description)]
               (str "## " name "\n\n" desc)))
       (str/join "\n\n")))

(defn all-scheduled-descriptions-dump-talk [db {token :token :as conf} pres-id]
  (let [pres-key (keyword pres-id)]
    (talk/def-command db (str pres-id "descriptions") "all-scheduled-descriptions-dump (admin only)"
      (fn [tx {{id :id} :from}]
        (general/assert-admin tx conf id)

        (talk/send-text token id "File with all scheduled descriptions by groups:")
        (let [dt (.format (java.text.SimpleDateFormat. "yyyy-MM-dd-HH-mm-Z") (misc/today))
              fn (str dt "-" pres-id "-descriptions.md")
              groups (-> conf (get pres-key) :groups keys)
              data (codax/get-at tx [])
              text (->> groups
                        (map #(str "# " % "\n\n" (scheduled-descriptions-dump data pres-key %)))
                        (str/join "\n\n\n"))]
          (spit fn text)
          (talk/send-document token id (io/file fn)))
        tx))))
