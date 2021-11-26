(ns course-bot.essay
  (:require [course-bot.talk :as t])
  (:require [codax.core :as c])
  (:require [clojure.string :as str]))

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

(defn get-essays [tx essay-code]
  (->> (c/get-at tx [])
       (filter (fn [[_k v]] (-> v :essays (get essay-code) :submitted?)))))

(defn is-bad-review [essay-code id info]
  (let [reviewers (-> info :essays (get essay-code) :request-review)]
    (or (some #(= % id) reviewers)
        (not (apply distinct? reviewers)))))

;; (is-bad-review "essay1" 1 {:essays {"essay1" {:request-review '(1070936164 2 1)}}})
;; (is-bad-review "essay1" 1 {:essays {"essay1" {:request-review '(1070936164 2 2)}}})
;; (is-bad-review "essay1" 1 {:essays {"essay1" {:request-review '(1070936164 2 4)}}})

(defn assign-reviews [tx essay-code n]
  (loop [n n
         limit (* n 1000)
         essays (get-essays tx essay-code)]
    (let [tmp (into {} (map (fn [[id info] review-id]
                              [id (update-in info
                                             [:essays essay-code :request-review]
                                             conj review-id)])
                            essays
                            (shuffle (keys essays))))
          bad-items (filter #(apply is-bad-review essay-code %) tmp)]
      (cond
        (= n 0) essays
        (empty? bad-items) (recur (- n 1) (- limit 1) tmp)
        (and (not-empty bad-items)
             (<= limit 0)) nil ;; (throw (ex-info "LIMIT" {:bad-items bad-items}))
        :else (recur n (- limit 1) essays)))))

(defn write-review-assignments [tx assignments]
  (reduce (fn [tx' [id desc]] (c/assoc-at tx' [id] desc)) tx assignments))

(defn assign-essay-reviews [tx token essay-code admin-chat]
  (let [assignments (assign-reviews tx essay-code 3)]
    (t/send-text token admin-chat
                 (str "Assignment count: " (count assignments) "; "
                      "Examples: " (some-> assignments first second :essays (get essay-code) :request-review)))
    (write-review-assignments tx assignments)))

(defn assign-essay-talk [db token essay-code assert-admin]
  (t/talk db (str essay-code "assign")
          :start
          (fn [tx {{id :id} :chat}]
            (assert-admin tx token id)
            (assign-essay-reviews tx token essay-code id))))

(defn my-assignement-ids [tx id essay-code]
  (c/get-at tx [id :essays essay-code :request-review]))

(defn my-assignements [tx id essay-code]
  (map #(c/get-at tx [% :essays essay-code :text])
       (my-assignement-ids tx id essay-code)))

(defn essay-review-talk [db token essay-code]
  (t/talk db (str essay-code "review")
          :start
          (fn [tx {{id :id} :chat}]
            (let [assignments (my-assignements tx id essay-code)]
              (when (empty? assignments)
                (t/send-text token id "Вам не назначено не одно эссе. Вероятно, вы не загрузили своё.")
                (t/stop-talk tx))
              (when-not (nil? (c/get-at tx [id :essays essay-code :my-reviews]))
                (t/send-text token id "Вы уже сделали ревью.")
                (t/stop-talk tx))
              (t/send-text token id (str "Вам на ревью пришло: " (count assignments) " эссе. Их текст сейчас отправлю ниже отдельными сообщениями."))
              (doall (map (fn [index text]
                            (t/send-text token id (str "Эссе #" (+ 1 index) " <<<<<<<<<<<<<<<<<<<<"))
                            (t/send-text token id text)
                            (t/send-text token id (str ">>>>>>>>>>>>>>>>>>>> Эссе #" (+ 1 index))))
                          (range)
                          assignments))
              (t/send-text token id "Ознакомьтесь и оцените их.

Для это необходимо отправить серию сообщений следующего вида: `<номер эссе> <опциональный текст который будет оправлен автору>`. Первым должно идти лучшее эссе.
Если хотите начать дискуссию с автором -- скиньте ему свой контакт.

Пример сообщений:

Сообщение 1:
2 Огонь, я сам до этого не додумался!

Сообщение 2:
1

Сообщение 3:
3 Увы, но ничего понять из эссе мне не удалось.

Перед тем как сохранить ваши ответы, у вас будет возможность проверить, правильно ли я вас понял.
")
              (t/change-branch tx :get-feedback)))

          :get-feedback
          (fn foo ([tx msg] (foo tx msg {}))
            ([tx {{id :id} :chat text :text} {reviews :reviews}]
             (when (and (seq reviews) (empty? (filter :index reviews)))
               (t/send-text token id "Увы, но вам надо начать писать отзывы с начала (если вы это сообщение видете в очередной раз -- сообщите).")
               (t/stop-talk tx))
             (let [assignments (my-assignement-ids tx id essay-code)
                   assignment-texts (my-assignements tx id essay-code)
                   index (try (- (Integer/parseInt (.trim (first (re-find #"^\d(\s|$)" text)))) 1)
                              (catch Exception _ nil))
                   pos (+ 1 (count reviews))
                   feedback (re-find #"[^\d\s].*" text)
                   author (nth assignments (count reviews))]
               (cond
                 (or (nil? index)
                     (< index 0) (>= index (count assignments))
                     (< pos 1) (> pos (count assignments)))
                 (do (t/send-text token id "Увы, но я не понял какое эссе вы назвали. Номер должен быть первым символом и отделён от остального текста.")
                     (t/wait tx))

                 (some #{index} (map :index reviews))
                 (do (t/send-text token id "Вы уже дали ответ относительно данного Эссе.")
                     (t/wait tx))

                 :else
                 (let [reviews' (cons {:pos pos :index index
                                       :author author :review-author id
                                       :feedback feedback} reviews)]
                   (if (not= (count reviews') (count assignments))
                     (do (t/send-text token id "ok")
                         (t/change-branch tx :get-feedback :reviews reviews'))
                     (let [conclusion (str "Первое из перечисленных эссе -- лучшее.\n\n"
                                           (str/join "\n\n---\n\n"
                                                     (map #(str "Место: " (:pos %) ", "
                                                                "эссе #" (+ 1 (:index %)) ", "
                                                                "отзыв: " (:feedback %) " "
                                                                "(начало текста: " (let [essay (-> assignment-texts (nth (:index %)))]
                                                                                     (subs essay 0 (min (count essay) 40))) "...)")
                                                          (sort-by :pos reviews')))
                                           "\n\nПоследнее эссе -- худшее.")]
                       (t/send-text token id "Вы высказались про все эссе. Посмотрите что получилось:")
                       (t/send-text token id conclusion)
                       (t/send-yes-no-kbd token id "Всё верно?")
                       (t/change-branch tx :approve
                                        :reviews reviews'
                                        :conclusion conclusion))))))))

          :approve
          (fn [tx {{id :id} :chat text :text} {reviews :reviews}]
            (cond
              (= text "yes")
              (do (t/send-text token id "Ваш отзыв был сохранён, как накопим побольше -- разошлём авторам.")
                  (-> (reduce (fn [tx' review] (c/update-at tx' [(:author review) :essays essay-code :received-review] conj review))
                              tx reviews)
                      (c/assoc-at [id :essays essay-code :my-reviews] reviews)
                      t/stop-talk))

              (= text "no")
              (do (t/send-text token id "Загрузите свой отзыв снова.")
                  (t/stop-talk tx))

              :else
              (do (t/send-yes-no-kbd token id "Непонял. yes или no?")
                  (t/wait tx))))))

(defn essay-status-talk [db token essay-code]
  (t/talk db (str essay-code "status")
          :start
          (fn [tx {{id :id} :chat}]
            (let [essays (get-essays tx essay-code)]
              (t/send-text token id
                           (str
                            "Всего эссе: " (count essays) "\n"
                            "Человек сделало ревью: "
                            (->> essays
                                 vals
                                 (map #(-> % :essays (get essay-code) :my-reviews))
                                 (filter some?)
                                 count) "\n"
                            "Есть комплект ревью на: "
                            (->> essays
                                 vals
                                 (map #(-> % :essays (get essay-code) :received-review))
                                 (filter #(= 3 (count %)))
                                 count) " эссе.")))

            (t/stop-talk tx))))

(defn my-reviews [tx essay-code id]
  (let [reviews (c/get-at tx [id :essays essay-code :received-review])]
    (if (< (count reviews) 3)
      (list "Слишком мало отзывов, ждём пока будут все.")
      (->> reviews
           (map #(str "Как за вас проголовали: " (:pos %)
                      (when-let [fb (:feedback %)] (str "\nОтзыв: " fb))))))))

(defn essay-results-talk [db token essay-code]
  (t/talk db (str essay-code "results")
          :start
          (fn [tx {{id :id} :chat}]
            (doall (map #(t/send-text token id %) (my-reviews tx essay-code id)))
            (t/stop-talk tx))))
