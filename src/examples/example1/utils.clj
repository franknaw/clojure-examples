;;; Part of the utils file

(defn rand-long
  "Returns a random long between 0 (inclusive) and n (exclusive)."
  [n] (long (rand n)))

(defn rand-date-long "Returns random date as long" []
  (clj-time.coerce/to-long (gen-date)))

(defn rand-date-days "Returns random data as number of days since 1900" []
  (time/in-days (time/interval
                  (time/date-time 1900 01 01)
                  (gen-date))))

(defn rand-y-n "Returns Y or N" []
  (rand-nth ["Y" "N"]))

(defn rand-yyyy "Returns random year" []
  (format-date-string "YYYY" (gen-date)))

(defn rand-yyyymmdd "Returns random date in format YYYYMMdd" []
  (format-date-string "YYYYMMdd" (gen-date)))

(defn gen-ssn "" []
  (r-int-in-range 100000000 900000000))

(defn gen-r10ds "" []
  (str (r-int-in-range 1000000000 9000000000)))

(defn gen-amt "" []
  (r-int-in-range 99 777))

(defn gen-cbpo "" []
  (str (rand-nth ["R" "X" "Z" "S"]) (r-int-in-range 1 9)))

(defn gen-voucher-nbr "" []
  (str (rand-nth ["R" "X" "Z" "S"]) (format "%07d" (r-int-in-range 1 100))))

(defn gen-apc "" []
  (str (r-int-in-range 1 99) (rand-nth ["R1" "RE" "ZE" "GE"]) (r-int-in-range 1 99)))

(defn gen-service-id "" []
  (rand-nth ["G" "V" "Z" "S"]) )

(defn gen-tin "" []
  (str (rand-nth ["D" "S"]) (r-int-in-range 1 99)))

;;(aicig-notional.utility/gen-start-stop-dt (aicig-notional.utility/gen-date))
(defn gen-start-stop-dt "" [date]
  {:start_dt
            (time-format/unparse
              (time-format/formatter "yyyy-MM-dd") date)
   :stop_dt (->
              (LocalDate/parse
                (time-format/unparse
                  (time-format/formatter "yyyy-MM-dd") date))
              (.plusDays (r-int-in-range 5 99)) str)})
