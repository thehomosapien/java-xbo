# Quick Start

> Note: To run docker you need start docker service.

**Build a local docker image**

```shell
> cd java-xbo/docker
> docker image build -t xbo-node .
```

**Run built image（refer to the home page）**

```shell
> docker container run -p 18888:18888 -p 50051:50051 -it xboprotocol/xbo-node /bin/bash
> ./gradlew run -Pwitness
```