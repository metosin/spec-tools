(ns spec-tools.data-spec
  "Data Specs"
  (:refer-clojure :exclude [or])
  (:require [spec-tools.impl :as impl]
            [spec-tools.core :as st]
            [clojure.spec.alpha :as s]
            [spec-tools.parse :as parse]))

;;
;; Helpers
;;

(defrecord OptionalKey [k])
(defrecord RequiredKey [k])
(defrecord Maybe [v])
(defrecord Or [v])

(defn opt
  "Makes a key optional:

  ```clojure
  {:name string?
   (ds/opt :age) int?}
  ```"
  [k]
  (->OptionalKey k))

(defn opt?
  "Test if the key is wrapped with [[opt]]"
  [x]
  (instance? OptionalKey x))

(defn req
  "Makes a key required:

  ```clojure
  {:name string?
   (ds/req :age) int?}
  ```"
  [k]
  (->RequiredKey k))

(defn req?
  "Test if the key is wrapped with [[req]]"
  [x]
  (not (opt? x)))

(defn maybe
  "Makes a value nillable:

  ```clojure
  {:name string?
   :age (ds/maybe int?)}
  ```"
  [v]
  (->Maybe v))

(defn maybe?
  "Test if the value is wrapped with [[maybe]]"
  [x]
  (instance? Maybe x))

(defn or [v] (->Or v))

(defn or? [x] (instance? Or x))

(defn wrapped-key?
  "Test if the key is wrapped with [[opt]] or [[req]]"
  [x]
  (clojure.core/or (opt? x) (instance? RequiredKey x)))

(defn unwrap-key
  "Unwrap the [[opt]] or [[req]] key."
  [x]
  (if (wrapped-key? x) (:k x) x))

;;
;; Implementation
;;

(declare spec)

(defn- -nested-key [n k]
  (assert (qualified-keyword? n) "spec must have a qualified name")
  (keyword (str (namespace n) "$" (name n)
                (if-let [kns (namespace k)]
                  (str "$" kns)) "/" (name k))))

(defn- -map-spec [data {n :name :keys [keys-spec keys-default] :or {keys-spec impl/keys-spec} :as opts}]
  ;; predicate keys
  (if-let [[k' v'] (and (= 1 (count data))
                        (let [[k v] (first data)]
                          (and
                            (not
                              (clojure.core/or (keyword? k)
                                               (wrapped-key? k)))
                            [k v])))]
    (st/create-spec {:spec (impl/map-of-spec (spec n k') (spec {:name n, :spec v'}))})
    ;; keyword keys
    (let [m (reduce-kv
              (fn [acc k v]
                (let [k (if (and keys-default (keyword? k)) (keys-default k) k)
                      kv (unwrap-key k)
                      rk (keyword
                           (str (if (req? k) "req" "opt")
                                (if-not (qualified-keyword? kv) "-un")))
                      [v wrap] (if (maybe? v)
                                 [(:v v) (comp #(st/create-spec {:spec %}) impl/nilable-spec)]
                                 [v identity])
                      [k' n'] (if (qualified-keyword? kv)
                                [kv (if (not= kv v) kv)]
                                (let [k' (-nested-key n (unwrap-key kv))]
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
    (st/create-spec {:spec (impl/coll-of-spec spec kind)})))

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
  (impl/or-spec (-> (for [[k v] v]
                      [k (spec (-nested-key n k) v)])
                    (into {}))))

;;
;; Api
;;

(defn spec
  "Creates a `clojure.spec.alpha/Spec` out of a data-spec. Supports 2 arities:

  ```clojure
  ;; arity1
  (ds/spec
    {:spec {:i int?}
     :name ::map})

  ;; arity2 (legacy)
  (ds/spec ::map {:i int?})
  ```

  The following options are valid for the 1 arity case:

  | Key              | Description
  | -----------------|----------------
  | `:spec`          | The wrapped data-spec.
  | `:name`          | Qualified root spec name - used to generate unique names for sub-specs.
  | `:keys-spec`     | Function to wrap not-wrapped keys, e.g. [[opt]] to make keys optional by default.
  | `:keys-default`  | Function to generate the keys-specs, default [[keys-specs]]."
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
       (maybe? data) (impl/nilable-spec (spec name (:v data)))
       (map? data) (named-spec (-map-spec data nested-opts))
       (set? data) (maybe-named-spec (-coll-spec data (assoc nested-opts :kind #{})))
       (vector? data) (maybe-named-spec (-coll-spec data (assoc nested-opts :kind [])))
       :else (maybe-named-spec (st/create-spec {:spec data})))))
  ([name data]
   (spec {:name name, :spec data})))
