(ns spec-tools.data-spec
  "Data Specs"
  (:refer-clojure :exclude [or])
  (:require [spec-tools.impl :as impl]
            [spec-tools.core :as st]
            [spec-tools.form :as form]
            [clojure.spec.alpha :as s]))

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

(declare spec)

(defn- -map-spec [n data]
  ;; predicate keys
  (if-let [[k' v'] (and (= 1 (count data))
                        (let [[k v] (first data)]
                          (and
                            (not
                              (clojure.core/or (keyword? k)
                                  (wrapped-key? k)))
                            [k v])))]
    (st/create-spec {:spec (map-of-spec (spec n k' false) (spec n v'))})
    ;; keyword keys
    (let [m (reduce-kv
              (fn [acc k v]
                (let [kv (unwrap-key k)
                      rk (keyword
                           (str (if (req? k) "req" "opt")
                                (if-not (qualified-keyword? kv) "-un")))
                      [v wrap] (if (maybe? v)
                                 [(:v v) (comp #(st/create-spec {:spec %}) nilable-spec)]
                                 [v identity])
                      [k' n'] (if (qualified-keyword? kv)
                                [kv (if (not= kv v) kv)]
                                (let [k' (keyword (str (str (namespace n) "$" (name n)) "/" (name (unwrap-key kv))))]
                                  [k' k']))
                      v' (if n' (wrap (spec n' v)))]
                  (-> acc
                      (update rk (fnil conj []) k')
                      (cond-> v' (update ::defs (fnil conj []) [k' v'])))))
              {}
              data)
          defs (::defs m)
          margs (apply concat (dissoc m ::defs))]
      (doseq [[k s] defs]
        (impl/register-spec! k s))
      (st/create-spec {:spec (keys-spec margs)}))))

(defn- -coll-spec [n v proto]
  (when-not (= 1 (count v))
    (throw
      (ex-info
        (str "data-spec collection " proto
             " should be homogeneous, " (count v)
             " values found")
        {:name n
         :proto proto
         :values v})))
  (let [spec (spec n (first v))]
    (st/create-spec {:spec (coll-of-spec spec proto)})))

(defn- -or-spec [v]
  (let [pairs (map (fn [[k v]] [k (spec k v)]) (:v v))
        ks (mapv first pairs)
        forms (mapv second pairs)]
    (s/or-spec-impl ks forms forms nil)))

(defn spec
  ([name x]
   (spec name x true))
  ([name x coll-specs?]
   (cond
     (st/spec? x) x
     (s/regex? x) x
     (or? x) (-or-spec x)
     (and coll-specs? (map? x)) (-map-spec name x)
     (and coll-specs? (set? x)) (-coll-spec name x #{})
     (and coll-specs? (vector? x)) (-coll-spec name x [])
     :else (st/create-spec {:spec x}))))
