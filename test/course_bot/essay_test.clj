(ns course-bot.essay-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str])
  (:require [codax.core :as codax])
  (:require [course-bot.general :as general]
            [course-bot.essay :as essay]
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

(declare db *chat)

(talk/deftest essay-submit-talk-test [db *chat]
  (let [conf (misc/get-config "conf-example")
        start-talk (ttalk/mock-talk general/start-talk db conf)
        essay-submit-talk (ttalk/mock-talk essay/submit-talk db conf "essay1")
        essay-submit-2-talk (ttalk/mock-talk essay/submit-talk db conf "essay2")
        essay-status-talk (ttalk/mock-talk essay/status-talk db conf "essay1")]

    (register-user *chat start-talk 1 "u1")
    (register-user *chat start-talk 2 "u2")
    (essay-submit-talk 1 "/essay1submit")

    (ttalk/match-text *chat 1
                      "Отправьте текст эссе 'essay1' одним сообщением. Тема(-ы):"
                      ""
                      "essay1-topics-text")
    (essay-submit-talk 1 "u1 essay1 text")
    (ttalk/match-history *chat
                         (ttalk/text 1 "Текст вашего эссе\n<<<<<<<<<<<<<<<<<<<<")
                         (ttalk/text 1 "u1 essay1 text")
                         (ttalk/text 1 ">>>>>>>>>>>>>>>>>>>>")
                         (ttalk/text 1 "Загружаем (yes/no)?"))

    (testing "cancelation"
      (essay-submit-talk 1 "hmmm")
      (ttalk/match-text *chat 1 "What (yes or no)?")
      (essay-submit-talk 1 "no")
      (ttalk/match-text *chat 1 "Cancelled.")
      (= nil (codax/get-at! db [1 :essays]))

      (essay-status-talk 1 "/essay1status")
      (ttalk/match-text *chat 1
                        "Всего эссе: 0"
                        "Человек сделало ревью: 0"
                        "Есть комплект ревью на: 0 эссе."))

    (testing "submit"
      (essay-submit-talk 1 "/essay1submit")
      (essay-submit-talk 1 "u1 essay1 text")
      (ttalk/in-history *chat [1 "Загружаем (yes/no)?"])
      (essay-submit-talk 1 "yes")
      (ttalk/in-history *chat [1 "Спасибо, текст загружен и скоро попадёт на рецензирование."])
      (essay-status-talk 1 "/essay1status")
      (ttalk/in-history *chat [1 "Всего эссе: 1"
                               "Человек сделало ревью: 0"
                               "Есть комплект ревью на: 0 эссе."])
      (is (= {:text "u1 essay1 text"}
             (codax/get-at! db [1 :essays "essay1"]))))

    (testing "re-submit"
      (essay-submit-talk 1 "/essay1submit")
      (ttalk/in-history *chat [1 "Ваше эссе 'essay1' уже загружено"]))

    (testing "submit without topic"
      (essay-submit-2-talk 2 "/essay2submit")
      (ttalk/in-history *chat [2 "Отправьте текст эссе 'essay2' одним сообщением."]))))

(defn essay-submit [*chat essay-submit-talk id]
  (essay-submit-talk id "/essay1submit")
  (essay-submit-talk id (str "user" id " essay1 text"))
  (ttalk/in-history *chat [id "Загружаем (yes/no)?"])
  (essay-submit-talk id "yes")
  (ttalk/in-history *chat [id "Спасибо, текст загружен и скоро попадёт на рецензирование."]))

(talk/deftest essay-assign-review-myfeedback-talk-test [db *chat]
  (let [conf (misc/get-config "conf-example")
        start-talk (ttalk/mock-talk general/start-talk db conf)
        essay-submit-talk (ttalk/mock-talk essay/submit-talk db conf "essay1")
        essay-status-talk (ttalk/mock-talk essay/status-talk db conf "essay1")
        assignreviewers-talk (ttalk/mock-talk essay/assignreviewers-talk db conf "essay1")
        review-talk (ttalk/mock-talk essay/review-talk db conf "essay1")
        myfeedback-talk (ttalk/mock-talk essay/myfeedback-talk db conf "essay1")
        report-talk (ttalk/mock-talk report/report-talk db conf
                                     "ID" report/stud-id
                                     "review-score" (essay/review-score conf "essay1")
                                     "essay-score" (essay/essay-score conf "essay1"))]

    (testing "prepare users and their essays"
      (doall (map #(register-user *chat start-talk %1 %2)
                  [1 2 3 4]
                  ["u1" "u2" "u3" "u4"]))

      (doall (map #(essay-submit *chat essay-submit-talk %1)
                  [1 2 3 4]))

      (essay-status-talk 1 "/essay1status")
      (ttalk/in-history *chat [1 "Всего эссе: 4"
                               "Человек сделало ревью: 0"
                               "Есть комплект ревью на: 0 эссе."]))

    (testing "non-admin"
      (assignreviewers-talk 1 "/essay1assignreviewers")
      (ttalk/in-history *chat 1 "That action requires admin rights."))

    (testing "out of limit shuffels"
      (with-redefs [shuffle (fn [lst]
                              (assert (= lst (list 1 2 3 4)))
                              lst)]
        (assignreviewers-talk 0 "/essay1assignreviewers")
        (ttalk/in-history *chat 0 "ERROR: can't find assignment for some reason!")))

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
        (assignreviewers-talk 0 "/essay1assignreviewers")
        (ttalk/in-history *chat 0 "Assignment count: 4; Examples: (4 3 2)")))

    (is (= '({:request-review (4 3 2), :text "user1 essay1 text"}
             {:request-review (1 4 3), :text "user2 essay1 text"}
             {:request-review (2 1 4), :text "user3 essay1 text"}
             {:request-review (3 2 1), :text "user4 essay1 text"})
           (->> (codax/get-at! db [])
                vals
                (map #(-> % :essays (get "essay1")))
                (filter some?))))

    (testing "without essay"
      (review-talk 5 "/essay1review")
      (ttalk/in-history *chat 5 "Вам не назначено ни одно эссе. Вероятно, вы не загрузили своё эссе вовремя или поспешили с отправкой ревью."))

    (testing "make a review"
      (review-talk 1 "/essay1review")
      (ttalk/in-history *chat
                        [1 "Вам на ревью пришло: 3 эссе. Их текст сейчас отправлю ниже отдельными сообщениями."]

                        [1 "Эссе #1 <<<<<<<<<<<<<<<<<<<<"]
                        [1 "user4 essay1 text"]
                        [1 ">>>>>>>>>>>>>>>>>>>> Эссе #1"]

                        [1 "Эссе #2 <<<<<<<<<<<<<<<<<<<<"]
                        [1 "user3 essay1 text"]
                        [1 ">>>>>>>>>>>>>>>>>>>> Эссе #2"]

                        [1 "Эссе #3 <<<<<<<<<<<<<<<<<<<<"]
                        [1 "user2 essay1 text"]
                        [1 ">>>>>>>>>>>>>>>>>>>> Эссе #3"]

                        [1 ":review-msg from config"])

      (testing "review with wrong index"
        (review-talk 1 "0 bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla")
        (ttalk/in-history *chat 1 "The essay number is inconsistent or out of bounds.")
        (review-talk 1 "4 bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla")
        (ttalk/in-history *chat 1 "The essay number is inconsistent or out of bounds.")
        (review-talk 1 "bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla")
        (ttalk/in-history *chat 1 "The essay number is inconsistent or out of bounds."))

      (testing "too short feedback"
        (review-talk 1 "1 bla")
        (ttalk/in-history *chat 1 "Your feedback text is too short."))

      (testing "send reviews"
        (review-talk 1 "1 111bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla")
        (ttalk/in-history *chat 1 "ok")

        (testing "repeat one essay two times"
          (review-talk 1 "1 bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla")
          (ttalk/in-history *chat 1 "You already rate this essay."))

        (review-talk 1 "2 222bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla")
        (ttalk/in-history *chat 1 "ok")

        (review-talk 1 "3 333bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla")
        (ttalk/in-history *chat
                          [1 "ok"]
                          [1 "You have rated all the essays. Let's take a look:"]
                          [1 "The first essay -- best."
                           ""
                           "Rank: 1, essay number in the list: #1, your review: 111bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla "
                           "(few words from the essay: user4 essay1 text...)"
                           ""
                           "---"
                           ""
                           "Rank: 2, essay number in the list: #2, your review: 222bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla "
                           "(few words from the essay: user3 essay1 text...)"
                           ""
                           "---"
                           ""
                           "Rank: 3, essay number in the list: #3, your review: 333bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla "
                           "(few words from the essay: user2 essay1 text...)"
                           ""
                           "The last essay -- worst."]
                          [1 "Correct?"]))
      (with-redefs [misc/str-time (fn [_] "2022.01.03 11:30 +0000")]
        (review-talk 1 "yes"))
      (ttalk/in-history *chat 1 "Your feedback has been saved and will be available to essay writers.")

      (essay-status-talk 1 "/essay1status")
      (ttalk/in-history *chat [1 "Всего эссе: 4"
                               "Человек сделало ревью: 1"
                               "Есть комплект ревью на: 0 эссе."])

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

    (myfeedback-talk 1 "/essay1myfeedback")
    (ttalk/in-history *chat [1 "You received 0 reviews."])

    (myfeedback-talk 2 "/essay1myfeedback")
    (ttalk/in-history *chat
                      [2 "Rank: 3; Feedback: 333bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla"]
                      [2 "You received 1 reviews."])

    (testing "send review again"
      (review-talk 1 "/essay1review")
      (ttalk/in-history *chat 1 "You already sent reviews."))

    (testing "finish all reviews"
      (with-redefs [misc/str-time (fn [_] "2022.01.15 12:00 +0100")]
        (doall (map (fn [id]
                      (review-talk id "/essay1review")
                      (review-talk id (str "1 bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from " id))
                      (ttalk/in-history *chat id "ok")
                      (review-talk id (str "2 bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from " id))
                      (ttalk/in-history *chat id "ok")
                      (review-talk id (str "3 bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from " id))
                      (ttalk/in-history *chat id "Correct?")
                      (review-talk id "yes")
                      (ttalk/in-history *chat id "Your feedback has been saved and will be available to essay writers."))
                    [2 3 4]))))

    (essay-status-talk 1 "/essay1status")
    (ttalk/in-history *chat [1 "Всего эссе: 4"
                             "Человек сделало ревью: 4"
                             "Есть комплект ревью на: 4 эссе."])

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

    (myfeedback-talk 1 "/essay1myfeedback")
    (ttalk/in-history *chat
                      [1 "Rank: 3; Feedback: bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from 4"]
                      [1 "Rank: 2; Feedback: bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from 3"]
                      [1 "Rank: 1; Feedback: bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla-bla from 2"]
                      [1 "You received 3 reviews."])

    (testing "report"
      (is (= "2022.01.03 11:30 +0000"
             (codax/get-at! db [1 :essays "essay1" :my-reviews-submitted-at])))
      (is (= (-> conf :essay1 :review-deadline) "2022.01.14 12:00 +0100"))
      (is (= "2022.01.15 12:00 +0100"
             (codax/get-at! db [2 :essays "essay1" :my-reviews-submitted-at])))

      (report-talk 0 "/report")
      (ttalk/match-csv *chat 0
                       ["ID" "review-score" "essay-score"]
                       ["0" "0" "x"]
                       ["1" "3" "3"]
                       ["2" "1,5" "3"]
                       ["3" "1,5" "3"]
                       ["4" "1,5" "3"]
                       ["5" "0" "x"]))))
