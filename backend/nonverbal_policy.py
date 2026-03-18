from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional


# =========================================================
# ENUMS
# =========================================================

class ResponseMode(str, Enum):
    GENTLE_ACKNOWLEDGEMENT = "gentle_acknowledgement"
    STRONG_ACKNOWLEDGEMENT = "strong_acknowledgement"
    ENCOURAGE_SPEECH_LOW = "encourage_speech_low"
    ENCOURAGE_SPEECH_HIGH = "encourage_speech_high"
    CELEBRATE_WORD = "celebrate_word"
    ATTENTIVE_CARE = "attentive_care"


# =========================================================
# INPUT EVENT
# =========================================================

@dataclass
class NonverbalEvent:
    speaker_id: Optional[str] = None
    speaker_name: Optional[str] = None
    known: bool = False
    voice_mode: str = "nonverbal"  # "nonverbal" | "verbal" | etc

    intensity_score: float = 0.0         # 0.0 - 1.0
    duration_score: float = 0.0          # 0.0 - 1.0
    speechlikeness_score: float = 0.0    # 0.0 - 1.0
    distress_score: float = 0.0          # 0.0 - 1.0

    detected_word: Optional[str] = None
    word_confidence: float = 0.0         # 0.0 - 1.0

    repetition_count: int = 1
    pitch_band: Optional[str] = None     # e.g. "low", "mid", "high", "child_high"
    acoustic_label: Optional[str] = None # e.g. "grunt", "hum", "call"

    metadata: Dict[str, Any] = field(default_factory=dict)


# =========================================================
# POLICY OUTPUT
# =========================================================

@dataclass
class NonverbalPolicyResult:
    should_respond: bool
    response_mode: ResponseMode
    emphasis_score: float
    celebration_score: float
    care_score: float
    llm_prompt_hint: str
    response_examples: List[str] = field(default_factory=list)
    tags: List[str] = field(default_factory=list)
    debug: Dict[str, Any] = field(default_factory=dict)


# =========================================================
# HELPERS
# =========================================================

def _clamp01(value: float) -> float:
    try:
        return max(0.0, min(1.0, float(value)))
    except Exception:
        return 0.0


def _safe_word(word: Optional[str]) -> Optional[str]:
    if not word:
        return None
    cleaned = word.strip()
    return cleaned if cleaned else None


# =========================================================
# CORE SCORING
# =========================================================

def compute_emphasis_score(event: NonverbalEvent) -> float:
    """
    Mede o quão enfática deve ser a resposta.
    Base principal:
    - intensidade
    - duração
    - repetição
    - semelhança com fala
    """
    intensity = _clamp01(event.intensity_score)
    duration = _clamp01(event.duration_score)
    speechlike = _clamp01(event.speechlikeness_score)

    repetition_bonus = min(max(event.repetition_count - 1, 0) * 0.10, 0.25)

    score = (
        intensity * 0.38 +
        duration * 0.27 +
        speechlike * 0.20 +
        repetition_bonus
    )
    return _clamp01(score)


def compute_celebration_score(event: NonverbalEvent) -> float:
    """
    Mede se deve haver celebração/incentivo vocal.
    Palavras detectadas e vocalizações mais parecidas com fala puxam esse score para cima.
    """
    speechlike = _clamp01(event.speechlikeness_score)
    word_conf = _clamp01(event.word_confidence)
    has_word = 1.0 if _safe_word(event.detected_word) else 0.0

    score = (
        has_word * 0.45 +
        word_conf * 0.35 +
        speechlike * 0.20
    )
    return _clamp01(score)


def compute_care_score(event: NonverbalEvent) -> float:
    """
    Mede se a resposta deve ser mais acolhedora/cuidadosa do que comemorativa.
    Útil para sons fortes, prolongados ou com cara de desconforto.
    """
    distress = _clamp01(event.distress_score)
    intensity = _clamp01(event.intensity_score)
    duration = _clamp01(event.duration_score)

    # Intensidade alta + duração longa sem muita semelhança com fala
    chaotic_bonus = max(0.0, intensity - event.speechlikeness_score) * 0.20

    score = (
        distress * 0.55 +
        intensity * 0.15 +
        duration * 0.10 +
        chaotic_bonus
    )
    return _clamp01(score)


# =========================================================
# MODE SELECTION
# =========================================================

