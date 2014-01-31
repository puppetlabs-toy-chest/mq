# puppetlabs-mq

A simple library to embed an ActiveMQ instance in your clojure application.

[![CI Build Status](https://travis-ci.org/puppetlabs/mq.png?branch=master)](https://travis-ci.org/puppetlabs/mq)

## Usage

```clj
(ns user (:require [com.puppetlabs.mq :refer :all]))

;; Create and start a broker
(def my-broker (doto (build-embedded-broker "my-broker" "/tmp/broker-storage")
                 (.setUseJmx false)
                 (.setPersistent false)))
(start-broker! my-broker)

;; Connect to the broker:
(def connection (connect! "vm://my-broker"))

;; Publish a message to the "hello-world" queue:
(connect-and-publish! connection "hello-world" "Hello, World!")

;; Retrieve the published message from the queue:
(bounded-drain-into-vec! connection "hello-world" 1)
;; => ["Hello, World!"]

;; Stop the broker
(stop-broker! my-broker)

;; delete the storage directory
(clojure.java.shell/sh "rm" "-r" "/tmp/broker-storage")
    ;; done this way for brevity; IRL do this with e.g. https://clojars.org/fs
```

## License

Copyright © 2014 Puppet Labs

Distributed under the [Apache License, version 2](http://www.apache.org/licenses/).
