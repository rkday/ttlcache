;; Copyright 2014 Rob Day
;; Released under the EPL


(ns uk.me.rkd.ttlcache
  (:require [clojure.data.priority-map :refer [priority-map]]
            [clojure.core.cache :refer [CacheProtocol defcache]]))

(defn- expires [ttl] (+ ttl (System/currentTimeMillis)))

(defn- setval [newkey newval ttl [info ttls :as cache]]
  [(assoc info newkey newval) (assoc ttls newkey (expires ttl))])

(defn- expire*
  ([info ttls now]
     (if (empty? ttls)
       [(persistent! info) ttls]
       (let [[key expires] (peek ttls)]
         (if (> now expires)
           (do
             (comment println "Expiring " key)
             (recur (dissoc! info key) (pop ttls) now))
           [(persistent! info) ttls]
           )))))


(defn- expire
  ([cache]
     (expire cache (System/currentTimeMillis)))
  ([[info ttls :as cache] now]
     (expire* (transient info) ttls now)))

(defcache PerItemTTLCache [cache expiry-heap get-ttl]
  CacheProtocol
  (lookup [this item]
          (let [ret (. this lookup item ::nope)]
            (when-not (= ret ::nope) ret)))
  (lookup [this item not-found]
          (if (. this has? item)
            (get cache item)
            not-found))
  (has? [_ item]
        (let [expires (get expiry-heap item 0)]
          (< (System/currentTimeMillis) expires)))
  (hit [this item] this)
  (miss [this item result]
        (let [start (System/currentTimeMillis)
              updated-cache (setval item result (get-ttl item result) [cache expiry-heap])
              _ (comment println "Updated cache after " (- (System/currentTimeMillis) start))
              after-expiry (expire updated-cache)
              _ (comment println "Expired cache after " (- (System/currentTimeMillis) start))
              ]
          (PerItemTTLCache. (first after-expiry)
                            (second after-expiry)
                            get-ttl)))
  (seed [_ base]
    (let [now (System/currentTimeMillis)]
      (PerItemTTLCache. base
                        (into (priority-map) (for [x base] [(key x) (+ now (get-ttl (key x) (val x)) )]))
                        get-ttl)))
  (evict [_ key]
    (PerItemTTLCache. (dissoc cache key)
                      expiry-heap
                      get-ttl))
  Object
  (toString [_]
    (str cache \, \space expiry-heap)))


(defn per-item-ttl-cache-factory
  "Returns a TTL cache with the cache and expiration-table initialied to `base` --
each with the same time-to-live.

This function also allows an optional `:ttl` argument that defines the default
time in milliseconds that entries are allowed to reside in the cache."
  [base & {get-ttl :ttl-getter}]
  {:pre [(map? base)
         (fn? get-ttl)]}
  (clojure.core.cache/seed (PerItemTTLCache. {} (priority-map) get-ttl) base))

(defn ttl-cache-factory
  "Returns a TTL cache with the cache and expiration-table initialied to `base` --
each with the same time-to-live.

This function also allows an optional `:ttl` argument that defines the default
time in milliseconds that entries are allowed to reside in the cache."
  [base & {ttl :ttl :or {ttl 2000}}]
  {:pre [(number? ttl) (<= 0 ttl)
         (map? base)]}
  (per-item-ttl-cache-factory base :ttl-getter (constantly ttl)))

