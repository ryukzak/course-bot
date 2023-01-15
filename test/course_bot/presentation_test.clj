(ns course-bot.presentation-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str])
  (:require [codax.core :as codax])
  (:require [course-bot.presentation :as pres]
            [course-bot.general :as general]
            [course-bot.misc :as misc]
            [course-bot.talk :as talk]
            [course-bot.report :as report]
            [course-bot.talk-test :as ttalk]))

(defn register-user [*chat start-talk id name]
  (testing "register user"
    (start-talk id "/start")
    (start-talk id name)
    (start-talk id "gr1")
    (start-talk id "/start")
    (ttalk/in-history *chat [id "You are already registered. To change your information, contact the teacher and send /whoami"])))

(declare submit-without-config-talk-test db tx *chat)

(talk/deftest setgroup-talk-test [db *chat]
  (let [conf (misc/get-config "conf-example")
        start-talk (ttalk/mock-talk general/start-talk db conf)
        setgroup-talk (ttalk/mock-talk pres/setgroup-talk db conf "lab1")]

    (register-user *chat start-talk 1 "Bot Botovich")
    (setgroup-talk 1 "/lab1setgroup")
    (ttalk/in-history *chat "Please, select your Lab 1 presentation group: lgr1, lgr2")

    (setgroup-talk 1 "miss")
    (ttalk/in-history *chat "I don't know this group. Try again (lgr1, lgr2)")

    (setgroup-talk 1 "lgr1")
    (ttalk/in-history *chat "Your Lab 1 presentation group set: lgr1")

    (setgroup-talk 1 "/lab1setgroup")
    (ttalk/in-history *chat "Your Lab 1 presentation group is already set: lgr1")
    (is (= {:lab1 {:group "lgr1"}} (codax/get-at! db [1 :presentation])))))

(talk/deftest submit-talk-test [db *chat]
  (let [conf (misc/get-config "conf-example")
        start-talk (ttalk/mock-talk general/start-talk db conf)
        setgroup-talk (ttalk/mock-talk pres/setgroup-talk db conf "lab1")
        submit-talk (ttalk/mock-talk pres/submit-talk db conf "lab1")]

    (register-user *chat start-talk 1 "Bot Botovich")
    (submit-talk 1 "/lab1submit")
    (ttalk/in-history *chat 1 "Please, set your 'Lab 1 presentation' group by /lab1setgroup")

    (setgroup-talk 1 "/lab1setgroup")
    (setgroup-talk 1 "lgr1")
    (ttalk/in-history *chat 1 "Your Lab 1 presentation group set: lgr1")

    (submit-talk 1 "/lab1submit")
    (ttalk/in-history *chat 1 "hint")

    (submit-talk 1 "bla-bla-bla the best")
    (ttalk/in-history *chat 1
                      "Your description:"
                      "bla-bla-bla the best"
                      "Do you approve it?")

    (submit-talk 1 "noooooooooooooo")
    (ttalk/in-history *chat 1 "Please, yes or no?")

    (submit-talk 1 "no")
    (ttalk/in-history *chat 1 "You can do this later.")

    (submit-talk 1 "/lab1submit")
    (submit-talk 1 "bla-bla-bla the best")
    (submit-talk 1 "yes")
    (ttalk/in-history *chat 1 "Registered, the teacher will check it soon.")

    (is (= {:lab1
            {:description "bla-bla-bla the best"
             :group "lgr1"
             :on-review? true}}
           (codax/get-at! db [1 :presentation])))))

