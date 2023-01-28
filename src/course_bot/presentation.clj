 (ns course-bot.presentation
   (:require [clojure.java.io :as io]
             [clojure.string :as str])
   (:require [codax.core :as codax])
   (:require [course-bot.talk :as talk]
             [course-bot.general :as general :refer [tr]]
             [course-bot.misc :as misc]))

(general/add-dict
 {:en
  {:pres
   {:nothing-to-check "Nothing to check."
    :group-is-already-set-error-2 "Your %s group is already set: %s"
    :select-group-2 "Please, select your %s group: %s"
    :missing-group-1 "I don't know this group. Try again (%s)"
    :group-is-already-set-2 "Your %s group set: %s"
    :on-review "On review, you will be informed when it is finished."
    :provide-description-1 "Please, provide description for your '%s' (in one message):"
    :your-description "Your description:"
    :do-you-approve "Do you approve it?"
    :teacher-will-check "Registered, the teacher will check it soon."
    :later "You can do this later."
    :yes-or-no "Please, yes or no?"
    :wait-for-review-1 "Wait for review: %s"
    :remarks "Remarks:"
    :receive-from-stud-topic-2 "We receive from the student (group %s): \n\nTopic: %s"
    :ok-stud-will-receive-approve-1 "OK, student will receive his approve.\n\n/%s"
    :approved-description-1 "'%s' description was approved."
    :ok-need-send-remark-for-student "OK, you need to send your remark for the student:"
    :declined-description-1 "Presentation description was declined. The student was informed about your decision.\n\n/%s"
    :rejected-description-2 "'%s' description was rejected. Remark:\n\n%s"
    :approve-yes-or-no "Approve (yes or no)?"
    :incorrect-group-one-from-2 "I don't know '%s', you should specify one from: %s"
    :agenda-2 "Agenda %s (%s)"
    :expect-soon-1 "We will expect for %s soon:"
    :not-have-options "I don't have options for you."
    :select-option "Select your option:\n"
    :not-found-allow-only "Not found, allow only:\n"
    :enter-pres-number "Enter the number of the best presentation in the list:\n"
    :feedback-collecting-disabled "Feedback collecting disabled (too early or too late)."
    :already-received "Already received."
    :collect-feedback-3 "Collect feedback for '%s' (%s) at %s"
    :best-presentation-error "Wrong input. Enter the number of the best presentation in the list."
    :thank-feedback-saved "Thanks, your feedback saved!"
    :drop "drop"
    :all "all"
    :only-schedule "only schedule"
    :wrong-input-1 "Wrong input: /%s 12345"
    :not-found "Not found."
    :drop-config-2 "Drop '%s' config for %s?"
    :drop-student-1 "We drop student: %s"
    :drop-state-1 "We drop your state for %s"
    :descriptions "%s descriptions"
    :all-scheduled-description-by-group "File with all scheduled descriptions by groups:"
    :yes "yes"
    :no "no"
    :set-group-help-2 "Please, set your '%s' group by /%ssetgroup"
    :already-submitted-and-approved-help-1 "Already submitted and approved, maybe you need to schedule it? /%sschedule"
    :submit-receive-before-schedule-help-1 "You should submit and receive approve before scheduling. Use /%ssubmit"
    :already-scheduled-help-1 "Already scheduled, check /%sagenda."
    :ok-check-schedule-help-1 "OK, you can check it by: /%sagenda"
    :should-set-group-to-send-feedback-help-2 "To send feedback, you should set your group for %s by /%ssetgroup"
    :setgroup-talk-1 "set your group for '%s'"
    :submit-talk-1 "submit your '%s' description"
    :check-talk "for teacher, check submitted presentation description"
    :submisstion-talk "list submissions and their status (no args -- your group, with args -- specified)"
    :agenda-talk "agenda (no args -- your group, with args -- specified)"
    :soon-talk "what will happen soon"
    :schedule-talk "select your presentation day"
    :feedback-talk "send feedback for report"
    :drop-talk-2 "for teacher, drop '%s' for specific student (%s)"
    :all-scheduled-descriptions-dump-talk "all-scheduled-descriptions-dump (admin only)"}}
  :ru
  {:pres
   {:nothing-to-check "Нечего проверять."
    :group-is-already-set-error-2 "Ваша группа %s уже установлена: %s"
    :select-group-2 "Пожалуйста, выберите вашу группу %s: %s"
    :missing-group-1 "Я не знаю эту группу. Попробуйте еще раз (%s)"
    :group-is-already-set-2 "Ваш набор групп %s: %s"
    :on-review "На рассмотрении вы будете проинформированы, когда оно будет завершено."
    :provide-description-1 "Пожалуйста, предоставьте описание вашего '%s' (в одном сообщении):"
    :your-description "Ваше описание:"
    :do-you-approve "Вы одобряете это?"
    :teacher-will-check "Зарегистрировался, учитель скоро проверит."
    :later "Вы можете сделать это позже."
    :yes-or-no "Пожалуйста, да или нет?"
    :wait-for-review-1 "Дождитесь проверки: %s"
    :remarks "Примечания:"
    :receive-from-stud-topic-2 "Получаем от студента (группа %s): \n\nТема: %s"
    :ok-stud-will-receive-approve-1 "Хорошо, учащийся получит одобрение.\n\n/%s"
    :approved-description-1 "Описание '%s' одобрено."
    :ok-need-send-remark-for-student "Хорошо, вам нужно отправить свое замечание для студента:"
    :declined-description-1 "Описание презентации отклонено. Студент был проинформирован о вашем решении.\n\n/%s"
    :rejected-description-2 "Описание '%s' было отклонено. Примечание:\n\n%s"
    :approve-yes-or-no "Одобрить (да или нет)?"
    :incorrect-group-one-from-2 "Я не знаю '%s', вы должны указать один из: %s"
    :agenda-2 "Повестка дня %s (%s)"
    :expect-soon-1 "Мы ожидаем для %s в ближайшее время:"
    :not-have-options "У меня нет для тебя вариантов."
    :select-option "Выберите свой вариант:\n"
    :not-found-allow-only "Не найдено, разрешить только:\n"
    :enter-pres-number "Введите номер лучшей презентации в списке:\n"
    :feedback-collecting-disabled "Сбор отзывов отключен (слишком рано или слишком поздно)."
    :already-received "Уже получено."
    :collect-feedback-3 "Собрать отзывы для '%s' (%s) в %s"
    :best-presentation-error "Неправильный ввод. Введите номер лучшей презентации в списке."
    :thank-feedback-saved "Спасибо, ваш отзыв сохранен!"
    :drop "уронить"
    :all "все"
    :only-schedule "только расписание"
    :wrong-input-1 "Неверный ввод: /%s 12345"
    :not-found "Не найден."
    :drop-config-2 "Удалить конфигурацию '%s' для %s?"
    :drop-student-1 "Мы бросаем студента: %s"
    :drop-state-1 "Мы сбрасываем ваше состояние на %s"
    :descriptions "%s описаний"
    :all-scheduled-description-by-group "Файл со всеми запланированными описаниями по группам:"
    :yes "да"
    :no "нет"
    :set-group-help-2 "Пожалуйста, установите группу '%s' с помощью /%ssetgroup"
    :already-submitted-and-approved-help-1 "Уже отправлено и одобрено, может быть, вам нужно запланировать его? /%sрасписание"
    :submit-receive-before-schedule-help-1 "Вы должны отправить и получить одобрение до планирования. Используйте /%ssubmit"
    :already-scheduled-help-1 "Уже запланировано, проверьте /%sagenda."
    :ok-check-schedule-help-1 "Хорошо, вы можете проверить это: /%sagenda"
    :should-set-group-to-send-feedback-help-2 "Чтобы отправить отзыв, вы должны установить свою группу для %s с помощью /%ssetgroup"
    :setgroup-talk-1 "установить вашу группу для '%s'"
    :submit-talk-1 "отправьте описание '%s'"
    :check-talk "для учителя, проверьте представленное описание презентации"
    :submisstion-talk "список представлений и их статус (без аргументов - ваша группа, с аргументами - указано)"
    :agenda-talk "повестка дня (без аргументов -- ваша группа, с аргументами -- указано)"
    :soon-talk "что произойдет в ближайшее время"
    :schedule-talk "выберите день презентации"
    :feedback-talk "отправить отзыв для отчета"
    :drop-talk-2 "для учителя, отбросить '%s' для конкретного ученика (%s)"
    :all-scheduled-descriptions-dump-talk "дамп всех запланированных описаний (только для администратора)"}}})

