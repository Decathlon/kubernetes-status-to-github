FROM ubuntu:22.04

USER 1000

WORKDIR /workspace

ENTRYPOINT ["/workspace/kubernetes-status"]


COPY target/kubernetes-status /workspace/kubernetes-status
