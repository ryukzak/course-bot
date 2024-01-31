(ns course-bot.presentation-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing is]])
  (:require [codax.core :as codax])
  (:require [course-bot.general :as general]
            [course-bot.misc :as misc]
            [course-bot.presentation :as pres]
            [course-bot.report :as report]
            [course-bot.talk-test :as tt :refer [answers?]]))

(defn register-user [_*chat talk id name]
  ; TODO: migrate to register-user-2 with talk/test-handler
  (testing "register user"
    (talk id "/start")
    (talk id name)
    (talk id "gr1")
    (is (answers? (talk id "/start")
                  "You are already registered. To change your information, contact the teacher and send /whoami"))))

(defn register-user-2 [talk stud-id name pres-group]
  (testing "register user"
    (is (answers? (talk stud-id "/start" name)
                  "Hi, I'm a bot for your course. I will help you with your work. What is your name (like in the registry)?"
                  "What is your group (gr1, gr2)?"))
    (is (answers? (talk stud-id "gr1")
                  (str "Hi, " name "!")
                  (str "Name: " name "; Group: gr1; Telegram ID: " stud-id)
                  "Send /help for help."))
    (talk stud-id "/lab1setgroup")
    (is (answers? (talk stud-id pres-group)
                  (str "Your Lab 1 presentation group set: " pres-group)))))

(deftest setgroup-talk-test
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))

        {talk :talk, *chat :*chat}
        (tt/test-handler (general/start-talk db conf)
                         (pres/setgroup-talk db conf "lab1"))]
    (tt/with-mocked-morse *chat
      (register-user *chat talk 1 "Bot Botovich")
      (is (answers? (talk 1 "/lab1setgroup")
                    "Please, select your Lab 1 presentation group: lgr1, lgr2"))
      (is (answers? (talk 1 "miss")
                    "I don't know this group. Try again (lgr1, lgr2)"))
      (is (answers? (talk 1 "lgr1")
                    "Your Lab 1 presentation group set: lgr1"))
      (is (answers? (talk 1 "/lab1setgroup")
                    "Your Lab 1 presentation group is already set: lgr1"))
      (is (= {:lab1 {:group "lgr1"}} (codax/get-at! db [1 :presentation]))))))

(deftest submit-talk-test
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))

        {talk :talk, *chat :*chat}
        (tt/test-handler (general/start-talk db conf)
                         (pres/setgroup-talk db conf "lab1")
                         (pres/submit-talk db conf "lab1"))]
    (tt/with-mocked-morse *chat

      (register-user *chat talk 1 "Bot Botovich")
      (is (answers? (talk 1 "/lab1submit")
                    "Please, set your 'Lab 1 presentation' group by /lab1setgroup"))
      (talk 1 "/lab1setgroup")
      (is (answers? (talk 1 "lgr1")
                    "Your Lab 1 presentation group set: lgr1"))
      (is (answers? (talk 1 "/lab1submit")
                    "hint"))
      (testing "too long description. After fail -- just send smaller text"
        (is (answers? (talk 1 "bla-bla-bla zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz")
                      "Description is too long, max length is 50.")))

      (talk 1 "bla-bla-bla the best")
      (tt/match-history *chat
                        (tt/text 1 "Your description:")
                        (tt/text 1 "bla-bla-bla the best")
                        (tt/text 1 "Do you approve it?"))

      (is (answers? (talk 1 "noooooooooooooo")
                    "Didn't understand: noooooooooooooo. Yes or no?"))
      (is (answers? (talk 1 "no")
                    "You can do this later."))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:34 +0000"))]
        (talk 1 "/lab1submit")
        (talk 1 "bla-bla-bla the best")
        (is (answers? (talk 1 "yes")
                      "Registered, the teacher will check it soon."))
        (is (= {:lab1
                {:description "bla-bla-bla the best"
                 :group "lgr1"
                 :history '({:date "2022.01.01 11:34 +0000", :action :submit})
                 :on-review? true}}
               (codax/get-at! db [1 :presentation])))))))

