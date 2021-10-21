(ns course-bot.core-test
  (:require [clojure.test :refer :all]
            [codax.core :as c]
            [morse.api :as t]
            [course-bot.talk :as b]
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

(deftest dropstudent-talk-test
  (let [out (atom "")
        test-db (c/open-database! "test-codax")]
    (c/assoc-at! test-db [admin-chat] {:name "Admin" :group "ROOT"})
    (c/assoc-at! test-db [1] {:name "Stud" :group "STUD" :lab1 {:approved? true}})
    (with-redefs [t/send-text (fn [_token id msg] (swap! out (constantly msg)) (println id msg))
                  b/send-yes-no-kbd (fn [_token id msg] (swap! out (constantly msg)) (println id "yes-or-no" msg))
                  db test-db]

      (dropstudent-talk {:message {:chat {:id admin-chat} :text "/dropstudent 42"}})
      (is (= @out "Нет такого пользователя."))

      (dropstudent-talk {:message {:chat {:id 1} :text "/dropstudent 1"}})
      (is (= @out "У вас нет таких прав."))

      (dropstudent-talk {:message {:chat {:id admin-chat} :text "/dropstudent 1"}})
      (is (= @out "Сбросим этого студента?"))
      (is (= (c/get-at! test-db [admin-chat]) {:name "Admin", :group "ROOT", :admin {:drop-student 1}}))

      (dropstudent-talk {:message {:chat {:id admin-chat} :text "blabla"}})
      (is (= @out "What?"))

      (dropstudent-talk {:message {:chat {:id admin-chat} :text "no"}})
      (is (= @out "Пускай пока остается."))
      (is (= (c/get-at! test-db [1]) {:name "Stud" :group "STUD" :lab1 {:approved? true}}))
      (is (= (c/get-at! test-db [admin-chat]) {:name "Admin", :group "ROOT", :admin {:drop-student nil}}))

      (dropstudent-talk {:message {:chat {:id admin-chat} :text "/dropstudent 1"}})
      (is (= @out "Сбросим этого студента?"))
      (is (= (c/get-at! test-db [admin-chat]) {:name "Admin", :group "ROOT", :admin {:drop-student 1}}))

      (dropstudent-talk {:message {:chat {:id admin-chat} :text "yes"}})
      (is (str/starts-with? @out "Студенту было отправлено"))
      (is (= (c/get-at! test-db [1]) {:name "Stud", :group "STUD", :lab1 {:approved? nil, :on-review? nil, :in-queue? nil}, :allow-restart true})))))
