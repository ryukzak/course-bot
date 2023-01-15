(ns course-bot.essay
  (:require [course-bot.talk :as talk]
            [course-bot.general :as general]
            [course-bot.misc :as misc])
  (:require [codax.core :as codax])
  (:require [clojure.string :as str]))

(defn submit-talk [db {token :token :as conf} essay-code]
  (let [cmd (str essay-code "submit")
        topics-msg (-> conf (get (keyword essay-code)) :topic-msg)
        help (str "Sumbit " essay-code)]
    (talk/def-talk db cmd help
      :start
      (fn [tx {{id :id} :from}]
        (let [submitted? (codax/get-at tx [id :essays essay-code :text])]
          (when submitted?
            (talk/send-text token id (str "Ваше эссе '" essay-code "' уже загружено"))
            (talk/stop-talk tx))
          (talk/send-text token id (str "Отправьте текст эссе '" essay-code "' одним сообщением."
                                        (when topics-msg (str " Тема(-ы):\n\n" topics-msg))))
          (talk/change-branch tx :submit)))

      :submit
      (fn [tx {{id :id} :from text :text}]
        (talk/send-text token id "Текст вашего эссе\n<<<<<<<<<<<<<<<<<<<<")
        (talk/send-text token id text)
        (talk/send-text token id ">>>>>>>>>>>>>>>>>>>>")
        (talk/send-yes-no-kbd token id (str "Загружаем (yes/no)?"))
        (talk/change-branch tx :approve {:essay-text text}))

      :approve
      (fn [tx {{id :id} :from text :text} {essay-text :essay-text}]
        (talk/when-parse-yes-or-no
         tx token id text
         (talk/send-text token id "Спасибо, текст загружен и скоро попадёт на рецензирование.")
         (-> tx
             (codax/assoc-at [id :essays essay-code :text] essay-text)
             talk/stop-talk))))))

(defn get-essays [tx essay-code]
  (->> (codax/get-at tx [])
       (filter (fn [[_k v]] (-> v :essays (get essay-code) :text)))))

