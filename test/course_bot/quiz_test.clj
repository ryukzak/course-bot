(ns course-bot.quiz-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str])
  (:require [codax.core :as codax])
  (:require [course-bot.general :as general]
            [course-bot.report :as report]
            [course-bot.quiz :as quiz]
            [course-bot.talk-test :as tt]
            [course-bot.misc :as misc]))

(defn start-user [*chat talk id name]
  (testing "register user"
    (talk id "/start")
    (talk id name)
    (talk id "gr1")
    (talk id "/start")
    (tt/match-text *chat id "You are already registered. To change your information, contact the teacher and send /whoami")))

(deftest startquiz-talk-test
  (let [conf  (misc/get-config "conf-example")
        db    (tt/test-database)
        *chat (atom (list))
        talk  (tt/handlers (general/start-talk db conf)
                           (quiz/startquiz-talk db conf))]

    (tt/with-mocked-morse *chat
      (start-user *chat talk 1 "Bot Botovich")

      (talk 1 "/startquiz")
      (tt/match-text *chat 1 "That action requires admin rights.")

      (talk 0 "/startquiz missed-quiz")
      (tt/match-text *chat 0 "Quiz is not defined.")

      (talk 0 "/startquiz")
      (tt/match-text *chat 0
                     (str/join "\n"
                               '("Available tests:"
                                 "- test-quiz (Test quiz)"
                                 "- test-quiz-2 (Test quiz 2)"
                                 "- test-quiz-3 (Test quiz 3)")))

      (talk 0 "/startquiz test-quiz")
      (tt/match-text *chat 0 "Are you sure to run 'Test quiz' quiz?")

      (talk 0 "no")
      (tt/match-text *chat 0 "In a next time.")

      (talk 0 "/startquiz test-quiz")
      (tt/match-text *chat 0 "Are you sure to run 'Test quiz' quiz?")

      (talk 0 "yes")
      (tt/match-text *chat 0 "The quiz was started.")
      (is (= {:current :test-quiz} (codax/get-at! db [:quiz])))

      (talk 0 "/startquiz test-quiz")
      (tt/match-text *chat 0 "Test is already running.")

      (talk 0 "/startquiz")
      (tt/match-text *chat 0 "Test is already running."))))

