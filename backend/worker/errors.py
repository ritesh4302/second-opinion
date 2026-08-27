"""Worker errors whose retry behavior is explicit and safe to persist."""


class PermanentPipelineError(RuntimeError):
    """The recording or prior-stage data cannot succeed without intervention."""


class ProviderConfigurationError(PermanentPipelineError):
    """A selected provider is unavailable because configuration is invalid."""


class SanitizedRetryError(RuntimeError):
    """Celery retry marker containing only an exception class name."""
