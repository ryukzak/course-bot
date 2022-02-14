(ns course-bot.csa.lab1-test
  (:require [clojure.test :refer :all]
            [codax.core :as c]
            [morse.api :as t]
            [course-bot.talk :as b]
            [course-bot.csa.lab1 :refer :all]
            [clojure.string :as str]
            [course-bot.core :refer :all]))

(deftest lab1-score-test
  (is (= (lab1-score 1 nil) '(0.0)))
  (is (= (lab1-score 2 [[1 "dt" "211"] [2 "dt" "12"]]) '(5.0 4.0)))
  (is (= (lab1-score 2 [[1 "dt" "no"] [2 "dt" "12"]]) '(5.0 4.0)))
  (is (= (lab1-score 2 [[1 "dt" "12"] [1 "dt" "21"]]) '(5.0 4.0)))
  (is (= (lab1-score 2 [[1 "dt" "12"] [2 "dt" "213"]]) '(4.5 4.5)))
  (is (= (lab1-score 2 [[1 "dt" "12"] [1 "dt" "21"]]) '(5.0 4.0)))
  (is (= (lab1-score 2 [[1 "dt" "12"] [2 "dt" "21"]]) '(4.5 4.5)))
  (is (= (lab1-score 4 [[1 "dt" "1234"] [2 "dt" "1234"]]) '(5.0 4.0 3.0 3.0))))
