(ns course-bot.quiz-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing is] :as t])
  (:require [codax.core :as codax])
  (:require [course-bot.general :as general]
            [course-bot.misc :as misc]
            [course-bot.quiz :as quiz]
            [course-bot.report :as report]
            [course-bot.talk-test :refer [answers?] :as tt]))

(defn start-user [_*chat talk id name]
  (testing "register user"
    (talk id "/start")
    (talk id name)
    (talk id "gr1")
    (is (answers? (talk id "/start")
          "You are already registered. To change your information, contact the teacher and send /whoami"))))

(deftest startquiz-talk-test
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))
        {talk :talk
         *chat :*chat} (tt/test-handler (general/start-talk db conf)
                         (quiz/startquiz-talk db conf))]

    (tt/with-mocked-morse *chat
      (start-user *chat talk 1 "Bot Botovich")

      (is (answers? (talk 1 "/startquiz")
            "That action requires admin rights."))

      (is (answers? (talk 0 "/startquiz missed-quiz")
            "Quiz is not defined."))

      (is (answers? (talk 0 "/startquiz missed-quiz")
            "Quiz is not defined."))

      (is (answers? (talk 0 "/startquiz")
            (tt/unlines "Available quizzes:"
              "- test-quiz (Test quiz)"
              "- test-quiz-2 (Test quiz 2)"
              "- test-quiz-3 (Test quiz 3)"
              "- test-quiz-10 (Test quiz 10)")))

      (is (answers? (talk 0 "/startquiz test-quiz")
            "Are you sure to run 'Test quiz' quiz?"))

      (is (answers? (talk 0 "no")
            "In a next time."))

      (is (answers? (talk 0 "/startquiz test-quiz")
            "Are you sure to run 'Test quiz' quiz?"))

      (is (answers? (talk 0 "yes")
            "The quiz was started."))
      (is (= {:current :test-quiz} (codax/get-at! db [:quiz])))

      (is (answers? (talk 0 "/startquiz test-quiz")
            "Quiz is already running."))

      (is (answers? (talk 0 "/startquiz")
            "Quiz is already running.")))))

