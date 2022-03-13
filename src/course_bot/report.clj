(ns course-bot.report
  (:require [codax.core :as codax]
            [codax.core :as c]
            [course-bot.presentation :as pres])
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv])
  (:require [course-bot.talk :as talk]
            [course-bot.quiz :as quiz]
            [course-bot.presentation :as pres]
            [course-bot.essay :as essay]))

(defn quiz-result [tx id name]
  (let [ans (codax/get-at tx [:quiz-results name id])
        quiz (get quiz/all-quiz name)
        [bool correct max] (quiz/stud-results-inner ans id quiz)]
    (if (= 0 max) 0
        (Math/round (* 100.0 (/ correct max))))))

(defn essay-result [tx id name]
  (let [scores (->> (essay/my-reviews tx name id)
                    (map #(subs % 24 25))
                    (map #(Integer/parseInt %))
                    (map #(- 6 %)))]
    (if (empty? scores) "-"
        (-> (/ (apply + scores) (count scores))
            float
            Math/round))))

(defn essay-review [tx id name]
  (boolean (seq (codax/get-at tx [id :essays name :my-reviews]))))

(defn send-report [tx token id]
  (let [tests [:t-1-2 :t-3-4 :t-5-6 :t-7-8 :t-9-10 :t-11-12 :t-13-14-15]
        rows (->> (codax/get-at tx [])
                  (filter #(-> % second :name))
                  (filter #(-> % (not= "yes")))
                  (map (fn [[id e]]
                         {:name (-> e :name)
                          :group (-> e :group)
                          :t-1-2 (quiz-result tx id "Лекция-1-2")
                          :t-3-4 (quiz-result tx id "Лекция-3-4")
                          :t-5-6 (quiz-result tx id "Лекции 5-6. Раздел 'Hardware и Software'")
                          :t-7-8 (quiz-result tx id "Лекция-7-8")
                          :t-9-10 (quiz-result tx id "Лекция-9-10. Системы команд. Процессор фон Неймана. Стековый процессор")
                          :t-11-12 (quiz-result tx id "Архитектура компьютера - Лекции 11-12, разделы: Память,иерархияпамяти; Устройство памяти с произвольным доступом; Кеширование")
                          :t-13-14-15 (quiz-result tx id "Архитектура компьютера - Лекции 13-14-15, разделы: Ввод-вывод, Параллелизм")
                          :e-1-result (essay-result tx id "essay1")
                          :e-1-review (essay-review tx id "essay1")
                          :e-2-result (essay-result tx id "essay2")
                          :e-2-review (essay-review tx id "essay2")
                          :e-3-result (essay-result tx id "essay3")
                          :e-3-review (essay-review tx id "essay3")
                          :e-10-result (essay-result tx id "essay10")
                          :e-10-review (essay-review tx id "essay10")
                          :e-20-result (essay-result tx id "essay20")
                          :e-20-review (essay-review tx id "essay20")
                          :e-30-result (essay-result tx id "essay30")
                          :e-30-review (essay-review tx id "essay30")

                          :lab1-teacher (pres/teacher-score tx "lab1" id)
                          :lab1-rank (pres/rank-score tx "lab1" id)

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
                 :t-1-2 :t-3-4 :t-5-6 :t-7-8 :t-9-10 :t-11-12 :t-13-14-15

                 :e-10-result
                 :e-10-review
                 :e-20-result
                 :e-20-review
                 :e-30-result
                 :e-30-review

                 :lab1-teacher
                 :lab1-rank]

        data (cons columns
                   (map (fn [row] (map #(% row) columns)) rows))]


    (with-open [writer (io/writer "out-file.csv")]
      (csv/write-csv writer data))
    (talk/send-document token id (io/file "out-file.csv"))))

(defn report-talk [db token assert-admin]
  (talk/def-command db "report"
    (fn [tx {{id :id} :chat}]
      (assert-admin tx token id)
      (talk/send-text token id "report:")
      (send-report tx token id))))
