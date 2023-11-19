(ns course-bot.general
  (:require [clojure.string :as str])
  (:require [codax.core :as codax]
            [taoensso.tempura :as tempura])
  (:require [course-bot.talk :as talk]))

(def *tr-options-dict (atom {}))
(defn add-dict [dict]
  (swap! *tr-options-dict (partial merge-with merge) dict))

(def *tr-locales (atom [:en]))
(defn set-locales [langs]
  (compare-and-set! *tr-locales @*tr-locales langs)
  @*tr-locales)

(defn tr [& in]
  (let [resource (conj (apply vector in)
                       (str "missing resource: " (str/join " " in)))]
    (tempura/tr {:dict @*tr-options-dict}
                @*tr-locales
                resource)))

(add-dict
 {:en
  {:general
   {:who-am-i-3 "Name: %s; Group: %s; Telegram ID: %s"
    :need-admin "That action requires admin rights."
    :who-am-i-info "Send me my registration info"
    :group " group:\n"
    :list-groups-info "Send me group list know by the bot"
    :register-student-info "register student"
    :already-registered "You are already registered. To change your information, contact the teacher and send /whoami"
    :start "Hi, I'm a bot for your course. I will help you with your work. What is your name (like in the registry)?"
    :what-is-your-group "What is your group ("
    :group-not-found "I don't know this group. Please, repeat it ("
    :hi-user-1 "Hi %s!"
    :send-help-for-help "Send /help for help."
    :rename-me-info "Rename me"
    :unregistered-rename-warn "You should be registered to rename yourself!"
    :what-is-your-new-name "What is your new name?"
    :renamed "Renamed:"
    :telegram-id-not-found "User with specific telegram id not found."
    :restart-this-student-question "Restart this student?"
    :restart-wrong-input "Wrong input. Expect: /restart 12345"
    :restarted-and-notified "Restarted and notified: "
    :use-start-once-more "You can use /start once more."
    :not-restarted "Not restarted."
    :yes-no-question "Please yes or no?"
    :edited-message-not-allowed "Edited message not allowed."}}
  :ru
  {:general
   {:who-am-i-3 "Имя: %s; Группа: %s; Telegram ID: %s"
    :need-admin "Это действие требует прав администратора."
    :who-am-i-info "Отправь мне мою информацию о регистрации"
    :group " группа:\n"
    :list-groups-info "Отправь мне список групп известных боту"
    :register-student-info "Зарегистрировать студента"
    :already-registered "Вы уже зарегистрированы. Чтобы изменить свою информацию, свяжитесь с учителем и отправьте /whoami"
    :start "Привет, я бот вашего курса. Я помогу тебе с твоей работой. Как тебя зовут (как в реестре)?"
    :what-is-your-group "Какая у тебя группа ("
    :group-not-found "Я не знаю эту группу. Пожалуйста, попробуй это ("
    :hi-user-1 "Привет %s!"
    :send-help-for-help "Отправь /help для помощь."
    :rename-me-info "Переименовать меня"
    :unregistered-rename-warn "Вы должны быть зарегистрированы, чтобы переименовать себя!"
    :what-is-your-new-name "Какое твое новое имя?"
    :renamed "Переименовано:"
    :telegram-id-not-found "Пользователь с указанным Telegram ID не найден."
    :restart-this-student-question "Перезапустить этого студента?"
    :restart-wrong-input "Неправильный ввод. Ожидается: /restart 12345"
    :restarted-and-notified "Перезапущено и уведомлено: "
    :use-start-once-more "Вы можете использовать /start еще раз."
    :not-restarted "Не перезапущено."
    :yes-no-question "Пожалуйста, yes или no?"
    :edited-message-not-allowed "Редактирование сообщений не поддерживается."}}})

(defn assert-admin
  ([tx {token :token admin-chat-id :admin-chat-id} id]
   (when-not (= id admin-chat-id)
     (talk/send-text token id (tr :general/need-admin))
     (talk/stop-talk tx))))

(defn send-whoami
  ([tx token id] (send-whoami tx token id id))
  ([tx token id about]
   (let [{name :name group :group} (codax/get-at tx [about])]
     (talk/send-text token id (format (tr :general/who-am-i-3) name group about)))))

