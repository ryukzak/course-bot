(ns course-bot.csa.lab1
  (:require [course-bot.dialog :as d])
  (:require [course-bot.talk :as b])
  (:require [course-bot.csa.general :as g])
  (:require [codax.core :as c])
  (:require [clojure.string :as str])
  (:require [morse.handlers :as h]
            [morse.api :as t]
            [morse.polling :as p])
  (:require [clojure.pprint :refer [pprint]]))

(defn get-next-for-review! [db]
  (first (filter (fn [[_id info]] (and (some-> info :lab1 :on-review?)
                                       (not (some-> info :lab1 :approved?))))
                 (c/get-at! db []))))

(defn state-str [on-review? approved?]
  (case [on-review? approved?]
    [false true] "OK"
    [true true] "OK" ;; FIXME: should not happens
    [true false] "WAIT"
    [true nil] "WAIT"
    [nil nil] "Not sub"
    [false false] "ISSUE"
    (str [on-review? approved?])))

(defn status-str [id desc]
  (let [{{on-review? :on-review? approved? :approved? desc :description} :lab1
         name :name
         {username :username} :chat} desc]
    (str name " (" id ", @" username ") "
         (state-str on-review? approved?)
         (when approved?
           (str "\n"
                "  - " (first (str/split-lines desc)))))))

(defn status-for-stud
  ([db id] (status-str id (c/get-at! db [id]))))

(defn send-status
  ([db token id] (send-status db token id nil))
  ([db token id only-group]
   (doall
    (->> (c/get-at! db [])
         (group-by (fn [[id desc]] (:group desc)))
         (filter (fn [[group _records]] (and (some? group) (or (nil? only-group) (= only-group group)))))
         (map (fn [[group records]]
                [group (filter (fn [[id desc]] (some? (some-> desc :lab1 :on-review?))) records)]))
         (map (fn [[group records]]
                (t/send-text token id
                             (str "Группа: " group
                                  "\n"
                                  (str/join "\n"
                                            (map #(str "- " (apply status-str %))
                                                 (sort-by (fn [[_id {{on-review? :on-review? approved? :approved?} :lab1}]]
                                                            (state-str on-review? approved?)) records)))))))))))

(def group-description (read-string (try (slurp (System/getenv "GROUP_DESC")) (catch Exception _ "nil"))))

