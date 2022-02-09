(ns course-bot.talk
  (:require [codax.core :as c])
  (:require [clojure.string :as str])
  (:require [morse.handlers :as h]
            [morse.api :as t]
            [clj-http.client :as http]))

;; Talk flow

(defn change-branch [tx name & kwargs]
  (throw (ex-info "Change branch" {:next-branch name :tx tx :kwargs kwargs})))

(defn stop-talk [tx]
  (throw (ex-info "Stop talk" {:tx tx})))

(defn wait [tx]
  (throw (ex-info "Wait talk" {:tx tx})))

(defn repeat-branch [tx] (wait tx))

(defn set-talk-branch [tx id talk branch kwargs]
  (-> tx
   (c/assoc-at [id :dialog-state] nil)
   (c/assoc-at [id :talk] {:current-talk talk
                           :current-branch branch
                           :kwargs (apply hash-map kwargs)})))

(defn command-args [text] (str/split (str/replace-first text #"^/\w+\s+" "") #"\s+"))

(defn id-from-arg [tx text]
  (let [args (command-args text)]
    (when (and (= (count args) 1) (re-matches #"^\d+$" (first args)))
      (Integer/parseInt (first args)))))

(defmacro talk [db name & body]
  (let [branches (apply hash-map body)
        current-branch-var `current-branch#
        current-talk-var `current-talk#
        kwargs `kwargs#
        msg-var `msg#
        tx-var `tx#]
    `(fn talk-top# [update#]
       (let [res# (atom nil)]
         (try
           (c/with-write-transaction [~db ~tx-var]
             (let [id# (-> update# :message :from :id)
                   ~msg-var (:message update#)
                   {~current-talk-var :current-talk
                    ~current-branch-var :current-branch
                    ~kwargs :kwargs} (c/get-at ~tx-var [id# :talk])]
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
                                                    `(if (nil? ~kwargs)
                                                       (~body ~tx-var ~msg-var)
                                                       (~body ~tx-var ~msg-var ~kwargs))])))))]
                   (swap! res# (constantly tmp#))
                   (if (nil? @res#) ~tx-var @res#))
                 (catch clojure.lang.ExceptionInfo e#
                   (swap! res# (constantly :ok))
                   (case (ex-message e#)
                     "Change branch" (set-talk-branch (-> e# ex-data :tx)
                                                      id# ~name
                                                      (-> e# ex-data :next-branch)
                                                      (-> e# ex-data :kwargs))
                     "Stop talk" (set-talk-branch (-> e# ex-data :tx) id# nil nil nil)
                     "Wait talk" (-> e# ex-data :tx)
                     (throw e#))))))
           @res#)))))

(defmacro def-talk [& args] `(talk ~@args))

(defn def-command [db name foo]
    (def-talk db name :start foo))

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