(deftest check-and-submissions-talks-test
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))

        {talk :talk, *chat :*chat}
        (tt/test-handler (general/start-talk db conf)
                         (pres/setgroup-talk db conf "lab1")
                         (pres/submit-talk db conf "lab1")
                         (pres/check-talk db conf "lab1")
                         (pres/submissions-talk db conf "lab1"))]
    (tt/with-mocked-morse *chat
      (register-user *chat talk 1 "Bot Botovich")
      (talk 1 "/lab1setgroup")
      (is (answers? (talk 1 "lgr1")
                    "Your Lab 1 presentation group set: lgr1"))
      (is (answers? (talk 1 "/lab1check")
                    "That action requires admin rights."))
      (is (answers? (talk 0 "/lab1check")
                    "Nothing to check."))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:30 +0000"))]
        (talk 1 "/lab1submit")
        (talk 1 "bla-bla-bla the best")
        (is (answers? (talk 1 "yes")
                      "Registered, the teacher will check it soon.")))

      (testing "check and reject"
        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:31 +0000"))]
          (talk 0 "/lab1check")
          (tt/match-history *chat
                            (tt/text 0 "Wait for review: 1")
                            (tt/text 0 "Approved presentation in 'lgr1':\n")
                            (tt/text 0 "We receive from the student (group gr1):\n\nTopic: bla-bla-bla the best")
                            (tt/text 0 "bla-bla-bla the best")
                            (tt/text 0 "Approve (yes or no)?"))

          (is (answers? (talk 0 "nooooooooooooo")
                        "Didn't understand: nooooooooooooo. Yes or no?"))
          (is (answers? (talk 0 "no")
                        "OK, you need to send your remark for the student:"))
          (talk 0 "Please, add details!")
          (tt/match-history *chat
                            (tt/text 0 "Presentation description was declined. The student was informed about your decision.\n\n/lab1check")
                            (tt/text 1 "'Lab 1 presentation' description was rejected. Remark:\n\nPlease, add details!"))

          (talk 1 "/lab1submissions")
          (tt/match-history *chat
                            (tt/text 1 "Submitted presentation in 'lgr1':"
                                     "- bla-bla-bla the best (Bot Botovich) - REJECTED"))))

      (testing "submissions-talk with specific group"
        (is (answers? (talk 2 "/lab1submissions")
                      "Please, set your 'Lab 1 presentation' group by /lab1setgroup"))
        (is (answers? (talk 2 "/lab1submissions lgr1")
                      "Submitted presentation in 'lgr1':\n- bla-bla-bla the best (Bot Botovich) - REJECTED"))
        (is (answers? (talk 2 "/lab1submissions eeeeeeeeeeeeeeeeeeeeeeeee")
                      "I don't know 'eeeeeeeeeeeeeeeeeeeeeeeee', you should specify one from: lgr1, lgr2"))
        (talk 0 "/lab1submissions")
        (tt/match-history *chat
                          (tt/text 0 "Submitted presentation in 'lgr1':\n- bla-bla-bla the best (Bot Botovich) - REJECTED")
                          (tt/text 0 "Submitted presentation in 'lgr2':\n")))

      (testing "resubmit 2"
        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:32 +0000"))]
          (talk 1 "/lab1submit")
          (talk 1 "bla-bla-bla the best (second reject)")
          (is (answers? (talk 1 "yes")
                        "Registered, the teacher will check it soon."))))

      (testing "check and reject 2"
        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:33 +0000"))]
          (is (answers? (talk 0 "/lab1check")
                        "Wait for review: 1"
                        "Approved presentation in 'lgr1':"
                        "Remarks:"
                        "Please, add details!"
                        "We receive from the student (group gr1):\n\nTopic: bla-bla-bla the best (second reject)"
                        "bla-bla-bla the best (second reject)"
                        "Approve (yes or no)?"))

          (is (answers? (talk 0 "no")
                        "OK, you need to send your remark for the student:"))
          (talk 0 "Please, add details 2!")
          (tt/match-history *chat
                            (tt/text 0 "Presentation description was declined. The student was informed about your decision.\n\n/lab1check")
                            (tt/text 1 "'Lab 1 presentation' description was rejected. Remark:\n\nPlease, add details 2!"))

          (talk 1 "/lab1submissions")))

      (is (= {:lab1
              {:description "bla-bla-bla the best (second reject)"
               :group "lgr1"
               :history
               '({:date "2022.01.01 11:33 +0000", :action :reject}
                 {:date "2022.01.01 11:32 +0000", :action :submit}
                 {:date "2022.01.01 11:31 +0000", :action :reject}
                 {:date "2022.01.01 11:30 +0000", :action :submit})
               :on-review? false
               :remarks '("Please, add details 2!"
                          "Please, add details!")}}
             (codax/get-at! db [1 :presentation])))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:34 +0000"))]
        (talk 1 "/lab1submit")
        (talk 1 "bla-bla-bla 2\ntext")
        (is (answers? (talk 1 "yes")
                      "Registered, the teacher will check it soon."))
        (testing "Try to resubmit:"
          (is (answers? (talk 1 "/lab1submit")
                        "On review, you will be informed when it is finished.")))

        (talk 0 "/lab1check")
        (tt/match-history *chat
                          (tt/text 0 "Wait for review: 1")
                          (tt/text 0 "Approved presentation in 'lgr1':\n")
                          (tt/text 0 "Remarks:")
                          (tt/text 0 "Please, add details!")
                          (tt/text 0 "Please, add details 2!")
                          (tt/text 0 "We receive from the student (group gr1):\n\nTopic: bla-bla-bla 2")
                          (tt/text 0 "bla-bla-bla 2\ntext")
                          (tt/text 0 "Approve (yes or no)?"))

        (talk 0 "yes")
        (tt/match-history *chat
                          (tt/text 0 "OK, student will receive his approve.\n\n/lab1check")
                          (tt/text 1 "'Lab 1 presentation' description was approved.")))

      (is (= {:lab1
              {:description "bla-bla-bla 2\ntext"
               :group "lgr1"
               :history
               '({:date "2022.01.01 11:34 +0000", :action :approve}
                 {:date "2022.01.01 11:34 +0000", :action :submit}
                 {:date "2022.01.01 11:33 +0000", :action :reject}
                 {:date "2022.01.01 11:32 +0000", :action :submit}
                 {:date "2022.01.01 11:31 +0000", :action :reject}
                 {:date "2022.01.01 11:30 +0000", :action :submit})
               :approved? true
               :on-review? false
               :remarks '("Please, add details 2!"
                          "Please, add details!")}}
             (codax/get-at! db [1 :presentation])))

      (is (answers? (talk 0 "/lab1check")
                    "Nothing to check."))
      (testing "Try to resubmit:"
        (is (answers? (talk 1 "/lab1submit")
                      "Already submitted and approved, maybe you need to schedule it? /lab1schedule")))

      (testing "Second student"
        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:35 +0000"))]
          (register-user *chat talk 2 "Alice")
          (is (answers? (talk 2 "/lab1submissions")
                        "Please, set your 'Lab 1 presentation' group by /lab1setgroup"))
          (talk 2 "/lab1setgroup")
          (talk 2 "lgr1")

          (talk 2 "/lab1submissions")
          (tt/match-history *chat
                            (tt/text 2 "Submitted presentation in 'lgr1':"
                                     "- bla-bla-bla 2 (Bot Botovich) - APPROVED"))

          (talk 2 "/lab1submit")
          (talk 2 "pres 2")
          (is (answers? (talk 2 "yes")
                        "Registered, the teacher will check it soon."))
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
                            (tt/text 0 "We receive from the student (group gr1):\n"
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
                   :history
                   '({:date "2022.01.01 11:35 +0000", :action :approve}
                     {:date "2022.01.01 11:35 +0000", :action :submit})
                   :approved? true
                   :on-review? false}}
                 (codax/get-at! db [2 :presentation])))

          (is (answers? (talk 2 "/lab1submissions")
                        (str/join "\n" (quote ("Submitted presentation in 'lgr1':" "- bla-bla-bla 2 (Bot Botovich) - APPROVED" "- pres 2 (Alice) - APPROVED"))))))))))

