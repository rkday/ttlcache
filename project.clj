(defproject uk.me.rkd.ttlcache "0.1.0"
  :description "A variation of core.cache's TTLCache specialised for some different cases"
  :url "https://github.com/rkday/ttlcache"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.cache "0.6.3"]
                 [criterium "0.4.3"]
                 [org.clojure/data.priority-map "0.0.4"]]
  :jvm-opts ^:replace ["-server"] )
