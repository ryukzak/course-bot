(ns course-bot.presentation-test
  (:require [course-bot.presentation :as pres]
            [course-bot.general :as general]
            [codax.core :as codax]
            [course-bot.misc :as misc]
            [course-bot.talk :as talk]
            [course-bot.talk-test :as ttalk]
            [clojure.test :refer :all]
            [clojure.string :as str]))

(defn register-user [*chat start-talk id name]
  (testing "register user"
    (start-talk id "/start")
    (start-talk id name)
    (start-talk id "gr1")
    (start-talk id "/start")
    (ttalk/in-history *chat [id "You are already registered. To change your information, contact the teacher and send /whoami"])))

(talk/deftest submit-without-config-talk-test [db *chat]
  (let [submit-talk-no-config (ttalk/mock-talk pres/submit-talk db "TOKEN" "noconfig")
        start-talk (ttalk/mock-talk general/start-talk db "TOKEN")]
    (submit-talk-no-config  "/noconfigsubmit")
    (ttalk/in-history *chat "Not registered. Do /start")

    (register-user *chat start-talk 1 "Bot Botovich")
    (submit-talk-no-config "/noconfigsubmit")
    (ttalk/in-history *chat "You should specify your group for presentation by /noconfigsetgroup")))

(talk/deftest submit-and-check-talk-test [db *chat]
  (let [start-talk (ttalk/mock-talk general/start-talk db "TOKEN")
        setgroup-talk (ttalk/mock-talk pres/setgroup-talk db "TOKEN" "lab1")
        submit-talk (ttalk/mock-talk pres/submit-talk db "TOKEN" "lab1")
        check-talk (ttalk/mock-talk pres/check-talk db "TOKEN" "lab1" general/assert-admin)
        submissions-talk (ttalk/mock-talk pres/submissions-talk db "TOKEN" "lab1")
        schedule-talk (ttalk/mock-talk pres/schedule-talk db "TOKEN" "lab1")
        agenda-talk (ttalk/mock-talk pres/agenda-talk db "TOKEN" "lab1")
        drop-talk (ttalk/mock-talk pres/drop-talk db "TOKEN" "lab1" general/assert-admin general/admin-chat)]

    (testing "Setup admin"
      (setgroup-talk 0 "/lab1setgroup")
      (setgroup-talk 0 "ext"))

    (register-user *chat start-talk 1 "Bot Botovich")

    (testing "Select presentation group"
      (setgroup-talk "/lab1setgroup")
      (ttalk/in-history *chat "Please, select your presentation group: ext")

      (setgroup-talk "bla-bla")
      (ttalk/in-history *chat "I don't know this group. Repeat please (ext):")

      (setgroup-talk "ext")
      (ttalk/in-history *chat "Your presentation group setted: ext")

      (setgroup-talk "/lab1setgroup")
      (ttalk/in-history *chat "Your presentation group: ext")

      (submissions-talk "/lab1submissions")
      (ttalk/in-history *chat "In group: ext:\n- UNDEFINED nil (Bot Botovich)"))

    (testing "wrong submit"
      (submit-talk "/lab1submit")
      (ttalk/in-history *chat "Please, provide description for your presentation (in one message):")

      (submit-talk "my-presentation")
      (ttalk/in-history *chat "Your description:"
                        "my-presentation"
                        "Do you approve it?")

      (submit-talk "bla-bla")
      (ttalk/in-history *chat "Please, yes or no?")

      (submit-talk "no")
      (ttalk/in-history *chat "You can do this later."))

    (testing "submit"
      (submit-talk "/lab1submit")
      (submit-talk "my-presentation")
      (submit-talk "yes")
      (ttalk/in-history *chat "Registered, the teacher will check it soon.")
      (is (= {:description "my-presentation" :on-review? true :group "ext"}
             (codax/get-at! db [1 :pres "lab1"])))

      (submissions-talk "/lab1submissions")
      (ttalk/in-history *chat "In group: ext:\n- WAIT my-presentation (Bot Botovich)")

      (submit-talk "/lab1submit")
      (ttalk/in-history *chat "On review, you will be informed when it finished."))

    (testing "try to check by student"
      (check-talk "/lab1check")
      (ttalk/in-history *chat "That action requires admin rights."))

    (testing "check by admin and reject"
      (check-talk 0 "/lab1check")
      (ttalk/in-history *chat 0 "In group: ext:\n- WAIT my-presentation (Bot Botovich)"
                        "We receive from the student (group gr1): \n\nTopic: my-presentation"
                        "my-presentation"
                        "Approve (yes or no)?")

      (check-talk 0 "bla-bla")
      (ttalk/in-history *chat 0 "Please, yes or no?")

      (check-talk 0 "no")
      (ttalk/in-history *chat 0 "OK, you need send your remark for the student:")

      (check-talk 0 "You can do it better!")
      (ttalk/in-history *chat
                        [0 "Presentation description declined. The student was informed about your decision.\n\n/lab1check"]
                        [1 "Your presentation description for lab1 declined with the following remark:\n\nYou can do it better!"])
      (is (= {:description "my-presentation" :on-review? false :group "ext"}
             (codax/get-at! db [1 :pres "lab1"])))

      (submissions-talk "/lab1submissions")
      (ttalk/in-history *chat "In group: ext:\n- ISSUE my-presentation (Bot Botovich)"))

    (testing "resubmit presentation"
      (submit-talk "/lab1submit")
      (submit-talk "my-presentation-2")
      (submit-talk "yes")
      (is (= {:description "my-presentation-2" :on-review? true :group "ext"}
             (codax/get-at! db [1 :pres "lab1"])))
      (ttalk/in-history *chat "Registered, the teacher will check it soon."))

    (testing "check by admin and reject"
      (check-talk 0 "/lab1check")
      (ttalk/in-history *chat 0 "In group: ext:\n- WAIT my-presentation-2 (Bot Botovich)"
                        "We receive from the student (group gr1): \n\nTopic: my-presentation-2"
                        "my-presentation-2"
                        "Approve (yes or no)?")

      (check-talk 0 "yes")
      (ttalk/in-history *chat [0 "OK, student will reveive his approve.\n\n/lab1check"]
                        [1 "Your presentation description for lab1 approved."])
      (is (= {:description "my-presentation-2" :on-review? false :approved? true :group "ext"}
             (codax/get-at! db [1 :pres "lab1"])))

      (submissions-talk "/lab1submissions")
      (ttalk/in-history *chat "In group: ext:\n- OK my-presentation-2 (Bot Botovich)"))

    (testing "schedule"
      (with-redefs [pres/today (fn [] (.getTime (.parse (java.text.SimpleDateFormat. "yyyy.MM.dd HH:mm") "2022.01.01 12:00")))]
        (agenda-talk "/lab1agenda")
        (ttalk/in-history *chat "2022.01.01 12:00 (ext)\n"
                          "2022.02.02 12:00 (ext)\n")

        (schedule-talk "/lab1schedule")
        (ttalk/in-history *chat "2022.01.01 12:00 (ext)\n"
                          "2022.02.02 12:00 (ext)\n"
                          "Select your option: 2022.01.01 12:00, 2022.02.02 12:00")
        (schedule-talk "bla-bla")
        (ttalk/in-history *chat "Not found, allow only: 2022.01.01 12:00, 2022.02.02 12:00")
        (schedule-talk "2022.02.02 12:00")
        (ttalk/in-history *chat "OK, you can check it by: /lab1agenda")
        (is (= {:description "my-presentation-2" :on-review? false :approved? true :scheduled? true :group "ext"}
               (codax/get-at! db [1 :pres "lab1"])))
        (is (= {"ext" {"2022.02.02 12:00" [1]}}
               (codax/get-at! db [:pres "lab1"])))

        (schedule-talk "/lab1schedule")
        (ttalk/in-history *chat "Already scheduled, check /lab1agenda.")

        (agenda-talk "/lab1agenda")
        (ttalk/in-history *chat "2022.01.01 12:00 (ext)\n"
                          "2022.02.02 12:00 (ext)\n- my-presentation-2 (Bot Botovich)"))

      (testing "drop student"
        (drop-talk "/lab1drop")
        (ttalk/in-history *chat "That action requires admin rights.")

        (drop-talk 0 "/lab1drop")
        (ttalk/in-history *chat 0 "Wrong input: /lab1drop 12345")

        (drop-talk 0 "/lab1drop bla")
        (ttalk/in-history *chat 0 "Wrong input: /lab1drop 12345")

        (drop-talk 0 "/lab1drop 1")
        (ttalk/in-history *chat 0 "Name: Bot Botovich; Group: gr1; Telegram ID: 1"
                          "Drop presentation config for lab1?")

        (drop-talk 0 "bla-bla")
        (ttalk/in-history *chat 0 "What?")

        (drop-talk 0 "no")
        (ttalk/in-history *chat 0 "Not droped.")

        (drop-talk 0 "/lab1drop 1")
        (drop-talk 0 "yes")
        (ttalk/in-history *chat [0 "Drop presentation config for lab1?"]
                          [0 "We drop student: lab1"]
                          [1 "We drop your state for lab1"])
        (is (= nil (codax/get-at! db [1 :pres "lab1"])))
        (is (= {"ext" {"2022.02.02 12:00" ()}} (codax/get-at! db [:pres "lab1"])))))))

