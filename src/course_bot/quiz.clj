(ns course-bot.quiz
  (:require [clojure.string :as str])
  (:require [codax.core :as codax])
  (:require [course-bot.general :as general :refer [tr]]
            [course-bot.talk :as talk]))

(general/add-dict
 {:en
  {:quiz
   {:quiz-already-running "Quiz is already running."
    :available-quizzes "Available quizzes:\n"
    :quiz-not-defined "Quiz is not defined."
    :confirm-run-quiz-1 "Are you sure to run '%s' quiz?"
    :quiz-started "The quiz was started."
    :quiz-canceled "In a next time."
    :no-running-quizzes "No running quizzes."
    :confirm-stop-quiz-1 "Are you sure to stop '%s' quiz?"
    :quiz-was-stopped-1 "The quiz '%s' was stopped"
    :no-answers "Answers did not received."
    :answer-correct "CORRECT "
    :quiz-your-result "Your result: "
    :quiz-is-still-in-progress "In a next time. The quiz is still in progress."
    :what-question "What?"
    :quiz-cmd-description "start the quiz if it is running"
    :quiz-already-taking-1 "You have/are taking this quiz before: %s. \nIf you interrupted it with another command, then it's not fate."
    :quiz-not-running "The quiz is not running, wait for the teacher's signal."
    :student-confirm-run-quiz-2 "Would you like to start quiz '%s' (%d question(s))?"
    :quiz-after-run-info "Answer with a number. Your first question:"
    :your-right "Your right."
    :what-question-yes-no "What (yes/no)?"
    :remember-your-answer "Remember your answer: "
    :quiz-passed "Thanks, quiz passed. The results will be sent when the quiz is closed."
    :quiz-answers "Quiz answers: "
    :incorrect-answer "I don't understand you, send the correct answer number (1, 2...)."}}
  :ru
  {:quiz
   {:quiz-already-running "Тест уже запущен."
    :available-quizzes "Доступные тесты:\n"
    :quiz-not-defined "Тест не определен."
    :confirm-run-quiz-1 "Вы действительно хотите запустить '%s' тест?"
    :quiz-started "Тест запущен."
    :quiz-canceled "В следующий раз."
    :no-running-quizzes "Никакой тест не запущен."
    :confirm-stop-quiz-1 "Вы действительно хотите остановить '%s' тест?"
    :quiz-was-stopped-1 "Тест '%s' был остановлен."
    :no-answers "Ответы не получены."
    :answer-correct "КОРРЕКТНЫЙ "
    :quiz-your-result "Ваш результат: "
    :quiz-is-still-in-progress "В следующий раз. Тест еще продолжается."
    :what-question "Что?"
    :quiz-cmd-description "начать прохождение теста, если он запущен"
    :quiz-already-taking-1 "Вы уже проходили/проходите этот тест: %s. \nЕсли вы его перебили другой коммандой -- значит не судьба."
    :quiz-not-running "Тест не запущен, дождитесь отмашки преподавателя."
    :student-confirm-run-quiz-2 "Хотите начать тест '%s' (%d вопроса(-ов))?"
    :quiz-after-run-info "Отвечайте цифрой. Ваш первый вопрос:"
    :your-right "Ваше право."
    :what-question-yes-no "Что (yes/no)?"
    :remember-your-answer "Запомнили ваш ответ: "
    :quiz-passed "Спасибо, тест пройден. Результаты пришлю, когда тест будет закрыт."
    :quiz-answers "Ответы на тест: "
    :incorrect-answer "Не понял, укажите корректный номер ответа (1, 2...)."}}})