(defn send-please-set-group [token id pres-key-name name]
  (talk/send-text token id (format (tr :pres/set-group-help-2) name pres-key-name)))

(defn setgroup-talk [db {token :token :as conf} pres-key-name]
  (let [cmd (str pres-key-name "setgroup")
        pres-key (keyword pres-key-name)
        name (-> conf (get pres-key) :name)
        groups (-> conf (get pres-key) :groups)
        groups-text (->> groups keys sort (str/join ", "))]
    (talk/def-talk db cmd
      (format (tr :pres/setgroup-talk-1) name)

      :start
      (fn [tx {{id :id} :from}]
        (let [pres-group (codax/get-at tx [id :presentation pres-key :group])]
          (if (some? pres-group)
            (do (talk/send-text token id (format (tr :pres/group-is-already-set-error-2) name pres-group))
                (talk/stop-talk tx))
            (do (talk/send-text token id (format (tr :pres/select-group-2) name groups-text))
                (talk/change-branch tx :set-group)))))

      :set-group
      (fn [tx {{id :id} :from text :text}]
        (when-not (get groups text)
          (talk/send-text token id (format (tr :pres/missing-group-1) groups-text))
          (talk/wait tx))

        (talk/send-text token id (format (tr :pres/group-is-already-set-2) name text))
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
      (format (tr :pres/submit-talk-1) name)

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
            (talk/send-text token id (format (tr :pres/already-submitted-and-approved-help-1) pres-key-name))
            (talk/stop-talk tx))

          (talk/send-text token id (if hint
                                     hint
                                     (format (tr :pres/provide-description-1) name)))
          (talk/change-branch tx :recieve-description)))

      :recieve-description
      (fn [tx {{id :id} :from text :text}]
        (talk/send-text token id (tr :pres/your-description))
        (talk/send-text token id text)
        (talk/send-yes-no-kbd token id (tr :pres/do-you-approve))
        (talk/change-branch tx :approve {:desc text}))

      :approve
      (fn [tx {{id :id} :from text :text} {desc :desc}]
        (cond
          (= text (tr :pres/yes)) (do (talk/send-text token id (tr :pres/teacher-will-check))
                                      (-> tx
                                          (codax/assoc-at [id :presentation pres-key :on-review?] true)
                                          (codax/assoc-at [id :presentation pres-key :description] desc)
                                          talk/stop-talk))
          (= text (tr :pres/no)) (do (talk/send-text token id (tr :pres/later))
                                     (talk/stop-talk tx))
          :else (do (talk/send-text token id (tr :pres/yes-or-no))
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
            (talk/send-text token id (format (tr :pres/wait-for-review-1) (count submitions)))
            (talk/send-text token id (approved-submissions tx pres-key group))
            (when (some? remarks)
              (talk/send-text token id (tr :pres/remarks))
              (doall (->> remarks reverse (map #(talk/send-text token id %)))))
            (talk/send-text token id (format (tr :pres/receive-from-stud-topic-2) (-> info :group) (topic desc)))
            (talk/send-text token id desc)
            (talk/send-yes-no-kbd token id (tr :pres/approve-yes-or-no))
            (talk/change-branch tx :approve {:stud-id stud-id}))))

      :approve
      (fn [tx {{id :id} :from text :text} {stud-id :stud-id}]
        (talk/if-parse-yes-or-no
         tx token id text
         (do (talk/send-text token id (format (tr :pres/ok-stud-will-receive-approve-1) cmd))
             (talk/send-text token stud-id (format (tr :pres/approved-description-1) name))
             (-> tx
                 (codax/assoc-at [stud-id :presentation pres-key :on-review?] false)
                 (codax/assoc-at [stud-id :presentation pres-key :approved?] true)
                 talk/stop-talk))

         (do (talk/send-text token id (tr :pres/ok-need-send-remark-for-student))
             (talk/change-branch tx :remark {:stud-id stud-id}))))

      :remark
      (fn [tx {{id :id} :from remark :text} {stud-id :stud-id}]
        (talk/send-text token id (format (tr :pres/declined-description-1) cmd))
        (talk/send-text token stud-id (format (tr :pres/rejected-description-2) name remark))
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
      (tr :pres/submisstion-talk)
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
            (talk/send-text token id (format (tr :pres/incorrect-group-one-from-2) arg groups-text)))
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
                 (str (format (tr :pres/agenda-2) dt group) (when (some? comment) (str ", " comment)) ":\n"
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
                 (str (format (tr :pres/agenda-2) dt group) (when (some? comment) (str ", " comment)) ":\n"
                      (str/join "\n" (map-indexed (fn [idx e] (str (+ 1 idx) ". " (presentation tx e pres-id))) studs))))))))

(defn agenda-talk [db {token :token admin-chat-id :admin-chat-id :as conf} pres-key-name]
  (let [cmd (str pres-key-name "agenda")
        pres-key (keyword pres-key-name)
        name (-> conf (get pres-key) :name)
        groups (-> conf (get pres-key) :groups)
        groups-text (->> groups keys sort (str/join ", "))]

    (talk/def-command db cmd
      (tr :pres/agenda-talk)
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
            (talk/send-text token id (format (tr :pres/incorrect-group-one-from-2) arg groups-text)))
          (talk/stop-talk tx))))))

