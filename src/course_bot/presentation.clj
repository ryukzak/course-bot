 (ns course-bot.presentation
   (:require [clojure.java.io :as io]
             [clojure.pprint :as pprint]
             [clojure.string :as str])
   (:require [codax.core :as codax])
   (:require [course-bot.general :as general]
             [course-bot.internationalization :as i18n :refer [tr]]
             [course-bot.misc :as misc]
             [course-bot.talk :as talk]))

(i18n/add-dict
 {:en
  {:pres
   {:nothing-to-check "Nothing to check."
    :group-is-already-set-error-:task-:group "Your %s group is already set: %s"
    :select-group-:task-:group "Please, select your %s group: %s"
    :missing-group-:all-groups "I don't know this group. Try again (%s)"
    :group-is-already-set-:task-:group "Your %s group set: %s"
    :on-review "On review, you will be informed when it is finished."
    :provide-description-:task "Please, provide description for your '%s' (in one message):"
    :your-description "Your description:"
    :do-you-approve "Do you approve it?"
    :teacher-will-check "Registered, the teacher will check it soon."
    :description-is-too-long-:max "Description is too long, max length is %s."
    :later "You can do this later."
    :yes-or-no "Please, yes or no?"
    :wait-for-review-:submissions-count "Wait for review: %s"
    :remarks "Remarks:"
    :receive-from-stud-topic-:group-:topic "We receive from the student (group %s): \n\nTopic: %s"
    :ok-stud-will-receive-approve-:command "OK, student will receive his approve.\n\n/%s"
    :approved-description-:task "'%s' description was approved."
    :ok-need-send-remark-for-student "OK, you need to send your remark for the student:"
    :declined-description-:command "Presentation description was declined. The student was informed about your decision.\n\n/%s"
    :rejected-description-:task-:remark "'%s' description was rejected. Remark:\n\n%s"
    :approve-yes-or-no "Approve (yes or no)?"
    :incorrect-group-one-from-:group-:all-groups "I don't know '%s', you should specify one from: %s"
    :agenda-:datetime-:group "Agenda %s (%s)"
    :expect-soon-:name "We will expect for %s soon:"
    :not-have-options "I don't have options for you."
    :select-option "Select your option:\n"
    :not-found-allow-only "Not found, allow only:\n"
    :enter-pres-number "Enter the number of the best presentation in the list:\n"
    :lesson-feedback-not-available "Lesson feedback is not available."
    :lesson-feedback-no-presentations "No presentations."
    :lesson-feedback-what-lesson-:dt-list "You need to specify lesson datetime explicitly:\n%s"
    :already-received "Already received."
    :collect-feedback-:task-:pres-group-:datetime "Collect feedback for '%s' (%s) at %s"
    :best-presentation-error "Wrong input. Enter the number of the best presentation in the list."
    :thank-feedback-saved "Thanks, your feedback saved!"
    :drop "drop"
    :all "all"
    :only-schedule "only schedule"
    :wrong-input-:command "Wrong input: /%s 12345"
    :not-found "Not found."
    :drop-config-:config-name-:stud-id "Drop '%s' config for %s?"
    :drop-student-:stud-id "We drop student: %s"
    :drop-state-:name "We drop your state for %s"
    :descriptions "%s descriptions"
    :all-scheduled-description-by-group "File with all scheduled descriptions by groups:"
    :set-group-help-:task-:key-name "Please, set your '%s' group by /%ssetgroup"
    :already-submitted-and-approved-help-:key-name "Already submitted and approved, maybe you need to schedule it? /%sschedule"
    :submit-receive-before-schedule-help-:key-name "You should submit and receive approve before scheduling. Use /%ssubmit"
    :already-scheduled-help-:key-name "Already scheduled, check /%sagenda."
    :ok-check-schedule-help-:key-name "OK, you can check it by: /%sagenda"
    :should-set-group-to-send-feedback-help-:task-:key-name "To send feedback, you should set your group for %s by /%ssetgroup"
    :setgroup-talk-:task "set your group for '%s'"
    :submit-talk-:task "submit your '%s' description"
    :check-talk "for teacher, check submitted presentation description"
    :submission-talk "list submissions and their status (no args -- your group, with args -- specified)"
    :agenda-talk "agenda (no args -- your group, with args -- specified)"
    :soon-talk-help "what will happen soon"
    :schedule-talk "select your presentation day"
    :feedback-talk-info "[<datetime>] send feedback for report"
    :drop-talk-:task-:text "for teacher, drop '%s' for specific student (%s)"
    :all-scheduled-descriptions-dump-talk "all-scheduled-descriptions-dump (admin only)"}}
  :ru
  {:pres
   {:nothing-to-check "Нечего проверять."
    :group-is-already-set-error-:task-:group "Ваша группа %s уже установлена: %s"
    :select-group-:task-:group "Пожалуйста, выберите вашу группу %s: %s"
    :missing-group-:all-groups "Я не знаю эту группу. Попробуйте еще раз (%s)"
    :group-is-already-set-:task-:group "Ваш набор групп %s: %s"
    :on-review "На рассмотрении. Вы будете проинформированы, когда оно будет завершено."
    :provide-description-:task "Пожалуйста, предоставьте описание вашего '%s' (в одном сообщении):"
    :your-description "Ваше описание:"
    :do-you-approve "Вы одобряете это?"
    :teacher-will-check "Зарегистрировано, учитель скоро проверит."
    :description-is-too-long-:max "Описание слишком длинное, максимальная длина %s."
    :later "Вы можете сделать это позже."
    :yes-or-no "Пожалуйста, да или нет?"
    :wait-for-review-:submissions-count "Дождитесь проверки: %s"
    :remarks "Примечания:"
    :receive-from-stud-topic-:group-:topic "Получаем от студента (группа %s): \n\nТема: %s"
    :ok-stud-will-receive-approve-:command "Хорошо, учащийся получит одобрение.\n\n/%s"
    :approved-description-:task "Описание '%s' одобрено."
    :ok-need-send-remark-for-student "Хорошо, вам нужно отправить свое замечание для студента:"
    :declined-description-:command "Описание презентации отклонено. Студент был проинформирован о вашем решении.\n\n/%s"
    :rejected-description-:task-:remark "Описание '%s' было отклонено. Примечание:\n\n%s"
    :approve-yes-or-no "Одобрить (да или нет)?"
    :incorrect-group-one-from-:group-:all-groups "Я не знаю '%s', вы должны указать один из: %s"
    :agenda-:datetime-:group "Повестка дня %s (%s)"
    :expect-soon-:name "Мы ожидаем для %s в ближайшее время:"
    :not-have-options "У меня нет для вас вариантов."
    :select-option "Выберите свой вариант:\n"
    :not-found-allow-only "Не найдено, разрешить только:\n"
    :enter-pres-number "Введите номер лучшей презентации в списке:\n"
    :lesson-feedback-not-available "Отзывы не доступны для этого занятия."
    :lesson-feedback-no-presentations "Нет презентаций для этого занятия."
    :lesson-feedback-what-lesson-:dt-list "Какое занятие?:\n%s"
    :already-received "Уже получено."
    :collect-feedback-:task-:pres-group-:datetime "Собрать отзывы для '%s' (%s) в %s"
    :best-presentation-error "Неправильный ввод. Введите номер лучшей презентации в списке."
    :thank-feedback-saved "Спасибо, ваш отзыв сохранен!"
    :drop "уронить"
    :all "все"
    :only-schedule "только расписание"
    :wrong-input-:command "Неверный ввод: /%s 12345"
    :not-found "Не найден."
    :drop-config-:config-name-:stud-id "Удалить конфигурацию '%s' для %s?"
    :drop-student-:stud-id "Мы бросаем студента: %s"
    :drop-state-:name "Мы сбрасываем ваше состояние на %s"
    :descriptions "%s описаний"
    :all-scheduled-description-by-group "Файл со всеми запланированными описаниями по группам:"
    :set-group-help-:task-:key-name "Пожалуйста, установите группу '%s' с помощью /%ssetgroup"
    :already-submitted-and-approved-help-:key-name "Уже отправлено и одобрено, может быть, вам нужно запланировать его? /%sрасписание"
    :submit-receive-before-schedule-help-1 "Вы должны отправить и получить одобрение до планирования. Используйте /%ssubmit"
    :already-scheduled-help-:key-name "Уже запланировано, проверьте /%sagenda."
    :ok-check-schedule-help-:key-name "Хорошо, вы можете проверить это: /%sagenda"
    :should-set-group-to-send-feedback-help-:task-:key-name "Чтобы отправить отзыв, вы должны установить свою группу для %s с помощью /%ssetgroup"
    :setgroup-talk-:task "Установить вашу группу для '%s'"
    :submit-talk-:task "Отправить описание '%s'"
    :check-talk "Для преподавателя, ревью загруженных тем"
    :submission-talk "Статус загруженных эссе (опциональный аргумент -- группа)"
    :agenda-talk "Расписание докладов (опциональный аргумент -- группа)"
    :soon-talk-help "Что произойдет в ближайшее время"
    :schedule-talk "Выбрать день презентации"
    :feedback-talk-info "[<datetime>] Отправить отзыв для отчета"
    :drop-talk-:task-:text "Для преподавателя, отбросить '%s' для конкретного ученика (%s)"
    :all-scheduled-descriptions-dump-talk "дамп всех запланированных описаний (только для администратора)"}}})

