(ns course-bot.internationalization-test
(:require [clojure.test :refer [deftest testing is]])
(:require [course-bot.internationalization :as i18n :refer [normalize-yes-no-text]]))

(deftest test-normalize-yes-no-text-en
  (testing "valid yes input: lowercase en"
    (is (= "yes" (normalize-yes-no-text "yes"))))

  (testing "valid yes input: mixed-case en"
    (is (= "yes" (normalize-yes-no-text "YeS"))))
  
  (testing "valid no input: lowercase en"
    (is (= "no" (normalize-yes-no-text "no"))))

  (testing "valid no input: mixed-case en"
    (is (= "no" (normalize-yes-no-text "NO"))))

  (testing "invalid input"
    (is (= "Invalid input" (normalize-yes-no-text "some incorrect input")))))

(deftest test-normalize-yes-no-text-ru
  (testing "valid yes input: lowercase ru"
    (is (= "yes" (normalize-yes-no-text "да"))))

  (testing "valid input: mixed-case ru"
    (is (= "yes" (normalize-yes-no-text "ДА"))))
  
  (testing "valid no input: lowercase ru"
    (is (= "no" (normalize-yes-no-text "нет"))))

  (testing "valid no input: mixed-case ru"
    (is (= "no" (normalize-yes-no-text "НЕТ")))))
