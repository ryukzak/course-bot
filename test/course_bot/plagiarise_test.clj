(ns course-bot.plagiarise_test
  (:require
    [course-bot.plagiarism :as plagiarism]
    [course-bot.talk-test :as tt])
  (:require [clojure.test :refer [deftest testing is]]
            [codax.core :as codax]))

(defn long-str [& strings] (clojure.string/join " " strings))

(def unique-essay
  (long-str "Even though they are curious animals who like to explore and hunt, cats are also a truly social species."
            "They communicate with humans through mewing, purring or sometimes grunting."
            "And it is possible to even learn cat-specific body language to better understand their needs and wishes."
            "An old English proverb says: \"A cat has nine lives. For three he plays, for three he strays, and for the last three he stays.\""))

(def original-essay
  (long-str "Cats are fascinating creatures known for their inquisitive nature and hunting instincts. They are also highly social animals,"
            "and they use various forms of communication to interact with humans. From gentle mewing to contented purring, each vocalization"
            "carries a specific meaning. Additionally, cats have a unique body language that, when understood, allows for a deeper connection"
            "between feline and human. As the old English proverb wisely puts it: \"A cat has nine lives. For three he plays, for three he"
            "strays"))

(def plagiarised-essay
  (long-str "Cats are fascinating creatures known for their inquisitive nature and hunting instincts. They are also highly social animals,"
            "and they use various forms of communication to interact with humans. From gentle mewing to contented purring, each vocalization"
            "between feline and human. As the old English proverb wisely puts it: \"A cat has nine lives. For three he plays, for three he"
            "strays"))


(deftest essay-plagiarize-test
  (let [forest (plagiarism/get-test-forest
                 "./test-databases/example-forest")
        db (tt/test-database)]

    (plagiarism/add-to-forest forest "short essay text" "essay1" "id1")
    (plagiarism/add-to-forest forest "little longer essay" "essay2" "id1")

    (testing "unique should pass"
      (is (= (plagiarism/is-essay-plagiarised forest unique-essay "un" "essay1" db) false)))

    (testing "should detect plagiarism"
      (plagiarism/add-to-forest forest original-essay "essay1" "id2")
      (codax/assoc-at! db ["id2" :essays "essay1" :text] original-essay)
      (codax/with-read-transaction [db tx]
                                   (def res (plagiarism/is-essay-plagiarised forest plagiarised-essay "bad-student" "essay1" tx))
                                   (codax/assoc-at tx ["id3" :essays "essay1" :text] plagiarised-essay))
      (is (= res true)))))


