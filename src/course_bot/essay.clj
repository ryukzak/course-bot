(ns course-bot.essay
  (:require [course-bot.general :as general]
            [course-bot.internationalization :as i18n :refer [tr]]
            [course-bot.misc :as misc]
            [course-bot.plagiarism :as plagiarism]
            [course-bot.talk :as talk])
  (:require [codax.core :as codax])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(i18n/add-dict
 {:en
  {:essay
   {:submit-:essay-name "Submit '%s'"
    :your-essay-already-uploaded-:essay-name "Your essay '%s' already uploaded."
    :send-essay-text-in-one-message-:essay-name "Submit your essay text '%s' in one message."
    :essay-text-too-short-:min-length "Your essay text is too short, it should be at least %d characters long."
    :plagiarised-warning-:similarity "Your essay didn't pass plagiarism check. Your score: %s. Make it more unique!"
    :plagiarised-report-:similarity-:origin-key-:uploaded-key "Plagiarism case: %s\n\norigin text: %s\nuploaded text: %s"
    :themes " Theme(s):\n\n"
    :text-of-your-essay "The text of your essay\n<<<<<<<<<<<<<<<<<<<<"
    :is-loading-question "Uploading (yes/no)?"
    :thank-you-your-essay-submited "Thank you, the text has been uploaded and will be submitted for review soon."
    :status-:essay-name "'%s' status"
    :total-essays "Total essays: "
    :number-of-reviewers "Number of people who reviewed: "
    :set-of-reviews "There is a set of reviews for: "
    :essays " essays."
    :assignment-error "ERROR: can't find assignment for some reason!"
    :assignment-count "Assignment count: "
    :assignment-examples "Examples: "
    :first-essay-best "The first essay -- best.\n\n"
    :rank-:number "Rank: %d; "
    :preview-reviews-:rank-:essay-number-:review "Rank: %d, essay number in the list: #%d, your review: %s \n(few words from the essay: "
    :the-last-essay-worst "\n\nThe last essay -- worst."
    :write-review-for-:essay-name "write review for '%s'"
    :not-assigned-reviews "You have not been assigned any essays. You probably didn't upload your essay on time or rushed to submit your review."
    :you-already-sent-reviews "You already sent reviews."
    :essays-submitted-for-review-:essay-count "You received: %d essays for your review. Their text will now be sent below by selected messages."
    :essay-number-begin-:number "Essay #%d <<<<<<<<<<<<<<<<<<<<"
    :essay-number-end-:number ">>>>>>>>>>>>>>>>>>>> Essay #%d"
    :essay-send-format "Send essay numbers with feedback in separate messages from best to worse (e.g.: `<essay number> <feedback text>`)"
    :essay-need-feedback-error "Alas, you need to start writing reviews first (if you see this message again, let me know)."
    :essay-number-error "The essay number is inconsistent or out of bounds."
    :essay-feedback-short "Your feedback text is too short."
    :essay-already-rate "You already rate this essay."
    :essays-have-rated "You have rated all the essays. Let's take a look:"
    :correct "Correct?"
    :essay-feedback-saved "Your feedback has been saved and will be available to essay writers."
    :essay-feedback "Feedback: "
    :feedback-on-your-essay "feedback on your essay "
    :number-of-reviews-:count "Review count: %d."
    :plagirism-report-:similarity-:origin-key-:new-key "%s original: %s new: %s"
    :warmup-plagiarism-help-:essay-name "(admin) Recheck and register existed '%s' for plagiarism"
    :warmup-no-plagiarsm "No plagiarism found."
    :warmup-processed-:count "Processed %d essays."
    :assignreviewers-info-:essay-name "(admin) Assign reviewers for '%s'"}}
  :ru
  {:essay
   {:submit-:essay-name "Отправить '%s'"
    :your-essay-already-uploaded-:essay-name "Ваше эссе '%s' уже загружено."
    :send-essay-text-in-one-message-:essay-name "Отправьте текст эссе '%s' одним сообщением."
    :essay-text-too-short-:min-length "Ваше эссе слишком короткое, оно должно быть длиной не менее %d символов."
    :plagiarised-warning-:similarity "Ваше эссе не прошло проверку на плагиат. Ваш балл: %s. Сделайте его более уникальным!"
    :plagiarised-report-:similarity-:origin-key-:uploaded-key "Плагиат: %s\n\nоригинал: %s\n загруженный текст: %s"
    :themes " Тема(-ы):\n\n"
    :text-of-your-essay "Текст вашего эссе\n<<<<<<<<<<<<<<<<<<<<"
    :is-loading-question "Загружаем (да/нет)?"
    :thank-you-your-essay-submited "Спасибо, текст загружен и скоро попадёт на рецензирование."
    :status-:essay-name "'%s' статус"
    :total-essays "Всего эссе: "
    :number-of-reviewers "Количество человек, сделавших ревью: "
    :set-of-reviews "Есть комплект ревью на: "
    :essays " эссе."
    :assignment-error "ОШИБКА: почему-то не удается найти задание!"
    :assignment-count "Количество заданий: "
    :assignment-examples "Примеры: "
    :first-essay-best "Первое эссе -- лучшее.\n\n"
    :rank-:number "Ранг: %d, "
    :preview-reviews-:rank-:essay-number-:review "Ранг: %d, номер эссе в списке: #%d, ваше ревью: %s \n(несколько слов из эссе: "
    :the-last-essay-worst "\n\nПоследнее эссе -- худшее."
    :write-review-for-:essay-name "Написать ревью на '%s'"
    :not-assigned-reviews "Вам не назначено ни одного эссе. Вероятно, вы не загрузили своё эссе вовремя или поспешили с отправкой ревью."
    :you-already-sent-reviews "Вы уже отправили ревью."
    :essays-submitted-for-review-:essay-count "Вам на ревью пришло: %d эссе. Их текст сейчас отправлю ниже отдельными сообщениями."
    :essay-number-begin-:number "Эссе #%d <<<<<<<<<<<<<<<<<<<<"
    :essay-number-end-:number ">>>>>>>>>>>>>>>>>>>> Эссе #%d"
    :essay-send-format "Отправляйте номера эссе с отзывами отдельными сообщениями (пример: `<номер_эссе> <текст_отзыва>`)"
    :essay-need-feedback-error "Увы, но вам надо начать писать отзывы сначала (если вы это сообщение видите в очередной раз -- сообщите)."
    :essay-number-error "Номер эссе несовместим или выходит за допустимые пределы."
    :essay-feedback-short "Ваш текст отзыва слишком короткий."
    :essay-already-rate "Вы уже оценили это эссе."
    :essays-have-rated "Вы оценили все эссе. Давайте посмотрим:"
    :correct "Корректно?"
    :essay-feedback-saved "Ваш отзыв сохранен и будет доступен авторам эссе."
    :essay-feedback "Отзыв: "
    :feedback-on-your-essay "Посмотреть отзывы на ваше эссе "
    :number-of-reviews-:count "Количество отзывов на ваше эссе: %d."
    :plagirism-report-:similarity-:origin-key-:new-key "%s оригинал: %s новое: %s"
    :warmup-plagiarism-help-:essay-name "(admin) Перепроверить и зарегистрировать существующие '%s' на плагиат"
    :warmup-no-plagiarsm "Плагиат не найден."
    :warmup-processed-:count "Обработано %d эссе."
    :assignreviewers-info-:essay-name "(admin) Назначить рецензентов для '%s'"}}})

(defn get-stud-reviews [tx essay-code stud-id]
  (codax/get-at tx [stud-id :essays essay-code :received-review]))

(defn save-student-report [tx essay-code stud-id text]
  (codax/update-at tx [stud-id :essays essay-code :abuse-reports] conj text))

(defn plagiarism-key [essay-code stud-id]
  (str stud-id " - " essay-code))

(defn submit-talk [db
                   {token :token admin-chat-id :admin-chat-id :as conf}
                   essay-code
                   {bad-texts-path :bad-texts-path :as plagiarism-db}]
  (let [cmd (str essay-code "submit")
        topics-msg (-> conf (get (keyword essay-code)) :topic-msg)
        help (format (tr :essay/submit-:essay-name) essay-code)]
    (talk/def-talk db cmd help
      :start
      (fn [tx {{id :id} :from}]
        (let [submitted? (codax/get-at tx [id :essays essay-code :text])]
          (when submitted?
            (talk/send-text token id (format (tr :essay/your-essay-already-uploaded-:essay-name) essay-code))
            (talk/stop-talk tx))
          (talk/send-text token id (str (format (tr :essay/send-essay-text-in-one-message-:essay-name) essay-code)
                                        (when topics-msg (str (tr :essay/themes) topics-msg))))
          (talk/change-branch tx :submit)))

      :submit
      (fn [tx {{id :id} :from text :text}]
        (let [min-length (or (-> conf (get (keyword essay-code)) :min-length)
                             512)]
          (when (< (count text) min-length)
            (talk/send-text token id (format (tr :essay/essay-text-too-short-:min-length) min-length))
            (talk/stop-talk tx)))

        (when-let [origin (plagiarism/find-original plagiarism-db text)]
          (let [similarity (Math/round (:similarity origin))
                origin-key (:key origin)
                key (plagiarism-key essay-code id)]
            (when-not (= origin-key key) ;; allow self plagiarism
              (talk/send-text token id (format (tr :essay/plagiarised-warning-:similarity) similarity))

              (let [bad-key (str (misc/filename-time (misc/today)) " - " key)
                    bad-filename (str bad-texts-path "/" bad-key ".txt")]
                (io/make-parents bad-filename)
                (spit bad-filename text)
                (talk/send-text token admin-chat-id
                                (format (tr :essay/plagiarised-report-:similarity-:origin-key-:uploaded-key) similarity origin-key bad-key)))
              (talk/stop-talk tx))))

        (talk/send-text token id (tr :essay/text-of-your-essay))
        (talk/send-text token id text)
        (talk/send-text token id ">>>>>>>>>>>>>>>>>>>>")
        (talk/send-yes-no-kbd token id (tr :essay/is-loading-question))
        (talk/change-branch tx :approve {:essay-text text}))

      :approve
      (fn [tx {{id :id} :from text :text} {essay-text :essay-text}]
        (case (i18n/normalize-yes-no-text text)
          "yes" (do (plagiarism/register-text! plagiarism-db (plagiarism-key essay-code id) essay-text)
                    (talk/send-text token id (tr :essay/thank-you-your-essay-submited))
                    (-> tx
                        (codax/assoc-at [id :essays essay-code :text] essay-text)
                        talk/stop-talk))
          "no" (talk/send-stop tx token id)
          (talk/clarify-input tx token id (format (tr :talk/clarify-input-tmpl) text)))))))

(defn get-essays [tx essay-code]
  (->> (codax/get-at tx [])
       (filter (fn [[_k v]] (-> v :essays (get essay-code) :text)))))

(defn status-talk [db {token :token} essay-code]
  (talk/def-talk db (str essay-code "status")
    (format (tr :essay/status-:essay-name) essay-code)
    :start
    (fn [tx {{id :id} :from}]
      (let [essays (get-essays tx essay-code)]
        (talk/send-text token id
                        (str
                         (tr :essay/total-essays) (count essays) "\n"
                         (tr :essay/number-of-reviewers)
                         (->> essays
                              (filter #(-> % second :essays (get essay-code) :my-reviews count (> 0)))
                              count) "\n"
                         (tr :essay/set-of-reviews)
                         (->> essays
                              vals
                              (map #(-> % :essays (get essay-code) :received-review))
                              (filter #(= 3 (count %)))
                              count) (tr :essay/essays))))

      (talk/stop-talk tx))))

(defn review-collision [essay-code id info]
  (let [reviewers (-> info :essays (get essay-code) :request-review)]
    (or (some #(= % id) reviewers)
        (not (apply distinct? reviewers)))))

(defn assign-reviews [tx essay-code n]
  (loop [n n
         limit (* n 1000)
         essays (->> (get-essays tx essay-code)
                     (filter (fn [[_k v]]
                               (-> v :essays (get essay-code) :request-review empty?))))]

    (let [tmp (into {} (map (fn [[id info] review-id]
                              [id (update-in info
                                             [:essays essay-code :request-review]
                                             conj review-id)])
                            essays
                            (shuffle (keys essays))))
          bad-items (filter #(apply review-collision essay-code %) tmp)]
      (cond
        (= n 0) essays
        (empty? bad-items) (recur (- n 1) (- limit 1) tmp)
        (and (not-empty bad-items)
             (<= limit 0)) nil
        :else (recur n (- limit 1) essays)))))

(defn assignreviewers-talk [db {token :token :as conf} essay-code]
  (let [cmd (str essay-code "assignreviewers")
        help (format (tr :essay/assignreviewers-info-:essay-name) essay-code)]
    (talk/def-command db cmd help
      (fn [tx {{id :id} :from}]
        (general/assert-admin tx conf id)
        (let [update (assign-reviews tx essay-code 3)]
          (when (nil? update)
            (talk/send-text token id (tr :essay/assignment-error))
            (talk/stop-talk tx))
          (talk/send-text token id
                          (str (tr :essay/assignment-count) (count update) "; "
                               (tr :essay/assignment-examples) (some-> update first second :essays (get essay-code) :request-review)))
          (reduce (fn [tx' [id desc]]
                    (codax/assoc-at tx' [id] desc))
                  tx update))))))

(defn stud-review-assignments [tx id essay-code]
  (let [reviewer-ids (codax/get-at tx [id :essays essay-code :request-review])
        assignments (map #(codax/get-at tx [% :essays essay-code :text]) reviewer-ids)]
    (map vector reviewer-ids assignments)))

(defn preview-reviews [assignments reviews]
  (str (tr :essay/first-essay-best)
       (str/join "\n\n---\n\n"
                 (map #(str (format (tr :essay/preview-reviews-:rank-:essay-number-:review)
                                    (:rank %)
                                    (+ 1 (:index %))
                                    (:feedback %))
                            (let [essay (-> assignments (nth (:index %)) second)]
                              (subs essay 0 (min (count essay) 40))) "...)")
                      (sort-by :rank reviews)))
       (tr :essay/the-last-essay-worst)))

(defn review-talk [db {token :token :as conf} essay-code]
  (let [cmd (str essay-code "review")
        help (format (tr :essay/write-review-for-:essay-name) essay-code)]
    (talk/def-talk db cmd help
      :start
      (fn [tx {{id :id} :from}]
        (let [assignments (stud-review-assignments tx id essay-code)]
          (when (empty? assignments)
            (talk/send-text token id (tr :essay/not-assigned-reviews))
            (talk/stop-talk tx))
          (when-not (nil? (codax/get-at tx [id :essays essay-code :my-reviews]))
            (talk/send-text token id (tr :essay/you-already-sent-reviews))
            (talk/stop-talk tx))
          (talk/send-text token id (format (tr :essay/essays-submitted-for-review-:essay-count) (count assignments)))
          (doall (map (fn [index [_auth-id text]]
                        (talk/send-text token id (format (tr :essay/essay-number-begin-:number) (+ 1 index)))
                        (talk/send-text token id text)
                        (talk/send-text token id (format (tr :essay/essay-number-end-:number) (+ 1 index))))
                      (range)
                      assignments))
          (talk/send-text token id (or (-> conf (get (keyword essay-code)) :review-msg)
                                       (tr :essay/essay-send-format)))
          (talk/change-branch tx :get-feedback {:assignments assignments})))

      :get-feedback
      (fn [tx {{id :id} :from text :text} {assignments :assignments reviews :reviews}]
        (when (and (seq reviews) (empty? (filter :index reviews)))
          (talk/send-text token id (tr :essay/essay-need-feedback-error))
          (talk/stop-talk tx))
        (let [index (try (- (Integer/parseInt (.trim (first (re-find #"^\d(\s|$)" text)))) 1)
                         (catch Exception _ nil))
              rank (+ 1 (count reviews))
              feedback (str/replace text #"^\d*\s*" "")]

          (when (or (nil? index)
                    (< index 0) (>= index (count assignments)))
            (talk/send-text token id (tr :essay/essay-number-error))
            (talk/wait tx))

          (when (< (count text) 40)
            (talk/send-text token id (tr :essay/essay-feedback-short))
            (talk/wait tx))

          (when (some #{index} (map :index reviews))
            (talk/send-text token id (tr :essay/essay-already-rate))
            (talk/wait tx))

          (let [reviews' (cons {:rank rank
                                :index index
                                :essay-author (first (nth assignments index))
                                :review-author id
                                :feedback feedback} reviews)]
            (talk/send-text token id "ok")
            (if (not= (count reviews') (count assignments))
              (talk/change-branch tx :get-feedback {:assignments assignments :reviews reviews'})
              (do (talk/send-text token id (tr :essay/essays-have-rated))
                  (talk/send-text token id (preview-reviews assignments reviews'))
                  (talk/send-yes-no-kbd token id (tr :essay/correct))
                  (talk/change-branch tx :approve {:reviews reviews'}))))))

      :approve
      (fn [tx {{id :id} :from text :text} {reviews :reviews}]
        (case (i18n/normalize-yes-no-text text)
          "yes" (do (talk/send-text token id (tr :essay/essay-feedback-saved))
                    (-> (reduce (fn [tx' review]
                                  (codax/update-at tx' [(:essay-author review) :essays essay-code :received-review] conj review))
                                tx reviews)
                        (codax/assoc-at [id :essays essay-code :my-reviews] reviews)
                        (codax/assoc-at [id :essays essay-code :my-reviews-submitted-at] (misc/str-time (misc/today)))
                        talk/stop-talk))
          "no" (talk/send-stop tx token id)
          (talk/clarify-input tx token id (format (tr :talk/clarify-input-tmpl) text)))))))

(defn my-reviews [tx essay-code id]
  (->> (get-stud-reviews tx essay-code id)
       (map #(str (format (tr :essay/rank-:number) (:rank %))
                  (when-let [fb (:feedback %)] (str (tr :essay/essay-feedback) fb))))))

(defn myfeedback-talk [db {token :token} essay-code]
  (let [cmd (str essay-code "myfeedback")
        help (str (tr :essay/feedback-on-your-essay) essay-code)]
    (talk/def-command db cmd help
      (fn [tx {{id :id} :from}]
        (let [reviews (my-reviews tx essay-code id)]
          (doall (map #(talk/send-text token id %) reviews))
          (talk/send-text token id (format (tr :essay/number-of-reviews-:count) (count reviews))))
        (talk/stop-talk tx)))))

(defn review-score [conf essay-code]
  (let [essay-key (keyword essay-code)]
    (fn [_tx data id]
      (let [n (-> data (get id) :essays (get essay-code) :my-reviews count)
            deadline (-> conf (get essay-key) :review-deadline)
            at (-> data (get id) :essays (get essay-code) :my-reviews-submitted-at)
            score-per-review (cond
                               (or (nil? deadline) (nil? at)) 1
                               (< (misc/read-time deadline) (misc/read-time at)) 0.5
                               :else 1)]
        (-> (* n score-per-review)
            str
            (str/replace #"\." ","))))))

(defn essay-score "hardcoded: rank + 1" [essay-code]
  (fn [_tx data id]
    (let [essay-uploaded? (-> data (get id) :essays (get essay-code) :text nil? not)
          reviews (-> data (get id) :essays (get essay-code) :received-review)
          scores (->> reviews (map :rank))]
      (cond (not (empty? scores))
            (-> (/ (apply + scores) (count scores))
                float
                Math/ceil
                int
                (#(- 4 %)) ; 3 (max score) = 4 - 1; 1 (min score) = 4 - 3
                (+ 1) ; + 1 to get actual score
                )

            essay-uploaded? 1

            :else 0))))

(defn warmup-plagiarism-talk [db {token :token :as conf} essay-code plagiarism-db]
  (let [cmd (str essay-code "warmupplagiarism")
        help (format (tr :essay/warmup-plagiarism-help-:essay-name) essay-code)]
    (talk/def-command db cmd help
      (fn [tx {{id :id} :from}]
        (general/assert-admin tx conf id)
        (let [reports (->> (get-essays tx essay-code)
                           (map (fn [[stud-id v]] [stud-id (-> v :essays (get essay-code) :text)]))
                           (map (fn [[stud-id text]]
                                  (let [key (plagiarism-key essay-code stud-id)
                                        origin (plagiarism/find-original plagiarism-db text key)]
                                    (plagiarism/register-text! plagiarism-db key text)
                                    (cond
                                      (nil? origin) nil
                                      (= key (:key origin)) nil
                                      :else (format (tr :essay/plagirism-report-:similarity-:origin-key-:new-key)
                                                    (misc/round-2 (:similarity origin))
                                                    (:key origin)
                                                    key))))))
              bad-reports (filter some? reports)]
          (if (empty? bad-reports)
            (talk/send-text token id (tr :essay/warmup-no-plagiarism))
            (doall (map #(talk/send-text token id %) bad-reports)))
          (talk/send-text token id (format (tr :essay/warmup-processed-:count) (count reports)))
          (talk/stop-talk tx))))))

(i18n/add-dict
 {:en {:essay {:reportabuse-cmd-help-:essay-name "Report about abuse in essay or review in '%s'"

               :no-assignments "No assignments for this essay, how you can report abuse?"
               :describe-essay-or-review-problem "Describe whats wrong with essay or review on your essay in one text message (with quote of problem place)?"
               :report-approve? "Your report text + reviewed essays and feedbacks will be send to the teacher. Are you sure?"

               :report-received-review-author "The follwing student submit abuse report:"
               :report-received-review-text "Report text:"
               :report-received-feedback "Feedback & author:"
               :report-received-essay "Essay & author:"

               :report-sent "Your report was sent to the teacher. Thank you!"}}

  :ru {:essay {:reportabuse-cmd-help-:essay-name "Пожаловаться на нарушение в эссе или ревью в '%s'"

               :no-assignments "Нет назначений для этого эссе, как вы можете сообщить о нарушении?"
               :describe-essay-or-review-problem "Опишите, что не так с эссе или ревью на ваше эссе в одном текстовом сообщении (с цитатой места проблемы)?"
               :report-approve? "Ваш текст + проверенные эссе и отзывы будут отправлены учителю. Вы уверены?"

               :report-received-review-author "Следующий студент отправил жалобу:"
               :report-received-review-text "Текст жалобы:"
               :report-received-feedback "Отзыв и автор:"
               :report-received-essay "Эссе и автор:"

               :report-sent "Ваша жалоба была отправлена учителю. Спасибо!"}}})

(defn reportabuse-talk [db {token :token admin-chat-id :admin-chat-id} essay-code]
  (let [cmd (str essay-code "reportabuse")
        help (format (tr :essay/reportabuse-cmd-help-:essay-name) essay-code)]
    (talk/def-talk db cmd help
      :start
      (fn [tx {{id :id} :from}]
        (let [assignments (stud-review-assignments tx id essay-code)]
          (when (empty? assignments)
            (talk/send-text token id (tr :essay/no-assignments))
            (talk/stop-talk tx))
          (talk/send-text token id (tr :essay/describe-essay-or-review-problem))
          (talk/change-branch tx :get-report)))

      :get-report
      (fn [tx {{id :id} :from text :text}]
        (talk/send-yes-no-kbd token id (tr :essay/report-approve?))
        (talk/change-branch tx :approve {:report text}))

      :approve
      (fn [tx {{id :id} :from text :text} {report :report}]
        (case (i18n/normalize-yes-no-text text)
          "yes" (let [assignments (stud-review-assignments tx id essay-code)
                      reviews (get-stud-reviews tx essay-code id)]
                  (talk/send-text token admin-chat-id (tr :essay/report-received-review-author))
                  (general/send-whoami tx token admin-chat-id id)
                  (talk/send-text token admin-chat-id (tr :essay/report-received-review-text))
                  (talk/send-text token admin-chat-id report)
                  (->> assignments
                       (map (fn [[stud-id text]]
                              (talk/send-text token admin-chat-id (tr :essay/report-received-essay))
                              (general/send-whoami tx token admin-chat-id stud-id)
                              (talk/send-text token admin-chat-id text)))
                       doall)
                  (->> reviews
                       (map (fn [{:keys [review-author feedback]}]
                              (talk/send-text token admin-chat-id (tr :essay/report-received-feedback))

                              (general/send-whoami tx token admin-chat-id review-author)
                              (talk/send-text token admin-chat-id feedback)))
                       doall)
                  (talk/send-text token id (tr :essay/report-sent))
                  (-> tx
                      (save-student-report essay-code id report)
                      talk/stop-talk))
          "no" (talk/send-stop tx token id)
          (talk/clarify-input tx token id (format (tr :talk/clarify-input-tmpl) text)))))))
