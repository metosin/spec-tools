(ns spec-tools.spell
  (:require [spell-spec.alpha :as ssa]
            [spell-spec.expound]
            [spec-tools.parse :as parse]
            [clojure.spec.alpha :as s]
            [spec-tools.core :as st]
            [spec-tools.impl :as impl]
            [spell-spec.expound]
            [expound.alpha :as expound]
            [expound.ansi :as ansi]))

(defrecord Problems [values])

(defn- flatten-problems [m]
  (if m
    ((fn flatten-helper [keyseq m]
       (when m
         (cond
           (and (map? m) (not (instance? Problems m)))
           (mapcat (fn [[k v]] (flatten-helper (conj keyseq k) v)) m)
           (or (set? m) (sequential? m))
           (map-indexed (fn [k v] (flatten-helper (conj keyseq k) v)) m)
           :else [[keyseq m]])))
     [] m)))

(defn- via [spec path]
  (if-let [spec (and (qualified-keyword? spec) spec)]
    (loop [via [spec], [p & ps] path]
      (if p
        (let [{:keys [::parse/key->spec]} (parse/parse-spec (last via))]
          (let [spec (get key->spec p)]
            (recur (conj via spec) ps)))
        via))))

(defn- problems [spec x paths]
  {::s/problems (for [[path {:keys [values]}] paths
                      problem values]
                  (assoc problem
                    :path (conj path 0)
                    :in nil
                    :via (via spec path)))
   ::s/spec spec
   ::s/value x})

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
  (if-let [keys (->> spec spec-tools.parse/parse-spec :spec-tools.parse/keys)]
    (let [data (case mode
                 :closed {:spec keys, :form keys}
                 :misspelled {:spec (ssa/not-misspelled keys)
                              :form `(ssa/not-misspelled ~keys)})]
      (pre-check
        (ssa/warning-spec
          (impl/map-of-spec
            (ssa/map-explain ssa/enhance-problem (st/create-spec data)) any?))
        spec))
    (throw (ex-info (str "Can't close non-keys specs: " spec) {:spec spec, :mode mode}))))

;;
;; Via Coercion
;;

(defn strict-keys-decoder [{:keys [::parse/keys]} x]
  (if-let [problems (and (map? x)
                         (reduce-kv
                           (fn [problems k _]
                             (if (keys k)
                               problems
                               (conj
                                 problems
                                 (ssa/enhance-problem
                                   {:val k, :pred keys}))))
                           nil x))]
    (->Problems problems)
    x))

(def strict-keys-transformer
  (st/type-transformer
    {:name ::strict-keys
     :decoders {:map strict-keys-decoder}}))

(defn explain-strict-data [spec x]
  (->> (st/coerce spec x strict-keys-transformer)
       (flatten-problems)
       (problems spec x)))

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