(defn get-lesson-state "per lesson" [tx pres-key pres-group datetime]
  (codax/get-at tx [:presentation pres-key pres-group datetime]))

(defn get-presentation-state "per user" [tx pres-key stud-id]
  (codax/get-at tx [stud-id :presentation pres-key]))

(defn submit-presentation [tx pres-key stud-id text]
  (-> tx
      (codax/assoc-at [stud-id :presentation pres-key :on-review?] true)
      (codax/assoc-at [stud-id :presentation pres-key :description] text)))

(defn approve-presentation [tx pres-key stud-id]
  (-> tx
      (codax/assoc-at [stud-id :presentation pres-key :on-review?] false)
      (codax/assoc-at [stud-id :presentation pres-key :approved?] true)))

(defn schedule-lesson [tx pres-key pres-group datetime stud-id]
  (-> tx
      (codax/update-at [:presentation pres-key pres-group datetime :stud-ids] #(concat % [stud-id]))
      (codax/assoc-at [stud-id :presentation pres-key :scheduled?] true)))

(defn set-presentation-group [tx pres-key stud-id pres-group]
  (codax/assoc-at tx [stud-id :presentation pres-key :group] pres-group))

(defn send-please-set-group [token id pres-key-name name]
  (talk/send-text token id (format (tr :pres/set-group-help-:task-:key-name) name pres-key-name)))

(defn setgroup-talk [db {token :token :as conf} pres-key-name]
  (let [cmd (str pres-key-name "setgroup")
        pres-key (keyword pres-key-name)
        name (-> conf (get pres-key) :name)
        groups (-> conf (get pres-key) :groups)
        groups-text (->> groups keys sort (str/join ", "))]
    (talk/def-talk db cmd
      (format (tr :pres/setgroup-talk-:task) name)

      :start
      (fn [tx {{id :id} :from}]
        (let [pres-group (codax/get-at tx [id :presentation pres-key :group])]
          (if (some? pres-group)
            (do (talk/send-text token id (format (tr :pres/group-is-already-set-error-:task-:group) name pres-group))
                (talk/stop-talk tx))
            (do (talk/send-text token id (format (tr :pres/select-group-:task-:group) name groups-text))
                (talk/change-branch tx :set-group)))))

      :set-group
      (fn [tx {{id :id} :from text :text}]
        (when-not (get groups text)
          (talk/send-text token id (format (tr :pres/missing-group-:all-groups) groups-text))
          (talk/wait tx))

        (talk/send-text token id (format (tr :pres/group-is-already-set-:task-:group) name text))
        (-> tx
            (set-presentation-group pres-key id text)
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
        {:keys [max-description-length submition-hint name]} (-> conf (get pres-key))]

    (talk/def-talk db cmd
      (format (tr :pres/submit-talk-:task) name)

      :start
      (fn [tx {{id :id} :from}]
        (let [pres (codax/get-at tx [id :presentation pres-key])
              group (-> pres :group)]
          (when-not group
            (send-please-set-group token id pres-key-name name)
            (talk/stop-talk tx))

          (when (:on-review? pres)
            (talk/send-text token id (tr :pres/on-review))
            (talk/stop-talk tx))

          (when (:approved? pres)
            (talk/send-text token id (format (tr :pres/already-submitted-and-approved-help-:key-name) pres-key-name))
            (talk/stop-talk tx))

          (talk/send-text token id (if submition-hint
                                     submition-hint
                                     (format (tr :pres/provide-description-:task) name)))
          (talk/change-branch tx :recieve-description)))

      :recieve-description
      (fn [tx {{id :id} :from text :text}]
        (talk/send-text token id (tr :pres/your-description))
        (talk/send-text token id text)
        (talk/send-yes-no-kbd token id (tr :pres/do-you-approve))
        (when-not (or (nil? max-description-length)
                      (<= (count text) max-description-length))
          (talk/send-text token id (format (tr :pres/description-is-too-long-:max) max-description-length))
          (talk/wait tx))
        (talk/change-branch tx :approve {:desc text}))

      :approve
      (fn [tx {{id :id} :from text :text} {desc :desc}]
        (case (i18n/normalize-yes-no-text text)
          "yes" (do (talk/send-text token id (tr :pres/teacher-will-check))
                    (-> tx
                        (submit-presentation pres-key id desc)
                        talk/stop-talk))
          "no" (talk/send-stop tx token id (tr :pres/later))
          (talk/clarify-input tx token id (format (tr :talk/clarify-input-tmpl) text)))))))

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
      (tr :pres/check-talk)
      :start
      (fn [tx {{id :id} :from}]
        (general/assert-admin tx conf id)
        (let [submitions (wait-for-reviews tx pres-key)
              submition (first submitions)]
          (when (nil? submition)
            (talk/send-text token id (tr :pres/nothing-to-check))
            (talk/stop-talk tx))
          (let [[stud-id info] submition
                group (-> info :presentation (get pres-key) :group)
                desc (-> info :presentation (get pres-key) :description)
                remarks (codax/get-at tx [stud-id :presentation pres-key :remarks])]
            (talk/send-text token id (format (tr :pres/wait-for-review-:submissions-count) (count submitions)))
            (talk/send-text token id (approved-submissions tx pres-key group))
            (when (some? remarks)
              (talk/send-text token id (tr :pres/remarks))
              (doall (->> remarks reverse (map #(talk/send-text token id %)))))
            (talk/send-text token id (format (tr :pres/receive-from-stud-topic-:group-:topic) (-> info :group) (topic desc)))
            (talk/send-text token id desc)
            (talk/send-yes-no-kbd token id (tr :pres/approve-yes-or-no))
            (talk/change-branch tx :approve {:stud-id stud-id}))))

      :approve
      (fn [tx {{id :id} :from text :text} {stud-id :stud-id}]
        (case (i18n/normalize-yes-no-text text)
          "yes" (do (talk/send-text token id (format (tr :pres/ok-stud-will-receive-approve-:command) cmd))
                    (talk/send-text token stud-id (format (tr :pres/approved-description-:task) name))
                    (-> tx
                        (approve-presentation pres-key stud-id)
                        talk/stop-talk))

          "no" (do (talk/send-text token id (tr :pres/ok-need-send-remark-for-student))
                   (talk/change-branch tx :remark {:stud-id stud-id}))

          (talk/clarify-input tx token id (format (tr :talk/clarify-input-tmpl) text))))

      :remark
      (fn [tx {{id :id} :from remark :text} {stud-id :stud-id}]
        (talk/send-text token id (format (tr :pres/declined-description-:command) cmd))
        (talk/send-text token stud-id (format (tr :pres/rejected-description-:task-:remark) name remark))
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
      (tr :pres/submission-talk)
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
            (talk/send-text token id (format (tr :pres/incorrect-group-one-from-:group-:all-groups) arg groups-text)))
          (talk/stop-talk tx))))))

(defn lessons [pres-conf group]
  (-> pres-conf :groups (get group) :lessons))

(defn in-min-interval?
  ([dt now a] (in-min-interval? dt now a nil))
  ([dt now a b]
   (let [dt (misc/read-time dt)
         offset (/ (- now dt) (* 1000 60))]
     (and (or (nil? a) (<= a offset))
          (or (nil? b) (<= offset b))))))

(defn filter-lesson [cut-off-in-min now lessons]
  (let [scale (* 1000 60)]
    (filter #(let [dt (misc/read-time (:datetime %))]
               (or (nil? now)
                   (<= cut-off-in-min (/ (- dt now) scale))))
            lessons)))

(defn future-lessons [pres-conf group now]
  (let [cut-off-in-min (-> pres-conf :schedule-cut-off-time-in-min)]

    (->> (lessons pres-conf group)
         (filter #(or (nil? now)
                      (in-min-interval? (:datetime %) now nil (- cut-off-in-min)))))))

