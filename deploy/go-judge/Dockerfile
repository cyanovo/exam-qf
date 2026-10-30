FROM python:3.12-slim@sha256:09f7da3bc104798d0afb40bc08d23ab2da20a76130cec1f2ef170848f5d85217
# The build context must contain the checksum-verified official Linux release binary.
COPY --chmod=0555 go-judge /opt/go-judge
ENTRYPOINT ["/opt/go-judge"]
CMD ["-http-addr=:5050", "-parallelism=2", "-no-fallback", "-output-limit=64k", "-copy-out-limit=64k", "-tmp-fs-param=size=32m,nr_inodes=1k"]