(deftest schedule-agenda-and-drop-talks-test
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))

        {talk :talk, *chat :*chat}
        (tt/test-handler (general/start-talk db conf)
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
      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:30 +0000"))]
        (register-user *chat talk 1 "Alice")
        (talk 1 "/lab1setgroup"
              "lgr1"
              "/lab1submit"
              "pres 1")
        (is (answers? (talk 1 "yes")
                      "Registered, the teacher will check it soon."))
        (register-user *chat talk 2 "Bob")

        (testing "not registered for presentation"
          (is (answers? (talk 2 "/lab1schedule")
                        "Please, set your 'Lab 1 presentation' group by /lab1setgroup")))

        (talk 2 "/lab1setgroup"
              "lgr1"
              "/lab1submit"
              "pres 2")
        (is (answers? (talk 2 "yes")
                      "Registered, the teacher will check it soon."))
        (testing "not checked"
          (is (answers? (talk 2 "/lab1schedule")
                        "You should submit and receive approve before scheduling. Use /lab1submit"))))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.02 11:30 +0000"))]
        (talk 0 "/lab1check")
        (talk 0 "yes")
        (talk 0 "/lab1check")
        (talk 0 "yes")
        (is (answers?
             (talk 0 "/lab1check")
             "Nothing to check.")))

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

          (is (answers? (talk 2 "2022.01.01 12:00 +0000")
                        "OK, you can check it by: /lab1agenda"))
          (talk 2 "/lab1submissions")
          (tt/match-history *chat
                            (tt/text 2 "Submitted presentation in 'lgr1':"
                                     "- pres 1 (Alice) - APPROVED"
                                     "- pres 2 (Bob) - SCHEDULED"))

          (testing "try-to-schedule-again"
            (is (answers? (talk 2 "/lab1schedule")
                          "Already scheduled, check /lab1agenda.")))

          (talk 1 "/lab1schedule")
          (tt/match-history *chat
                            (tt/text 1 "Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:"
                                     "1. pres 2 (Bob)")
                            (tt/text 1 "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:\n")
                            (tt/text 1 "Select your option:"
                                     "- 2022.01.01 12:00 +0000"
                                     "- 2022.01.02 12:00 +0000"))

          (is (answers? (talk 1 "2022.01.02 12:00 +0000")
                        "OK, you can check it by: /lab1agenda"))
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

            (is (answers? (talk 2 "/lab1agenda eeeeeeeeeeeeeeeeeeeeeeeee")
                          "I don't know 'eeeeeeeeeeeeeeeeeeeeeeeee', you should specify one from: lgr1, lgr2"))
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
          (is (= (tt/history *chat :user-id 1 :number 3)
                 ["We will expect for Lab 1 presentation soon:"
                  (tt/unlines "Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:"
                              "1. [Bob | 2022.01.01 | Лаб. | АК-2023 | ПИиКТ | Университет ИТМО]()"
                              "    1. pres 2 (Bob)")
                  (tt/unlines "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:"
                              "1. [Alice | 2022.01.02 | Лаб. | АК-2023 | ПИиКТ | Университет ИТМО]()"
                              "    1. pres 1 (Alice)")])))

        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.02 11:30 +0000"))]
          (talk 1 "/lab1soon")
          (is (= (tt/history *chat :user-id 1 :number 3)
                 ["We will expect for Lab 1 presentation soon:"
                  (tt/unlines "Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:"
                              "1. [Bob | 2022.01.01 | Лаб. | АК-2023 | ПИиКТ | Университет ИТМО]()"
                              "    1. pres 2 (Bob)")
                  (tt/unlines "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:"
                              "1. [Alice | 2022.01.02 | Лаб. | АК-2023 | ПИиКТ | Университет ИТМО]()"
                              "    1. pres 1 (Alice)")])))

        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.03 11:30 +0000"))]
          (talk 1 "/lab1soon")
          (is (= (tt/history *chat :user-id 1 :number 2)
                 ["We will expect for Lab 1 presentation soon:"
                  (tt/unlines "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:"
                              "1. [Alice | 2022.01.02 | Лаб. | АК-2023 | ПИиКТ | Университет ИТМО]()"
                              "    1. pres 1 (Alice)")])))

        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.04 11:30 +0000"))]
          (talk 1 "/lab1soon")
          (is (= (tt/history *chat :user-id 1)
                 ["We will expect for Lab 1 presentation soon:"])))

        (with-redefs [misc/today (fn [] (misc/read-time "2022.02.01 00:30 +0000"))]
          (talk 1 "/lab1soon")
          (is (= (tt/history *chat :user-id 1 :number 2)
                 ["We will expect for Lab 1 presentation soon:"
                  (tt/unlines "Agenda 2022.02.02 12:00 +0000 (lgr2):"
                              "1. [ | 2022.02.02 | Лаб. | АК-2023 | ПИиКТ | Университет ИТМО]()")]))))

      (is (= {:lab1 {:approved? true
                     :description "pres 1"
                     :group "lgr1"
                     :history
                     '({:date "2022.01.02 11:30 +0000", :action :approve}
                       {:date "2022.01.01 11:30 +0000", :action :submit})
                     :on-review? false
                     :scheduled? true}}
             (codax/get-at! db [1 :presentation])))

      (is (= {:lab1 {:approved? true
                     :description "pres 2"
                     :group "lgr1"
                     :history
                     '({:date "2022.01.02 11:30 +0000", :action :approve}
                       {:date "2022.01.01 11:30 +0000", :action :submit})
                     :on-review? false
                     :scheduled? true}}
             (codax/get-at! db [2 :presentation])))

      (is (= {:lab1 {"lgr1" {"2022.01.01 12:00 +0000" {:stud-ids '(2)}
                             "2022.01.02 12:00 +0000" {:stud-ids '(1)}}}}
             (codax/get-at! db [:presentation])))

      (is (answers? (talk 1 "/lab1drop 1")
                    "That action requires admin rights."))
      (is (answers? (talk 0 "/lab1drop")
                    "Wrong input: /lab1drop 12345"))
      (is (answers? (talk 0 "/lab1drop asdf")
                    "Wrong input: /lab1drop 12345"))
      (is (answers? (talk 0 "/lab1drop 123")
                    "Not found."))
      (is (answers? (talk 0 "/lab1dropall asdf")
                    "Wrong input: /lab1dropall 12345"))
      (is (answers? (talk 0 "/lab1drop 1")
                    "Name: Alice; Group: gr1; Telegram ID: 1"
                    (tt/unlines
                     "{:approved? true,"
                     " :group \"lgr1\","

                     " :history"
                     " ({:date \"2022.01.02 11:30 +0000\", :action :approve}"
                     "  {:date \"2022.01.01 11:30 +0000\", :action :submit}),"

                     " :on-review? false,"
                     " :scheduled? true,"
                     " :topic \"pres 1\"}")
                    "[\"2022.01.02 12:00 +0000\" {:stud-ids (1)}]"
                    "Drop 'Lab 1 presentation' config for 1?"))

      (is (answers? (talk 0 "noooooooooooooooooooo")
                    "Didn't understand: noooooooooooooooooooo. Yes or no?"))
      (is (answers? (talk 0 "no")
                    "Cancelled."))

      (is (answers? (talk 0 "/lab1drop 1")
                    "Name: Alice; Group: gr1; Telegram ID: 1"
                    (tt/unlines
                     "{:approved? true,"
                     " :group \"lgr1\","

                     " :history"
                     " ({:date \"2022.01.02 11:30 +0000\", :action :approve}"
                     "  {:date \"2022.01.01 11:30 +0000\", :action :submit}),"

                     " :on-review? false,"
                     " :scheduled? true,"
                     " :topic \"pres 1\"}")
                    "[\"2022.01.02 12:00 +0000\" {:stud-ids (1)}]"
                    "Drop 'Lab 1 presentation' config for 1?"))

      (talk 0 "yes")
      (tt/match-history *chat
                        (tt/text 0 "We drop student: 1")
                        (tt/text 1 "We drop your state for Lab 1 presentation"))

      (is (= {:lab1 {:approved? true
                     :description "pres 1"
                     :group "lgr1"
                     :history
                     '({:date "2022.01.02 11:30 +0000", :action :approve}
                       {:date "2022.01.01 11:30 +0000", :action :submit})
                     :on-review? false
                     :scheduled? nil}}
             (codax/get-at! db [1 :presentation])))

      (is (= {"lgr1" {"2022.01.01 12:00 +0000" {:stud-ids '(2)}
                      "2022.01.02 12:00 +0000" {:stud-ids '()}}}
             (codax/get-at! db [:presentation :lab1])))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:39 +0000"))]
        (is (answers? (talk 0 "/lab1dropall 2")
                      "Name: Bob; Group: gr1; Telegram ID: 2"
                      (tt/unlines
                       "{:approved? true,"
                       " :group \"lgr1\","

                       " :history"
                       " ({:date \"2022.01.02 11:30 +0000\", :action :approve}"
                       "  {:date \"2022.01.01 11:30 +0000\", :action :submit}),"

                       " :on-review? false,"
                       " :scheduled? true,"
                       " :topic \"pres 2\"}")
                      "[\"2022.01.01 12:00 +0000\" {:stud-ids (2)}]"
                      "Drop 'Lab 1 presentation' config for 2?"))

        (talk 0 "yes")
        (tt/match-history *chat
                          (tt/text 0 "We drop student: 2")
                          (tt/text 2 "We drop your state for Lab 1 presentation")))

      (is (= {:lab1 {:history
                     '({:date "2022.01.01 11:39 +0000", :action :drop}
                       {:date "2022.01.02 11:30 +0000", :action :approve}
                       {:date "2022.01.01 11:30 +0000", :action :submit})}}
             (codax/get-at! db [2 :presentation])))

      (is (= {"lgr1" {"2022.01.01 12:00 +0000" {:stud-ids '()}
                      "2022.01.02 12:00 +0000" {:stud-ids '()}}}
             (codax/get-at! db [:presentation :lab1]))))))

(deftest feedback-and-rank-talks-test
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))

        {talk :talk *chat :*chat}
        (tt/test-handler (general/start-talk db conf)
                         (pres/setgroup-talk db conf "lab1")
                         (pres/submit-talk db conf "lab1")
                         (pres/check-talk db conf "lab1")
                         (pres/schedule-talk db conf "lab1")
                         (pres/agenda-talk db conf "lab1")
                         (pres/feedback-talk db conf "lab1")
                         (report/report-talk db conf
                                             "ID" report/stud-id
                                             "pres-group" (pres/report-presentation-group "lab1")
                                             "feedback-avg" (pres/report-presentation-avg-rank "lab1")
                                             "feedback" (pres/report-presentation-score conf "lab1")
                                             "classes" (pres/report-presentation-classes "lab1")
                                             "lesson-counter" (pres/lesson-count "lab1")))]

    (tt/with-mocked-morse *chat
      (register-user *chat talk 1 "Alice")
      (talk 1 "/lab1setgroup")
      (talk 1 "lgr1")
      (talk 1 "/lab1submit")
      (talk 1 "pres 1")
      (is (answers? (talk 1 "yes")
                    "Registered, the teacher will check it soon."))
      (register-user *chat talk 2 "Bob")
      (talk 2 "/lab1setgroup")
      (talk 2 "lgr1")
      (talk 2 "/lab1submit")
      (talk 2 "pres 2")
      (is (answers? (talk 2 "yes")
                    "Registered, the teacher will check it soon."))
      (register-user *chat talk 3 "Charly")
      (talk 3 "/lab1setgroup")
      (talk 3 "lgr1")

      (talk 0 "/lab1check")
      (talk 0 "yes")
      (talk 0 "/lab1check")
      (talk 0 "yes")
      (talk 0 "/lab1check")
      (talk 0 "Nothing to check")

      (is (answers? (talk 1 "/lab1feedback 2022.01.02 12:00 +0000")
                    "No presentations."))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:29 +0000"))]
        (talk 2 "/lab1schedule")
        (is (answers? (talk 2 "2022.01.01 12:00 +0000")
                      "OK, you can check it by: /lab1agenda"))
        (talk 1 "/lab1schedule")
        (is (answers? (talk 1 "2022.01.01 12:00 +0000")
                      "OK, you can check it by: /lab1agenda"))
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
                      ["1" "lgr1" "" "4" "1" "1"]
                      ["2" "lgr1" "" "2" "1" "1"]
                      ["3" "lgr1" "" "" "1" "1"]))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:29 +0000"))
                    misc/str-time misc/str-time-in-utc]
        (is (answers? (talk 1 "/lab1feedback")
                      (tt/unlines "Lesson feedback is not available. Your lab1 group: lgr1. Now: 2022.01.01 11:29 +0000. Expected feedback dates:"
                                  "- 2022.01.01 12:30 +0000 -- 2022.01.01 15:00 +0000"
                                  "- 2022.01.02 12:30 +0000 -- 2022.01.02 15:00 +0000"))))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 12:29 +0000"))]
        (is (answers? (talk 1 "/lab1feedback")
                      (tt/unlines
                       "Use format: /lab1feedback [<datetime>]"
                       ""
                       "You need to specify lesson datetime explicitly:"
                       "- 2022.01.01 12:00 +0000"))))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 15:01 +0000"))]
        (is (answers? (talk 1 "/lab1feedback")
                      (tt/unlines
                       "Use format: /lab1feedback [<datetime>]"
                       ""
                       "You need to specify lesson datetime explicitly:"
                       "- 2022.01.01 12:00 +0000"))))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.10 12:29 +0000"))]
        (is (answers? (talk 1 "/lab1feedback")
                      (tt/unlines
                       "Use format: /lab1feedback [<datetime>]"
                       ""
                       "You need to specify lesson datetime explicitly:"
                       "- 2022.01.01 12:00 +0000"
                       "- 2022.01.02 12:00 +0000"))))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.10 12:29 +0000"))]
        (is (answers? (talk 1 "/lab1feedback 2022.01.01 12:00 +0000")
                      "Collect feedback for 'Lab 1 presentation' (lgr1) at 2022.01.01 12:00 +0000"
                      (tt/unlines
                       "Enter the number of the best presentation in the list:"
                       "0. Bob (pres 2)"
                       "1. Alice (pres 1)"))))

      (testing "feedback on future presentations"
        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 09:00 +0000"))]
          (is (answers? (talk 1 "/lab1feedback 2022.01.01 12:00 +0000")
                        "You can't give a feedback to the future lesson."))))

      (testing "pres group not set"
        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 12:30 +0000"))]
          (is (answers? (talk 4 "/lab1feedback")
                        "To send feedback, you should set your group for Lab 1 presentation by /lab1setgroup"))))

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

        (is (answers? (talk 1 "0")
                      "Thanks, your feedback saved!"))
        (talk 2 "/lab1feedback")
        (talk 2 "1")
        (is (answers? (talk 2 "0")
                      "Thanks, your feedback saved!"))
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
                        ["1" "lgr1" "1,5" "2" "1" "1"]
                        ["2" "lgr1" "1,5" "4" "1" "1"]
                        ["3" "lgr1" "" "" "1" "1"]))

        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 12:30 +0000"))]
          (talk 3 "/lab1feedback")
          (talk 3 "1")
          (is (answers? (talk 3 "0")
                        "Thanks, your feedback saved!"))
          (is (answers? (talk 3 "/lab1feedback")
                        "Already received.")))

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
                      ["1" "lgr1" "1,33" "4" "1" "1"]
                      ["2" "lgr1" "1,67" "2" "1" "1"]
                      ["3" "lgr1" "" "" "1" "1"])))))