(talk/deftest check-and-submissions-talks-test [db *chat]
  (let [conf (misc/get-config "conf-example")
        start-talk (ttalk/mock-talk general/start-talk db conf)
        setgroup-talk (ttalk/mock-talk pres/setgroup-talk db conf "lab1")
        submit-talk (ttalk/mock-talk pres/submit-talk db conf "lab1")
        check-talk (ttalk/mock-talk pres/check-talk db conf "lab1")
        submissions-talk (ttalk/mock-talk pres/submissions-talk db conf "lab1")]
    (register-user *chat start-talk 1 "Bot Botovich")
    (setgroup-talk 1 "/lab1setgroup")
    (setgroup-talk 1 "lgr1")
    (ttalk/in-history *chat 1 "Your Lab 1 presentation group set: lgr1")

    (check-talk 1 "/lab1check")
    (ttalk/in-history *chat 1 "That action requires admin rights.")

    (check-talk 0 "/lab1check")
    (ttalk/in-history *chat 0 "Nothing to check.")

    (submit-talk 1 "/lab1submit")
    (submit-talk 1 "bla-bla-bla the best")
    (submit-talk 1 "yes")
    (ttalk/in-history *chat 1 "Registered, the teacher will check it soon.")

    (testing "check and reject"
      (check-talk 0 "/lab1check")
      (ttalk/in-history *chat 0
                        "Wait for review: 1"
                        "Approved presentation in 'lgr1':\n"
                        "We receive from the student (group gr1): \n\nTopic: bla-bla-bla the best"
                        "bla-bla-bla the best"
                        "Approve (yes or no)?")

      (check-talk 0 "nooooooooooooo")
      (ttalk/in-history *chat 0 "What (yes or no)?")

      (check-talk 0 "no")
      (ttalk/in-history *chat 0 "OK, you need to send your remark for the student:")

      (check-talk 0 "Please, add details!")
      (ttalk/in-history *chat
                        [0 "Presentation description was declined. The student was informed about your decision.\n\n/lab1check"]
                        [1 "'Lab 1 presentation' description was rejected. Remark:\n\nPlease, add details!"])

      (submissions-talk 1 "/lab1submissions")
      (ttalk/in-history *chat 1
                        (str/join "\n" '("Submitted presentation in 'lgr1':"
                                         "- bla-bla-bla the best (Bot Botovich) - REJECTED"))))

    (testing "submissions-talk with specific group"
      (submissions-talk 2 "/lab1submissions")
      (ttalk/in-history *chat 2 "Please, set your 'Lab 1 presentation' group by /lab1setgroup")

      (submissions-talk 2 "/lab1submissions lgr1")
      (ttalk/in-history *chat 2 "Submitted presentation in 'lgr1':\n- bla-bla-bla the best (Bot Botovich) - REJECTED")

      (submissions-talk 2 "/lab1submissions eeeeeeeeeeeeeeeeeeeeeeeee")
      (ttalk/in-history *chat 2 "I don't know 'eeeeeeeeeeeeeeeeeeeeeeeee', you should specify one from: lgr1, lgr2")

      (submissions-talk 0 "/lab1submissions")
      (ttalk/in-history *chat 0
                        "Submitted presentation in 'lgr1':\n- bla-bla-bla the best (Bot Botovich) - REJECTED"
                        "Submitted presentation in 'lgr2':\n"))

    (testing "resubmit 2"
      (submit-talk 1 "/lab1submit")
      (submit-talk 1 "bla-bla-bla the best (second reject)")
      (submit-talk 1 "yes")
      (ttalk/in-history *chat 1 "Registered, the teacher will check it soon."))

    (testing "check and reject 2"
      (check-talk 0 "/lab1check")
      (ttalk/in-history *chat 0
                        "Wait for review: 1"
                        "Approved presentation in 'lgr1':\n"
                        "Remarks:"
                        "Please, add details!"
                        "We receive from the student (group gr1): \n\nTopic: bla-bla-bla the best (second reject)"
                        "bla-bla-bla the best (second reject)"
                        "Approve (yes or no)?")

      (check-talk 0 "no")
      (ttalk/in-history *chat 0 "OK, you need to send your remark for the student:")

      (check-talk 0 "Please, add details 2!")
      (ttalk/in-history *chat
                        [0 "Presentation description was declined. The student was informed about your decision.\n\n/lab1check"]
                        [1 "'Lab 1 presentation' description was rejected. Remark:\n\nPlease, add details 2!"])

      (submissions-talk 1 "/lab1submissions"))

    (is (= {:lab1
            {:description "bla-bla-bla the best (second reject)"
             :group "lgr1"
             :on-review? false
             :remarks '("Please, add details 2!"
                        "Please, add details!")}}
           (codax/get-at! db [1 :presentation])))

    (submit-talk 1 "/lab1submit")
    (submit-talk 1 "bla-bla-bla 2\ntext")
    (submit-talk 1 "yes")
    (ttalk/in-history *chat 1 "Registered, the teacher will check it soon.")

    (testing "Try to resubmit:"
      (submit-talk 1 "/lab1submit")
      (ttalk/in-history *chat 1 "On review, you will be informed when it is finished."))

    (check-talk 0 "/lab1check")
    (ttalk/in-history *chat 0
                      "Wait for review: 1"
                      "Approved presentation in 'lgr1':\n"
                      "Remarks:"
                      "Please, add details!"
                      "Please, add details 2!"
                      "We receive from the student (group gr1): \n\nTopic: bla-bla-bla 2"
                      "bla-bla-bla 2\ntext"
                      "Approve (yes or no)?")

    (check-talk 0 "yes")
    (ttalk/in-history *chat
                      [0 "OK, student will receive his approve.\n\n/lab1check"]
                      [1 "'Lab 1 presentation' description was approved."])

    (is (= {:lab1
            {:description "bla-bla-bla 2\ntext"
             :group "lgr1"
             :approved? true
             :on-review? false
             :remarks '("Please, add details 2!"
                        "Please, add details!")}}
           (codax/get-at! db [1 :presentation])))

    (check-talk 0 "/lab1check")
    (ttalk/in-history *chat 0 "Nothing to check.")

    (testing "Try to resubmit:"
      (submit-talk 1 "/lab1submit")
      (ttalk/in-history *chat 1 "Already submitted and approved, maybe you need to schedule it? /lab1schedule"))

    (testing "Second student"
      (register-user *chat start-talk 2 "Alice")
      (submissions-talk 2 "/lab1submissions")
      (ttalk/in-history *chat 2 "Please, set your 'Lab 1 presentation' group by /lab1setgroup")

      (setgroup-talk 2 "/lab1setgroup")
      (setgroup-talk 2 "lgr1")

      (submissions-talk 2 "/lab1submissions")
      (ttalk/in-history *chat 2
                        (str/join "\n" '("Submitted presentation in 'lgr1':"
                                         "- bla-bla-bla 2 (Bot Botovich) - APPROVED")))

      (submit-talk 2 "/lab1submit")
      (submit-talk 2 "pres 2")
      (submit-talk 2 "yes")
      (ttalk/in-history *chat 2 "Registered, the teacher will check it soon.")

      (submissions-talk 2 "/lab1submissions")
      (ttalk/in-history *chat 2
                        (str/join "\n" '("Submitted presentation in 'lgr1':"
                                         "- bla-bla-bla 2 (Bot Botovich) - APPROVED"
                                         "- pres 2 (Alice) - ON-REVIEW")))

      (check-talk 0 "/lab1check")
      (ttalk/in-history *chat
                        [0 "Wait for review: 1"]
                        [0 "Approved presentation in 'lgr1':"
                         "- bla-bla-bla 2 (Bot Botovich)"]
                        [0 "We receive from the student (group gr1): \n"
                         "Topic: pres 2"]
                        [0 "pres 2"]
                        [0 "Approve (yes or no)?"])
      (check-talk 0 "yes")
      (ttalk/in-history *chat
                        [0 "OK, student will receive his approve.\n\n/lab1check"]
                        [2 "'Lab 1 presentation' description was approved."])

      (is (= {:lab1
              {:description "pres 2"
               :group "lgr1"
               :approved? true
               :on-review? false}}
             (codax/get-at! db [2 :presentation])))

      (submissions-talk 2 "/lab1submissions")
      (ttalk/in-history *chat 2
                        (str/join "\n" '("Submitted presentation in 'lgr1':"
                                         "- bla-bla-bla 2 (Bot Botovich) - APPROVED"
                                         "- pres 2 (Alice) - APPROVED"))))))

