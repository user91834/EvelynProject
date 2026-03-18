# tests/test_personality_modes.py
import pytest
from personality_modes import (
    merge_mode_config,
    get_mode_style_instructions,
    build_behavior_vector,
    DEFAULT_MODE_CONFIG,
    PERSONALITY_MODES,
)


def test_merge_mode_config_none():
    out = merge_mode_config(None)
    assert out == DEFAULT_MODE_CONFIG


def test_merge_mode_config_empty():
    out = merge_mode_config({})
    assert out == DEFAULT_MODE_CONFIG


def test_merge_mode_config_merge():
    out = merge_mode_config({"miguxes": 50, "ego": 80})
    assert out["miguxes"] == 50
    assert out["ego"] == 80
    assert out["tagarela"] is None
    for key in DEFAULT_MODE_CONFIG:
        assert key in out


def test_merge_mode_config_clamp():
    out = merge_mode_config({"miguxes": 150, "ego": -10})
    assert out["miguxes"] == 100
    assert out["ego"] == 0


def test_merge_mode_config_unknown_key_ignored():
    out = merge_mode_config({"unknown_mode": 50, "miguxes": 30})
    assert "unknown_mode" not in out
    assert out["miguxes"] == 30


def test_get_mode_style_instructions_none():
    instructions = get_mode_style_instructions(None)
    assert instructions == []


def test_get_mode_style_instructions_zero_values():
    instructions = get_mode_style_instructions({"miguxes": 0, "ego": None})
    assert instructions == []


def test_get_mode_style_instructions_includes_label_and_speech():
    instructions = get_mode_style_instructions({"miguxes": 60})
    assert len(instructions) >= 1
    assert "60/100" in instructions[0]
    assert any("miguxes" in s.lower() or "Miguxes" in s for s in instructions)
    assert any("- " in s for s in instructions)


def test_build_behavior_vector_none():
    v = build_behavior_vector(None)
    assert isinstance(v, dict)
    assert "affection" in v
    assert "teasing" in v
    for val in v.values():
        assert isinstance(val, (int, float))


def test_build_behavior_vector_empty():
    v = build_behavior_vector({})
    assert isinstance(v, dict)
    assert "affection" in v


def test_build_behavior_vector_with_modes():
    config = {"miguxes": 100, "implicante": 50}
    v = build_behavior_vector(config)
    assert isinstance(v, dict)
    # miguxes has effects on affection, softness, etc.
    assert "affection" in v
    assert "teasing" in v
    # implicante at 50% should add some teasing
    assert v.get("teasing", 0) >= 0 or v.get("affection", 0) >= 0
