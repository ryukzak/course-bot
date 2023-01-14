(ns course-bot.talk-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [clojure.data.csv :as csv])
  (:require [codax.core :as codax]
            [morse.handlers :as handlers])
  (:require [course-bot.talk :as talk]))

(defn mock-talk [talk-maker & args]
  (let [do-talk (apply talk-maker args)]
    (fn ([msg] (do-talk {:message {:from {:id 1} :text msg}}))
      ([id msg] (do-talk {:message {:from {:id id} :text msg}})))))

(defn in-history [*chat & msgs]
  (let [[def-id msgs] (if (number? (first msgs)) [(first msgs) (rest msgs)] [1 msgs])
        expect (->> msgs
                    (map #(let [id (if (vector? %) (first %) def-id)
                                text (if (vector? %) (str/join "\n" (rest %)) %)]  {:id id :msg text})))
        actual (->> @*chat
                    (take (count expect))
                    reverse)]
    (is (= expect actual))))

(defn csv [tid & rows]
  (if (number? tid)
    (fn [{actual-id :id actual-msg :msg}]
      (let [actual-data (csv/read-csv actual-msg :separator \;)]
        (is (= tid actual-id) "wrong recipient")
        (is (= rows actual-data) "wrong data")))
    (apply (partial csv 1) rows)))

(defn text [tid & lines]
  (if (number? tid)
    (fn [{actual-id :id actual-msg :msg}]
      (is (= tid actual-id) "wrong recipient")
      (is (= (str/join "\n" lines) actual-msg) "wrong message"))
    (apply (partial text 1) lines)))

(defn match-history [*chat & asserts]
  (doall (map #(%1 %2)
              asserts
              (->> @*chat (take (count asserts)) reverse))))

(defn match-text [*chat tid & lines]
  (if (number? tid)
    (match-history *chat (apply (partial text tid) lines))
    (apply (partial match-text *chat 1 tid) lines)))

(defn match-csv [*chat tid & rows]
  (match-history *chat (apply (partial csv tid) rows)))

(deftest test-talk-return-value
  (let [test-db (codax/open-database! "codax-db-test")
        test-talk1 (talk/def-talk test-db "cmd" :start (fn [tx _msg] (talk/wait tx)))
        test-talk2 (talk/def-talk test-db "cmd" :start (fn [_tx _msg] :ok))
        test-talk3 (talk/def-talk test-db "cmd" :start (fn [_tx _msg] nil))]
    (is (= :ok (test-talk1 {:message {:text "/cmd"}})))
    (is (= nil (test-talk1 {:message {:text "bla-bla"}})))
    ;; handler should return nil value to pass
    (is (thrown? clojure.lang.ExceptionInfo (test-talk2 {:message {:text "/cmd"}})))
    (is (= nil (test-talk3 {:message {:text "/cmd"}})))))

(defmacro with-mocked-morse [*chat-init & body]
  (let [*chat *chat-init]
    `(with-redefs [talk/send-text (fn [token# id# msg#]
                                    (assert (= "TOKEN" token#))
                                    (swap! ~*chat conj {:id id# :msg msg#}))
                   talk/send-yes-no-kbd (fn [token# id# msg#] (swap! ~*chat conj {:id id# :msg msg#}))
                   talk/send-document (fn [token# id# file#] (swap! ~*chat conj {:id id# :msg (slurp file#)}))]
       ~@body)))

(defn test-database []
  (let [test-db "test-databases/example-database"]
    (codax/destroy-database! test-db)
    (codax/open-database! test-db)))

(defn atom? [v] (instance? clojure.lang.Atom v))

(defn handlers [& handlers]
  (fn rec
    ([msg] (rec nil 1 msg))
    ([*chat-or-id msg]
     (if (atom? *chat-or-id)
       (rec *chat-or-id 1 msg)
       (rec nil *chat-or-id msg)))
    ([_*chat id msg] (let [handler (apply handlers/handlers handlers)]
                       (handler {:message {:from {:id id} :text msg}})))))