(deftest quiz-talk-test
  (let [conf  (misc/get-config "conf-example")
        db    (tt/test-database)
        *chat (atom (list))
        talk  (tt/handlers (general/start-talk db conf)
                           (quiz/startquiz-talk db conf)
                           (quiz/quiz-talk db conf))]

    (tt/with-mocked-morse *chat
      (start-user *chat talk 1 "Bot Botovich")

      (talk 1 "/quiz")
      (tt/match-text *chat 1 "Тест не запущен, дождитесь отмашки преподавателя.")

      (talk 0 "/startquiz test-quiz")
      (talk 0 "yes")
      (tt/match-text *chat 0 "The quiz was started.")

      (talk 1 "/quiz")
      (tt/match-text *chat 1 "Хотите начать тест 'Test quiz' (2 вопроса(-ов))?")

      (talk 1 "nooooooo")
      (tt/match-text *chat 1 "Что (yes/no)?")

      (talk 1 "no")
      (tt/match-text *chat 1 "Ваше право.")

      (talk 1 "/quiz")
      (tt/match-text *chat 1 "Хотите начать тест 'Test quiz' (2 вопроса(-ов))?")
      (talk 1 "yes")
      (tt/match-history *chat
                        (tt/text 1 "Отвечайте цифрой. Ваш первый вопрос:")
                        (tt/text 1 "Q1\n"
                                 "1. a1"
                                 "2. a2"))
      (talk 1 "first")
      (tt/match-text *chat 1 "Не понял, укажите корректный номер ответа (1, 2...).")

      (talk 1 "0")
      (tt/match-text *chat 1 "Не понял, укажите корректный номер ответа (1, 2...).")

      (talk 1 "3")
      (tt/match-text *chat 1 "Не понял, укажите корректный номер ответа (1, 2...).")

      (talk 1 "1")
      (tt/match-history *chat
                        (tt/text 1 "Запомнили ваш ответ: 1")
                        (tt/text 1 "Q2\n"
                                 "1. a3"
                                 "2. a4"))

      (talk 1 "2")
      (tt/match-history *chat
                        (tt/text 1 "Спасибо, тест пройден. Результаты пришлю, когда тест будет закрыт.")
                        (tt/text 0 "Quiz answers: 1, 2"))

      (is (= {:test-quiz {1 '("1" "2")}} (codax/get-at! db [:quiz :results]))))))

(deftest stopquiz-talk-test
  (let [conf  (misc/get-config "conf-example")
        db    (tt/test-database)
        *chat (atom (list))
        talk  (tt/handlers
               (general/start-talk db conf)
               (quiz/startquiz-talk db conf)
               (quiz/quiz-talk db conf)
               (quiz/stopquiz-talk db conf)
               (report/report-talk db conf
                                   "ID" report/stud-id
                                   "fail" (quiz/fail-tests conf)
                                   "percent" (quiz/success-tests-percent conf)))]

    (tt/with-mocked-morse *chat

      (start-user *chat talk 1 "Bot Botovich")
      (talk 0 "/startquiz test-quiz")
      (talk 0 "yes")
      (tt/match-text *chat 0 "The quiz was started.")

      (talk 1 "/quiz")
      (tt/match-text *chat 1 "Хотите начать тест 'Test quiz' (2 вопроса(-ов))?")
      (talk 1 "yes")
      (talk 1 "1")
      (talk 1 "1")
      (tt/match-history *chat
                        (tt/text 1 "Спасибо, тест пройден. Результаты пришлю, когда тест будет закрыт.")
                        (tt/text 0 "Quiz answers: 1, 1"))

      (is (= {:test-quiz {1 '("1" "1")}} (codax/get-at! db [:quiz :results])))

      (talk 1 "/stopquiz")
      (tt/match-text *chat 1 "That action requires admin rights.")

      (talk 0 "/stopquiz")
      (tt/match-text *chat 0 "Are you sure to stop 'Test quiz' quiz?")

      (talk 0 "noooooooooooo")
      (tt/match-text *chat 0 "What?")

      (talk 0 "no")
      (tt/match-text *chat 0 "In a next time. The quiz is still in progress.")

      (talk 0 "/stopquiz")
      (tt/match-text *chat 0 "Are you sure to stop 'Test quiz' quiz?")

      (talk 0 "yes")
      (tt/match-history *chat
                        (tt/text 0 "The quiz 'Test quiz' was stopped")
                        (tt/text 0
                                 "Q1"
                                 ""
                                 "- [1] a1"
                                 "- [0] CORRECT a2")
                        (tt/text 0
                                 "Q2"
                                 ""
                                 "- [1] CORRECT a3"
                                 "- [0] a4")
                        (tt/text 1 "Ваш результат: 1/2"))

      (testing "report"
        (talk 0 "/report")
        (tt/match-history *chat
                          (tt/text 0
                                   "ID;fail;percent"
                                   "0;:test-quiz, :test-quiz-3;0"
                                   "1;:test-quiz, :test-quiz-3;0"
                                   ""))))))

(deftest report-talk-test
  (let [conf    (misc/get-config "conf-example")
        db      (tt/test-database)
        *chat   (atom (list))
        talk    (tt/handlers (general/start-talk db conf)
                             (quiz/startquiz-talk db conf)
                             (quiz/quiz-talk db conf)
                             (quiz/stopquiz-talk db conf)
                             (report/report-talk db conf
                                                 "ID" report/stud-id
                                                 "fail" (quiz/fail-tests conf)
                                                 "percent" (quiz/success-tests-percent conf)))
        do-test (fn [name id & answers]
                  (talk id "/quiz")
                  (tt/match-text *chat id
                                 (str "Хотите начать тест '" name "' ("
                                      (count answers) " вопроса(-ов))?"))
                  (talk id "yes")
                  (doall (map #(talk id %) answers))
                  (tt/match-history *chat
                                    (tt/text id "Спасибо, тест пройден. Результаты пришлю, когда тест будет закрыт.")
                                    (tt/text 0 (str "Quiz answers: " (str/join ", " answers)))))]

    (tt/with-mocked-morse *chat

      (start-user *chat talk 1 "Alice")
      (start-user *chat talk 2 "Bob")
      (start-user *chat talk 3 "Charly")
      (start-user *chat talk 4 "Dany")

      (talk 0 "/startquiz test-quiz-3")
      (talk 0 "yes")
      (tt/match-text *chat 0 "The quiz was started.")

      (do-test "Test quiz 3" 1 "1" "2" "2")
      (do-test "Test quiz 3" 2 "1" "2" "1")
      (do-test "Test quiz 3" 3 "1" "1" "1")
      (do-test "Test quiz 3" 4 "2" "1" "1")

      (talk 0 "/stopquiz")
      (tt/match-text *chat 0 "Are you sure to stop 'Test quiz 3' quiz?")
      (talk 0 "yes")
      (tt/match-history *chat
                        (tt/text 0 "The quiz 'Test quiz 3' was stopped")
                        (tt/text 0 "Q1\n\n- [3] a1\n- [1] CORRECT a2")
                        (tt/text 0 "Q2\n\n- [2] CORRECT a3\n- [2] a4")
                        (tt/text 0 "Q3\n\n- [3] CORRECT a5\n- [1] a6")
                        (tt/text 1 "Ваш результат: 0/3")
                        (tt/text 2 "Ваш результат: 1/3")
                        (tt/text 3 "Ваш результат: 2/3")
                        (tt/text 4 "Ваш результат: 3/3"))

      (is
       (=
        {:test-quiz-3 {1 '("1" "2" "2"), 2 '("1" "2" "1"), 3 '("1" "1" "1"), 4 '("2" "1" "1")}}
        (codax/get-at! db [:quiz :results])))

      (testing "report 1"
        (talk 0 "/report")
        (tt/match-history *chat
                          (tt/text 0 "ID;fail;percent"
                                   "0;:test-quiz, :test-quiz-3;0"
                                   "1;:test-quiz, :test-quiz-3;0"
                                   "2;:test-quiz, :test-quiz-3;0"
                                   "3;:test-quiz;50"
                                   "4;:test-quiz;50"
                                   "")))

      (talk 0 "/startquiz test-quiz")
      (talk 0 "yes")
      (tt/match-text *chat 0 "The quiz was started.")

      (do-test "Test quiz" 1 "2" "1")
      (do-test "Test quiz" 4 "2" "1")

      (talk 0 "/stopquiz")
      (tt/match-text *chat 0 "Are you sure to stop 'Test quiz' quiz?")
      (talk 0 "yes")

      (testing "report 1"
        (talk 0 "/report")
        (tt/match-history *chat
                          (tt/text 0
                                   "ID;fail;percent"
                                   "0;:test-quiz, :test-quiz-3;0"
                                   "1;:test-quiz-3;50"
                                   "2;:test-quiz, :test-quiz-3;0"
                                   "3;:test-quiz;50"
                                   "4;;100"
                                   ""))))))

(deftest stop-quiz-without-answers-talk-test
  (let [conf    (misc/get-config "conf-example")
        db      (tt/test-database)
        *chat   (atom (list))
        talk    (tt/handlers (general/start-talk db conf)
                             (quiz/startquiz-talk db conf)
                             (quiz/quiz-talk db conf)
                             (quiz/stopquiz-talk db conf)
                             (report/report-talk db conf
                                                 "ID" report/stud-id
                                                 "fail" (quiz/fail-tests conf)
                                                 "percent" (quiz/success-tests-percent conf)))]
    (tt/with-mocked-morse *chat

      (start-user *chat talk 1 "Alice")

      (talk 0 "/startquiz test-quiz-3")
      (talk 0 "yes")
      (tt/match-text *chat 0 "The quiz was started.")

      (talk 0 "/stopquiz")
      (tt/match-text *chat 0 "Are you sure to stop 'Test quiz 3' quiz?")
      (talk 0 "yes")
      (tt/match-history *chat
                        (tt/text 0 "The quiz 'Test quiz 3' was stopped")
                        (tt/text 0 "Answers did not recieved.")))))