(defn agenda [tx pres-conf pres-id group now]
  (let [cut-off-in-min (-> pres-conf :agenda-hide-cut-off-time-in-min)
        comment (-> pres-conf :groups (get group) :comment)]
    (->> (lessons pres-conf group)
         (filter-lesson cut-off-in-min now)
         (map #(let [dt (:datetime %)
                     studs (codax/get-at tx [:presentation pres-id group dt :stud-ids])]
                 (str (format (tr :pres/agenda-:datetime-:group) dt group) (when (some? comment) (str ", " comment)) ":\n"
                      (str/join "\n" (map-indexed (fn [idx e] (str (+ 1 idx) ". " (presentation tx e pres-id))) studs))))))))

(defn soon [tx conf pres-id group now]
  (let [scale (* 1000 60 60)]
    (->> (lessons (-> conf (get pres-id)) group)
         (filter #(let [dt (misc/read-time (:datetime %))
                        diff (/ (- dt now) scale)]
                    (and (> diff -24) (<= diff 48))))
         (map #(let [dt (:datetime %)
                     comment (-> conf (get pres-id) :groups (get group) :comment)
                     studs (codax/get-at tx [:presentation pres-id group dt :stud-ids])
                     last-names (str/join ", "
                                          (map (fn [stud-id] (let [name (codax/get-at tx [stud-id :name])]
                                                               (if (nil? name)
                                                                 "ANONYMOUS"
                                                                 (-> name
                                                                     (str/split #"\s+")
                                                                     first))))

                                               studs))]

                 (str (format (tr :pres/agenda-:datetime-:group) dt group)
                      (when (some? comment) (str ", " comment)) ":\n"
                      (format "1. [%s | %s %s]()"
                              last-names
                              (-> dt (str/split #"\s+") first)
                              (-> conf (get pres-id) :agenda-postfix))
                      (when (not-empty studs) "\n")
                      (str/join "\n"
                                (map-indexed (fn [idx e] (str "    " (+ 1 idx) ". " (presentation tx e pres-id))) studs))))))))

