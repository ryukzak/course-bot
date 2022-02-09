(ns course-bot.misc)

(defn read-config
  ([filename] (read-config filename true))
  ([filename skip]
   (read-string (try
                  (slurp (str (or (System/getenv "CONF_PATH") "../csa-tests") "/" filename))
                  (catch Exception e
                    (if skip "nil" (throw e)))))))
