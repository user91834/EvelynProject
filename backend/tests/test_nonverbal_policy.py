# tests/test_nonverbal_policy.py
import pytest
from nonverbal_policy import (
    NonverbalEvent,
    NonverbalPolicyResult,
    ResponseMode,
    choose_response_mode,
    choose_nonverbal_response,
    build_llm_prompt_hint,
)


def _event(
    known=True,
    voice_mode="nonverbal",
    intensity_score=0.5,
    duration_score=0.5,
    speechlikeness_score=0.0,
    distress_score=0.0,
    detected_word=None,
    word_confidence=0.0,
    repetition_count=1,
    speaker_name="Test",
):
    return NonverbalEvent(
        speaker_id="s1",
        speaker_name=speaker_name,
        known=known,
        voice_mode=voice_mode,
        intensity_score=intensity_score,
        duration_score=duration_score,
        speechlikeness_score=speechlikeness_score,
        distress_score=distress_score,
        detected_word=detected_word,
        word_confidence=word_confidence,
        repetition_count=repetition_count,
    )


def test_choose_response_mode_gentle_default():
    e = _event(intensity_score=0.2, speechlikeness_score=0.2)
    assert choose_response_mode(e) == ResponseMode.GENTLE_ACKNOWLEDGEMENT


def test_choose_response_mode_strong_acknowledgement():
    e = _event(intensity_score=0.8, duration_score=0.7, speechlikeness_score=0.3)
    assert choose_response_mode(e) == ResponseMode.STRONG_ACKNOWLEDGEMENT


def test_choose_response_mode_celebrate_word():
    e = _event(detected_word="oi", word_confidence=0.8)
    assert choose_response_mode(e) == ResponseMode.CELEBRATE_WORD


def test_choose_response_mode_attentive_care():
    e = _event(distress_score=0.75)
    assert choose_response_mode(e) == ResponseMode.ATTENTIVE_CARE


def test_choose_response_mode_encourage_speech_high():
    e = _event(speechlikeness_score=0.75, intensity_score=0.6)
    assert choose_response_mode(e) == ResponseMode.ENCOURAGE_SPEECH_HIGH


def test_choose_response_mode_encourage_speech_low():
    e = _event(speechlikeness_score=0.72, intensity_score=0.4)
    assert choose_response_mode(e) == ResponseMode.ENCOURAGE_SPEECH_LOW


def test_build_llm_prompt_hint_contains_mode_and_context():
    e = _event(speaker_name="Maria")
    hint = build_llm_prompt_hint(e, ResponseMode.GENTLE_ACKNOWLEDGEMENT)
    assert "Maria" in hint
    assert ResponseMode.GENTLE_ACKNOWLEDGEMENT.value in hint
    assert "não verbal" in hint or "não verbal" in hint.lower() or "verbal" in hint
    assert "intensity_score" in hint
    assert "português" in hint or "Portugues" in hint


def test_build_llm_prompt_hint_with_word():
    e = _event(detected_word="mamãe", word_confidence=0.9)
    hint = build_llm_prompt_hint(e, ResponseMode.CELEBRATE_WORD)
    assert "mamãe" in hint or "Palavra detectada" in hint


def test_choose_nonverbal_response_returns_result():
    e = _event(known=True, voice_mode="nonverbal")
    result = choose_nonverbal_response(e)
    assert isinstance(result, NonverbalPolicyResult)
    assert result.should_respond is True
    assert isinstance(result.response_mode, ResponseMode)
    assert isinstance(result.llm_prompt_hint, str)
    assert len(result.llm_prompt_hint) > 0
    assert 0 <= result.emphasis_score <= 1
    assert 0 <= result.celebration_score <= 1
    assert 0 <= result.care_score <= 1
    assert "nonverbal" in result.tags


def test_choose_nonverbal_response_unknown_verbal_no_respond():
    e = _event(known=False, voice_mode="verbal")
    result = choose_nonverbal_response(e)
    assert result.should_respond is False


def test_choose_nonverbal_response_known_nonverbal_responds():
    e = _event(known=True, voice_mode="nonverbal")
    result = choose_nonverbal_response(e)
    assert result.should_respond is True
