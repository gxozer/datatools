"""Unit tests for env-configurable lockout thresholds (PR-50)."""

import importlib
import pytest
import app.auth_controllers as ac


@pytest.fixture(autouse=True)
def restore_module(monkeypatch):
    """Reload the module to defaults after each test so other tests aren't affected."""
    yield
    monkeypatch.delenv("MAX_LOGIN_ATTEMPTS", raising=False)
    monkeypatch.delenv("LOCKOUT_WINDOW_MINUTES", raising=False)
    importlib.reload(ac)


def test_default_lockout_window(monkeypatch):
    monkeypatch.delenv("LOCKOUT_WINDOW_MINUTES", raising=False)
    importlib.reload(ac)
    assert ac._LOCKOUT_WINDOW_MINUTES == 15


def test_default_lockout_threshold(monkeypatch):
    monkeypatch.delenv("MAX_LOGIN_ATTEMPTS", raising=False)
    importlib.reload(ac)
    assert ac._LOCKOUT_THRESHOLD == 5


def test_custom_lockout_window(monkeypatch):
    monkeypatch.setenv("LOCKOUT_WINDOW_MINUTES", "30")
    importlib.reload(ac)
    assert ac._LOCKOUT_WINDOW_MINUTES == 30


def test_custom_lockout_threshold(monkeypatch):
    monkeypatch.setenv("MAX_LOGIN_ATTEMPTS", "10")
    importlib.reload(ac)
    assert ac._LOCKOUT_THRESHOLD == 10
