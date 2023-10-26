(ns course-bot.plagiarism-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [course-bot.plagiarism :as sut]))

(def text1
  (str/join " " ["Even though they are curious animals who like to explore and hunt, cats are also a truly social species."
                 "They communicate with humans through mewing, purring or sometimes grunting."
                 "And it is possible to even learn cat-specific body language to better understand their needs and wishes."
                 "An old English proverb says: \"A cat has nine lives. For three he plays, for three he strays, and for the last three he stays.\""]))

(def text2
  (str/join " " ["Cats are fascinating creatures known for their inquisitive nature and hunting instincts. They are also highly social animals,"
                 "and they use various forms of communication to interact with humans. From gentle mewing to contented purring, each vocalization"
                 "carries a specific meaning. Additionally, cats have a unique body language that, when understood, allows for a deeper connection"
                 "between feline and human. As the old English proverb wisely puts it: \"A cat has nine lives. For three he plays, for three he"
                 "strays"]))

(def text3 "The impact of climate change on biodiversity is a matter of growing concern among scientists.")

(def text4 "Scientists are increasingly worried about the effect of climate change on biodiversity.")

(deftest register-check-test
  (let [path "tmp/plagiarism-test"]
    (when (.exists (io/file path))
      (run! io/delete-file (reverse (file-seq (io/file path)))))

    (-> (sut/open-path-or-fail path)
        (sut/register-text! "a" "bla")
        (sut/register-text! "b" "bla-bla")
        (sut/register-text! "cl" "bla-bla asdf")
        (sut/register-text! "d" "hello")
        (sut/register-text! "e" "привет")
        (sut/register-text! "text1" text1)
        (sut/register-text! "text2" text2)
        (sut/register-text! "text3" text3)
        (sut/register-text! "text4" text4))

    (let [db (sut/open-path-or-fail path)]
      (is (= "b" (-> db (sut/find-original "bla-bla") :key)))
      (is (= "e" (-> db (sut/find-original "привет") :key)))
      (is (= nil (-> db (sut/find-original "asdf") :key)))

      (is (= "text1" (-> db (sut/find-original text1) :key)))
      (is (= "text2" (-> db (sut/find-original text2) :key)))
      (is (= "text2" (-> db (sut/find-original (str text2 " bla bla bla")) :key)))
      (is (= text2 (-> db (sut/get-text "text2"))))

      (is (= '("text3" "text4")
             (->> (sut/sorted-similars db text3) (map :key) (take 2)))))))
