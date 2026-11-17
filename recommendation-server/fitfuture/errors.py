class RecommendationError(Exception):
    def __init__(self, code: str, message: str, status: int = 422, **details):
        self.code, self.message, self.status, self.details = code, message, status, details
        super().__init__(message)
