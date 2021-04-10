(defmethod access-log-query :group-url [type params entity request user]
  (sort (db/get-distinct-values "tomcat-access-logs" :url {})))

(defmethod access-log-query :group-user [type params entity request user]
  (remove #{"-"} (db/get-distinct-values "tomcat-access-logs" :a2tUser {})))

(defmethod access-log-query :log-totals [type params entity request user]
  [(db/get-count "tomcat-access-logs")])

(defmethod access-log-query :endpoint-freqs [type params entity request user]
  (->> (db/aggregate "tomcat-access-logs"
                     [{"$group" {:_id  {:url "$url"} :count {"$sum" 1}}}
                      {"$project" {:url "$_id.url" :count "$count"}}
                      {"$sort"  {:count -1}}])
       vec))

(defmethod access-log-query :endpoint-activity-url
  [type params entity request user]
  (->> (db/aggregate
        "tomcat-access-logs"
        [{"$group" {:_id  {:url "$url"
                           :datetime {"$dateToString" {:format "%Y-%m-%d",
                                                       :date "$datetime"}}}
                    :count {"$sum" 1}}}
         {"$project" {:url "$_id.url" :datetime "$_id.datetime" :count "$count"}}
         {"$sort"  {:count -1}}])
       vec))

(defmethod access-log-query :endpoint-activity-user
  [type params entity request user]
  (->> (db/aggregate
        "tomcat-access-logs"
        [{"$group" {:_id  {:auser "$a2tUser" :url "$url"
                           :datetime {"$dateToString" {:format "%Y-%m-%d",
                                                       :date "$datetime"}}}
                    :count {"$sum" 1}}}
         {"$project" {:auser "$_id.auser" :url "$_id.url" :datetime "$_id.datetime" :count "$count"}}
         {"$sort"  {:count -1}}])
       vec))

(defmethod access-log-query :user-total [type params entity request user]
  (->> (db/aggregate
        "tomcat-access-logs"
        [{"$match"
          {"$and"  [{:a2tUser {"$ne" "-"}}]}}
         {"$group" {:_id  {:auser "$a2tUser"
                           :datetime {"$dateToString" {:format "%Y-%m-%d"
                                                       :date "$datetime"}}}
                    :count {"$sum" 1}}}
         {"$project" {:auser "$_id.auser" :datetime "$_id.datetime"
                      :count "$count"}}
         {"$sort"  {:count -1}}])
       vec))

(defmethod access-log-query :user-total-sessions [type params entity request user]
  (->> (db/aggregate
         "tomcat-access-logs"
         [{"$match"
           {"$and"  [{:a2tUser {"$ne" "-"}} {:sessionId {"$ne" "-"}}]}}
          {"$group" {:_id  {:sessionid "$sessionId"
                            :auser "$a2tUser"
                            :datetime {"$dateToString" {:format "%Y-%m-%d"
                                                        :date "$datetime"}}}
                     :totalreq {"$sum" 1}}}
          {"$project" {:_id {:auser "$_id.auser" :datetime "$_id.datetime"
                       :sessionid "$_id.sessionid" :totalreq "$totalreq"}
                       :auser "$_id.auser" :datetime "$_id.datetime" :totalreq "$totalreq"}}
          {"$sort"  {:totalreq -1}}])
    vec))

(defmethod access-log-query :top-longest [type params entity request user]
  (->> (db/aggregate
        "tomcat-access-logs"
        [{"$match"
          {"$and"  [{:a2tUser {"$ne" "-"}}]}}
         {"$group" {:_id {:elapsed "$elapsedSeconds"
                          :auser "$a2tUser" :url "$url"
                          :datetime {"$dateToString" {:format "%Y-%m-%d",
                                                      :date "$datetime"}}
                          :params "$params"
                          } }}
         {"$project" {:auser "$_id.auser"
                      :url "$_id.url"
                      :elapsed "$_id.elapsed"
                      :datetime "$_id.datetime"
                      :params "$_id.params"
                      }}
         {"$sort"  {:_id.elapsed -1}}])
        vec (take (if (nil? (:limit (:params request)) )
                   2 (Integer/parseInt (:limit (:params request)))))) )

(defmethod access-log-query :daily-totals [type params entity request user]
  (->> (db/aggregate
        "tomcat-access-logs"
        [{"$group" {:_id {:datetime {"$dateToString" {:format "%Y-%m-%d",
                                                      :date "$datetime"}}}
                    :count {"$sum" 1} }}
         {"$project" {:datetime "$_id.datetime" :count "$count"}}
         {"$sort"  {:count -1}}])
      vec))

(defmethod access-log-query :user-session [type params entity request user]
  (->> (db/aggregate
        "tomcat-access-logs"
        [{"$match"
          {"$and" [{:datetime {"$gte" (time/minus
                                       (time/now)
                                       (time/minutes (Integer/parseInt
                                                       (if (nil? (:threshold
                                                                   (:params
                                                                     request)))
                                                         "600000" (:threshold
                                                               (:params
                                                                 request))))))}
                    }
                   {:a2tUser {"$ne" "-"}}]}}
         {"$group" {:_id  {:auser "$a2tUser" :datetime "$datetime"
                           :url "$url"
                           :agent "$requestHeaders.User-Agent"
                           :remoteip "$remoteIP"}}}
         {"$project" {:datetime "$_id.datetime"
                      :group {"$concat" ["$_id.auser" "-" "$_id.agent"
                                         "-" "$_id.remoteip"]}}}
         {"$sort"  {:_id.datetime -1}}])
       (map #(assoc % :datetime (str (DateTime. (:datetime %)))))
       vec))

(defmethod list-response :access-log [entity request user]
  (let [query  (:params request)
        type   (ck/camel-str->kebab-kywd (:type query))
        params (:params query)]
    (json-200 (access-log-query type params entity request user))))

(defmethod access-log-query :log-total-exports
  [type params entity request user]
  (->> (db/aggregate
         "log"
         [{"$match"
           {"$and" [{:ns "a2t-clj.log"} {:msg {"$regex" "export"}}]}
           }
          {"$group" {:_id   {:datetime {"$dateToString" {:format "%Y-%m"
                                                        :date   "$instant"}}}
                     :count {"$sum" 1}}}
          {"$project" {:datetime "$_id.datetime" :count "$count"}}
          {"$sort" {:datetime -1}}])
    vec))

(defmethod access-log-query :total-exports-users
  [type params entity request user]
  (->> (db/aggregate
         "tomcat-access-logs"
         [{"$match"
           {"$and" [{:url {"$regex" "Export"}}]}}
          {"$group" {:_id   {:auser  "$a2tUser"
                             :datetime {"$dateToString" {:format "%Y-%m"
                                                         :date   "$datetime"}}}
                     :count {"$sum" 1}}}
          {"$project" {:auser "$_id.auser" :datetime "$_id.datetime"
                       :count "$count"}}
          {"$sort" {:datetime -1}}])
    vec))

(defmethod access-log-query :total-exports
  [type params entity request user]
  (->> (db/aggregate
         "tomcat-access-logs"
         [{"$match"
           {"$and" [{:url {"$regex" "Export"}}]}}
          {"$group" {:_id   {:datetime {"$dateToString" {:format "%Y-%m"
                                                         :date   "$datetime"}}}
                     :count {"$sum" 1}}}
          {"$project" {:datetime "$_id.datetime" :count "$count"}}
          {"$sort" {:datetime -1}}])
    vec))

(defmethod get-response :report-logging [entity request user]
  (let [excel (dal/std-get-by-id :file-export-temp user (:params request))]
    (dal/std-delete :file-export-temp user (:params request))
    (file-generation (str (:title excel)
                       (when config/production-mode "-(Secret)")
                       "-" (let [sdf (SimpleDateFormat. "yyyyMMdd-HHmmss")]
                             (.setTimeZone sdf (TimeZone/getTimeZone
                                                 "America/New_York"))
                             (.format sdf (Date.)))
                       ".xlsx")
      (:bytes excel) "reportLogging")))

(defmethod create-response :report-logging [entity request user]
  (let [body        (parse-body (:body request))
        title       (:title body)
        type       (:type body)
        data        (assoc-in body [:data 0 :data]
                      (access-log-query (ck/camel-str->kebab-kywd type)
                        "" "" "" ""))
        excel-bytes (excel/build-excel data)
        id          (dal/create :file-export-temp user {}
                      {:bytes excel-bytes :title title})]
    (json-200 {:_id id})))
