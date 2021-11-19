(ns course-bot.core
  (:require [codax.core :as c])
  (:require [course-bot.dialog :as d]
            [course-bot.talk :as b]
            [course-bot.quiz :as q]
            [course-bot.essay :as e]
            [course-bot.csa.general :as g]
            [course-bot.csa.lab1 :as lab1])
  (:require [morse.handlers :as h]
            [morse.api :as t]
            [morse.polling :as p])
  (:require [clojure.string :as str]
            [clojure.pprint :refer [pprint]])
  (:gen-class))

(def db (c/open-database! (or (System/getenv "BOT_DATABASE") "default-codax")))
(def token (System/getenv "BOT_TOKEN"))

(def group-list #{"P33102" "P33111" "P33301" "P33101" "P33312" "P33302" "P33112" "thursday"})
(def admin-chat 70151255)

(defn save-chat-info [id chat]
  (doall (map (fn [[key value]] (c/assoc-at! db [id :chat key] value)) chat)))

;; for drop student

(defn assert-admin [tx token id]
  (when-not (= id admin-chat)
    (t/send-text token id "У вас нет таких прав.")
    (b/stop-talk tx)))

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
                                (g/send-whoami! db token id)
                                (t/send-text token id "Если вы где-то ошиблись - выполните команду /start повторно. Помощь -- /help.")))))

  ;; (h/command "dump" {{id :id} :chat} (t/send-text token id (str "Всё, что мы о вас знаем:\n\n:" (c/get-at! db [id]))))
  (h/command "whoami" {{id :id} :chat} (g/send-whoami! db token id))

  (q/startquiz-talk db token assert-admin)
  (q/stopquiz-talk db token assert-admin)
  (q/quiz-talk db token admin-chat)

  (e/essay-talk db token "essay1")
  (e/assign-essay-talk db token "essay1" assert-admin)
  (e/essay-review-talk db token "essay1")

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
                     (b/send-yes-no-kbd token id "Все верно, могу отправлять преподавателю (текст нельзя будет изменить)?")
                     (:yes-no {{id :id :as chat} :chat}
                              :input-error (b/send-yes-no-kbd token id "Непонял, скажите yes или no (там вроде клавиатура должна быть).")
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
              (b/send-yes-no-kbd token id "Все нормально?"))
            (:yes-no {{id :id :as chat} :chat}
                     :input-error (b/send-yes-no-kbd token id "Непонял, скажите yes или no (там вроде клавиатура должна быть).")
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
                                                               "Попробуйте снова.
                                                         "))
                                  (t/send-text token id (str "Замечания отправлено студенту."
                                                             "\n\n"
                                                             (lab1/status-for-stud db stud)
                                                             "\n\n"
                                                             "Ещё /lab1next")))))))

  (h/command "help" {{id :id} :chat}
             (t/send-text token id (str "start - регистрация\n"
                                        "whoami - какая у меня группа\n"
                                        "lab1 - отправка описания инцидента на согласование\n"
                                        "lab1benext - заявиться докладчиком на ближайшее занятие\n"
                                        "lab1reportqueue - очередь докладов на ближайшие занятия (по группам)\n"
                                        "lab1reportnext - доклады наследующий раз (по группам)\n"
                                        "lab1feedback - оценить доклады с занятия\n"
                                        "dump - что бот знает про меня?\n"))))

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