def choose_response_mode(event: NonverbalEvent) -> ResponseMode:
    word = _safe_word(event.detected_word)
    emphasis = compute_emphasis_score(event)
    celebration = compute_celebration_score(event)
    care = compute_care_score(event)

    if word and event.word_confidence >= 0.65:
        return ResponseMode.CELEBRATE_WORD

    if care >= 0.70:
        return ResponseMode.ATTENTIVE_CARE

    if event.speechlikeness_score >= 0.72:
        if emphasis >= 0.55:
            return ResponseMode.ENCOURAGE_SPEECH_HIGH
        return ResponseMode.ENCOURAGE_SPEECH_LOW

    if emphasis >= 0.65:
        return ResponseMode.STRONG_ACKNOWLEDGEMENT

    return ResponseMode.GENTLE_ACKNOWLEDGEMENT


# =========================================================
# EXAMPLE LIBRARY
# =========================================================

EXAMPLE_LIBRARY: Dict[ResponseMode, List[str]] = {
    ResponseMode.GENTLE_ACKNOWLEDGEMENT: [
        "Oi, eu te ouvi.",
        "Tô aqui com você.",
        "Eu percebi seu som."
    ],
    ResponseMode.STRONG_ACKNOWLEDGEMENT: [
        "Oi! Eu ouvi você bem!",
        "Você me chamou, eu percebi!",
        "Tô prestando atenção em você!"
    ],
    ResponseMode.ENCOURAGE_SPEECH_LOW: [
        "Que bom ouvir sua voz.",
        "Isso, continua falando comigo.",
        "Gostei de te ouvir."
    ],
    ResponseMode.ENCOURAGE_SPEECH_HIGH: [
        "Muito bem! Eu ouvi sua voz!",
        "Que legal, continua falando comigo!",
        "Isso! Você está se expressando muito bem!"
    ],
    ResponseMode.CELEBRATE_WORD: [
        "Você falou {word}! Muito bem!",
        "Que legal! Eu ouvi {word}!",
        "Isso! {word}! Muito bem!"
    ],
    ResponseMode.ATTENTIVE_CARE: [
        "Eu te ouvi, tô prestando atenção.",
        "Tô aqui com você, pode ficar tranquilo.",
        "Eu percebi você e estou atento."
    ],
}


# =========================================================
# LLM HINT BUILDER
# =========================================================

def build_llm_prompt_hint(
    event: NonverbalEvent,
    mode: ResponseMode,
) -> str:
    """
    Retorna um prompt curto e estruturado para orientar a LLM.
    Os exemplos abaixo são apenas referências de estilo e intenção.
    A LLM NÃO deve repetir sempre as mesmas frases literalmente.
    """
    word = _safe_word(event.detected_word)
    name = event.speaker_name or "a criança"

    base_context = [
        "Você está respondendo a uma vocalização de uma pessoa conhecida em modo não verbal.",
        f"Nome da pessoa: {name}.",
        f"Modo de resposta escolhido: {mode.value}.",
        "Gere uma resposta curta, calorosa e natural em português do Brasil.",
        "Varie a formulação para não repetir sempre as mesmas frases.",
        "Os exemplos fornecidos são apenas exemplos de estilo, não roteiros fixos.",
        "A resposta deve soar acolhedora, simples e afetiva.",
    ]

    mode_instructions: Dict[ResponseMode, List[str]] = {
        ResponseMode.GENTLE_ACKNOWLEDGEMENT: [
            "Reconheça a vocalização de forma leve e acolhedora.",
            "Não exagere no entusiasmo.",
            "Priorize presença, atenção e segurança emocional."
        ],
        ResponseMode.STRONG_ACKNOWLEDGEMENT: [
            "Responda de forma mais enfática e perceptível.",
            "Mostre claramente que a vocalização foi percebida.",
            "Use mais energia emocional, mas sem soar artificial."
        ],
        ResponseMode.ENCOURAGE_SPEECH_LOW: [
            "A vocalização parece um pouco próxima de fala.",
            "Incentive suavemente novas tentativas de expressão vocal.",
            "Mantenha um tom positivo e encorajador."
        ],
        ResponseMode.ENCOURAGE_SPEECH_HIGH: [
            "A vocalização parece bastante próxima de fala.",
            "Responda com entusiasmo claro e reforço positivo.",
            "Incentive a continuidade da expressão vocal."
        ],
        ResponseMode.CELEBRATE_WORD: [
            "Uma palavra provavelmente foi detectada.",
            "Comemore de forma clara e positiva.",
            "Repita a palavra de forma natural 1 ou 2 vezes no máximo.",
            "Não exagere na repetição para não soar mecânico."
        ],
        ResponseMode.ATTENTIVE_CARE: [
            "A resposta deve priorizar acolhimento e atenção.",
            "Não trate como celebração.",
            "Use linguagem de segurança, presença e cuidado."
        ],
    }

    acoustic_summary = [
        f"intensity_score={_clamp01(event.intensity_score):.2f}",
        f"duration_score={_clamp01(event.duration_score):.2f}",
        f"speechlikeness_score={_clamp01(event.speechlikeness_score):.2f}",
        f"distress_score={_clamp01(event.distress_score):.2f}",
        f"repetition_count={event.repetition_count}",
        f"acoustic_label={event.acoustic_label or 'unknown'}",
        f"pitch_band={event.pitch_band or 'unknown'}",
    ]

    word_block: List[str] = []
    if word:
        word_block.append(f"Palavra detectada: {word}.")
        word_block.append(f"Confiança da palavra: {_clamp01(event.word_confidence):.2f}.")

    examples = render_examples_for_mode(mode, word=word)

    hint_lines = (
        base_context
        + mode_instructions[mode]
        + ["Resumo acústico: " + ", ".join(acoustic_summary)]
        + word_block
        + ["Exemplos de estilo:"] 
        + [f"- {ex}" for ex in examples]
    )

    return "\n".join(hint_lines)


