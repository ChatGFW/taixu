"""Authenticated bounded HTTP over loopback; the shared key arrives only on stdin."""
import hashlib
import hmac
import json
import re
import threading
import time

HEADER = 'X-Taixu-Service-Authorization'
RESPONSE_HEADER = 'X-Taixu-Service-Response'


class ServiceProtocol:
    def __init__(self, key, clock=time.time):
        if not isinstance(key, bytes) or len(key) != 32:
            raise ValueError('Invalid service key')
        self._key = key
        self._clock = clock
        self._seen = {}
        self._lock = threading.Lock()

    @classmethod
    def from_stdin(cls, stream):
        line = stream.readline(256)
        if not line.endswith(b'\n'):
            raise ValueError('Missing native bootstrap')
        value = json.loads(line)
        if set(value) != {'protocol', 'key'} or value['protocol'] != 'taixu-service-v1' or not re.fullmatch('[a-f0-9]{64}', value['key']):
            raise ValueError('Invalid native bootstrap')
        return cls(bytes.fromhex(value['key']))

    def _sign(self, text):
        return hmac.new(self._key, text.encode('utf-8'), hashlib.sha256).hexdigest()

    def verify(self, method, path, body, header):
        if not isinstance(header, str) or not re.fullmatch(r'[0-9]{10,12}:[a-f0-9]{32}:[a-f0-9]{64}', header):
            return None
        timestamp, nonce, supplied = header.split(':')
        now = self._clock()
        if abs(now - int(timestamp)) > 30:
            return None
        expected = self._sign(f'request\n{timestamp}\n{nonce}\n{method}\n{path}\n{hashlib.sha256(body).hexdigest()}')
        if not hmac.compare_digest(expected, supplied):
            return None
        with self._lock:
            self._seen = {key: expiry for key, expiry in self._seen.items() if expiry > now}
            if nonce in self._seen or len(self._seen) >= 4096:
                return None
            self._seen[nonce] = int(timestamp) + 31
        return nonce

    def response(self, nonce, status, body):
        return self._sign(f'response\n{nonce}\n{status}\n{hashlib.sha256(body).hexdigest()}')
