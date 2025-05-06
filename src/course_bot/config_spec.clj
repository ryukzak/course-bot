(ns course-bot.config-spec
  (:require [clojure.spec.alpha :as s]))

(s/def ::inline-entry (s/tuple #{:inline} string?))

(s/def ::text string?)
(s/def ::correct boolean?)
(s/def ::option (s/keys :req-un [::text] :opt-un [::correct]))
(s/def ::options (s/coll-of ::option :kind vector?))

(s/def ::ask string?)
(s/def ::question (s/keys :req-un [::ask ::options]))
(s/def ::questions (s/coll-of ::question :kind vector?))

(s/def ::name string?)
(s/def ::ignore-in-score boolean?)

(s/def ::quiz-config
  (s/keys :req-un [::name ::questions]
          :opt-un [::ignore-in-score]))

(s/def ::topic-msg string?)
(s/def ::review-msg string?)
(s/def ::review-deadline string?)
(s/def ::min-length int?)

(s/def ::essay-config
  (s/keys :req-un [::topic-msg ::review-deadline ::min-length]
          :opt-un [::review-msg]))

(s/def ::admins (s/coll-of int? :kind vector?))
(s/def ::submition-hint string?)
(s/def ::schedule-cut-off-time-in-min int?)
(s/def ::agenda-hide-cut-off-time-in-min int?)
(s/def ::max-description-length int?)
(s/def ::agenda-postfix string?)

(s/def ::datetime string?)
(s/def ::lesson (s/keys :req-un [::datetime]))
(s/def ::lessons (s/coll-of ::lesson :kind vector?))

(s/def ::comment string?)
(s/def ::group (s/keys :req-un [::lessons] :opt-un [::comment]))
(s/def ::groups (s/map-of string? ::group))

(s/def ::stud-id int?)
(s/def ::presentation (s/keys :req-un [::stud-id ::text]))
(s/def ::presentations (s/coll-of ::presentation :kind vector?))
(s/def ::lost-lesson (s/keys :req-un [::datetime ::presentations]))
(s/def ::lost-lessons (s/coll-of ::lost-lesson :kind vector?))
(s/def ::lost-group (s/keys :req-un [::lessons]))
(s/def ::lost-and-found (s/map-of string? ::lost-group))

(s/def ::feedback-scores (s/map-of int? (s/coll-of int? :kind vector?)))

(s/def ::lab-config
  (s/keys :req-un [::name ::admins ::groups ::feedback-scores]
          :opt-un [::submition-hint
                   ::schedule-cut-off-time-in-min
                   ::agenda-hide-cut-off-time-in-min
                   ::max-description-length
                   ::agenda-postfix
                   ::lost-and-found]))

(s/def ::admin-chat-id int?)
(s/def ::token string?)
(s/def ::db-path string?)
(s/def ::plagiarism-path string?)
(s/def ::can-receive-reports (s/coll-of int? :kind vector?))
(s/def ::allow-restart boolean?)
(s/def ::groups (s/map-of string? map?))

(s/def ::lab1 ::inline-entry)
(s/def ::essay1 ::inline-entry)
(s/def ::quiz (s/map-of keyword? ::inline-entry))

(s/def ::csa-config
  (s/keys :req-un [::admin-chat-id
                   ::token
                   ::db-path
                   ::plagiarism-path
                   ::groups
                   ::lab1
                   ::essay1
                   ::quiz]
           :opt-un [::can-receive-reports
                    ::allow-restart]))

(defn validate-csa-config
  [config]
  (when-not (s/valid? ::csa-config config)
    (s/explain-str ::csa-config config)))

(defn validate-lab-config
  [config]
  (when-not (s/valid? ::lab-config config)
    (s/explain-str ::lab-config config)))

(defn validate-essay-config
  [config]
  (when-not (s/valid? ::essay-config config)
    (s/explain-str ::essay-config config)))

(defn validate-quiz-config
  [config]
  (when-not (s/valid? ::quiz-config config)
    (s/explain-str ::quiz-config config)))

(defn test-validation 
  ([file]
   (let [filename (.getName (clojure.java.io/file file))
         config-type (cond
                       (re-matches #"(?i).*csa.*\.edn" filename) :csa
                       (re-matches #"lab\d+\.edn" filename) :lab
                       (re-matches #"essay\d+\.edn" filename) :essay
                       (re-matches #"test-quiz.*\.edn" filename) :quiz
                       :else :unknown)]
     (test-validation file config-type)))
  ([file config-type]
   (let [conf (read-string (slurp file))
         validator (case config-type
                     :csa validate-csa-config
                     :lab validate-lab-config
                     :essay validate-essay-config
                     :quiz validate-quiz-config
                     (constantly "Unknown configuration type"))]
     (println "Testing file validation:" file "(" config-type ")")
     (if-let [error (validator conf)]
       (do
         (println "VALIDATION ERROR:")
         (println error)
         false)
       (do
         (println "Validation successful!")
         true))))) 