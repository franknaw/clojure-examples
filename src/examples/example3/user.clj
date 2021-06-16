(ns user)


(defn split-first [re s]
  (clojure.string/split s re 2))

(defn split-last [re s]
  (let [pattern (re-pattern (str re "(?!.*" re ")"))]
    (split-first pattern s)))

(defn query-rr-logs "" []
  (->> (db/aggregate
         "rr-log"
         [{"$group" {:_id {:method    "$method"
                           :uri       "$uri"
                           :query     "$query"
                           :body      "$body"
                           :response  "$response"
                           :end-point {"$arrayElemAt" [{"$split" ["$uri", "/"]}, 4]}}}}
          {"$project" {:method    "$_id.method" :uri "$_id.uri"
                       :query     "$_id.query" :body "$_id.body"
                       :response  "$_id.response"
                       :end-point "$_id.end-point"
                       ;:uri-len   {"$cond" {:if {"$eq" ["$_id.method" "GET"]}
                       ; :then "foo" :else "fee"}}
                       :u-q-group {"$concat" ["$_id.end-point" "?" "$_id.query"]}}}
          ;{"$limit" 10}
          {"$sort" {:u-q-group 1}}])))

(def api-docs-location "./resources/api-docs/")
(defn remove-generate-api-docs "" []
  (doseq [f (reverse (file-seq (clojure.java.io/file api-docs-location)))]
    (clojure.java.io/delete-file f true))
  (.mkdir (File. api-docs-location)))

(defn generate-api-docs "" []
  (remove-generate-api-docs)
  (doseq [val (query-rr-logs)]
    (with-open [w (clojure.java.io/writer
                    (str api-docs-location
                      (:end-point val) "-api-doc.md") :append true)]
      (.write w (str "#### METHOD \n * `" (:method val) "`\n"
                  "#### URI \n * `" (:uri val) "`\n"
                  "#### QUERY \n * `" (:query val) "`\n"
                  "#### BODY \n```json\n" (:body val) "\n```\n"
                  "#### RESPONSE \n```json\n" (:response val) "\n```\n\n")))))