(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'course-bot/csa)
(def version (format "0.2.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :ns-compile '[course-bot.csa]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'course-bot.csa})
  (b/copy-file {:src uber-file
                :target "target/course-bot.jar"}))
