FROM docker.io/library/node@sha256:43aeff40f4afc22e83f7589a2f37e111cff5ca84529571f1c8415bcc5fcc21b2
WORKDIR /opt/r4-issuer

COPY ["deadline.mjs","gate.mjs","jobs.mjs","listener.mjs","records.mjs","service-auth.mjs","service-entrypoint.mjs","storage-store.mjs","transport.mjs","./"]
COPY ["runtime/closure-evidence.mjs","runtime/control-evidence.mjs","runtime/decision-digests.mjs","runtime/dispatch-authorization.mjs","runtime/gate.mjs","runtime/job-bindings.mjs","runtime/job-client.mjs","runtime/managed-provider-ports.mjs","runtime/protected-service-ports.mjs","runtime/startup.mjs","runtime/stop-job.mjs","runtime/transport.mjs","runtime/writer-job.mjs","./runtime/"]

RUN ["/usr/local/bin/node", "-e", "if (process.platform !== 'linux' || process.arch !== 'x64' || process.versions.node !== '22.23.2') throw new Error('Unsupported R4 issuer runtime')"]

USER 1000:1000
ENTRYPOINT ["/usr/local/bin/node"]
CMD ["-e", "process.stderr.write('R4 issuer role required\\n'); process.exitCode = 64"]
