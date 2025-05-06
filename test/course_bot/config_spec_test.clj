(ns course-bot.config-spec-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest testing is are]]
            [course-bot.config-spec :as config-spec]
            [course-bot.misc :as misc]))

(defn test-valid-config [file validator message]
  (let [conf (read-string (slurp file))]
    (is (nil? (validator conf)) message)))

(defn test-invalid-config [invalid-conf validator message]
  (is (string? (validator invalid-conf)) message))

(deftest config-validation-test
  (testing "Validation of correct configs"
    (test-valid-config "conf-example/csa-2023.edn" config-spec/validate-csa-config
      "Valid config should pass validation")
    (test-valid-config "conf-example/lab1.edn" config-spec/validate-lab-config
      "Valid lab config should pass validation")
    (test-valid-config "conf-example/essay1.edn" config-spec/validate-essay-config
      "Valid essay config should pass validation")
    (test-valid-config "conf-example/quiz/test-quiz.edn" config-spec/validate-quiz-config
      "Valid quiz config should pass validation"))

  (testing "Validation of incorrect configs"
    (test-invalid-config
      {:admin-chat-id "not-a-number", :token 123, :db-path 123,
       :plagiarism-path "./tmp/test/plagiarism", :groups {"gr1" {}},
       :lab1 [:inline "lab1.edn"], :essay1 [:inline "essay1.edn"],
       :quiz {:test-quiz [:inline "quiz/test-quiz.edn"]}}
      config-spec/validate-csa-config
      "Invalid config should not pass validation")

    (test-invalid-config
      {:name 123, :admins ["not-a-number"],
       :groups {"lgr1" {:lessons [{:datetime 123}]}}, :feedback-scores {1 [2]}}
      config-spec/validate-lab-config
      "Invalid lab config should not pass validation")

    (test-invalid-config
      {:topic-msg 123, :review-deadline 123, :min-length "not-a-number"}
      config-spec/validate-essay-config
      "Invalid essay config should not pass validation")

    (test-invalid-config
      {:name 123, :questions [{:ask 123, :options [{:text 123}]}]}
      config-spec/validate-quiz-config
      "Invalid quiz config should not pass validation")))

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
    (is (map? (misc/get-config "conf-example/csa-2023.edn"))
      "Complete config with embedded files should load without errors")))