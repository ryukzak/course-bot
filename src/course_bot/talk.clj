(ns course-bot.talk
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:require [clj-http.client :as http]
            [codax.core :as codax]
            [morse.api :as morse]
            [morse.handlers :as handlers])
  (:require [course-bot.internationalization :as i18n :refer [tr]]
            [course-bot.misc :as misc]))

(i18n/add-dict
  {:en
   {:talk
    {:yes "yes"
     :no "no"
     :cancelled "Cancelled."
     :question-yes-no "What (yes or no)?"
     :clarify-input-tmpl "Didn't understand: %s. Yes or no?"}}
   :ru
   {:talk
    {:yes "да"
     :no "нет"
     :cancelled "Отменено."
     :question-yes-no "Что (да или нет)?"
     :clarify-input-tmpl "Не разобрал: %s. Да или нет?"}}})

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

(def talk-states (atom {}))

(defn reset-talk-states! []
  (reset! talk-states {}))

(defn get-talk-state [_tx id]
  (get @talk-states id nil))

(defn set-talk-branch [tx id talk branch state]
  (swap! talk-states assoc id {:current-talk talk
                               :current-branch branch
                               :state state})
  tx)

(defn command-args [text] (filter #(not (empty? %)) (str/split (str/replace-first text #"^/\w+\s*" "") #"\s+")))

(defn command-text-arg [text]
  (str/replace-first text #"^/\w+\s*" ""))

(defn command-text-arg-or-nil [text]
  (let [arg (str/replace-first text #"^/\w+\s*" "")]
    (if (not (empty? arg))
      arg
      nil)))

(defn command-num-arg [text]
  (let [args (command-args text)]
    (if (and (= (count args) 1) (re-matches #"^\d+$" (first args)))
      (parse-long (first args))
      nil)))

(defn command-keyword-arg [text]
  (let [args (command-args text)]
    (if (and (= (count args) 1) (re-matches #"^[\w-_]+$" (first args)))
      (keyword (first args))
      nil)))

(def *helps (atom {}))

(defn descriptions []
  (->> @*helps
       (map (fn [[n d]] (str n " - " d)))
       sort
       (str/join "\n")))

(defn helps []
  (let [commands (->> (descriptions)
                      (str/split-lines)
                      (map #(str "/" %)))]
    (str/join "\n" commands)))

;; TODO: move help hint to name, like: "start - register student"

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
        (declare tx)
        #_:clj-kondo/ignore [:unresolved-symbol] ; for some reason clj-kondo work with mainstream version.
        (codax/with-upgradable-transaction [db tx]
          (let [id (-> update :message :from :id)
                msg (:message update)
                {current-talk :current-talk
                 current-branch :current-branch
                 state :state} (get-talk-state tx id)]
            (try
              (let [tmp (cond
                          (handlers/command? update name)
                          (start-handler tx msg)

                          (nil? msg) nil
                          (nil? (-> msg :text)) nil
                          (str/starts-with? (-> msg :text) "/") nil

                          (and (= current-talk name) (contains? handlers current-branch))
                          (let [handler (get handlers current-branch)]
                            (if (empty? state)
                              (handler tx msg)
                              (handler tx msg state))))]

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
        @res))))

(defmacro def-talk [& args] `(talk ~@args))

(defmacro when-handlers [test & handlers]
  `(if ~test
     (handlers/handlers ~@handlers)
     (constantly nil)))

;; TODO: move help hint to name, like: "start - register student"

(defn def-command
  ([db name foo] (talk db name :start foo))
  ([db name help foo] (talk db name help :start foo)))

;; Re-exports

(defn send-text [token id msg]
  (cond 
    (> (count msg) 4096)
    (do (morse/send-text token id (subs msg 0 4096))
        (send-text token id (subs msg 4096)))

    :else
    (morse/send-text token id msg)))

(defn send-document [& args] (apply morse/send-document args))

(defn send-as-document [token id filename content]
  (let [dt (.format (java.text.SimpleDateFormat. "yyyy-MM-dd-HH-mm-Z") (misc/today))
        filename (str "tmp/" dt "-" filename)]
    (io/make-parents filename)
    (spit filename (if (string? content)
                     content
                     (misc/pp-str content)))
    (send-document token id (io/file filename))))

;; Morse helpers

(defn send-message
  "Sends json to the chat"
  ([token chat-id data] (send-message token chat-id {} data))
  ([token chat-id options data]
   (let [url (str morse/base-url token "/sendMessage")
         body (merge {:chat_id chat-id} options data)
         resp (http/post url {:content-type :json
                              :as :json
                              :form-params body})]
     (-> resp :body))))

(defn send-yes-no-kbd [token id msg]
  (send-message token id {:text msg
                          :reply_markup
                          {:one_time_keyboard true
                           :resize_keyboard true
                           :keyboard
                           [[{:text (tr :talk/yes)} {:text (tr :talk/no)}]]}}))

(defn send-stop
  ([tx token id] (send-stop tx token id (tr :talk/cancelled)))
  ([tx token id msg] (send-text token id msg)
                     (stop-talk tx)))

(defn clarify-input
  ([tx token id] (clarify-input tx token id (tr :talk/question-yes-no)))
  ([tx token id msg] (send-text token id msg)
                     (repeat-branch tx)))
;; tests

(defn msg
  ([msg] {:message {:from {:id 1} :text msg}})
  ([id msg] {:message {:from {:id id} :text msg}}))