(defn whoami-talk [db {token :token}]
  (talk/def-command db "whoami" (tr :general/who-am-i-info)
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
                      msg (str group (tr :general/group) (str/join "\n" studs))]
                  (talk/send-text token id msg))))))))

(defn listgroups-talk [db {token :token :as conf}]
  (talk/def-command db "listgroups" (tr :general/list-groups-info)
    (fn [tx {{id :id} :from}]
      (assert-admin tx conf id)
      (send-list-groups tx token id) tx)))

(defn start-talk [db {token :token groups-raw :groups allow-restart :allow-restart}]
  (let [groups (-> groups-raw keys set)]
    (talk/def-talk db "start" (tr :general/register-student-info)
      :start
      (fn [tx {{id :id} :from}]
        (let [info (codax/get-at tx [id])]
          (when (and (some? (:name info))
                     (-> info :allow-restart not)
                     (not allow-restart))
            (talk/send-text token id (tr :general/already-registered))
            (talk/stop-talk tx))
          (talk/send-text token id (str (tr :general/start)))
          (talk/change-branch tx :get-name)))

      :get-name
      (fn [tx {{id :id} :from text :text}]
        (talk/send-text token id (str (tr :general/what-is-your-group) (str/join ", " (sort groups)) ")?"))
        (talk/change-branch tx :get-group {:name text}))

      :get-group
      (fn [tx {{id :id :as chat} :from text :text} {name :name}]
        (when-not (contains? groups text)
          (talk/send-text token id (str (tr :general/group-not-found) (str/join ", " (sort groups)) "):"))
          (talk/repeat-branch tx))
        (let [tx (-> tx
                     (codax/assoc-at [id :chat] chat)
                     (codax/assoc-at [id :name] name)
                     (codax/assoc-at [id :group] text)
                     (codax/assoc-at [id :reg-date] (str (new java.util.Date)))
                     (codax/assoc-at [id :allow-restart] false))]
          (talk/send-text token id (format (tr :general/hi-user-1) name))
          (send-whoami tx token id)
          (talk/send-text token id (tr :general/send-help-for-help))
          (talk/stop-talk tx))))))

(defn renameme-talk [db {token :token}]
  (talk/def-talk db "renameme" (tr :general/rename-me-info)
    :start
    (fn [tx {{id :id} :from}]
      (let [info (codax/get-at tx [id])]
        (when (nil? (:name info))
          (talk/send-text token id (tr :general/unregistered-rename-warn))
          (talk/stop-talk tx))
        (talk/send-text token id (str (tr :general/what-is-your-new-name)))
        (talk/change-branch tx :get-name)))

    :get-name
    (fn [tx {{id :id} :from text :text}]
      (let [tx (-> tx
                   (codax/assoc-at [id :name] text)
                   (codax/assoc-at [id :rename-date] (str (new java.util.Date))))]
        (talk/send-text token id (tr :general/renamed))
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
              (talk/send-text token id (tr :general/telegram-id-not-found))
              (talk/stop-talk tx))
            (send-whoami tx token id stud-id)
            (talk/send-yes-no-kbd token id (tr :general/restart-this-student-question))
            (-> tx
                (talk/change-branch :approve {:restart-stud stud-id})))
          (do
            (talk/send-text token id (tr :general/restart-wrong-input))
            (talk/stop-talk tx)))))

    :approve
    (fn [tx {{id :id} :from text :text} {stud-id :restart-stud}]
      (case (str/lower-case text)
        "yes" (do (talk/send-text token id (str (tr :general/restarted-and-notified) stud-id))
                  (talk/send-text token stud-id (str (tr :general/use-start-once-more)))
                  (-> tx
                      (codax/assoc-at [stud-id :allow-restart] true)
                      (talk/stop-talk)))
        "no" (do (talk/send-text token id (tr :general/not-restarted))
                 (talk/stop-talk tx))
        (do (talk/send-text token id (tr :general/yes-no-question))
            (talk/repeat-branch tx))))))

(defn warning-on-edited-message [{token :token}]
  (fn [{{{id :id} :from :as edited-message} :edited_message}]
    (when (some? edited-message)
      (talk/send-text token id (tr :general/edited-message-not-allowed))
      true)))

