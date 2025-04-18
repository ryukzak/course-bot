(ns course-bot.talk-test
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is] :as t])
  (:require [codax.core :as codax]
            [morse.handlers :as handlers])
  (:require [course-bot.plagiarism :as plagiarism]
            [course-bot.talk :as talk]))

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

(defn ^:deprecated match-history [*chat & asserts]
  (doall (map #(%1 %2)
           asserts
           (->> @*chat (take (count asserts)) reverse))))

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
                   talk/send-document (fn [token# id# file#] (swap! ~*chat conj {:id id# :msg (slurp file#)}))
                   talk/send-as-document (fn [token# id# _file# data#] (swap! ~*chat conj {:id id# :msg data#}))]
       ~@body)))

(defn test-database [path]
  (talk/reset-talk-states!)
  (codax/destroy-database! path)
  (codax/open-database! path))

(defn test-plagiarsm-database [path]
  (when (.exists (io/file path))
    (run! io/delete-file (reverse (file-seq (io/file path)))))
  (plagiarism/open-path-or-fail path))

(defn test-handler [& handlers]
  (let [*chat (atom (list))]
    {:*chat *chat
     :talk (fn rec
             ([id & msgs]
              (let [msg-count-before (count @*chat)
                    handler (apply handlers/handlers handlers)
                    _ (doall (map #(handler {:message {:from {:id id} :text %}}) msgs))
                    msg-count-after (count @*chat)
                    new-msgs-count (- msg-count-after msg-count-before)
                    new-msgs (->> @*chat
                                  (take new-msgs-count)
                                  (map #(if (-> % :msg string?)
                                          (assoc % :msg (-> % :msg str/trim-newline))
                                          %))
                                  reverse)]
                (cond
                  (and (= id (->> new-msgs first :id))
                       (= 1 (->> new-msgs (map :id) dedupe count)))
                  (->> new-msgs (map :msg) (into []))

                  :else
                  (->> new-msgs (map #(vector (:id %) (:msg %))) (into []))))))}))

(defn answers? [actual & answers]
  (let [expect (into [] answers)]
    (= actual expect)))

(defmethod t/assert-expr answers? [msg form]
  `(let [actual# ~(nth form 1)
         answers# ~(into [] (drop 2 form))
         result# (= actual# answers#)]
     (t/do-report
       {:type (if result# :pass :fail)
        :message ~msg
        :expected answers#
        :actual actual#})
     result#))

(defn ^:deprecated history
  [*chat & {:keys [user-id number] :or {user-id nil number 1}}]
  (->> @*chat
       (filter #(or (nil? user-id) (= user-id (:id %))))
       (take number)
       (map #(assoc % :msg (-> % :msg str/trim-newline)))
       (map #(if (nil? user-id)
               (vector (:id %) (:msg %))
               (:msg %)))
       reverse
       (apply vector)))

(defn unlines [& coll] (str/join "\n" coll))
