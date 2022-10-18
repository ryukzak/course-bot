(ns course-bot.quiz
  (:require [clojure.string :as str])
  (:require [codax.core :as codax])
  (:require [course-bot.general :as general]
            [course-bot.talk :as talk]))

(defn startquiz-talk [db {token :token :as conf}]
  (talk/talk db "startquiz"
             :start
             (fn [tx {{id :id} :from text :text}]
               (general/assert-admin tx conf id)
               (when-not (nil? (codax/get-at tx [:quiz :current]))
                 (talk/send-text token id "Test is already running.")
                 (talk/stop-talk tx))

               (let [quiz-key (talk/command-keyword-arg text)
                     quiz (-> conf :quiz (get quiz-key))]
                 (when-not quiz-key
                   (let [quizs (->> (-> conf :quiz)
                                    (sort-by first)
                                    (map (fn [[k v]] (str "- " (name k) " (" (-> v :name) ")"))))]
                     (talk/send-text token id (str "Available tests:\n"
                                                   (str/join "\n" quizs)))
                     (talk/stop-talk tx)))

                 (when-not quiz
                   (talk/send-text token id "Quiz is not defined.") (talk/stop-talk tx))

                 (talk/send-yes-no-kbd token id (str "Are you sure to run '" (:name quiz) "' quiz?"))
                 (talk/change-branch tx :approve {:quiz-key quiz-key})))
             :approve
             (fn [tx {{id :id} :from text :text} {quiz-key :quiz-key}]
               (case text
                 "yes" (do
                         (talk/send-text token id "The quiz was started.")
                         (codax/assoc-at tx [:quiz :current] quiz-key))
                 "no" (do (talk/send-text token id "In a next time.") (talk/stop-talk tx))))))