(defn agenda-talk [db {token :token admin-chat-id :admin-chat-id :as conf} pres-key-name]
  (let [cmd (str pres-key-name "agenda")
        pres-key (keyword pres-key-name)
        {:keys [name groups] :as pres-conf} (-> conf (get pres-key))
        groups-text (->> groups keys sort (str/join ", "))]

    (talk/def-command db cmd (tr :pres/agenda-talk)
      (fn [tx {{id :id} :from text :text}]
        (let [arg (talk/command-text-arg text)]
          (cond
            (and (= id admin-chat-id) (or (= arg "")
                                          (= arg "all")))
            (doall (->> groups keys sort
                        (map #(agenda tx pres-conf pres-key % (when (= arg "") (misc/today))))
                        (apply concat)
                        (map #(talk/send-text token id %))))

            (= arg "")
            (let [group (codax/get-at tx [id :presentation pres-key :group])]
              (if (nil? group)
                (send-please-set-group token id pres-key-name name)
                (doall (->> (agenda tx pres-conf pres-key group (misc/today))
                            (map #(talk/send-text token id %))))))

            (get groups arg)
            (doall (->> (agenda tx pres-conf pres-key arg (misc/today))
                        (map #(talk/send-text token id %))))

            :else
            (talk/send-text token id (format (tr :pres/incorrect-group-one-from-:group-:all-groups) arg groups-text)))
          (talk/stop-talk tx))))))

(defn soon-talk [db {token :token :as conf} pres-key-name]
  (let [cmd (str pres-key-name "soon")
        pres-key (keyword pres-key-name)
        name (-> conf (get pres-key) :name)
        groups (-> conf (get pres-key) :groups)]
    (talk/def-command db cmd
      (tr :pres/soon-talk-help)
      (fn [tx {{id :id} :from}]
        (talk/send-text token id (format (tr :pres/expect-soon-:name) name))
        (doall (->> groups keys sort
                    (map #(soon tx conf pres-key % (misc/today)))
                    (apply concat)
                    (map #(talk/send-text token id %))))
        (talk/stop-talk tx)))))

(defn schedule-talk [db {token :token :as conf} pres-key-name]
  (let [cmd (str pres-key-name "schedule")
        pres-key (keyword pres-key-name)
        {:keys [name] :as pres-conf} (-> conf (get pres-key))]
    (talk/def-talk db cmd
      (tr :pres/schedule-talk)
      :start
      (fn [tx {{id :id} :from}]
        (let [pres (codax/get-at tx [id :presentation pres-key])
              group (-> pres :group)]
          (when-not group
            (send-please-set-group token id pres-key-name name)
            (talk/stop-talk tx))

          (when-not (-> pres :approved?)
            (talk/send-text token id (format (tr :pres/submit-receive-before-schedule-help-1) pres-key-name))
            (talk/stop-talk tx))

          (when (-> pres :scheduled?)
            (talk/send-text token id (format (tr :pres/already-scheduled-help-:key-name) pres-key-name))
            (talk/stop-talk tx))

          (let [future (future-lessons pres-conf group (misc/today))]
            (when (empty? future)
              (talk/send-text token id (tr :pres/select-option))
              (talk/stop-talk tx))

            (doall (map #(talk/send-text token id %)
                        (agenda tx pres-conf pres-key group (misc/today))))
            (talk/send-text token id
                            (str (tr :pres/select-option)
                                 (->> future
                                      (map #(str "- " (:datetime %)))
                                      (str/join "\n"))))
            (talk/change-branch tx :get-date))))

      :get-date
      (fn [tx {{id :id} :from text :text}]
        (let [pres (codax/get-at tx [id :presentation pres-key])
              group (-> pres :group)
              future (future-lessons pres-conf group (misc/today))
              dt (some #(-> % :datetime (= text)) future)]
          (when (nil? dt)
            (talk/send-text token id
                            (str (tr :pres/not-found-allow-only)
                                 (->> future
                                      (map #(str "- " (:datetime %)))
                                      (str/join "\n"))))
            (talk/repeat-branch tx))
          (talk/send-text token id (format (tr :pres/ok-check-schedule-help-:key-name) pres-key-name))
          (-> tx
              (schedule-lesson pres-key group text id)
              talk/stop-talk))))))

(defn feedback-str [studs]
  (str (tr :pres/enter-pres-number)
       (->> studs
            (map-indexed #(str %1 ". " (:name %2) " (" (:topic %2) ")"))
            (str/join "\n"))))

(defn feedback-talk [db {token :token :as conf} pres-key-str]
  (let [pres-key (keyword pres-key-str)
        {:keys [name] :as pres-conf} (-> conf (get pres-key))
        cmd (str pres-key-str "feedback")]
    (talk/def-talk db cmd (tr :pres/feedback-talk-info)
      :start
      (fn [tx {{id :id} :from text :text}]
        (let [now (misc/today)
              {pres-group :group} (get-presentation-state tx pres-key id)

              dt (if-let [current-lesson-dt
                          (->> (lessons pres-conf pres-group)
                               (filter #(in-min-interval? (:datetime %) now 30 180))
                               first :datetime)]
                   current-lesson-dt
                   (talk/command-text-arg-or-nil text))

              {:keys [stud-ids feedback-from]} (get-lesson-state tx pres-key pres-group dt)

              studs (->> stud-ids
                         (map #(let [{:keys [name presentation]} (codax/get-at tx [%])]
                                 {:id %
                                  :name name
                                  :topic (-> presentation
                                             (get pres-key)
                                             :description
                                             topic)})))]

          (when (nil? pres-group)
            (talk/send-text token id (format (tr :pres/should-set-group-to-send-feedback-help-:task-:key-name) name pres-key-str))
            (talk/stop-talk tx))

          (when (nil? dt)
            (let [pass-lessons (->> (lessons pres-conf pres-group)
                                    (filter #(in-min-interval? (:datetime %) now 0 nil)))]
              (if (not (empty? pass-lessons))
                (talk/send-text token id
                                (format (tr :pres/lesson-feedback-what-lesson-:dt-list)
                                        (->> pass-lessons
                                             (map #(str "- " (:datetime %)))
                                             (str/join "\n"))))
                (talk/send-text token id (tr :pres/lesson-feedback-not-available))))
            (talk/stop-talk tx))

          (when (empty? studs)
            (talk/send-text token id (tr :pres/lesson-feedback-no-presentations))
            (talk/stop-talk tx))

          (when (some #(= id %) feedback-from)
            (talk/send-text token id (tr :pres/already-received))
            (talk/stop-talk tx))

          (talk/send-text token id
                          (format (tr :pres/collect-feedback-:task-:pres-group-:datetime) name pres-group dt))
          (talk/send-text token id (feedback-str studs))
          (talk/change-branch tx :select {:rank [] :remain studs :group pres-group :dt dt})))

      :select
      (fn [tx {{id :id} :from text :text} {rank :rank studs :remain group :group dt :dt :as state}]
        (let [n (if (re-matches #"^\d+$" text) (parse-long text) nil)]
          (when (or (nil? n) (not (< n (count studs))))
            (talk/send-text token id (tr :pres/best-presentation-error))
            (talk/wait tx))

          (when (> (count studs) 1)
            (let [best (nth studs n)
                  studs (concat (take n studs) (drop (+ n 1) studs))]
              (talk/send-text token id (feedback-str studs))
              (talk/change-branch tx :select
                                  (assoc state
                                         :rank (conj rank best)
                                         :remain studs))))

          (talk/send-text token id (tr :pres/thank-feedback-saved))
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
        help (format (tr :pres/drop-talk-:task-:text) name
                     (if drop-all (tr :pres/all) (tr :pres/drop)))]
    (talk/def-talk db cmd help

      :start
      (fn [tx {{id :id} :from text :text}]
        (general/assert-admin tx conf id)
        (let [stud-id (talk/command-num-arg text)]

          (when (nil? stud-id)
            (talk/send-text token id (format (tr :pres/wrong-input-:command) cmd))
            (talk/stop-talk tx))

          (let [stud (codax/get-at tx [stud-id])]
            (when-not stud
              (talk/send-text token id (tr :pres/not-found))
              (talk/stop-talk tx))

            (general/send-whoami tx token id stud-id)
            (talk/send-yes-no-kbd token id (format (tr :pres/drop-config-:config-name-:stud-id) name stud-id))

            (talk/change-branch tx :approve {:stud-id stud-id}))))

      :approve
      (fn [tx {{id :id} :from text :text} {stud-id :stud-id}]
        (case (i18n/normalize-yes-no-text text)
          "yes" (let [group (codax/get-at tx [stud-id :presentation pres-key :group])
                      lessons (codax/get-at tx [:presentation pres-key group])]
                  (talk/send-text token id (format (tr :pres/drop-student-:stud-id) stud-id))
                  (talk/send-text token stud-id (format (tr :pres/drop-state-:name) name))
                  (-> (if drop-all
                        (codax/assoc-at tx [stud-id :presentation pres-key] nil)
                        (codax/assoc-at tx [stud-id :presentation pres-key :scheduled?] nil))
                      (codax/assoc-at [:presentation pres-key group]
                                      (->> lessons
                                           (map (fn [[dt desc]]
                                                  [dt (assoc desc
                                                             :stud-ids (filter #(not= stud-id %) (:stud-ids desc)))]))
                                           (into {})))
                      talk/stop-talk))
          "no" (talk/send-stop tx token id (tr :talk/cancelled))
          (talk/clarify-input tx token id (format (tr :talk/clarify-input-tmpl) text)))))))

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
        (misc/round-2 avg)))))

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

(defn report-presentation-avg-rank [pres-key-name]
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
               (str "## (" name " -- " (topic desc) ")\n\n" desc)))
       (str/join "\n\n")))

(defn all-scheduled-descriptions-dump-talk [db {token :token :as conf} pres-id]
  (let [pres-key (keyword pres-id)
        cmd (str pres-id "descriptions")]
    (talk/def-command db cmd
      (tr :pres/all-scheduled-descriptions-dump-talk)
      (fn [tx {{id :id} :from}]
        (general/assert-admin tx conf id)

        (talk/send-text token id (tr :pres/all-scheduled-description-by-group))
        (let [dt (.format (java.text.SimpleDateFormat. "yyyy-MM-dd-HH-mm-Z") (misc/today))
              filename (str "tmp/" dt "-" pres-id "-descriptions.md")
              groups (-> conf (get pres-key) :groups keys)
              data (codax/get-at tx [])
              text (->> groups
                        (map #(str "# " % "\n\n" (scheduled-descriptions-dump data pres-key %)))
                        (str/join "\n\n\n"))]
          (io/make-parents filename)
          (spit filename text)
          (talk/send-document token id (io/file filename)))
        tx))))

(i18n/add-dict
 {:en {:pres
       {:restore-lost-and-found-cmd-help "(admin) Restore lost-and-found lessons."
        :lost-and-found-collision "Collision between lost-and-found lessons and scheduled lessons. Canceled."
        :lost-and-found-restore? "Restore lost-and-found lessons?"
        :lost-and-found-canceled "Lost-and-found lessons restore canceled."
        :lost-and-found-restored "Lost-and-found lessons restored."}}
  :ru {:pres
       {:restore-lost-and-found-cmd-help "(admin) Restore lost-and-found lessons."
        :lost-and-found-collision "Конфликт между уроками из lost-and-found и запланированными уроками. Отменено."
        :lost-and-found-restore? "Восстановить уроки из lost-and-found?"
        :lost-and-found-canceled "Восстановление из lost-and-found отменено."
        :lost-and-found-restored "Уроки из lost-and-found восстановлены."}}})

(defn lost-and-found-talk [db {token :token :as conf} pres-key-name]
  (let [cmd (str pres-key-name "lostandfound")
        pres-key (keyword pres-key-name)
        {:keys [lost-and-found]} (-> conf (get pres-key))
        cmd-help (tr :pres/restore-lost-and-found-cmd-help)]
    (talk/def-talk db cmd cmd-help
      :start
      (fn [tx {{id :id} :from}]
        (general/assert-admin tx conf id)
        (let [changes (for [[pres-group {:keys [lessons]}] lost-and-found
                            {:keys [datetime presentations]} lessons
                            :let [current-state (get-lesson-state tx pres-key (->> pres-group) datetime)
                                  lost-state (->> presentations
                                                  (map (fn [{:keys [stud-id text]}]
                                                         {:id stud-id
                                                          :topic (topic text)
                                                          :name (:name (general/stud-info tx stud-id))
                                                          :text text
                                                          :pres-group pres-group})))]]
                        {:pres-group pres-group
                         :datetime datetime
                         :current-state current-state
                         :collision (not (or (nil? current-state)
                                             (-> current-state :stud-ids empty?)))
                         :lost-state lost-state})]

          (doall (for [lesson changes]
                   (let [without-text (assoc lesson :lost-state (map #(dissoc % :text) (:lost-state lesson)))]
                     (talk/send-text token id (with-out-str (pprint/pprint without-text))))))

          (when (some :collision changes)
            (talk/send-text token id (tr :pres/lost-and-found-collision))
            (talk/stop-talk tx))
          (talk/send-text token id (tr :pres/lost-and-found-restore?))
          (talk/change-branch tx :approve {:changes changes})))

      :approve
      (fn [tx {{id :id} :from text :text} {:keys [changes]}]
        (case (i18n/normalize-yes-no-text text)
          "yes" (do (talk/send-text token id (tr :pres/lost-and-found-restored))
                    (-> (reduce (fn [tx' {:keys [pres-group datetime lost-state]}]
                                  (reduce (fn [tx'' {stud-id :id :keys [topic]}]
                                            (-> tx''
                                                (submit-presentation pres-key stud-id topic)
                                                (approve-presentation pres-key stud-id)
                                                (schedule-lesson pres-key pres-group datetime stud-id)))
                                          tx'
                                          lost-state))
                                tx
                                changes)
                        talk/stop-talk))

          "no" (talk/send-stop tx token id (tr :pres/lost-and-found-canceled))

          (talk/clarify-input tx token id))))))
