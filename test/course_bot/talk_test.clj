(ns course-bot.talk-test
  (:require [clojure.test :refer :all]
            [course-bot.talk :as talk]
            [codax.core :as c]))

(deftest test-talk-return-value
  (let [test-db (c/open-database! "talk-test-codax")
        test-talk1 (talk/def-talk test-db "cmd" :start (fn [tx _msg] (talk/wait tx)))
        test-talk2 (talk/def-talk test-db "cmd" :start (fn [_tx _msg] :ok))
        test-talk3 (talk/def-talk test-db "cmd" :start (fn [_tx _msg] nil))]
    (is (= :ok (test-talk1 {:message {:text "/cmd"}})))
    (is (= nil (test-talk1 {:message {:text "bla-bla"}})))
    ;; handler should return nil value to pass
    (is (thrown? clojure.lang.ExceptionInfo (test-talk2 {:message {:text "/cmd"}})))
    (is (= nil (test-talk3 {:message {:text "/cmd"}})))))
