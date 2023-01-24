(ns course-bot.presentation-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str])
  (:require [codax.core :as codax])
  (:require [course-bot.presentation :as pres]
            [course-bot.general :as general]
            [course-bot.misc :as misc]
            [course-bot.report :as report]
            [course-bot.talk-test :as tt]))

(defn register-user [*chat talk id name]
  (testing "register user"
    (talk id "/start")
    (talk id name)
    (talk id "gr1")
    (talk id "/start")
    (tt/match-text *chat id "You are already registered. To change your information, contact the teacher and send /whoami")))

(deftest setgroup-talk-test
  (let [conf (misc/get-config "conf-example")
        db (tt/test-database)
        *chat (atom (list))
        talk (tt/handlers (general/start-talk db conf)
                          (pres/setgroup-talk db conf "lab1"))]
    (tt/with-mocked-morse *chat
      (register-user *chat talk 1 "Bot Botovich")
      (talk 1 "/lab1setgroup")
      (tt/match-text *chat "Please, select your Lab 1 presentation group: lgr1, lgr2")

      (talk 1 "miss")
      (tt/match-text *chat "I don't know this group. Try again (lgr1, lgr2)")

      (talk 1 "lgr1")
      (tt/match-text *chat "Your Lab 1 presentation group set: lgr1")

      (talk 1 "/lab1setgroup")
      (tt/match-text *chat "Your Lab 1 presentation group is already set: lgr1")
      (is (= {:lab1 {:group "lgr1"}} (codax/get-at! db [1 :presentation]))))))

(deftest submit-talk-test
  (let [conf (misc/get-config "conf-example")
        db (tt/test-database)
        *chat (atom (list))
        talk (tt/handlers (general/start-talk db conf)
                          (pres/setgroup-talk db conf "lab1")
                          (pres/submit-talk db conf "lab1"))]
    (tt/with-mocked-morse *chat

      (register-user *chat talk 1 "Bot Botovich")
      (talk 1 "/lab1submit")
      (tt/match-text *chat 1 "Please, set your 'Lab 1 presentation' group by /lab1setgroup")

      (talk 1 "/lab1setgroup")
      (talk 1 "lgr1")
      (tt/match-text *chat 1 "Your Lab 1 presentation group set: lgr1")

      (talk 1 "/lab1submit")
      (tt/match-text *chat 1 "hint")

      (talk 1 "bla-bla-bla the best")
      (tt/match-history *chat
                        (tt/text 1 "Your description:")
                        (tt/text 1 "bla-bla-bla the best")
                        (tt/text 1 "Do you approve it?"))

      (talk 1 "noooooooooooooo")
      (tt/match-text *chat 1 "Please, yes or no?")

      (talk 1 "no")
      (tt/match-text *chat 1 "You can do this later.")

      (talk 1 "/lab1submit")
      (talk 1 "bla-bla-bla the best")
      (talk 1 "yes")
      (tt/match-text *chat 1 "Registered, the teacher will check it soon.")

      (is (= {:lab1
              {:description "bla-bla-bla the best"
               :group "lgr1"
               :on-review? true}}
             (codax/get-at! db [1 :presentation]))))))

