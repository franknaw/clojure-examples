(ns notional.reserve-flpp
  (:require [notional.utility :as util]
            [dk.ative.docjure.spreadsheet :as xls]))

(defn gen--dlpp-record
  "Generate a DLPP reserve notional record." [data]
  (let [strop (util/gen-start-stop-dt (util/gen-date))]
    {:voucher-dt  (util/format-yyyy-MM-dd (util/gen-date))
     :voucher-nbr (:voucher-nbr data)
     :ssan        (:ssan data)
     :amt         (util/gen-amt)
     :apc         (util/gen-apc)
     :fy          0
     :comp        "UG"
     :start-dt    (:start_dt strop)
     :stop-dt     (:stop_dt strop)
     :cbpo        (util/gen-cbpo)
     :tin         (:tin data)
     :service-id  (:service-id data)}))

(defn gen-dlpp-records
  "Generate a lazy sequence of DLPP reserve notional records." [counts]
  (let [ssns (take (:ssn-count counts)
               (repeatedly #(aicig-notional.utility/gen-ssn)))]
    (for [ssn ssns]
      (let [data1 {:voucher-nbr (util/gen-voucher-nbr)
                   :ssan        ssn
                   :tin         (util/gen-tin)
                   :service-id  (util/gen-service-id)}
            rec1 (take (:record-count counts)
                   (repeatedly
                     #(gen-dlpp-record data1)))
            data2 {:voucher-nbr (util/gen-voucher-nbr)
                   :ssan        ssn
                   :tin         (util/gen-tin)
                   :service-id  (util/gen-service-id)}
            rec2 (take (:record-count counts)
                   (repeatedly
                     #(gen--dlpp-record data2)))]
        (merge rec1 rec2)))))

(defn gen--dlpp-file-data
  "Generate DLPP reserve notional records, formatted as XLS data."
  [counts]
  (let [columns [:voucher-dt "VOUCHER_DT"
                 :voucher-nbr "VOUCHER_NBR"
                 :ssan "SSAN"
                 :amt "AMT"
                 :apc "APC"
                 :fy "FY"
                 :comp "COMP"
                 :start-dt "START_DT"
                 :stop-dt "STOP_DT"
                 :cbpo "CBPO"
                 :tin "TIN"
                 :service-id "SERVICE_ID"]]
    (as-> (flatten (gen-dlpp-records counts)) $
      (map #(map % (take-nth 2 columns)) $)
      (conj $ (take-nth 2 (rest columns))))))

(defn gen-dlpp-reserve-files
  "Generate `file-count` number of DLPP
  reserve spreadsheets, each containing
  `record-count` number of records."
  [counts]
  (->> (util/create-files
         {:filetype
                      :spreadsheet
          :file-gen
                      {"dlpp-reserve.xls"
                       #(xls/create-workbook "DLPP_RESERVE_OPEN"
                          (gen-dlpp-file-data counts))}
          :file-count (:file-count counts)})
    (util/write-files)))