(defn startquiz-talk [db {token :token :as conf}]
  (talk/talk db "startquiz"
             :start
             (fn [tx {{id :id} :from text :text}]
               (general/assert-admin tx conf id)
               (when-not (nil? (codax/get-at tx [:quiz :current]))
                 (talk/send-text token id (tr :quiz/quiz-already-running))
                 (talk/stop-talk tx))

               (let [quiz-key (talk/command-keyword-arg text)
                     quiz (-> conf :quiz (get quiz-key))]
                 (when-not quiz-key
                   (let [quizs (->> (-> conf :quiz)
                                    (sort-by first)
                                    (map (fn [[k v]] (str "- " (name k) " (" (-> v :name) ")"))))]
                     (talk/send-text token id (str (tr :quiz/available-quizzes)
                                                   (str/join "\n" quizs)))
                     (talk/stop-talk tx)))

                 (when-not quiz
                   (talk/send-text token id (tr :quiz/quiz-not-defined)) (talk/stop-talk tx))

                 (talk/send-yes-no-kbd token id (str (format (tr :quiz/confirm-run-quiz-1) (:name quiz))))
                 (talk/change-branch tx :approve {:quiz-key quiz-key})))
             :approve
             (fn [tx {{id :id} :from text :text} {quiz-key :quiz-key}]
               (case text
                 "yes" (do
                         (talk/send-text token id (tr :quiz/quiz-started))
                         (codax/assoc-at tx [:quiz :current] quiz-key))
                 "no" (do (talk/send-text token id (tr :quiz/quiz-canceled)) (talk/stop-talk tx))))))

