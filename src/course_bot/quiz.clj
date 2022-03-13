(ns course-bot.quiz
  (:require [course-bot.talk :as t])
  (:require [codax.core :as c])
  (:require [clojure.string :as str])
  (:require [morse.handlers :as h]
            [morse.polling :as p])
  (:require [clojure.java.io :as io])
  (:require [clojure.pprint :refer [pprint]]))

(def quiz (read-string (try (slurp (or (System/getenv "QUIZ") "test-quiz.edn")) (catch Exception _ "nil"))))

(println "Quiz: " (:name quiz))

(def all-quiz
  (apply hash-map
         (apply concat
                (->> (try (let [path (or (System/getenv "QUIZ_PATH") "../csa-tests")]
                            (if (empty? path) []
                                (file-seq (io/file path))))
                          (catch Exception _ '()))
                     (filter #(and (.isFile %) (str/ends-with? (.getName %) ".edn")))
                     (map #(read-string (slurp %)))
                     (filter #(-> % :name seq))
                     (map #(list (:name %) %))))))

(println "All quiz:\n" (str/join "\n" (map #(-> % second :name) all-quiz)) "\n[" (count all-quiz) "]")

(defn assert-and-get-quiz [tx token id expected]
  (let [current-quiz (c/get-at tx [:quiz :current])]
    (when (nil? quiz)
      (t/send-text token id "Конфигурация теста не задана, к администратору.")
      (t/stop-talk tx))
    (when (nil? current-quiz)
      (t/send-text token id "Никакого тестирования не проводится.")
      (t/stop-talk tx))
    (when (and (some? expected) (not= current-quiz expected))
      (t/send-text token id (str "Вы залипли в другом тесте (" (-> quiz :name) "). "
                                 "Мы его оставили. Вам необходимо запустить текущий тест: /quiz"))
      (-> tx
          (c/assoc-at [:quiz-results current-quiz id] nil)
          t/stop-talk))
    current-quiz))

(defn startquiz-talk [db token assert-admin]
  (t/talk db "startquiz"
          :start
          (fn [tx {{id :id} :chat}]
            (assert-admin tx token id)
            (when (nil? quiz) (t/send-text token id "Quiz not defined.") (t/stop-talk tx))
            (when-not (nil? (c/get-at tx [:quiz :current]))
              (t/send-text token id "Тест уже запущен.")
              (t/stop-talk tx))
            (t/send-yes-no-kbd token id (str "Are you sure to run '" (:name quiz) "' quiz?"))
            (t/change-branch tx :approve))
          :approve
          (fn [tx {{id :id} :chat text :text}]
            (case text
              "yes" (do
                      (t/send-text token id "The quiz was started.")
                      (c/assoc-at tx [:quiz :current] (-> quiz :name)))
              "no" (do (t/send-text token id "In a next time.") (t/stop-talk tx))))))

(defn result-stat [results]
  (->> results
       vals
       (filter #(= (count %) (count (-> quiz :questions))))
       (apply mapv vector)
       (map-indexed (fn [index anss]
                      (->>
                       (-> quiz :questions (get index) :options)
                       (map-indexed (fn [opt-index _text]
                                      (filter #(= % (str (+ 1 opt-index))) anss)))
                       (map #(str (count %)))
                       (str/join "; "))))))

(defn stud-results-inner [ans id quiz]
  (let [options (map :options (:questions quiz))
        bools (map (fn [opts ans] (-> opts
                                      (get (- ans 1))
                                      (get :correct false)))
                   options
                   (map #(Integer/parseInt %) ans))
        scores (map #(if % 1 0) bools)]
    [bools (reduce + scores) (count options)]))

(defn stopquiz-talk [db token assert-admin]
  (t/talk db "stopquiz"
          :start
          (fn [tx {{id :id} :chat}]
            (assert-admin tx token id)
            (let [current-quiz (c/get-at tx [:quiz :current])]
              (when (nil? current-quiz)
                (t/send-text token id "Никакой тест не запущен.")
                (t/stop-talk tx))
              (when (not= current-quiz (-> quiz :name))
                (t/send-text token id "Запущенный тест не соответствует конфигурации."))
              (t/send-yes-no-kbd token id (str "Are you sure to stop '" current-quiz "' quiz?"))
              (t/change-branch tx :approve)))
          :approve
          (fn [tx {{id :id} :chat text :text}]
            (let [current-quiz (c/get-at tx [:quiz :current])]
              (case text
                "yes" (let [results (c/get-at tx [:quiz-results (:name quiz)])
                            per-studs (map (fn [stud-id]
                                             (let [[details cur max] (stud-results-inner (get results stud-id) stud-id quiz)
                                                   info (str cur "/" max " (" (str/join ", " details) ")")]
                                               [stud-id cur info]))
                                           (keys results))]
                        (t/send-text token id (str "The quiz '" current-quiz "' was stopped"))
                        (t/send-text token id
                                     (str "Статистика по ответам: \n\n"
                                          (if results
                                            (str/join "\n" (map str
                                                                (->> (-> quiz :questions)
                                                                     (map-indexed (fn [idx item] (str (+ 1 idx) ". " (:ask item) "\n"
                                                                                                      (->> (:options item)
                                                                                                           (map #(str "  - " (:text %)))
                                                                                                           (str/join "\n"))
                                                                                                      "\n  -- "))))
                                                                (result-stat results)))
                                            "нет ответов")))
                        (doall (map (fn [[stud-id _cur info]] (t/send-text token stud-id (str "Ваш результат: " info)))
                                    per-studs))
                        (-> (reduce (fn [tx [stud-id cur _info]] (c/assoc-at tx [id :quiz (:name quiz)] cur))
                                    tx per-studs)
                            (c/assoc-at [:quiz :current] nil)
                            t/stop-talk))
                "no" (do (t/send-text token id "In a next time. The quiz is still in progres.") (t/stop-talk tx))
                (t/send-text token id "What?"))))))

(defn question-str [i]
  (-> quiz
      :questions
      (get i)
      (#(when % (str (:ask %) "\n\n"
                     (->> (:options %)
                          (map-indexed (fn [idx itm] (str (+ 1 idx) ". " (:text itm))))
                          (str/join "\n")))))))

(defn is-answer [question-n text]
  (let [re (re-pattern (str "^[" (str/join "" (range 1 (+ 1 (-> quiz :questions (get question-n) :options count)))) "]$"))]
    (not (empty? (re-seq re text)))))

(defn quiz-talk [db token admin-chat]
  (t/talk db "quiz"
          :start
          (fn [tx {{id :id} :chat}]
            (let [current-quiz (assert-and-get-quiz tx token id (:name quiz))
                  results (c/get-at tx [:quiz-results current-quiz id])]
              (when-not (nil? results)
                (t/send-text token id (str "Вы уже проходили/проходите этот тест: " current-quiz ". "
                                           "Если вы его перебили другой коммандой -- значит не судьба."))
                (-> tx
                    ;; (c/assoc-at [:quiz-results current-quiz id] nil)
                    t/stop-talk))
              (t/send-yes-no-kbd token id (str "Хотите начать тест '" (:name quiz) "'?"))
              (t/change-branch tx :quiz-approve)))
          :quiz-approve
          (fn [tx {{id :id} :chat text :text}]
            (assert-and-get-quiz tx token id nil)
            (case text
              "yes" (do (t/send-text token id "Отвечайте цифрой. Ваш первый вопрос:")
                        (t/send-text token id (question-str 0))
                        (t/change-branch tx :quiz-step))
              "no" (do (t/send-text token id "Ваше право.")
                       (t/stop-talk tx))
              (t/send-yes-no-kbd token id (str "Что (yes/no)?"))))
          :quiz-step
          (fn [tx {{id :id} :chat text :text}]
            (let [current-quiz (assert-and-get-quiz tx token id (:name quiz))
                  results (c/get-at tx [:quiz-results current-quiz id])
                  new-results (concat results (list text))
                  question-index (count results)
                  next-question-index (+ 1 (count results))
                  next-question (question-str next-question-index)]
              (if (is-answer question-index text)
                (do
                  (t/send-text token id (str "Запомнили ваш ответ: " text))
                  (if next-question
                    (do
                      (t/send-text token id next-question)
                      (c/assoc-at tx [:quiz-results current-quiz id] new-results))
                    (do
                      (t/send-text token id "Спасибо, тест пройден, результат будет доступен позже.")
                      (t/send-text token admin-chat (str "Вы дали такие ответы: " (str/join ", " new-results)))
                      (-> tx
                          (c/assoc-at [:quiz-results current-quiz id] (concat results (list text)))
                          t/stop-talk))))
                (do
                  (t/send-text token id "Не понял, укажите только номер ответа.")
                  tx))))))