# =========================================================
# EXAMPLE RENDERING
# =========================================================

def render_examples_for_mode(mode: ResponseMode, word: Optional[str] = None) -> List[str]:
    examples = EXAMPLE_LIBRARY.get(mode, [])
    rendered: List[str] = []

    for ex in examples:
        if "{word}" in ex:
            rendered.append(ex.format(word=word or "essa palavra"))
        else:
            rendered.append(ex)

    return rendered


# =========================================================
# MAIN POLICY
# =========================================================

def choose_nonverbal_response(event: NonverbalEvent) -> NonverbalPolicyResult:
    """
    Decide se deve responder, em que modo responder
    e retorna um hint para a LLM gerar a fala de forma variável.
    """
    emphasis = compute_emphasis_score(event)
    celebration = compute_celebration_score(event)
    care = compute_care_score(event)

    # Regra simples: para conhecidos não verbais, responder sempre.
    should_respond = bool(event.known and event.voice_mode == "nonverbal")

    mode = choose_response_mode(event)
    examples = render_examples_for_mode(mode, word=event.detected_word)
    llm_hint = build_llm_prompt_hint(event, mode)

    tags: List[str] = [
        "nonverbal",
        f"mode:{mode.value}",
        f"known:{str(event.known).lower()}",
    ]

    if _safe_word(event.detected_word):
        tags.append("word_detected")

    if event.speechlikeness_score >= 0.60:
        tags.append("speechlike")

    if event.distress_score >= 0.60:
        tags.append("care_attention")

    return NonverbalPolicyResult(
        should_respond=should_respond,
        response_mode=mode,
        emphasis_score=emphasis,
        celebration_score=celebration,
        care_score=care,
        llm_prompt_hint=llm_hint,
        response_examples=examples,
        tags=tags,
        debug={
            "speaker_id": event.speaker_id,
            "speaker_name": event.speaker_name,
            "detected_word": event.detected_word,
            "word_confidence": _clamp01(event.word_confidence),
            "intensity_score": _clamp01(event.intensity_score),
            "duration_score": _clamp01(event.duration_score),
            "speechlikeness_score": _clamp01(event.speechlikeness_score),
            "distress_score": _clamp01(event.distress_score),
            "repetition_count": event.repetition_count,
            "acoustic_label": event.acoustic_label,
            "pitch_band": event.pitch_band,
        },
    )


# =========================================================
# OPTIONAL UTILITY
# =========================================================

def policy_result_to_dict(result: NonverbalPolicyResult) -> Dict[str, Any]:
    return {
        "should_respond": result.should_respond,
        "response_mode": result.response_mode.value,
        "emphasis_score": result.emphasis_score,
        "celebration_score": result.celebration_score,
        "care_score": result.care_score,
        "llm_prompt_hint": result.llm_prompt_hint,
        "response_examples": result.response_examples,
        "tags": result.tags,
        "debug": result.debug,
    }