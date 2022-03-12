(ns course-bot.essay
  (:require [course-bot.talk :as talk]
            [course-bot.general :as general])
  (:require [codax.core :as codax])
  (:require [clojure.string :as str]))

(defn essay-talk [db token essay-code]
  (talk/talk db essay-code
          :start
          (fn [tx {{id :id} :chat}]
            (let [{submitted? :submitted?} (codax/get-at tx [id :essays essay-code])]
              (when submitted?
                (talk/send-text token id (str "Ваше эссе '" essay-code "' уже загружено"))
                (talk/stop-talk tx))
              (talk/send-text token id (str "Отправьте текст эссе '" essay-code "' одним сообщением."))
              (talk/change-branch tx :submit)))

          :submit
          (fn [tx {{id :id} :chat text :text}]
            (let [{submitted? :submitted?} (codax/get-at tx [id :essays essay-code])]
              (when submitted?
                (talk/send-text token id (str "Ваше эссе '" essay-code "' уже загружено"))
                (talk/stop-talk tx))
              (talk/send-text token id "Текст вашего эссе\n<<<<<<<<<<<<<<<<<<<<")
              (talk/send-text token id text)
              (talk/send-text token id ">>>>>>>>>>>>>>>>>>>>")
              (talk/send-yes-no-kbd token id (str "Загружаем (yes/no)?"))
              (-> tx
                  (codax/assoc-at [id :essays essay-code :text] text)
                  (talk/change-branch :approve))))

          :approve
          (fn [tx {{id :id} :chat text :text}]
            (let [{submitted? :submitted?
                   essay :text} (codax/get-at tx [id :essays essay-code])]
              (when submitted?
                (talk/send-text token id (str "Ваше эссе '" essay-code "' уже загружено"))
                (talk/stop-talk tx))
              (case text
                "yes" (do (talk/send-text token id "Спасибо, текст загружен и скоро попадет на рецензирование.")
                          (-> tx
                              (codax/assoc-at [id :essays essay-code :submitted?] true)
                              talk/stop-talk))
                "no" (do (talk/send-text token id "Вы можете перезагрузить текст.")
                         (talk/stop-talk tx))
                (do (talk/send-text token id "What? Yes or no.") tx))))))

(defn get-essays [tx essay-code]
  (->> (codax/get-at tx [])
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
  (reduce (fn [tx' [id desc]] (codax/assoc-at tx' [id] desc)) tx assignments))

(defn assign-essay-reviews [tx token essay-code admin-chat]
  (let [assignments (assign-reviews tx essay-code 3)]
    (talk/send-text token admin-chat
                 (str "Assignment count: " (count assignments) "; "
                      "Examples: " (some-> assignments first second :essays (get essay-code) :request-review)))
    (write-review-assignments tx assignments)))

(defn assign-essay-talk [db token essay-code assert-admin]
  (talk/talk db (str essay-code "assign")
          :start
          (fn [tx {{id :id} :chat}]
            (assert-admin tx token id)
            (assign-essay-reviews tx token essay-code id))))

(defn my-assignement-ids [tx id essay-code]
  (codax/get-at tx [id :essays essay-code :request-review]))

(defn my-assignements [tx id essay-code]
  (map #(codax/get-at tx [% :essays essay-code :text])
       (my-assignement-ids tx id essay-code)))

(defn str-reviews [tx essay-code id reviews]
  (let [assignment-texts (my-assignements tx id essay-code)]
    (str "Первое из перечисленных эссе -- лучшее.\n\n"
         (str/join "\n\n---\n\n"
                   (map #(str "Место: " (:pos %) ", "
                              "эссе #" (+ 1 (:index %)) ", "
                              "отзыв: " (:feedback %) " "
                              "\n(начало текста: " (let [essay (-> assignment-texts (nth (:index %)))]
                                                     (subs essay 0 (min (count essay) 40))) "...)")
                        (sort-by :pos reviews)))
         "\n\nПоследнее эссе -- худшее.")))

