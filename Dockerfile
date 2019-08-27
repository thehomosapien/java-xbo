FROM xboprotocol/xbo-gradle

RUN set -o errexit -o nounset \
    && echo "git clone" \
    && git clone https://github.com/xboprotocol/java-xbo.git \
    && cd java-xbo \
    && gradle build

WORKDIR /java-xbo

EXPOSE 18888