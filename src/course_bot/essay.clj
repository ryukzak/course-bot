(ns course-bot.essay
  (:require [course-bot.talk :as t])
  (:require [codax.core :as c])
  (:require [clojure.string :as str])
  (:require [morse.handlers :as h]
            [morse.polling :as p])
  (:require [clojure.pprint :refer [pprint]]))

(defn essay-talk [db token essay-code]
  (t/talk db essay-code
          :start
          (fn [tx {{id :id} :chat}]
            (let [{submitted? :submitted?} (c/get-at tx [id :essays essay-code])]
              (when submitted?
                (t/send-text token id (str "Ваше эссе '" essay-code "' уже загружено"))
                (t/stop-talk tx))
              (t/send-text token id (str "Отправьте текст эссе '" essay-code "' одним сообщением."))
              (t/change-branch tx :submit)))
          :submit
          (fn [tx {{id :id} :chat text :text}]
            (let [{submitted? :submitted?} (c/get-at tx [id :essays essay-code])]
              (when submitted?
                (t/send-text token id (str "Ваше эссе '" essay-code "' уже загружено"))
                (t/stop-talk tx))
              (t/send-text token id "Текст вашего эссе\n<<<<<<<<<<<<<<<<<<<<")
              (t/send-text token id text)
              (t/send-text token id ">>>>>>>>>>>>>>>>>>>>")
              (t/send-yes-no-kbd token id (str "Загружаем (yes/no)?"))
              (-> tx
                  (c/assoc-at [id :essays essay-code :text] text)
                  (t/change-branch :approve))))
          :approve
          (fn [tx {{id :id} :chat text :text}]
            (let [{submitted? :submitted?
                   essay :text} (c/get-at tx [id :essays essay-code])]
              (when submitted?
                (t/send-text token id (str "Ваше эссе '" essay-code "' уже загружено"))
                (t/stop-talk tx))
              (case text
                "yes" (do (t/send-text token id "Спасибо, текст загружен и скоро попадет на рецензирование.")
                          (-> tx
                              (c/assoc-at [id :essays essay-code :submitted?] true)
                              t/stop-talk))
                "no" (do (t/send-text token id "Вы можете перезагрузить текст.")
                         (t/stop-talk tx))
                (do (t/send-text token id "What? Yes or no.") tx))))))