(defn status-talk [db {token :token} essay-code]
  (talk/def-talk db (str essay-code "status")
    (str essay-code " статус")
    :start
    (fn [tx {{id :id} :from}]
      (let [essays (get-essays tx essay-code)]
        (talk/send-text token id
                        (str
                         "Всего эссе: " (count essays) "\n"
                         "Человек сделало ревью: "
                         (->> essays
                              (filter #(-> % second :essays (get essay-code) :my-reviews count (> 0)))
                              count) "\n"
                         "Есть комплект ревью на: "
                         (->> essays
                              vals
                              (map #(-> % :essays (get essay-code) :received-review))
                              (filter #(= 3 (count %)))
                              count) " эссе.")))

      (talk/stop-talk tx))))

(defn review-collision [essay-code id info]
  (let [reviewers (-> info :essays (get essay-code) :request-review)]
    (or (some #(= % id) reviewers)
        (not (apply distinct? reviewers)))))

(defn assign-reviews [tx essay-code n]
  (loop [n n
         limit (* n 1000)
         essays (get-essays tx essay-code)]
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
            (talk/send-text token id "ERROR: can't find assignment for some reason!")
            (talk/stop-talk tx))
          (talk/send-text token id
                          (str "Assignment count: " (count update) "; "
                               "Examples: " (some-> update first second :essays (get essay-code) :request-review)))
          (reduce (fn [tx' [id desc]]
                    (codax/assoc-at tx' [id] desc))
                  tx update))))))

(defn stud-review-assignments [tx id essay-code]
  (let [reviewer-ids (codax/get-at tx [id :essays essay-code :request-review])
        assignments (map #(codax/get-at tx [% :essays essay-code :text]) reviewer-ids)]
    (map vector reviewer-ids assignments)))

(defn preview-reviews [assignments reviews]
  (str "The first essay -- best.\n\n"
       (str/join "\n\n---\n\n"
                 (map #(str "Rank: " (:rank %) ", "
                            "essay number in the list: #" (+ 1 (:index %)) ", "
                            "your review: " (:feedback %) " "
                            "\n(few words from the essay: " (let [essay (-> assignments (nth (:index %)) second)]
                                                              (subs essay 0 (min (count essay) 40))) "...)")
                      (sort-by :rank reviews)))
       "\n\nThe last essay -- worst."))

(defn review-talk [db {token :token :as conf} essay-code]
  (let [cmd (str essay-code "review")
        help (str "write review for " essay-code)]
    (talk/def-talk db cmd help
      :start
      (fn [tx {{id :id} :from}]
        (let [assignments (stud-review-assignments tx id essay-code)]
          (when (empty? assignments)
            (talk/send-text token id "Вам не назначено ни одно эссе. Вероятно, вы не загрузили своё эссе вовремя или поспешили с отправкой ревью.")
            (talk/stop-talk tx))
          (when-not (nil? (codax/get-at tx [id :essays essay-code :my-reviews]))
            (talk/send-text token id "You already sent reviews.")
            (talk/stop-talk tx))
          (talk/send-text token id (str "Вам на ревью пришло: " (count assignments) " эссе. Их текст сейчас отправлю ниже отдельными сообщениями."))
          (doall (map (fn [index [_auth-id text]]
                        (talk/send-text token id (str "Эссе #" (+ 1 index) " <<<<<<<<<<<<<<<<<<<<"))
                        (talk/send-text token id text)
                        (talk/send-text token id (str ">>>>>>>>>>>>>>>>>>>> Эссе #" (+ 1 index))))
                      (range)
                      assignments))
          (talk/send-text token id (or (-> conf (get (keyword essay-code)) :review-msg)
                                       "Send essay numbers with feedback in separate messages from best to worse (e.g.: `<essay number> <feedback text>`)"))
          (talk/change-branch tx :get-feedback {:assignments assignments})))

      :get-feedback
      (fn [tx {{id :id} :from text :text} {assignments :assignments reviews :reviews}]
        (when (and (seq reviews) (empty? (filter :index reviews)))
          (talk/send-text token id "Увы, но вам надо начать писать отзывы сначала (если вы это сообщение видите в очередной раз -- сообщите).")
          (talk/stop-talk tx))
        (let [index (try (- (Integer/parseInt (.trim (first (re-find #"^\d(\s|$)" text)))) 1)
                         (catch Exception _ nil))
              rank (+ 1 (count reviews))
              feedback (str/replace text #"^\d*\s*" "")]

          (when (or (nil? index)
                    (< index 0) (>= index (count assignments)))
            (talk/send-text token id "The essay number is inconsistent or out of bounds.")
            (talk/wait tx))

          (when (< (count text) 40)
            (talk/send-text token id "Your feedback text is too short.")
            (talk/wait tx))

          (when (some #{index} (map :index reviews))
            (talk/send-text token id "You already rate this essay.")
            (talk/wait tx))

          (let [reviews' (cons {:rank rank
                                :index index
                                :essay-author (first (nth assignments index))
                                :review-author id
                                :feedback feedback} reviews)]
            (talk/send-text token id "ok")
            (if (not= (count reviews') (count assignments))
              (talk/change-branch tx :get-feedback {:assignments assignments :reviews reviews'})
              (do (talk/send-text token id "You have rated all the essays. Let's take a look:")
                  (talk/send-text token id (preview-reviews assignments reviews'))
                  (talk/send-yes-no-kbd token id "Correct?")
                  (talk/change-branch tx :approve {:reviews reviews'}))))))

      :approve
      (fn [tx {{id :id} :from text :text} {reviews :reviews}]
        (talk/when-parse-yes-or-no
         tx token id text
         (talk/send-text token id "Your feedback has been saved and will be available to essay writers.")
         (-> (reduce (fn [tx' review]
                       (codax/update-at tx' [(:essay-author review) :essays essay-code :received-review] conj review))
                     tx reviews)
             (codax/assoc-at [id :essays essay-code :my-reviews] reviews)
             (codax/assoc-at [id :essays essay-code :my-reviews-submitted-at] (misc/str-time (misc/today)))
             talk/stop-talk))))))

(defn my-reviews [tx essay-code id]
  (->> (codax/get-at tx [id :essays essay-code :received-review])
       (map #(str "Rank: " (:rank %)
                  (when-let [fb (:feedback %)] (str "; Feedback: " fb))))))

(defn myfeedback-talk [db {token :token} essay-code]
  (let [cmd (str essay-code "myfeedback")
        help (str "feedback on your essay " essay-code)]
    (talk/def-command db cmd help
      (fn [tx {{id :id} :from}]
        (let [reviews (my-reviews tx essay-code id)]
          (doall (map #(talk/send-text token id %) reviews))
          (talk/send-text token id (str "You received " (count reviews) " reviews.")))
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

(defn essay-score "hardcoded: rank + 1" [conf essay-code]
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
