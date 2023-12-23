(ns course-bot.essay-test
  (:require [clojure.test :refer [deftest testing is]])
  (:require [codax.core :as codax])
  (:require [course-bot.essay :as essay]
            [course-bot.general :as general]
            [course-bot.misc :as misc]
            [course-bot.report :as report]
            [course-bot.talk-test :as tt :refer [answers?]]))

(defn register-user [*chat start-talk id name]
  (testing "register user"
    (start-talk id "/start")
    (start-talk id name)
    (start-talk id "gr1")
    (start-talk id "/start")
    (is (= (tt/history *chat :user-id id)
           ["You are already registered. To change your information, contact the teacher and send /whoami"]))))

(deftest essay-submit-talk-test
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))
        plagiarism-db (tt/test-plagiarsm-database (-> conf :plagiarism-path))

        {talk :talk, *chat :*chat}
        (tt/test-handler (general/start-talk db conf)
                         (essay/submit-talk db conf "essay1" plagiarism-db)
                         (essay/submit-talk db conf "essay2" plagiarism-db)
                         (essay/status-talk db conf "essay1"))]
    (tt/with-mocked-morse *chat
      (register-user *chat talk 1 "u1")
      (register-user *chat talk 2 "u2")
      (talk 1 "/essay1submit")
      (is (= (tt/history *chat :user-id 1)
             [(tt/unlines "Submit your essay text 'essay1' in one message. Theme(s):"
                          ""
                          "essay1-topics-text")]))

      (talk 1 "u1 essay1 text")
      (is (= (tt/history *chat :user-id 1 :number 4)
             [(tt/unlines "The text of your essay"
                          "<<<<<<<<<<<<<<<<<<<<")
              "u1 essay1 text"
              ">>>>>>>>>>>>>>>>>>>>"
              "Uploading (yes/no)?"]))

      (testing "cancelation"
        (talk 1 "hmmm")
        (is (= (tt/history *chat :user-id 1) ["Didn't understand: hmmm. Yes or no?"]))

        (talk 1 "no")
        (is (= (tt/history *chat :user-id 1) ["Cancelled."]))
        (= nil (codax/get-at! db [1 :essays]))

        (talk 1 "/essay1status")
        (is (= (tt/history *chat :user-id 1)
               [(tt/unlines "Total essays: 0"
                            "Number of people who reviewed: 0"
                            "There is a set of reviews for: 0 essays.")])))

      (testing "submit"
        (talk 1 "/essay1submit")
        (talk 1 "u1 essay1 text")
        (is (= (tt/history *chat :user-id 1) ["Uploading (yes/no)?"]))

        (talk 1 "yes")
        (is (= (tt/history *chat :user-id 1) ["Thank you, the text has been uploaded and will be submitted for review soon."]))

        (talk 1 "/essay1status")
        (is (= (tt/history *chat :user-id 1)
               [(tt/unlines "Total essays: 1"
                            "Number of people who reviewed: 0"
                            "There is a set of reviews for: 0 essays.")]))

        (is (= {:text "u1 essay1 text"}
               (codax/get-at! db [1 :essays "essay1"]))))

      (testing "re-submit"
        (talk 1 "/essay1submit")
        (is (= (tt/history *chat :user-id 1)
               [(tt/unlines "Your essay 'essay1' already uploaded.")])))

      (testing "submit without topic"
        (talk 2 "/essay2submit")
        (is (= (tt/history *chat :user-id 2)
               ["Submit your essay text 'essay2' in one message."]))))))

(defn essay-submit [*chat essay-submit-talk id]
  (essay-submit-talk id "/essay1submit")
  (essay-submit-talk id (str "user" id " essay1 text" (hash id)))
  (is (= (tt/history *chat :user-id id) ["Uploading (yes/no)?"]))

  (essay-submit-talk id "yes")
  (is (= (tt/history *chat :user-id id)
         ["Thank you, the text has been uploaded and will be submitted for review soon."])))