(deftest check-and-submissions-talks-test
  (let [conf (misc/get-config "conf-example")
        db (tt/test-database)
        *chat (atom (list))
        talk (tt/handlers (general/start-talk db conf)
                          (pres/setgroup-talk db conf "lab1")
                          (pres/submit-talk db conf "lab1")
                          (pres/check-talk db conf "lab1")
                          (pres/submissions-talk db conf "lab1"))]
    (tt/with-mocked-morse *chat
      (register-user *chat talk 1 "Bot Botovich")
      (talk 1 "/lab1setgroup")
      (talk 1 "lgr1")
      (tt/match-text *chat 1 "Your Lab 1 presentation group set: lgr1")

      (talk 1 "/lab1check")
      (tt/match-text *chat 1 "That action requires admin rights.")

      (talk 0 "/lab1check")
      (tt/match-text *chat 0 "Nothing to check.")

      (talk 1 "/lab1submit")
      (talk 1 "bla-bla-bla the best")
      (talk 1 "yes")
      (tt/match-text *chat 1 "Registered, the teacher will check it soon.")

      (testing "check and reject"
        (talk 0 "/lab1check")
        (tt/match-history *chat
                          (tt/text 0 "Wait for review: 1")
                          (tt/text 0 "Approved presentation in 'lgr1':\n")
                          (tt/text 0 "We receive from the student (group gr1): \n\nTopic: bla-bla-bla the best")
                          (tt/text 0 "bla-bla-bla the best")
                          (tt/text 0 "Approve (yes or no)?"))

        (talk 0 "nooooooooooooo")
        (tt/match-text *chat 0 "What (yes or no)?")

        (talk 0 "no")
        (tt/match-text *chat 0 "OK, you need to send your remark for the student:")

        (talk 0 "Please, add details!")
        (tt/match-history *chat
                          (tt/text 0 "Presentation description was declined. The student was informed about your decision.\n\n/lab1check")
                          (tt/text 1 "'Lab 1 presentation' description was rejected. Remark:\n\nPlease, add details!"))

        (talk 1 "/lab1submissions")
        (tt/match-history *chat
                          (tt/text 1 "Submitted presentation in 'lgr1':"
                                   "- bla-bla-bla the best (Bot Botovich) - REJECTED")))

      (testing "submissions-talk with specific group"
        (talk 2 "/lab1submissions")
        (tt/match-text *chat 2 "Please, set your 'Lab 1 presentation' group by /lab1setgroup")

        (talk 2 "/lab1submissions lgr1")
        (tt/match-text *chat 2 "Submitted presentation in 'lgr1':\n- bla-bla-bla the best (Bot Botovich) - REJECTED")

        (talk 2 "/lab1submissions eeeeeeeeeeeeeeeeeeeeeeeee")
        (tt/match-text *chat 2 "I don't know 'eeeeeeeeeeeeeeeeeeeeeeeee', you should specify one from: lgr1, lgr2")

        (talk 0 "/lab1submissions")
        (tt/match-history *chat
                          (tt/text 0 "Submitted presentation in 'lgr1':\n- bla-bla-bla the best (Bot Botovich) - REJECTED")
                          (tt/text 0 "Submitted presentation in 'lgr2':\n")))

      (testing "resubmit 2"
        (talk 1 "/lab1submit")
        (talk 1 "bla-bla-bla the best (second reject)")
        (talk 1 "yes")
        (tt/match-text *chat 1 "Registered, the teacher will check it soon."))

      (testing "check and reject 2"
        (talk 0 "/lab1check")
        (tt/match-history  *chat
                           (tt/text 0 "Wait for review: 1")
                           (tt/text 0 "Approved presentation in 'lgr1':\n")
                           (tt/text 0 "Remarks:")
                           (tt/text 0 "Please, add details!")
                           (tt/text 0 "We receive from the student (group gr1): \n\nTopic: bla-bla-bla the best (second reject)")
                           (tt/text 0 "bla-bla-bla the best (second reject)")
                           (tt/text 0 "Approve (yes or no)?"))

        (talk 0 "no")
        (tt/match-text *chat 0 "OK, you need to send your remark for the student:")

        (talk 0 "Please, add details 2!")
        (tt/match-history *chat
                          (tt/text 0 "Presentation description was declined. The student was informed about your decision.\n\n/lab1check")
                          (tt/text 1 "'Lab 1 presentation' description was rejected. Remark:\n\nPlease, add details 2!"))

        (talk 1 "/lab1submissions"))

      (is (= {:lab1
              {:description "bla-bla-bla the best (second reject)"
               :group "lgr1"
               :on-review? false
               :remarks '("Please, add details 2!"
                          "Please, add details!")}}
             (codax/get-at! db [1 :presentation])))

      (talk 1 "/lab1submit")
      (talk 1 "bla-bla-bla 2\ntext")
      (talk 1 "yes")
      (tt/match-text *chat 1 "Registered, the teacher will check it soon.")

      (testing "Try to resubmit:"
        (talk 1 "/lab1submit")
        (tt/match-text *chat 1 "On review, you will be informed when it is finished."))

      (talk 0 "/lab1check")
      (tt/match-history *chat
                        (tt/text 0 "Wait for review: 1")
                        (tt/text 0 "Approved presentation in 'lgr1':\n")
                        (tt/text 0 "Remarks:")
                        (tt/text 0 "Please, add details!")
                        (tt/text 0 "Please, add details 2!")
                        (tt/text 0 "We receive from the student (group gr1): \n\nTopic: bla-bla-bla 2")
                        (tt/text 0 "bla-bla-bla 2\ntext")
                        (tt/text 0 "Approve (yes or no)?"))

      (talk 0 "yes")
      (tt/match-history *chat
                        (tt/text 0 "OK, student will receive his approve.\n\n/lab1check")
                        (tt/text 1 "'Lab 1 presentation' description was approved."))

      (is (= {:lab1
              {:description "bla-bla-bla 2\ntext"
               :group "lgr1"
               :approved? true
               :on-review? false
               :remarks '("Please, add details 2!"
                          "Please, add details!")}}
             (codax/get-at! db [1 :presentation])))

      (talk 0 "/lab1check")
      (tt/match-text *chat 0 "Nothing to check.")

      (testing "Try to resubmit:"
        (talk 1 "/lab1submit")
        (tt/match-text *chat 1 "Already submitted and approved, maybe you need to schedule it? /lab1schedule"))

      (testing "Second student"
        (register-user *chat talk 2 "Alice")
        (talk 2 "/lab1submissions")
        (tt/match-text *chat 2 "Please, set your 'Lab 1 presentation' group by /lab1setgroup")

        (talk 2 "/lab1setgroup")
        (talk 2 "lgr1")

        (talk 2 "/lab1submissions")
        (tt/match-history *chat
                          (tt/text 2 "Submitted presentation in 'lgr1':"
                                   "- bla-bla-bla 2 (Bot Botovich) - APPROVED"))

        (talk 2 "/lab1submit")
        (talk 2 "pres 2")
        (talk 2 "yes")
        (tt/match-text *chat 2 "Registered, the teacher will check it soon.")

        (talk 2 "/lab1submissions")
        (tt/match-history *chat
                          (tt/text 2 "Submitted presentation in 'lgr1':"
                                   "- bla-bla-bla 2 (Bot Botovich) - APPROVED"
                                   "- pres 2 (Alice) - ON-REVIEW"))

        (talk 0 "/lab1check")
        (tt/match-history *chat
                          (tt/text 0 "Wait for review: 1")
                          (tt/text 0 "Approved presentation in 'lgr1':"
                                   "- bla-bla-bla 2 (Bot Botovich)")
                          (tt/text 0 "We receive from the student (group gr1): \n"
                                   "Topic: pres 2")
                          (tt/text 0 "pres 2")
                          (tt/text 0 "Approve (yes or no)?"))
        (talk 0 "yes")
        (tt/match-history *chat
                          (tt/text 0 "OK, student will receive his approve.\n\n/lab1check")
                          (tt/text 2 "'Lab 1 presentation' description was approved."))

        (is (= {:lab1
                {:description "pres 2"
                 :group "lgr1"
                 :approved? true
                 :on-review? false}}
               (codax/get-at! db [2 :presentation])))

        (talk 2 "/lab1submissions")
        (tt/match-text *chat 2
                       (str/join "\n" '("Submitted presentation in 'lgr1':"
                                        "- bla-bla-bla 2 (Bot Botovich) - APPROVED"
                                        "- pres 2 (Alice) - APPROVED")))))))