(defn lab1-group-stat [db group]
  (let [studs (g/studs-by-group db group)
        approved (->> studs (filter #(-> % second :lab1 :approved?)))
        done (->> (c/get-at! db [:schedule :lab1 group :history])
                  (map :reports)
                  (apply concat))]
    (str "Всего студентов в группе (по информации бота): " (count studs) "; "
         "Согласовало тем: " (count approved) "; "
         "Сделало докладов: " (count done))))

(defn send-schedule-list [db token id group lst]
  (t/send-text token id
               (str (or (get group-description group) (str "Группа: " group))
                    "\n"
                    (if (empty? lst)
                      "Нет заявок"
                      (str/join "\n"
                                (map (fn [[i stud-id]] (str (+ 1 i) ". " (status-for-stud db stud-id)))
                                     (zipmap (range) lst))))
                    "\n\n"

                    (lab1-group-stat db group))))

;; Drop lab1 and student.

(defn drop-lab1-schedule-for [tx id]
  (let [lab1 (c/get-at tx [:schedule :lab1])
        upd (into {}
                  (map (fn [[gr info]]
                         [gr (assoc info
                                    :fixed (filter #(not= % id) (:fixed info))
                                    :queue (filter #(not= % id) (:queue info)))])
                       lab1))]
    (c/assoc-at tx [:schedule :lab1] upd)))

(defn drop-lab1-review [tx token id admin-chat msg]
  (when msg
    (t/send-text token id msg)
    (t/send-text token admin-chat (str "Студенту было отправлено:" "\n\n" msg)))
  (-> tx
      (c/assoc-at [id :lab1 :on-review?] nil)
      (c/assoc-at [id :lab1 :approved?] nil)
      (c/assoc-at [id :lab1 :in-queue?] nil)))

(defn drop-stud [tx token id admin-chat]
  (-> tx
      (drop-lab1-schedule-for id)
      (drop-lab1-review token id admin-chat "Увы, но пришлось сбросить ваше согласование по лабораторной работе №1.")))

(defn dropstudent-talk [db token assert-admin admin-id]
  (b/talk db "dropstudent"
          :start
          (fn [tx {{id :id} :chat text :text}]
            (assert-admin tx token id)
            (let [args (b/command-args text)]
              (if (and (= (count args) 1) (re-matches #"^\d+$" (first args)))
                (let [stud-id (Integer/parseInt (first args))
                      stud (c/get-at tx [stud-id])]
                  (when-not stud
                    (t/send-text token id "Нет такого пользователя.")
                    (b/stop-talk tx))
                  (g/send-whoami tx token id stud-id)
                  (b/send-yes-no-kbd token id "Сбросим этого студента?")
                  (-> tx
                      (c/assoc-at [admin-id :admin :drop-student] stud-id)
                      (b/change-branch :approve)))
                (do
                  (t/send-text token id "Ошибка ввода, нужно сообщение вроде: /dropstudent 12345")
                  (b/wait tx)))))

          :approve
          (fn [tx {{id :id} :from text :text}]
            (cond
              (= text "yes") (let [stud-id (c/get-at tx [admin-id :admin :drop-student])]
                               (t/send-text token id "Сбросили.")
                               (-> tx
                                   (drop-stud token stud-id admin-id)
                                   (c/assoc-at [stud-id :allow-restart] true)
                                   (c/assoc-at [admin-id :admin :drop-student] nil)
                                   (b/stop-talk)))
              (= text "no") (do
                              (t/send-text token id "Пускай пока остается.")
                              (-> tx
                                  (c/assoc-at [admin-id :admin :drop-student] nil)
                                  (b/stop-talk)))
              :else (do (t/send-text token id "What?")
                        (b/wait tx))))))

(defn fix [db group n]
  (let [desc (c/get-at! db [:schedule :lab1 group])
        next (take n (:queue desc))
        other (drop n (:queue desc))]
    (when-not (empty? (:fixed desc))
      (throw (Exception. "Already fixed!")))
    (println "lab1fix " group n)
    (println :history (cons next (:history desc)))
    (println :queue other)
    (c/assoc-at! db [:schedule :lab1 group :fixed] next)
    (c/assoc-at! db [:schedule :lab1 group :queue] other)))

(defn round [x] (/ (Math/round (* x 10.0)) 10.0))

(defn lab1-score [report-count feedback]
  (let [re (re-pattern (str "[^" (str/join "" (range 1 (+ 1 report-count))) "]"))
        scores (->> feedback
                    (map (fn [[id _dt score]] [id (str/replace score re "")]))
                    reverse
                    (into (hash-map))
                    (map second)
                    (filter #(= (count (dedupe (sort %))) (count %)))
                    (filter #(= report-count (count %))))
        n (float (count scores))]
    (println scores)
    (map (fn [i] (round (/ (apply + (map #(- 5 (str/index-of % (str i))) scores)) n)))
         (take report-count (concat '(1 2 3) (repeat 3))))))

(defn pass [db group]
  (let [desc (c/get-at! db [:schedule :lab1 group])
        fixed (:fixed desc)
        feedback (:feedback desc)
        record {:reports fixed :feedback feedback :user-scores (lab1-score (count fixed) feedback)}]
    (if fixed
      (do
        (println "> " group)
        (pprint record)
        (c/assoc-at! db [:schedule :lab1 group :history] (cons record (:history desc)))
        (c/assoc-at! db [:schedule :lab1 group :fixed] nil)
        (c/assoc-at! db [:schedule :lab1 group :feedback] nil))
      (println "lab1pass FAIL:" desc))))
