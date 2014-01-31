(defproject puppetlabs/puppetlabs-mq "0.1.0-SNAPSHOT"
  :description "A simple library to embed an ActiveMQ instance in your application."
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.apache.activemq/activemq-core "5.6.0" :exclusions [org.slf4j/slf4j-api org.fusesource.fuse-extra/fusemq-leveldb]]
                 [cheshire "5.2.0"]
                 [clamq/clamq-activemq "0.4" :exclusions [org.slf4j/slf4j-api]]
                 [clj-time "0.5.1"]
                 [fs "1.1.2"]
                 [org.slf4j/slf4j-log4j12 "1.7.5"]
                 [puppetlabs/trapperkeeper "0.1.1"]]
  :profiles {:dev {:dependencies [[puppetlabs/trapperkeeper "0.1.1" :classifier "test"]]}})
