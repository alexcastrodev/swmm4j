# syntax=docker/dockerfile:1
FROM debian:bookworm-slim AS swmm
ARG SWMM_VERSION=v5.2.4
RUN apt-get update && apt-get install -y --no-install-recommends build-essential cmake git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
RUN git clone --depth 1 --branch ${SWMM_VERSION} https://github.com/USEPA/Stormwater-Management-Model.git /src \
    && cmake -S /src -B /build -DCMAKE_BUILD_TYPE=Release \
    && cmake --build /build --target swmm5 -j \
    && cp "$(find /build -name 'libswmm5.so' | head -1)" /libswmm5.so

FROM scratch AS jextract-amd64
ADD --checksum=sha256:d0cc481abc1adb16fb9514e1c5e0bfc08d38c29228bece667fb5054ceaffaa42 \
    https://download.java.net/java/early_access/jextract/25/2/openjdk-25-jextract+2-4_linux-x64_bin.tar.gz /jextract.tar.gz

FROM scratch AS jextract-arm64
ADD --checksum=sha256:0e25e6f6efa042f8758eaec65a873887fd2247fcf2e3e22dcfd7e4179fc8b0ae \
    https://download.java.net/java/early_access/jextract/25/2/openjdk-25-jextract+2-4_linux-aarch64_bin.tar.gz /jextract.tar.gz

FROM jextract-${BUILDARCH} AS jextract

FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
ARG PICOCLI_VERSION=4.7.7
WORKDIR /src
ADD --checksum=sha256:f86e30fffd10d2b13b8caa8d4b237a7ee61f2ffccf5b1941de718b765d235bf8 \
    https://repo1.maven.org/maven2/info/picocli/picocli/${PICOCLI_VERSION}/picocli-${PICOCLI_VERSION}.jar /jars/
COPY --from=jextract /jextract.tar.gz /tmp/
COPY --from=swmm /src/src/solver/include/swmm5.h /tmp/
RUN tar xzf /tmp/jextract.tar.gz -C /opt \
    && /opt/jextract-25/bin/jextract --output gen --target-package swmm4j.ffi --header-class-name swmm5 /tmp/swmm5.h
COPY Main.java ./
COPY swmm4j swmm4j
RUN javac -cp "/jars/*" -d /classes Main.java swmm4j/*.java gen/swmm4j/ffi/*.java

FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 && rm -rf /var/lib/apt/lists/*
COPY --from=swmm /libswmm5.so /opt/swmm/
COPY --from=build /classes /app/classes
COPY --from=build /jars /app/jars
ENV SWMM_LIB=/opt/swmm/libswmm5.so
WORKDIR /work
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-cp", "/app/classes:/app/jars/*", "Main"]
