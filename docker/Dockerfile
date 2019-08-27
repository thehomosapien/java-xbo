FROM xboprotocol/xbo-gradle

RUN set -o errexit -o nounset \
#  Download and build java-xbo
    && echo "git clone" \
    && git clone https://github.com/xboprotocol/java-xbo.git \
    && cd java-xbo \
    && gradle build

# Change work directory
WORKDIR /java-xbo

# open port 18888
EXPOSE 18888