(deftest lost-and-found-talks-test
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))

        {talk :talk *chat :*chat}
        (tt/test-handler (general/start-talk db conf)
                         (pres/lost-and-found-talk db conf "lab1")
                         (pres/setgroup-talk db conf "lab1")
                         (pres/submit-talk db conf "lab1")
                         (pres/drop-talk db conf "lab1" false)
                         (pres/check-talk db conf "lab1")
                         (pres/schedule-talk db conf "lab1")
                         (pres/agenda-talk db conf "lab1")
                         (pres/feedback-talk db conf "lab1")
                         (report/report-talk db conf
                                             "ID" report/stud-id
                                             "pres-group" (pres/report-presentation-group "lab1")
                                             "feedback-avg" (pres/report-presentation-avg-rank "lab1")
                                             "feedback" (pres/report-presentation-score conf "lab1")
                                             "classes" (pres/report-presentation-classes "lab1")
                                             "lesson-counter" (pres/lesson-count "lab1")))]
    (tt/with-mocked-morse *chat
      (register-user-2 talk 1 "Alice" "lgr1")
      (register-user-2 talk 2 "Bob" "lgr1")
      (register-user-2 talk 3 "Charly" "lgr1")

      (with-redefs [misc/today (fn [] (misc/read-time "2021.01.01 10:00 +0000"))]
        (talk 1 "/lab1submit")
        (talk 1 "pres 1")
        (is (answers? (talk 1 "yes")
                      "Registered, the teacher will check it soon."))

        (talk 2 "/lab1submit")
        (talk 2 "pres 2")
        (is (answers? (talk 2 "yes")
                      "Registered, the teacher will check it soon."))

        (talk 3 "/lab1submit")
        (talk 3 "pres 3")
        (is (answers? (talk 3 "yes")
                      "Registered, the teacher will check it soon."))

        (talk 0 "/lab1check")
        (is (answers? (talk 0 "yes")
                      [0 (tt/unlines "OK, student will receive his approve."
                                     ""
                                     "/lab1check")]
                      [1 "'Lab 1 presentation' description was approved."])))

      (is (answers? (talk 1 "/lab1lostandfound")
                    "That action requires admin rights."))

      (testing "schedule 1 lesson and check for conflict"
        (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:00 +0000"))]
          (is (answers? (talk 1 "/lab1schedule")
                        "Agenda 2022.01.01 12:00 +0000 (lgr1), ABC:"
                        "Agenda 2022.01.02 12:00 +0000 (lgr1), ABC:"
                        (tt/unlines "Select your option:"
                                    "- 2022.01.01 12:00 +0000"
                                    "- 2022.01.02 12:00 +0000")))
          (is (answers? (talk 1 "2022.01.01 12:00 +0000")
                        "OK, you can check it by: /lab1agenda"))

          (is (answers? (talk 0 "/lab1lostandfound")
                        (tt/unlines
                         "{:pres-group \"lgr1\","
                         " :datetime \"2022.01.01 12:00 +0000\","
                         " :current-state {:stud-ids (1)},"
                         " :collision true,"
                         " :lost-state"
                         " ({:id 1, :topic \"aaa\", :name \"Alice\", :pres-group \"lgr1\"}"
                         "  {:id 2, :topic \"bbb\", :name \"Bob\", :pres-group \"lgr1\"}"
                         "  {:id 3, :topic \"ccc\", :name \"Charly\", :pres-group \"lgr1\"})}")
                        "Collision between lost-and-found lessons and scheduled lessons. Canceled."))

          (is (answers? (talk 0 "/lab1drop 1" "yes")
                        [0 "Name: Alice; Group: gr1; Telegram ID: 1"]
                        [0 (tt/unlines
                            "{:approved? true,"
                            " :group \"lgr1\","

                            " :history"
                            " ({:date \"2021.01.01 10:00 +0000\", :action :approve}"
                            "  {:date \"2021.01.01 10:00 +0000\", :action :submit}),"

                            " :on-review? false,"
                            " :scheduled? true,"
                            " :topic \"pres 1\"}")]
                        [0 "[\"2022.01.01 12:00 +0000\" {:stud-ids (1)}]"]
                        [0 "Drop 'Lab 1 presentation' config for 1?"]
                        [0 "We drop student: 1"]
                        [1 "We drop your state for Lab 1 presentation"]))))

      (is (answers? (talk 0 "/lab1lostandfound")
                    (tt/unlines
                     "{:pres-group \"lgr1\","
                     " :datetime \"2022.01.01 12:00 +0000\","
                     " :current-state {:stud-ids ()},"
                     " :collision false,"
                     " :lost-state"
                     " ({:id 1, :topic \"aaa\", :name \"Alice\", :pres-group \"lgr1\"}"
                     "  {:id 2, :topic \"bbb\", :name \"Bob\", :pres-group \"lgr1\"}"
                     "  {:id 3, :topic \"ccc\", :name \"Charly\", :pres-group \"lgr1\"})}")
                    "Restore lost-and-found lessons?"))

      (is (answers? (talk 0 "yes")
                    "Lost-and-found lessons restored."))

      (is (answers? (talk 1 "/lab1feedback 2022.01.01 12:00 +0000"
                          "2"
                          "1"
                          "0")
                    "Collect feedback for 'Lab 1 presentation' (lgr1) at 2022.01.01 12:00 +0000"
                    (tt/unlines "Enter the number of the best presentation in the list:"
                                "0. Alice (aaa)"
                                "1. Bob (bbb)"
                                "2. Charly (ccc)")
                    (tt/unlines "Enter the number of the best presentation in the list:"
                                "0. Alice (aaa)"
                                "1. Bob (bbb)")
                    (tt/unlines "Enter the number of the best presentation in the list:"
                                "0. Alice (aaa)")
                    "Thanks, your feedback saved!"))

      (testing "report"
        (talk 0 "/report")
        (tt/match-csv *chat 0
                      ["ID" "pres-group" "feedback-avg" "feedback" "classes" "lesson-counter"]
                      ["1" "lgr1" "3,0" "2" "1" "1"]
                      ["2" "lgr1" "2,0" "4" "1" "1"]
                      ["3" "lgr1" "1,0" "6" "1" "1"])))))
