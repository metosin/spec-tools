(ns spec-tools.spell
  (:require [spell-spec.alpha :as ssa]
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

(defn- spell-spec [mode spec]
  (let [keys (->> spec spec-tools.parse/parse-spec :spec-tools.parse/keys)
        data (case mode
               :closed {:spec keys, :form keys}
               :misspelled {:spec (ssa/not-misspelled keys)
                            :form `(ssa/not-misspelled ~keys)})]
    (ssa/pre-check
      (impl/map-of-spec (ssa/map-explain ssa/enhance-problem (st/create-spec data)) any?)
      spec)))

;;
;; Public API
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

(defn explain-data [spec x]
  (->> (st/coerce spec x strict-keys-transformer)
       (flatten-problems)
       (problems spec x)))

(defn closed-keys [spec] (spell-spec :closed spec))
(defn misspelled-keys [spec] (spell-spec :misspelled spec))

(defn strict-keys [args]
  (eval `(ssa/strict-keys ~@(apply concat args))))

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

(comment
  (ns user
    (:require [clojure.spec.alpha :as s]
              [spec-tools.spell]
              [spec-tools.core :as st]))

  (s/def ::name string?)
  (s/def ::use-history boolean?)
  (s/def ::config (st/spec (s/keys :opt-un [::name ::use-history])))
  (s/def ::options (s/keys :opt-un [::config]))

  (def data {:config {:name "John" :use-hisory false :countr 1}})

  (spec-tools.spell/explain-data ::options data)

  (s/explain-data (spec-tools.spell/closed-keys ::options) data)

  (s/valid? (spec-tools.spell/closed-keys ::options) data))
