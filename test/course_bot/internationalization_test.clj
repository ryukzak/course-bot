(ns course-bot.internationalization-test
  (:require [clojure.test :refer [deftest testing are]])
  (:require [course-bot.internationalization :as i18n :refer [normalize-yes-no-text]]))

(deftest test-normalize-yes-no-text-en
  (testing
   (are [expected actual] (= expected actual)
     "yes" (normalize-yes-no-text "yes")
     "yes" (normalize-yes-no-text "YeS")
     "no" (normalize-yes-no-text "no")
     "no" (normalize-yes-no-text "NO")
     "some input" (normalize-yes-no-text "some input"))))

(deftest test-normalize-yes-no-text-ru
  (testing
   (i18n/set-locales [:ru :en])
    (are [expected actual] (= expected actual)
      "yes" (normalize-yes-no-text "ДА")
      "no" (normalize-yes-no-text "нет")
      "no" (normalize-yes-no-text "НЕТ")
      "что-нибудь" (normalize-yes-no-text "что-нибудь"))
    (i18n/set-locales [:en])))