(defn essay-review-talk [db token essay-code]
  (talk/talk db (str essay-code "review")
          :start
          (fn [tx {{id :id} :chat}]
            (let [assignments (my-assignements tx id essay-code)]
              (when (empty? assignments)
                (talk/send-text token id "Вам не назначено не одно эссе. Вероятно, вы не загрузили своё.")
                (talk/stop-talk tx))
              (when-not (nil? (codax/get-at tx [id :essays essay-code :my-reviews]))
                (talk/send-text token id "Вы уже сделали ревью.")
                (talk/stop-talk tx))
              (talk/send-text token id (str "Вам на ревью пришло: " (count assignments) " эссе. Их текст сейчас отправлю ниже отдельными сообщениями."))
              (doall (map (fn [index text]
                            (talk/send-text token id (str "Эссе #" (+ 1 index) " <<<<<<<<<<<<<<<<<<<<"))
                            (talk/send-text token id text)
                            (talk/send-text token id (str ">>>>>>>>>>>>>>>>>>>> Эссе #" (+ 1 index))))
                          (range)
                          assignments))
              (talk/send-text token id "Ознакомьтесь и оцените их.

Для это необходимо отправить серию сообщений следующего вида: `<номер эссе> <текст отзыва который будет оправлен автору>`. Первым должно идти лучшее эссе.
Если хотите начать дискуссию с автором -- скиньте ему свой контакт.

Пример сообщений:

Сообщение 1:
2 Огонь, я сам до этого не додумался!

Сообщение 2:
1 Автор разобрался с материалом, но не смог выйти за его пределы.

Сообщение 3:
3 Увы, но ничего понять из эссе мне не удалось.

Перед тем как сохранить ваши ответы, у вас будет возможность проверить, правильно ли я вас понял.
")
              (talk/change-branch tx :get-feedback)))

          :get-feedback
          (fn foo ([tx msg] (foo tx msg {}))
            ([tx {{id :id} :chat text :text} {reviews :reviews}]
             (when (and (seq reviews) (empty? (filter :index reviews)))
               (talk/send-text token id "Увы, но вам надо начать писать отзывы с начала (если вы это сообщение видете в очередной раз -- сообщите).")
               (talk/stop-talk tx))
             (let [assignments (my-assignement-ids tx id essay-code)
                   index (try (- (Integer/parseInt (.trim (first (re-find #"^\d(\s|$)" text)))) 1)
                              (catch Exception _ nil))
                   pos (+ 1 (count reviews))
                   feedback (re-find #"[^\d\s].*" text)
                   author (nth assignments index)]
               (cond
                 (or (nil? index)
                     (< index 0) (>= index (count assignments))
                     (< pos 1) (> pos (count assignments)))
                 (do (talk/send-text token id "Увы, но я не понял какое эссе вы назвали. Номер должен быть первым символом и отделён от остального текста.")
                     (talk/wait tx))

                 (< (count text) 10)
                 (do (talk/send-text token id "Вы не оставили отзыва или сделали его смешно коротким. Напишите отзыв, уважьте автора.")
                     (talk/wait tx))

                 (some #{index} (map :index reviews))
                 (do (talk/send-text token id "Вы уже дали ответ относительно данного Эссе.")
                     (talk/wait tx))

                 :else
                 (let [reviews' (cons {:pos pos :index index
                                       :author author :review-author id
                                       :feedback feedback} reviews)]
                   (if (not= (count reviews') (count assignments))
                     (do (talk/send-text token id "ok")
                         (talk/change-branch tx :get-feedback {:reviews reviews'}))
                     (let [conclusion (str-reviews tx essay-code id reviews')]
                       (talk/send-text token id "Вы высказались про все эссе. Посмотрите что получилось:")
                       (talk/send-text token id conclusion)
                       (talk/send-yes-no-kbd token id "Всё верно?")
                       (talk/change-branch tx :approve
                                        {:reviews reviews'
                                         :conclusion conclusion}))))))))

          :approve
          (fn [tx {{id :id} :chat text :text} {reviews :reviews}]
            (cond
              (= text "yes")
              (do (talk/send-text token id "Ваш отзыв был сохранён, как накопим побольше -- разошлём авторам.")
                  (-> (reduce (fn [tx' review] (codax/update-at tx' [(:author review) :essays essay-code :received-review] conj review))
                              tx reviews)
                      (codax/assoc-at [id :essays essay-code :my-reviews] reviews)
                      talk/stop-talk))

              (= text "no")
              (do (talk/send-text token id "Загрузите свой отзыв снова.")
                  (talk/stop-talk tx))

              :else
              (do (talk/send-yes-no-kbd token id "Непонял. yes или no?")
                  (talk/wait tx))))))

(defn essay-status-talk [db token essay-code]
  (talk/talk db (str essay-code "status")
          :start
          (fn [tx {{id :id} :chat}]
            (let [essays (get-essays tx essay-code)]
              (talk/send-text token id
                           (str
                            "Всего эссе: " (count essays) "\n"
                            "Человек сделало ревью: "
                            (->> essays
                                 (filter #(-> % second :essays (get essay-code) :my-reviews count (> 0)))
                                 count) "\n"
                            "Есть комплект ревью на: "
                            (->> essays
                                 vals
                                 (map #(-> % :essays (get essay-code) :received-review))
                                 (filter #(= 3 (count %)))
                                 count) " эссе.")))

            (talk/stop-talk tx))))

(defn with-essay-author [tx essay-code reviewer-id reviews]
  (let [assignment-texts (my-assignement-ids tx reviewer-id essay-code)]
    (map #(assoc % :essay-author (-> assignment-texts (nth (:index %)))) reviews)))

(defn assign-essay-author [tx essay-code]
  (->> (get-essays tx essay-code)
       (map (fn [[id desc]]
              [id (update-in desc [:essays essay-code :my-reviews]
                             #(with-essay-author tx essay-code id %))]))

       ((fn [pairs]
          (reduce (fn [tx' [id desc]] (codax/assoc-at tx' [id] desc)) tx pairs)))))

(defn my-reviews [tx essay-code id]
  (if (= essay-code "essay1")
    (let [reviews (->> (codax/get-at tx [])
                       (map #(-> % second :essays (get essay-code) :my-reviews))
                       (apply concat)
                       (filter #(-> % :essay-author (= id))))]
      (->> reviews
           (map #(str "Как за вас проголовали: " (:pos %)
                      (when-let [fb (:feedback %)] (str "\nОтзыв: " fb))))))
    (->> (codax/get-at tx [id :essays essay-code :received-review])
         (map #(str "Как за вас проголовали: " (:pos %)
                    (when-let [fb (:feedback %)] (str "\nОтзыв: " fb)))))))

(defn essay-results-talk [db token essay-code]
  (talk/talk db (str essay-code "results")
          :start
          (fn [tx {{id :id} :chat}]
            (let [reviews (my-reviews tx essay-code id)]
              (doall (map #(talk/send-text token id %) reviews))
              (talk/send-text token id (str "Это были все ваши отзывы в количестве: " (count reviews) " шт.")))
            (talk/stop-talk tx))))

(defn essays-without-review [tx essay-code]
  (->> (get-essays tx essay-code)
       (filter #(-> (my-reviews tx essay-code (first %)) empty?))
       (map (fn [[id info]] (hash-map :author (:name info)
                                      :text (-> info :essays (get essay-code) :text)
                                      :on-review (-> info :essays (get essay-code) :request-review empty? not))))))

(defn essays-without-review-talk [db token essay-code assert-admin]
  (talk/talk db (str essay-code "missreview")
          :start
          (fn [tx {{id :id} :chat}]
            (assert-admin tx token id)
            (doall (->> (essays-without-review tx essay-code)
                        (map #(do (talk/send-text token id (str (:author %) " " (:on-review %)))
                                  (talk/send-text token id (:text %)))))))))

(defn collect-report
  "Example:

  (c/with-read-transaction [db tx]
     (print (essay/collect-report tx (str 'essay1))))"
  [tx essay-id]
  (->> (get-essays tx essay-id)
       (map (fn [[id desc]]
              (-> desc
                  :essays
                  (get essay-id)
                  (#(str "====================================\n"
                         "Оценка: " (->> % :received-review (map :pos) (apply +)) " (чем меньше, тем лучше)\n"
                         (:text %))))))
       (str/join "\n\n\n")))

(defn drop-talk [db token essay-code assert-admin]
  (talk/def-talk db (str "drop" essay-code)
    :start
    (fn [tx {{id :id} :from text :text}]
      (assert-admin tx token id)
      (let [args (talk/command-args text)]
        (if (and (= (count args) 1) (re-matches #"^\d+$" (first args)))
          (let [stud-id (Integer/parseInt (first args))
                stud (codax/get-at tx [stud-id])]
            (when (nil? (:name stud))
              (talk/send-text token id "User with specific telegram id not found.")
              (talk/stop-talk tx))
            (general/send-whoami tx token id stud-id)
            (talk/send-yes-no-kbd token id (str "Drop essay " essay-code " for this student?"))
            (-> tx
                (talk/change-branch :approve {:stud-id stud-id})))
          (do
            (talk/send-text token id (str "Wrong input. Expect: /drop" essay-code " 12345"))
            (talk/stop-talk tx)))))

    :approve
    (fn [tx {{id :id} :from text :text} {stud-id :stud-id}]
      (cond
        (= text "yes") (do (talk/send-text token id (str "Essay dropped: " stud-id))
                           (talk/send-text token stud-id (str "Essay dropped, you can resubmit it by /" essay-code))
                           (-> tx
                               (codax/assoc-at [stud-id :essays essay-code] nil)
                               (talk/stop-talk)))
        (= text "no") (do (talk/send-text token id "Not dropped.")
                          (talk/stop-talk tx))
        :else (do (talk/send-text token id "Please yes or no?")
                  (talk/repeat-branch tx))))))
