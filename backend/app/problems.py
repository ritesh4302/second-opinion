from fastapi import HTTPException, Request
from fastapi.responses import JSONResponse


class Problem(HTTPException):
    """RFC 9457 Problem Details error (docs/BACKEND.md §3 conventions)."""

    def __init__(self, status: int, title: str, detail: str) -> None:
        super().__init__(status_code=status, detail=detail)
        self.title = title


async def problem_handler(_request: Request, exc: HTTPException) -> JSONResponse:
    title = getattr(exc, "title", "Error")
    return JSONResponse(
        status_code=exc.status_code,
        media_type="application/problem+json",
        content={
            "type": "about:blank",
            "title": title,
            "status": exc.status_code,
            "detail": exc.detail,
        },
    )
