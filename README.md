# akka-wits (*W*here *I*s *t*his *S*ervice)

Simple project for testing Akka Cluster behavior and having a service registry to locate remote services.

The basic idea is to have a service registry running on each node and exchanging their local service addresses. 
Services are available through a local proxy for each remote service needed. The service proxy is routing request to the 
node running the service.


## Usage

* On each node you must start a ServiceRegistry
* Shared messages must be stored in a share project between service and client. 
* Service can run on one node and made available on another node.
* Creating a service is as simple as extending ServiceActor
* Using a service is as simple as starting a ServiceProxy


# Running the sample

Inside `sbt`, run the following command to start a client:
```
sample/runMain SampleClientMain
```

And the following command to start a remote service provider:
```
sample/runMain SampleServiceMain
```

Press `CTRL+D` to exit from the underlying process.
