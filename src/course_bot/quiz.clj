(ns course-bot.quiz
  (:require [clojure.string :as str])
  (:require [codax.core :as codax])
  (:require [course-bot.internationalization :as i18n :refer [tr]]
            [course-bot.general :as general]
            [course-bot.talk :as talk]))

(i18n/add-dict
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
    :already-stopped "The quiz is already stopped."
    :quiz-answers-:answers-:stud-info-:stat "Quiz answers: %s (%s) - %s"
    :incorrect-answer "I don't understand you, send the correct answer number (1, 2...)."}}
  :ru
  {:quiz
   {:quiz-already-running "Тест уже запущен."
    :available-quizzes "Доступные тесты:\n"
    :quiz-not-defined "Тест не определен."
    :confirm-run-quiz-1 "Вы действительно хотите запустить '%s' тест?"
    :quiz-started "Тест запущен."
    :quiz-canceled "В следующий раз."
    :no-running-quizzes "Нет активных тестов."
    :confirm-stop-quiz-1 "Вы действительно хотите остановить '%s' тест?"
    :quiz-was-stopped-1 "Тест '%s' был остановлен."
    :no-answers "Ответы не получены."
    :answer-correct "КОРРЕКТНЫЙ "
    :quiz-your-result "Ваш результат: "
    :quiz-is-still-in-progress "В следующий раз. Тест еще продолжается."
    :what-question "Что?"
    :quiz-cmd-description "Начать прохождение теста, если он запущен"
    :quiz-already-taking-1 "Вы уже проходили/проходите этот тест: %s. \nЕсли вы его перебили другой командой -- значит не судьба."
    :quiz-not-running "Тест не запущен, дождитесь отмашки преподавателя."
    :student-confirm-run-quiz-2 "Хотите начать тест '%s' (%d вопроса(-ов))?"
    :quiz-after-run-info "Отвечайте цифрой. Ваш первый вопрос:"
    :your-right "Ваше право."
    :what-question-yes-no "Что (да/нет)?"
    :remember-your-answer "Запомнил ваш ответ: "
    :quiz-passed "Спасибо, тест пройден. Результаты пришлю, когда тест будет закрыт."
    :already-stopped "Тест уже остановлен."
    :quiz-answers-:answers-:stud-info-:stat "Ответы на тест: %s (%s) - %s"
    :incorrect-answer "Не понял, укажите корректный номер ответа (1, 2...)."}}})

(def *quiz-state (atom {:started 0 :finished 0 :answers {}}))

(defn quiz-stud-start! [] (swap! *quiz-state update :started inc))

