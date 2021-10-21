(ns course-bot.dialog
  (:require [codax.core :as c])
  (:require [morse.handlers :as h])
  (:gen-class))


(defn set-chat-dialog-state [db id state]
  ;; (println "set new state: " (c/get-at! db [id :dialog-state]))
  (c/assoc-at! db [id :dialog-state] state))

(defn get-chat-dialog-state [db id]
  ;; (println "current state: " (c/get-at! db [id :dialog-state]))
  (c/get-at! db [id :dialog-state]))

(defn state-fork [state i] (keyword (str (name state) "-" i)))

(defn extract-param [name body]
  (let [[_ value & tail] (drop-while #(not= name %) body)
        body (if value
               (concat (take (- (count body) (count tail) 2) body) tail)
               body)]
    [value body]))

(defn prepare-do [body]
  (if (and (list? body) (= 'do (first body)))
    (rest body)
    (list body)))

(def dialog-steps #{:listen :yes-no})

(defn extract-dialog-forks [body]
  (case (first body)
    :listen `((listen ~@(rest body)))
    :yes-no (let [[input-error body] (extract-param :input-error body)
                  [_macro param if-yes if-no] body]
              (list
               `(yes-no-desc "yes" ~input-error ~param ~@(prepare-do if-yes))
               `(yes-no-desc "no" ~input-error ~param ~@(prepare-do if-no))))
    nil))

(defn split-dialog-body [body]
  (if (and (not-empty body) (contains? dialog-steps (-> body last first)))
    [(drop-last body) (last body)]
    [body nil]))

(defn dialog-handlers [db expect-state body]
  (let [forks (extract-dialog-forks body)]
    (apply concat
           (map (fn [[i fork]]
                  (let [[body dialog-flow] (split-dialog-body fork)
                        next-state (state-fork expect-state i)]
                    (cons
                     `(fn [state# update#]
                        (if (= state# ~expect-state)
                          (do ((~@body
                                :update-state (set-chat-dialog-state ~db (-> update# :message :from :id) ~(when dialog-flow next-state))) (:message update#)))))
                     (dialog-handlers db next-state dialog-flow))))
                (map vector (range) forks)))))

(defmacro yes-no-desc [expect input-error param & body]
  (let [[update-state body] (extract-param :update-state body)]
    `(fn [{text# :text :as message#}]
       (let [~param message#]
         (if (= text# ~expect)
           (do ~@body ~update-state)
           (when (and (= ~expect "no") (not= text# "yes")) ~input-error))))))

(defmacro listen [param & body]
  (let [[update-state body] (extract-param :update-state body)
        [guard body] (extract-param :guard body)]
    `(fn [~param]
       (let [guard-result# ~guard]
         (if guard-result# guard-result# (do ~@body ~update-state))))))

;; TODO: rename to flow
(defmacro dialog [name db param & body]
  (let [next-state (keyword name)
        [guard body] (extract-param :guard body)
        [start-body dialog-flow] (split-dialog-body body)
        handlers (dialog-handlers db next-state dialog-flow)]
    `(fn [update#]
       (some #(% update#)
             (list
              (fn [update#]
                (let [~param (:message update#)]
                  (if (and (h/command? update# ~name) (nil? ~guard))
                    (do ~@start-body
                        (set-chat-dialog-state ~db (-> update# :message :from :id) ~(if dialog-flow next-state nil))))))
              (fn [update#]
                (if (not (clojure.string/starts-with? (-> update# :message :text) "/"))
                  (let [cur-state# (get-chat-dialog-state ~db (-> update# :message :from :id))]
                    (some (fn [handler#] (handler# cur-state# update#)) (list ~@handlers))))))))))
