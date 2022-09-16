(ns course-bot.presentation-test
  (:require [course-bot.presentation :as pres]
            [course-bot.general :as general]
            [codax.core :as codax]
            [course-bot.misc :as misc]
            [course-bot.talk :as talk]
            [course-bot.talk-test :as ttalk]
            [codax.core :as codax]
            [clojure.test :refer :all]
            [clojure.string :as str]))

(defn register-user [*chat start-talk id name]
  (testing "register user"
    (start-talk id "/start")
    (start-talk id name)
    (start-talk id "gr1")
    (start-talk id "/start")
    (ttalk/in-history *chat [id "You are already registered. To change your information, contact the teacher and send /whoami"])))

(declare submit-without-config-talk-test db *chat)

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
    (ttalk/in-history *chat "Your Lab 1 presentation group setted: lgr1")

    (setgroup-talk 1 "/lab1setgroup")
    (ttalk/in-history *chat "Your Lab 1 presentation group already setted: lgr1")
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
    (ttalk/in-history *chat 1 "Your Lab 1 presentation group setted: lgr1")

    (submit-talk 1 "/lab1submit")
    (ttalk/in-history *chat 1 "Please, provide description for your 'Lab 1 presentation' (in one message):")

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
        submissions-talk (ttalk/mock-talk pres/submissions-talk db conf "lab1")
        ;; schedule-talk (ttalk/mock-talk pres/schedule-talk db "TOKEN" "lab1")
        ;; agenda-talk (ttalk/mock-talk pres/agenda-talk db "TOKEN" "lab1")
        ;; drop-talk (ttalk/mock-talk pres/drop-talk db "TOKEN" "lab1" 'assert-admin 'admin-chat)
        ]
    (register-user *chat start-talk 1 "Bot Botovich")
    (setgroup-talk 1 "/lab1setgroup")
    (setgroup-talk 1 "lgr1")
    (ttalk/in-history *chat 1 "Your Lab 1 presentation group setted: lgr1")

    (check-talk 1 "/lab1check")
    (ttalk/in-history *chat 1 "That action requires admin rights.")

    (check-talk 0 "/lab1check")
    (ttalk/in-history *chat 0 "Nothing to check.")

    (submit-talk 1 "/lab1submit")
    (submit-talk 1 "bla-bla-bla the best")
    (submit-talk 1 "yes")
    (ttalk/in-history *chat 1 "Registered, the teacher will check it soon.")

    (check-talk 0 "/lab1check")
    (ttalk/in-history *chat 0
                      "Approved presentation in 'lgr1':\n"
                      "We receive from the student (group gr1): \n\nTopic: bla-bla-bla the best"
                      "bla-bla-bla the best"
                      "Approve (yes or no)?")

    (check-talk 0 "nooooooooooooo")
    (ttalk/in-history *chat 0 "Please, yes or no?")

    (check-talk 0 "no")
    (ttalk/in-history *chat 0 "OK, you need send your remark for the student:")

    (check-talk 0 "Please, add details!")
    (ttalk/in-history *chat
                      [0 "Presentation description declined. The student was informed about your decision.\n\n/lab1check"]
                      [1 "'Lab 1 presentation' description was rejected. Remark:\n\nPlease, add details!"])

    (submissions-talk 1 "/lab1submissions")
    (ttalk/in-history *chat 1
                      (str/join "\n" '("Submitted presentation in 'lgr1':"
                                       "- bla-bla-bla the best (Bot Botovich) - REJECTED")))

    (is (= {:lab1
            {:description "bla-bla-bla the best"
             :group "lgr1"
             :on-review? false}}
           (codax/get-at! db [1 :presentation])))

    (submit-talk 1 "/lab1submit")
    (submit-talk 1 "bla-bla-bla 2\ntext")
    (submit-talk 1 "yes")
    (ttalk/in-history *chat 1 "Registered, the teacher will check it soon.")

    (testing "Try to resubmit:"
      (submit-talk 1 "/lab1submit")
      (ttalk/in-history *chat 1 "On review, you will be informed when it finished."))

    (check-talk 0 "/lab1check")
    (ttalk/in-history *chat 0
                      "Approved presentation in 'lgr1':\n"
                      "We receive from the student (group gr1): \n\nTopic: bla-bla-bla 2"
                      "bla-bla-bla 2\ntext"
                      "Approve (yes or no)?")

    (check-talk 0 "yes")
    (ttalk/in-history *chat
                      [0 "OK, student will reveive his approve.\n\n/lab1check"]
                      [1 "'Lab 1 presentation' description was approved."])

    (is (= {:lab1
            {:description "bla-bla-bla 2\ntext"
             :group "lgr1"
             :approved? true
             :on-review? false}}
           (codax/get-at! db [1 :presentation])))

    (check-talk 0 "/lab1check")
    (ttalk/in-history *chat 0 "Nothing to check.")

    (testing "Try to resubmit:"
      (submit-talk 1 "/lab1submit")
      (ttalk/in-history *chat 1 "Already submitted and approved, maybe you need schedule it? /lab1schedule"))

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
      (ttalk/in-history *chat 0
                        (str/join "\n" '("Approved presentation in 'lgr1':"
                                         "- bla-bla-bla 2 (Bot Botovich)"))
                        (str/join "\n" '("We receive from the student (group gr1): \n"
                                         "Topic: pres 2"))
                        "pres 2"
                        "Approve (yes or no)?")
      (check-talk 0 "yes")
      (ttalk/in-history *chat
                        [0 "OK, student will reveive his approve.\n\n/lab1check"]
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

(talk/deftest schedule-and-agenda-talks-test [db *chat]
  (let [conf (misc/get-config "conf-example")
        start-talk (ttalk/mock-talk general/start-talk db conf)
        setgroup-talk (ttalk/mock-talk pres/setgroup-talk db conf "lab1")
        submit-talk (ttalk/mock-talk pres/submit-talk db conf "lab1")
        check-talk (ttalk/mock-talk pres/check-talk db conf "lab1")
        submissions-talk (ttalk/mock-talk pres/submissions-talk db conf "lab1")
        schedule-talk (ttalk/mock-talk pres/schedule-talk db conf "lab1")
        agenda-talk (ttalk/mock-talk pres/agenda-talk db conf "lab1")]
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
                          "Agenda:"
                          "2022.01.01 12:00 +0000 (lgr1):\n"
                          "2022.01.02 12:00 +0000 (lgr1):\n"
                          (str/join "\n" '("Select your option:"
                                           "- 2022.01.01 12:00 +0000"
                                           "- 2022.01.02 12:00 +0000")))
        (schedule-talk 2 "WRONG DATE")
        (ttalk/in-history *chat 2 (str/join "\n" '("Not found, allow only:"
                                                   "- 2022.01.01 12:00 +0000"
                                                   "- 2022.01.02 12:00 +0000")))

        (schedule-talk 2 "2022.01.01 12:00 +0000")
        (ttalk/in-history *chat 2 "OK, you can check it by: /lab1agenda")

        (testing "try-to-schedule-again"
          (schedule-talk 2 "/lab1schedule")
          (ttalk/in-history *chat 2 "Already scheduled, check /lab1agenda."))

        (schedule-talk 1 "/lab1schedule")
        (ttalk/in-history *chat 1
                          "Agenda:"
                          (str/join "\n" '("2022.01.01 12:00 +0000 (lgr1):"
                                           "1. pres 2 (Bob)"))
                          "2022.01.02 12:00 +0000 (lgr1):\n"
                          (str/join "\n" '("Select your option:"
                                           "- 2022.01.01 12:00 +0000"
                                           "- 2022.01.02 12:00 +0000")))

        (schedule-talk 1 "2022.01.02 12:00 +0000")
        (ttalk/in-history *chat 1 "OK, you can check it by: /lab1agenda")

        (agenda-talk 1 "/lab1agenda")
        (ttalk/in-history *chat 1
                          "Agenda:"
                          (str/join "\n" '("2022.01.01 12:00 +0000 (lgr1):"
                                           "1. pres 2 (Bob)"))
                          (str/join "\n" '("2022.01.02 12:00 +0000 (lgr1):"
                                           "1. pres 1 (Alice)")))))

    (testing "agenda show history for one day more"
      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.02 11:30 +0000"))]
        (agenda-talk 1 "/lab1agenda")
        (ttalk/in-history *chat 1
                          "Agenda:"
                          (str/join "\n" '("2022.01.01 12:00 +0000 (lgr1):"
                                           "1. pres 2 (Bob)"))
                          (str/join "\n" '("2022.01.02 12:00 +0000 (lgr1):"
                                           "1. pres 1 (Alice)")))))

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
           (codax/get-at! db [:presentation])))))

