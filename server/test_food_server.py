import base64
import io
import json
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from food_server import ApiError, GeminiRecognizer, handler_for, validate_input, validate_result


def result(name="비빔밥", grams=400):
    return {"isFood": True, "name": name, "foods": [{"name": name, "grams": grams}]}


class RecognitionTests(unittest.TestCase):
    def test_image_bytes_are_forwarded(self):
        encoded = base64.b64encode(b"\xff\xd8\xffexample").decode()
        parts = validate_input({"image": {"mimeType": "image/jpeg", "data": encoded}})
        self.assertEqual(parts[1]["inlineData"]["data"], encoded)

    def test_bad_inputs_never_reach_provider(self):
        for data in ({}, [], {"text": 123}, {"image": {}},
                     {"image": {"mimeType": "image/jpeg", "data": "not base64"}},
                     {"image": {"mimeType": "image/png", "data": "YWJj"}}):
            with self.subTest(data=data), self.assertRaises(ApiError):
                validate_input(data)

    def test_non_food_is_a_recognition_failure(self):
        with self.assertRaises(ApiError) as caught:
            validate_result({"isFood": False, "name": "", "foods": []})
        self.assertEqual(caught.exception.status, 422)

    def test_invalid_provider_quantities_are_rejected(self):
        for grams in (0, -10, 5001, True, "100", float("nan"), float("inf")):
            with self.subTest(grams=grams), self.assertRaises(ApiError):
                validate_result(result(grams=grams))

    def test_empty_and_oversized_results_are_rejected(self):
        for data in ([], {"isFood": "true"}, result(name=""),
                     {"isFood": True, "name": "밥", "foods": []},
                     {"isFood": True, "name": "밥", "foods": result()["foods"] * 21}):
            with self.subTest(data=data), self.assertRaises(ApiError):
                validate_result(data)

    def test_real_provider_request_and_mapping(self):
        def opener(request, timeout):
            self.assertEqual(timeout, 40)
            self.assertTrue(request.full_url.endswith("/test-model:generateContent"))
            self.assertNotIn("secret", request.full_url)
            body = json.loads(request.data)
            self.assertEqual(body["generationConfig"]["responseMimeType"], "application/json")
            self.assertIn("responseSchema", body["generationConfig"])
            return io.BytesIO(json.dumps({"candidates": [{"finishReason": "STOP", "content": {
                "parts": [{"text": "private thought", "thought": True}, {"text": json.dumps(result())}]}}]}).encode())
        output = GeminiRecognizer("secret", "test-model", opener)(validate_input({"text": "비빔밥"}))
        self.assertEqual(output["foods"][0]["grams"], 400)
        self.assertTrue(output["portionEstimated"])

    def test_provider_failure_mapping(self):
        for raw, status in (({}, 422), ({"candidates": [{"finishReason": "MAX_TOKENS"}]}, 422),
                            ({"candidates": [{"finishReason": "STOP", "content": {"parts": [{"text": "invalid"}]}}]}, 502)):
            with self.subTest(raw=raw), self.assertRaises(ApiError) as caught:
                GeminiRecognizer("secret", "test", lambda *a, **kw: io.BytesIO(json.dumps(raw).encode()))([])
            self.assertEqual(caught.exception.status, status)

    def test_rate_limit_does_not_expose_upstream_body(self):
        def opener(*a, **kw):
            raise urllib.error.HTTPError("https://example", 429, "secret", {}, None)
        with self.assertRaises(ApiError) as caught:
            GeminiRecognizer("secret", "test", opener)([])
        self.assertEqual(caught.exception.status, 503)
        self.assertNotIn("secret", caught.exception.message)

    def test_no_credential_fails_at_startup(self):
        with self.assertRaises(ValueError):
            GeminiRecognizer("", "")

    def test_provider_busy_and_configuration_errors(self):
        for status, code in ((503, "MODEL_BUSY"), (401, "API_AUTH"), (403, "API_AUTH"), (404, "MODEL_NOT_FOUND")):
            def opener(*args, **kwargs):
                raise urllib.error.HTTPError("https://example", status, "secret", {}, None)
            with self.subTest(status=status), self.assertRaises(ApiError) as caught:
                GeminiRecognizer("secret", "test", opener)([])
            self.assertEqual(caught.exception.code, code)
            self.assertNotIn("secret", caught.exception.message)


class HttpContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.received = []
        def recognize(parts):
            cls.received.append(parts)
            if "nonfood" in parts[0]["text"]:
                raise ApiError(422, "RECOGNITION", "음식이 아니에요.")
            return validate_result(result())
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), handler_for(recognize))
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.origin = "http://127.0.0.1:" + str(cls.server.server_port)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def request(self, path, body=None):
        req = urllib.request.Request(self.origin + path, data=body,
                                     headers={"Content-Type": "application/json"})
        try:
            response = urllib.request.urlopen(req, timeout=3)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.status, json.load(response)

    def test_health_and_unknown_route(self):
        self.assertEqual(self.request("/health")[0], 200)
        self.assertEqual(self.request("/missing", b"{}")[0], 404)

    def test_recognition_round_trip(self):
        status, data = self.request("/food/recognize", json.dumps({"text": "비빔밥"}).encode())
        self.assertEqual(status, 200)
        self.assertEqual(data["foods"], [{"name": "비빔밥", "grams": 400}])
        self.assertTrue(self.received)

    def test_json_validation_and_nonfood_status(self):
        self.assertEqual(self.request("/food/recognize", b"bad json")[0], 400)
        self.assertEqual(self.request("/food/recognize", b"{}")[0], 400)
        self.assertEqual(self.request("/food/recognize", b'{"text":"nonfood"}')[0], 422)


if __name__ == "__main__":
    unittest.main()
