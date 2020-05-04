(ns spec-tools.spell
  (:require [spec-tools.spell-spec.alpha :as ssa]
            [spec-tools.parse :as parse]
            [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [spec-tools.impl :as impl]
            [expound.alpha :as expound]
            [expound.ansi :as ansi]))

(defn- pre-check [& specs]
  (let [pre (butlast specs)
        spec (last specs)]
    (reify
      s/Specize
      (specize* [s] s)
      (specize* [s _] s)
      s/Spec
      (conform* [_ x]
        (if (every? #(s/valid? % x) pre)
          (s/conform* spec x)
          ::s/invalid))
      (unform* [_ x] (s/unform* spec x))
      (explain* [_ path via in x]
        (if-let [problems (some #(s/explain* % path via in x) pre)]
          problems
          (s/explain* spec path via in x)))
      (gen* [_ a b c]
        (s/gen* spec a b c))
      (with-gen* [_ gfn]
        (s/with-gen* spec gfn))
      (describe* [_] (s/describe* spec)))))

(defn- spell-spec [mode spec]
  (if-let [keys (->> spec spec-tools.parse/parse-spec parse/get-keys)]
    (let [data (case mode
                 :closed {:spec keys, :form keys}
                 :misspelled {:spec (ssa/not-misspelled keys)
                              :form `(ssa/not-misspelled ~keys)})]
      (pre-check
        (ssa/warning-spec
          (impl/map-of-spec
            (ssa/map-explain ssa/enhance-problem (st/create-spec data)) any?))
        spec))
    (throw (ex-info (str "Can't read keys from spec: " spec) {:spec spec, :mode mode}))))

;;
;; Public API
;;

(defn closed [spec] (spell-spec :closed spec))
(defn misspelled [spec] (spell-spec :misspelled spec))

(defn closed-keys [data]
  (closed (impl/keys-spec data)))

(defn explain-str [spec data]
  (if-let [explain-data (s/explain-data spec data)]
    (binding [ansi/*enable-color* true]
      (#'expound/printer-str
        {:print-specs? false
         :show-valid-values? false}
        (if explain-data
          (assoc explain-data
            ::s/value data)
          nil)))))

(defn explain [spec data]
  (some-> (explain-str spec data) (println)))
