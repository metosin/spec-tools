(ns spec-tools.data
  "Data Specs"
  (:require [spec-tools.impl :as impl]
            [spec-tools.core :as st]
            [clojure.spec :as s]))

;; FIXME: uses ^:skip-wiki functions from clojure.spec

(defrecord OptionalKey [k])
(defrecord RequiredKey [k])
(defrecord Maybe [v])

(defn opt [k] (->OptionalKey k))
(defn req [k] (->RequiredKey k))
(defn maybe [v] (->Maybe v))

(defn opt? [x]
  (instance? OptionalKey x))

(defn req? [x]
  (not (opt? x)))

(defn wrapped-key? [x]
  (or (opt? x) (instance? RequiredKey x)))

(defn unwrap-key [x]
  (if (wrapped-key? x) (:k x) x))

(defn maybe? [x]
  (instance? Maybe x))

(declare data-spec)

(defn- -map-spec [n data]
  ;; predicate keys
  (if-let [[k' v'] (and (= 1 (count data))
                        (let [[k v] (first data)]
                          (and
                            (not
                              (or (keyword? k)
                                  (wrapped-key? k)))
                            [k v])))]
    (st/create-spec {:spec (impl/map-of-spec (data-spec n k' false) (data-spec n v'))})
    ;; keyword keys
    (let [m (reduce-kv
              (fn [acc k v]
                (let [kv (unwrap-key k)
                      rk (keyword
                           (str (if (req? k) "req" "opt")
                                (if-not (qualified-keyword? kv) "-un")))
                      [v wrap] (if (maybe? v)
                                 [(:v v) (comp #(st/create-spec {:spec %}) impl/nilable-spec)]
                                 [v identity])
                      [k' n'] (if (qualified-keyword? kv)
                                [kv (if (not= kv v) kv)]
                                (let [k' (keyword (str (str (namespace n) "$" (name n)) "/" (name (unwrap-key kv))))]
                                  [k' k']))
                      v' (if n' (wrap (data-spec n' v)))]
                  (-> acc
                      (update rk (fnil conj []) k')
                      (cond-> v' (update ::defs (fnil conj []) [k' v'])))))
              {}
              data)
          defs (::defs m)
          margs (apply concat (dissoc m ::defs))]
      (doseq [[k s] defs]
        (impl/register-spec! k s))
      (st/create-spec {:spec (impl/keys-spec margs)}))))

(defn- -coll-spec [n v proto]
  (when-not (= 1 (count v))
    (throw
      (ex-info
        (str "only single maps allowed in nested " proto)
        {:k n :v v})))
  (let [spec (data-spec n (first v))]
    (st/create-spec {:spec (impl/coll-of-spec spec proto)})))

(defn data-spec
  ([name x]
    (data-spec name x true))
  ([name x coll-specs?]
   (cond
     (st/spec? x) x
     (s/regex? x) x
     (and coll-specs? (map? x)) (-map-spec name x)
     (and coll-specs? (set? x)) (-coll-spec name x #{})
     (and coll-specs? (vector? x)) (-coll-spec name x [])
     :else (st/create-spec {:spec x}))))
