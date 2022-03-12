(ns course-bot.talk
  (:require [codax.core :as c])
  (:require [clojure.string :as str])
  (:require [morse.handlers :as h]
            [morse.api :as t]
            [clj-http.client :as http])
  (:require [clojure.test :as test]))

;; Talk flow

(defn change-branch
  ([tx name] (change-branch tx name {}))
  ([tx name state]
   (throw (ex-info "Change branch" {:next-branch name :tx tx :state state}))))

(defn stop-talk [tx]
  (throw (ex-info "Stop talk" {:tx tx})))

(defn wait [tx]
  (throw (ex-info "Wait talk" {:tx tx})))

(defn repeat-branch [tx] (wait tx))

(defn set-talk-branch [tx id talk branch state]
  (-> tx
      (c/assoc-at [id :dialog-state] nil)
      (c/assoc-at [id :talk] {:current-talk talk
                              :current-branch branch
                              :state state})))

(defn command-args [text] (filter #(not (empty? %)) (str/split (str/replace-first text #"^/\w+\s*" "") #"\s+")))

(defn command-text-arg [text] (str/replace-first text #"^/\w+\s*" ""))

(defn command-num-arg [text]
  (let [args (command-args text)]
    (if (and (= (count args) 1) (re-matches #"^\d+$" (first args)))
      (Integer/parseInt (first args))
      nil)))

(defn id-from-arg [tx text]
  (let [args (command-args text)]
    (when (and (= (count args) 1) (re-matches #"^\d+$" (first args)))
      (Integer/parseInt (first args)))))

(def *helps (atom {}))

(defn helps [] (->> @*helps
                    (map (fn [[n d]] (str n " - " d)))
                    sort
                    (str/join "\n")))

(defn talk [db name & handlers]
  (let [[help handlers] (if (string? (first handlers))
                          [(first handlers) (rest handlers)]
                          [nil handlers])
        handlers (apply hash-map handlers)
        start-handler (:start handlers)
        handlers (into {} (filter #(not= :start (first %)) handlers))]
    (when (and (some? help) (not (contains? @*helps name)))
      (swap! *helps assoc name help))
    (fn talk-top [update]
      (let [res (atom nil)]
        (try
          (declare tx)
          (c/with-write-transaction [db tx]
            (let [id (-> update :message :from :id)
                  msg (:message update)
                  {current-talk :current-talk
                   current-branch :current-branch
                   state :state} (c/get-at tx [id :talk])]
              (try
                (let [tmp (cond
                            (h/command? update name) (start-handler tx msg)

                            (str/starts-with? (-> msg :text) "/") nil

                            (and (= current-talk name) (contains? handlers current-branch))
                            (if (nil? state)
                              ((get handlers current-branch) tx msg)
                              ((get handlers current-branch) tx msg state)))]
                  (swap! res (constantly tmp))
                  (if (nil? @res) tx @res))
                (catch clojure.lang.ExceptionInfo e
                  (swap! res (constantly :ok))
                  (case (ex-message e)
                    "Change branch" (set-talk-branch (-> e ex-data :tx)
                                                     id name
                                                     (-> e ex-data :next-branch)
                                                     (-> e ex-data :state))
                    "Stop talk" (set-talk-branch (-> e ex-data :tx) id nil nil nil)
                    "Wait talk" (-> e ex-data :tx)
                    (throw e))))))
          @res)))))

(defmacro def-talk [& args] `(talk ~@args))

(defn def-command
  ([db name foo] (talk db name :start foo))
  ([db name help foo] (talk db name help :start foo)))

;; Re-exports

(intern 'course-bot.talk 'send-text t/send-text)
(intern 'course-bot.talk 'send-document t/send-document)

;; Morse helpers

(defn send-message
  "Sends json to the chat"
  ([token chat-id data] (send-message token chat-id {} data))
  ([token chat-id options data]
   (let [url  (str t/base-url token "/sendMessage")
         body (merge {:chat_id chat-id} options data)
         resp (http/post url {:content-type :json
                              :as           :json
                              :form-params  body})]
     (-> resp :body))))

(defn send-yes-no-kbd [token id msg]
  (send-message token id {:text msg
                          :reply_markup
                          {:one_time_keyboard true
                           :resize_keyboard true
                           :keyboard
                           [[{:text "yes"} {:text "no"}]]}}))

;; tests

(defmacro deftest [name args & body]
  (let [test-db "test-databases/example-database"
        [db *chat] args]
    `(test/deftest ~name
       (codax/destroy-database! ~test-db)
       (let [~*chat (atom (list))
             ~db (codax/open-database! ~test-db)]
         (with-redefs [talk/send-text (fn [token# id# msg#]
                                        (assert (= "TOKEN" token#))
                                        (swap! ~*chat conj {:id id# :msg msg#}))
                       talk/send-yes-no-kbd (fn [token# id# msg#] (swap! ~*chat conj {:id id# :msg msg#}))]
           ~@body)
         (codax/destroy-database! ~test-db)))))

(defn msg
  ([msg] {:message {:from {:id 1} :text msg}})
  ([id msg] {:message {:from {:id id} :text msg}}))
