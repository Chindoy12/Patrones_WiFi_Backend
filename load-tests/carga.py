"""Prueba de carga del backend de WiFiSense (solo la librería estándar de Python 3.9+).

Simula a varias personas del equipo usando la página al mismo tiempo (panel, redes, mediciones, sensor, reportes,
alertas y modelo de IA) y, si se le dan claves de agentes de prueba, a varios agentes enviando reportes. Mide cuántas
peticiones por segundo responde el backend, cuánto tardan (p50, p95, p99) y cuántas fallan, y termina con código 1 si
no cumple los objetivos (p95 menor que 1 s y menos de 1 % de errores).

Uso:
    set WIFISENSE_USER=<usuario del equipo>      (Linux/Mac: export ...)
    set WIFISENSE_PASSWORD=<su contraseña>        o bien WIFISENSE_TOKEN=<token de acceso>
    python load-tests/carga.py --url http://localhost:8080 --usuarios 20 --segundos 60

Las credenciales se leen solo de variables de entorno: nunca se escriben en el código ni en la salida. Contra un
servidor que no es local (por ejemplo Railway) exige --remoto y limita la prueba a 20 usuarios y 120 segundos, para no
gastar los recursos del plan ni afectar a los usuarios reales. Nunca crea cuentas, redes ni claves: solo lee.
"""
from __future__ import annotations

import argparse
import json
import os
import random
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict
from dataclasses import dataclass, field

P95_GOAL_MS = 1000.0
ERROR_GOAL = 0.01
REMOTE_MAX_USERS = 20
REMOTE_MAX_SECONDS = 120
LOCAL_HOSTS = {"localhost", "127.0.0.1", "::1"}


@dataclass
class Stats:
    """Tiempos y resultados por ruta. Un 429 de un agente es el límite funcionando, no un error."""
    times: dict[str, list[float]] = field(default_factory=lambda: defaultdict(list))
    errors: dict[str, int] = field(default_factory=lambda: defaultdict(int))
    codes: dict[int, int] = field(default_factory=lambda: defaultdict(int))
    limited: dict[str, int] = field(default_factory=lambda: defaultdict(int))
    lock: threading.Lock = field(default_factory=threading.Lock)

    def add(self, name: str, ms: float, status: int) -> None:
        with self.lock:
            self.times[name].append(ms)
            if status == 429:
                self.limited[name] += 1
            elif status >= 400 or status == 0:
                self.errors[name] += 1
                self.codes[status] += 1