(defn quiz-stud-finish! [stud-id answers]
  (swap! *quiz-state #(-> % (update :finished inc) (assoc-in [:answers stud-id] answers))))

(defn start-quiz! [tx quiz-key]
  (reset! *quiz-state {:started 0 :finished 0 :answers {}})
  (codax/assoc-at tx [:quiz :current] quiz-key))

(defn current-quiz! [tx conf]
  (let [quiz-key (codax/get-at tx [:quiz :current])]
    {:key quiz-key
     :quiz (-> conf :quiz (get quiz-key))
     :name (-> conf :quiz (get quiz-key) :name)}))

(defn stop-quiz! [tx]
  (codax/assoc-at tx [:quiz :current] nil))

(defn get-longest-digits-seq [string] (apply max 0 (map count (re-seq #"\d+" string))))
(defn find-longest-digits-seq-in-list [list] (apply max (map get-longest-digits-seq list)))

(defn prepend-leading-zeros [s total-digits-count]
  (str/replace s #"\d+" #(format (str "%0" total-digits-count "d") (parse-long %))))

(defn sort-map-by-digits-in-key [m]
  (let [digits (find-longest-digits-seq-in-list (map (fn [[k _]] (name k)) m))]
    (sort-by (fn [[k _]] (prepend-leading-zeros (name k) digits)) m)))

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
                                    sort-map-by-digits-in-key
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
               (case (i18n/normalize-yes-no-text text)
                 "yes" (do (talk/send-text token id (tr :quiz/quiz-started))
                           (-> tx
                               (start-quiz! quiz-key)
                               talk/stop-talk))
                 "no" (talk/send-stop tx token id (tr :quiz/quiz-canceled))
                 (talk/clarify-input tx token id (format (tr :talk/clarify-input-tmpl) text))))))

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
                   (map #(parse-long %) ans))
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

(defn result-stat [questions results]
  (let [full-answers (->> results
                          vals
                          (filter #(= (count %) (count questions)))
                          (apply mapv vector))]
    (->> full-answers
         (map-indexed (fn [index answers]
                        (let [options (-> questions (get index) :options)]
                          (->> options
                               (map-indexed (fn [opt-index _text]
                                              (filter #(= % (str (inc opt-index))) answers)))
                               (map #(str (count %))))))))))

(defn stopquiz-talk [db {token :token :as conf}]
  (talk/talk db "stopquiz"
             :start
             (fn [tx {{id :id} :from}]
               (general/assert-admin tx conf id)
               (let [{quiz-key :key quiz-name :name} (current-quiz! tx conf)]
                 (when-not quiz-key
                   (talk/send-text token id (tr :quiz/no-running-quizzes))
                   (talk/stop-talk tx))
                 (talk/send-yes-no-kbd token id (str (format (tr :quiz/confirm-stop-quiz-1) quiz-name)))
                 (talk/change-branch tx :approve)))
             :approve
             (fn [tx {{id :id} :from text :text}]
               (let [{quiz-key :key quiz-name :name
                      {:keys [questions]} :quiz} (current-quiz! tx conf)]
                 (case (i18n/normalize-yes-no-text text)
                   "yes" (let [results (:answers @*quiz-state)
                               per-studs (map (fn [stud-id]
                                                (let [answers (get results stud-id)
                                                      {cur :count-correct
                                                       max :count-questions} (evaluate-answers questions answers)
                                                      info (str cur "/" max)]
                                                  [stud-id cur info]))
                                              (keys results))]
                           (talk/send-text token id (str (format (tr :quiz/quiz-was-stopped-1) quiz-name)))
                           (when (empty? results)
                             (talk/send-text token id (str (tr :quiz/no-answers)))
                             (-> tx stop-quiz! talk/stop-talk))

                           (->> (map vector questions (result-stat questions results))
                                (map (fn [[{:keys [ask options] :as _question} scores]]
                                       (str ask "\n\n"
                                            (str/join "\n" (map (fn [score {:keys [correct text]}]
                                                                  (str "- [" score "] "
                                                                       (when correct (tr :quiz/answer-correct))
                                                                       text))
                                                                scores
                                                                options)))))
                                (map #(talk/send-text token id %))
                                doall)

                           (doall (map (fn [[stud-id _cur info]]
                                         (talk/send-text token stud-id (str (tr :quiz/quiz-your-result) info)))
                                       per-studs))
                           (-> (reduce (fn [tx [_stud-id cur _info]] (codax/assoc-at tx [id :quiz quiz-name] cur))
                                       tx per-studs)
                               (codax/assoc-at [:quiz :results quiz-key] results)
                               stop-quiz!
                               talk/stop-talk))
                   "no" (talk/send-stop tx token id (tr :quiz/quiz-is-still-in-progress))
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
               (let [{quiz-key :key quiz :quiz quiz-name :name} (current-quiz! tx conf)
                     questions-count (-> quiz :questions count)
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
               (let [{quiz :quiz} (current-quiz! tx conf)]
                 (case (i18n/normalize-yes-no-text text)
                   "yes" (do (quiz-stud-start!)
                             (talk/send-text token id (tr :quiz/quiz-after-run-info))
                             (talk/send-text token id (question-msg quiz 0))
                             (talk/change-branch tx :quiz-step {:results '()}))
                   "no" (talk/send-stop tx token id (tr :quiz/your-right))
                   (do (talk/send-yes-no-kbd token id (str (tr :quiz/what-question-yes-no)))
                       (talk/wait tx)))))

             :quiz-step
             (fn [tx {{id :id} :from text :text} {results :results}]
               (let [{quiz-key :key quiz :quiz} (current-quiz! tx conf)
                     question-index (count results)
                     next-question-index (+ 1 question-index)
                     new-results (concat results (list text))]
                 (when (nil? quiz-key)
                   (talk/send-text token id (tr :quiz/already-stopped))
                   (-> tx talk/stop-talk))

                 (when-not (is-answer quiz question-index text)
                   (talk/send-text token id (tr :quiz/incorrect-answer))
                   (talk/wait tx))

                 (talk/send-text token id (str (tr :quiz/remember-your-answer) text))

                 (when-let [next-question (question-msg quiz next-question-index)]
                   (talk/send-text token id next-question)
                   (talk/change-branch tx :quiz-step {:results new-results}))

                 ; finish quiz
                 (quiz-stud-finish! id new-results)
                 (talk/send-text token id (tr :quiz/quiz-passed))
                 (let [{student-name :name group :group} (codax/get-at tx [id])]
                   (talk/send-text token (-> conf :admin-chat-id)
                                   (format (tr :quiz/quiz-answers-:answers-:stud-info-:stat)
                                           (str/join ", " new-results)
                                           (format "%s, %s, %s, %s" (:name quiz) student-name group id)
                                           (dissoc @*quiz-state :answers))))
                 (-> tx talk/stop-talk)))))
