#!/usr/bin/env python3
"""Validador de eventos del contrato M2 (Eventos v1.78, envelope del Core).

Uso:
    python validate.py                  # corre todos los ejemplos válidos + los casos inválidos
    python validate.py evento.json ...  # valida uno o más archivos propios contra el contrato

Requiere:  pip install "jsonschema[format]" referencing
"""
import copy
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker

# No permitir que date-time se valide sin el comprobador RFC 3339: FormatChecker
# puede omitir silenciosamente controles si faltan dependencias opcionales.
try:
    import rfc3339_validator  # noqa: F401
except ImportError as exc:
    raise SystemExit('Falta rfc3339-validator: instalar pip install "jsonschema[format]" referencing') from exc
from jsonschema.exceptions import best_match
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT202012

ROOT = Path(__file__).resolve().parent
SCHEMAS = ROOT / "schemas"
VALID = ROOT / "examples" / "valid"


def build_validator():
    resources = []
    entry = None
    for path in sorted(SCHEMAS.glob("*.schema.json")):
        doc = json.loads(path.read_text(encoding="utf-8"))
        resources.append((doc["$id"], Resource.from_contents(doc, default_specification=DRAFT202012)))
        if path.name == "event.schema.json":
            entry = doc
    registry = Registry().with_resources(resources)
    return Draft202012Validator(entry, registry=registry, format_checker=FormatChecker()), registry


VALIDATOR, REGISTRY = build_validator()


def data_validator(event_type: str) -> Draft202012Validator:
    """Valida sólo el objeto data (es lo que se registra como jsonSchema del tipo en el Core)."""
    schema = json.loads((SCHEMAS / f"{event_type}.data.schema.json").read_text(encoding="utf-8"))
    return Draft202012Validator(schema, registry=REGISTRY, format_checker=FormatChecker())


def schema_errors(event) -> list[str]:
    out = []
    for err in sorted(VALIDATOR.iter_errors(event), key=lambda e: list(map(str, e.absolute_path))):
        where = "/" + "/".join(map(str, err.absolute_path)) if err.absolute_path else "(raíz)"
        msg = err.message if len(err.message) < 160 else err.message[:157] + "..."
        hint = None
        if err.validator in ("anyOf", "oneOf") and isinstance(err.schema, dict):
            hint = err.schema.get("description")
            if not hint and err.context:
                # sin descripción: mostrar la causa más específica de las alternativas
                ctx = [e for e in err.context if not (e.validator == "type" and e.validator_value == "null")]
                inner = best_match(ctx or err.context)
                hint = inner.message if len(inner.message) < 140 else inner.message[:137] + "..."
            msg = "no cumple ninguna de las alternativas permitidas"
        out.append(f"{where}: {msg}" + (f"  [{hint}]" if hint else ""))
    return out


def semantic_errors(event) -> list[str]:
    """Reglas del contrato que JSON Schema no puede expresar."""
    out = []
    if isinstance(event, dict) and event.get("causationId") and event.get("causationId") == event.get("eventId"):
        out.append("causationId no puede ser igual al eventId del propio evento")
    return out


def validate_event(event) -> list[str]:
    return schema_errors(event) + semantic_errors(event)