(defn soon-talk [db {token :token  :as conf} pres-key-name]
  (let [cmd (str pres-key-name "soon")
        pres-key (keyword pres-key-name)
        name (-> conf (get pres-key) :name)
        groups (-> conf (get pres-key) :groups)]
    (talk/def-command db cmd
      (tr :pres/soon-talk)
      (fn [tx {{id :id} :from}]
        (talk/send-text token id (format (tr :pres/expect-soon-1) name))
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
            (talk/send-text token id (format (tr :pres/already-scheduled-help-1)  pres-key-name))
            (talk/stop-talk tx))

          (let [future (future-lessons conf pres-key group (misc/today))]
            (when (empty? future)
              (talk/send-text token id (tr :pres/select-option))
              (talk/stop-talk tx))

            (doall (map #(talk/send-text token id %)
                        (agenda tx conf pres-key group (misc/today))))
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
              future (future-lessons conf pres-key group (misc/today))
              dt (some #(-> % :datetime (= text)) future)]
          (when (nil? dt)
            (talk/send-text token id
                            (str (tr :pres/not-found-allow-only)
                                 (->> future
                                      (map #(str "- " (:datetime %)))
                                      (str/join "\n"))))
            (talk/repeat-branch tx))
          (talk/send-text token id (format (tr :pres/ok-check-schedule-help-1) pres-key-name))
          (-> tx
              (codax/update-at [:presentation pres-key group text :stud-ids]
                               #(concat % [id]))
              (codax/assoc-at [id :presentation pres-key :scheduled?] true)
              talk/stop-talk))))))

(defn feedback-str [studs]
  (str (tr :pres/enter-pres-number)
       (->> studs
            (map-indexed #(str %1 ". " (:name %2) " (" (:topic %2) ")"))
            (str/join "\n"))))

(defn feedback-talk [db {token :token :as conf} pres-key-name]
  (let [pres-key (keyword pres-key-name)
        cmd (str pres-key-name "feedback")]
    (talk/def-talk db cmd
      (tr :pres/feedback-talk)
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
            (talk/send-text token id (format (tr :pres/should-set-group-to-send-feedback-help-2) name pres-key-name))
            (talk/stop-talk tx))

          (when (nil? dt)
            (talk/send-text token id (tr :pres/feedback-collecting-disabled))
            (talk/stop-talk tx))

          (when (some #(= id %)
                      (codax/get-at tx [:presentation pres-key group :feedback-from dt]))
            (talk/send-text token id (tr :pres/already-received))
            (talk/stop-talk tx))

          (talk/send-text token id
                          (format (tr :pres/collect-feedback-3) name group dt))
          (talk/send-text token id (feedback-str studs))
          (talk/change-branch tx :select {:rank [] :remain studs :group group :dt dt})))

      :select
      (fn [tx {{id :id} :from text :text} {rank :rank studs :remain group :group dt :dt :as state}]
        (let [n (if (re-matches #"^\d+$" text) (Integer/parseInt text) nil)]
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
  (let [cmd (str pres-key-name (tr :pres/drop) (when drop-all (tr :pres/all)))
        pres-key (keyword pres-key-name)
        name (-> conf (get pres-key) :name)
        help (format (tr :pres/drop-talk-2) name
                     (if drop-all (tr :pres/all) (tr :pres/drop)))]
    (talk/def-talk db cmd help

      :start
      (fn [tx {{id :id} :from text :text}]
        (general/assert-admin tx conf id)
        (let [stud-id (talk/command-num-arg text)]

          (when (nil? stud-id)
            (talk/send-text token id (format (tr :pres/wrong-input-1) cmd))
            (talk/stop-talk tx))

          (let [stud (codax/get-at tx [stud-id])]
            (when-not stud
              (talk/send-text token id (tr :pres/not-found))
              (talk/stop-talk tx))

            (general/send-whoami tx token id stud-id)
            (talk/send-yes-no-kbd token id (format (tr :pres/drop-config-2) name stud-id))

            (talk/change-branch tx :approve {:stud-id stud-id}))))

      :approve
      (fn [tx {{id :id} :from text :text} {stud-id :stud-id}]
        (talk/when-parse-yes-or-no
         tx token id text
         (let [group (codax/get-at tx [stud-id :presentation pres-key :group])
               lessons (codax/get-at tx [:presentation pres-key group])]
           (talk/send-text token id (format (tr :pres/drop-student-1) stud-id))
           (talk/send-text token stud-id (format (tr :pres/drop-state-1) name))
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
              fn (str dt "-" pres-id "-descriptions.md")
              groups (-> conf (get pres-key) :groups keys)
              data (codax/get-at tx [])
              text (->> groups
                        (map #(str "# " % "\n\n" (scheduled-descriptions-dump data pres-key %)))
                        (str/join "\n\n\n"))]
          (spit fn text)
          (talk/send-document token id (io/file fn)))
        tx))))
