(ns course-bot.essay
  (:require [course-bot.talk :as talk]
            [course-bot.general :as general :refer [tr]]
            [course-bot.misc :as misc])
  (:require [codax.core :as codax])
  (:require [clojure.string :as str]))

(general/add-dict
 {:en
  {:essay
   {:submit "Submit "
    :your-essay-already-uploaded-1 "Your essay '%s' already uploaded."
    :send-essay-text-in-one-message-1 "Submit your essay text '%s' in one message."
    :themes " Theme(s):\n\n"
    :text-of-your-essay "The text of your essay\n<<<<<<<<<<<<<<<<<<<<"
    :is-loading-question "Uploading (yes/no)?"
    :thank-you-your-essay-submited "Thank you, the text has been uploaded and will be submitted for review soon."
    :status " status"
    :total-essays "Total essays: "
    :number-of-reviewers "Number of people who reviewed: "
    :set-of-reviews "There is a set of reviews for: "
    :essays " essays."
    :assignment-error "ERROR: can't find assignment for some reason!"
    :assignment-count "Assignment count: "
    :assignment-examples "Examples: "
    :first-essay-best "The first essay -- best.\n\n"
    :rank-1 "Rank: %d; "
    :preview-reviews-3 "Rank: %d, essay number in the list: #%d, your review: %s \n(few words from the essay: "
    :the-last-essay-worst "\n\nThe last essay -- worst."
    :write-review-for "write review for "
    :not-assigned-reviews "You have not been assigned any essays. You probably didn't upload your essay on time or rushed to submit your review."
    :you-already-sent-reviews "You already sent reviews."
    :essays-submitted-for-review-1 "You received: %d essays for your review. Their text will now be sent below by selected messages."
    :essay-number-begin-1 "Essay #%d <<<<<<<<<<<<<<<<<<<<"
    :essay-number-end-1 ">>>>>>>>>>>>>>>>>>>> Essay #%d"
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
    :number-of-reviews-1 "You received %d reviews."}}
  :ru
  {:essay
   {:submit "Отправить "
    :your-essay-already-uploaded-1 "Ваше эссе '%s' уже загружено."
    :send-essay-text-in-one-message-1 "Отправьте текст эссе '%s' одним сообщением."
    :themes " Тема(-ы):\n\n"
    :text-of-your-essay "Текст вашего эссе\n<<<<<<<<<<<<<<<<<<<<"
    :is-loading-question "Загружаем (yes/no)?"
    :thank-you-your-essay-submited "Спасибо, текст загружен и скоро попадёт на рецензирование."
    :status " статус"
    :total-essays "Всего эссе: "
    :number-of-reviewers "Человек сделало ревью: "
    :set-of-reviews "Есть комплект ревью на: "
    :essays " эссе."
    :assignment-error "ОШИБКА: почему-то не удается найти задание!"
    :assignment-count "Количество заданий: "
    :assignment-examples "Примеры: "
    :first-essay-best "Первое эссе -- лучшее.\n\n"
    :rank-1 "Ранг: %d, "
    :preview-reviews "Ранг: %d, номер эссе в списке: #%d, ваше ревью: %s \n(несколько слов из эссе: "
    :the-last-essay-worst "\n\nПоследнее эссе -- худшее."
    :write-review-for "напишите ревью для "
    :not-assigned-reviews "Вам не назначено ни одно эссе. Вероятно, вы не загрузили своё эссе вовремя или поспешили с отправкой ревью."
    :you-already-sent-reviews "Вы уже отправили ревью."
    :essays-submitted-for-review-1 "Вам на ревью пришло: %d эссе. Их текст сейчас отправлю ниже отдельными сообщениями."
    :essay-number-begin-1 "Эссе #%d <<<<<<<<<<<<<<<<<<<<"
    :essay-number-end-1 ">>>>>>>>>>>>>>>>>>>> Эссе #%d"
    :essay-send-format "Отправляйте номера эссе с отзывами отдельными сообщениями (пример: `<номер_эссе> <текст_отзыва>`)"
    :essay-need-feedback-error "Увы, но вам надо начать писать отзывы сначала (если вы это сообщение видите в очередной раз -- сообщите)."
    :essay-number-error "Номер эссе несовместим или выходит за допустимые пределы."
    :essay-feedback-short "Ваш текст отзыва слишком короткий"
    :essay-already-rate "Вы уже оценили это эссе."
    :essays-have-rated "Вы оценили все эссе. Давайте посмотрим:"
    :correct "Корректно?"
    :essay-feedback-saved "Ваш отзыв сохранен и будет доступен авторам эссе."
    :essay-feedback "Отзыв: "
    :feedback-on-your-essay "отзыв на ваше эссе "
    :number-of-reviews-1 "Вы получили %d отзывов."}}})

