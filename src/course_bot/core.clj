(ns course-bot.core
  (:require [course-bot.dialog :as d])
  (:require [codax.core :as c])
  (:require [clj-http.client :as http])
  (:require [morse.handlers :as h]
            [morse.api :as t]
            [morse.polling :as p])
  (:gen-class))

; (def db (c/open-database! (or (System/getenv "DATA_STORE") "course_database")))
(def db (c/open-database! "/data/csa"))
(def token (System/getenv "BOT_TOKEN"))

(def group-list #{"P33102" "P33111" "P33301" "P33101" "P33312" "P33302" "P33112" "thursday"})
(def admin-chat 70151255)

(defn get-lab1-for-review [db]
  (first (filter (fn [[sid info]] (and (some-> info :lab1 :on-review?)
                                       (not (some-> info :lab1 :approved?))))
                 (c/get-at! db []))))

(defn save-chat-info [id chat]
  (doall (map (fn [[key value]] (c/assoc-at! db [id :chat key] value)) chat)))

(defn send-message
  "Sends json to the chat"
  ([token chat-id data] (send-message token chat-id {} data))
  ([token chat-id options data]
   (let [url  (str t/base-url token "/sendMessage")
         body (merge {:chat_id chat-id} options data)
         resp (http/post url {:content-type :json
                              :as           :json
                              :form-params  body})]
     (-> resp :body))))

;; (send-message token id {:text "bla-bla"})

(defn drop-lab1-review [db id msg]
  (c/assoc-at! db [id :lab1 :on-review?] nil)
  (c/assoc-at! db [id :lab1 :approved?] nil)
  (when msg
    (t/send-text token id msg)
    (t/send-text token admin-chat (str "Студенту было отправлено:" "\n\n" msg))))

(def drop-lab1-msg "Увы, но пришлось сбросить ваше согласование по лабораторной работе №1. Можете перезалить описание, но первой строкой пустить название вашего доклада. Глядя на него должно быть видно, что вы такой один в своей группе.")

(defn lab1-drop-from-queue
  ([db id] (lab1-drop-from-queue db id nil))
  ([db id msg]
   (let [group (c/get-at! db [id :group])
         q (c/get-at! db [:schedule :lab1 group :queue])
         q-new (filter #(not= id %) q)]
     (c/assoc-at! db [:schedule :lab1 group :queue] q-new)
     (when msg
       (t/send-text token id msg)
       (t/send-text token admin-chat (str "Студенту было отправлено:" "\n\n" msg))))))

;(drop-lab1-review db admin-chat)

(defn lab1-state [on-review? approved?]
  (case [on-review? approved?]
    [false true] "OK"
    [true true] "OK" ;; FIXME: should not happens
    [true false] "WAIT"
    [true nil] "WAIT"
    [nil nil] "Not sub"
    [false false] "ISSUE"
    (str [on-review? approved?])))

(defn lab1-status-str [id desc]
    (let [{{on-review? :on-review? approved? :approved? desc :description} :lab1
          name :name
          group :group
          {username :username} :chat} desc]
      (str name " (" id ", @" username ") "
           (lab1-state on-review? approved?)
           (when approved?
             (str "\n"
                  "  > " (first (clojure.string/split-lines desc)))))))

(defn lab1-status
  ([db id] (lab1-status-str id (c/get-at! db [id]))))

(defn send-yes-no-kbd [token id msg]
  (send-message token id {:text msg
                          :reply_markup
                          {:one_time_keyboard true
                           :resize_keyboard true
                           :keyboard
                           [[{:text "yes"} {:text "no"}]]}}))

;; (defn send-msg-and-drop-keyboard [token id msg]
;;   (send-message token id {:text msg
;;                           :reply_markup
;;                           {:one_time_keyboard true
;;                            :keyboard
;;                            [[{:text "yes"} {:text "no"}]]}}))


;; (defn send-keyboard [token id msg & args]
;;   (send-message token id {:text msg :reply_markup {:keyboard args}}))

;; (send-keyboard token id "Approve: " [{:text "approve"} {:text "decline"}])

(h/defhandler bot-api
  (d/dialog "start" db {{id :id :as chat} :chat}
            (save-chat-info id chat)
            (t/send-text token id (str "Привет, я бот курса \"Архитектура компьютера\". "
                                       "Через меня будут организовано выполнение лабораторных работ."
                                       "\n\n"
                                       "Представьтесь пожалуйста, мне нужно знать как вносить вас в ведомости (ФИО):"))
            (:listen {{id :id} :from text :text}
                     (c/assoc-at! db [id :name] text)
                     (t/send-text token id "Из какой вы группы?")
                     (:listen {{id :id} :from text :text}
                              :guard (when-not (contains? group-list text)
                                       (t/send-text token id (str "Увы, но я не знаю такой группы. Мне сказали что должна быть одна из: "
                                                                  (clojure.string/join " " group-list))))
                              (c/assoc-at! db [id :group] text)
                              (let [{name :name group :group} (c/get-at! db [id])]
                                (t/send-text token id (str "Итого: " name " из группы " group " зарегистрирован."
                                                           "\n"
                                                           "Если вы где-то ошиблись - выполните команду /start повторно. Помощь - /help."))))))

  (h/command "dump" {{id :id} :chat}
             (t/send-text token id (str "Всё, что мы о вас знаем:\n\n:" (c/get-at! db [id]))))

  (d/dialog "lab1" db {{id :id} :from text :text}
            :guard (let [lab1 (c/get-at! db [id :lab1])]
                     (cond
                       (:approved? lab1)
                       (t/send-text token id "У вас уже все согласовано, пора делать доклад.")
                       (:on-review? lab1)
                       (t/send-text token id "Увы, но ваше описание уже на пути к преподавателю, ждите вердикта.")
                       :else nil))

            (t/send-text token id (str "Итак, лабораторная работа №1."
                                       "\n\n"
                                       "Вам необходимо согласовать выборанный вами инцидент/случай/postmortem, "
                                       "для этого отправьте мне одним сообщением название, "
                                       "краткое описание случая и ссылки на источники. "
                                       "А я уже согласую дальше и оповещу вас о результатах."
                                       "\n\n"
                                       "На всякий случай, перед отправкой преподавателю вы проверите что за текст я сохранил."))
            (:listen {{id :id} :from text :text}
                     (c/assoc-at! db [id :lab1 :description] text)
                     (t/send-text token id "Ваше описание инцидента для Лабораторной работы №1:")
                     (t/send-text token id (c/get-at! db [id :lab1 :description]))
                     (send-yes-no-kbd token id "Все верно, могу отправлять преподавателю (текст нельзя будет изменить)?")
                     (:yes-no {{id :id :as chat} :chat}
                              :input-error (send-yes-no-kbd token id "Непонял, скажите yes или no (там вроде клавиатура должна быть).")
                              (do (t/send-text token id "Отлично, передам все преподавателю.")
                                  (c/assoc-at! db [id :lab1 :on-review?] true))
                              (t/send-text token id "Нет проблем, выполните команду /lab1 повторно."))))

  (d/dialog "lab1onNextLesson" db {{id :id} :from text :text}
            (t/send-text token id "Используйте команду /lab1benext, так как телеграм не хочет подсказывать команды с camelCase-ом."))

  (d/dialog "lab1benext" db {{id :id} :from text :text}
            :guard (let [lab1 (c/get-at! db [id :lab1])]
                     (cond
                       (not (:approved? lab1))
                       (t/send-text token id "Увы, но вам необходимо сперва согласовать свою тему доклада на первую лабораторную работу.")
                       :else nil))
            (let [group-id (c/get-at! db [id :group])
                  q (c/get-at! db [:schedule :lab1 group-id :queue])
                  q-text #(->> %
                               (map (fn [id] (lab1-status db id)))
                               (clojure.string/join "\n"))
                  ps (str "Будьте внимательны, только первые три пункта будут на ближайшем занятии. "
                          "\n\n"
                          "Если по каким-то причинам вам нужно изменить план, тогда вам надо: "
                          "1) согласовать это с остальными ребятами в очереди (найти замену, поменяться местами и т.п.); "
                          "2) сообщить об этом преподавателю, что бы внести правки в ручном режиме.")]
              (if (some #(= id %) q)
                (t/send-text token id (str "Вы уже в очереди: " "\n\n" (q-text q) "\n\n" ps))
                (let [q (c/assoc-at! db [:schedule :lab1 group-id :queue] (concat q (list id)))]
                  (t/send-text token admin-chat (str "Заявка на доклад в группу: " group-id))
                  (t/send-text token id (str "Ваш доклад добавлен в очередь на следующее занятие. Сейчас она выглядит так:"
                                             "\n\n" (q-text q)
                                             "\n\n" ps))))))

  (d/dialog "lab1schedule" db {{id :id} :from text :text}
            (doall
             (->> (c/get-at! db [:schedule :lab1])
                  (map (fn [[group desc]]
                         (t/send-text token id
                                      (str "Группа: " group
                                           "\n"
                                           (clojure.string/join "\n"
                                                                (map (fn [[i stud-id]] (str (+ 1 i) ". " (lab1-status db stud-id)))
                                                                     (zipmap (range) (:queue desc)))))))))))

  (h/command "magic" {{id :id} :chat}
             (when (= id admin-chat)
               ;(lab1-drop-from-queue db admin-chat)
               ;(t/send-text token admin-chat (c/get-at! db [492965339]))
               ;(drop-lab1-review db 434532551 "Извините, случайно одобрил (отозвал). Можете перегрузить описание указав первой строкой название доклада")
              ))

  (d/dialog "lab1status" db {{id :id} :from text :text}
            :guard (if (= id admin-chat) nil :break)
            (doall
              (->> (c/get-at! db [])
                   (group-by (fn [[id desc]] (:group desc)))
                   (filter (fn [[group _records]] (some? group)))
                   (map (fn [[group records]]
                          [group (filter (fn [[id desc]] (some? (some-> desc :lab1 :on-review?))) records)]))
                   (map (fn [[group records]]
                     (t/send-text token id
                       (str "Группа: " group
                            "\n"
                            (clojure.string/join "\n"
                                                 (map #(str "- " (apply lab1-status-str %))
                                                      (sort-by (fn [[id {{on-review? :on-review? approved? :approved?} :lab1}]] (lab1-state on-review? approved?)) records))))))))))

  (d/dialog "lab1next" db {{id :id} :from text :text}
            :guard (if (= id admin-chat)
                     (if (nil? (get-lab1-for-review db))
                       (t/send-text token id "Все просмотрено.")
                       nil)
                     :break)
            (let [[stud desc] (if true (get-lab1-for-review db) [admin-chat (c/get-at! db admin-chat)])]
              (t/send-text token id (str "Было пирслано следующее на согласование (группа " (:group desc) "): "
                                         "\n\n"
                                         (lab1-status-str id desc)
                                         "\n\n"
                                         "Тема: " (-> desc :lab1 :description clojure.string/split-lines first)))
              (t/send-text token id (-> desc :lab1 :description))
              (c/assoc-at! db [admin-chat :admin :lab1 :on-approve] stud)
              (send-yes-no-kbd token id "Все нормально?"))
            (:yes-no {{id :id :as chat} :chat}
                     :input-error (send-yes-no-kbd token id "Непонял, скажите yes или no (там вроде клавиатура должна быть).")
                     (let [stud (c/get-at! db [admin-chat :admin :lab1 :on-approve])]
                       (c/assoc-at! db [admin-chat :admin :lab1 :on-approve] nil)
                       (c/assoc-at! db [stud :lab1 :approved?] true)
                       (c/assoc-at! db [stud :lab1 :on-review?] false)

                       (t/send-text token stud "Ваше описание инцидента для лабораторной работы №1 одобрили, начинайте готовить доклад.")
                       (t/send-text token id (str "Отправили студенту одобрение."
                                                  "\n\n"
                                                  (lab1-status db stud)
                                                  "\n\n"
                                                  "Еще /lab1next?")))
                     (do
                        (t/send-text token id "Все плохо, но надо рассказать почему:")
                        (:listen {{id :id} :from text :text}
                          (let [stud (c/get-at! db [admin-chat :admin :lab1 :on-approve])]
                           (c/assoc-at! db [admin-chat :admin :lab1 :on-approve] nil)
                           (c/assoc-at! db [stud :lab1 :on-review?] false)
                           (c/assoc-at! db [stud :lab1 :approved?] false)

                           (t/send-text token stud (str "Увы, но ваше описание инцидента для лабораторно работы №1 отвергли со следующим комментарием: "
                                                         "\n\n"
                                                         text
                                                         "\n\n"
                                                         "Попробуйте снова.
                                                         "))
                           (t/send-text token id (str "Замечания отправлено студенту."
                                                      "\n\n"
                                                      (lab1-status db stud)
                                                      "\n\n"
                                                      "Ещё /lab1next")))))))

  (h/command "help" {{id :id} :chat}
    (t/send-text token id (str "/start - регистрация\n"
                               "/dump - что бот знает о вас\n"
                               "/lab1 - согласовать задание на лабораторную работу №1"))))

;; (h/defhandler bot-api
;;   (d/dialog "start" db {{id :id :as chat} :chat}
;;             (t/send-text token id "yes or no?")
;;             (:yes-no {{id :id :as chat} :chat}
;;                      :input-error (t/send-text token id "Wront input, repeat please")
;;                      (do (t/send-text token id "Your input: yes; Put text")
;;                          (:listen {{id :id :as chat} :chat} (t/send-text token id "input after yes")))
;;                      (do (t/send-text token id "Your input: no; Put text")
;;                          (:listen {{id :id :as chat} :chat} (t/send-text token id "input after no"))))))

;; (def channel (p/start token bot-api))
;; (p/stop channel)

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Bot activated, my Lord!")
  (p/start token bot-api)
  (Thread/sleep Long/MAX_VALUE))
