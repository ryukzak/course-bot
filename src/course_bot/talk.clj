(ns course-bot.talk
  (:require [codax.core :as c])
  (:require [clojure.string :as str])
  (:require [clojure.test :refer [is deftest]])
  (:require [morse.handlers :as h]))
  
(defn change-branch [tx name]
  (throw (ex-info "Change branch" {:next-branch name :tx tx})))

(defn stop-talk [tx]
  (throw (ex-info "Stop talk" {:tx tx})))

(defn wait [tx]
  (throw (ex-info "Wait talk" {:tx tx})))

(defn set-talk-branch [tx id talk branch]
  (c/assoc-at tx [id :talk] {:current-talk talk :current-branch branch}))

(defn command-args [text] (str/split (str/replace-first text #"^/\w+\s+" "") #"\s+"))

(defmacro talk [db name & body]
  (let [branches (apply hash-map body)
        current-branch-var `current-branch#
        current-talk-var `current-talk#
        msg-var `msg#
        tx-var `tx#]
    `(fn [update#]
       (let [res# (atom nil)]
         (try
           (c/with-write-transaction [~db ~tx-var]
             (let [id# (-> update# :message :from :id)
                   ~msg-var (:message update#)
                   {~current-talk-var :current-talk
                    ~current-branch-var :current-branch} (c/get-at ~tx-var [id# :talk])]
               (try
                 (let [tmp# (cond
                              (h/command? update# ~name)
                              (~(:start branches) ~tx-var ~msg-var)

                              (str/starts-with? (-> update# :message :text) "/") nil

                              ~@(apply concat
                                       (->> branches
                                            (filter #(not= :start (first %)))
                                            (map (fn [[branch body]]
                                                   [`(and (= ~current-talk-var ~name) (= ~current-branch-var ~branch))
                                                    `(~body ~tx-var ~msg-var)])))))]
                   (swap! res# (constantly tmp#))
                   (if (nil? @res#) ~tx-var @res#))
                 (catch clojure.lang.ExceptionInfo e#
                   (swap! res# (constantly :ok))
                   (case (ex-message e#)
                     "Change branch" (set-talk-branch (-> e# ex-data :tx) id# ~name (-> e# ex-data :next-branch))
                     "Stop talk" (set-talk-branch (-> e# ex-data :tx) id# nil nil)
                     "Wait talk" (-> e# ex-data :tx)
                     (throw e#))))))
           @res#)))))

(deftest test-talk-return-value
  (let [test-db (c/open-database! "test-codax")
        test-talk1 (talk test-db "cmd" :start (fn [tx _msg] (wait tx)))
        test-talk2 (talk test-db "cmd" :start (fn [_tx _msg] :ok))
        test-talk3 (talk test-db "cmd" :start (fn [_tx _msg] nil))]
    (is (= :ok (test-talk1 {:message {:text "/cmd"}})))
    (is (= nil (test-talk1 {:message {:text "bla-bla"}})))
    ;; handler should return nil value to pass
    (is (thrown? clojure.lang.ExceptionInfo (test-talk2 {:message {:text "/cmd"}})))
    (is (= nil (test-talk3 {:message {:text "/cmd"}})))))