(defn result-stat [quiz results]
  (->> results
       vals
       (filter #(= (count %) (count (-> quiz :questions))))
       (apply mapv vector)
       (map-indexed (fn [index anss]
                      (->>
                       (-> quiz :questions (get index) :options)
                       (map-indexed (fn [opt-index _text]
                                      (filter #(= % (str (+ 1 opt-index))) anss)))
                       (map #(str (count %))))))))

(defn stud-results-inner [quiz ans id]
  (let [options (map :options (:questions quiz))
        bools (map (fn [opts ans] (-> opts
                                      (get (- ans 1))
                                      (get :correct false)))
                   options
                   (map #(Integer/parseInt %) ans))
        scores (map #(if % 1 0) bools)]
    [bools (reduce + scores) (count options)]))

(defn stopquiz-talk [db {token :token :as conf}]
  (talk/talk db "stopquiz"
             :start
             (fn [tx {{id :id} :from}]
               (general/assert-admin tx conf id)
               (let [quiz-key (codax/get-at tx [:quiz :current])
                     quiz-name (-> conf :quiz (get quiz-key) :name)]
                 (when-not quiz-key
                   (talk/send-text token id "Никакой тест не запущен.")
                   (talk/stop-talk tx))
                 (talk/send-yes-no-kbd token id (str "Are you sure to stop '" quiz-name "' quiz?"))
                 (talk/change-branch tx :approve)))
             :approve
             (fn [tx {{id :id} :from text :text}]
               (let [quiz-key (codax/get-at tx [:quiz :current])
                     quiz-name (-> conf :quiz (get quiz-key) :name)
                     quiz (-> conf :quiz (get quiz-key))]
                 (case text
                   "yes" (let [results (codax/get-at tx [:quiz :results quiz-key])
                               per-studs (map (fn [stud-id]
                                                (let [[_details cur max] (stud-results-inner quiz (get results stud-id) stud-id)
                                                      info (str cur "/" max)]
                                                  [stud-id cur info]))
                                              (keys results))]
                           (talk/send-text token id (str "The quiz '" quiz-name "' was stopped"))

                           (doall (map  #(talk/send-text token id %)
                                        (map (fn [question scores]

                                               (str (:ask question) "\n\n"
                                                    (str/join "\n" (map #(str "- [" %1 "] " (when (:correct %2) "CORRECT ") (:text %2)) scores (:options question)))))
                                             (-> quiz :questions)
                                             (result-stat quiz results))))

                           (doall (map (fn [[stud-id _cur info]] (talk/send-text token stud-id (str "Ваш результат: " info)))
                                       per-studs))
                           (-> (reduce (fn [tx [_stud-id cur _info]] (codax/assoc-at tx [id :quiz (:name quiz)] cur))
                                       tx per-studs)
                               (codax/assoc-at [:quiz :current] nil)
                               talk/stop-talk))
                   "no" (do (talk/send-text token id "In a next time. The quiz is still in progress.") (talk/stop-talk tx))
                   (do (talk/send-text token id "What?") (talk/wait tx)))))))

(defn question-msg [quiz question-idx]
  (-> quiz
      :questions
      (get question-idx)
      (#(when % (str (:ask %) "\n\n"
                     (->> (:options %)
                          (map-indexed (fn [idx itm] (str (+ 1 idx) ". " (:text itm))))
                          (str/join "\n")))))))

(defn is-answer [quiz question-n text]
  (let [question-range (range 1 (+ 1 (-> quiz :questions (get question-n) :options count)))
        re (re-pattern (str "^["
                            (str/join "" question-range)
                            "]$"))]
    (not (empty? (re-seq re text)))))

(defn quiz-talk [db {token :token :as conf}]
  (talk/talk db "quiz" "начать прохождение теста, если он запущен"
             :start
             (fn [tx {{id :id} :from}]
               (let [quiz-key (codax/get-at tx [:quiz :current])
                     quiz (-> conf :quiz (get quiz-key))
                     questions-count (-> conf :quiz (get quiz-key) :questions count)
                     quiz-name (-> quiz :name)
                     results (codax/get-at tx [:quiz :results quiz-key id])]

                 (when (some? results)
                   (talk/send-text token id (str "Вы уже проходили/проходите этот тест: " quiz-key ". "
                                                 "Если вы его перебили другой коммандой -- значит не судьба."))
                   (-> tx talk/stop-talk))

                 (when (nil? quiz)
                   (talk/send-text token id (str "Тест не запущен, дождитесь отмашки преподавателя."))
                   (-> tx talk/stop-talk))

                 (talk/send-yes-no-kbd token id (str "Хотите начать тест '" quiz-name
                                                     "' (" questions-count " вопроса(-ов))?"))
                 (talk/change-branch tx :quiz-approve)))
             :quiz-approve
             (fn [tx {{id :id} :from text :text}]
               (let [quiz-key (codax/get-at tx [:quiz :current])
                     quiz (-> conf :quiz (get quiz-key))]
                 (case text
                   "yes" (do (talk/send-text token id "Отвечайте цифрой. Ваш первый вопрос:")
                             (talk/send-text token id (question-msg quiz 0))
                             (talk/change-branch tx :quiz-step))
                   "no" (do (talk/send-text token id "Ваше право.")
                            (talk/stop-talk tx))
                   (do (talk/send-yes-no-kbd token id (str "Что (yes/no)?"))
                       (talk/wait tx)))))
             :quiz-step
             (fn [tx {{id :id} :from text :text}]
               (let [quiz-key (codax/get-at tx [:quiz :current])
                     quiz (-> conf :quiz (get quiz-key))
                     results (codax/get-at tx [:quiz :results quiz-key id])

                     new-results (concat results (list text))
                     question-index (count results)
                     next-question-index (+ 1 (count results))
                     next-question (question-msg quiz next-question-index)]
                 (if (is-answer quiz question-index text)
                   (do
                     (talk/send-text token id (str "Запомнили ваш ответ: " text))
                     (if next-question
                       (do
                         (talk/send-text token id next-question)
                         (codax/assoc-at tx [:quiz :results quiz-key id] new-results))
                       (do
                         (talk/send-text token id "Спасибо, тест пройден. Результаты пришлю, когда тест будет закрыт.")
                         (talk/send-text token (-> conf :admin-chat-id)
                                         (str "Quiz answers: " (str/join ", " new-results)))
                         (-> tx
                             (codax/assoc-at [:quiz :results quiz-key id] (concat results (list text)))
                             talk/stop-talk))))
                   (do
                     (talk/send-text token id "Не понял, укажите корректный номер ответа (1, 2...).")
                     tx))))))
