(ns course-bot.config-spec-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest testing is are]]
            [course-bot.config-spec :as config-spec]
            [course-bot.misc :as misc]))

(defn test-valid-config [file validator]
  (let [conf (read-string (slurp file))]
    (is (nil? (validator conf)))))

(defn test-invalid-config [invalid-conf validator]
  (is (string? (validator invalid-conf))))

(deftest config-validation-test
  (testing "Validation of correct configs"
    (test-valid-config "conf-example/csa-2023.edn" config-spec/validate-csa-config)
    (test-valid-config "conf-example/lab1.edn" config-spec/validate-lab-config)
    (test-valid-config "conf-example/essay1.edn" config-spec/validate-essay-config)
    (test-valid-config "conf-example/quiz/test-quiz.edn" config-spec/validate-quiz-config))

  (testing "Validation of incorrect configs"
    (test-invalid-config
      {:admin-chat-id "not-a-number", :token 123, :db-path 123,
       :plagiarism-path "./tmp/test/plagiarism", :groups {"gr1" {}},
       :lab1 [:inline "lab1.edn"], :essay1 [:inline "essay1.edn"],
       :quiz {:test-quiz [:inline "quiz/test-quiz.edn"]}}
      config-spec/validate-csa-config)

    (test-invalid-config
      {:name 123, :admins ["not-a-number"],
       :groups {"lgr1" {:lessons [{:datetime 123}]}}, :feedback-scores {1 [2]}}
      config-spec/validate-lab-config)

    (test-invalid-config
      {:topic-msg 123, :review-deadline 123, :min-length "not-a-number"}
      config-spec/validate-essay-config)

    (test-invalid-config
      {:name 123, :questions [{:ask 123, :options [{:text 123}]}]}
      config-spec/validate-quiz-config)))

(deftest missing-required-fields-test
  (testing "Missing required fields in CSA config"
    (test-invalid-config
      {:admin-chat-id 100}
      config-spec/validate-csa-config)

    (test-invalid-config
      {:admin-chat-id 100, :token "TOKEN"}
      config-spec/validate-csa-config)

    (test-invalid-config
      {:admin-chat-id 100, :token "TOKEN", :db-path "./path", :plagiarism-path "./path"}
      config-spec/validate-csa-config)

    (test-invalid-config
      {:admin-chat-id 100, :token "TOKEN", :db-path "./path",
       :plagiarism-path "./path", :groups {"gr1" {}}}
      config-spec/validate-csa-config))

  (testing "Missing required fields in lab config"
    (test-invalid-config
      {:name "Lab Test"}
      config-spec/validate-lab-config)

    (test-invalid-config
      {:name "Lab Test", :admins [1, 2]}
      config-spec/validate-lab-config)

    (test-invalid-config
      {:name "Lab Test", :admins [1, 2], :groups {"lgr1" {:lessons [{:datetime "2022.01.01 12:00 +0000"}]}}}
      config-spec/validate-lab-config)))

(deftest specific-validation-rules-test
  (testing "CSA config validation rules"
    (test-invalid-config
      {:admin-chat-id 100, :token "TOKEN", :db-path "./path",
       :plagiarism-path "./path", :groups {"gr1" {}},
       :lab1 "not-inline-format", :essay1 [:inline "essay1.edn"],
       :quiz {:test-quiz [:inline "quiz/test-quiz.edn"]}}
      config-spec/validate-csa-config)

    (test-invalid-config
      {:admin-chat-id 100, :token "TOKEN", :db-path "./path",
       :plagiarism-path "./path", :groups {"gr1" {}},
       :lab1 [:inline "lab1.edn"], :essay1 [:inline "essay1.edn"],
       :quiz "not-a-map"}
      config-spec/validate-csa-config))

  (testing "Lab group and datetime validation"
    (is (not (s/valid? :course-bot.config-spec/group {:comment "ABC"})))

    (is (not (s/valid? :course-bot.config-spec/datetime 123)))

    (let [lab-config-with-group-missing-lessons
          {:name "Lab Test", :admins [1, 2],
           :groups {"lgr1" {:comment "ABC"}},
           :feedback-scores {1 [2]}}

          lab-config-with-non-string-datetime
          {:name "Lab Test", :admins [1, 2],
           :groups {"lgr1" {:lessons [{:datetime 123}]}},
           :feedback-scores {1 [2]}}]

      (test-invalid-config
        lab-config-with-group-missing-lessons
        config-spec/validate-lab-config)

      (test-invalid-config
        lab-config-with-non-string-datetime
        config-spec/validate-lab-config)))

  (testing "Lab feedback scores validation"
    (test-invalid-config
      {:name "Lab Test", :admins [1, 2],
       :groups {"lgr1" {:lessons [{:datetime "2022.01.01 12:00 +0000"}]}},
       :feedback-scores {1 "not-a-vector"}}
      config-spec/validate-lab-config))

  (testing "Essay config validation rules"
    (test-invalid-config
      {:topic-msg "Essay topic",
       :review-deadline "2022.01.14 12:00 +0100",
       :min-length -5}
      config-spec/validate-essay-config)

    (test-invalid-config
      {:topic-msg "Essay topic",
       :review-deadline "2022.01.14 12:00 +0100",
       :min-length 0}
      config-spec/validate-essay-config)

    (is (nil? (config-spec/validate-essay-config
                {:topic-msg "Essay topic",
                 :review-deadline "2022.01.14 12:00 +0100",
                 :min-length 1}))))

  (testing "Quiz config validation rules"
    (test-invalid-config
      {:name "Quiz Test", :questions [{}]}
      config-spec/validate-quiz-config)

    (let [quiz-with-empty-options
          {:name "Quiz Test",
           :questions [{:ask "Question?", :options []}]}]

      (test-invalid-config
        quiz-with-empty-options
        config-spec/validate-quiz-config))

    (let [valid-quiz-with-one-option
          {:name "Quiz Test",
           :questions [{:ask "Question?", :options [{:text "Option 1"}]}]}]

      (is (nil? (config-spec/validate-quiz-config valid-quiz-with-one-option))))

    (test-invalid-config
      {:name "Quiz Test", :questions [{:ask "Question?", :options [{:text "Option"}]}],
       :ignore-in-score "not-a-boolean"}
      config-spec/validate-quiz-config)))

