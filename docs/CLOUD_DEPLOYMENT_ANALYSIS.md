# Cloud deployment analysis

**Reviewed:** 27 August 2026  
**Scope:** FastAPI, Celery worker/beat, PostgreSQL, Redis, MinIO-compatible audio storage, Sarvam,
and Firebase ID-token verification.

## Decision

No evaluated provider can run the complete stack continuously and durably on an ongoing free
tier. Free plans are suitable only for synthetic-data demos. For the least-change pilot, use a
paid VM/container platform with at least 2 GB RAM (preferably 4 GB for burst headroom), managed
PostgreSQL/Redis, and object storage. Of these candidates, **GCP is the strongest production
target** because Cloud Storage and Secret Manager avoid self-hosting sensitive storage/secrets,
but this requires adapting MinIO to GCS or retaining the existing S3 API behind a paid VM.

For a short-lived demo with no real patient data, **Railway is the simplest developer experience**.
Its free allowance is too small for continuous operation and its 0.5 GB volume is not an audio
retention solution.

## Provider comparison

| Provider | No-cost compute | Persistent audio | Secret handling | Fit |
|---|---|---|---|---|
| Fly.io | Trial only: 2 total VM-hours or 7 days; up to 4 GB RAM/2 vCPU per trial Machine. There is no ongoing free compute for new accounts. | Trial includes 20 GB volumes. Paid volumes are local, one Machine/one app, non-replicated, $0.15/GB-month; snapshots are not primary backups. Tigris is an external paid object-store option. | Per-app encrypted vault; secrets are injected as environment variables at Machine boot and values are not shown/logged. | Good low-cost container topology after billing, not free. MinIO needs a paid volume and backup strategy. |
| Render | Free web service: 512 MB/0.1 CPU, 750 workspace-hours/month, spins down after 15 minutes idle. Free background workers do not exist. | Free web filesystem is ephemeral and cannot attach a disk. Free Redis-compatible Key Value is 25 MB and loses data on restart. Paid disks attach to one instance only. | Environment variables, environment groups, and secret files; scope the Sarvam key to the paid worker. | API demo only. Celery and MinIO require paid services; free Redis is unsafe as a durable broker. |
| Railway | Free: $1 monthly usage credit; per-service ceiling 0.5 GB RAM/1 vCPU, one replica, 1 GB ephemeral disk. | One 0.5 GB volume per free project; one volume mounts to one service and prevents replicas. Object-storage buckets are preferable when available, but are usage billed. | Service/shared/reference variables; sealed values are write-only in UI/API, though available to builds and deployments. | Easiest demo. Credit, RAM, and storage are inadequate for API + worker + DB + Redis + MinIO continuously. |
| GCP | Always Free: one e2-micro VM-month in selected US regions (shared-core, 1 GB RAM), plus 30 GB standard persistent disk. Cloud Run has request/CPU/memory free quotas. | 5 GB-month Cloud Storage in selected US regions. A VM disk can host MinIO but is a single failure domain; Cloud Storage is the preferred durable replacement. | Secret Manager: 6 active versions and 10,000 accesses/month free; IAM and workload identity avoid static Google credentials. | Best controls and storage primitives, but e2-micro cannot reliably run the full stack. Cloud Run does not directly suit a continuously polling Celery worker, and Memorystore has no free tier. |

## Stack-specific implications

- The Compose stack is actually five stateful/runtime services: API, worker/beat, PostgreSQL,
  Redis, and MinIO. The worker performs network AI work and needs reliable continuous queue access.
- Do not put real audio on ephemeral filesystems, free Redis, or a single unbacked local volume.
- PaaS local disks are single-service attachments; MinIO can expose them over S3, but this creates
  a single-node storage service. Native object storage is safer for recordings and retention rules.
- GCP Cloud Run is attractive for FastAPI, but request-based CPU does not fit Celery polling.
  Instance-based CPU costs money; managed Redis also costs money. A redesign could replace Celery
  and Redis with Cloud Tasks/Pub/Sub and replace MinIO with Cloud Storage.
- The current Firebase verifier needs only `SO_FIREBASE_PROJECT_ID` and Google's public JWKS. It
  does **not** need a Firebase service-account private key. Add such credentials only for future
  Admin SDK features, using workload identity on GCP or a secret file elsewhere.

## Secret and data plan

1. Inject `SO_SARVAM_API_KEY` only into the worker, never the API, build arguments, image, or logs.
2. Inject database, Redis, and S3 credentials into API and worker; keep MinIO root credentials only
   on MinIO. Generate unique production values rather than Compose defaults.
3. Set `SO_FIREBASE_PROJECT_ID` as non-secret configuration. Do not deploy `google-services.json`
   to the backend; it is mobile configuration, not an Admin credential.
4. Use separate staging/production secrets, least-privilege service identities, rotation, quota and
   spend alerts, and audit logs. Test rotation without printing resolved configuration.
5. Encrypt storage and transport, keep services on private networking, enforce the 30-day purge,
   back up metadata, and validate region/residency requirements with counsel before real-patient use.

## Recommended deployment phases

1. **Demo:** Railway or Render, fake providers, synthetic audio only, no durability claim.
2. **Pilot:** GCP paid resources: Cloud Run FastAPI; a continuously allocated worker target; managed
   PostgreSQL and Redis; Cloud Storage; Secret Manager; private networking and budgets.
3. **Migration work:** implement a GCS storage adapter (or validate an S3-compatible managed store),
   separate Celery beat, add IaC, backups/restore drills, and staging end-to-end tests.

## Primary sources

- [Fly.io free trial](https://fly.io/docs/about/free-trial/), [pricing](https://fly.io/docs/about/pricing/), [volumes](https://fly.io/docs/volumes/overview/), [secrets](https://fly.io/docs/apps/secrets/)
- [Render free services](https://render.com/docs/free), [compute](https://render.com/docs/compute-plans), [disks](https://render.com/docs/disks), [secrets](https://render.com/docs/configure-environment-variables)
- [Railway plans](https://docs.railway.com/pricing/plans), [volumes](https://docs.railway.com/volumes/reference), [variables](https://docs.railway.com/variables)
- [GCP Free Tier](https://cloud.google.com/free/docs/free-cloud-features), [Secret Manager](https://cloud.google.com/secret-manager/docs/best-practices), [Memorystore pricing](https://cloud.google.com/memorystore/docs/redis/pricing)