(defn submit-talk [db {token :token :as conf} essay-code]
  (let [cmd (str essay-code "submit")
        topics-msg (-> conf (get (keyword essay-code)) :topic-msg)
        help (str (tr :essay/submit) essay-code)]
    (talk/def-talk db cmd help
      :start
      (fn [tx {{id :id} :from}]
        (let [submitted? (codax/get-at tx [id :essays essay-code :text])]
          (when submitted?
            (talk/send-text token id (str (format (tr :essay/your-essay-already-uploaded-1) essay-code)))
            (talk/stop-talk tx))
          (talk/send-text token id (str (format (tr :essay/send-essay-text-in-one-message-1) essay-code)
                                        (when topics-msg (str (tr :essay/themes) topics-msg))))
          (talk/change-branch tx :submit)))

      :submit
      (fn [tx {{id :id} :from text :text}]
        (talk/send-text token id (tr :essay/text-of-your-essay))
        (talk/send-text token id text)
        (talk/send-text token id ">>>>>>>>>>>>>>>>>>>>")
        (talk/send-yes-no-kbd token id (str (tr :essay/is-loading-question)))
        (talk/change-branch tx :approve {:essay-text text}))

      :approve
      (fn [tx {{id :id} :from text :text} {essay-text :essay-text}]
        (talk/when-parse-yes-or-no
         tx token id text
         (talk/send-text token id (tr :essay/thank-you-your-essay-submited))
         (-> tx
             (codax/assoc-at [id :essays essay-code :text] essay-text)
             talk/stop-talk))))))

(defn get-essays [tx essay-code]
  (->> (codax/get-at tx [])
       (filter (fn [[_k v]] (-> v :essays (get essay-code) :text)))))

(defn status-talk [db {token :token} essay-code]
  (talk/def-talk db (str essay-code "status")
    (str essay-code (tr :essay/status))
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
        help (str "for admin only")]
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
                 (map #(str (format (tr :essay/preview-reviews-3) (:rank %) (+ 1 (:index %)) (:feedback %)) (let [essay (-> assignments (nth (:index %)) second)]
                                                                                                              (subs essay 0 (min (count essay) 40))) "...)")
                      (sort-by :rank reviews)))
       (tr :essay/the-last-essay-worst)))

(defn review-talk [db {token :token :as conf} essay-code]
  (let [cmd (str essay-code "review")
        help (str (tr :essay/write-review-for) essay-code)]
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
          (talk/send-text token id (str (format (tr :essay/essays-submitted-for-review-1) (count assignments))))
          (doall (map (fn [index [_auth-id text]]
                        (talk/send-text token id (str (format (tr :essay/essay-number-begin-1) (+ 1 index))))
                        (talk/send-text token id text)
                        (talk/send-text token id (str (format (tr :essay/essay-number-end-1) (+ 1 index)))))
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
        (talk/when-parse-yes-or-no
         tx token id text
         (talk/send-text token id (tr :essay/essay-feedback-saved))
         (-> (reduce (fn [tx' review]
                       (codax/update-at tx' [(:essay-author review) :essays essay-code :received-review] conj review))
                     tx reviews)
             (codax/assoc-at [id :essays essay-code :my-reviews] reviews)
             (codax/assoc-at [id :essays essay-code :my-reviews-submitted-at] (misc/str-time (misc/today)))
             talk/stop-talk))))))

(defn my-reviews [tx essay-code id]
  (->> (codax/get-at tx [id :essays essay-code :received-review])
       (map #(str (format (tr :essay/rank-1) (:rank %))
                  (when-let [fb (:feedback %)] (str (tr :essay/essay-feedback) fb))))))

(defn myfeedback-talk [db {token :token} essay-code]
  (let [cmd (str essay-code "myfeedback")
        help (str (tr :essay/feedback-on-your-essay) essay-code)]
    (talk/def-command db cmd help
      (fn [tx {{id :id} :from}]
        (let [reviews (my-reviews tx essay-code id)]
          (doall (map #(talk/send-text token id %) reviews))
          (talk/send-text token id (str (format (tr :essay/number-of-reviews-1) (count reviews)))))
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
    (let [reviews (-> data (get id) :essays (get essay-code) :received-review)
          scores (->> reviews (map :rank))]
      (if (empty? scores)
        "x"
        (-> (/ (apply + scores) (count scores))
            float
            Math/round
            (#(- 4 %)) ; 3 (max score) = 4 - 1; 1 (min score) = 4 - 3
            (+ 1) ; + 1 to get actual score
            )))))