(talk/deftest feedback-and-rank-talks-test [db *chat]
  (let [conf (misc/get-config "conf-example")
        start-talk (ttalk/mock-talk general/start-talk db conf)
        setgroup-talk (ttalk/mock-talk pres/setgroup-talk db conf "lab1")
        submit-talk (ttalk/mock-talk pres/submit-talk db conf "lab1")
        check-talk (ttalk/mock-talk pres/check-talk db conf "lab1")
        schedule-talk (ttalk/mock-talk pres/schedule-talk db conf "lab1")
        agenda-talk (ttalk/mock-talk pres/agenda-talk db conf "lab1")
        feedback-talk (ttalk/mock-talk pres/feedback-talk db conf "lab1")]
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
                        "Agenda:"
                        (str/join "\n" '("2022.01.01 12:00 +0000 (lgr1):"
                                         "1. pres 2 (Bob)"
                                         "2. pres 1 (Alice)"))
                        "2022.01.02 12:00 +0000 (lgr1):\n"))

    (is (= {:lab1 {"lgr1" {"2022.01.01 12:00 +0000" {:stud-ids '(2 1)}}}}
           (codax/get-at! db [:presentation])))

    (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:29 +0000"))]
      (feedback-talk 1 "/lab1feedback")
      (ttalk/in-history *chat 1 "Feedback collecting disabled (too early or too late)."))

    (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 12:29 +0000"))]
      (feedback-talk 1 "/lab1feedback")
      (ttalk/in-history *chat 1 "Feedback collecting disabled (too early or too late)."))

    (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 14:01 +0000"))]
      (feedback-talk 1 "/lab1feedback")
      (ttalk/in-history *chat 1 "Feedback collecting disabled (too early or too late)."))

    (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 12:30 +0000"))]
      (feedback-talk 1 "/lab1feedback")
      (ttalk/in-history *chat 1
                        "Collect feedback for 'Lab 1 presentation' (lgr1) at 2022.01.01 12:00 +0000"
                        (str/join "\n" '("Enter number of the best presentation in the list:"
                                         "0. Bob (pres 2)"
                                         "1. Alice (pres 1)")))
      (feedback-talk 1 "0")
      (ttalk/in-history *chat 1
                        (str/join "\n" '("Enter number of the best presentation in the list:"
                                         "0. Alice (pres 1)")))

      (feedback-talk 1 "0")
      (ttalk/in-history *chat 1 "Thank, your feedback saved!")

      (feedback-talk 2 "/lab1feedback")
      (feedback-talk 2 "1")
      (feedback-talk 2 "0")
      (ttalk/in-history *chat 2 "Thank, your feedback saved!")

      (is (= {:feedback (list
                         {:receive-at (misc/normalize-time "2022.01.01 13:30 +0100")
                          :rank
                          [{:id 1, :name "Alice", :topic "pres 1"}
                           {:id 2, :name "Bob", :topic "pres 2"}]}
                         {:receive-at (misc/normalize-time "2022.01.01 13:30 +0100")
                          :rank
                          [{:id 2, :name "Bob", :topic "pres 2"}
                           {:id 1, :name "Alice", :topic "pres 1"}]})
              :feedback-from '(2 1)
              :stud-ids '(2 1)}
             (codax/get-at! db [:presentation
                                :lab1 "lgr1"
                                "2022.01.01 12:00 +0000"])))

      (codax/with-read-transaction [db tx]
        (is (= 1.5 (pres/avg-rank-score tx :lab1 1)))
        (is (= 1.5 (pres/avg-rank-score tx :lab1 2))))

      (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 12:30 +0000"))]
        (feedback-talk 3 "/lab1feedback")
        (feedback-talk 3 "1")
        (feedback-talk 3 "0")
        (ttalk/in-history *chat 3 "Thank, your feedback saved!"))

      (is (= {:feedback (list
                         {:receive-at (misc/normalize-time "2022.01.01 13:30 +0100")
                          :rank
                          [{:id 1, :name "Alice", :topic "pres 1"}
                           {:id 2, :name "Bob", :topic "pres 2"}]}
                         {:receive-at (misc/normalize-time "2022.01.01 13:30 +0100")
                          :rank
                          [{:id 1, :name "Alice", :topic "pres 1"}
                           {:id 2, :name "Bob", :topic "pres 2"}]}
                         {:receive-at (misc/normalize-time "2022.01.01 13:30 +0100")
                          :rank
                          [{:id 2, :name "Bob", :topic "pres 2"}
                           {:id 1, :name "Alice", :topic "pres 1"}]})
              :feedback-from '(3 2 1)
              :stud-ids '(2 1)}
             (codax/get-at! db [:presentation
                                :lab1 "lgr1"
                                "2022.01.01 12:00 +0000"])))

      (codax/with-read-transaction [db tx]
        (is (= 1.33 (pres/avg-rank-score tx :lab1 1)))
        (is (= 1.67 (pres/avg-rank-score tx :lab1 2)))
        (is (= nil (pres/avg-rank-score tx :lab1 3))))

      (codax/with-read-transaction [db tx]
        (is (= 4 (pres/rank-score tx conf :lab1 1)))
        (is (= 2 (pres/rank-score tx conf :lab1 2)))))

;;
    ))

;;     (testing "schedule"
;;       (with-redefs [misc/today (fn [] (.getTime (.parse (java.text.SimpleDateFormat. "yyyy.MM.dd HH:mm") "2022.01.01 12:00")))]
;;         (agenda-talk "/lab1agenda")
;;         (ttalk/in-llhistory *chat "2022.01.01 12:00 (ext)\n"
;;                           "2022.02.02 12:00 (ext)\n")

;;         (schedule-talk "/lab1schedule")
;;         (ttalk/in-history *chat "2022.01.01 12:00 (ext)\n"
;;                           "2022.02.02 12:00 (ext)\n"
;;                           "Select your option: 2022.01.01 12:00, 2022.02.02 12:00")
;;         (schedule-talk "bla-bla")
;;         (ttalk/in-history *chat "Not found, allow only: 2022.01.01 12:00, 2022.02.02 12:00")
;;         (schedule-talk "2022.02.02 12:00")
;;         (ttalk/in-history *chat "OK, you can check it by: /lab1agenda")
;;         (is (= {:description "my-presentation-2" :on-review? false :approved? true :scheduled? true :group "ext"}
;;                (codax/get-at! db [1 :pres "lab1"])))
;;         (is (= {"ext" {"2022.02.02 12:00" [1]}}
;;                (codax/get-at! db [:pres "lab1"])))

;;         (schedule-talk "/lab1schedule")
;;         (ttalk/in-history *chat "Already scheduled, check /lab1agenda.")

;;         (agenda-talk "/lab1agenda")
;;         (ttalk/in-history *chat "2022.01.01 12:00 (ext)\n"
;;                           "2022.02.02 12:00 (ext)\n- my-presentation-2 (Bot Botovich)"))

;;       (testing "drop student"
;;         (drop-talk "/lab1drop")
;;         (ttalk/in-history *chat "That action requires admin rights.")

;;         (drop-talk 0 "/lab1drop")
;;         (ttalk/in-history *chat 0 "Wrong input: /lab1drop 12345")

;;         (drop-talk 0 "/lab1drop bla")
;;         (ttalk/in-history *chat 0 "Wrong input: /lab1drop 12345")

;;         (drop-talk 0 "/lab1drop 1")
;;         (ttalk/in-history *chat 0 "Name: Bot Botovich; Group: gr1; Telegram ID: 1"
;;                           "Drop presentation config for lab1?")

;;         (drop-talk 0 "bla-bla")
;;         (ttalk/in-history *chat 0 "What?")

;;         (drop-talk 0 "no")
;;         (ttalk/in-history *chat 0 "Not droped.")

;;         (drop-talk 0 "/lab1drop 1")
;;         (drop-talk 0 "yes")
;;         (ttalk/in-history *chat [0 "Drop presentation config for lab1?"]
;;                           [0 "We drop student: lab1"]
;;                           [1 "We drop your state for lab1"])
;;         (is (= nil (codax/get-at! db [1 :pres "lab1"])))
;;         (is (= {"ext" {"2022.02.02 12:00" ()}} (codax/get-at! db [:pres "lab1"]))))
      ;; ))

;; (defn schedule-report [*chat start-talk setgroup-talk submit-talk check-talk schedule-talk id name desc dt]
;;   (register-user *chat start-talk id name)
;;   (setgroup-talk id "/lab1setgroup")
;;   (setgroup-talk id "ext")
;;   (submit-talk id "/lab1submit")
;;   (submit-talk id desc)
;;   (submit-talk id "yes")
;;   (check-talk 0 "/lab1check")
;;   (check-talk 0 "yes")
;;   (schedule-talk id "/lab1schedule")
;;   (schedule-talk id dt))

;; (talk/deftest submit-and-check-talk-test [db *chat]
;;   (let [start-talk (ttalk/mock-talk general/start-talk db "TOKEN")
;;         setgroup-talk (ttalk/mock-talk pres/setgroup-talk db "TOKEN" "lab1")
;;         submit-talk (ttalk/mock-talk pres/submit-talk db "TOKEN" "lab1")
;;         check-talk (ttalk/mock-talk pres/check-talk db "TOKEN" "lab1" general/assert-admin)
;;         schedule-talk (ttalk/mock-talk pres/schedule-talk db "TOKEN" "lab1")
;;         agenda-talk (ttalk/mock-talk pres/agenda-talk db "TOKEN" "lab1")
;;         feedback-talk (ttalk/mock-talk pres/feedback-talk db "TOKEN" "lab1")
;;         evaluate-talk (ttalk/mock-talk pres/evaluate-talk db "TOKEN" "lab1" general/assert-admin)
;;         history-talk (ttalk/mock-talk pres/history-talk db "TOKEN" "lab1")]

;;     (testing "Setup admin"
;;       (setgroup-talk 0 "/lab1setgroup")
;;       (setgroup-talk 0 "ext"))

;;     (testing "registration"
;;       (with-redefs [misc/today (fn [] (.getTime (.parse (java.text.SimpleDateFormat. "yyyy.MM.dd HH:mm") "2022.01.01 12:00")))]
;;         (schedule-report *chat start-talk setgroup-talk submit-talk check-talk schedule-talk 1 "Alice" "History A" "2022.01.01 12:00")
;;         (schedule-report *chat start-talk setgroup-talk submit-talk check-talk schedule-talk 2 "Bob" "History B" "2022.01.01 12:00")
;;         (schedule-report *chat start-talk setgroup-talk submit-talk check-talk schedule-talk 3 "Charly" "History C" "2022.01.01 12:00")
;;         (agenda-talk 1 "/lab1agenda")
;;         (ttalk/in-history *chat
;;                           (str/join "\n" ["2022.01.01 12:00 +0000 (ext)"
;;                                           "1. History A (Alice)"
;;                                           "2. History B (Bob)"
;;                                           "3. History C (Charly)"])
;;                           "2022.02.02 12:00 +0000 (ext)\n")))

;;     (testing "feedback command not available"
;;       (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:00 +0000"))]
;;         (feedback-talk 1 "/lab1feedback")
;;         (ttalk/in-history *chat "Feedback collecting is over."))
;;       (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 12:00 +0000"))]
;;         (feedback-talk 1 "/lab1feedback")
;;         (ttalk/in-history *chat "Feedback collecting is over."))
;;       (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 12:29 +0000"))]
;;         (feedback-talk 1 "/lab1feedback")
;;         (ttalk/in-history *chat "Feedback collecting is over."))
;;       (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 14:01 +0000"))]
;;         (feedback-talk 1 "/lab1feedback")
;;         (ttalk/in-history *chat "Feedback collecting is over."))

;;       (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 13:01 +0000"))]
;;         (feedback-talk 1 "/lab1feedback")
;;         (ttalk/in-history *chat  (str/join "\n"
;;                                            ["Enter number of the best presentation in the list:\n"
;;                                             "0. Alice (History A)"
;;                                             "1. Bob (History B)"
;;                                             "2. Charly (History C)"]))
;;         (feedback-talk 1 "3")
;;         (ttalk/in-history *chat "Wrong input. Enter number of the best presentation in the list.")
;;         (feedback-talk 1 "2")
;;         (ttalk/in-history *chat  (str/join "\n"
;;                                            ["Enter number of the best presentation in the list:\n"
;;                                             "0. Alice (History A)"
;;                                             "1. Bob (History B)"]))
;;         (feedback-talk 1 "0")
;;         (ttalk/in-history *chat  (str/join "\n"
;;                                            ["Enter number of the best presentation in the list:\n"
;;                                             "0. Bob (History B)"]))
;;         (feedback-talk 1 "0")
;;         (ttalk/in-history *chat "Thank, your feedback saved!")
;;         (is (= {"2022.01.01 12:00 +0000" '(1 2 3)
;;                 :feedback-from {"2022.01.01 12:00 +0000" '(1)}
;;                 :feedback
;;                 {"2022.01.01 12:00 +0000"
;;                  '({:receive-at "2022.01.01 13:01 +0000",
;;                     :rank
;;                     [{:id 3, :name "Charly", :topic "History C"}
;;                      {:id 1, :name "Alice", :topic "History A"}
;;                      {:id 2, :name "Bob", :topic "History B"}]})}}
;;                (codax/get-at! db [:pres "lab1" "ext"]))))

;;       (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 13:02 +0000"))]
;;         (feedback-talk 1 "/lab1feedback")
;;         (ttalk/in-history *chat "Already received.")
;;         (feedback-talk 1 "0"))

;;       (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 13:02 +0000"))]
;;         (feedback-talk 2 "/lab1feedback")
;;         (feedback-talk 2 "0")
;;         (feedback-talk 2 "0")
;;         (feedback-talk 2 "0")
;;         (ttalk/in-history *chat 2 "Thank, your feedback saved!")
;;         (is (= {"2022.01.01 12:00 +0000" '(1 2 3)
;;                 :feedback-from {"2022.01.01 12:00 +0000" '(2 1)},
;;                 :feedback {"2022.01.01 12:00 +0000"
;;                            '({:receive-at "2022.01.01 13:02 +0000",
;;                               :rank
;;                               [{:id 1, :name "Alice", :topic "History A"}
;;                                {:id 2, :name "Bob", :topic "History B"}
;;                                {:id 3, :name "Charly", :topic "History C"}]}
;;                              {:receive-at "2022.01.01 13:01 +0000",
;;                               :rank
;;                               [{:id 3, :name "Charly", :topic "History C"}
;;                                {:id 1, :name "Alice", :topic "History A"}
;;                                {:id 2, :name "Bob", :topic "History B"}]})}}
;;                (codax/get-at! db [:pres "lab1" "ext"]))))

;;       (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 13:03 +0000"))]
;;         (feedback-talk 3 "/lab1feedback")
;;         (feedback-talk 3 "2")
;;         (feedback-talk 3 "1")
;;         (feedback-talk 3 "0")
;;         (ttalk/in-history *chat 3 "Thank, your feedback saved!")
;;         (is (= {"2022.01.01 12:00 +0000" '(1 2 3)
;;                 :feedback-from {"2022.01.01 12:00 +0000" '(3 2 1)},
;;                 :feedback {"2022.01.01 12:00 +0000"
;;                            '({:receive-at "2022.01.01 13:03 +0000",
;;                               :rank
;;                               [{:id 3, :name "Charly", :topic "History C"}
;;                                {:id 2, :name "Bob", :topic "History B"}
;;                                {:id 1, :name "Alice", :topic "History A"}]}
;;                              {:receive-at "2022.01.01 13:02 +0000",
;;                               :rank
;;                               [{:id 1, :name "Alice", :topic "History A"}
;;                                {:id 2, :name "Bob", :topic "History B"}
;;                                {:id 3, :name "Charly", :topic "History C"}]}
;;                              {:receive-at "2022.01.01 13:01 +0000",
;;                               :rank
;;                               [{:id 3, :name "Charly", :topic "History C"}
;;                                {:id 1, :name "Alice", :topic "History A"}
;;                                {:id 2, :name "Bob", :topic "History B"}]})}}

;;                (codax/get-at! db [:pres "lab1" "ext"])))))