(defn schedule-report [*chat start-talk setgroup-talk submit-talk check-talk schedule-talk id name desc dt]
  (register-user *chat start-talk id name)
  (setgroup-talk id "/lab1setgroup")
  (setgroup-talk id "ext")
  (submit-talk id "/lab1submit")
  (submit-talk id desc)
  (submit-talk id "yes")
  (check-talk 0 "/lab1check")
  (check-talk 0 "yes")
  (schedule-talk id "/lab1schedule")
  (schedule-talk id dt))

(talk/deftest submit-and-check-talk-test [db *chat]
  (let [start-talk (ttalk/mock-talk general/start-talk db "TOKEN")
        setgroup-talk (ttalk/mock-talk pres/setgroup-talk db "TOKEN" "lab1")
        submit-talk (ttalk/mock-talk pres/submit-talk db "TOKEN" "lab1")
        check-talk (ttalk/mock-talk pres/check-talk db "TOKEN" "lab1" general/assert-admin)
        submissions-talk (ttalk/mock-talk pres/submissions-talk db "TOKEN" "lab1")
        schedule-talk (ttalk/mock-talk pres/schedule-talk db "TOKEN" "lab1")
        agenda-talk (ttalk/mock-talk pres/agenda-talk db "TOKEN" "lab1")
        drop-talk (ttalk/mock-talk pres/drop-talk db "TOKEN" "lab1" general/assert-admin general/admin-chat)
        feedback-talk (ttalk/mock-talk pres/feedback-talk db "TOKEN" "lab1")]

    (testing "Setup admin"
      (setgroup-talk 0 "/lab1setgroup")
      (setgroup-talk 0 "ext"))

    (testing "registration"
      (with-redefs [pres/today (fn [] (.getTime (.parse (java.text.SimpleDateFormat. "yyyy.MM.dd HH:mm") "2022.01.01 12:00")))]
        (schedule-report *chat start-talk setgroup-talk submit-talk check-talk schedule-talk 1 "Alice" "History A" "2022.01.01 12:00")
        (schedule-report *chat start-talk setgroup-talk submit-talk check-talk schedule-talk 2 "Bob" "History B" "2022.01.01 12:00")
        (schedule-report *chat start-talk setgroup-talk submit-talk check-talk schedule-talk 3 "Charly" "History C" "2022.01.01 12:00")
        (agenda-talk 1 "/lab1agenda")
        (ttalk/in-history *chat
                          (str/join "\n" ["2022.01.01 12:00 (ext)"
                                          "- History A (Alice)"
                                          "- History B (Bob)"
                                          "- History C (Charly)"])
                          "2022.02.02 12:00 (ext)\n")))

    (testing "feedback command not available"
      (with-redefs [pres/today (fn [] (misc/read-time "2022.01.01 11:00"))]
        (feedback-talk 1 "/lab1feedback")
        (ttalk/in-history *chat "Feedback collecting is over."))
      (with-redefs [pres/today (fn [] (misc/read-time "2022.01.01 12:00"))]
        (feedback-talk 1 "/lab1feedback")
        (ttalk/in-history *chat "Feedback collecting is over."))
      (with-redefs [pres/today (fn [] (misc/read-time "2022.01.01 12:29"))]
        (feedback-talk 1 "/lab1feedback")
        (ttalk/in-history *chat "Feedback collecting is over."))
      (with-redefs [pres/today (fn [] (misc/read-time "2022.01.01 14:01"))]
        (feedback-talk 1 "/lab1feedback")
        (ttalk/in-history *chat "Feedback collecting is over."))

      (with-redefs [pres/today (fn [] (misc/read-time "2022.01.01 13:01"))]
        (feedback-talk 1 "/lab1feedback")
        (ttalk/in-history *chat  (str/join "\n"
                                           ["Enter number of the best presentation in the list:\n"
                                            "0. Alice (History A)"
                                            "1. Bob (History B)"
                                            "2. Charly (History C)"]))
        (feedback-talk 1 "3")
        (ttalk/in-history *chat "Wrong input. Enter number of the best presentation in the list.")
        (feedback-talk 1 "2")
        (ttalk/in-history *chat  (str/join "\n"
                                           ["Enter number of the best presentation in the list:\n"
                                            "0. Alice (History A)"
                                            "1. Bob (History B)"]))
        (feedback-talk 1 "0")
        (ttalk/in-history *chat  (str/join "\n"
                                           ["Enter number of the best presentation in the list:\n"
                                            "0. Bob (History B)"]))
        (feedback-talk 1 "0")
        (ttalk/in-history *chat "Thank, your feedback saved!")
        (is (= {"2022.01.01 12:00" '(1 2 3)
                :feedback-from {"2022.01.01 12:00" '(1)}
                :feedback
                {"2022.01.01 12:00"
                 '({:receive-at "2022.01.01 13:01",
                    :rank
                    [{:id 3, :name "Charly", :topic "History C"}
                     {:id 1, :name "Alice", :topic "History A"}
                     {:id 2, :name "Bob", :topic "History B"}]})}}
               (codax/get-at! db [:pres "lab1" "ext"]))))

      (with-redefs [pres/today (fn [] (misc/read-time "2022.01.01 13:02"))]
        (feedback-talk 1 "/lab1feedback")
        (ttalk/in-history *chat "Already received.")
        (feedback-talk 1 "0"))

      (with-redefs [pres/today (fn [] (misc/read-time "2022.01.01 13:02"))]
        (feedback-talk 2 "/lab1feedback")
        (feedback-talk 2 "0")
        (feedback-talk 2 "0")
        (feedback-talk 2 "0")
        (ttalk/in-history *chat 2 "Thank, your feedback saved!")
        (is (= {"2022.01.01 12:00" '(1 2 3)
                :feedback-from {"2022.01.01 12:00" '(2 1)},
                :feedback {"2022.01.01 12:00"
                           '({:receive-at "2022.01.01 13:02",
                              :rank
                              [{:id 1, :name "Alice", :topic "History A"}
                               {:id 2, :name "Bob", :topic "History B"}
                               {:id 3, :name "Charly", :topic "History C"}]}
                             {:receive-at "2022.01.01 13:01",
                              :rank
                              [{:id 3, :name "Charly", :topic "History C"}
                               {:id 1, :name "Alice", :topic "History A"}
                               {:id 2, :name "Bob", :topic "History B"}]})}}
               (codax/get-at! db [:pres "lab1" "ext"]))))

      (with-redefs [pres/today (fn [] (misc/read-time "2022.01.01 13:03"))]
        (feedback-talk 3 "/lab1feedback")
        (feedback-talk 3 "2")
        (feedback-talk 3 "1")
        (feedback-talk 3 "0")
        (ttalk/in-history *chat 3 "Thank, your feedback saved!")
        (is (= {"2022.01.01 12:00" '(1 2 3)
                :feedback-from {"2022.01.01 12:00" '(3 2 1)},
                :feedback {"2022.01.01 12:00"
                           '({:receive-at "2022.01.01 13:03",
                              :rank
                              [{:id 3, :name "Charly", :topic "History C"}
                               {:id 2, :name "Bob", :topic "History B"}
                               {:id 1, :name "Alice", :topic "History A"}]}
                             {:receive-at "2022.01.01 13:02",
                              :rank
                              [{:id 1, :name "Alice", :topic "History A"}
                               {:id 2, :name "Bob", :topic "History B"}
                               {:id 3, :name "Charly", :topic "History C"}]}
                             {:receive-at "2022.01.01 13:01",
                              :rank
                              [{:id 3, :name "Charly", :topic "History C"}
                               {:id 1, :name "Alice", :topic "History A"}
                               {:id 2, :name "Bob", :topic "History B"}]})}}

               (codax/get-at! db [:pres "lab1" "ext"])))))))
(deftest schedule-test
  (let [dt #(.getTime (.parse (java.text.SimpleDateFormat. "yyyy.MM.dd HH:mm") %))]
    (is (= 2 (count (pres/schedule "lab1" "ext" nil))))
    (is (= 2 (count (pres/schedule "lab1" "ext" -1 (dt "2022.01.01 12:00")))))
    (is (= 1 (count (pres/schedule "lab1" "ext" -1 (dt "2022.01.02 12:05")))))
    (is (= 1 (count (pres/schedule "lab1" "ext" 0.25 (dt "2022.01.01 12:00")))))
    (is (= 1 (count (pres/schedule "lab1" "ext" -1 (dt "2022.02.01 12:00")))))
    (is (= 0 (count (pres/schedule "lab1" "ext" -1 (dt "2022.03.01 12:00")))))
    (is (= 2 (count (pres/schedule "lab1" "ext" nil (dt "2022.03.01 12:00")))))))
