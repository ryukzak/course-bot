(ns course-bot.core
  (:require [codax.core :as c]
            [clojure.data.csv :as csv])

  (:require [clojure.java.io :as io])
  (:require [course-bot.dialog :as d]
            [course-bot.talk :as talk]
            [course-bot.quiz :as quiz]
            [course-bot.essay :as essay]
            [course-bot.general :as general]
            [course-bot.csa.lab1 :as lab1])
  (:require [morse.handlers :as h]
            [morse.api :as t]
            [morse.polling :as p])
  (:require [clojure.string :as str]
            [clojure.pprint :refer [pprint]])
  (:gen-class))

(def db (c/open-database! (or (System/getenv "BOT_DATABASE") "course-data/csa")))
(def token general/chat-token)
(def group-list general/group-list)
(def admin-chat general/admin-chat)
(def assert-admin general/assert-admin)

(def help-msg
  "start - регистрация
help - посмотреть информацию о существующих командах
whoami - какая у меня группа
listgroups - списки групп по мнению ботов
quiz - начать лекционный тест
lab1 - отправка описания инцидента на согласование
lab1benext - заявиться докладчиком на ближайшее занятие
lab1reportqueue - очередь докладов на ближайшие занятия (по группам)
lab1reportnext - доклады на следующий раз (по группам)
lab1feedback - оценить доклады с занятия
essay1 - загрузить первое эссе
essay1review - сделать ревью на первое эссе
essay1status - посмотреть сколько ревью собрано на первое эссе
essay1results - результаты рассмотрения моего первого эссе
essay2 - загрузить второе эссе
essay2review - сделать ревью на второе эссе
essay2status - посмотреть сколько ревью собрано на второе эссе
essay2results - результаты рассмотрения моего второго эссе
essay3 - загрузить третье эссе
essay3review - сделать ревью на третье эссе
essay3status - посмотреть сколько ревью собрано на третье эссе
essay3results - результаты рассмотрения моего третьего эссе
")

(defn save-chat-info [id chat]
  (doall (map (fn [[key value]] (c/assoc-at! db [id :chat key] value)) chat)))

;; for drop student

(defn quiz-result [db id name]
  (let [ans (c/get-at! db [:quiz-results name id])
        quiz (get quiz/all-quiz name)
        [bool correct max] (quiz/stud-results-inner ans id quiz)]
    (Math/round (* 100.0 (/ correct max)))))

(defn essay-result [db id name]
  (c/with-read-transaction [db tx]
    (let [scores (->> (essay/my-reviews tx name id)
                      (map #(subs % 24 25))
                      (map #(Integer/parseInt %))
                      (map #(- 6 %)))]
      (if (empty? scores) "-"
          (-> (/ (apply + scores) (count scores))
              float
              Math/round)))))

(defn essay-review [db id name]
  (boolean (seq (c/get-at! db [id :essays name :my-reviews]))))

(defn send-report [db token id]
  (let [tests [:t-1-2 :t-3-4 :t-5-6 :t-7-8 :t-9-10 :t-11-12 :t-13-14-15]
        rows (->> (c/get-at! db [])
                  (filter #(-> % second :name))
                  (filter #(-> % (not= "yes")))
                  (map (fn [[id e]]
                         {:name (-> e :name)
                          :group (-> e :group)
                          :t-1-2 (quiz-result db id "Лекция-1-2")
                          :t-3-4 (quiz-result db id "Лекция-3-4")
                          :t-5-6 (quiz-result db id "Лекции 5-6. Раздел 'Hardware и Software'")
                          :t-7-8 (quiz-result db id "Лекция-7-8")
                          :t-9-10 (quiz-result db id "Лекция-9-10. Системы команд. Процессор фон Неймана. Стековый процессор")
                          :t-11-12 (quiz-result db id "Архитектура компьютера - Лекции 11-12, разделы: Память,иерархияпамяти; Устройство памяти с произвольным доступом; Кеширование")
                          :t-13-14-15 (quiz-result db id "Архитектура компьютера - Лекции 13-14-15, разделы: Ввод-вывод, Параллелизм")
                          :e-1-result (essay-result db id "essay1")
                          :e-1-review (essay-review db id "essay1")
                          :e-2-result (essay-result db id "essay2")
                          :e-2-review (essay-review db id "essay2")
                          :e-3-result (essay-result db id "essay3")
                          :e-3-review (essay-review db id "essay3")
                          :id (-> e :chat :id)}))
                  (map (fn [row] (assoc row :test-summary
                                        (->> tests
                                             (map #(% row))
                                             (map #(if (>= % 50) 1 0))
                                             (apply +)
                                             (#(-> (/ % (count tests)) float (* 100) Math/round))))))
                  (map (fn [row] (assoc row :test-pass
                                        (if (>= (:test-summary row) 50) 1 0))))

                  (map (fn [row] (assoc row :essay-review
                                        (->> [:e-1-review :e-2-review :e-3-review]
                                             (map #(% row))
                                             (map #(if % 1 0))
                                             (apply +))))))

        columns [:group :name
                 :test-summary :test-pass
                 :e-1-result  :e-2-result  :e-3-result
                 :essay-review
                 ;; :id
                 :e-1-review :e-2-review :e-3-review
                 :t-1-2 :t-3-4 :t-5-6 :t-7-8 :t-9-10 :t-11-12 :t-13-14-15]
        data (cons columns
                   (map (fn [row] (map #(% row) columns)) rows))]

    (with-open [writer (io/writer "out-file.csv")]
      (csv/write-csv writer data))
    (t/send-document token id (io/file "out-file.csv"))))

(declare bot-api id chat text)
(h/defhandler bot-api
  (d/dialog "start" db {{id :id :as chat} :chat}
            :guard (let [info (c/get-at! db [id])]
                     (cond
                       (nil? info) nil
                       (:allow-restart info) nil
                       :else (t/send-text token id "Что бы изменить информацию о вас -- сообщите об этом преподавателю.")))
            (save-chat-info id chat)
            (t/send-text token id (str "Привет, я бот курса \"Архитектура компьютера\". "
                                       "Через меня будут организовано выполнение лабораторных работ."
                                       "\n\n"
                                       "Представьтесь пожалуйста, мне нужно знать как вносить вас в ведомости (ФИО):"))
            (c/assoc-at! db [id :reg-date] (str (new java.util.Date)))
            (:listen {{id :id} :from text :text}
                     (c/assoc-at! db [id :name] text)
                     (t/send-text token id "Из какой вы группы?")
                     (:listen {{id :id} :from text :text}
                              :guard (when-not (contains? group-list text)
                                       (t/send-text token id (str "Увы, но я не знаю такой группы. Мне сказали что должна быть одна из: "
                                                                  (str/join " " group-list))))
                              ;; TODO: проверка, менял ли студент группу.
                              (c/assoc-at! db [id :group] text)
                              (let [{name :name group :group} (c/get-at! db [id])]
                                (declare tx)
                                (c/with-read-transaction [db tx]
                                  (general/send-whoami tx token id))
                                (t/send-text token id "Если вы где-то ошиблись - выполните команду /start повторно. Помощь -- /help.")))))

  ;; (h/command "dump" {{id :id} :chat} (t/send-text token id (str "Всё, что мы о вас знаем:\n\n:" (c/get-at! db [id]))))

  (general/whoami-talk db token)
  (general/listgroups-talk db token)

  (h/command "grouplists" {{id :id} :chat}
             (c/with-read-transaction [db tx]
               (general/send-group-lists tx token id)))

  (quiz/startquiz-talk db token assert-admin)
  (quiz/stopquiz-talk db token assert-admin)
  (quiz/quiz-talk db token admin-chat)

  (essay/essay-talk db token "essay1")
  (essay/assign-essay-talk db token "essay1" assert-admin)
  (essay/essay-review-talk db token "essay1")
  (essay/essay-status-talk db token "essay1")
  (essay/essay-results-talk db token "essay1")
  (essay/essays-without-review-talk db token "essay1" assert-admin)

  (essay/essay-talk db token "essay2")
  (essay/assign-essay-talk db token "essay2" assert-admin)
  (essay/essay-review-talk db token "essay2")
  (essay/essay-status-talk db token "essay2")
  (essay/essay-results-talk db token "essay2")
  (essay/essays-without-review-talk db token "essay2" assert-admin)

  (essay/essay-talk db token "essay3")
  (essay/assign-essay-talk db token "essay3" assert-admin)
  (essay/essay-review-talk db token "essay3")
  (essay/essay-status-talk db token "essay3")
  (essay/essay-results-talk db token "essay3")
  (essay/essays-without-review-talk db token "essay3" assert-admin)

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
                     (talk/send-yes-no-kbd token id "Все верно, могу отправлять преподавателю (текст нельзя будет изменить)?")
                     (:yes-no {{id :id :as chat} :chat}
                              :input-error (talk/send-yes-no-kbd token id "Непонял, скажите yes или no (там вроде клавиатура должна быть).")
                              (do (t/send-text token id "Отлично, передам все преподавателю.")
                                  (c/assoc-at! db [id :lab1 :on-review?] true))
                              (t/send-text token id "Нет проблем, выполните команду /lab1 повторно."))))

  (d/dialog "lab1benext" db {{id :id} :from text :text}
            :guard (let [lab1 (c/get-at! db [id :lab1])]
                     (cond
                       (:in-queue? lab1)
                       (t/send-text token id "Вы уже в очереди.")
                       (not (:approved? lab1))
                       (t/send-text token id "Увы, но вам необходимо сперва согласовать свою тему доклада на первую лабораторную работу.")
                       :else nil))
            (let [group-id (c/get-at! db [id :group])
                  q (c/get-at! db [:schedule :lab1 group-id :queue])
                  q-text #(->> %
                               (map (fn [id] (lab1/status-for-stud db id)))
                               (str/join "\n"))
                  ps (str "Будьте внимательны, только первые три пункта будут на ближайшем занятии. "
                          "\n\n"
                          "Если по каким-то причинам вам нужно изменить план, тогда вам надо: "
                          "1) согласовать это с остальными ребятами в очереди (найти замену, поменяться местами и т.п.); "
                          "2) сообщить об этом преподавателю, что бы внести правки в ручном режиме.")]
              (if (some #(= id %) q)
                (t/send-text token id (str "Вы уже в очереди: " "\n\n" (q-text q) "\n\n" ps))
                (let [q (c/assoc-at! db [:schedule :lab1 group-id :queue] (concat q (list id)))]
                  (c/assoc-at! db [id :lab1 :in-queue?] true)
                  (t/send-text token admin-chat (str "Заявка на доклад в группу (группа четверга -- отдельная группа!): " group-id))
                  (t/send-text token id (str "Ваш доклад добавлен в очередь на следующее занятие. Сейчас она выглядит так:"
                                             "\n\n" (q-text q)
                                             "\n\n" ps))))))

  (d/dialog "lab1reportqueue" db {{id :id} :from text :text}
            (doall
             (->> (c/get-at! db [:schedule :lab1])
                  (map (fn [[group desc]] (lab1/send-schedule-list db token id group (:queue desc)))))))

  (d/dialog "lab1reportnext" db {{id :id} :from text :text}
            (doall
             (->> (c/get-at! db [:schedule :lab1])
                  (filter (fn [[_group desc]] (some-> desc :fixed count (> 0))))
                  (map (fn [[group desc]]
                         (when-let [fixed (:fixed desc)]
                           (lab1/send-schedule-list db token id group fixed))))))
            (t/send-text token id "Всё что было я прислал."))

  (d/dialog "lab1feedback" db {{id :id :as chat} :chat}
            :guard (let [group (c/get-at! db [id :group])
                         fixed (c/get-at! db [:schedule :lab1 group :fixed])]
                     (if fixed
                       nil
                       (t/send-text token id "Голосование либо не запущено, либо уже завершено.")))
            (let [group (c/get-at! db [id :group])
                  fixed (c/get-at! db [:schedule :lab1 group :fixed])]
              (lab1/send-schedule-list db token id group fixed))
            (t/send-text token id (str "По идее, вы только что были на лабораторном занятии и ознакомились с тремя докладами (если список "
                                       "не корректный -- сообщите об этом преподавателю)."
                                       "\n\n"
                                       "Пожалуйста, отсортируйте доклады от лучшего к худшему на ваш взгляд. Для этого "
                                       "напишите мне строку вида \"123\", если с каждым докладом становилось только хуже. "
                                       "Если лучшим докладом был второй, а худшим - первый, то напишите 231."
                                       "\n\n"
                                       "Я буду вынужден сохранить информацию о том, кто как головал, что бы не было дублей, "
                                       "но публичной будет только сводная характеристика (либо кто-то сделает доклад об этом косяке)."
                                       "\n\n"
                                       "Если вы считаете что все доклады были ужасны либо совершенно прекрасны -- напишите словами, постараюсь учесть в ручном режиме."
                                       "\n\n"
                                       "Ваша оценка:"))
            (:listen {{id :id :as chat} :chat text :text}
                     (let [group (c/get-at! db [id :group])
                           feedback (c/get-at! db [:schedule :lab1 group :feedback])]
                       (c/assoc-at! db [:schedule :lab1 group :feedback]
                                    (cons [id (str (new java.util.Date)) text] feedback)))
                     (t/send-text token id "Записал, если что-то напутали -- загрузите еще раз.")))

  (lab1/dropstudent-talk db token assert-admin admin-chat)

  (h/command "magic" {{id :id} :chat}
             (when (= id admin-chat)
               ;(pprint (c/get-at! db [889101382]))
               ;(pprint (c/get-at! db [admin-chat :admin]))
               ;;(lab1/fix db "P33102" 2)
               ;;(lab1/fix db "P33301" 3)
               ;;(lab1/fix db "P33312" 2)
               ;;(lab1/fix db "P33112" 2)

               ;;(lab1/pass db "P33102")
               ;;(lab1/pass db "P33301")
               ;;(lab1/pass db "P33312")
               ;;(lab1/pass db "P33112")

               ;;(lab1/fix db "P33111" 2)
               ;;(lab1/fix db "P33101" 3)
               ;;(lab1/fix db "P33302" 3)

               ;;(lab1/pass db "P33111")
               ;;(lab1/pass db "P33101")
               ;;(lab1/pass db "P33302")

               ;;(lab1/fix db "thursday" 3)

               ;;(lab1/pass db "thursday")

               ;(c/assoc-at! db [249575093 :allow-restart] true)
               ;(c/assoc-at! db [671848510 :group] "P33301")
               ;(pprint (c/get-at! db [249575093]))
               (t/send-text token id "magic happen...")))

  (h/command "report" {{id :id} :chat}
             (when (= id admin-chat)
               (send-report db token id)
               (t/send-text token id "report sended...")))

  (d/dialog "lab1status" db {{id :id} :from text :text}
            :guard (if (= id admin-chat) nil :break)
            (lab1/send-status db token id))

  (d/dialog "lab1next" db {{id :id} :from text :text}
            :guard (if (= id admin-chat)
                     (if (nil? (lab1/get-next-for-review! db))
                       (t/send-text token id "Все просмотрено.")
                       nil)
                     :break)
            (let [[stud desc] (if true (lab1/get-next-for-review! db) [admin-chat (c/get-at! db admin-chat)])]
              (lab1/send-status db token id (:group desc))
              (t/send-text token id (str "Было пирслано следующее на согласование (группа " (:group desc) "): "
                                         "\n\n"
                                         (lab1/status-str id desc)
                                         "\n\n"
                                         "Тема: " (-> desc :lab1 :description str/split-lines first)))
              (t/send-text token id (-> desc :lab1 :description))
              (c/assoc-at! db [admin-chat :admin :lab1 :on-approve] stud)
              (talk/send-yes-no-kbd token id "Все нормально?"))
            (:yes-no {{id :id :as chat} :chat}
                     :input-error (talk/send-yes-no-kbd token id "Непонял, скажите yes или no (там вроде клавиатура должна быть).")
                     (let [stud (c/get-at! db [admin-chat :admin :lab1 :on-approve])]
                       (c/assoc-at! db [admin-chat :admin :lab1 :on-approve] nil)
                       (c/assoc-at! db [stud :lab1 :approved?] true)
                       (c/assoc-at! db [stud :lab1 :on-review?] false)

                       (t/send-text token stud "Ваше описание инцидента для лабораторной работы №1 одобрили, начинайте готовить доклад.")
                       (t/send-text token id (str "Отправили студенту одобрение."
                                                  "\n\n"
                                                  (lab1/status-for-stud db stud)
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
                                                               "Попробуйте снова."))
                                  (t/send-text token id (str "Замечания отправлено студенту."
                                                             "\n\n"
                                                             (lab1/status-for-stud db stud)
                                                             "\n\n"
                                                             "Ещё /lab1next")))))))

  (h/command "help" {{id :id} :chat}
             (t/send-text token id help-msg))

  ;; (h/message {{id :id} :chat :as message}
  ;;     (println "Intercepted message: " message)
  ;;     (t/send-text token id "I don't do a whole lot ... yet."))
  )

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Bot activated, my Lord!")
  (loop [channel (p/start token bot-api)]
    (Thread/sleep 1000)
    ;; (print ".")(flush)
    (when-not (.closed? channel)
      (recur channel)))
  (println "Bot is dead, my Lord!"))