;;     (testing "evaluate command not available"
;;       (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 11:00 +0000"))]
;;         (evaluate-talk 1 "/lab1evaluate")
;;         (ttalk/in-history *chat "That action requires admin rights.")
;;         (evaluate-talk 0 "/lab1evaluate 2022.01.01 12:00 +0000")
;;         (ttalk/in-history *chat 0 "Enter your evaluation for:\nAlice (History A)")
;;         (evaluate-talk 0 "/lab1evaluate")
;;         (ttalk/in-history *chat 0 "Feedback collecting is over.")))

;;     (testing "evaluate"
;;       (with-redefs [misc/today (fn [] (misc/read-time "2022.01.01 13:05 +0000"))]
;;         (evaluate-talk 1 "/lab1evaluate")
;;         (ttalk/in-history *chat 1 "That action requires admin rights.")
;;         (evaluate-talk 0 "/lab1evaluate")
;;         (ttalk/in-history *chat 0 "Enter your evaluation for:\nAlice (History A)")
;;         (evaluate-talk 0 "4")
;;         (ttalk/in-history *chat 0 "Enter your evaluation for:\nBob (History B)")
;;         (evaluate-talk 0 "5")
;;         (ttalk/in-history *chat 0 "Enter your evaluation for:\nCharly (History C)")
;;         (evaluate-talk 0 "3")
;;         (ttalk/in-history *chat 0 "Please, provide list of discussion participants (comma separated):")
;;         (evaluate-talk 0 "Alice, Bob")
;;         (ttalk/in-history *chat 0 "Thank you, all data stored. If you make mistake, you can reupload it.")
;;         (is (= {"2022.01.01 12:00 +0000"
;;                 {:participants '("Alice" "Bob"),
;;                  :scores
;;                  '({:score "3", :stud {:id 3, :name "Charly", :topic "History C"}}
;;                    {:score "5", :stud {:id 2, :name "Bob", :topic "History B"}}
;;                    {:score "4", :stud {:id 1, :name "Alice", :topic "History A"}})}}
;;                (codax/get-at! db [:pres "lab1" "ext" :evaluate])))))
;;     (history-talk 1 "/lab1history")
;;     (ttalk/in-history *chat 1 "history-out.md")

;;     (is (= '(["2022.01.01 12:00 +0000" "Alice" "Alice" 1] ["2022.01.01 12:00 +0000" "Bob" "Bob" 2])
;;            (codax/with-read-transaction [db tx] (pres/participants tx "lab1"))))))

;; (deftest schedule-test
;;   (let [dt misc/read-time]
;;     (is (= 2 (count (pres/schedule "lab1" "ext" nil))))
;;     (is (= 2 (count (pres/schedule "lab1" "ext" -1 (dt "2022.01.01 12:00 +0000")))))
;;     (is (= 1 (count (pres/schedule "lab1" "ext" -1 (dt "2022.01.02 12:05 +0000")))))
;;     (is (= 1 (count (pres/schedule "lab1" "ext" 0.25 (dt "2022.01.01 12:00 +0000")))))
;;     (is (= 1 (count (pres/schedule "lab1" "ext" -1 (dt "2022.02.01 12:00 +0000")))))
;;     (is (= 0 (count (pres/schedule "lab1" "ext" -1 (dt "2022.03.01 12:00 +0000")))))
;;     (is (= 2 (count (pres/schedule "lab1" "ext" nil (dt "2022.03.01 12:00 +0000")))))))