# ---------------------------------------------------------------------------
# Casos inválidos: cada uno parte de un ejemplo válido, le aplica un cambio y
# DEBE ser rechazado. Formato de cambio: ("set", "ruta.con.puntos", valor) o ("del", "ruta").
# ---------------------------------------------------------------------------
INVALID_CASES = [
    # envelope del Core (FAQ Core #1)
    ("envelope: eventVersion distinta de 1.0", "ticketCreated", [("set", "eventVersion", "2.0")]),
    ("envelope: occurredAt sin zona horaria", "ticketCreated", [("set", "occurredAt", "2026-10-05T09:12:01")]),
    ("envelope: eventId no es UUIDv4", "ticketCreated", [("set", "eventId", "6f1c2b7e-0000-5000-8000-00000000a174")]),
    ("envelope: eventType desconocido", "ticketCreated", [("set", "eventType", "ticketDeleted")]),
    ("envelope: campo extra (el Core no los acepta)", "ticketCreated", [("set", "traceId", "abc")]),
    ("envelope: specVersion ya no existe (ahora es eventVersion)", "ticketCreated", [("set", "specVersion", "1.0")]),
    ("envelope: producer ya no existe (ahora es sourceModule)", "ticketCreated", [("set", "producer", {"moduleId": "M2", "service": "help-center-api"})]),
    ("envelope: subject ya no existe", "ticketCreated", [("set", "subject", "tickets/550e8400-e29b-41d4-a716-446655440000")]),
    ("envelope: falta sourceModule", "ticketCreated", [("del", "sourceModule")]),
    ("envelope: sourceModule con código M3 en vez del identificador del Core", "updateTicketStatus_STARTED", [("set", "sourceModule", "M3")]),
    ("envelope: sourceModule de más de 60 caracteres", "updateTicketStatus_STARTED", [("set", "sourceModule", "a" * 61)]),
    ("envelope: correlationId no UUID", "ticketCreated", [("set", "correlationId", "abc")]),
    ("envelope: falta data", "ticketCreated", [("del", "data")]),
    ("semántica: causationId igual al eventId", "updateTicketStatus_STARTED",
     [("set", "causationId", "5ac2b7ff-c8bc-4dae-a44b-1f9f1266a65b")]),
    ("envelope: fecha imposible", "ticketCreated", [("set", "occurredAt", "2026-99-99T88:77:66-03:00")]),
    ("ticketUpdated: ROUTED sin categoría", "ticketUpdated_ROUTED", [("del", "data.details.routing.category")]),
    ("ticketUpdated: ROUTED sin subcategoría", "ticketUpdated_ROUTED", [("del", "data.details.routing.subcategory")]),
    ("ticketUpdated: propósito desconocido", "ticketUpdated_INFORMATION_REQUIRED_reclasificacion", [("set", "data.details.informationRequest.purpose", "OTHER")]),
    # ticketCreated
    ("ticketCreated: ticket anónimo", "ticketCreated", [("set", "data.isAnonymous", True)]),
    ("ticketCreated: status distinto de REGISTERED", "ticketCreated", [("set", "data.status", "IN_REVIEW")]),
    ("ticketCreated: falta responsibleAreaId", "ticketCreated", [("del", "data.responsibleAreaId")]),
    ("ticketCreated: responsibleAreaId con nombre libre", "ticketCreated", [("set", "data.responsibleAreaId", "Obras Públicas")]),
    ("ticketCreated: publicId con formato inválido", "ticketCreated", [("set", "data.publicId", "123")]),
    ("ticketCreated: trackingAccessCode no se publica", "ticketCreated", [("set", "data.trackingAccessCode", "ABCD-1234")]),
    ("ticketCreated: coordenadas en location", "ticketCreated", [("set", "data.location.latitude", -34.8)]),
    ("ticketCreated: lo publica otro módulo", "ticketCreated", [("set", "sourceModule", "obras")]),
    # ticketUpdated
    ("ticketUpdated: ROUTED sin details.routing", "ticketUpdated_ROUTED", [("del", "data.details")]),
    ("ticketUpdated: ROUTED con currentStatus IN_PROGRESS", "ticketUpdated_ROUTED", [("set", "data.currentStatus", "IN_PROGRESS")]),
    ("ticketUpdated: ROUTED con coordenadas", "ticketUpdated_ROUTED", [("set", "data.details.routing.location.latitude", -34.8)]),
    ("ticketUpdated: anónimo con citizenId", "ticketUpdated_ROUTED_anonimo", [("set", "data.citizenId", "8f14e45f-ea5e-4e5d-bd3f-1c71f4f6a001")]),
    ("ticketUpdated: identificado sin citizenId", "ticketUpdated_ROUTED", [("set", "data.citizenId", None)]),
    ("ticketUpdated: internalMessage nunca se publica", "ticketUpdated_RESOLVED", [("set", "data.internalMessage", "nota privada")]),
    ("ticketUpdated: informationRequestId no existe en el contrato", "ticketUpdated_INFORMATION_REQUIRED",
     [("set", "data.details.informationRequest.informationRequestId", 31)]),
    ("ticketUpdated: INFORMATION_REQUIRED sin messageForCitizen", "ticketUpdated_INFORMATION_REQUIRED",
     [("del", "data.details.informationRequest.messageForCitizen")]),
    ("ticketUpdated: INFORMATION_PROVIDED sin mensaje ni adjuntos", "ticketUpdated_INFORMATION_PROVIDED_solo_adjuntos",
     [("del", "data.attachments")]),
    ("ticketUpdated: escalation activa sin reasonCode", "ticketUpdated_ESCALATION_CHANGED",
     [("set", "data.details.escalation.reasonCode", None)]),
    ("ticketUpdated: RESOLVED sin publicMessage", "ticketUpdated_RESOLVED", [("del", "data.publicMessage")]),
    ("ticketUpdated: RESOLVED con tipo inexistente", "ticketUpdated_RESOLVED", [("set", "data.details.resolution.type", "DONE")]),
    ("ticketUpdated: CANCELLED con motivo inexistente", "ticketUpdated_CANCELLED", [("set", "data.details.cancellation.reasonCode", "BECAUSE")]),
    ("ticketUpdated: updateType inexistente", "ticketUpdated_STATUS_CHANGED", [("set", "data.updateType", "REJECTED")]),
    ("ticketUpdated: DUPLICATE_LINKED con mainTicketId no UUID", "ticketUpdated_DUPLICATE_LINKED",
     [("set", "data.details.duplicate.mainTicketId", "TK-2026-000050")]),
    ("ticketUpdated: lo publica otro módulo", "ticketUpdated_STATUS_CHANGED", [("set", "sourceModule", "obras")]),
    ("ticketUpdated: progress fuera de rango", "ticketUpdated_PROGRESS", [("set", "data.progress", 101)]),
    ("ticketUpdated: CONTENT_UPDATED vacío", "ticketUpdated_CONTENT_UPDATED", [("set", "data.details.content", {})]),
    # updateTicketStatus
    ("updateTicketStatus: newStatus no existe en el contrato", "updateTicketStatus_STARTED", [("set", "data.newStatus", "IN_PROGRESS")]),
    ("updateTicketStatus: publicId no se envía", "updateTicketStatus_STARTED", [("set", "data.publicId", "TK-2026-000123")]),
    ("updateTicketStatus: falta updateOccurredAt", "updateTicketStatus_STARTED", [("del", "data.updateOccurredAt")]),
    ("updateTicketStatus: falta updatedBy", "updateTicketStatus_STARTED", [("del", "data.updatedBy")]),
    ("updateTicketStatus: updatedBy.type CITIZEN desde otro módulo", "updateTicketStatus_STARTED", [("set", "data.updatedBy.type", "CITIZEN")]),
    ("updateTicketStatus: updateType de ticketUpdated", "updateTicketStatus_STARTED", [("set", "data.updateType", "PRIORITY_CHANGED")]),
    ("updateTicketStatus: STARTED con details", "updateTicketStatus_STARTED", [("set", "data.details", {"x": 1})]),
    ("updateTicketStatus: PROGRESS vacío", "updateTicketStatus_PROGRESS_programacion",
     [("del", "data.publicMessage"), ("del", "data.internalMessage"), ("del", "data.progress")]),
    ("updateTicketStatus: progress mayor a 100", "updateTicketStatus_PROGRESS_avance", [("set", "data.progress", 150)]),
    ("updateTicketStatus: INFORMATION_REQUIRED sin messageForCitizen", "updateTicketStatus_INFORMATION_REQUIRED",
     [("del", "data.details.informationRequest.messageForCitizen")]),
    ("updateTicketStatus: RETURNED sin ningún mensaje", "updateTicketStatus_RETURNED",
     [("del", "data.publicMessage"), ("del", "data.internalMessage")]),
    ("updateTicketStatus: RETURNED sin reasonCode", "updateTicketStatus_RETURNED", [("del", "data.details.returnInfo.reasonCode")]),
    ("updateTicketStatus: RETURNED con motivo fuera del enum", "updateTicketStatus_RETURNED", [("set", "data.details.returnInfo.reasonCode", "BECAUSE_I_SAY_SO")]),
    ("updateTicketStatus: RETURNED con motivo en minúsculas", "updateTicketStatus_RETURNED", [("set", "data.details.returnInfo.reasonCode", "other")]),
    ("updateTicketStatus: RESOLVED sin publicMessage", "updateTicketStatus_RESOLVED", [("del", "data.publicMessage")]),
    ("updateTicketStatus: RESOLVED sin resolution.type", "updateTicketStatus_RESOLVED", [("del", "data.details.resolution.type")]),
    ("updateTicketStatus: REJECTED sin ningún mensaje", "updateTicketStatus_REJECTED",
     [("del", "data.publicMessage"), ("del", "data.internalMessage")]),
    ("updateTicketStatus: REJECTED con motivo sólo interno de M2", "updateTicketStatus_REJECTED",
     [("set", "data.details.cancellation.reasonCode", "WITHDRAWN_BY_CITIZEN")]),
    ("updateTicketStatus: adjunto con binario embebido", "updateTicketStatus_RESOLVED",
     [("set", "data.attachments.0.content", "aGVsbG8=")]),
    ("updateTicketStatus: adjunto sin url", "updateTicketStatus_RESOLVED", [("del", "data.attachments.0.url")]),
    ("updateTicketStatus: sourceModule con nombre libre y mayúsculas", "updateTicketStatus_STARTED", [("set", "sourceModule", "Obras Públicas")]),
    ("updateTicketStatus: lo publica M2", "updateTicketStatus_STARTED", [("set", "sourceModule", "atencion-ciudadana")]),
    ("updateTicketStatus: actor.id que no es UUID", "updateTicketStatus_STARTED", [("set", "data.updatedBy.id", "USR-M3-77")]),
    ("updateTicketStatus: actor.id numérico", "updateTicketStatus_STARTED", [("set", "data.updatedBy.id", 77)]),
]