def percentile(values: list[float], pct: float) -> float:
    """Percentil por el método del rango más cercano (el mismo valor que una persona leería en la lista ordenada)."""
    if not values:
        return 0.0
    ordered = sorted(values)
    rank = max(1, min(len(ordered), int(-(-pct * len(ordered) // 100))))
    return ordered[rank - 1]


def request(url: str, method: str = "GET", token: str | None = None, body: dict | None = None,
            headers: dict | None = None, timeout: float = 15.0) -> tuple[int, float, bytes]:
    data = None if body is None else json.dumps(body).encode()
    all_headers = {"Accept": "application/json", **(headers or {})}
    if data is not None:
        all_headers["Content-Type"] = "application/json"
    if token:
        all_headers["Authorization"] = "Bearer " + token
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=data, method=method, headers=all_headers),
                                    timeout=timeout) as answer:
            content = answer.read()
            return answer.status, (time.perf_counter() - start) * 1000, content
    except urllib.error.HTTPError as error:
        error.read()
        return error.code, (time.perf_counter() - start) * 1000, b""
    except (urllib.error.URLError, TimeoutError, ConnectionError):
        return 0, (time.perf_counter() - start) * 1000, b""


def login(base: str) -> str:
    token = os.environ.get("WIFISENSE_TOKEN")
    if token:
        return token
    user, password = os.environ.get("WIFISENSE_USER"), os.environ.get("WIFISENSE_PASSWORD")
    if not user or not password:
        sys.exit("Define WIFISENSE_USER y WIFISENSE_PASSWORD (o WIFISENSE_TOKEN) antes de correr la prueba.")
    status, _, content = request(base + "/api/auth/login", "POST", body={"username": user, "password": password})
    if status != 200:
        sys.exit(f"No se pudo iniciar sesión (HTTP {status}). Si el servidor solo acepta contraseñas cifradas, "
                 "usa WIFISENSE_TOKEN con un token de una sesión abierta.")
    return json.loads(content)["token"]


def team_routes(base: str, token: str) -> list[tuple[str, str]]:
    """Las páginas del equipo que más se usan, para las redes que existan (máximo 3)."""
    status, _, content = request(base + "/api/networks", token=token)
    if status != 200:
        sys.exit(f"No se pudieron leer las redes (HTTP {status}): ¿la cuenta es del equipo?")
    # the networks with data (a new network without measurements answers 404 in some pages, which is correct)
    measured = sorted((n for n in json.loads(content) if n.get("lastCollectedAt")),
                      key=lambda n: n["lastCollectedAt"], reverse=True)
    networks = [n["id"] for n in measured][:3]
    routes = [("panel", "/api/dashboard/summary"), ("estado", "/api/dashboard/status"), ("redes", "/api/networks"),
              ("alertas", "/api/alerts"), ("modelo IA", "/api/ai/model"), ("anomalías", "/api/anomalies/causes")]
    for network in networks:
        routes += [("mediciones", f"/api/networks/{network}/measurements?limit=100"),
                   ("calidad", f"/api/networks/{network}/quality"),
                   ("sensor", f"/api/networks/{network}/sensor/overview"),
                   ("reporte semanal", f"/api/reports/networks/{network}/weekly")]
    return routes


def team_user(base: str, token: str, routes: list[tuple[str, str]], stats: Stats, until: float, pause: float) -> None:
    """Una persona que va abriendo páginas, con una pausa corta entre una y otra."""
    rng = random.Random()
    while time.time() < until:
        name, path = rng.choice(routes)
        status, ms, _ = request(base + path, token=token)
        stats.add(name, ms, status)
        time.sleep(rng.uniform(0, pause))


def agent(base: str, key: str, stats: Stats, until: float, every: float) -> None:
    """Un agente de prueba que envía un reporte cada `every` segundos (el backend acepta uno cada 50 s por clave)."""
    rng = random.Random()
    devices = [{"id": f"{rng.getrandbits(96):024x}", "kind": "PHONE", "privateMac": True} for _ in range(6)]
    while time.time() < until:
        report = {"agentVersion": "1.7.0", "platform": "Prueba de carga", "authentication": "WPA2", "band": "5",
                  "channel": 36, "signalDbm": -55, "latencyMs": round(rng.uniform(15, 60), 1),
                  "jitterMs": round(rng.uniform(1, 6), 1), "packetLossPct": 0, "deviceCount": len(devices),
                  "devices": devices}
        status, ms, _ = request(base + "/api/agent/reports", "POST", body=report, headers={"X-Agent-Key": key})
        stats.add("reporte del agente", ms, status)
        time.sleep(max(0.0, min(every, until - time.time())))


def summary(stats: Stats, seconds: float) -> dict:
    rows, all_times, all_errors = [], [], 0
    for name in sorted(stats.times):
        times = stats.times[name]
        all_times += times
        all_errors += stats.errors[name]
        rows.append({"ruta": name, "peticiones": len(times), "errores": stats.errors[name],
                     "limitadas_429": stats.limited[name], "p50_ms": round(percentile(times, 50), 1),
                     "p95_ms": round(percentile(times, 95), 1), "p99_ms": round(percentile(times, 99), 1),
                     "max_ms": round(max(times), 1)})
    total = len(all_times)
    return {"segundos": round(seconds, 1), "peticiones": total,
            "errores_por_codigo": {str(code): count for code, count in sorted(stats.codes.items())},
            "por_segundo": round(total / seconds, 1) if seconds else 0.0,
            "errores": all_errors, "tasa_errores": round(all_errors / total, 4) if total else 0.0,
            "p50_ms": round(percentile(all_times, 50), 1), "p95_ms": round(percentile(all_times, 95), 1),
            "p99_ms": round(percentile(all_times, 99), 1), "rutas": rows}


def print_summary(result: dict, ok: bool) -> None:
    print(f"\n{'Ruta':22} {'Peticiones':>10} {'Errores':>8} {'429':>5} {'p50 ms':>8} {'p95 ms':>8} {'p99 ms':>8} {'máx ms':>8}")
    for row in result["rutas"]:
        print(f"{row['ruta']:22} {row['peticiones']:>10} {row['errores']:>8} {row['limitadas_429']:>5} "
              f"{row['p50_ms']:>8} {row['p95_ms']:>8} {row['p99_ms']:>8} {row['max_ms']:>8}")
    print(f"\nTotal: {result['peticiones']} peticiones en {result['segundos']} s = {result['por_segundo']} por segundo; "
          f"errores {result['tasa_errores'] * 100:.2f} %; p50 {result['p50_ms']} ms, p95 {result['p95_ms']} ms, "
          f"p99 {result['p99_ms']} ms")
    if result["errores_por_codigo"]:
        print("Errores por código HTTP (0 = sin respuesta):", result["errores_por_codigo"])
    print("RESULTADO:", "CUMPLE" if ok else "NO CUMPLE",
          f"(objetivo: p95 < {P95_GOAL_MS:.0f} ms y errores < {ERROR_GOAL * 100:.0f} %)")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Prueba de carga del backend de WiFiSense (solo lectura).")
    parser.add_argument("--url", default="http://localhost:8080")
    parser.add_argument("--usuarios", type=int, default=20, help="personas del equipo usando la página a la vez")
    parser.add_argument("--segundos", type=int, default=60)
    parser.add_argument("--pausa", type=float, default=1.0, help="pausa máxima entre dos páginas (segundos)")
    parser.add_argument("--agentes-cada", type=float, default=55.0,
                        help="segundos entre reportes de cada agente de WIFISENSE_AGENT_KEYS (claves de prueba)")
    parser.add_argument("--remoto", action="store_true", help="confirma que la URL no es local (máx. 20 usuarios, 120 s)")
    parser.add_argument("--salida", help="guardar el resultado en este archivo JSON")
    args = parser.parse_args(argv)

    base = args.url.rstrip("/")
    host = urllib.parse.urlparse(base).hostname or ""
    if host not in LOCAL_HOSTS:
        if not args.remoto:
            sys.exit(f"{host} no es local: agrega --remoto para confirmar (la prueba se limita a "
                     f"{REMOTE_MAX_USERS} usuarios y {REMOTE_MAX_SECONDS} s).")
        args.usuarios = min(args.usuarios, REMOTE_MAX_USERS)
        args.segundos = min(args.segundos, REMOTE_MAX_SECONDS)

    token = login(base)
    routes = team_routes(base, token)
    keys = [k.strip() for k in os.environ.get("WIFISENSE_AGENT_KEYS", "").split(",") if k.strip()]
    print(f"Probando {base}: {args.usuarios} usuarios y {len(keys)} agentes durante {args.segundos} s "
          f"({len(routes)} páginas distintas)…")

    stats = Stats()
    start = time.time()
    until = start + args.segundos
    threads = [threading.Thread(target=team_user, args=(base, token, routes, stats, until, args.pausa), daemon=True)
               for _ in range(args.usuarios)]
    threads += [threading.Thread(target=agent, args=(base, key, stats, until, args.agentes_cada), daemon=True)
                for key in keys]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join(timeout=args.segundos + 30)

    result = summary(stats, time.time() - start)
    ok = result["peticiones"] > 0 and result["p95_ms"] < P95_GOAL_MS and result["tasa_errores"] < ERROR_GOAL
    print_summary(result, ok)
    if args.salida:
        with open(args.salida, "w", encoding="utf-8") as out:
            json.dump({**result, "url": base, "usuarios": args.usuarios, "agentes": len(keys), "cumple": ok}, out,
                      ensure_ascii=False, indent=2)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
