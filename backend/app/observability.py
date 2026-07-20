"""Observability baseline (docs/BACKEND.md §7, build-order step 7).

- Structured JSON logs (structlog) shared by the API and the worker; stdlib
  records from libraries (uvicorn, celery, sarvamai) are routed through the
  same formatter so all output is uniform.
- `recording_id` is bound to contextvars per Celery task (worker/main.py),
  so every log line of every pipeline stage answers "what happened to
  recording X?" without threading the id through call signatures.
- Metrics are log-based for now: stage events carry `stage`, `duration_ms`,
  and outcome. Prometheus/Grafana and OpenTelemetry are the next build-out
  (§7 table), not part of this baseline.
"""

import logging

import structlog

from app.settings import get_settings

# Applied to structlog-native and foreign (stdlib) records alike.
_SHARED_PROCESSORS: list = [
    structlog.contextvars.merge_contextvars,
    structlog.stdlib.add_logger_name,
    structlog.stdlib.add_log_level,
    structlog.processors.TimeStamper(fmt="iso", utc=True),
]


def configure_logging(log_format: str | None = None) -> None:
    """Route all logging (structlog + stdlib) through one structured formatter."""
    log_format = log_format or get_settings().log_format
    renderer = (
        structlog.dev.ConsoleRenderer()
        if log_format == "console"
        else structlog.processors.JSONRenderer()
    )

    structlog.configure(
        processors=[
            *_SHARED_PROCESSORS,
            structlog.stdlib.ProcessorFormatter.wrap_for_formatter,
        ],
        logger_factory=structlog.stdlib.LoggerFactory(),
        wrapper_class=structlog.stdlib.BoundLogger,
        cache_logger_on_first_use=True,
    )

    formatter = structlog.stdlib.ProcessorFormatter(
        processors=[
            structlog.stdlib.ProcessorFormatter.remove_processors_meta,
            structlog.processors.format_exc_info,
            renderer,
        ],
        foreign_pre_chain=_SHARED_PROCESSORS,
    )
    handler = logging.StreamHandler()
    handler.setFormatter(formatter)
    root = logging.getLogger()
    root.handlers.clear()
    root.addHandler(handler)
    root.setLevel(logging.INFO)