(deftest essay-assign-review-myfeedback-talk-test
  (let [conf (misc/get-config "conf-example/csa-2023.edn")
        db (tt/test-database (-> conf :db-path))
        plagiarism-db (tt/test-plagiarsm-database (-> conf :plagiarism-path))

        {talk :talk, *chat :*chat}
        (tt/test-handler (general/start-talk db conf)
                         (essay/submit-talk db conf "essay1" plagiarism-db)
                         (essay/submit-talk db conf "essay2" plagiarism-db)
                         (essay/status-talk db conf "essay1")
                         (essay/assignreviewers-talk db conf "essay1")
                         (essay/review-talk db conf "essay1")
                         (essay/myfeedback-talk db conf "essay1")
                         (essay/reportabuse-talk db conf "essay1")
                         (report/report-talk db conf
                                             "ID" report/stud-id
                                             "review-score" (essay/review-score conf "essay1")
                                             "essay-score" (essay/essay-score "essay1")))]

    (tt/with-mocked-morse *chat
      (testing "prepare users and their essays"
        (doall (map #(register-user *chat talk %1 %2)
                    [1 2 3 4]
                    ["u1" "u2" "u3" "u4"]))

        (doall (map #(essay-submit *chat talk %1)
                    [1 2 3 4]))

        (talk 1 "/essay1status")
        (is (= (tt/history *chat :user-id 1)
               [(tt/unlines "Total essays: 4"
                            "Number of people who reviewed: 0"
                            "There is a set of reviews for: 0 essays.")])))

      (testing "non-admin"
        (talk 1 "/essay1assignreviewers")
        (is (= (tt/history *chat :user-id 1) ["That action requires admin rights."])))

      (testing "out of limit shuffels"
        (with-redefs [shuffle (fn [lst]
                                (assert (= lst (list 1 2 3 4)))
                                lst)]
          (talk 0 "/essay1assignreviewers")
          (is (= (tt/history *chat :user-id 0)
                 ["ERROR: can't find assignment for some reason!"]))))

      (testing "check review collision"
        (is (essay/review-collision "essay1" 1 {:essays {"essay1" {:request-review '(1070936164 2 1)}}}))
        (is (essay/review-collision "essay1" 1 {:essays {"essay1" {:request-review '(1070936164 2 2)}}}))
        (is (not (essay/review-collision "essay1" 1 {:essays {"essay1" {:request-review '(1070936164 2 4)}}}))))

      (let [*shuffles (atom [[2 3 4 1] [3 4 1 2] [4 1 2 3]])]
        (with-redefs [shuffle (fn [lst]
                                (assert (= lst (list 1 2 3 4)))
                                (let [res (first @*shuffles)]
                                  (swap! *shuffles rest)
                                  res))]
          (is (answers? (talk 0 "/essay1assignreviewers")
                        "Assignment count: 4; Examples: (4 3 2)"))))

      (is (= (list {:request-review '(4 3 2), :text (str "user1 essay1 text" (hash 1))}
                   {:request-review '(1 4 3), :text (str "user2 essay1 text" (hash 2))}
                   {:request-review '(2 1 4), :text (str "user3 essay1 text" (hash 3))}
                   {:request-review '(3 2 1), :text (str "user4 essay1 text" (hash 4))})
             (->> (codax/get-at! db [])
                  vals
                  (map #(-> % :essays (get "essay1")))
                  (filter some?))))

      (testing "without essay"
        (talk 5 "/essay1review")
        (is (= (tt/history *chat :user-id 5)
               ["You have not been assigned any essays. You probably didn't upload your essay on time or rushed to submit your review."])))

      (testing "make a review"
        (talk 1 "/essay1review")
        (is (= (tt/history *chat :user-id 1 :number 11)
               ["You received: 3 essays for your review. Their text will now be sent below by selected messages."
                "Essay #1 <<<<<<<<<<<<<<<<<<<<"
                (str "user4 essay1 text" (hash 4))
                ">>>>>>>>>>>>>>>>>>>> Essay #1"

                "Essay #2 <<<<<<<<<<<<<<<<<<<<"
                (str "user3 essay1 text" (hash 3))
                ">>>>>>>>>>>>>>>>>>>> Essay #2"

                "Essay #3 <<<<<<<<<<<<<<<<<<<<"
                (str "user2 essay1 text" (hash 2))
                ">>>>>>>>>>>>>>>>>>>> Essay #3"
                ":review-msg from config"]))

        (testing "review with wrong index"
          (talk 1 "0 bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla")
          (is (= (tt/history *chat :user-id 1)
                 ["The essay number is inconsistent or out of bounds."]))

          (talk 1 "4 bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla")
          (is (= (tt/history *chat :user-id 1)
                 ["The essay number is inconsistent or out of bounds."]))

          (talk 1 "bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla")
          (is (= (tt/history *chat :user-id 1)
                 ["The essay number is inconsistent or out of bounds."])))

        (testing "too short feedback"
          (talk 1 "1 bla")
          (is (= (tt/history *chat :user-id 1)
                 ["Your feedback text is too short."])))

        (testing "send reviews"
          (talk 1 "1 111bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla")
          (is (= (tt/history *chat :user-id 1) ["ok"]))

          (testing "repeat one essay two times"
            (talk 1 "1 bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla")
            (is (= (tt/history *chat :user-id 1)
                   ["You already rate this essay."])))

          (talk 1 "2 222bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla")
          (is (= (tt/history *chat :user-id 1) ["ok"]))

          (talk 1 "3 333bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla")
          (is (= (tt/history *chat :user-id 1 :number 4)
                 ["ok"
                  "You have rated all the essays. Let's take a look:"
                  (tt/unlines "The first essay -- best."
                              ""
                              "Rank: 1, essay number in the list: #1, your review: 111bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla "
                              (str "(few words from the essay: user4 essay1 text" (hash 4) "...)")
                              ""
                              "---"
                              ""
                              "Rank: 2, essay number in the list: #2, your review: 222bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla "
                              (str "(few words from the essay: user3 essay1 text" (hash 3) "...)")
                              ""
                              "---"
                              ""
                              "Rank: 3, essay number in the list: #3, your review: 333bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla "
                              (str "(few words from the essay: user2 essay1 text" (hash 2) "...)")
                              ""
                              "The last essay -- worst.")
                  "Correct?"])))

        (with-redefs [misc/str-time (fn [_] "2022.01.03 11:30 +0000")]
          (talk 1 "yes"))
        (is (= (tt/history *chat :user-id 1)
               ["Your feedback has been saved and will be available to essay writers. If you want to report abuse, use command `/essay1reportabuse`."]))

        (talk 1 "/essay1status")
        (is (= (tt/history *chat :user-id 1)
               [(tt/unlines "Total essays: 4"
                            "Number of people who reviewed: 1"
                            "There is a set of reviews for: 0 essays.")]))

        (is (= '({:rank 3
                  :index 2
                  :essay-author 2
                  :review-author 1
                  :feedback "333bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla"}
                 {:rank 2
                  :index 1
                  :essay-author 3
                  :review-author 1
                  :feedback "222bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla"}
                 {:rank 1
                  :index 0
                  :essay-author 4
                  :review-author 1
                  :feedback "111bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla"})
               (codax/get-at! db [1 :essays "essay1" :my-reviews])))

        (is (= "2022.01.03 11:30 +0000"
               (codax/get-at! db [1 :essays "essay1" :my-reviews-submitted-at])))

        (is (= '({:rank 3
                  :index 2
                  :essay-author 2
                  :review-author 1
                  :feedback "333bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla"})
               (codax/get-at! db [2 :essays "essay1" :received-review])))

        (is (= '({:rank 2
                  :index 1
                  :essay-author 3
                  :review-author 1
                  :feedback "222bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla"})
               (codax/get-at! db [3 :essays "essay1" :received-review])))

        (is (= '({:rank 1
                  :index 0
                  :essay-author 4
                  :review-author 1
                  :feedback "111bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla"})
               (codax/get-at! db [4 :essays "essay1" :received-review]))))

      (talk 1 "/essay1myfeedback")
      (is (= (tt/history *chat :user-id 1)
             ["Review count: 0. If you want to report abuse, use command `/essay1reportabuse` (please don't use it in case of review author mistake."]))

      (talk 2 "/essay1myfeedback")
      (is (= (tt/history *chat :user-id 2 :number 2)
             ["Rank: 3; Feedback: 333bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla"
              "Review count: 1. If you want to report abuse, use command `/essay1reportabuse` (please don't use it in case of review author mistake."]))

      (testing "send review again"
        (talk 1 "/essay1review")
        (is (= (tt/history *chat :user-id 1)
               ["You already sent reviews."])))

      (testing "finish all reviews"
        (with-redefs [misc/str-time (fn [_] "2022.01.15 12:00 +0100")]
          (doall (map (fn [id]
                        (talk id "/essay1review")
                        (talk id (str "1 bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from " id))
                        (is (= (tt/history *chat :user-id id) ["ok"]))
                        (talk id (str "2 bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from " id))
                        (is (= (tt/history *chat :user-id id) ["ok"]))
                        (talk id (str "3 bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from " id))
                        (is (= (tt/history *chat :user-id id) ["Correct?"]))
                        (is (answers? (talk id "yes")
                                      "Your feedback has been saved and will be available to essay writers. If you want to report abuse, use command `/essay1reportabuse`.")))
                      [2 3 4]))))

      (talk 1 "/essay1status")
      (is (= (tt/history *chat :user-id 1)
             [(tt/unlines "Total essays: 4"
                          "Number of people who reviewed: 4"
                          "There is a set of reviews for: 4 essays.")]))

      (is (= '({:rank 3
                :index 2
                :essay-author 1
                :review-author 4
                :feedback "bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from 4"}
               {:rank 2
                :index 1
                :essay-author 1
                :review-author 3
                :feedback "bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from 3"}
               {:rank 1
                :index 0
                :essay-author 1
                :review-author 2
                :feedback "bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from 2"})
             (codax/get-at! db [1 :essays "essay1" :received-review])))

      (talk 1 "/essay1myfeedback")
      (is (= (tt/history *chat :user-id 1 :number 4)
             ["Rank: 3; Feedback: bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from 4"
              "Rank: 2; Feedback: bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from 3"
              "Rank: 1; Feedback: bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from 2"
              "Review count: 3. If you want to report abuse, use command `/essay1reportabuse` (please don't use it in case of review author mistake."]))

      (testing "report abuse"
        (is (answers? (talk 1 "/essay1reportabuse")
                      "Describe whats wrong with essay or review on your essay in one text message (with quote of problem place)?"))
        (is (answers? (talk 1 "bad-bad-bad essay, please, take a look")
                      "Your report text + reviewed essays and feedbacks will be send to the teacher. Are you sure?"))
        (is (answers? (talk 1 "yes")
                      [0 "The follwing student submit abuse report:"]
                      [0 "Name: u1; Group: gr1; Telegram ID: 1"]
                      [0 "Report text:"]
                      [0 "bad-bad-bad essay, please, take a look"]
                      [0 "Essay & author:"]
                      [0 "Name: u4; Group: gr1; Telegram ID: 4"]
                      [0 "user4 essay1 text-803074778"]
                      [0 "Essay & author:"]
                      [0 "Name: u3; Group: gr1; Telegram ID: 3"]
                      [0 "user3 essay1 text-1556392013"]
                      [0 "Essay & author:"]
                      [0 "Name: u2; Group: gr1; Telegram ID: 2"]
                      [0 "user2 essay1 text-971005196"]
                      [0 "Feedback & author:"]
                      [0 "Name: u4; Group: gr1; Telegram ID: 4"]
                      [0 "bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from 4"]
                      [0 "Feedback & author:"]
                      [0 "Name: u3; Group: gr1; Telegram ID: 3"]
                      [0 "bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from 3"]
                      [0 "Feedback & author:"]
                      [0 "Name: u2; Group: gr1; Telegram ID: 2"]
                      [0 "bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from 2"]
                      [1 "Your report was sent to the teacher. Thank you!"]))

        (talk 1 "/essay1reportabuse" "my second report" "yes")

        (is (= '(("my second report" "bad-bad-bad essay, please, take a look"))
               (->> (codax/get-at! db [])
                    vals
                    (map #(-> % :essays (get "essay1") :abuse-reports))
                    (filter some?)))))

      (testing "report"
        (is (= "2022.01.03 11:30 +0000"
               (codax/get-at! db [1 :essays "essay1" :my-reviews-submitted-at])))
        (is (= (-> conf :essay1 :review-deadline) "2022.01.14 12:00 +0100"))
        (is (= "2022.01.15 12:00 +0100"
               (codax/get-at! db [2 :essays "essay1" :my-reviews-submitted-at])))

        (talk 0 "/report")
        (tt/match-csv *chat 0
                      ["ID" "review-score" "essay-score"]
                      ["1" "3" "3"]
                      ["2" "1,5" "3"]
                      ["3" "1,5" "3"]
                      ["4" "1,5" "3"]))

      (testing "additional essays from new users"
        (doall (map #(register-user *chat talk %1 %2)
                    [5 6 7 8]
                    ["u5" "u6" "u7" "u8"]))

        (doall (map #(essay-submit *chat talk %1)
                    [5 6 7 8]))

        (let [*shuffles (atom [[6 7 8 5]
                               [7 8 5 6]
                               [8 5 6 7]])]
          (println "shuffle init")
          (with-redefs [shuffle (fn [lst]
                                  (is (= '(5 6 7 8) lst) "should be only new users")
                                  (let [res (first @*shuffles)]
                                    (swap! *shuffles rest)
                                    res))]

            (is (answers? (talk 0 "/essay1assignreviewers")
                          "Assignment count: 4; Examples: (8 7 6)"))))

        (is (= '({:my-reviews-submitted-at "2022.01.03 11:30 +0000",
                  :request-review (4 3 2),
                  :text "user1 essay1 text1392991556"}
                 {:my-reviews-submitted-at "2022.01.15 12:00 +0100",
                  :request-review (1 4 3),
                  :text "user2 essay1 text-971005196"}
                 {:my-reviews-submitted-at "2022.01.15 12:00 +0100",
                  :request-review (2 1 4),
                  :text "user3 essay1 text-1556392013"}
                 {:my-reviews-submitted-at "2022.01.15 12:00 +0100",
                  :request-review (3 2 1),
                  :text "user4 essay1 text-803074778"}
                 {:request-review (8 7 6), :text "user5 essay1 text1740791543"}
                 {:request-review (5 8 7), :text "user6 essay1 text1795257809"}
                 {:request-review (6 5 8), :text "user7 essay1 text-137604029"}
                 {:request-review (7 6 5), :text "user8 essay1 text-153401025"})
               (->> (codax/get-at! db [])
                    vals
                    (map #(-> % :essays (get "essay1")))
                    (map #(dissoc % :received-review :my-reviews :abuse-reports))
                    (filter some?)))))

      (testing "too-small-article"
        (talk 10 "/essay1submit")
        (talk 10 (str "1234"))

        (is (= (tt/history *chat :user-id 10 :number 1)
               ["Your essay text is too short, it should be at least 5 characters long."])))

      (testing "plagirism"
        (with-redefs [misc/filename-time (fn [_] "202201031130")]
          (register-user *chat talk 10 "10")
          (talk 10 "/essay1submit")
          (talk 10 (str "1 user7 essay1 text" (hash 7))))

        (is (= (tt/history *chat :user-id 10 :number 1)
               ["Your essay didn't pass plagiarism check. Your score: 24. Make it more unique!"]))

        (is (= (tt/history *chat :user-id 0 :number 1)
               [(tt/unlines
                 "Plagiarism case: 24"
                 ""
                 "origin text: 7 - essay1"
                 "uploaded text: 202201031130 - 10 - essay1")]))

        (testing "reupload self text after db broke. Aware: broken db state."
          (is (= (str "user7 essay1 text" (hash 7))
                 (codax/get-at! db [7 :essays "essay1" :text])))
          (codax/dissoc-at! db [7 :essays "essay1"])

          (with-redefs [misc/filename-time (fn [_] "202201031130")]
            (talk 7 "/essay1submit")
            (talk 7 (str "1 user7 essay1 text" (hash 7))))

          (is (= ["Uploading (yes/no)?"]
                 (tt/history *chat :user-id 7 :number 1))))))))

(deftest essay-calculate-score-test
  (testing "calculate a score based on 3 reviews"
    (is (= 4 (essay/calculate-essay-score [1 1 2])))
    (is (= 4 (essay/calculate-essay-score [1 2 2])))
    (is (= 3 (essay/calculate-essay-score [1 2 3])))
    (is (= 3 (essay/calculate-essay-score [2 3 3])))
    (is (= 2 (essay/calculate-essay-score [3 3 3])))))