def _walk(obj, parts):
    for p in parts:
        obj = obj[int(p)] if isinstance(obj, list) else obj[p]
    return obj


def apply_ops(event, ops):
    event = copy.deepcopy(event)
    for op, path, *value in ops:
        parts = path.split(".")
        parent = _walk(event, parts[:-1])
        key = parts[-1]
        key = int(key) if isinstance(parent, list) else key
        if op == "set":
            parent[key] = value[0]
        elif op == "del":
            del parent[key]
    return event


def load_example(name):
    return json.loads((VALID / f"{name}.json").read_text(encoding="utf-8"))


def run_all() -> int:
    failures = 0
    files = sorted(VALID.glob("*.json")) + sorted((ROOT / "examples" / "guia").glob("*.json"))
    print(f"== Ejemplos válidos ({len(files)}) ==")
    for f in files:
        errs = validate_event(json.loads(f.read_text(encoding="utf-8")))
        if errs:
            failures += 1
            print(f"FALLA  {f.name}")
            for e in errs[:5]:
                print(f"       {e}")
        else:
            print(f"ok     {f.name}")

    data_bad = 0
    for f in files:
        ev = json.loads(f.read_text(encoding="utf-8"))
        if list(data_validator(ev["eventType"]).iter_errors(ev["data"])):
            data_bad += 1
            print(f"FALLA  data-only: {f.name}")
    failures += data_bad
    print(f"\nSchemas de data por separado: {len(files) - data_bad}/{len(files)} ejemplos validan.")

    print(f"\n== Casos inválidos que deben ser rechazados ({len(INVALID_CASES)}) ==")
    for title, base, ops in INVALID_CASES:
        mutated = apply_ops(load_example(base), ops)
        errs = validate_event(mutated)
        if errs:
            print(f"ok     {title}")
            print(f"         -> {errs[0]}")
        else:
            failures += 1
            print(f"FALLA  {title}  (se aceptó y debía rechazarse)")

    print()
    if failures:
        print(f"{failures} problema(s).")
        return 1
    print(f"Todo en orden: {len(files)} válidos aceptados, {len(INVALID_CASES)} inválidos rechazados.")
    return 0


def run_files(paths) -> int:
    bad = 0
    for p in paths:
        try:
            event = json.loads(Path(p).read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            print(f"FALLA  {p}: no se pudo leer como JSON ({exc})")
            bad += 1
            continue
        errs = validate_event(event)
        if errs:
            bad += 1
            print(f"FALLA  {p}")
            for e in errs:
                print(f"       {e}")
        else:
            print(f"ok     {p}")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(run_files(sys.argv[1:]) if len(sys.argv) > 1 else run_all())