(deftest lost-and-found-test
  (testing "Lost and found structure validation"
    (let [valid-lost-and-found {"lgr1"
                                {:lessons
                                 [{:datetime "2022.01.01 12:00 +0000"
                                   :presentations [{:stud-id 1 :text "aaa"}
                                                   {:stud-id 2 :text "bbb"}]}]}}
          invalid-presentations {"lgr1"
                                 {:lessons
                                  [{:datetime "2022.01.01 12:00 +0000"
                                    :presentations [{:stud-id "not-a-number" :text "aaa"}]}]}}
          missing-presentations {"lgr1"
                                 {:lessons
                                  [{:datetime "2022.01.01 12:00 +0000"}]}}
          invalid-lesson-format {"lgr1"
                                 {:lessons
                                  "not-a-vector"}}]

      (is (nil? (config-spec/validate-lab-config
                  {:name "Lab Test", :admins [1, 2],
                   :groups {"lgr1" {:lessons [{:datetime "2022.01.01 12:00 +0000"}]}},
                   :feedback-scores {1 [2]},
                   :lost-and-found valid-lost-and-found})))

      (test-invalid-config
        {:name "Lab Test", :admins [1, 2],
         :groups {"lgr1" {:lessons [{:datetime "2022.01.01 12:00 +0000"}]}},
         :feedback-scores {1 [2]},
         :lost-and-found invalid-presentations}
        config-spec/validate-lab-config)

      (test-invalid-config
        {:name "Lab Test", :admins [1, 2],
         :groups {"lgr1" {:lessons [{:datetime "2022.01.01 12:00 +0000"}]}},
         :feedback-scores {1 [2]},
         :lost-and-found missing-presentations}
        config-spec/validate-lab-config)

      (test-invalid-config
        {:name "Lab Test", :admins [1, 2],
         :groups {"lgr1" {:lessons [{:datetime "2022.01.01 12:00 +0000"}]}},
         :feedback-scores {1 [2]},
         :lost-and-found invalid-lesson-format}
        config-spec/validate-lab-config))))

(deftest edge-cases-test
  (testing "Edge cases for configs"
    (let [valid-lab-empty-admins
          {:name "Lab Test", :admins [],
           :groups {"lgr1" {:lessons [{:datetime "2022.01.01 12:00 +0000"}]}},
           :feedback-scores {1 [2]}}]

      (is (nil? (config-spec/validate-lab-config valid-lab-empty-admins))))

    (let [valid-lab-empty-groups
          {:name "Lab Test", :admins [1, 2],
           :groups {},
           :feedback-scores {1 [2]}}]

      (is (nil? (config-spec/validate-lab-config valid-lab-empty-groups))))

    (test-valid-config
      "conf-example/lab1.edn"
      config-spec/validate-lab-config)))

(deftest test-validation-fn-test
  (testing "test-validation function with auto-detection"
    (with-redefs [println (constantly nil)]
      (are [file result] (= (config-spec/test-validation file) result)
        "conf-example/csa-2023.edn" true
        "conf-example/lab1.edn" true
        "conf-example/essay1.edn" true
        "conf-example/quiz/test-quiz.edn" true))))

(deftest complete-config-validation-test
  (testing "Loading complete config with embedded files"
    (is (map? (misc/get-config "conf-example/csa-2023.edn")))))

(deftest explicit-type-validation-test
  (testing "test-validation with explicit type parameter"
    (with-redefs [println (constantly nil)]
      (are [file type result] (= (config-spec/test-validation file type) result)
        "conf-example/csa-2023.edn" :csa true
        "conf-example/lab1.edn" :lab true
        "conf-example/essay1.edn" :essay true
        "conf-example/quiz/test-quiz.edn" :quiz true
        "conf-example/lab1.edn" :csa false ; wrong type
        "conf-example/essay1.edn" :lab false ; wrong type
        "conf-example/csa-2023.edn" :unknown false))))