(talk/deftest schedule-agenda-and-drop-talks-test [db *chat]
  (let [conf (misc/get-config "conf-example")
        start-talk (ttalk/mock-talk general/start-talk db conf)
        setgroup-talk (ttalk/mock-talk pres/setgroup-talk db conf "lab1")
        submit-talk (ttalk/mock-talk pres/submit-talk db conf "lab1")
        check-talk (ttalk/mock-talk pres/check-talk db conf "lab1")
        submissions-talk (ttalk/mock-talk pres/submissions-talk db conf "lab1")
        schedule-talk (ttalk/mock-talk pres/schedule-talk db conf "lab1")
        agenda-talk (ttalk/mock-talk pres/agenda-talk db conf "lab1")
        soon-talk (ttalk/mock-talk pres/soon-talk db conf "lab1")
        all-scheduled-descriptions-dump-talk (ttalk/mock-talk pres/all-scheduled-descriptions-dump-talk db conf "lab1")
        drop-talk (ttalk/mock-talk pres/drop-talk db conf "lab1" false)
        dropall-talk (ttalk/mock-talk pres/drop-talk db conf "lab1" true)]

    (register-user *chat start-talk 1 "Alice")
    (setgroup-talk 1 "/lab1setgroup")
    (setgroup-talk 1 "lgr1")
    (submit-talk 1 "/lab1submit")
    (submit-talk 1 "pres 1")
    (submit-talk 1 "yes")
    (ttalk/in-history *chat 1 "Registered, the teacher will check it soon.")

    (register-user *chat start-talk 2 "Bob")

    (testing "not registered for presentation"
      (schedule-talk 2 "/lab1schedule")
      (ttalk/in-history *chat 2 "Please, set your 'Lab 1 presentation' group by /lab1setgroup"))

    (setgroup-talk 2 "/lab1setgroup")
    (setgroup-talk 2 "lgr1")
    (submit-talk 2 "/lab1submit")
    (submit-talk 2 "pres 2")
    (submit-talk 2 "yes")
    (ttalk/in-history *chat 2 "Registered, the teacher will check it soon.")

    (testing "not checked"
      (schedule-talk 2 "/lab1schedule")
      (ttalk/in-history *chat 2 "You should submit and receive approve before scheduling. Use /lab1submit"))

    (check-talk 0 "/lab1check")
    (check-talk 0 "yes")
    (check-talk 0 "/lab1check")
    (check-talk 0 "yes")
    (check-talk 0 "/lab1check")
    (check-talk 0 "Nothing to check")

    (submissions-talk 2 "/lab1submissions")
    (ttalk/in-history *chat 2
                      (str/join "\n" '("Submitted presentation in 'lgr1':"
                                       "- pres 1 (Alice) - APPROVED"
                                       "- pres 2 (Bob) - APPROVED")))

    (testing "29 minutes before all lessons (first should hidden)"
      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:31 +0000"))]
        (schedule-talk 2 "/lab1schedule")
        (ttalk/in-history *chat 2
                          (str/join "\n" '("Select your option:"
                                           "- 2022.01.02 12:00 +0000")))))

    (testing "31 minutes before all lessons"
      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:29 +0000"))]
        (schedule-talk 2 "/lab1schedule")
        (ttalk/in-history *chat 2
                          "Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:\n"
                          "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:\n"
                          (str/join "\n" '("Select your option:"
                                           "- 2022.01.01 12:00 +0000"
                                           "- 2022.01.02 12:00 +0000")))
        (schedule-talk 2 "WRONG DATE")
        (ttalk/in-history *chat 2 (str/join "\n" '("Not found, allow only:"
                                                   "- 2022.01.01 12:00 +0000"
                                                   "- 2022.01.02 12:00 +0000")))

        (schedule-talk 2 "2022.01.01 12:00 +0000")
        (ttalk/in-history *chat 2 "OK, you can check it by: /lab1agenda")

        (submissions-talk 2 "/lab1submissions")
        (ttalk/in-history *chat 2
                          (str/join "\n" '("Submitted presentation in 'lgr1':"
                                           "- pres 1 (Alice) - APPROVED"
                                           "- pres 2 (Bob) - SCHEDULED")))

        (testing "try-to-schedule-again"
          (schedule-talk 2 "/lab1schedule")
          (ttalk/in-history *chat 2 "Already scheduled, check /lab1agenda."))

        (schedule-talk 1 "/lab1schedule")
        (ttalk/in-history *chat 1
                          (str/join "\n" '("Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:"
                                           "1. pres 2 (Bob)"))
                          "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:\n"
                          (str/join "\n" '("Select your option:"
                                           "- 2022.01.01 12:00 +0000"
                                           "- 2022.01.02 12:00 +0000")))

        (schedule-talk 1 "2022.01.02 12:00 +0000")
        (ttalk/in-history *chat 1 "OK, you can check it by: /lab1agenda")

        (agenda-talk 1 "/lab1agenda")
        (ttalk/in-history *chat 1
                          (str/join "\n" '("Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:"
                                           "1. pres 2 (Bob)"))
                          (str/join "\n" '("Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:"
                                           "1. pres 1 (Alice)")))))

    (testing "agenda show history for one day more"
      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.02 11:30 +0000"))]
        (agenda-talk 1 "/lab1agenda")
        (ttalk/in-history *chat 1
                          (str/join "\n" '("Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:"
                                           "1. pres 2 (Bob)"))
                          (str/join "\n" '("Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:"
                                           "1. pres 1 (Alice)")))

        (testing "agenda with args"
          (agenda-talk 2 "/lab1agenda lgr1")
          (ttalk/in-history *chat 2
                            (str/join "\n" '("Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:"
                                             "1. pres 2 (Bob)"))
                            (str/join "\n" '("Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:"
                                             "1. pres 1 (Alice)")))

          (agenda-talk 2 "/lab1agenda eeeeeeeeeeeeeeeeeeeeeeeee")
          (ttalk/in-history *chat 2 "I don't know 'eeeeeeeeeeeeeeeeeeeeeeeee', you should specify one from: lgr1, lgr2")

          (agenda-talk 0 "/lab1agenda")
          (ttalk/in-history *chat 0
                            (str/join "\n" '("Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:"
                                             "1. pres 2 (Bob)"))
                            (str/join "\n" '("Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:"
                                             "1. pres 1 (Alice)"))
                            "Agenda 2022.02.02 12:00 +0000 (lgr2):\n"))))

    (all-scheduled-descriptions-dump-talk 0 "/lab1descriptions")
    (ttalk/in-history *chat
                      [0 "File with all scheduled descriptions by groups:"]
                      [0
                       "# lgr1\n"
                       "## Alice\n"
                       "pres 1\n"
                       "## Bob\n"
                       "pres 2\n\n"
                       "# lgr2\n\n"])

    (testing "soon-talk"
      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:30 +0000"))]
        (soon-talk 1 "/lab1soon")
        (ttalk/in-history *chat 1
                          "We will expect for Lab 1 presentation soon:"
                          "Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:\n1. pres 2 (Bob)"
                          "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:\n1. pres 1 (Alice)"))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.02 11:30 +0000"))]
        (soon-talk 1 "/lab1soon")
        (ttalk/in-history *chat 1
                          "We will expect for Lab 1 presentation soon:"
                          "Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:\n1. pres 2 (Bob)"
                          "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:\n1. pres 1 (Alice)"))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.03 11:30 +0000"))]
        (soon-talk 1 "/lab1soon")
        (ttalk/in-history *chat 1
                          "We will expect for Lab 1 presentation soon:"
                          "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:\n1. pres 1 (Alice)"))
      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.04 11:30 +0000"))]
        (soon-talk 1 "/lab1soon")
        (ttalk/in-history *chat 1
                          "We will expect for Lab 1 presentation soon:"))
      (with-redefs [misc/today (fn [] (misc/read-time "2022.02.01 00:30 +0000"))]
        (soon-talk 1 "/lab1soon")
        (ttalk/in-history *chat 1
                          "We will expect for Lab 1 presentation soon:"
                          "Agenda 2022.02.02 12:00 +0000 (lgr2):\n")))

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

    (drop-talk 1 "/lab1drop 1")
    (ttalk/in-history *chat 1 "That action requires admin rights.")

    (drop-talk 0 "/lab1drop")
    (ttalk/in-history *chat 0 "Wrong input: /lab1drop 12345")

    (drop-talk 0 "/lab1drop asdf")
    (ttalk/in-history *chat 0 "Wrong input: /lab1drop 12345")

    (drop-talk 0 "/lab1drop 123")
    (ttalk/in-history *chat 0 "Not found.")

    (dropall-talk 0 "/lab1dropall asdf")
    (ttalk/in-history *chat 0 "Wrong input: /lab1dropall 12345")

    (drop-talk 0 "/lab1drop 1")
    (ttalk/in-history *chat 0
                      "Name: Alice; Group: gr1; Telegram ID: 1"
                      "Drop 'Lab 1 presentation' config for 1?")

    (drop-talk 0 "noooooooooooooooooooo")
    (ttalk/in-history *chat 0 "What (yes or no)?")

    (drop-talk 0 "no")
    (ttalk/in-history *chat 0 "Cancelled.")

    (drop-talk 0 "/lab1drop 1")
    (ttalk/in-history *chat 0
                      "Name: Alice; Group: gr1; Telegram ID: 1"
                      "Drop 'Lab 1 presentation' config for 1?")

    (drop-talk 0 "yes")
    (ttalk/in-history *chat
                      [0 "We drop student: 1"]
                      [1 "We drop your state for Lab 1 presentation"])

    (is (= {:lab1 {:approved? true
                   :description "pres 1"
                   :group "lgr1"
                   :on-review? false
                   :scheduled? nil}}
           (codax/get-at! db [1 :presentation])))

    (is (= {"lgr1" {"2022.01.01 12:00 +0000" {:stud-ids '(2)}
                    "2022.01.02 12:00 +0000" {:stud-ids '()}}}
           (codax/get-at! db [:presentation :lab1])))

    (dropall-talk 0 "/lab1dropall 2")
    (ttalk/in-history *chat 0
                      "Name: Bob; Group: gr1; Telegram ID: 2"
                      "Drop 'Lab 1 presentation' config for 2?")

    (dropall-talk 0 "yes")
    (ttalk/in-history *chat
                      [0 "We drop student: 2"]
                      [2 "We drop your state for Lab 1 presentation"])

    (is (= {:lab1 nil}
           (codax/get-at! db [2 :presentation])))

    (is (= {"lgr1" {"2022.01.01 12:00 +0000" {:stud-ids '()}
                    "2022.01.02 12:00 +0000" {:stud-ids '()}}}
           (codax/get-at! db [:presentation :lab1])))))

(talk/deftest feedback-and-rank-talks-test [db *chat]
  (let [conf (misc/get-config "conf-example")
        start-talk (ttalk/mock-talk general/start-talk db conf)
        setgroup-talk (ttalk/mock-talk pres/setgroup-talk db conf "lab1")
        submit-talk (ttalk/mock-talk pres/submit-talk db conf "lab1")
        check-talk (ttalk/mock-talk pres/check-talk db conf "lab1")
        schedule-talk (ttalk/mock-talk pres/schedule-talk db conf "lab1")
        agenda-talk (ttalk/mock-talk pres/agenda-talk db conf "lab1")
        feedback-talk (ttalk/mock-talk pres/feedback-talk db conf "lab1")
        report-talk (ttalk/mock-talk report/report-talk db conf
                                     "ID" report/stud-id
                                     "pres-group" (pres/report-presentation-group "lab1")
                                     "feedback-avg" (pres/report-presentation-avg-rank conf "lab1")
                                     "feedback" (pres/report-presentation-score conf "lab1")
                                     "classes" (pres/report-presentation-classes "lab1")
                                     "lesson-counter" (pres/lesson-count "lab1"))]
    (register-user *chat start-talk 1 "Alice")
    (setgroup-talk 1 "/lab1setgroup")
    (setgroup-talk 1 "lgr1")
    (submit-talk 1 "/lab1submit")
    (submit-talk 1 "pres 1")
    (submit-talk 1 "yes")
    (ttalk/in-history *chat 1 "Registered, the teacher will check it soon.")

    (register-user *chat start-talk 2 "Bob")
    (setgroup-talk 2 "/lab1setgroup")
    (setgroup-talk 2 "lgr1")
    (submit-talk 2 "/lab1submit")
    (submit-talk 2 "pres 2")
    (submit-talk 2 "yes")
    (ttalk/in-history *chat 2 "Registered, the teacher will check it soon.")

    (register-user *chat start-talk 3 "Charly")
    (setgroup-talk 3 "/lab1setgroup")
    (setgroup-talk 3 "lgr1")

    (check-talk 0 "/lab1check")
    (check-talk 0 "yes")
    (check-talk 0 "/lab1check")
    (check-talk 0 "yes")
    (check-talk 0 "/lab1check")
    (check-talk 0 "Nothing to check")

    (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:29 +0000"))]
      (schedule-talk 2 "/lab1schedule")
      (schedule-talk 2 "2022.01.01 12:00 +0000")
      (ttalk/in-history *chat 2 "OK, you can check it by: /lab1agenda")

      (schedule-talk 1 "/lab1schedule")
      (schedule-talk 1 "2022.01.01 12:00 +0000")
      (ttalk/in-history *chat 1 "OK, you can check it by: /lab1agenda")

      (agenda-talk 1 "/lab1agenda")
      (ttalk/in-history *chat 1
                        (str/join "\n" '("Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:"
                                         "1. pres 2 (Bob)"
                                         "2. pres 1 (Alice)"))
                        "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:\n"))

    (is (= {:lab1 {"lgr1" {"2022.01.01 12:00 +0000" {:stud-ids '(2 1)}}}}
           (codax/get-at! db [:presentation])))

    (testing "report without feedback"
      (report-talk 0 "/report")
      (ttalk/match-csv *chat 0
                       ["ID" "pres-group" "feedback-avg" "feedback" "classes" "lesson-counter"]
                       ["0" "" "" "" "0" "0"]
                       ["1" "lgr1" "" "4" "1" "1"]
                       ["2" "lgr1" "" "2" "1" "1"]
                       ["3" "lgr1" "" "" "1" "1"]))

    (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:29 +0000"))]
      (feedback-talk 1 "/lab1feedback")
      (ttalk/in-history *chat 1 "Feedback collecting disabled (too early or too late)."))

    (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 12:29 +0000"))]
      (feedback-talk 1 "/lab1feedback")
      (ttalk/in-history *chat 1 "Feedback collecting disabled (too early or too late)."))

    (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 15:01 +0000"))]
      (feedback-talk 1 "/lab1feedback")
      (ttalk/in-history *chat 1 "Feedback collecting disabled (too early or too late)."))

    (testing "pres group not set"
      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 12:30 +0000"))]
        (feedback-talk 4 "/lab1feedback")
        (ttalk/in-history *chat 4 "To send feedback, you should set your group for Lab 1 presentation by /lab1setgroup")))

    (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 12:30 +0000"))]
      (feedback-talk 1 "/lab1feedback")
      (ttalk/in-history *chat 1
                        "Collect feedback for 'Lab 1 presentation' (lgr1) at 2022.01.01 12:00 +0000"
                        (str/join "\n" '("Enter the number of the best presentation in the list:"
                                         "0. Bob (pres 2)"
                                         "1. Alice (pres 1)")))
      (feedback-talk 1 "0")
      (ttalk/in-history *chat 1
                        (str/join "\n" '("Enter the number of the best presentation in the list:"
                                         "0. Alice (pres 1)")))

      (feedback-talk 1 "0")
      (ttalk/in-history *chat 1 "Thanks, your feedback saved!")

      (feedback-talk 2 "/lab1feedback")
      (feedback-talk 2 "1")
      (feedback-talk 2 "0")
      (ttalk/in-history *chat 2 "Thanks, your feedback saved!")

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

      (codax/with-read-transaction [db tx]
        (is (= 1.5 (pres/avg-rank tx :lab1 1)))
        (is (= 1.5 (pres/avg-rank tx :lab1 2))))

      (testing "report"
        (report-talk 0 "/report")
        (ttalk/match-csv *chat 0
                         ["ID" "pres-group" "feedback-avg" "feedback" "classes" "lesson-counter"]
                         ["0" "" "" "" "0" "0"]
                         ["1" "lgr1" "1,5" "2" "1" "1"]
                         ["2" "lgr1" "1,5" "4" "1" "1"]
                         ["3" "lgr1" "" "" "1" "1"]
                         ["4" "" "" "" "0" "0"]))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 12:30 +0000"))]
        (feedback-talk 3 "/lab1feedback")
        (feedback-talk 3 "1")
        (feedback-talk 3 "0")
        (ttalk/in-history *chat 3 "Thanks, your feedback saved!"))

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
      (report-talk 0 "/report")
      (ttalk/match-csv *chat 0
                       ["ID" "pres-group" "feedback-avg" "feedback" "classes" "lesson-counter"]
                       ["0" "" "" "" "0" "0"]
                       ["1" "lgr1" "1,33" "4" "1" "1"]
                       ["2" "lgr1" "1,67" "2" "1" "1"]
                       ["3" "lgr1" "" "" "1" "1"]
                       ["4" "" "" "" "0" "0"]))))