(deftest schedule-agenda-and-drop-talks-test
  (let [conf (misc/get-config "conf-example")
        db (tt/test-database)
        *chat (atom (list))
        talk (tt/handlers (general/start-talk db conf)
                          (pres/setgroup-talk db conf "lab1")
                          (pres/submit-talk db conf "lab1")
                          (pres/check-talk db conf "lab1")
                          (pres/submissions-talk db conf "lab1")
                          (pres/schedule-talk db conf "lab1")
                          (pres/agenda-talk db conf "lab1")
                          (pres/soon-talk db conf "lab1")
                          (pres/all-scheduled-descriptions-dump-talk db conf "lab1")
                          (pres/drop-talk db conf "lab1" false)
                          (pres/drop-talk db conf "lab1" true))]

    (tt/with-mocked-morse *chat

      (register-user *chat talk 1 "Alice")
      (talk 1 "/lab1setgroup")
      (talk 1 "lgr1")
      (talk 1 "/lab1submit")
      (talk 1 "pres 1")
      (talk 1 "yes")
      (tt/match-text *chat 1 "Registered, the teacher will check it soon.")

      (register-user *chat talk 2 "Bob")

      (testing "not registered for presentation"
        (talk 2 "/lab1schedule")
        (tt/match-text *chat 2 "Please, set your 'Lab 1 presentation' group by /lab1setgroup"))

      (talk 2 "/lab1setgroup")
      (talk 2 "lgr1")
      (talk 2 "/lab1submit")
      (talk 2 "pres 2")
      (talk 2 "yes")
      (tt/match-text *chat 2 "Registered, the teacher will check it soon.")

      (testing "not checked"
        (talk 2 "/lab1schedule")
        (tt/match-text *chat 2 "You should submit and receive approve before scheduling. Use /lab1submit"))

      (talk 0 "/lab1check")
      (talk 0 "yes")
      (talk 0 "/lab1check")
      (talk 0 "yes")
      (talk 0 "/lab1check")
      (talk 0 "Nothing to check")

      (talk 2 "/lab1submissions")
      (tt/match-history *chat
                        (tt/text 2 "Submitted presentation in 'lgr1':"
                                 "- pres 1 (Alice) - APPROVED"
                                 "- pres 2 (Bob) - APPROVED"))

      (testing "29 minutes before all lessons (first should hidden)"
        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:31 +0000"))]
          (talk 2 "/lab1schedule")
          (tt/match-history *chat
                            (tt/text 2 "Select your option:"
                                     "- 2022.01.02 12:00 +0000"))))

      (testing "31 minutes before all lessons"
        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:29 +0000"))]
          (talk 2 "/lab1schedule")
          (tt/match-history *chat
                            (tt/text 2 "Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:\n")
                            (tt/text 2 "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:\n")
                            (tt/text 2 "Select your option:"
                                     "- 2022.01.01 12:00 +0000"
                                     "- 2022.01.02 12:00 +0000"))
          (talk 2 "WRONG DATE")
          (tt/match-history *chat
                            (tt/text 2 "Not found, allow only:"
                                     "- 2022.01.01 12:00 +0000"
                                     "- 2022.01.02 12:00 +0000"))

          (talk 2 "2022.01.01 12:00 +0000")
          (tt/match-text *chat 2 "OK, you can check it by: /lab1agenda")

          (talk 2 "/lab1submissions")
          (tt/match-history *chat
                            (tt/text 2 "Submitted presentation in 'lgr1':"
                                     "- pres 1 (Alice) - APPROVED"
                                     "- pres 2 (Bob) - SCHEDULED"))

          (testing "try-to-schedule-again"
            (talk 2 "/lab1schedule")
            (tt/match-text *chat 2 "Already scheduled, check /lab1agenda."))

          (talk 1 "/lab1schedule")
          (tt/match-history *chat
                            (tt/text 1 "Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:"
                                     "1. pres 2 (Bob)")
                            (tt/text 1 "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:\n")
                            (tt/text 1 "Select your option:"
                                     "- 2022.01.01 12:00 +0000"
                                     "- 2022.01.02 12:00 +0000"))

          (talk 1 "2022.01.02 12:00 +0000")
          (tt/match-text *chat 1 "OK, you can check it by: /lab1agenda")

          (talk 1 "/lab1agenda")
          (tt/match-history *chat
                            (tt/text 1 "Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:"
                                     "1. pres 2 (Bob)")
                            (tt/text 1 "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:"
                                     "1. pres 1 (Alice)"))))

      (testing "agenda show history for one day more"
        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.02 11:30 +0000"))]
          (talk 1 "/lab1agenda")
          (tt/match-history *chat
                            (tt/text 1 "Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:"
                                     "1. pres 2 (Bob)")
                            (tt/text 1 "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:"
                                     "1. pres 1 (Alice)"))

          (testing "agenda with args"
            (talk 2 "/lab1agenda lgr1")
            (tt/match-history *chat
                              (tt/text 2 "Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:"
                                       "1. pres 2 (Bob)")
                              (tt/text 2 "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:"
                                       "1. pres 1 (Alice)"))

            (talk 2 "/lab1agenda eeeeeeeeeeeeeeeeeeeeeeeee")
            (tt/match-text *chat 2 "I don't know 'eeeeeeeeeeeeeeeeeeeeeeeee', you should specify one from: lgr1, lgr2")

            (talk 0 "/lab1agenda")
            (tt/match-history *chat
                              (tt/text 0 "Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:"
                                       "1. pres 2 (Bob)")
                              (tt/text 0 "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:"
                                       "1. pres 1 (Alice)")
                              (tt/text 0 "Agenda 2022.02.02 12:00 +0000 (lgr2):\n")))))

      (talk 0 "/lab1descriptions")
      (tt/match-history *chat
                        (tt/text 0 "File with all scheduled descriptions by groups:")
                        (tt/text 0
                                 "# lgr1"
                                 ""
                                 "## (Alice -- pres 1)"
                                 ""
                                 "pres 1"
                                 ""
                                 "## (Bob -- pres 2)"
                                 ""
                                 "pres 2"
                                 ""
                                 ""
                                 "# lgr2\n\n"))

      (testing "soon-talk"
        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:30 +0000"))]
          (talk 1 "/lab1soon")
          (tt/match-history *chat
                            (tt/text 1 "We will expect for Lab 1 presentation soon:")
                            (tt/text 1 "Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:\n1. pres 2 (Bob)")
                            (tt/text 1 "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:\n1. pres 1 (Alice)")))

        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.02 11:30 +0000"))]
          (talk 1 "/lab1soon")
          (tt/match-history *chat
                            (tt/text 1 "We will expect for Lab 1 presentation soon:")
                            (tt/text 1 "Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:\n1. pres 2 (Bob)")
                            (tt/text 1 "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:\n1. pres 1 (Alice)")))

        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.03 11:30 +0000"))]
          (talk 1 "/lab1soon")
          (tt/match-history *chat
                            (tt/text 1 "We will expect for Lab 1 presentation soon:")
                            (tt/text 1 "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:\n1. pres 1 (Alice)")))
        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.04 11:30 +0000"))]
          (talk 1 "/lab1soon")
          (tt/match-text *chat 1
                         "We will expect for Lab 1 presentation soon:"))
        (with-redefs [misc/today (fn [] (misc/read-time "2022.02.01 00:30 +0000"))]
          (talk 1 "/lab1soon")
          (tt/match-history *chat
                            (tt/text 1 "We will expect for Lab 1 presentation soon:")
                            (tt/text 1 "Agenda 2022.02.02 12:00 +0000 (lgr2):\n"))))

      (is (= {:lab1 {:approved? true
                     :description "pres 1"
                     :group "lgr1"
                     :on-review? false
                     :scheduled? true}}
             (codax/get-at! db [1 :presentation])))

      (is (= {:lab1 {:approved? true
                     :description "pres 2"
                     :group "lgr1"
                     :on-review? false
                     :scheduled? true}}
             (codax/get-at! db [2 :presentation])))

      (is (= {:lab1 {"lgr1" {"2022.01.01 12:00 +0000" {:stud-ids '(2)}
                             "2022.01.02 12:00 +0000" {:stud-ids '(1)}}}}
             (codax/get-at! db [:presentation])))

      (talk 1 "/lab1drop 1")
      (tt/match-text *chat 1 "That action requires admin rights.")

      (talk 0 "/lab1drop")
      (tt/match-text *chat 0 "Wrong input: /lab1drop 12345")

      (talk 0 "/lab1drop asdf")
      (tt/match-text *chat 0 "Wrong input: /lab1drop 12345")

      (talk 0 "/lab1drop 123")
      (tt/match-text *chat 0 "Not found.")

      (talk 0 "/lab1dropall asdf")
      (tt/match-text *chat 0 "Wrong input: /lab1dropall 12345")

      (talk 0 "/lab1drop 1")
      (tt/match-history *chat
                        (tt/text 0 "Name: Alice; Group: gr1; Telegram ID: 1")
                        (tt/text 0 "Drop 'Lab 1 presentation' config for 1?"))

      (talk 0 "noooooooooooooooooooo")
      (tt/match-text *chat 0 "What (yes or no)?")

      (talk 0 "no")
      (tt/match-text *chat 0 "Cancelled.")

      (talk 0 "/lab1drop 1")
      (tt/match-history *chat
                        (tt/text 0 "Name: Alice; Group: gr1; Telegram ID: 1")
                        (tt/text 0 "Drop 'Lab 1 presentation' config for 1?"))

      (talk 0 "yes")
      (tt/match-history *chat
                        (tt/text 0 "We drop student: 1")
                        (tt/text 1 "We drop your state for Lab 1 presentation"))

      (is (= {:lab1 {:approved? true
                     :description "pres 1"
                     :group "lgr1"
                     :on-review? false
                     :scheduled? nil}}
             (codax/get-at! db [1 :presentation])))

      (is (= {"lgr1" {"2022.01.01 12:00 +0000" {:stud-ids '(2)}
                      "2022.01.02 12:00 +0000" {:stud-ids '()}}}
             (codax/get-at! db [:presentation :lab1])))

      (talk 0 "/lab1dropall 2")
      (tt/match-history *chat
                        (tt/text 0 "Name: Bob; Group: gr1; Telegram ID: 2")
                        (tt/text 0 "Drop 'Lab 1 presentation' config for 2?"))

      (talk 0 "yes")
      (tt/match-history *chat
                        (tt/text 0 "We drop student: 2")
                        (tt/text 2 "We drop your state for Lab 1 presentation"))

      (is (= {:lab1 nil}
             (codax/get-at! db [2 :presentation])))

      (is (= {"lgr1" {"2022.01.01 12:00 +0000" {:stud-ids '()}
                      "2022.01.02 12:00 +0000" {:stud-ids '()}}}
             (codax/get-at! db [:presentation :lab1]))))))

(deftest feedback-and-rank-talks-test
  (let [conf (misc/get-config "conf-example")
        db (tt/test-database)
        *chat (atom (list))
        talk (tt/handlers (general/start-talk db conf)
                          (pres/setgroup-talk db conf "lab1")
                          (pres/submit-talk db conf "lab1")
                          (pres/check-talk db conf "lab1")
                          (pres/schedule-talk db conf "lab1")
                          (pres/agenda-talk db conf "lab1")
                          (pres/feedback-talk db conf "lab1")
                          (report/report-talk db conf
                                              "ID" report/stud-id
                                              "pres-group" (pres/report-presentation-group "lab1")
                                              "feedback-avg" (pres/report-presentation-avg-rank conf "lab1")
                                              "feedback" (pres/report-presentation-score conf "lab1")
                                              "classes" (pres/report-presentation-classes "lab1")
                                              "lesson-counter" (pres/lesson-count "lab1")))]

    (tt/with-mocked-morse *chat
      (register-user *chat talk 1 "Alice")
      (talk 1 "/lab1setgroup")
      (talk 1 "lgr1")
      (talk 1 "/lab1submit")
      (talk 1 "pres 1")
      (talk 1 "yes")
      (tt/match-text *chat 1 "Registered, the teacher will check it soon.")

      (register-user *chat talk 2 "Bob")
      (talk 2 "/lab1setgroup")
      (talk 2 "lgr1")
      (talk 2 "/lab1submit")
      (talk 2 "pres 2")
      (talk 2 "yes")
      (tt/match-text *chat 2 "Registered, the teacher will check it soon.")

      (register-user *chat talk 3 "Charly")
      (talk 3 "/lab1setgroup")
      (talk 3 "lgr1")

      (talk 0 "/lab1check")
      (talk 0 "yes")
      (talk 0 "/lab1check")
      (talk 0 "yes")
      (talk 0 "/lab1check")
      (talk 0 "Nothing to check")

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:29 +0000"))]
        (talk 2 "/lab1schedule")
        (talk 2 "2022.01.01 12:00 +0000")
        (tt/match-text *chat 2 "OK, you can check it by: /lab1agenda")

        (talk 1 "/lab1schedule")
        (talk 1 "2022.01.01 12:00 +0000")
        (tt/match-text *chat 1 "OK, you can check it by: /lab1agenda")

        (talk 1 "/lab1agenda")
        (tt/match-history *chat
                          (tt/text 1 "Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:"
                                   "1. pres 2 (Bob)"
                                   "2. pres 1 (Alice)")
                          (tt/text 1 "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:\n")))

      (is (= {:lab1 {"lgr1" {"2022.01.01 12:00 +0000" {:stud-ids '(2 1)}}}}
             (codax/get-at! db [:presentation])))

      (testing "report without feedback"
        (talk 0 "/report")
        (tt/match-csv *chat 0
                      ["ID" "pres-group" "feedback-avg" "feedback" "classes" "lesson-counter"]
                      ["0" "" "" "" "0" "0"]
                      ["1" "lgr1" "" "4" "1" "1"]
                      ["2" "lgr1" "" "2" "1" "1"]
                      ["3" "lgr1" "" "" "1" "1"]))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:29 +0000"))]
        (talk 1 "/lab1feedback")
        (tt/match-text *chat 1 "Feedback collecting disabled (too early or too late)."))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 12:29 +0000"))]
        (talk 1 "/lab1feedback")
        (tt/match-text *chat 1 "Feedback collecting disabled (too early or too late)."))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 15:01 +0000"))]
        (talk 1 "/lab1feedback")
        (tt/match-text *chat 1 "Feedback collecting disabled (too early or too late)."))

      (testing "pres group not set"
        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 12:30 +0000"))]
          (talk 4 "/lab1feedback")
          (tt/match-text *chat 4 "To send feedback, you should set your group for Lab 1 presentation by /lab1setgroup")))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 12:30 +0000"))]
        (talk 1 "/lab1feedback")
        (tt/match-history *chat
                          (tt/text 1 "Collect feedback for 'Lab 1 presentation' (lgr1) at 2022.01.01 12:00 +0000")
                          (tt/text 1 "Enter the number of the best presentation in the list:"
                                   "0. Bob (pres 2)"
                                   "1. Alice (pres 1)"))
        (talk 1 "0")
        (tt/match-history *chat
                          (tt/text 1 "Enter the number of the best presentation in the list:"
                                   "0. Alice (pres 1)"))

        (talk 1 "0")
        (tt/match-text *chat 1 "Thanks, your feedback saved!")

        (talk 2 "/lab1feedback")
        (talk 2 "1")
        (talk 2 "0")
        (tt/match-text *chat 2 "Thanks, your feedback saved!")

        (is (= {:feedback (list
                           {:receive-at (misc/normalize-time "2022.01.01 13:30 +0100")
                            :rank [{:id 1, :name "Alice", :topic "pres 1"}
                                   {:id 2, :name "Bob", :topic "pres 2"}]}
                           {:receive-at (misc/normalize-time "2022.01.01 13:30 +0100")
                            :rank [{:id 2, :name "Bob", :topic "pres 2"}
                                   {:id 1, :name "Alice", :topic "pres 1"}]})
                :feedback-from '(2 1)
                :stud-ids '(2 1)}
               (codax/get-at! db [:presentation
                                  :lab1 "lgr1"
                                  "2022.01.01 12:00 +0000"])))
        (declare tx)

        (codax/with-read-transaction [db tx]
          (is (= 1.5 (pres/avg-rank tx :lab1 1)))
          (is (= 1.5 (pres/avg-rank tx :lab1 2))))

        (testing "report"
          (talk 0 "/report")
          (tt/match-csv *chat 0
                        ["ID" "pres-group" "feedback-avg" "feedback" "classes" "lesson-counter"]
                        ["0" "" "" "" "0" "0"]
                        ["1" "lgr1" "1,5" "2" "1" "1"]
                        ["2" "lgr1" "1,5" "4" "1" "1"]
                        ["3" "lgr1" "" "" "1" "1"]
                        ["4" "" "" "" "0" "0"]))

        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 12:30 +0000"))]
          (talk 3 "/lab1feedback")
          (talk 3 "1")
          (talk 3 "0")
          (tt/match-text *chat 3 "Thanks, your feedback saved!"))

        (is (= {:feedback (list
                           {:receive-at (misc/normalize-time "2022.01.01 13:30 +0100")
                            :rank [{:id 1, :name "Alice", :topic "pres 1"}
                                   {:id 2, :name "Bob", :topic "pres 2"}]}
                           {:receive-at (misc/normalize-time "2022.01.01 13:30 +0100")
                            :rank [{:id 1, :name "Alice", :topic "pres 1"}
                                   {:id 2, :name "Bob", :topic "pres 2"}]}
                           {:receive-at (misc/normalize-time "2022.01.01 13:30 +0100")
                            :rank [{:id 2, :name "Bob", :topic "pres 2"}
                                   {:id 1, :name "Alice", :topic "pres 1"}]})
                :feedback-from '(3 2 1)
                :stud-ids '(2 1)}
               (codax/get-at! db [:presentation
                                  :lab1 "lgr1"
                                  "2022.01.01 12:00 +0000"])))

        (codax/with-read-transaction [db tx]
          (is (= 1.33 (pres/avg-rank tx :lab1 1)))
          (is (= 1.67 (pres/avg-rank tx :lab1 2)))
          (is (= nil (pres/avg-rank tx :lab1 3))))

        (codax/with-read-transaction [db tx]
          (is (= nil (pres/score tx conf :lab1 0)))
          (is (= 4 (pres/score tx conf :lab1 1)))
          (is (= 2 (pres/score tx conf :lab1 2)))
          (is (= nil (pres/score tx conf :lab1 3)))))

      (testing "report"
        (talk 0 "/report")
        (tt/match-csv *chat 0
                      ["ID" "pres-group" "feedback-avg" "feedback" "classes" "lesson-counter"]
                      ["0" "" "" "" "0" "0"]
                      ["1" "lgr1" "1,33" "4" "1" "1"]
                      ["2" "lgr1" "1,67" "2" "1" "1"]
                      ["3" "lgr1" "" "" "1" "1"]
                      ["4" "" "" "" "0" "0"])))))
