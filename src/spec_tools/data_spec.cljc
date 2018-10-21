(ns spec-tools.data-spec
  "Data Specs"
  (:refer-clojure :exclude [or])
  (:require [spec-tools.impl :as impl]
            [spec-tools.core :as st]
            [spec-tools.form :as form]
            [clojure.spec.alpha :as s]
            [spec-tools.parse :as parse]))

;;
;; functional clojure.spec
;;

(defn- coll-of-spec [pred type]
  (let [form (form/resolve-form pred)]
    (clojure.spec.alpha/every-impl
      form
      pred
      {:into type
       ::s/conform-all true
       ::s/describe `(s/coll-of ~form :into ~type),
       ::s/cpred coll?,
       ::s/kind-form (quote nil)}
      nil)))

(defn- map-of-spec [kpred vpred]
  (let [forms (map form/resolve-form [kpred vpred])
        tuple (s/tuple-impl forms [kpred vpred])]
    (clojure.spec.alpha/every-impl
      `(s/tuple ~@forms)
      tuple
      {:into {}
       :conform-keys true
       ::s/conform-all true
       ::s/describe `(s/map-of ~@forms :conform-keys true),
       ::s/cpred coll?,
       ::s/kind-form (quote nil)}
      nil)))

(defn- keys-spec [{:keys [req opt req-un opt-un]}]
  (let [req-specs (flatten (map impl/polish (concat req req-un)))
        opt-specs (flatten (map impl/polish (concat opt opt-un)))
        req-keys (flatten (concat (map impl/polish req) (map impl/polish-un req-un)))
        opt-keys (flatten (concat (map impl/polish opt) (map impl/polish-un opt-un)))
        pred-exprs (concat
                     [#(map? %)]
                     (map (fn [x] #(contains? % x)) req-keys))
        pred-forms (concat
                     [`(fn [~'%] (map? ~'%))]
                     (map (fn [k] `(fn [~'%] (contains? ~'% ~k))) req-keys))
        keys-pred (fn [x]
                    (reduce
                      (fn [_ p]
                        (clojure.core/or (p x) (reduced false)))
                      true
                      pred-exprs))]

    (s/map-spec-impl
      {:req-un req-un
       :opt-un opt-un
       :pred-exprs pred-exprs
       :keys-pred keys-pred
       :opt-keys opt-keys
       :req-specs req-specs
       :req req
       :req-keys req-keys
       :opt-specs opt-specs
       :pred-forms pred-forms
       :opt opt})))

(defn- nilable-spec [pred]
  (let [form (form/resolve-form pred)]
    (s/nilable-impl form pred nil)))

(defn- or-spec [v]
  (let [ks (mapv first v)
        preds (mapv second v)
        forms (mapv form/resolve-form preds)]
    (s/or-spec-impl ks forms preds nil)))

;;
;; Data Specs
;;

(defrecord OptionalKey [k])
(defrecord RequiredKey [k])
(defrecord Maybe [v])
(defrecord Or [v])

(defn opt [k] (->OptionalKey k))
(defn req [k] (->RequiredKey k))
(defn maybe [v] (->Maybe v))
(defn or [v] (->Or v))

(defn opt? [x]
  (instance? OptionalKey x))

(defn req? [x]
  (not (opt? x)))

(defn wrapped-key? [x]
  (clojure.core/or (opt? x) (instance? RequiredKey x)))

(defn unwrap-key [x]
  (if (wrapped-key? x) (:k x) x))

(defn maybe? [x]
  (instance? Maybe x))

(defn or? [x]
  (instance? Or x))

(defn nested-key [n k]
  (assert (qualified-keyword? n) "spec must have a qualified name")
  (keyword (str (namespace n) "$" (name n)
                (if-let [kns (namespace k)]
                  (str "$" kns)) "/" (name k))))

(declare spec)

(defn- -map-spec [data {n :name :keys [keys-spec keys-default] :or {keys-spec keys-spec} :as opts}]
  ;; predicate keys
  (if-let [[k' v'] (and (= 1 (count data))
                        (let [[k v] (first data)]
                          (and
                            (not
                              (clojure.core/or (keyword? k)
                                               (wrapped-key? k)))
                            [k v])))]
    (st/create-spec {:spec (map-of-spec (spec n k') (spec {:name n, :spec v'}))})
    ;; keyword keys
    (let [m (reduce-kv
              (fn [acc k v]
                (let [k (if (and keys-default (keyword? k)) (keys-default k) k)
                      kv (unwrap-key k)
                      rk (keyword
                           (str (if (req? k) "req" "opt")
                                (if-not (qualified-keyword? kv) "-un")))
                      [v wrap] (if (maybe? v)
                                 [(:v v) (comp #(st/create-spec {:spec %}) nilable-spec)]
                                 [v identity])
                      [k' n'] (if (qualified-keyword? kv)
                                [kv (if (not= kv v) kv)]
                                (let [k' (nested-key n (unwrap-key kv))]
                                  [k' k']))
                      v' (if n' (wrap (spec (-> opts (assoc :name n') (assoc :spec v)))))]
                  (-> acc
                      (update rk (fnil conj []) k')
                      (cond-> v' (update ::defs (fnil conj []) [k' v'])))))
              {}
              data)
          defs (::defs m)
          data (apply hash-map (apply concat (dissoc m ::defs)))]
      (doseq [[k s] defs]
        (let [synthetic? (and (st/spec? s) (not (parse/collection-type? s)))]
          (impl/register-spec! k (cond-> s synthetic? (assoc ::st/synthetic? true)))))
      (st/create-spec {:spec (keys-spec data)}))))

(defn- -coll-spec [data {n :name kind :kind}]
  (when-not (= 1 (count data))
    (throw
      (ex-info
        (str "data-spec collection " kind
             " should be homogeneous, " (count data)
             " values found")
        {:name n
         :kind kind
         :values data})))
  (let [spec (spec n (first data))]
    (st/create-spec {:spec (coll-of-spec spec kind)})))

(defn- -or-spec [n v]
  (when-not (and
              (map? v)
              (every? keyword? (keys v)))
    (throw
      (ex-info
        (str "data-spec or must be a map of keyword keys -> specs, "
             v " found")
        {:name n
         :value v})))
  (or-spec (-> (for [[k v] v]
                 [k (spec (nested-key n k) v)])
               (into {}))))

(defn spec
  "Creates a clojure.spec.alpha/Spec out of a data-spec. Supports 2 arities:

     ;; arity1
     (ds/spec
       {:spec {:i int?}
        :name ::map})

     ;; arity2 (legacy)
     (ds/spec ::map {:i int?})

  The following options are valid for the 1 arity case:

               :spec the data-spec form (required)
               :name fully qualified keyword name for the spec, used in keys-spec
                     key spec registration, required if there are non-qualified
                     keyword keys in maps.
          :keys-spec function to generate keys-spec (default: [[keys-spec]])
       :keys-default optional function to wrap the plain keyword keys, e.g. setting
                     the value to [[opt]] maes all plain keyword keys optional."
  ([{data :spec name :name nested? ::nested? :as opts}]
   (assert spec "missing :spec predicate in data-spec")
   (let [opts (-> opts (assoc :name name) (dissoc :spec))
         named-spec #(assoc % :name name)
         maybe-named-spec #(cond-> % (not nested?) named-spec)
         nested-opts (assoc opts ::nested? true)]
     (cond
       (st/spec? data) (maybe-named-spec data)
       (s/regex? data) data
       (or? data) (-or-spec name (:v data))
       (maybe? data) (nilable-spec (spec name (:v data)))
       (map? data) (named-spec (-map-spec data nested-opts))
       (set? data) (maybe-named-spec (-coll-spec data (assoc nested-opts :kind #{})))
       (vector? data) (maybe-named-spec (-coll-spec data (assoc nested-opts :kind [])))
       :else (maybe-named-spec (st/create-spec {:spec data})))))
  ([name data]
   (spec {:name name, :spec data})))