(defn get-test-keys-for-score [conf]
  (->> conf
       :quiz
       (filter #(-> % second :ignore-in-score not))
       keys))

(defn evaluate-answers [quiz ans]
  (let [options (->> quiz (map :options))
        bools (map (fn [opts ans] (-> opts
                                      (get (- ans 1))
                                      (get :correct false)))
                   options
                   (map #(Integer/parseInt %) ans))
        scores (map #(if % 1 0) bools)
        count-correct (reduce + scores)
        count-questions (count options)]
    {:details bools
     :count-correct count-correct
     :count-questions count-questions
     :percent (Math/round (* 100.0 (/ count-correct count-questions)))}))

(def fail-test-threshold 50)

(defn fail-tests [conf]
  (fn [_tx data id]
    (->> conf
         get-test-keys-for-score
         (filter (fn [test] (let [questions (-> conf :quiz (get test) :questions)
                                  answers (-> data :quiz :results (get test) (get id))]
                              (-> (evaluate-answers questions answers)
                                  :percent
                                  (<= fail-test-threshold)))))
         sort
         (str/join ", "))))

(defn success-tests-percent [conf]
  (fn [_tx data id]
    (let [tests (get-test-keys-for-score conf)
          count-success (->> tests
                             (filter (fn [test] (let [questions (-> conf :quiz (get test) :questions)
                                                      answers (-> data :quiz :results (get test) (get id))]
                                                  (-> (evaluate-answers questions answers) :percent
                                                      (> fail-test-threshold)))))

                             count)]
      (Math/round (* 100.0 (/ count-success (count tests)))))))

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

(defn stopquiz-talk [db {token :token :as conf}]
  (talk/talk db "stopquiz"
             :start
             (fn [tx {{id :id} :from}]
               (general/assert-admin tx conf id)
               (let [quiz-key (codax/get-at tx [:quiz :current])
                     quiz-name (-> conf :quiz (get quiz-key) :name)]
                 (when-not quiz-key
                   (talk/send-text token id (tr :quiz/no-running-quizzes))
                   (talk/stop-talk tx))
                 (talk/send-yes-no-kbd token id (str (format (tr :quiz/confirm-stop-quiz-1) quiz-name)))
                 (talk/change-branch tx :approve)))
             :approve
             (fn [tx {{id :id} :from text :text}]
               (let [quiz-key (codax/get-at tx [:quiz :current])
                     quiz-name (-> conf :quiz (get quiz-key) :name)
                     quiz (-> conf :quiz (get quiz-key))]
                 (case text
                   "yes" (let [results (codax/get-at tx [:quiz :results quiz-key])
                               per-studs (map (fn [stud-id]
                                                (let [{cur :count-correct max :count-questions} (evaluate-answers (:questions quiz)
                                                                                                                  (get results stud-id))
                                                      info (str cur "/" max)]
                                                  [stud-id cur info]))
                                              (keys results))]
                           (talk/send-text token id (str (format (tr :quiz/quiz-was-stopped-1) quiz-name)))
                           (when (empty? results)
                             (talk/send-text token id (str (tr :quiz/no-answers)))
                             (talk/stop-talk tx))

                           (doall (map  #(talk/send-text token id %)
                                        (map (fn [question scores]

                                               (str (:ask question) "\n\n"
                                                    (str/join "\n" (map #(str "- [" %1 "] " (when (:correct %2) (tr :quiz/answer-correct)) (:text %2)) scores (:options question)))))
                                             (-> quiz :questions)
                                             (result-stat quiz results))))

                           (doall (map (fn [[stud-id _cur info]] (talk/send-text token stud-id (str (tr :quiz/quiz-your-result) info)))
                                       per-studs))
                           (-> (reduce (fn [tx [_stud-id cur _info]] (codax/assoc-at tx [id :quiz (:name quiz)] cur))
                                       tx per-studs)
                               (codax/assoc-at [:quiz :current] nil)
                               talk/stop-talk))
                   "no" (do (talk/send-text token id (tr :quiz/quiz-is-still-in-progress)) (talk/stop-talk tx))
                   (do (talk/send-text token id (tr :quiz/what-question)) (talk/wait tx)))))))

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
  (talk/talk db "quiz" (tr :quiz/quiz-cmd-description)
             :start
             (fn [tx {{id :id} :from}]
               (let [quiz-key (codax/get-at tx [:quiz :current])
                     quiz (-> conf :quiz (get quiz-key))
                     questions-count (-> conf :quiz (get quiz-key) :questions count)
                     quiz-name (-> quiz :name)
                     results (codax/get-at tx [:quiz :results quiz-key id])]

                 (when (some? results)
                   (talk/send-text token id (str (format (tr :quiz/quiz-already-taking-1) quiz-key)))
                   (-> tx talk/stop-talk))

                 (when (nil? quiz)
                   (talk/send-text token id (str (tr :quiz/quiz-not-running)))
                   (-> tx talk/stop-talk))

                 (talk/send-yes-no-kbd token id (str (format (tr :quiz/student-confirm-run-quiz-2) quiz-name questions-count)))
                 (talk/change-branch tx :quiz-approve)))
             :quiz-approve
             (fn [tx {{id :id} :from text :text}]
               (let [quiz-key (codax/get-at tx [:quiz :current])
                     quiz (-> conf :quiz (get quiz-key))]
                 (case text
                   "yes" (do (talk/send-text token id (tr :quiz/quiz-after-run-info))
                             (talk/send-text token id (question-msg quiz 0))
                             (talk/change-branch tx :quiz-step))
                   "no" (do (talk/send-text token id (tr :quiz/your-right))
                            (talk/stop-talk tx))
                   (do (talk/send-yes-no-kbd token id (str (tr :quiz/what-question-yes-no)))
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
                     (talk/send-text token id (str (tr :quiz/remember-your-answer) text))
                     (if next-question
                       (do
                         (talk/send-text token id next-question)
                         (codax/assoc-at tx [:quiz :results quiz-key id] new-results))
                       (do
                         (talk/send-text token id (tr :quiz/quiz-passed))
                         (talk/send-text token (-> conf :admin-chat-id)
                                         (str (tr :quiz/quiz-answers) (str/join ", " new-results)))
                         (-> tx
                             (codax/assoc-at [:quiz :results quiz-key id] (concat results (list text)))
                             talk/stop-talk))))
                   (do
                     (talk/send-text token id (tr :quiz/incorrect-answer))
                     tx))))))