(deftest quiz-talk-test
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))

        {talk :talk *chat :*chat}
        (tt/test-handler (general/start-talk db conf)
          (quiz/startquiz-talk db conf)
          (quiz/stopquiz-talk db conf)
          (quiz/quiz-talk db conf))]

    (tt/with-mocked-morse *chat
      (with-redefs [quiz/utime (fn [] 42)]
        (start-user *chat talk 1 "Bot Botovich")

        (is (answers? (talk 1 "/quiz")
              "The quiz is not running, wait for the teacher's signal."))

        (talk 0 "/startquiz test-quiz")
        (is (answers? (talk 0 "yes")
              "The quiz was started."))

        (is (answers? (talk 1 "/quiz")
              "Would you like to start quiz 'Test quiz' (2 question(s))?"))

        (is (answers? (talk 1 "nooooooo")
              "What (yes/no)?"))

        (is (answers? (talk 1 "no")
              "Your right."))

        (is (answers? (talk 1 "/quiz")
              "Would you like to start quiz 'Test quiz' (2 question(s))?"))

        (is (answers? (talk 1 "yes")
              "Answer with a number. Your first question:"
              (tt/unlines "Q1\n"
                "1. a1"
                "2. a2")))

        (is (answers? (talk 1 "first" "0" "3")
              "I don't understand you, send the correct answer number (1, 2...)."
              "I don't understand you, send the correct answer number (1, 2...)."
              "I don't understand you, send the correct answer number (1, 2...).")
          "Wrong answer")

        (is (answers? (talk 1 "1")
              "Remember your answer: 1"
              (tt/unlines "Q2\n"
                "1. a3"
                "2. a4")))

        (is (answers? (talk 1 "2")
              [1 "Remember your answer: 2"]
              [1 "Thanks, quiz passed. The results will be sent when the quiz is closed."]
              [0 "Quiz answers: 1, 2 (Test quiz, Bot Botovich, gr1, 1) - {:started 1, :finished 1}"]))

        (is (answers? (talk 0 "/stopquiz")
              "Are you sure to stop 'Test quiz' quiz?"))

        (is (answers? (talk 0 "yes")
              [0 "The quiz 'Test quiz' was stopped"]
              [0 (tt/unlines "Q1\n"
                   "- [1] a1"
                   "- [0] CORRECT a2")]
              [0 (tt/unlines "Q2\n"
                   "- [0] CORRECT a3"
                   "- [1] a4")]
              [0 (tt/unlines
                   ":stud,:start,1,2,:finish"
                   "Bot Botovich,42,42,42,42")]
              [1 "Your result: 0/2"]))
        (is (= {:test-quiz {1 '("1" "2")}} (codax/get-at! db [:quiz :results])))))))

(deftest stopquiz-talk-test
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))

        {talk :talk, *chat :*chat}
        (tt/test-handler
          (general/start-talk db conf)
          (quiz/startquiz-talk db conf)
          (quiz/quiz-talk db conf)
          (quiz/stopquiz-talk db conf)
          (report/report-talk db conf
            "ID" report/stud-id
            "fail" (quiz/fail-tests conf)
            "percent" (quiz/success-tests-percent conf)))]

    (tt/with-mocked-morse *chat
      (with-redefs [quiz/utime (fn [] 42)]
        (start-user *chat talk 1 "Bot Botovich")
        (talk 0 "/startquiz test-quiz")
        (talk 0 "yes")
        (is (= (tt/history *chat :user-id 0)
              ["The quiz was started."]))

        (talk 1 "/quiz")
        (is (= (tt/history *chat :user-id 1)
              ["Would you like to start quiz 'Test quiz' (2 question(s))?"]))

        (talk 1 "yes")
        (talk 1 "1")
        (talk 1 "1")
        (is (= (tt/history *chat :number 2)
              [[1 "Thanks, quiz passed. The results will be sent when the quiz is closed."]
               [0 "Quiz answers: 1, 1 (Test quiz, Bot Botovich, gr1, 1) - {:started 1, :finished 1}"]]))

        (talk 1 "/stopquiz")
        (is (= (tt/history *chat :user-id 1)
              ["That action requires admin rights."]))

        (talk 0 "/stopquiz")
        (is (= (tt/history *chat :user-id 0)
              ["Are you sure to stop 'Test quiz' quiz?"]))

        (talk 0 "noooooooooooo")
        (is (= (tt/history *chat :user-id 0)
              ["What?"]))

        (talk 0 "no")
        (is (= (tt/history *chat :user-id 0)
              ["In a next time. The quiz is still in progress."]))

        (talk 0 "/stopquiz")
        (is (= (tt/history *chat :user-id 0)
              ["Are you sure to stop 'Test quiz' quiz?"]))

        (talk 0 "yes")
        (is (= (tt/history *chat :user-id 0 :number 4)
              ["The quiz 'Test quiz' was stopped"
               (tt/unlines "Q1"
                 ""
                 "- [1] a1"
                 "- [0] CORRECT a2")
               (tt/unlines "Q2"
                 ""
                 "- [1] CORRECT a3"
                 "- [0] a4")
               (tt/unlines
                 ":stud,:start,1,2,:finish"
                 "Bot Botovich,42,42,42,42")])))
      (is (= (tt/history *chat :user-id 1)
            ["Your result: 1/2"]))

      (is (= {:test-quiz {1 '("1" "1")}} (codax/get-at! db [:quiz :results])))

      (testing "report"
        (talk 0 "/report")
        (is (= (tt/history *chat :user-id 0)
              [(tt/unlines
                 "ID;fail;percent"
                 "0;:test-quiz, :test-quiz-3;0"
                 "1;:test-quiz, :test-quiz-3;0")]))))))

(deftest report-talk-test
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))

        {talk :talk, *chat :*chat}
        (tt/test-handler (general/start-talk db conf)
          (quiz/startquiz-talk db conf)
          (quiz/quiz-talk db conf)
          (quiz/stopquiz-talk db conf)
          (report/report-talk db conf
            "ID" report/stud-id
            "fail" (quiz/fail-tests conf)
            "percent" (quiz/success-tests-percent conf)))
        format-quiz-answers (fn [new-results quiz-name student-name group id n]
                              (format "Quiz answers: %s (%s, %s, %s, %s) - %s"
                                (str/join ", " new-results)
                                quiz-name student-name group id
                                {:started n :finished n}))
        do-test (fn [quiz-name id n & answers]
                  (is (answers? (talk id "/quiz")
                        (str "Would you like to start quiz '" quiz-name "' ("
                             (count answers) " question(s))?")))
                  (talk id "yes")
                  (doall (map #(talk id %) answers))

                  (let [{stud-name :name stud-group :group} (codax/get-at! db [id])]
                    (tt/match-history *chat
                      (tt/text id "Thanks, quiz passed. The results will be sent when the quiz is closed.")
                      (tt/text 0 (format-quiz-answers answers quiz-name stud-name stud-group id n)))))]

    (tt/with-mocked-morse *chat

      (start-user *chat talk 1 "Alice")
      (start-user *chat talk 2 "Bob")
      (start-user *chat talk 3 "Charly")
      (start-user *chat talk 4 "Dany")

      (talk 0 "/startquiz test-quiz-3")
      (is (answers? (talk 0 "yes") "The quiz was started."))
      (with-redefs [quiz/utime (fn [] 42)]
        (do-test "Test quiz 3" 1 1 "1" "2" "2")
        (do-test "Test quiz 3" 2 2 "1" "2" "1")
        (do-test "Test quiz 3" 3 3 "1" "1" "1")
        (do-test "Test quiz 3" 4 4 "2" "1" "1"))

      (is (answers? (talk 0 "/stopquiz") "Are you sure to stop 'Test quiz 3' quiz?"))
      (talk 0 "yes")
      (tt/match-history *chat
        (tt/text 0 "The quiz 'Test quiz 3' was stopped")
        (tt/text 0 "Q1\n\n- [3] a1\n- [1] CORRECT a2")
        (tt/text 0 "Q2\n\n- [2] CORRECT a3\n- [2] a4")
        (tt/text 0 "Q3\n\n- [3] CORRECT a5\n- [1] a6")
        (tt/text 0 (tt/unlines

                     ":stud,:start,1,2,3,:finish"
                     "Alice,42,42,42,42,42"
                     "Bob,42,42,42,42,42"
                     "Charly,42,42,42,42,42"
                     "Dany,42,42,42,42,42"))

        (tt/text 1 "Your result: 0/3")
        (tt/text 2 "Your result: 1/3")
        (tt/text 3 "Your result: 2/3")
        (tt/text 4 "Your result: 3/3"))

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
      (is (answers? (talk 0 "yes") "The quiz was started."))
      (do-test "Test quiz" 1 1 "2" "1")
      (do-test "Test quiz" 4 2 "2" "1")

      (is (answers? (talk 0 "/stopquiz") "Are you sure to stop 'Test quiz' quiz?"))
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
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))

        {talk :talk, *chat :*chat}
        (tt/test-handler (general/start-talk db conf)
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
      (is (answers? (talk 0 "yes") "The quiz was started."))
      (is (answers? (talk 0 "/stopquiz") "Are you sure to stop 'Test quiz 3' quiz?"))
      (talk 0 "yes")
      (tt/match-history *chat
        (tt/text 0 "The quiz 'Test quiz 3' was stopped")
        (tt/text 0 "Answers did not received.")))))

(deftest quiz-yes-no-test
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))

        {talk :talk, *chat :*chat}
        (tt/test-handler (general/start-talk db conf)
          (quiz/startquiz-talk db conf)
          (quiz/quiz-talk db conf)
          (quiz/stopquiz-talk db conf))]
    (tt/with-mocked-morse *chat
      (start-user *chat talk 1 "Alice")

      (talk 0 "/startquiz test-quiz")
      (is (answers? (talk 0 "yeS") "The quiz was started."))
      (is (answers? (talk 1 "/quiz") "Would you like to start quiz 'Test quiz' (2 question(s))?"))
      (is (answers? (talk 1 "NO") "Your right."))
      (is (answers? (talk 1 "/quiz") "Would you like to start quiz 'Test quiz' (2 question(s))?"))
      (talk 1 "YEs")
      (tt/match-history *chat
        (tt/text 1 "Answer with a number. Your first question:")
        (tt/text 1 "Q1\n"
          "1. a1"
          "2. a2"))

      (talk 0 "/stopquiz")
      (is (= (tt/history *chat :user-id 0)
            ["Are you sure to stop 'Test quiz' quiz?"]))

      (talk 0 "NO")
      (is (= (tt/history *chat :user-id 0)
            ["In a next time. The quiz is still in progress."]))

      (talk 0 "/stopquiz")
      (is (= (tt/history *chat :user-id 0)
            ["Are you sure to stop 'Test quiz' quiz?"]))

      (talk 0 "YES")
      (is (not= (tt/history *chat :user-id 0)
            ["Are you sure to stop 'Test quiz' quiz?"])))))

(defn quiz-performance [n]
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))

        {talk :talk *chat :*chat}
        (tt/test-handler (general/start-talk db conf)
          (quiz/startquiz-talk db conf)
          (quiz/stopquiz-talk db conf)
          (quiz/quiz-talk db conf))]

    (tt/with-mocked-morse *chat
      (talk 0 "/startquiz test-quiz-3")
      (is (answers? (talk 0 "yes")
            "The quiz was started."))

      (let [ids (range 1 n)]
        (->> ids (map #(future (talk % "/start" (str "Bot-" %) "gr1")
                               (talk % "/quiz" "yes")
                               (talk % "1" "1" "1")))
             doall
             (map deref)
             doall)))))

(deftest quiz-performance-test (time (quiz-performance 100)))
